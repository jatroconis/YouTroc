package com.youtroc.core.domain.playback

/**
 * A point in a video's timeline, in milliseconds. Wrapping the raw `Long` stops
 * a position from being silently confused with a duration or any other
 * millisecond count elsewhere in the domain.
 */
@JvmInline
value class PlaybackPosition(val positionMs: Long) {
    init {
        require(positionMs >= 0) { "PlaybackPosition must not be negative." }
    }

    /**
     * Whether this position is worth resuming from, given the video's
     * [durationMs]: too close to the start (nothing was watched yet) or too
     * close to the end (the video is effectively finished) both resolve to
     * "start from zero" instead.
     */
    fun isResumable(durationMs: Long): Boolean {
        if (positionMs < MIN_RESUMABLE_POSITION_MS) return false
        val pastRatioThreshold = durationMs > 0 && positionMs >= (durationMs * NEAR_END_RATIO).toLong()
        val withinEndWindow = positionMs >= durationMs - NEAR_END_WINDOW_MS
        return !pastRatioThreshold && !withinEndWindow
    }

    private companion object {
        /** Saved progress under this is treated as "barely started". */
        const val MIN_RESUMABLE_POSITION_MS = 10_000L

        /** Saved progress past this fraction of the duration is treated as "finished". */
        const val NEAR_END_RATIO = 0.95

        /** Saved progress within this many ms of the end is treated as "finished". */
        const val NEAR_END_WINDOW_MS = 15_000L
    }
}
