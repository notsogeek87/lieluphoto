package com.hinnka.mycamera.ml

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Netteté finale de l'amélioration IA : masque flou (unsharp) sur la luminance, appliqué seulement
 * aux vrais contours. Les faibles variations (bruit, peau, zones unies) sont laissées telles quelles
 * grâce à un seuil progressif, et l'accentuation est bornée pour éviter les halos.
 *
 * Validé hors téléphone sur des selfies de l'app (build 189) : netteté du visage ×2 (45,6 → 100),
 * bruit des zones unies quasi inchangé (+2 à 5 %), sans halo visible (barbe, cils, lunettes).
 */
internal object AiSharpener {
    private const val SIGMA = 1.2f
    // 1,3 (au lieu de 0,8) : build 202, visage 97 contre 148 chez Samsung ; 1,3 vise ~160, sans halo
    // ni grain visible sur la peau (vérifié sur le selfie à lunettes).
    private const val AMOUNT = 1.3f
    private const val THRESHOLD_LOW = 2f
    private const val THRESHOLD_HIGH = 6f
    private const val MAX_DELTA = 24f
    private const val STRIP_ROWS = 256

    fun sharpen(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 8 || height < 8) return bitmap
        val output = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return bitmap
        val radius = ceil(3f * SIGMA).toInt()
        val kernel = FloatArray(2 * radius + 1) { i ->
            val k = (i - radius).toFloat()
            exp(-(k * k) / (2f * SIGMA * SIGMA))
        }
        val kernelSum = kernel.sum()
        for (i in kernel.indices) kernel[i] /= kernelSum

        // Traitement par bandes (avec marge du rayon) pour limiter la mémoire sur 10 à 50 Mpx.
        var y0 = 0
        while (y0 < height) {
            val y1 = min(height, y0 + STRIP_ROWS)
            val top = max(0, y0 - radius)
            val bottom = min(height, y1 + radius)
            val rows = bottom - top
            val pixels = IntArray(width * rows)
            output.getPixels(pixels, 0, width, 0, top, width, rows)
            val luma = FloatArray(width * rows)
            for (i in pixels.indices) {
                val p = pixels[i]
                luma[i] = 0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)
            }
            val horizontal = FloatArray(width * rows)
            for (r in 0 until rows) {
                val base = r * width
                for (x in 0 until width) {
                    var sum = 0f
                    for (k in -radius..radius) {
                        val sx = (x + k).coerceIn(0, width - 1)
                        sum += luma[base + sx] * kernel[k + radius]
                    }
                    horizontal[base + x] = sum
                }
            }
            val result = IntArray(width * (y1 - y0))
            for (y in y0 until y1) {
                val r = y - top
                for (x in 0 until width) {
                    var blurred = 0f
                    for (k in -radius..radius) {
                        val sr = (r + k).coerceIn(0, rows - 1)
                        blurred += horizontal[sr * width + x] * kernel[k + radius]
                    }
                    val index = r * width + x
                    val detail = luma[index] - blurred
                    val t = ((abs(detail) - THRESHOLD_LOW) / (THRESHOLD_HIGH - THRESHOLD_LOW)).coerceIn(0f, 1f)
                    val weight = t * t * (3f - 2f * t)
                    val delta = (AMOUNT * weight * detail).coerceIn(-MAX_DELTA, MAX_DELTA)
                    val p = pixels[index]
                    result[(y - y0) * width + x] = if (delta == 0f) {
                        p
                    } else {
                        val d = delta.roundToInt()
                        (p and 0xFF000000.toInt()) or
                            ((((p shr 16) and 0xFF) + d).coerceIn(0, 255) shl 16) or
                            ((((p shr 8) and 0xFF) + d).coerceIn(0, 255) shl 8) or
                            ((p and 0xFF) + d).coerceIn(0, 255)
                    }
                }
            }
            output.setPixels(result, 0, width, 0, y0, width, y1 - y0)
            y0 = y1
        }
        return output
    }
}
