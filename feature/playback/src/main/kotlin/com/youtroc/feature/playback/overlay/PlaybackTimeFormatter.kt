package com.youtroc.feature.playback.overlay

/**
 * Formats a millisecond position/duration as a scrubber label ("M:SS", or
 * "H:MM:SS" once past an hour). Pure — no Compose/Android types.
 */
object PlaybackTimeFormatter {

    fun format(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0L) / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
