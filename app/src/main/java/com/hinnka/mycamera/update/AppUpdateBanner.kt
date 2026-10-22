package com.hinnka.mycamera.update

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R

/**
 * Bandeau affiché dès l'ouverture quand une release plus récente existe, comme dans LieluFind :
 * « Mettre à jour » télécharge l'APK avec sa progression, puis lance l'installeur système.
 */
@Composable
fun AppUpdateBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by AppUpdateManager.state.collectAsState()
    var dismissed by rememberSaveable { mutableStateOf(false) }
    var installWhenReady by rememberSaveable { mutableStateOf(false) }

    fun install(apkFile: java.io.File) {
        if (!AppUpdateManager.startInstall(context, apkFile)) {
            Toast.makeText(context, R.string.update_install_permission_hint, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(state) {
        val ready = state as? AppUpdateState.Ready ?: return@LaunchedEffect
        if (installWhenReady) {
            installWhenReady = false
            install(ready.apkFile)
        }
    }

    val current = state
    if (dismissed || current is AppUpdateState.None) return

    val message: String
    var progress: Float? = null
    var showProgress = false
    var action: Pair<String, () -> Unit>? = null
    when (current) {
        is AppUpdateState.Available -> {
            message = stringResource(
                R.string.update_banner_available,
                current.release.versionName.orEmpty(),
                current.release.buildNumber
            )
            action = stringResource(R.string.update_banner_download) to {
                installWhenReady = true
                AppUpdateManager.startDownload(context, current.release)
            }
        }

        is AppUpdateState.Downloading -> {
            message = current.progress
                ?.let { stringResource(R.string.update_banner_downloading, (it * 100).toInt()) }
                ?: stringResource(R.string.update_banner_downloading_unknown)
            progress = current.progress
            showProgress = true
        }

        is AppUpdateState.Ready -> {
            message = stringResource(
                R.string.update_banner_ready,
                current.release.versionName.orEmpty(),
                current.release.buildNumber
            )
            action = stringResource(R.string.update_install_now) to { install(current.apkFile) }
        }

        is AppUpdateState.Failed -> {
            message = stringResource(R.string.update_banner_failed)
            action = stringResource(R.string.update_banner_retry) to {
                installWhenReady = true
                AppUpdateManager.startDownload(context, current.release)
            }
        }

        AppUpdateState.None -> return
    }

    Surface(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF2A2A2A),
        contentColor = Color.White,
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_material_download_24),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                action?.let { (label, onClick) ->
                    TextButton(onClick = onClick) {
                        Text(text = label, color = MaterialTheme.colorScheme.primary)
                    }
                }
                IconButton(onClick = { dismissed = true }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.update_banner_dismiss)
                    )
                }
            }
            if (showProgress) {
                val modifierProgress = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp, bottom = 8.dp)
                val fraction = progress
                if (fraction != null) {
                    LinearProgressIndicator(progress = { fraction }, modifier = modifierProgress)
                } else {
                    LinearProgressIndicator(modifier = modifierProgress)
                }
            }
        }
    }
}
