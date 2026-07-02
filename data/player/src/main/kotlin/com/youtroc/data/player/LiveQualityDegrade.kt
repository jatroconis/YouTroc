package com.youtroc.data.player

import com.youtroc.core.domain.playback.VideoQuality

/**
 * Pure quality-degrade rule for live playback (R1, REQ-L9): a live manifest
 * exposes no selectable renditions, so the Calidad menu must collapse to
 * Automatic-only. Applied at the SOURCE — [Media3MediaPlayer.publishState] —
 * not the overlay, so [com.youtroc.core.domain.playback.PlaybackState]
 * already arrives pre-degraded and no live-specific masking is needed
 * downstream. Pure and total — no ExoPlayer instance needed — mirrors
 * [PlaybackPhaseMapper]'s headless-testable-seam pattern.
 */
internal object LiveQualityDegrade {

    /** Empty when [isLive]; [catalog] unchanged otherwise. */
    fun qualities(isLive: Boolean, catalog: List<VideoQuality>): List<VideoQuality> =
        if (isLive) emptyList() else catalog

    /** `null` when [isLive] (no manual pin possible on live); [pinned] unchanged otherwise. */
    fun active(isLive: Boolean, pinned: VideoQuality?): VideoQuality? =
        if (isLive) null else pinned
}
