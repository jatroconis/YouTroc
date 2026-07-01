package com.youtroc.feature.search

import com.youtroc.core.ui.component.VideoCardUi

/**
 * Deterministic states the search screen can render. Mirrors the domain's
 * [com.youtroc.core.domain.search.SearchResult] vocabulary 1:1, with one
 * addition: [Idle] before any query is confirmed — unlike Home's
 * [com.youtroc.feature.catalog.HomeUiState], search does NOT auto-load.
 */
sealed interface SearchUiState {

    /** No query confirmed yet — the initial state, no auto-load. */
    data object Idle : SearchUiState

    /** Resolving the confirmed query. */
    data object Loading : SearchUiState

    /** Matching videos resolved and ready to render. */
    data class Results(val videos: List<VideoCardUi>) : SearchUiState

    /** The query resolved but returned zero matches. */
    data object Empty : SearchUiState

    /** No network reachable. */
    data object Offline : SearchUiState

    /** Extraction failed unexpectedly. */
    data object Error : SearchUiState
}
