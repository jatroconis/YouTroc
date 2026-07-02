package com.youtroc.core.domain.playback

/** Snapshot of a [MediaPlayer]'s observable state at a point in time. */
data class PlaybackState(
    val phase: Phase,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    /** Resolutions the current manifest currently exposes; empty when unknown or single-rendition. */
    val availableQualities: List<VideoQuality> = emptyList(),
    /** The pinned quality, or `null` when Automatic (ABR) is active. */
    val activeQuality: VideoQuality? = null,
    /** True when the active manifest is a live delivery (LIVE_HLS/LIVE_DASH). */
    val isLive: Boolean = false,
) {
    /** Lifecycle of a media source loaded into a [MediaPlayer]. */
    enum class Phase {
        /** No media loaded yet, or loaded but not started. */
        Idle,

        /** Media loaded and buffering — either the initial load or a rebuffer. */
        Buffering,

        /** Media is ready to play or already playing. */
        Ready,

        /** Playback reached the end of the media. */
        Ended,

        /** Playback failed. */
        Error,
    }
}
