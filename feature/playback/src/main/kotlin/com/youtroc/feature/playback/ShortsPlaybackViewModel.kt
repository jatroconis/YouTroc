package com.youtroc.feature.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtroc.core.domain.playback.GetPlayableStreams
import com.youtroc.core.domain.playback.MediaPlayer
import com.youtroc.core.domain.stream.StreamResult
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Container that drives a vertical Shorts pager (REQ-HF12): unlike
 * [PlaybackViewModel], it owns BOTH extraction ([getPlayableStreams]) AND
 * control ([player]) for however many items the caller hands it, since a
 * per-item [PlaybackManifest][com.youtroc.core.domain.playback.PlaybackManifest]
 * must resolve automatically as [items] pages, with no separate
 * `:app`-level "extraction ViewModel" per page (unlike the landscape
 * `PlayerViewModel`/`PlaybackViewModel` split).
 *
 * [items] is exposed publicly (mirrors [PlaybackViewModel.player]'s M1
 * "composition root reads it back" convention) so [ShortsOverlay] can render
 * the current/next titles from it directly, without a second observable.
 *
 * **Store-less by construction (R6)**: there is no
 * [com.youtroc.core.domain.playback.WatchProgressStore] parameter anywhere in
 * this class — Shorts structurally cannot write watch history.
 *
 * **N5 — flatMapLatest cancellation**: [currentIndex] changing (via [next]/
 * [previous]) cancels whatever [resolveAndPlay] coroutine was still
 * suspended for the PRIOR index — a slow/stale resolution can never reach
 * [MediaPlayer.setMedia] after the user has already paged past it. The
 * underlying [GetPlayableStreams] call itself cannot be socket-interrupted
 * (same B1 caveat as `ComposeHomeFeed`'s per-source ceiling), but the
 * discarded emission never runs its post-suspension `setMedia`/`play` side
 * effects, so there is no stale-page race even so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShortsPlaybackViewModel(
    val player: MediaPlayer,
    private val getPlayableStreams: GetPlayableStreams,
    val items: List<ShortsQueueItem>,
    startIndex: Int = 0,
) : ViewModel() {

    private val mutableCurrentIndex = MutableStateFlow(startIndex.coerceIn(0, items.lastIndex))

    /** The pager's active index -- read back by the composition root for [ShortsOverlay]'s title/`@channel`/next-up text. */
    val currentIndex: StateFlow<Int> = mutableCurrentIndex.asStateFlow()

    /** Resolution outcome of the page at [currentIndex]; restarts fresh (N5) on every index change. */
    val page: StateFlow<ShortsPageState> = mutableCurrentIndex
        .flatMapLatest { index -> resolveAndPlay(items[index].id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ShortsPageState.Loading)

    /** DOWN: advances to the next short, clamped at the end of the queue (no wrap-around). */
    fun next() {
        mutableCurrentIndex.update { (it + 1).coerceAtMost(items.lastIndex) }
    }

    /** UP: returns to the previous short, clamped at the start of the queue (no wrap-around). */
    fun previous() {
        mutableCurrentIndex.update { (it - 1).coerceAtLeast(0) }
    }

    /** Release contract: the framework invokes this once, when the destination's ViewModelStore clears (BACK). No history to persist (R6). */
    public override fun onCleared() {
        player.release()
    }

    private fun resolveAndPlay(rawId: String): Flow<ShortsPageState> = flow {
        emit(ShortsPageState.Loading)
        val videoId = runCatching { VideoId(rawId) }.getOrNull()
        if (videoId == null) {
            emit(ShortsPageState.NotAvailable)
            return@flow
        }
        when (val result = getPlayableStreams(videoId)) {
            is StreamResult.Success -> {
                val manifest = result.streams.manifest
                if (manifest != null) {
                    player.setMedia(manifest, startAt = null)
                    player.play()
                    emit(ShortsPageState.Ready)
                } else {
                    emit(ShortsPageState.NotAvailable)
                }
            }
            StreamResult.NotAvailable -> emit(ShortsPageState.NotAvailable)
            StreamResult.Offline -> emit(ShortsPageState.Offline)
            is StreamResult.Error -> emit(ShortsPageState.Failed)
        }
    }
}
