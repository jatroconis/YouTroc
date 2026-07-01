package com.youtroc.app.ui.player

/**
 * Deterministic states the player screen can render. It mirrors the domain's
 * [com.youtroc.core.domain.stream.StreamResult] vocabulary one-to-one, so the
 * UI never has to interpret exceptions — every outcome already has a state.
 */
sealed interface PlayerUiState {

    /** Resolving the ad-free streams. */
    data object Loading : PlayerUiState

    /** A playable, ad-free stream URL is ready. */
    data class Ready(val url: String, val title: String) : PlayerUiState

    /** The video exists but cannot be played anonymously (age-gated, removed, region-locked). */
    data object NotAvailable : PlayerUiState

    /** No network reachable. */
    data object Offline : PlayerUiState

    /** Extraction failed unexpectedly. */
    data object Failed : PlayerUiState
}
