package com.youtroc.core.domain.playback

/**
 * Decides where playback should start: auto-seek to a saved position, or start
 * from zero. Pure — the thresholds live on [PlaybackPosition.isResumable].
 */
object ResumeDecision {

    fun startAt(saved: PlaybackPosition?, durationMs: Long): PlaybackPosition? =
        saved?.takeIf { it.isResumable(durationMs) }
}
