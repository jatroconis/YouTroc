package com.youtroc.core.domain.playback

/** Snapshot of a [MediaPlayer]'s observable state at a point in time. */
data class PlaybackState(
    val phase: Phase,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
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
