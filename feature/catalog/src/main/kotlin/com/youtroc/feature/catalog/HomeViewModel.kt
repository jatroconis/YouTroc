package com.youtroc.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtroc.core.domain.catalog.ComposeHomeFeed
import com.youtroc.core.domain.catalog.HomeFeedDecision
import com.youtroc.core.domain.catalog.HomeFeedPolicy
import com.youtroc.core.domain.catalog.TerminalKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Container that drives [ComposeHomeFeed] for the Home screen. Knows only
 * the domain composer -- never NewPipe/InnerTube/`:data:extraction` -- so it
 * is fully testable with fake [com.youtroc.core.domain.catalog.ShelfSource]s.
 *
 * [load] cancels and restarts the collection rather than layering a second
 * one on top (F2): the composer's Flow can stay open for ~30s (the unbounded
 * late-lead leg, N3), so a retry BEFORE it completes must cancel the stale
 * collection -- otherwise the old attempt's late slot-fill could clobber
 * [_state] after a newer retry (last-writer-wins bug). Re-seeding
 * [HomeUiState.Loading] at every [load] call (not only the first) keeps
 * [HomeFocusTarget]'s NONE->MESSAGE/CONTENT re-arm working across retries.
 * The collection is never stopped early on its own (F6) -- it runs until
 * [ComposeHomeFeed]'s Flow itself completes, so a late lead fill is never
 * lost.
 */
class HomeViewModel(
    private val composeHomeFeed: ComposeHomeFeed,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var collectJob: Job? = null

    init {
        load()
    }

    /** (Re)starts the feed composition; also the Offline/Error/Empty retry action. */
    fun load() {
        collectJob?.cancel()
        _state.value = HomeUiState.Loading
        collectJob = viewModelScope.launch {
            composeHomeFeed().collect { snapshot ->
                _state.value = when (val decision = HomeFeedPolicy.decide(snapshot)) {
                    HomeFeedDecision.Loading -> HomeUiState.Loading
                    is HomeFeedDecision.Content -> HomeUiState.Content(decision.shelves.map(::toHomeShelf))
                    is HomeFeedDecision.Terminal -> when (decision.kind) {
                        TerminalKind.OFFLINE -> HomeUiState.Offline
                        TerminalKind.ERROR -> HomeUiState.Error
                        TerminalKind.EMPTY -> HomeUiState.Empty
                    }
                }
            }
        }
    }
}
