package com.youtroc.feature.playback.overlay

/**
 * Computes the seek offset for a D-pad L/R press (REQ-11): a clean single
 * press/release steps 10s; holding the key engages a continuous, accelerating
 * fast-seek. Pure — [repeatCount] is the OS-reported key-repeat count for the
 * held press (0 for a single tap, 1+ for each subsequent repeat tick).
 */
object SeekAmount {

    const val SINGLE_STEP_MS = 10_000L

    private const val FAST_SEEK_BASE_MS = 10_000L
    private const val FAST_SEEK_ACCELERATION_MS = 5_000L
    private const val FAST_SEEK_MAX_MS = 60_000L

    fun forPress(repeatCount: Int): Long =
        if (repeatCount <= 0) {
            SINGLE_STEP_MS
        } else {
            (FAST_SEEK_BASE_MS + FAST_SEEK_ACCELERATION_MS * repeatCount).coerceAtMost(FAST_SEEK_MAX_MS)
        }
}
