package com.youtroc.app.ui.player

import com.youtroc.core.domain.playback.PlaybackManifest
import com.youtroc.core.domain.stream.HdrFormat
import com.youtroc.core.domain.stream.StoryboardSpec

/**
 * Deterministic states the player screen can render. It mirrors the domain's
 * [com.youtroc.core.domain.stream.StreamResult] vocabulary one-to-one, so the
 * UI never has to interpret exceptions — every outcome already has a state.
 */
sealed interface PlayerUiState {

    /** Resolving the ad-free streams. */
    data object Loading : PlayerUiState

    /**
     * A playable [PlaybackManifest] is ready to hand to the `MediaPlayer` port.
     * [hdr] carries the selected streams' aggregate HDR intent (REQ-H5) --
     * [PlaybackRoute] uses it to decide whether to opt the Window into HDR
     * display mode. [storyboard] carries the scrub-preview sprite data
     * (REQ-SB5) -- additive and defaulted to null, so it renders no thumbnail
     * when extraction never resolved one.
     */
    data class Ready(
        val manifest: PlaybackManifest,
        val title: String,
        val hdr: HdrFormat,
        val storyboard: StoryboardSpec? = null,
    ) : PlayerUiState

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
