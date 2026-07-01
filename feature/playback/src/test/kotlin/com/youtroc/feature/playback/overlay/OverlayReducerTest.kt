package com.youtroc.feature.playback.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pure reveal-then-seek state machine tests (REQ-11, docs/07 §10): no
 * Compose/Android involved, so this needs no device/emulator.
 */
class OverlayReducerTest {

    @Test
    fun `first L R press while hidden reveals without seeking`() {
        val transition = OverlayReducer.onDirectionalKey(OverlayState.Hidden, SeekDirection.Forward, nowMs = 1_000L)

        assertEquals(OverlayState.Revealed(sinceMs = 1_000L), transition.nextState)
        assertNull(transition.seek)
    }

    @Test
    fun `second L R press while revealed seeks`() {
        val revealed = OverlayState.Revealed(sinceMs = 1_000L)

        val transition = OverlayReducer.onDirectionalKey(revealed, SeekDirection.Backward, nowMs = 1_500L)

        assertEquals(SeekDirection.Backward, transition.seek)
        assertEquals(OverlayState.Revealed(sinceMs = 1_500L), transition.nextState)
    }

    @Test
    fun `activity reveals without seeking regardless of prior state`() {
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
