package com.youtroc.core.domain.playback

/**
 * Resolves a requested pin height against the live [VideoQuality] catalog a
 * manifest currently exposes. Pure, deterministic, and total — never touches
 * a player; the adapter applies whatever this returns, or falls back to Auto
 * on `null`.
 */
object QualitySelectionPolicy {

    /**
     * Exact height match wins; otherwise the nearest available height at or
     * below [requestedHeight]; if none qualifies (request is below every
     * available height), snaps up to the smallest available. An empty
     * [catalog] resolves to `null` (Auto).
     */
    fun resolve(requestedHeight: Int, catalog: List<VideoQuality>): VideoQuality? {
        if (catalog.isEmpty()) return null

        catalog.firstOrNull { it.heightPx == requestedHeight }?.let { return it }

        return catalog.filter { it.heightPx <= requestedHeight }.maxByOrNull { it.heightPx }
            ?: catalog.minByOrNull { it.heightPx }
    }
}
