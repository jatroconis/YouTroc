package com.youtroc.feature.playback

/**
 * Resolution outcome of the CURRENT Shorts pager page, mirroring
 * `com.youtroc.app.ui.player.PlayerUiState`'s vocabulary shape
 * (Loading/NotAvailable/Offline/Failed) for the same
 * [com.youtroc.core.domain.stream.StreamResult] the landscape flow already
 * dispatches on — the only addition is [Ready], since a Short has no
 * separate "extraction done, now show a manifest" hand-off: playback starts
 * the instant a page resolves.
 */
sealed interface ShortsPageState {
    data object Loading : ShortsPageState
    data object Ready : ShortsPageState
    data object NotAvailable : ShortsPageState
    data object Offline : ShortsPageState
    data object Failed : ShortsPageState
}
