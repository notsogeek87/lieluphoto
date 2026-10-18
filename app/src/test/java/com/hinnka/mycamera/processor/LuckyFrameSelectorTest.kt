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
    fun singleShakenFrameIsDroppedOthersKept() {
        val blur = listOf(0.0011f, 0.001f, 0.0012f, 0.0013f, 0.0014f, 0.1f)
        assertEquals(listOf(1, 0, 2, 3, 4), LuckyFrameSelector.order(blur))
    }

    @Test
    fun uniformlySharpBurstKeepsEveryFrame() {
        val blur = listOf(0.0002f, 0.0001f, 0.0004f, 0.0003f, 0.00045f, 0.0002f)
        assertEquals(listOf(1, 0, 2, 3, 4, 5), LuckyFrameSelector.order(blur))
    }
}
