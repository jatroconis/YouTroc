package com.youtroc.feature.playback.overlay

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackTimeFormatterTest {

    @Test
    fun `formats zero as 0 colon 00`() {
        assertEquals("0:00", PlaybackTimeFormatter.format(0L))
    }

    @Test
    fun `formats seconds under a minute`() {
        assertEquals("0:09", PlaybackTimeFormatter.format(9_000L))
    }

    @Test
    fun `formats minutes and seconds`() {
        assertEquals("12:34", PlaybackTimeFormatter.format(754_000L))
    }

    @Test
    fun `formats hours minutes and seconds once past an hour`() {
        assertEquals("1:02:03", PlaybackTimeFormatter.format(3_723_000L))
    }

    @Test
    fun `clamps negative values to zero`() {
        assertEquals("0:00", PlaybackTimeFormatter.format(-500L))
    }
}
