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
 *
 * [warmNextId] (design D2) is a SEPARATE early path used by speculative
 * prefetch: it resolves [GetVideoDetail] ONCE at stable-play to learn the
 * first up-next video's id as early as possible, but DELIBERATELY never
 * writes [_state] — the panel's lazy-load contract (REQ-U3/D5) stays
 * byte-identical, since only [ensureLoaded] (panel-open) is allowed to
 * publish to [state]. [ensureLoaded] reuses the warmed result when present
 * instead of re-fetching.
 */
class UpNextViewModel(
    private val videoId: String,
    private val getVideoDetail: GetVideoDetail,
) : ViewModel() {

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private val _nextUpNextId = MutableStateFlow<String?>(null)

    /** `:app`-only. The first up-next video's id, learned early by [warmNextId] -- independent of the panel's [state]. */
    val nextUpNextId: StateFlow<String?> = _nextUpNextId.asStateFlow()

    private var warmedCache: DetailUiState? = null

    /** Once-per-video latch: a rebuffer (Ready->Buffering->Ready) re-fires the stable-play trigger but must NOT re-resolve. */
    private var warmed = false

    private var loaded = false

    /** Idempotent, cached first-load trigger — called from `onPanelOpened` when the panel gains focus. */
    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val warm = warmedCache
        if (warm != null) {
            _state.value = warm
            return
        }
        load()
    }

    /** Resolves the detail; also the NotAvailable/Offline/Error retry action. */
    fun load() {
        viewModelScope.launch {
            _state.value = DetailUiState.Loading
            _state.value = resolveDetail().first
        }
    }

    /**
     * `:app`-only, stable-play trigger for speculative prefetch (design D2).
     * Resolves [GetVideoDetail] at most once per video and publishes the
     * first related video's id to [nextUpNextId] -- never touches [_state],
     * so the panel stays [DetailUiState.Loading] until it is actually opened.
     */
    fun warmNextId() {
        if (warmed) return
        warmed = true
        viewModelScope.launch {
            val (state, firstId) = resolveDetail()
            warmedCache = state
            _nextUpNextId.value = firstId
        }
    }

    /** Shared by [load] and [warmNextId]: the blank-id guard + [GetVideoDetail] dispatch + first-related-id extraction. */
    private suspend fun resolveDetail(): Pair<DetailUiState, String?> {
        // A blank videoId can't be identified — `VideoId`'s
        // require(non-blank) init check would otherwise throw an
        // IllegalArgumentException that escapes viewModelScope and
        // crashes the app. Map it to the same terminal NotAvailable
        // state a resolvable-but-missing video reaches, and never call
        // the port with an id that can't be constructed.
        val id = runCatching { VideoId(videoId) }.getOrNull()
            ?: return DetailUiState.NotAvailable to null

        return when (val result = getVideoDetail(id)) {
            is DetailResult.Success -> {
                val detail = result.detail
                DetailUiState.Content(detail.toDetailUi()) to detail.related.firstOrNull()?.id?.value
            }
            DetailResult.NotAvailable -> DetailUiState.NotAvailable to null
            DetailResult.Offline -> DetailUiState.Offline to null
            is DetailResult.Error -> DetailUiState.Error to null
        }
    }

    /** Re-runs [load] — the NotAvailable/Offline/Error retry action. Re-invokes the port regardless of cache state. */
    fun retry() = load()
}
