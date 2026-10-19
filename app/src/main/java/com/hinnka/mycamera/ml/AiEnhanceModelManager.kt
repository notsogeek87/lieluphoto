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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.math.ln
import kotlin.math.min
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface AiEnhanceModelState {
    data object Missing : AiEnhanceModelState
    data class Downloading(val progress: Float?) : AiEnhanceModelState
    data object Ready : AiEnhanceModelState
    data object Failed : AiEnhanceModelState
}

/** Modèles téléchargeables, vérifiés à l'installation (taille et SHA-256). */
enum class AiModel(
    val directory: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val url: String,
) {
    // NAFNet (jeu SIDD, vraies photos de smartphones) : débruitage, licence MIT, ~62 Mo.
    // Entrée/sortie RGB [0,1] NCHW 256×256, même format que le DnCNN intégré.
    DENOISE(
        directory = "ml_models/nafnet_sidd",
        fileName = "nafnet_sidd_width32_fp16.tflite",
        sizeBytes = 62_454_048L,
        sha256 = "f8fbaa422411683c53e802cf7cc7cf9be0a0de00886ad4af057232e26b172a0c",
        url = "https://huggingface.co/litert-community/NAFNet-SIDD-width32-LiteRT/resolve/main/nafnet_sidd_width32_fp16.tflite",
    ),

    // GFPGAN v1.4 : restauration des visages, licence Apache-2.0, ~431 Mo.
    // Entrée/sortie RGB [-1,1] NCHW 512×512, visage aligné sur le gabarit FFHQ.
    FACES(
        directory = "ml_models/gfpgan_v14",
        fileName = "gfpgan_fp16.tflite",
        sizeBytes = 431_493_712L,
        sha256 = "6b792e1c33eb93b8115c57086042a440adf4072c60349d85beb266821d2b3dfe",
        url = "https://huggingface.co/litert-community/GFPGAN-v1.4-LiteRT/resolve/main/gfpgan_fp16.tflite",
    ),
}

/** Choix de l'utilisateur (appui long sur le bouton IA) : quel(s) modèle(s) appliquer. */
enum class AiEnhanceChoice(val models: List<AiModel>) {
    DENOISE(listOf(AiModel.DENOISE)),
    FACES(listOf(AiModel.FACES)),
    BOTH(listOf(AiModel.DENOISE, AiModel.FACES));

    companion object {
        fun fromPersisted(value: String?): AiEnhanceChoice =
            entries.firstOrNull { it.name == value } ?: DENOISE
    }
}

// Mélange avec l'original (1 = débruitage complet). Le modèle est appliqué à toutes les photos
// quand l'utilisateur l'active, mais il adoucit un peu la texture : à ISO bas (peu de bruit, constaté
// sur le salon éclairé) on garde surtout l'original, à ISO 800 et plus on débruite franchement.
private const val BLEND_AT_LOW_ISO = 0.35f
private const val BLEND_AT_HIGH_ISO = 0.8f
private const val LOW_ISO = 200f
private const val HIGH_ISO = 800f

internal fun aiEnhanceBlendStrength(iso: Int?): Float {
    val value = (iso ?: HIGH_ISO.toInt()).coerceAtLeast(1).toFloat()
    val t = ((ln(value / LOW_ISO)) / ln(HIGH_ISO / LOW_ISO)).coerceIn(0f, 1f)
    return BLEND_AT_LOW_ISO + (BLEND_AT_HIGH_ISO - BLEND_AT_LOW_ISO) * t
}

/**
 * Modèles facultatifs d'amélioration des photos, téléchargés à la demande dans le stockage privé.
 * Tout tourne sur le téléphone. Appliqués automatiquement à toutes les photos (Photo, Nuit,
 * Portrait) quand l'utilisateur l'a activé (bouton « IA » du viseur ou réglages), avant
 * l'enregistrement de l'original et l'export : débruitage d'abord, puis visages.
 */
object AiEnhanceModelManager {
    private const val TAG = "AiEnhanceModelManager"

    // Recouvrement des tuiles 256×256 (DnCNN : 4 px) : évite les jointures visibles.
    private const val TILE_OVERLAP = 16

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()
    private val states = AiModel.entries.associateWith {
        MutableStateFlow<AiEnhanceModelState>(AiEnhanceModelState.Missing)
    }
    private val lock = Any()
    private var initialized = false
    private val downloadJobs = mutableMapOf<AiModel, Job>()
    private val inferenceMutex = Mutex()
    private val facesBusy = java.util.concurrent.atomic.AtomicBoolean(false)
    private const val DENOISE_TIMEOUT_MS = 45_000L
    private const val FACES_TIMEOUT_MS = 30_000L
    private const val SKIP_DENOISE_MAX_ISO = 200
    private const val DENOISE_ENABLED = false
    private const val DENOISER_IDLE_RELEASE_MS = 90_000L

    // NAFNet en pleine résolution : ~220 tuiles pour 10 Mpx (0,2 s par tuile sur un PC à 4 cœurs,
    // bien plus sur le processeur d'un téléphone). Une version réduite ne retire pas le grain fin.
    // On chronomètre donc une tuile et on ne débruite que si le total tient dans ce budget
    // (délégué GPU rapide) ; sinon la photo garde son rendu et l'EXIF l'indique.
    private const val DENOISE_BUDGET_MS = 30_000L
    private const val DENOISE_TILE = 256
    private const val DENOISE_TILE_STRIDE = DENOISE_TILE - 2 * TILE_OVERLAP
    private var cachedDenoiserTileMs: Long? = null

    // NAFNet gardé chargé entre deux photos proches (chargement + délégué GPU ≈ 1 à 2 s).
    // Utilisé uniquement sous inferenceMutex.
    private var cachedDenoiser: DnCNNDenoiseEstimator? = null
    private var cachedDenoiserPath: String? = null
    private var denoiserReleaseJob: Job? = null

    private fun acquireDenoiser(context: Context, modelFile: File): DnCNNDenoiseEstimator {
        denoiserReleaseJob?.cancel()
        cachedDenoiser?.takeIf { cachedDenoiserPath == modelFile.absolutePath }?.let { return it }
        cachedDenoiser?.close()
        cachedDenoiserTileMs = null
        return DnCNNDenoiseEstimator(
            context = context,
            modelFile = modelFile,
            tileOverlap = TILE_OVERLAP,
            // NAFNet est conçu pour tourner entièrement sur GPU ; le CPU (XNNPACK) refuse ce graphe fp16.
            forceGpuAttempt = true,
        ).also {
            cachedDenoiser = it
            cachedDenoiserPath = modelFile.absolutePath
        }
    }

    private fun releaseDenoiserLater() {
        denoiserReleaseJob?.cancel()
        denoiserReleaseJob = scope.launch {
            kotlinx.coroutines.delay(DENOISER_IDLE_RELEASE_MS)
            inferenceMutex.lock()
            try {
                releaseDenoiser()
            } finally {
                inferenceMutex.unlock()
            }
        }
    }

    private fun releaseDenoiser() {
        cachedDenoiserTileMs = null
        cachedDenoiser?.close()
        cachedDenoiser = null
        cachedDenoiserPath = null
    }

    fun observe(context: Context, model: AiModel): StateFlow<AiEnhanceModelState> {
        initialize(context.applicationContext)
        return states.getValue(model).asStateFlow()
    }

    fun isInstalled(context: Context, model: AiModel): Boolean =
        installedModelFileOrNull(context.applicationContext, model) != null

    private fun modelDirectory(context: Context, model: AiModel) = File(context.filesDir, model.directory)

    private fun modelFile(context: Context, model: AiModel) = File(modelDirectory(context, model), model.fileName)

    private fun installedModelFileOrNull(context: Context, model: AiModel): File? =
        modelFile(context, model).takeIf { it.isFile && it.length() == model.sizeBytes }

    private fun initialize(context: Context) {
        synchronized(lock) {
            if (initialized) return
            initialized = true
            AiModel.entries.forEach { model ->
                if (installedModelFileOrNull(context, model) != null) {
                    states.getValue(model).value = AiEnhanceModelState.Ready
                }
            }
        }
    }

    fun download(context: Context, model: AiModel) {
        val appContext = context.applicationContext
        initialize(appContext)
        val state = states.getValue(model)
        synchronized(lock) {
            if (installedModelFileOrNull(appContext, model) != null) {
                state.value = AiEnhanceModelState.Ready
                return
            }
            if (downloadJobs[model]?.isActive == true) return
            state.value = AiEnhanceModelState.Downloading(0f)
            downloadJobs[model] = scope.launch {
                state.value = try {
                    downloadAndInstall(appContext, model)
                    AiEnhanceModelState.Ready
                } catch (e: Exception) {
                    PLog.w(TAG, "Failed to download the AI model $model", e)
                    AiEnhanceModelState.Failed
                }
            }
        }
    }

    fun delete(context: Context, model: AiModel) {
        val appContext = context.applicationContext
        synchronized(lock) {
            if (downloadJobs[model]?.isActive == true) return
            if (model == AiModel.FACES) FaceRestorer.releaseNow()
            if (model == AiModel.DENOISE) {
                scope.launch {
                    inferenceMutex.lock()
                    try {
                        releaseDenoiser()
                    } finally {
                        inferenceMutex.unlock()
                    }
                }
            }
            modelDirectory(appContext, model).deleteRecursively()
            states.getValue(model).value = AiEnhanceModelState.Missing
        }
    }

    private fun downloadAndInstall(context: Context, model: AiModel) {
        val state = states.getValue(model)
        val directory = modelDirectory(context, model).apply { mkdirs() }
        val partFile = File(directory, "${model.fileName}.part")
        partFile.delete()
        val request = Request.Builder().url(model.url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response")
            val total = body.contentLength().takeIf { it > 0 } ?: model.sizeBytes
            val digest = MessageDigest.getInstance("SHA-256")
            body.byteStream().use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                    var written = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        written += read
                        if (written > model.sizeBytes) throw IOException("Model larger than expected")
                        val progress = (written.toFloat() / total).coerceIn(0f, 1f)
                        val percent = (progress * 100f).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            state.value = AiEnhanceModelState.Downloading(progress)
                        }
                    }
                }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (partFile.length() != model.sizeBytes || sha != model.sha256) {
                partFile.delete()
                throw IOException("Model verification failed: size=${partFile.length()} sha=$sha")
            }
        }
        val target = modelFile(context, model)
        target.delete()
        if (!partFile.renameTo(target)) {
            partFile.delete()
            throw IOException("Failed to install the AI model $model")
        }
    }

    /** Vrai si une photo prise maintenant sera améliorée (réglage actif et modèle choisi installé). */
    suspend fun willEnhance(context: Context): Boolean {
        val appContext = context.applicationContext
        val preferences = ContentRepository.getInstance(appContext).userPreferencesRepository
            .userPreferences.firstOrNull() ?: return false
        if (!preferences.aiEnhanceEnabled) return false
        return AiEnhanceChoice.fromPersisted(preferences.aiEnhanceModel).models
            .any { installedModelFileOrNull(appContext, it) != null }
    }

    /**
     * Améliore [bitmap] si le réglage est activé et le(s) modèle(s) choisi(s) installé(s) ; la force
     * du débruitage suit [iso]. Renvoie le bitmap d'origine sinon, ou en cas d'échec (la photo n'est
     * jamais perdue). L'appelant reste propriétaire de [bitmap] ; s'il reçoit un nouveau bitmap, il
     * peut recycler l'ancien.
     */
    suspend fun enhanceIfEnabled(
        context: Context,
        bitmap: Bitmap,
        iso: Int?,
        denoise: Boolean = true,
        waitForTurn: Boolean = false,
        // Résumé lisible de ce qui a été fait (ajouté à l'EXIF « Software »).
        onReport: (String) -> Unit = {},
    ): Bitmap {
        val appContext = context.applicationContext
        val preferences = runCatching {
            ContentRepository.getInstance(appContext).userPreferencesRepository
                .userPreferences.firstOrNull()
        }.getOrNull() ?: return bitmap
        if (!preferences.aiEnhanceEnabled) return bitmap
        val choice = AiEnhanceChoice.fromPersisted(preferences.aiEnhanceModel)
        // Scène très lumineuse (ISO ≤ 200) : presque pas de bruit, débruitage inutile.
        // Au-delà, il ne tourne que si le téléphone le fait dans le budget (voir denoise()).
        val denoiseUseful = iso == null || iso > SKIP_DENOISE_MAX_ISO
        // Débruitage NAFNet désactivé : sur le GPU du Fold il laisse des tuiles en damier (38 blocs
        // aberrants sur un selfie, build 202, et d'autres trop discrets pour être détectés) et il
        // ramollit le visage (netteté 49 contre 97 sans lui). Le rendu de l'app débruite déjà.
        val denoiseModel = installedModelFileOrNull(appContext, AiModel.DENOISE)
            ?.takeIf { DENOISE_ENABLED && denoise && denoiseUseful && AiModel.DENOISE in choice.models }
        val facesModel = installedModelFileOrNull(appContext, AiModel.FACES)
            ?.takeIf { AiModel.FACES in choice.models }
        val skippedDenoise = when {
            AiModel.DENOISE !in choice.models -> null
            !DENOISE_ENABLED -> "bruit desactive"
            !denoise -> "bruit non (RAW)"
            !denoiseUseful -> "bruit non (ISO $iso)"
            denoiseModel == null -> "bruit non (modèle absent)"
            else -> null
        }
        val skippedFaces = when {
            AiModel.FACES !in choice.models -> "visages non choisis"
            facesModel == null -> "visages modèle absent"
            else -> null
        }
        // Jamais d'attente derrière une autre photo : si l'IA est déjà occupée, on enregistre
        // cette photo telle quelle plutôt que de bloquer toute la file de traitement.
        if (waitForTurn) {
            inferenceMutex.lock()
        } else if (!inferenceMutex.tryLock()) {
            PLog.w(TAG, "AI enhance busy, skipping for this photo")
            return bitmap
        }
        try {
            var current = bitmap
            val started = System.currentTimeMillis()
            val steps = mutableListOf<String>()
            skippedDenoise?.let(steps::add)
            skippedFaces?.let(steps::add)
            if (denoiseModel != null) {
                val stepStart = System.currentTimeMillis()
                var denoiseNote = "bruit délai dépassé"
                val denoised = withTimeoutOrNull(DENOISE_TIMEOUT_MS) {
                    denoise(appContext, denoiseModel, current, iso).also { denoiseNote = it.second }.first
                } ?: run {
                    PLog.w(TAG, "AI denoise timed out, keeping the photo as is")
                    current
                }
                val seconds = "%.1f".format((System.currentTimeMillis() - stepStart) / 1000f)
                steps += "$denoiseNote ${seconds}s"
                if (denoised !== current && current !== bitmap) current.recycle()
                current = denoised
            }
            if (facesModel != null) {
                if (facesBusy.get()) {
                    steps += "visages non (occupé)"
                } else {
                    val stepStart = System.currentTimeMillis()
                    val restored = restoreFacesWithTimeout(appContext, facesModel, current)
                    val seconds = "%.1f".format((System.currentTimeMillis() - stepStart) / 1000f)
                    steps += if (restored !== current) {
                        "visages ${FaceRestorer.lastRestoredFaceCount} ${seconds}s"
                    } else {
                        "visages aucun ${seconds}s"
                    }
                    if (restored !== current && current !== bitmap) current.recycle()
                    current = restored
                }
            }
            // Netteté finale, toujours appliquée : accentue les vrais contours (barbe, cils, tissus)
            // sans faire remonter le bruit des zones unies.
            run {
                val stepStart = System.currentTimeMillis()
                val sharpened = try {
                    AiSharpener.sharpen(current)
                } catch (e: Throwable) {
                    PLog.e(TAG, "AI sharpening failed", e)
                    current
                }
                val seconds = "%.1f".format((System.currentTimeMillis() - stepStart) / 1000f)
                steps += if (sharpened !== current) "nettete ${seconds}s" else "nettete echec"
                if (sharpened !== current && current !== bitmap) current.recycle()
                current = sharpened
            }
            onReport("IA[${steps.joinToString(", ")}]")
            PLog.d(TAG, "AI enhance total ${System.currentTimeMillis() - started}ms ${bitmap.width}x${bitmap.height} $steps")
            return current
        } finally {
            inferenceMutex.unlock()
        }
    }

    // L'inférence TFLite est un appel natif non interruptible : on la lance sur un fil dédié et on
    // l'abandonne après le délai. Tant qu'elle tourne encore, les visages sont ignorés (facesBusy).
    private suspend fun restoreFacesWithTimeout(context: Context, modelFile: File, bitmap: Bitmap): Bitmap {
        facesBusy.set(true)
        val result = CompletableDeferred<Bitmap>()
        val worker = Thread({
            try {
                result.complete(FaceRestorer.restore(context, modelFile, bitmap))
            } catch (e: Throwable) {
                PLog.e(TAG, "Face restoration failed, keeping the photo as is", e)
                result.complete(bitmap)
            } finally {
                facesBusy.set(false)
            }
        }, "face-restorer").apply { isDaemon = true }
        worker.start()
        return withTimeoutOrNull(FACES_TIMEOUT_MS) { result.await() } ?: run {
            PLog.w(TAG, "Face restoration timed out after ${FACES_TIMEOUT_MS}ms, photo kept as is")
            // La photo est enregistrée sans attendre ; ce résultat tardif est ignoré.
            result.invokeOnCompletion { runCatching { result.getCompleted().takeIf { it !== bitmap }?.recycle() } }
            bitmap
        }
    }

    /** Renvoie la photo débruitée (ou l'originale) et un court motif pour l'EXIF. */
    private suspend fun denoise(
        context: Context,
        modelFile: File,
        bitmap: Bitmap,
        iso: Int?,
    ): Pair<Bitmap, String> {
        if (bitmap.width < DENOISE_TILE || bitmap.height < DENOISE_TILE) return bitmap to "bruit non (image trop petite)"
        return try {
            val estimator = acquireDenoiser(context, modelFile)
            val input = if (bitmap.config == Bitmap.Config.ARGB_8888) {
                bitmap
            } else {
                bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return bitmap to "bruit échec"
            }
            try {
                val tiles = ceilDiv(input.width, DENOISE_TILE_STRIDE).toLong() *
                    ceilDiv(input.height, DENOISE_TILE_STRIDE)
                val tileMs = cachedDenoiserTileMs ?: measureDenoiseTileMs(estimator, input)?.also {
                    cachedDenoiserTileMs = it
                } ?: return bitmap to "bruit échec"
                val estimatedMs = tileMs * tiles
                if (estimatedMs > DENOISE_BUDGET_MS) {
                    PLog.w(TAG, "AI denoise skipped: $tiles tiles × ${tileMs}ms ≈ ${estimatedMs}ms > budget")
                    val gpuNote = estimator.gpuInitError?.let { ", GPU: $it" }.orEmpty()
                    return bitmap to "bruit non (trop lent ~${estimatedMs / 1000}s, " +
                        "${estimator.backendName} ${tileMs}ms/tuile$gpuNote)"
                }
                val enhanced = estimator.denoisePatchwise(input, strength = aiEnhanceBlendStrength(iso))
                    ?: return bitmap to "bruit échec"
                // Le GPU calcule en précision réduite : quelques tuiles peuvent sortir en damier
                // (constaté sur zone claire unie, build 191). Un vrai débruitage ne bouge les pixels
                // que de quelques niveaux ; les blocs aberrants reprennent l'original.
                val repaired = repairAberrantBlocks(input, enhanced)
                PLog.d(TAG, "AI denoise applied: iso=$iso ${bitmap.width}x${bitmap.height} repairedBlocks=$repaired")
                val note = "bruit ${estimator.backendName}" + if (repaired > 0) " ($repaired blocs corriges)" else ""
                enhanced to note
            } finally {
                if (input !== bitmap) input.recycle()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            PLog.e(TAG, "AI denoise failed, keeping the original photo", e)
            releaseDenoiser()
            bitmap to "bruit échec"
        } finally {
            releaseDenoiserLater()
        }
    }

    // Une tuile au centre, deux fois : la première absorbe l'initialisation (compilation GPU).
    private suspend fun measureDenoiseTileMs(estimator: DnCNNDenoiseEstimator, input: Bitmap): Long? {
        val probe = Bitmap.createBitmap(
            input,
            (input.width - DENOISE_TILE) / 2,
            (input.height - DENOISE_TILE) / 2,
            DENOISE_TILE,
            DENOISE_TILE,
        )
        return try {
            estimator.denoise(probe)?.recycle() ?: return null
            val start = android.os.SystemClock.elapsedRealtime()
            estimator.denoise(probe)?.recycle() ?: return null
            (android.os.SystemClock.elapsedRealtime() - start).coerceAtLeast(1L)
        } finally {
            probe.recycle()
        }
    }

    private fun ceilDiv(value: Int, divisor: Int) = (value + divisor - 1) / divisor

    private const val CHECK_BLOCK = 32
    private const val MAX_BLOCK_MEAN_DIFF = 16f

    /**
     * Remet l'original dans les blocs 32×32 où le débruitage s'écarte trop de l'entrée (écart
     * moyen > 16 niveaux), ainsi que leurs voisins immédiats. Renvoie le nombre de blocs fautifs.
     */
    internal fun repairAberrantBlocks(original: Bitmap, denoised: Bitmap): Int {
        val width = min(original.width, denoised.width)
        val height = min(original.height, denoised.height)
        val blocksX = ceilDiv(width, CHECK_BLOCK)
        val blocksY = ceilDiv(height, CHECK_BLOCK)
        val bad = BooleanArray(blocksX * blocksY)
        val rowA = IntArray(width * CHECK_BLOCK)
        val rowB = IntArray(width * CHECK_BLOCK)
        var badCount = 0
        for (by in 0 until blocksY) {
            val y0 = by * CHECK_BLOCK
            val rows = min(CHECK_BLOCK, height - y0)
            original.getPixels(rowA, 0, width, 0, y0, width, rows)
            denoised.getPixels(rowB, 0, width, 0, y0, width, rows)
            for (bx in 0 until blocksX) {
                val x0 = bx * CHECK_BLOCK
                val cols = min(CHECK_BLOCK, width - x0)
                var sum = 0L
                for (r in 0 until rows) {
                    val base = r * width + x0
                    for (c in 0 until cols) {
                        val a = rowA[base + c]
                        val b = rowB[base + c]
                        sum += kotlin.math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) +
                            kotlin.math.abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) +
                            kotlin.math.abs((a and 0xFF) - (b and 0xFF))
                    }
                }
                val mean = sum.toFloat() / (3f * rows * cols)
                if (mean > MAX_BLOCK_MEAN_DIFF) {
                    bad[by * blocksX + bx] = true
                    badCount++
                }
            }
        }
        if (badCount == 0) return 0
        val patch = IntArray(CHECK_BLOCK * CHECK_BLOCK)
        for (by in 0 until blocksY) {
            for (bx in 0 until blocksX) {
                // Bloc fautif ou voisin d'un bloc fautif.
                var restore = false
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = bx + dx
                    val ny = by + dy
                    if (nx in 0 until blocksX && ny in 0 until blocksY && bad[ny * blocksX + nx]) restore = true
                }
                if (!restore) continue
                val x0 = bx * CHECK_BLOCK
                val y0 = by * CHECK_BLOCK
                val cols = min(CHECK_BLOCK, width - x0)
                val rows = min(CHECK_BLOCK, height - y0)
                original.getPixels(patch, 0, cols, x0, y0, cols, rows)
                denoised.setPixels(patch, 0, cols, x0, y0, cols, rows)
            }
        }
        return badCount
    }
}
