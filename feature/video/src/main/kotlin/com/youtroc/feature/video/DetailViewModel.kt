package com.youtroc.feature.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtroc.core.domain.detail.DetailResult
import com.youtroc.core.domain.detail.GetVideoDetail
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Container that drives [GetVideoDetail] for the video-detail screen. Knows
 * only the domain use case — never NewPipe/`:data:extraction` — so it is
 * fully testable with a fake [com.youtroc.core.domain.detail.VideoDetail]
 * wrapped in a real [GetVideoDetail] (mirrors
 * [com.youtroc.feature.catalog.HomeViewModel]).
 *
 * Auto-loads in `init` — like `HomeViewModel`, NOT `Idle` like
 * `SearchViewModel` (a videoId is always available when this screen opens,
 * there is no "no query yet" state to represent).
 *
 * [videoId] is kept a plain `String` (NOT [VideoId]) and only converted
 * inside [load]'s coroutine — gate-review correction #3: [VideoId]'s
 * `require(non-blank)` init check must not throw at construction time,
 * mirrors [com.youtroc.app.ui.player.PlayerViewModel].
 */
class DetailViewModel(
    private val videoId: String,
    private val getVideoDetail: GetVideoDetail,
) : ViewModel() {

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** Resolves the detail; also the NotAvailable/Offline/Error retry action. */
    fun load() {
        viewModelScope.launch {
            _state.value = DetailUiState.Loading
            _state.value = when (val result = getVideoDetail(VideoId(videoId))) {
                is DetailResult.Success -> DetailUiState.Content(result.detail.toDetailUi())
                DetailResult.NotAvailable -> DetailUiState.NotAvailable
                DetailResult.Offline -> DetailUiState.Offline
                is DetailResult.Error -> DetailUiState.Error
            }
        }
    }

    /** Re-runs [load] — the NotAvailable/Offline/Error retry action. */
    fun retry() = load()
}
