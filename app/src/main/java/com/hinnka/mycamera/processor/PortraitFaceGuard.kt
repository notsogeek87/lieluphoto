package com.hinnka.mycamera.processor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.hinnka.mycamera.ml.SubjectMask
import com.hinnka.mycamera.utils.PLog
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Mode Portrait : le flou d'arrière-plan ne doit jamais toucher le visage. La carte de profondeur et
 * le masque du sujet placent parfois la barbe et les bords du visage « derrière » (build 203 : barbe
 * -64 %, joue droite -76 % de netteté). Après le flou, on recopie l'image nette dans un ovale adouci
 * qui couvre le visage, le haut des cheveux et la barbe.
 *
 * L'ovale déborde du visage : sur un visage de trois quarts ou une tête étroite, il couvre aussi du
 * fond, qui restait net à côté de la tête. Le cadre détecté (front → menton) est toujours recopié ;
 * hors de ce cadre, la recopie suit la couverture du masque du sujet, si bien que le fond reste flou.
 */
internal object PortraitFaceGuard {
    private const val TAG = "PortraitFaceGuard"
    private const val DETECTOR_ASSET = "blaze_face_full_range.tflite"
    private const val DETECTION_MAX_DIMENSION = 1280
    private const val MIN_DETECTION_CONFIDENCE = 0.6f
    private const val MIN_FACE_FRACTION = 0.04f

    // Ovale autour du cadre détecté (front → menton) : élargi pour inclure le haut des cheveux, les
    // oreilles et la barbe, adouci sur ~25 % du rayon (vérifié sur un selfie Portrait du build 203).
    private const val RADIUS_X = 0.66f
    private const val RADIUS_Y = 0.86f
    private const val CENTER_SHIFT_Y = 0.0f
    private const val FEATHER_INNER = 0.82f
    private const val FEATHER_OUTER = 1.08f

    // Cœur toujours net : ellipse inscrite dans le cadre détecté, adoucie entre ces rayons relatifs.
    private const val CORE_INNER = 0.9f
    private const val CORE_OUTER = 1.1f

    /**
     * Recopie [sharp] dans [blurred] sur chaque visage, limité au sujet de [subjectMask] hors du cadre
     * du visage. [blurred] doit être modifiable.
     */
    fun restoreFaces(context: Context, sharp: Bitmap, blurred: Bitmap, subjectMask: SubjectMask): Int {
        if (sharp.width != blurred.width || sharp.height != blurred.height || !blurred.isMutable) return 0
        val faces = try {
            detectFaces(context, sharp)
        } catch (e: Throwable) {
            PLog.w(TAG, "Face detection failed, portrait left as is", e)
            return 0
        }
        for (face in faces) blendFace(sharp, blurred, face, subjectMask)
        if (faces.isNotEmpty()) PLog.d(TAG, "Portrait faces kept sharp: ${faces.size}")
        return faces.size
    }

    private fun detectFaces(context: Context, bitmap: Bitmap): List<RectF> {
        val source = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return emptyList()
        val scale = min(1f, DETECTION_MAX_DIMENSION.toFloat() / max(source.width, source.height))
        val small = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).roundToInt().coerceAtLeast(1),
                (source.height * scale).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            source
        }
        val options = FaceDetector.FaceDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setDelegate(Delegate.CPU).setModelAssetPath(DETECTOR_ASSET).build())
            .setMinDetectionConfidence(MIN_DETECTION_CONFIDENCE)
            .setRunningMode(RunningMode.IMAGE)
            .build()
        try {
            val detector = FaceDetector.createFromOptions(context, options)
            val result = try {
                detector.detect(BitmapImageBuilder(small).build())
            } finally {
                detector.close()
            }
            val toFull = 1f / scale
            return result.detections().mapNotNull { detection ->
                val box = detection.boundingBox()
                val rect = RectF(box.left * toFull, box.top * toFull, box.right * toFull, box.bottom * toFull)
                rect.takeIf { it.width() >= MIN_FACE_FRACTION * min(bitmap.width, bitmap.height) }
            }
        } finally {
            if (small !== source) small.recycle()
            if (source !== bitmap) source.recycle()
        }
    }

    private fun blendFace(sharp: Bitmap, blurred: Bitmap, face: RectF, subjectMask: SubjectMask) {
        val cx = face.centerX()
        val cy = face.centerY() + CENTER_SHIFT_Y * face.height()
        val rx = RADIUS_X * face.width()
        val ry = RADIUS_Y * face.height()
        val coreRx = 0.5f * face.width()
        val coreRy = 0.5f * face.height()
        val left = (cx - rx * FEATHER_OUTER).toInt().coerceIn(0, sharp.width)
        val right = (cx + rx * FEATHER_OUTER).toInt().coerceIn(0, sharp.width)
        val top = (cy - ry * FEATHER_OUTER).toInt().coerceIn(0, sharp.height)
        val bottom = (cy + ry * FEATHER_OUTER).toInt().coerceIn(0, sharp.height)
        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0) return
        val a = IntArray(width * height)
        val b = IntArray(width * height)
        sharp.getPixels(a, 0, width, left, top, width, height)
        blurred.getPixels(b, 0, width, left, top, width, height)
        for (y in 0 until height) {
            val dy = (top + y + 0.5f - cy) / ry
            for (x in 0 until width) {
                val dx = (left + x + 0.5f - cx) / rx
                val d = sqrt(dx * dx + dy * dy)
                if (d >= FEATHER_OUTER) continue
                val i = y * width + x
                val ovalWeight = smoothFalloff(d, FEATHER_INNER, FEATHER_OUTER)
                val px = left + x + 0.5f
                val py = top + y + 0.5f
                val cdx = (px - face.centerX()) / coreRx
                val cdy = (py - face.centerY()) / coreRy
                val coreWeight = smoothFalloff(sqrt(cdx * cdx + cdy * cdy), CORE_INNER, CORE_OUTER)
                val subjectWeight = if (coreWeight >= 1f) 1f else
                    max(coreWeight, subjectMask.sample(px / sharp.width, py / sharp.height))
                val w = ovalWeight * subjectWeight
                if (w <= 0f) continue
                if (w >= 1f) {
                    b[i] = a[i]
                    continue
                }
                val pa = a[i]
                val pb = b[i]
                val r = (((pa shr 16) and 0xFF) * w + ((pb shr 16) and 0xFF) * (1f - w)).roundToInt()
                val g = (((pa shr 8) and 0xFF) * w + ((pb shr 8) and 0xFF) * (1f - w)).roundToInt()
                val bl = ((pa and 0xFF) * w + (pb and 0xFF) * (1f - w)).roundToInt()
                b[i] = (pb and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or bl
            }
        }
        blurred.setPixels(b, 0, width, left, top, width, height)
    }

    /** 1 jusqu'à [inner], 0 à partir de [outer], transition smoothstep entre les deux. */
    private fun smoothFalloff(distance: Float, inner: Float, outer: Float): Float {
        if (distance <= inner) return 1f
        if (distance >= outer) return 0f
        val t = (outer - distance) / (outer - inner)
        return t * t * (3f - 2f * t)
    }
}
