package com.hinnka.mycamera.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiEnhanceBlendTest {
    @Test
    fun lowIsoKeepsMostOfTheOriginal() {
        assertEquals(0.35f, aiEnhanceBlendStrength(100), 1e-4f)
        assertEquals(0.35f, aiEnhanceBlendStrength(200), 1e-4f)
    }

    @Test
    fun highIsoDenoisesFully() {
        assertEquals(0.8f, aiEnhanceBlendStrength(800), 1e-4f)
        assertEquals(0.8f, aiEnhanceBlendStrength(3200), 1e-4f)
        // ISO inconnu : traité comme une scène bruitée plutôt que de laisser le bruit.
        assertEquals(0.8f, aiEnhanceBlendStrength(null), 1e-4f)
    }

    @Test
    fun strengthGrowsWithIso() {
        val mid = aiEnhanceBlendStrength(400)
        assertTrue(mid > 0.35f && mid < 0.8f)
        assertEquals(0.575f, mid, 1e-3f)
    }
}
