package com.hinnka.mycamera.ui.camera

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "QrCodeOverlay"
private const val SCAN_INTERVAL_MS = 300L
private const val SCAN_MAX_LONG_EDGE = 1920
private const val CAPTURE_TIMEOUT_MS = 1500L
private const val DECODE_TIMEOUT_MS = 3000L
private const val LOST_AFTER_MS = 3000L

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
            var pass = 0
            while (true) {
                delay(SCAN_INTERVAL_MS)
                val variants = ScanVariant.entries
                val text = scanFrame(glSurfaceView, scanner, variants[pass++ % variants.size])
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
    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
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

/**
 * Façons d'analyser une même image, essayées tour à tour (une par passage, pour rester léger) :
 * ML Kit lit mal les codes clairs sur fond sombre (inversés) et les codes flous ; l'image
 * réduite rend les modules d'un code défocalisé à nouveau lisibles.
 */
private enum class ScanVariant(val divisor: Int, val inverted: Boolean) {
    FULL(1, false),
    FULL_INVERTED(1, true),
    HALF(2, false),
    THIRD_INVERTED(3, true),
    THIRD(3, false),
}

/** Capture l'aperçu courant et retourne le texte du premier QR code trouvé. */
private suspend fun scanFrame(
    glSurfaceView: CameraGLSurfaceView,
    scanner: BarcodeScanner,
    variant: ScanVariant,
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
    val prepared = withContext(Dispatchers.Default) { prepare(bitmap, variant) }
    try {
        return decode(scanner, prepared)
    } finally {
        if (prepared !== bitmap) prepared.recycle()
        bitmap.recycle()
    }
}

private fun prepare(source: Bitmap, variant: ScanVariant): Bitmap {
    if (variant.divisor == 1 && !variant.inverted) return source
    val width = (source.width / variant.divisor).coerceAtLeast(1)
    val height = (source.height / variant.divisor).coerceAtLeast(1)
    val scaled = if (variant.divisor == 1) source else Bitmap.createScaledBitmap(source, width, height, true)
    if (!variant.inverted) return scaled
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val invert = ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
    Canvas(result).drawBitmap(scaled, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(invert) })
    if (scaled !== source) scaled.recycle()
    return result
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
