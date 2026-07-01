package com.youtroc.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.youtroc.core.domain.playback.GetPlayableStreams
import com.youtroc.core.domain.stream.StreamKind
import com.youtroc.core.domain.stream.StreamResult
import com.youtroc.core.domain.video.VideoId
import com.youtroc.data.extraction.NewPipeStreamProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Container for the player screen: turns a [VideoId] into a deterministic
 * [PlayerUiState] by driving the [GetPlayableStreams] use case. It knows nothing
 * about Media3 or Compose — the surface renders whatever state it emits.
 *
 * For now it picks the first MUXED (progressive) stream: one URL with video+audio,
 * the simplest thing that plays. Adaptive DASH is a later slice (ADR-7).
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
                    val stream = result.streams.streams.firstOrNull { it.kind == StreamKind.MUXED }
                        ?: result.streams.streams.first()
                    PlayerUiState.Ready(url = stream.url, title = title)
                }

                StreamResult.NotAvailable -> PlayerUiState.NotAvailable
                StreamResult.Offline -> PlayerUiState.Offline
                is StreamResult.Error -> PlayerUiState.Failed
            }
        }
    }

    companion object {
        /**
         * Composition root for the player: the only place that wires the concrete
         * [NewPipeStreamProvider] adapter into the domain use case. When DI lands,
         * this factory is the single seam that changes.
         */
        fun factory(videoId: String, title: String) = viewModelFactory {
            initializer {
                PlayerViewModel(
                    getPlayableStreams = GetPlayableStreams(NewPipeStreamProvider()),
                    videoId = videoId,
                    title = title,
                )
            }
        }
    }
}
