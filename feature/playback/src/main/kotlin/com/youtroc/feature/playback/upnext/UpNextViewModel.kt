package com.youtroc.feature.playback.upnext

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
 * Container that drives [GetVideoDetail] for the in-player Info+Up-Next
 * panel. Knows only the domain use case — never NewPipe/`:data:extraction` —
 * so it is fully testable with a fake [com.youtroc.core.domain.detail.VideoDetail]
 * wrapped in a real [GetVideoDetail] (mirrors [com.youtroc.feature.catalog.HomeViewModel]).
 *
 * LAZY, session-cached resolution (REQ-U3, design D5, gate R5) — re-homed
 * from the deleted `:feature:video` module's `DetailViewModel`, which
 * auto-loaded in `init`. This ViewModel MUST NOT call [GetVideoDetail] at
 * construction (playback start / overlay reveal) — only [ensureLoaded],
 * triggered by the panel gaining focus for the first time, does. A second
 * [ensureLoaded] call reuses the cached result instead of re-fetching.
 *
 * [videoId] is kept a plain `String` (NOT [VideoId]) and only converted
 * inside [load]'s coroutine — [VideoId]'s `require(non-blank)` init check
 * must not throw at construction time, mirrors
 * [com.youtroc.app.ui.player.PlayerViewModel].
 */
class UpNextViewModel(
    private val videoId: String,
    private val getVideoDetail: GetVideoDetail,
) : ViewModel() {

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var loaded = false

    /** Idempotent, cached first-load trigger — called from `onPanelOpened` when the panel gains focus. */
    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        load()
    }

    /** Resolves the detail; also the NotAvailable/Offline/Error retry action. */
    fun load() {
        viewModelScope.launch {
            _state.value = DetailUiState.Loading

            // A blank videoId can't be identified — `VideoId`'s
            // require(non-blank) init check would otherwise throw an
            // IllegalArgumentException that escapes viewModelScope and
            // crashes the app. Map it to the same terminal NotAvailable
            // state a resolvable-but-missing video reaches, and never call
            // the port with an id that can't be constructed.
            val id = runCatching { VideoId(videoId) }.getOrNull()
            if (id == null) {
                _state.value = DetailUiState.NotAvailable
                return@launch
            }

            _state.value = when (val result = getVideoDetail(id)) {
                is DetailResult.Success -> DetailUiState.Content(result.detail.toDetailUi())
                DetailResult.NotAvailable -> DetailUiState.NotAvailable
                DetailResult.Offline -> DetailUiState.Offline
                is DetailResult.Error -> DetailUiState.Error
            }
        }
    }

    /** Re-runs [load] — the NotAvailable/Offline/Error retry action. Re-invokes the port regardless of cache state. */
    fun retry() = load()
}
