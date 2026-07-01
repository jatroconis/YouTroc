package com.youtroc.app.ui.player

import android.content.Context
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.youtroc.core.domain.playback.WatchProgressStore
import com.youtroc.core.domain.video.VideoId
import com.youtroc.data.player.Media3MediaPlayer
import com.youtroc.feature.playback.PlaybackViewModel
import kotlinx.coroutines.CoroutineScope

/**
 * Composition-root factory for [PlaybackViewModel]: the single seam that
 * wires the concrete `:data:player`/`:data:persistence` adapters into the
 * feature ViewModel's pure domain ports. No DI framework: manual wiring, the
 * same convention [PlayerViewModel.factory] already uses to wire
 * `NewPipeStreamProvider` into [com.youtroc.core.domain.playback.GetPlayableStreams].
 *
 * Builds the concrete [Media3MediaPlayer] itself, INSIDE [initializer]
 * (MAJOR M1): `initializer` runs on the main thread exactly once — the first
 * time `viewModel()` needs to CREATE this NavBackStackEntry's
 * [PlaybackViewModel] — never again on a later recomposition or Activity
 * recreation. The previous `remember { Media3MediaPlayer(context) }` in the
 * composable rebuilt the engine on every composition (torn down by
 * `onDispose` on recreation) while the retained ViewModel kept driving the
 * OLD, already-released instance — causing the black-screen/leak this fixes.
 * [PlaybackRoute] reads the SAME instance back off [PlaybackViewModel.player]
 * for [com.youtroc.data.player.PlayerSurface].
 */
fun playbackViewModelFactory(
    context: Context,
    watchProgressStore: WatchProgressStore,
    videoId: VideoId,
    appScope: CoroutineScope,
) = viewModelFactory {
    initializer {
        PlaybackViewModel(
            player = Media3MediaPlayer(context),
            watchProgressStore = watchProgressStore,
            videoId = videoId,
            appScope = appScope,
        )
    }
}
