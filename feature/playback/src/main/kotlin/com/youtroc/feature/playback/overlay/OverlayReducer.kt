package com.youtroc.feature.playback.overlay

/**
 * Pure visibility state machine for the player overlay (REQ-11, docs/07 §10).
 * The Netflix-style redesign dropped the old reveal-then-seek gesture: seeking
 * now lives entirely on the focused scrubber ([decideDpadAction]), so this
 * reducer only owns reveal-on-activity and auto-hide-on-inactivity.
 *
 * No Compose/Android types here — the Composable feeds real key/focus events in
 * and applies the resulting [OverlayState] back onto its own state.
 */
object OverlayReducer {

    const val AUTO_HIDE_TIMEOUT_MS = 4_000L

    /** ANY activity (a D-pad press that reveals, entering a zone, a button click) reveals the overlay. */
    fun onActivity(nowMs: Long): OverlayState = OverlayState.Revealed(sinceMs = nowMs)

    fun onInactivityTimeout(state: OverlayState, nowMs: Long): OverlayState = when (state) {
        is OverlayState.Hidden -> state
        is OverlayState.Revealed ->
            if (nowMs - state.sinceMs >= AUTO_HIDE_TIMEOUT_MS) OverlayState.Hidden else state
    }
}
