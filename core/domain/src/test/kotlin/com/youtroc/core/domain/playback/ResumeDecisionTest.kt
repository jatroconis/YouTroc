package com.youtroc.core.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResumeDecisionTest {

    private val durationMs = 200_000L // 200s

    @Test
    fun `starts from zero when nothing was saved`() {
        assertNull(ResumeDecision.startAt(saved = null, durationMs = durationMs))
    }

    @Test
    fun `starts from zero when the saved position is under 10 seconds`() {
        val saved = PlaybackPosition(9_999L)

        assertNull(ResumeDecision.startAt(saved, durationMs))
    }

    @Test
    fun `resumes at exactly the 10 second threshold`() {
        val saved = PlaybackPosition(10_000L)

        assertEquals(saved, ResumeDecision.startAt(saved, durationMs))
    }

    @Test
    fun `starts from zero when the saved position is past 95 percent of the duration`() {
        val saved = PlaybackPosition(191_000L) // 95.5%

        assertNull(ResumeDecision.startAt(saved, durationMs))
    }

    @Test
    fun `starts from zero when the saved position is within the last 15 seconds`() {
        // 95% of 200_000ms is 190_000ms, so this position is NOT past the ratio
        // threshold but IS within the last 15s window (200_000 - 187_000 = 13_000ms).
        val saved = PlaybackPosition(187_000L)

        assertNull(ResumeDecision.startAt(saved, durationMs))
    }

    @Test
    fun `resumes from the saved position when comfortably mid-video`() {
        val saved = PlaybackPosition(50_000L)

        assertEquals(saved, ResumeDecision.startAt(saved, durationMs))
    }
}
