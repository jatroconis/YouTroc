package com.youtroc.feature.playback.overlay

import kotlin.math.abs

/**
 * Pure pending-seek latch for the decoupled scrub (`PlayerOverlay`).
 *
 * Committing a scrub hands the engine an absolute seek, but the engine keeps
 * REPORTING its old position until the seek is processed (and, on a slow
 * network, keeps re-buffering at it) — so a scrub bar that falls back to the
 * raw engine position the moment the preview commits visibly SNAPS BACK to
 * the pre-seek position, and a follow-up scrub started in that window wrongly
 * bases itself on the stale position instead of the just-committed target
 * (on-device TCL 55C6K bug: "el timeline se devuelve al primer avance").
 *
 * The latch closes the gap: the committed target stays the bar's (and the
 * next scrub's) source of truth until the engine reports a position within
 * [SYNC_EPSILON_MS] of it — the point where the engine itself has become the
 * fresher truth. The epsilon tolerates the position ticker's coarse pulses;
 * stale positions differ by at least one full scrub step (an order of
 * magnitude larger), so both forward and backward seeks hold correctly.
 */
object ScrubSeekSync {

    /** Positions this close to a pending target count as "the engine caught up" (ticker pulses are 300ms). */
    const val SYNC_EPSILON_MS = 1_500L

    /**
     * The single source of truth for the scrub bar AND the base of a next
     * scrub tick, in priority order: live preview → in-flight committed
     * target → engine position.
     */
    fun displayPosition(previewMs: Long?, pendingMs: Long?, enginePositionMs: Long): Long =
        previewMs ?: pendingMs ?: enginePositionMs

    /** [pendingMs] until the engine reaches it (within [SYNC_EPSILON_MS]); `null` once caught up. */
    fun resolvePending(pendingMs: Long?, enginePositionMs: Long): Long? =
        pendingMs?.takeIf { abs(enginePositionMs - it) > SYNC_EPSILON_MS }
}
