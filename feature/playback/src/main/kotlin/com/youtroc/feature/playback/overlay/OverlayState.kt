package com.youtroc.feature.playback.overlay

/**
 * Visibility of the player's custom overlay (REQ-11, docs/07 §10). Pure —
 * driven by [OverlayReducer], never touches Compose/Android types directly
 * so the reveal-then-seek machine is unit-testable in isolation.
 */
sealed interface OverlayState {
    data object Hidden : OverlayState
    data class Revealed(val sinceMs: Long) : OverlayState
}

/** Which way a D-pad seek gesture moves the playhead. */
enum class SeekDirection { Backward, Forward }
