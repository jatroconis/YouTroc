package com.youtroc.feature.catalog

/**
 * The 3-way focus category [com.youtroc.app.ui.HomeShell] re-fires focus on,
 * keyed instead of a boolean latch (N1): a boolean collapses
 * Offline/Error/Empty/Content into one "true" bucket, so a MESSAGE->CONTENT
 * transition (a late-arriving shelf turning a terminal state into content)
 * never re-fires and strands focus on a Retry button that already left
 * composition.
 */
enum class HomeFocusTarget { NONE, MESSAGE, CONTENT }

/** Pure derivation from [HomeUiState] -- see [HomeFocusTarget]. */
fun HomeUiState.focusTarget(): HomeFocusTarget = when (this) {
    HomeUiState.Loading -> HomeFocusTarget.NONE
    is HomeUiState.Content -> HomeFocusTarget.CONTENT
    HomeUiState.Offline, HomeUiState.Error, HomeUiState.Empty -> HomeFocusTarget.MESSAGE
}
