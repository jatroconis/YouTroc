package com.youtroc.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtroc.core.domain.search.SearchResult
import com.youtroc.core.domain.search.SearchVideos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Container that drives [SearchVideos] for the search screen. Knows only the
 * domain use case — never NewPipe/`:data:extraction` — so it is fully
 * testable with a fake [com.youtroc.core.domain.search.VideoSearch] wrapped
 * in a real [SearchVideos] (mirrors
 * [com.youtroc.feature.catalog.HomeViewModel]).
 *
 * Unlike [com.youtroc.feature.catalog.HomeViewModel], there is NO `init {}`
 * auto-load — search needs a confirmed query first, so [state] stays [Idle]
 * until [search] is called.
 */
class SearchViewModel(
    private val searchVideos: SearchVideos,
) : ViewModel() {

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var lastQuery: String = ""

    /**
     * Runs [query] against [searchVideos]. Gate MAJOR-1 (design-gate #4408,
     * closes acceptance-checklist #5): a blank/whitespace [query] returns
     * BEFORE touching [lastQuery], [state], or the port — [state] stays
     * [SearchUiState.Idle] and the port is NEVER invoked. This is the real
     * guard; [com.youtroc.data.extraction.search.NewPipeVideoSearch]'s own
     * blank check (WU-1) is defense-in-depth only.
     */
    fun search(query: String) {
        if (query.isBlank()) return
        lastQuery = query
        viewModelScope.launch {
            _state.value = SearchUiState.Loading
            _state.value = when (val result = searchVideos(query)) {
                is SearchResult.Success -> SearchUiState.Results(result.videos.map { it.toVideoCardUi() })
                SearchResult.Empty -> SearchUiState.Empty
                SearchResult.Offline -> SearchUiState.Offline
                is SearchResult.Error -> SearchUiState.Error
            }
        }
    }

    /** Re-runs [lastQuery] — the Offline/Error retry action. */
    fun retry() = search(lastQuery)
}
