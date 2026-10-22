package com.hinnka.mycamera.update

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class AppUpdateRelease(
    val versionName: String?,
    val buildNumber: Int,
    val downloadUrl: String,
    val fileName: String
)

sealed interface AppUpdateState {
    data object None : AppUpdateState
    data class Available(val release: AppUpdateRelease) : AppUpdateState
    data class Downloading(val release: AppUpdateRelease, val progress: Float?) : AppUpdateState
    data class Ready(val release: AppUpdateRelease, val apkFile: File) : AppUpdateState
    data class Failed(val release: AppUpdateRelease) : AppUpdateState
}

object AppUpdateManager {
    val state: StateFlow<AppUpdateState> = MutableStateFlow(AppUpdateState.None)

    suspend fun checkForUpdate(currentVersion: String = ""): AppUpdateRelease? = null

    fun checkOnLaunch(context: Context) = Unit

    fun startDownload(context: Context, release: AppUpdateRelease) = Unit

    suspend fun downloadApk(context: Context, release: AppUpdateRelease): File {
        throw UnsupportedOperationException("App updates are disabled for the Google flavor.")
    }

    fun startInstall(context: Context, apkFile: File): Boolean = false
}
