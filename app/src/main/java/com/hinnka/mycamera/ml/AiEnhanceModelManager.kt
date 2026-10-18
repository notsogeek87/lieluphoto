package com.hinnka.mycamera.ml

import android.content.Context
import android.graphics.Bitmap
import com.hinnka.mycamera.data.ContentRepository
import com.hinnka.mycamera.utils.PLog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface AiEnhanceModelState {
    data object Missing : AiEnhanceModelState
    data class Downloading(val progress: Float?) : AiEnhanceModelState
    data object Ready : AiEnhanceModelState
    data object Failed : AiEnhanceModelState
}

/**
 * Modèle facultatif d'amélioration des photos : NAFNet (débruitage appris sur de vraies photos
 * de smartphones, jeu SIDD), licence MIT, ~62 Mo, téléchargé à la demande et vérifié (taille et
 * SHA-256). Entrée/sortie RGB [0,1] NCHW 256×256 : même format que le DnCNN intégré, on réutilise
 * donc DnCNNDenoiseEstimator (tuiles, GPU/NNAPI/CPU).
 *
 * Appliqué automatiquement, si l'utilisateur l'a activé, aux photos prises en faible lumière,
 * avant l'enregistrement de l'original et l'export.
 */
object AiEnhanceModelManager {
    private const val TAG = "AiEnhanceModelManager"
    private const val MODEL_DIRECTORY = "ml_models/nafnet_sidd"
    private const val MODEL_FILE_NAME = "nafnet_sidd_width32_fp16.tflite"
    private const val MODEL_PART_FILE_NAME = "$MODEL_FILE_NAME.part"
    private const val EXPECTED_MODEL_SIZE_BYTES = 62_454_048L
    private const val MODEL_SHA256 =
        "f8fbaa422411683c53e802cf7cc7cf9be0a0de00886ad4af057232e26b172a0c"

    const val MODEL_DOWNLOAD_URL =
        "https://huggingface.co/litert-community/NAFNet-SIDD-width32-LiteRT/resolve/main/nafnet_sidd_width32_fp16.tflite"

    // Faible lumière : l'appareil a dû monter à ISO 800 ou plus. En dessous le bruit est faible et
    // le modèle adoucirait la texture pour rien (constaté sur le salon éclairé).
    const val MIN_ISO = 800

    // Mélange avec l'original : 1 = débruitage complet. 0,8 garde un peu de grain naturel.
    private const val BLEND_STRENGTH = 0.8f

    // Recouvrement des tuiles 256×256 (DnCNN : 4 px) : évite les jointures visibles.
    private const val TILE_OVERLAP = 16

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()
    private val _state = MutableStateFlow<AiEnhanceModelState>(AiEnhanceModelState.Missing)
    private val lock = Any()
    private var initialized = false
    private var downloadJob: Job? = null
    private val inferenceMutex = Mutex()

    fun observe(context: Context): StateFlow<AiEnhanceModelState> {
        initialize(context.applicationContext)
        return _state.asStateFlow()
    }

    fun isInstalled(context: Context): Boolean = installedModelFileOrNull(context.applicationContext) != null

    private fun modelFile(context: Context) = File(File(context.filesDir, MODEL_DIRECTORY), MODEL_FILE_NAME)

    private fun installedModelFileOrNull(context: Context): File? =
        modelFile(context).takeIf { it.isFile && it.length() == EXPECTED_MODEL_SIZE_BYTES }

    private fun initialize(context: Context) {
        synchronized(lock) {
            if (initialized) return
            initialized = true
            if (installedModelFileOrNull(context) != null) _state.value = AiEnhanceModelState.Ready
        }
    }

    fun download(context: Context) {
        val appContext = context.applicationContext
        initialize(appContext)
        synchronized(lock) {
            if (installedModelFileOrNull(appContext) != null) {
                _state.value = AiEnhanceModelState.Ready
                return
            }
            if (downloadJob?.isActive == true) return
            _state.value = AiEnhanceModelState.Downloading(0f)
            downloadJob = scope.launch {
                _state.value = try {
                    downloadAndInstall(appContext)
                    AiEnhanceModelState.Ready
                } catch (e: Exception) {
                    PLog.w(TAG, "Failed to download the AI enhance model", e)
                    AiEnhanceModelState.Failed
                }
            }
        }
    }

    fun delete(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            if (downloadJob?.isActive == true) return
            File(appContext.filesDir, MODEL_DIRECTORY).deleteRecursively()
            _state.value = AiEnhanceModelState.Missing
        }
    }

    private fun downloadAndInstall(context: Context) {
        val directory = File(context.filesDir, MODEL_DIRECTORY).apply { mkdirs() }
        val partFile = File(directory, MODEL_PART_FILE_NAME)
        partFile.delete()
        val request = Request.Builder().url(MODEL_DOWNLOAD_URL).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response")
            val total = body.contentLength().takeIf { it > 0 } ?: EXPECTED_MODEL_SIZE_BYTES
            val digest = MessageDigest.getInstance("SHA-256")
            body.byteStream().use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        written += read
                        if (written > EXPECTED_MODEL_SIZE_BYTES) throw IOException("Model larger than expected")
                        _state.value = AiEnhanceModelState.Downloading((written.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (partFile.length() != EXPECTED_MODEL_SIZE_BYTES || sha != MODEL_SHA256) {
                partFile.delete()
                throw IOException("Model verification failed: size=${partFile.length()} sha=$sha")
            }
        }
        val target = modelFile(context)
        target.delete()
        if (!partFile.renameTo(target)) {
            partFile.delete()
            throw IOException("Failed to install the AI enhance model")
        }
    }

    /**
     * Améliore [bitmap] si le réglage est activé, le modèle installé et la photo prise en faible
     * lumière ([iso] ≥ [MIN_ISO]). Renvoie le bitmap d'origine sinon, ou en cas d'échec (la photo
     * n'est jamais perdue). L'appelant reste propriétaire de [bitmap] ; s'il reçoit un nouveau
     * bitmap, il peut recycler l'ancien.
     */
    suspend fun enhanceIfEnabled(context: Context, bitmap: Bitmap, iso: Int?): Bitmap {
        val appContext = context.applicationContext
        if ((iso ?: 0) < MIN_ISO) return bitmap
        val modelFile = installedModelFileOrNull(appContext) ?: return bitmap
        val enabled = runCatching {
            ContentRepository.getInstance(appContext).userPreferencesRepository
                .userPreferences.firstOrNull()?.aiEnhanceEnabled
        }.getOrNull() ?: false
        if (!enabled) return bitmap
        return inferenceMutex.withLock {
            val estimator = DnCNNDenoiseEstimator(
                context = appContext,
                modelFile = modelFile,
                tileOverlap = TILE_OVERLAP,
            )
            try {
                val input = if (bitmap.config == Bitmap.Config.ARGB_8888) {
                    bitmap
                } else {
                    bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return@withLock bitmap
                }
                val enhanced = estimator.denoisePatchwise(input, strength = BLEND_STRENGTH)
                if (input !== bitmap) input.recycle()
                PLog.d(TAG, "AI enhance applied: iso=$iso ${bitmap.width}x${bitmap.height} ok=${enhanced != null}")
                enhanced ?: bitmap
            } catch (e: Throwable) {
                PLog.e(TAG, "AI enhance failed, keeping the original photo", e)
                bitmap
            } finally {
                estimator.close()
            }
        }
    }
}
