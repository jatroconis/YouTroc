package com.youtroc.feature.playback.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Exercises [ScrubSeekSync]'s pure decisions (pending-seek latch): the scrub
 * bar must never fall back to a stale engine position while a committed seek
 * is still in flight, and a follow-up scrub must continue FROM the committed
 * target instead of the stale engine position.
 */
class ScrubSeekSyncTest {

    @Test
    fun `display prefers the active preview over everything`() {
        assertEquals(
            120_000L,
            ScrubSeekSync.displayPosition(previewMs = 120_000L, pendingMs = 90_000L, enginePositionMs = 60_000L),
        )
    }

    @Test
    fun `display holds the pending target while the engine lags behind`() {
        assertEquals(
            90_000L,
            ScrubSeekSync.displayPosition(previewMs = null, pendingMs = 90_000L, enginePositionMs = 60_000L),
        )
    }

    @Test
    fun `display follows the engine once nothing is pending`() {
        assertEquals(
            60_000L,
            ScrubSeekSync.displayPosition(previewMs = null, pendingMs = null, enginePositionMs = 60_000L),
        )
    }

    @Test
    fun `pending clears once the engine reaches the target`() {
        assertNull(ScrubSeekSync.resolvePending(pendingMs = 90_000L, enginePositionMs = 90_000L))
        assertNull(ScrubSeekSync.resolvePending(pendingMs = 90_000L, enginePositionMs = 90_400L))
        assertNull(ScrubSeekSync.resolvePending(pendingMs = 90_000L, enginePositionMs = 89_700L))
    }

    @Test
    fun `pending survives stale engine positions in both directions`() {
        // Forward seek: engine still reports the old (earlier) position.
        assertEquals(90_000L, ScrubSeekSync.resolvePending(pendingMs = 90_000L, enginePositionMs = 60_000L))
        // Backward seek: engine still reports the old (later) position.
        assertEquals(30_000L, ScrubSeekSync.resolvePending(pendingMs = 30_000L, enginePositionMs = 60_000L))
    }

    @Test
    fun `no pending resolves to no pending`() {
        assertNull(ScrubSeekSync.resolvePending(pendingMs = null, enginePositionMs = 60_000L))
    }
}
