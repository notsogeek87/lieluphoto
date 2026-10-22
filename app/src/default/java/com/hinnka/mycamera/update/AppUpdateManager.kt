package com.hinnka.mycamera.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.hinnka.mycamera.BuildConfig
import com.hinnka.mycamera.utils.PLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

data class AppUpdateRelease(
    val versionName: String?,
    val buildNumber: Int,
    val downloadUrl: String,
    val fileName: String
)

/** État de la mise à jour, affiché par le bandeau de MainActivity. */
sealed interface AppUpdateState {
    data object None : AppUpdateState
    data class Available(val release: AppUpdateRelease) : AppUpdateState
    /** [progress] dans 0..1, null tant que la taille est inconnue. */
    data class Downloading(val release: AppUpdateRelease, val progress: Float?) : AppUpdateState
    data class Ready(val release: AppUpdateRelease, val apkFile: File) : AppUpdateState
    data class Failed(val release: AppUpdateRelease) : AppUpdateState
}

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/notsogeek87/lieluphoto/releases/latest"
    private const val VERSION_CODE_BASE = 1000
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val APK_MAGIC_0 = 'P'.code.toByte()
    private const val APK_MAGIC_1 = 'K'.code.toByte()
    private const val HTTP_RANGE_NOT_SATISFIABLE = 416
    private const val MAX_REDIRECTS = 5

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadLock = Any()
    private var activeDownload: Pair<String, Deferred<File>>? = null
    private var activeLaunchCheck: Deferred<Unit>? = null
    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.None)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    /**
     * Même mécanique que LieluFind : dernière release GitHub (tag `v<versionName>.<run>`),
     * le versionCode installé vaut 1000 + run (voir android-build.yml).
     */
    suspend fun checkForUpdate(): AppUpdateRelease? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()

            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        PLog.w(TAG, "Version check failed: code=${response.code} url=${request.url}")
                        return@withContext null
                    }

                    val root = JSONObject(response.body?.string().orEmpty())
                    val tag = root.optString("tag_name")
                    val parts = tag.removePrefix("v").split('.')
                    val run = parts.lastOrNull()?.toIntOrNull()
                    if (parts.size < 2 || run == null) {
                        PLog.w(TAG, "Unreadable release tag: $tag")
                        return@withContext null
                    }
                    if (VERSION_CODE_BASE + run <= BuildConfig.VERSION_CODE) {
                        PLog.d(TAG, "No update available: tag=$tag, installed=${BuildConfig.VERSION_CODE}")
                        return@withContext null
                    }

                    val assets = root.optJSONArray("assets")
                    for (i in 0 until (assets?.length() ?: 0)) {
                        val asset = assets!!.getJSONObject(i)
                        val name = asset.optString("name")
                        val url = asset.optString("browser_download_url")
                        if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                            return@withContext AppUpdateRelease(
                                versionName = parts.dropLast(1).joinToString("."),
                                buildNumber = VERSION_CODE_BASE + run,
                                downloadUrl = url,
                                fileName = name
                            )
                        }
                    }
                    PLog.w(TAG, "Update found but APK asset is missing: tag=$tag")
                    null
                }
            }.onFailure { error ->
                PLog.e(TAG, "Version check error", error)
            }.getOrNull()
        }

    suspend fun downloadApk(context: Context, release: AppUpdateRelease): File =
        getOrStartDownload(context.applicationContext, release).await()

    /**
     * Vérification au lancement, comme LieluFind : ne télécharge rien, signale seulement
     * la release disponible (ou l'APK déjà téléchargé) via [state].
     */
    fun checkOnLaunch(context: Context) {
        val appContext = context.applicationContext
        synchronized(downloadLock) {
            if (activeLaunchCheck?.isActive == true) return
            activeLaunchCheck = downloadScope.async {
                val release = checkForUpdate() ?: return@async
                if (_state.value !is AppUpdateState.None) return@async
                val apkFile = findDownloadedApk(appContext, release)
                _state.value = if (apkFile != null) {
                    AppUpdateState.Ready(release, apkFile)
                } else {
                    AppUpdateState.Available(release)
                }
            }
        }
    }

    /** Lance le téléchargement depuis le bandeau ; la progression passe par [state]. */
    fun startDownload(context: Context, release: AppUpdateRelease) {
        getOrStartDownload(context.applicationContext, release)
    }

    fun findDownloadedApk(context: Context, release: AppUpdateRelease): File? {
        val apkFile = resolveApkFile(context.applicationContext, release)
        return apkFile.takeIf { it.isUsableForRelease(context.applicationContext, release) }
    }

    fun startInstall(context: Context, apkFile: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri()
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return runCatching {
                context.startActivity(permissionIntent)
                false
            }.onFailure { error ->
                PLog.e(TAG, "Failed to open unknown app sources settings", error)
            }.getOrDefault(false)
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return try {
            context.startActivity(installIntent)
            true
        } catch (error: ActivityNotFoundException) {
            PLog.e(TAG, "No installer activity found", error)
            false
        } catch (error: SecurityException) {
            PLog.e(TAG, "Installer launch denied", error)
            false
        }
    }

    private fun String.sanitizeApkFileName(): String {
        val safeName = replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (safeName.endsWith(".apk", ignoreCase = true)) safeName else "$safeName.apk"
    }

    private fun getOrStartDownload(context: Context, release: AppUpdateRelease): Deferred<File> {
        val key = "${release.downloadUrl}|${release.fileName}"
        synchronized(downloadLock) {
            activeDownload?.let { (activeKey, activeDeferred) ->
                if (activeKey == key && activeDeferred.isActive) return activeDeferred
            }

            val deferred = downloadScope.async {
                runCatching {
                    val apkFile = resolveApkFile(context, release)
                    if (apkFile.isUsableForRelease(context, release)) {
                        PLog.d(TAG, "Reuse downloaded APK: ${apkFile.absolutePath}, size=${apkFile.length()}")
                        return@runCatching apkFile
                    }

                    discardInvalidCachedApk(context, release, apkFile)
                    _state.value = AppUpdateState.Downloading(release, null)
                    downloadApkFile(release.downloadUrl, apkFile) { progress ->
                        _state.value = AppUpdateState.Downloading(release, progress)
                    }
                    if (!apkFile.isUsableForRelease(context, release)) {
                        throw IllegalStateException("Downloaded APK is invalid: ${apkFile.absolutePath}")
                    }
                    PLog.d(TAG, "APK downloaded: ${apkFile.absolutePath}, size=${apkFile.length()}")
                    apkFile
                }.onSuccess { apkFile ->
                    _state.value = AppUpdateState.Ready(release, apkFile)
                }.onFailure { error ->
                    PLog.e(TAG, "APK download error", error)
                    _state.value = AppUpdateState.Failed(release)
                }.getOrThrow()
            }
            activeDownload = key to deferred
            return deferred
        }
    }

    private fun resolveApkFile(context: Context, release: AppUpdateRelease): File {
        val updateDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, Environment.DIRECTORY_DOWNLOADS),
            "updates"
        ).apply { mkdirs() }
        return File(updateDir, release.fileName.sanitizeApkFileName())
    }

    private fun downloadApkFile(downloadUrl: String, apkFile: File, onProgress: (Float) -> Unit) {
        val partFile = File("${apkFile.absolutePath}.part")
        var connection: HttpURLConnection? = null
        try {
            val existingBytes = partFile.takeIf { it.exists() }?.length()?.coerceAtLeast(0L) ?: 0L
            connection = openDownloadConnection(downloadUrl, existingBytes)
            val responseCode = connection.responseCode

            if (responseCode == HTTP_RANGE_NOT_SATISFIABLE && apkFile.isCompleteApk()) {
                partFile.delete()
                return
            }

            val append = existingBytes > 0L && responseCode == HttpURLConnection.HTTP_PARTIAL
            if (responseCode !in listOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_PARTIAL)) {
                throw IllegalStateException("Download failed: code=$responseCode")
            }
            if (!append && partFile.exists()) {
                partFile.delete()
            }

            val startBytes = if (append) existingBytes else 0L
            val totalBytes = connection.contentLengthLong
                .takeIf { it > 0L }
                ?.let { startBytes + it }
            connection.inputStream.use { input ->
                FileOutputStream(partFile, append).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var written = startBytes
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (totalBytes != null) {
                            val percent = (written * 100 / totalBytes).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress((written.toFloat() / totalBytes).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }

            if (!partFile.isCompleteApk()) {
                throw IllegalStateException("Downloaded partial file is not a valid APK")
            }
            if (apkFile.exists()) apkFile.delete()
            if (!partFile.renameTo(apkFile)) {
                partFile.copyTo(apkFile, overwrite = true)
                partFile.delete()
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun openDownloadConnection(downloadUrl: String, existingBytes: Long): HttpURLConnection {
        var url = URL(downloadUrl)
        repeat(MAX_REDIRECTS) {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 0
                requestMethod = "GET"
                setRequestProperty("Accept", APK_MIME_TYPE)
                if (existingBytes > 0L) {
                    setRequestProperty("Range", "bytes=$existingBytes-")
                }
            }

            val responseCode = connection.responseCode
            if (responseCode in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) {
                    throw IllegalStateException("Download redirect missing location")
                }
                url = URL(url, location)
            } else {
                return connection
            }
        }
        throw IllegalStateException("Too many download redirects")
    }

    private fun File.isCompleteApk(): Boolean {
        if (!exists() || length() < 2L) return false
        return inputStream().use { input ->
            input.read().toByte() == APK_MAGIC_0 && input.read().toByte() == APK_MAGIC_1
        }
    }

    private fun File.isUsableForRelease(context: Context, release: AppUpdateRelease): Boolean {
        if (!isCompleteApk()) {
            PLog.d(TAG, "Cached APK missing or incomplete: $absolutePath")
            return false
        }

        val packageInfo = readArchivePackageInfo(context) ?: run {
            PLog.w(TAG, "Cached APK cannot be parsed: $absolutePath")
            return false
        }

        val expectedVersionName = release.versionName?.normalizedVersionName()
        if (expectedVersionName == null) {
            PLog.w(TAG, "Latest release version is missing, cached APK will not be reused")
            return false
        }

        val actualVersionName = packageInfo.versionName?.normalizedVersionName()
        if (actualVersionName != expectedVersionName) {
            PLog.w(
                TAG,
                "Cached APK version mismatch: expected=$expectedVersionName, actual=$actualVersionName"
            )
            return false
        }

        return true
    }

    @Suppress("DEPRECATION")
    private fun File.readArchivePackageInfo(context: Context): PackageInfo? =
        context.packageManager.getPackageArchiveInfo(absolutePath, 0)

    private fun String.normalizedVersionName(): String =
        trim().removePrefix("v").removePrefix("V")

    private fun discardInvalidCachedApk(context: Context, release: AppUpdateRelease, apkFile: File) {
        if (!apkFile.exists()) return
        if (apkFile.isUsableForRelease(context, release)) return

        runCatching {
            if (apkFile.delete()) {
                PLog.d(TAG, "Deleted invalid cached APK: ${apkFile.absolutePath}")
            }
        }.onFailure { error ->
            PLog.w(TAG, "Failed to delete invalid cached APK: ${apkFile.absolutePath}", error)
        }
    }
}
