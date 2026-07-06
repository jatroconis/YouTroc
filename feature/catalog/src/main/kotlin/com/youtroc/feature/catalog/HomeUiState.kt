package com.youtroc.feature.catalog

import com.youtroc.core.domain.catalog.ShelfId
import com.youtroc.core.ui.component.VideoCardUi

/**
 * Deterministic states the Home screen can render. Mirrors the domain's
 * [com.youtroc.core.domain.catalog.CatalogResult] vocabulary 1:1 — the UI never
 * has to interpret exceptions, every outcome already has a state (mirrors
 * [com.youtroc.app.ui.player.PlayerUiState]).
 */
sealed interface HomeUiState {

    /** Resolving the trending feed. */
    data object Loading : HomeUiState

    /** Trending shelves resolved and ready to render. */
    data class Content(val shelves: List<HomeShelf>) : HomeUiState

    /** The feed resolved but returned zero items. */
    data object Empty : HomeUiState

    /** No network reachable. */
    data object Offline : HomeUiState

    /** Extraction failed unexpectedly. */
    data object Error : HomeUiState
}

/** A titled shelf of presentation-ready [VideoCardUi]s, tagged with its domain [ShelfId]. */
data class HomeShelf(
    val id: ShelfId,
    val title: String,
    val videos: List<VideoCardUi>,
)
