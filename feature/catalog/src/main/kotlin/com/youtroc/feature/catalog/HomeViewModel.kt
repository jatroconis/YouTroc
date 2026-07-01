package com.youtroc.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtroc.core.domain.catalog.CatalogResult
import com.youtroc.core.domain.catalog.GetHomeFeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Container that drives [GetHomeFeed] for the Home screen. Knows only the
 * domain port — never NewPipe/`:data:extraction` — so it is fully testable
 * with a fake [com.youtroc.core.domain.catalog.VideoCatalog] wrapped in a real
 * [GetHomeFeed] (mirrors [com.youtroc.app.ui.player.PlayerViewModel]).
 */
class HomeViewModel(
    private val getHomeFeed: GetHomeFeed,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** Resolves the feed; also the Offline/Error/Empty retry action. */
    fun load() {
        viewModelScope.launch {
            _state.value = HomeUiState.Loading
            _state.value = when (val result = getHomeFeed()) {
                is CatalogResult.Success -> HomeUiState.Content(result.shelves.map(::toHomeShelf))
                CatalogResult.Empty -> HomeUiState.Empty
                CatalogResult.Offline -> HomeUiState.Offline
                is CatalogResult.Error -> HomeUiState.Error
            }
        }
    }
}
