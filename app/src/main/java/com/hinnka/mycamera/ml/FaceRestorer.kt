package com.hinnka.mycamera.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.hinnka.mycamera.utils.PLog
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate

/**
 * Restauration des visages avec GFPGAN v1.4 (validé hors téléphone sur des selfies de l'app) :
 * détection MediaPipe (yeux, nez, bouche) → alignement par similitude sur le gabarit FFHQ 512 →
 * inférence → recollage avec un masque elliptique adouci, mélangé à 70 % avec l'original. Pour les
 * visages plus grands que 512 px, GFPGAN lisse la peau : on réinjecte alors les hautes fréquences de
 * l'original pour garder le grain et les cils.
 */
internal object FaceRestorer {
    private const val TAG = "FaceRestorer"
    private const val DETECTOR_ASSET = "blaze_face_full_range.tflite"
    private const val FACE_SIZE = 512
    private const val DETECTION_MAX_DIMENSION = 1280
    private const val MIN_DETECTION_CONFIDENCE = 0.6f
    private const val MIN_EYE_DISTANCE_PX = 24f
    private const val MAX_FACES = 4
    private const val BLEND_STRENGTH = 0.7f

    // Gabarit FFHQ 512 : œil gauche, œil droit, nez, centre de la bouche (moyenne des commissures).
    private val TEMPLATE = floatArrayOf(
        192.98138f, 239.94708f,
        318.90277f, 240.1936f,
        256.63416f, 314.01935f,
        257.17511f, 371.2808f,
    )
    private val TEMPLATE_EYE_DISTANCE = hypot(318.90277f - 192.98138f, 240.1936f - 239.94708f)

    // Masque elliptique (repère du gabarit) adouci sur ~25 px, comme dans le test hors téléphone.
    private const val MASK_CENTER_X = 256f
    private const val MASK_CENTER_Y = 280f
    private const val MASK_RADIUS_X = 190f
    private const val MASK_RADIUS_Y = 230f
    private const val MASK_FEATHER = 0.13f

    private class Face(val points: FloatArray, val eyeDistance: Float)

    fun restore(context: Context, modelFile: File, bitmap: Bitmap): Bitmap {
        val source = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return bitmap
        val faces = detectFaces(context, source)
        if (faces.isEmpty()) {
            if (source !== bitmap) source.recycle()
            PLog.d(TAG, "No face to restore")
            return bitmap
        }
        val interpreter = createInterpreter(modelFile) ?: run {
            if (source !== bitmap) source.recycle()
            return bitmap
        }
        val output = source.copy(Bitmap.Config.ARGB_8888, true) ?: run {
            interpreter.close()
            if (source !== bitmap) source.recycle()
            return bitmap
        }
        try {
            val inputShape = interpreter.instance.getInputTensor(0).shape()
            val channelsFirst = inputShape.size == 4 && inputShape[1] == 3
            for (face in faces) {
                val start = System.currentTimeMillis()
                restoreFace(interpreter.instance, channelsFirst, source, output, face)
                PLog.d(
                    TAG,
                    "Face restored: eyes=${face.eyeDistance.roundToInt()}px " +
                        "backend=${interpreter.backend} ${System.currentTimeMillis() - start}ms"
                )
            }
        } finally {
            interpreter.close()
            if (source !== bitmap) source.recycle()
        }
        return output
    }

    private fun detectFaces(context: Context, bitmap: Bitmap): List<Face> {
        val scale = min(1f, DETECTION_MAX_DIMENSION.toFloat() / max(bitmap.width, bitmap.height))
        val detectionBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        val options = FaceDetector.FaceDetectorOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setDelegate(Delegate.CPU)
                    .setModelAssetPath(DETECTOR_ASSET)
                    .build()
            )
            .setMinDetectionConfidence(MIN_DETECTION_CONFIDENCE)
            .setRunningMode(RunningMode.IMAGE)
            .build()
        return try {
            val detector = FaceDetector.createFromOptions(context, options)
            val result = try {
                detector.detect(BitmapImageBuilder(detectionBitmap).build())
            } finally {
                detector.close()
            }
            run {
                result.detections().mapNotNull { detection ->
                    val keypoints = detection.keypoints().orElse(null) ?: return@mapNotNull null
                    if (keypoints.size < 4) return@mapNotNull null
                    val w = bitmap.width.toFloat()
                    val h = bitmap.height.toFloat()
                    val eyeA = keypoints[0]
                    val eyeB = keypoints[1]
                    val (leftEye, rightEye) = if (eyeA.x() <= eyeB.x()) eyeA to eyeB else eyeB to eyeA
                    val points = floatArrayOf(
                        leftEye.x() * w, leftEye.y() * h,
                        rightEye.x() * w, rightEye.y() * h,
                        keypoints[2].x() * w, keypoints[2].y() * h,
                        keypoints[3].x() * w, keypoints[3].y() * h,
                    )
                    if (points.any { !it.isFinite() }) return@mapNotNull null
                    val eyeDistance = hypot(points[2] - points[0], points[3] - points[1])
                    if (eyeDistance < MIN_EYE_DISTANCE_PX) return@mapNotNull null
                    Face(points, eyeDistance)
                }.sortedByDescending { it.eyeDistance }.take(MAX_FACES)
            }
        } catch (e: RuntimeException) {
            PLog.e(TAG, "Face detection failed", e)
            emptyList()
        } finally {
            if (detectionBitmap !== bitmap) detectionBitmap.recycle()
        }
    }

    private class LoadedInterpreter(
        val instance: Interpreter,
        val backend: String,
        private val gpuDelegate: GpuDelegate?,
    ) {
        fun close() {
            instance.close()
            gpuDelegate?.close()
        }
    }

    private fun createInterpreter(modelFile: File): LoadedInterpreter? {
        val model = RandomAccessFile(modelFile, "r").use { file ->
            file.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
        }
        try {
            val compatList = CompatibilityList()
            if (compatList.isDelegateSupportedOnThisDevice) {
                val delegate = GpuDelegate(compatList.bestOptionsForThisDevice)
                try {
                    val interpreter = Interpreter(model, Interpreter.Options().addDelegate(delegate))
                    return LoadedInterpreter(interpreter, "GPU", delegate)
                } catch (e: Exception) {
                    PLog.w(TAG, "GPU delegate unavailable for GFPGAN, falling back to CPU", e)
                    delegate.close()
                }
            }
        } catch (e: Throwable) {
            PLog.w(TAG, "GPU check failed for GFPGAN", e)
        }
        return try {
            val options = Interpreter.Options().apply {
                setNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 8))
                setUseXNNPACK(true)
            }
            LoadedInterpreter(Interpreter(model, options), "CPU", null)
        } catch (e: Exception) {
            PLog.e(TAG, "Failed to load GFPGAN", e)
            null
        }
    }

    /** Similitude (rotation + échelle uniforme + translation) aux moindres carrés : image → gabarit. */
    internal fun estimateSimilarity(src: FloatArray, dst: FloatArray): FloatArray {
        val n = src.size / 2
        var sx = 0f; var sy = 0f; var dx = 0f; var dy = 0f
        for (i in 0 until n) {
            sx += src[2 * i]; sy += src[2 * i + 1]; dx += dst[2 * i]; dy += dst[2 * i + 1]
        }
        sx /= n; sy /= n; dx /= n; dy /= n
        var norm = 0f; var a = 0f; var b = 0f
        for (i in 0 until n) {
            val x = src[2 * i] - sx
            val y = src[2 * i + 1] - sy
            val u = dst[2 * i] - dx
            val v = dst[2 * i + 1] - dy
            norm += x * x + y * y
            a += x * u + y * v
            b += x * v - y * u
        }
        if (norm <= 0f) return floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f)
        a /= norm
        b /= norm
        // [a -b tx; b a ty]
        val tx = dx - (a * sx - b * sy)
        val ty = dy - (b * sx + a * sy)
        return floatArrayOf(a, -b, tx, b, a, ty)
    }

    private fun restoreFace(
        interpreter: Interpreter,
        channelsFirst: Boolean,
        source: Bitmap,
        output: Bitmap,
        face: Face,
    ) {
        val m = estimateSimilarity(face.points, TEMPLATE)
        val scale = sqrt(m[0] * m[0] + m[3] * m[3])
        if (scale <= 0f) return
        val toTemplate = Matrix().apply { setValues(floatArrayOf(m[0], m[1], m[2], m[3], m[4], m[5], 0f, 0f, 1f)) }

        // 1. Visage aligné 512×512.
        val aligned = Bitmap.createBitmap(FACE_SIZE, FACE_SIZE, Bitmap.Config.ARGB_8888)
        Canvas(aligned).drawBitmap(source, toTemplate, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        val alignedPixels = IntArray(FACE_SIZE * FACE_SIZE)
        aligned.getPixels(alignedPixels, 0, FACE_SIZE, 0, 0, FACE_SIZE, FACE_SIZE)
        aligned.recycle()

        // 2. Inférence, RGB [-1, 1].
        val plane = FACE_SIZE * FACE_SIZE
        val input = ByteBuffer.allocateDirect(plane * 3 * 4).order(ByteOrder.nativeOrder())
        val inputFloats = input.asFloatBuffer()
        for (i in 0 until plane) {
            val p = alignedPixels[i]
            val r = ((p shr 16) and 0xFF) / 127.5f - 1f
            val g = ((p shr 8) and 0xFF) / 127.5f - 1f
            val b = (p and 0xFF) / 127.5f - 1f
            if (channelsFirst) {
                inputFloats.put(i, r); inputFloats.put(plane + i, g); inputFloats.put(2 * plane + i, b)
            } else {
                inputFloats.put(3 * i, r); inputFloats.put(3 * i + 1, g); inputFloats.put(3 * i + 2, b)
            }
        }
        val outputBuffer = ByteBuffer.allocateDirect(plane * 3 * 4).order(ByteOrder.nativeOrder())
        interpreter.run(input, outputBuffer)
        val outFloats = outputBuffer.apply { rewind() }.asFloatBuffer()
        val restored = FloatArray(plane * 3)
        for (i in 0 until plane) {
            for (c in 0 until 3) {
                val value = if (channelsFirst) outFloats.get(c * plane + i) else outFloats.get(3 * i + c)
                restored[3 * i + c] = ((value + 1f) * 127.5f).coerceIn(0f, 255f)
            }
        }

        // 3. Zone de l'image couverte par le carré du gabarit.
        val inverse = Matrix().also { toTemplate.invert(it) }
        val corners = floatArrayOf(0f, 0f, FACE_SIZE.toFloat(), 0f, 0f, FACE_SIZE.toFloat(), FACE_SIZE.toFloat(), FACE_SIZE.toFloat())
        inverse.mapPoints(corners)
        val left = floor(minOf(corners[0], corners[2], corners[4], corners[6])).toInt().coerceIn(0, source.width)
        val top = floor(minOf(corners[1], corners[3], corners[5], corners[7])).toInt().coerceIn(0, source.height)
        val right = ceil(maxOf(corners[0], corners[2], corners[4], corners[6])).toInt().coerceIn(0, source.width)
        val bottom = ceil(maxOf(corners[1], corners[3], corners[5], corners[7])).toInt().coerceIn(0, source.height)
        val regionW = right - left
        val regionH = bottom - top
        if (regionW <= 0 || regionH <= 0) return

        val original = IntArray(regionW * regionH)
        source.getPixels(original, 0, regionW, left, top, regionW, regionH)
        // Hautes fréquences de l'original quand le visage dépasse 512 px (sigma ∝ échelle).
        val imageScale = 1f / scale
        val blurred = if (imageScale > 1f) gaussianBlur(original, regionW, regionH, 0.5f * imageScale) else null

        val result = original.copyOf()
        val a = m[0]; val bm = m[1]; val tx = m[2]; val c = m[3]; val d = m[4]; val ty = m[5]
        for (y in 0 until regionH) {
            val iy = (top + y) + 0.5f
            for (x in 0 until regionW) {
                val ix = (left + x) + 0.5f
                // Repère du gabarit (centres de pixels).
                val fx = a * ix + bm * iy + tx - 0.5f
                val fy = c * ix + d * iy + ty - 0.5f
                val ex = (fx - MASK_CENTER_X) / MASK_RADIUS_X
                val ey = (fy - MASK_CENTER_Y) / MASK_RADIUS_Y
                val dist = sqrt(ex * ex + ey * ey)
                val mask = smoothMask(dist)
                if (mask <= 0f) continue
                if (fx < 0f || fy < 0f || fx > FACE_SIZE - 1f || fy > FACE_SIZE - 1f) continue
                val idx = y * regionW + x
                val alpha = mask * BLEND_STRENGTH
                val orig = original[idx]
                val blur = blurred?.get(idx)
                var packed = orig and 0xFF000000.toInt()
                for (ch in 0 until 3) {
                    val shift = 16 - 8 * ch
                    val o = ((orig shr shift) and 0xFF).toFloat()
                    var r = sampleBilinear(restored, fx, fy, ch)
                    if (blur != null) r += o - ((blur shr shift) and 0xFF)
                    val value = (o * (1f - alpha) + r.coerceIn(0f, 255f) * alpha).roundToInt().coerceIn(0, 255)
                    packed = packed or (value shl shift)
                }
                result[idx] = packed
            }
        }
        output.setPixels(result, 0, regionW, left, top, regionW, regionH)
    }

    private fun smoothMask(dist: Float): Float {
        val inner = 1f - MASK_FEATHER
        val outer = 1f + MASK_FEATHER
        if (dist <= inner) return 1f
        if (dist >= outer) return 0f
        val t = (outer - dist) / (outer - inner)
        return t * t * (3f - 2f * t)
    }

    private fun sampleBilinear(data: FloatArray, x: Float, y: Float, channel: Int): Float {
        val x0 = x.toInt().coerceIn(0, FACE_SIZE - 1)
        val y0 = y.toInt().coerceIn(0, FACE_SIZE - 1)
        val x1 = min(x0 + 1, FACE_SIZE - 1)
        val y1 = min(y0 + 1, FACE_SIZE - 1)
        val wx = x - x0
        val wy = y - y0
        val v00 = data[3 * (y0 * FACE_SIZE + x0) + channel]
        val v10 = data[3 * (y0 * FACE_SIZE + x1) + channel]
        val v01 = data[3 * (y1 * FACE_SIZE + x0) + channel]
        val v11 = data[3 * (y1 * FACE_SIZE + x1) + channel]
        return (v00 * (1 - wx) + v10 * wx) * (1 - wy) + (v01 * (1 - wx) + v11 * wx) * wy
    }

    /** Flou gaussien séparable sur des pixels ARGB (bords répliqués). */
    private fun gaussianBlur(pixels: IntArray, width: Int, height: Int, sigma: Float): IntArray {
        val radius = ceil(3f * sigma).toInt().coerceAtLeast(1)
        val kernel = FloatArray(2 * radius + 1) { i ->
            val k = (i - radius).toFloat()
            exp(-(k * k) / (2f * sigma * sigma))
        }
        val sum = kernel.sum()
        for (i in kernel.indices) kernel[i] /= sum
        val temp = IntArray(pixels.size)
        val out = IntArray(pixels.size)
        blurPass(pixels, temp, width, height, kernel, radius, horizontal = true)
        blurPass(temp, out, width, height, kernel, radius, horizontal = false)
        return out
    }

    private fun blurPass(
        src: IntArray,
        dst: IntArray,
        width: Int,
        height: Int,
        kernel: FloatArray,
        radius: Int,
        horizontal: Boolean,
    ) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0f; var g = 0f; var b = 0f
                for (k in -radius..radius) {
                    val sx = if (horizontal) (x + k).coerceIn(0, width - 1) else x
                    val sy = if (horizontal) y else (y + k).coerceIn(0, height - 1)
                    val p = src[sy * width + sx]
                    val w = kernel[k + radius]
                    r += ((p shr 16) and 0xFF) * w
                    g += ((p shr 8) and 0xFF) * w
                    b += (p and 0xFF) * w
                }
                dst[y * width + x] = (0xFF shl 24) or
                    (r.roundToInt().coerceIn(0, 255) shl 16) or
                    (g.roundToInt().coerceIn(0, 255) shl 8) or
                    b.roundToInt().coerceIn(0, 255)
            }
        }
    }
}
