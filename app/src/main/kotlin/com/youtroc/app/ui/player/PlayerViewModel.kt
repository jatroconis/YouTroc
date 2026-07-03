package com.youtroc.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.youtroc.core.domain.playback.GetPlayableStreams
import com.youtroc.core.domain.stream.StreamProvider
import com.youtroc.core.domain.stream.StreamResult
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Container for the player screen's EXTRACTION phase: turns a [VideoId] into
 * a deterministic [PlayerUiState] by driving the [GetPlayableStreams] use
 * case. It knows nothing about Media3/ExoPlayer or Compose.
 *
 * Once resolved, playback CONTROL is handed off entirely to
 * `:feature:playback`'s `PlaybackViewModel` (wired by [PlaybackRoute]), so
 * this ViewModel's only remaining job is picking up the
 * [com.youtroc.core.domain.playback.PlaybackManifest] the delivery policy
 * already selected (DASH-first, never a lone video-only track — REQ-9).
 */
class PlayerViewModel(
    private val getPlayableStreams: GetPlayableStreams,
    private val videoId: String,
    private val title: String,
) : ViewModel() {

    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    init {
        resolve()
    }

    fun resolve() {
        viewModelScope.launch {
            _state.value = PlayerUiState.Loading
            _state.value = when (val result = getPlayableStreams(VideoId(videoId))) {
                is StreamResult.Success -> {
                    val manifest = result.streams.manifest
                    // A null manifest means the selection policy couldn't assemble any
                    // valid delivery (no DASH/MUXED, and no video+audio pair to merge).
                    if (manifest != null) {
                        PlayerUiState.Ready(manifest, title, result.streams.hdr)
                    } else {
                        PlayerUiState.NotAvailable
                    }
                }

                StreamResult.NotAvailable -> PlayerUiState.NotAvailable
                StreamResult.Offline -> PlayerUiState.Offline
                is StreamResult.Error -> PlayerUiState.Failed
            }
        }
    }

    companion object {
        /**
         * Composition root for extraction: builds [GetPlayableStreams] over
         * the port-typed [streamProvider] the caller passes in -- the
         * concrete `PrefetchingStreamProvider`/`LadderStreamProvider`
         * composition lives ONLY in `YouTrocApp.streamProvider` (design D1),
         * so this factory no longer constructs a fresh adapter chain per
         * player entry. Cache-transparent: [GetPlayableStreams] sees only
         * [StreamProvider.playableStreams].
         */
        fun factory(videoId: String, title: String, streamProvider: StreamProvider) = viewModelFactory {
            initializer {
                PlayerViewModel(
                    getPlayableStreams = GetPlayableStreams(streamProvider),
                    videoId = videoId,
                    title = title,
                )
            }
        }
    }
}
