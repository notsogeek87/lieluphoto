package com.hinnka.mycamera.processor

import org.junit.Assert.assertEquals
import org.junit.Test

class LuckyFrameSelectorTest {
    @Test
    fun withoutGyroTheChronologicalOrderIsKept() {
        assertEquals(listOf(0, 1, 2), LuckyFrameSelector.order(listOf(0.001f, null, 0.002f)))
    }

    @Test
    fun sharpestFrameBecomesReferenceAndBlurredOnesAreDropped() {
        // La première image (appui du doigt) est la plus floue ; l'image 3 est la plus nette.
        val blur = listOf(0.010f, 0.002f, 0.0021f, 0.0008f, 0.0019f, 0.0022f, 0.002f, 0.009f)
        val order = LuckyFrameSelector.order(blur)
        assertEquals(3, order.first())
        assertEquals(listOf(3, 1, 2, 4, 5, 6), order)
    }

    @Test
    fun alwaysKeepsAtLeastMinimumFrames() {
        val blur = listOf(0.001f, 0.02f, 0.03f, 0.04f, 0.05f, 0.06f)
        val order = LuckyFrameSelector.order(blur)
        assertEquals(LuckyFrameSelector.MIN_KEPT, order.size)
        assertEquals(0, order.first())
    }
}
