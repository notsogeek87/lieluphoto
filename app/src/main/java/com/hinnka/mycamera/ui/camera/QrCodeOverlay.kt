package com.hinnka.mycamera.ui.camera

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.hinnka.mycamera.R
import com.hinnka.mycamera.utils.PLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "QrCodeOverlay"
private const val SCAN_INTERVAL_MS = 500L
private const val SCAN_MAX_LONG_EDGE = 1920
private const val BLUR_FALLBACK_DIVISOR = 3
private const val CAPTURE_TIMEOUT_MS = 1500L
private const val DECODE_TIMEOUT_MS = 3000L
private const val LOST_AFTER_MS = 1500L

/**
 * Détecte les QR codes dans l'aperçu et propose de copier le texte ou d'ouvrir le lien,
 * comme l'appareil photo de Samsung ou de Google.
 */
@Composable
fun QrCodeOverlay(
    glSurfaceView: CameraGLSurfaceView?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var detected by remember { mutableStateOf<String?>(null) }
    var dismissed by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(glSurfaceView, enabled) {
        if (glSurfaceView == null || !enabled) {
            detected = null
            return@LaunchedEffect
        }
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        )
        var lastSeen = 0L
        try {
            while (true) {
                delay(SCAN_INTERVAL_MS)
                val text = scanFrame(glSurfaceView, scanner)
                val now = System.currentTimeMillis()
                if (text != null) {
                    lastSeen = now
                    if (detected != text) detected = text
                } else if (detected != null && now - lastSeen > LOST_AFTER_MS) {
                    detected = null
                    dismissed = null
                }
            }
        } finally {
            scanner.close()
            detected = null
        }
    }

    val text = detected
    if (text == null || text == dismissed) return

    val url = remember(text) { text.toWebUrl() }
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Surface(
            color = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 360.dp)
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
                Text(
                    text = stringResource(R.string.qr_code_detected),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp, end = 8.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    TextButton(onClick = { dismissed = text }) {
                        Text(stringResource(R.string.close), color = Color.White.copy(alpha = 0.7f))
                    }
                    TextButton(onClick = { copyToClipboard(context, text) }) {
                        Text(stringResource(R.string.qr_code_copy), color = Color.White)
                    }
                    if (url != null) {
                        TextButton(onClick = { openInBrowser(context, url) }) {
                            Text(stringResource(R.string.qr_code_open), color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/** Capture l'aperçu courant et retourne le texte du premier QR code trouvé. */
private suspend fun scanFrame(
    glSurfaceView: CameraGLSurfaceView,
    scanner: BarcodeScanner,
): String? {
    // Le rendu peut ne jamais répondre (aperçu en pause) : sans délai la boucle resterait bloquée.
    val bitmap = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
        suspendCancellableCoroutine<Bitmap?> { cont ->
            glSurfaceView.capturePreviewFrame(SCAN_MAX_LONG_EDGE) { cont.resume(it) }
        }
    }
    if (bitmap == null) {
        PLog.w(TAG, "QR scan: no preview frame within ${CAPTURE_TIMEOUT_MS}ms")
        return null
    }
    try {
        decode(scanner, bitmap)?.let { return it }
        // Image floue (mise au point ailleurs) : réduite, les modules du QR code redeviennent lisibles.
        val small = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width / BLUR_FALLBACK_DIVISOR).coerceAtLeast(1),
            (bitmap.height / BLUR_FALLBACK_DIVISOR).coerceAtLeast(1),
            true
        )
        try {
            return decode(scanner, small)
        } finally {
            small.recycle()
        }
    } finally {
        bitmap.recycle()
    }
}

private suspend fun decode(scanner: BarcodeScanner, bitmap: Bitmap): String? =
    withTimeoutOrNull(DECODE_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            scanner.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { codes ->
                    cont.resume(codes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) })
                }
                .addOnFailureListener {
                    PLog.w(TAG, "QR scan failed: ${it.message}")
                    cont.resume(null)
                }
        }
    }

private fun String.toWebUrl(): String? {
    val value = trim()
    return value.takeIf {
        !it.any(Char::isWhitespace) &&
            (it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true))
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("QR", text))
    Toast.makeText(context, R.string.qr_code_copied, Toast.LENGTH_SHORT).show()
}

private fun openInBrowser(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, R.string.qr_code_open_failed, Toast.LENGTH_SHORT).show()
    }
}
