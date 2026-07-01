package com.youtroc.app.ui.player

import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.youtroc.core.domain.playback.MediaPlayer
import com.youtroc.core.domain.playback.WatchProgressStore
import com.youtroc.core.domain.video.VideoId
import com.youtroc.feature.playback.PlaybackViewModel

/**
 * Composition-root factory for [PlaybackViewModel]: the single seam that
 * wires the concrete `:data:player`/`:data:persistence` adapters — built by
 * the caller, see [PlaybackRoute] — into the feature ViewModel's pure domain
 * ports. No DI framework: manual wiring, the same convention
 * [PlayerViewModel.factory] already uses to wire `NewPipeStreamProvider`
 * into [com.youtroc.core.domain.playback.GetPlayableStreams].
 */
fun playbackViewModelFactory(
    player: MediaPlayer,
    watchProgressStore: WatchProgressStore,
    videoId: VideoId,
) = viewModelFactory {
    initializer {
        PlaybackViewModel(player = player, watchProgressStore = watchProgressStore, videoId = videoId)
    }
}
