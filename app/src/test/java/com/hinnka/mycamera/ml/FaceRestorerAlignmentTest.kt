package com.hinnka.mycamera.ml

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceRestorerAlignmentTest {
    @Test
    fun similarityMapsScaledAndShiftedPointsBack() {
        val dst = floatArrayOf(10f, 10f, 30f, 10f, 20f, 25f, 20f, 35f)
        // Mêmes points, ×2 et décalés : la similitude doit diviser par 2 et recentrer.
        val src = FloatArray(dst.size) { i -> dst[i] * 2f + if (i % 2 == 0) 100f else 50f }
        val m = FaceRestorer.estimateSimilarity(src, dst)
        assertEquals(0.5f, m[0], 1e-4f)
        assertEquals(0f, m[1], 1e-4f)
        assertEquals(-50f, m[2], 1e-3f)
        assertEquals(0f, m[3], 1e-4f)
        assertEquals(0.5f, m[4], 1e-4f)
        assertEquals(-25f, m[5], 1e-3f)
    }
}
