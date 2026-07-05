package com.youtroc.feature.playback.overlay

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure visibility state machine tests (REQ-11, docs/07 §10): no Compose/Android
 * involved, so this needs no device/emulator. Seeking is no longer this
 * reducer's concern (it moved onto the focused scrubber, see [DpadDecisionTest]);
 * this only covers reveal-on-activity and auto-hide-on-inactivity.
 */
class OverlayReducerTest {

    @Test
    fun `activity reveals regardless of prior state`() {
        assertEquals(OverlayState.Revealed(sinceMs = 2_000L), OverlayReducer.onActivity(nowMs = 2_000L))
    }

    @Test
    fun `hidden stays hidden on inactivity timeout`() {
        assertEquals(OverlayState.Hidden, OverlayReducer.onInactivityTimeout(OverlayState.Hidden, nowMs = 9_999L))
    }

    @Test
    fun `revealed stays revealed before the auto-hide timeout elapses`() {
        val revealed = OverlayState.Revealed(sinceMs = 1_000L)

        val result = OverlayReducer.onInactivityTimeout(revealed, nowMs = 1_000L + OverlayReducer.AUTO_HIDE_TIMEOUT_MS - 1)

        assertEquals(revealed, result)
    }

    @Test
    fun `revealed hides once the auto-hide timeout elapses`() {
        val revealed = OverlayState.Revealed(sinceMs = 1_000L)

        val result = OverlayReducer.onInactivityTimeout(revealed, nowMs = 1_000L + OverlayReducer.AUTO_HIDE_TIMEOUT_MS)

        assertEquals(OverlayState.Hidden, result)
    }
}
