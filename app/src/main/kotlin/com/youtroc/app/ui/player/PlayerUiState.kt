package com.youtroc.app.ui.player

import com.youtroc.core.domain.playback.PlaybackManifest

/**
 * Deterministic states the player screen can render. It mirrors the domain's
 * [com.youtroc.core.domain.stream.StreamResult] vocabulary one-to-one, so the
 * UI never has to interpret exceptions — every outcome already has a state.
 */
sealed interface PlayerUiState {

    /** Resolving the ad-free streams. */
    data object Loading : PlayerUiState

    /** A playable [PlaybackManifest] is ready to hand to the `MediaPlayer` port. */
    data class Ready(val manifest: PlaybackManifest, val title: String) : PlayerUiState

    /**
     * The video exists but cannot be played anonymously (age-gated, removed,
     * region-locked) — or no valid delivery could be assembled at all (no
     * DASH/MUXED, and no video+audio pair to merge). Never falls back to a
     * lone video-only track (REQ-9).
     */
    data object NotAvailable : PlayerUiState

    /** No network reachable. */
    data object Offline : PlayerUiState

    /** Extraction failed unexpectedly. */
    data object Failed : PlayerUiState
}
