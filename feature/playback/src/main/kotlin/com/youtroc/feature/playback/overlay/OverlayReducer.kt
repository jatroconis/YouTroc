package com.youtroc.feature.playback.overlay

/** Result of feeding a single D-pad L/R press through [OverlayReducer]. */
data class OverlayTransition(
    val nextState: OverlayState,
    val seek: SeekDirection?,
)

/**
 * Pure reveal-then-seek state machine for the player overlay (REQ-11,
 * docs/07 §10): the FIRST D-pad L/R press while [OverlayState.Hidden] only
 * reveals the overlay — it never seeks. Every press while
 * [OverlayState.Revealed] performs a seek instead. [onInactivityTimeout]
 * hides the overlay again after [AUTO_HIDE_TIMEOUT_MS] of no activity.
 *
 * No Compose/Android types here — the Composable feeds real key events in
 * and applies the resulting [OverlayTransition] back onto its own state.
 */
object OverlayReducer {

    const val AUTO_HIDE_TIMEOUT_MS = 4_000L

    fun onDirectionalKey(state: OverlayState, direction: SeekDirection, nowMs: Long): OverlayTransition =
        when (state) {
            is OverlayState.Hidden ->
                OverlayTransition(nextState = OverlayState.Revealed(sinceMs = nowMs), seek = null)

            is OverlayState.Revealed ->
                OverlayTransition(nextState = OverlayState.Revealed(sinceMs = nowMs), seek = direction)
        }

    /** Any other activity (play/pause, entering the control rows) also reveals without seeking. */
    fun onActivity(nowMs: Long): OverlayState = OverlayState.Revealed(sinceMs = nowMs)

    fun onInactivityTimeout(state: OverlayState, nowMs: Long): OverlayState = when (state) {
        is OverlayState.Hidden -> state
        is OverlayState.Revealed ->
            if (nowMs - state.sinceMs >= AUTO_HIDE_TIMEOUT_MS) OverlayState.Hidden else state
    }
}
