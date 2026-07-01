package com.youtroc.feature.playback.overlay

import kotlin.test.Test
import kotlin.test.assertEquals

class SeekAmountTest {

    @Test
    fun `a single press steps 10 seconds`() {
        assertEquals(10_000L, SeekAmount.forPress(repeatCount = 0))
    }

    @Test
    fun `the first long-press repeat accelerates past the single step`() {
        assertEquals(15_000L, SeekAmount.forPress(repeatCount = 1))
    }

    @Test
    fun `later repeats keep accelerating`() {
        assertEquals(25_000L, SeekAmount.forPress(repeatCount = 3))
    }

    @Test
    fun `acceleration is capped so fast-seek never overshoots wildly`() {
        assertEquals(60_000L, SeekAmount.forPress(repeatCount = 50))
    }
}
