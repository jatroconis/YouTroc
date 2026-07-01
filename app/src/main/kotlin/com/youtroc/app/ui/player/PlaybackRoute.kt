package com.youtroc.app.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.youtroc.core.domain.playback.PlaybackManifest
import com.youtroc.core.domain.video.VideoId
import com.youtroc.data.persistence.DataStoreWatchProgressStore
import com.youtroc.data.player.Media3MediaPlayer
import com.youtroc.data.player.PlayerSurface
import com.youtroc.feature.playback.PlaybackViewModel
import com.youtroc.feature.playback.PlayerOverlay

/**
 * Composition root for a resolved playback session (REQ-8..14): the only
 * place in the app that constructs the concrete [Media3MediaPlayer] /
 * [DataStoreWatchProgressStore] adapters and injects them — via
 * [playbackViewModelFactory] — into `:feature:playback`'s [PlaybackViewModel].
 * No DI framework: manual wiring, this composable IS the single seam.
 *
 * [Media3MediaPlayer] MUST be built on the main thread — it binds to the
 * constructing thread's `Looper`. `remember` runs during composition, which
 * always happens on the UI/main thread, so this satisfies that constraint
 * without an extra dispatch.
 *
 * Renders the SAME [Media3MediaPlayer] instance through two composables
 * stacked on top of each other: [PlayerSurface] (rendering, `:data:player`)
 * underneath, and [PlayerOverlay] (control/state, `:feature:playback`) above
 * it — the surface-as-adapter split the design ratified for REQ-8.
 */
@Composable
fun PlaybackRoute(
    videoId: String,
    manifest: PlaybackManifest,
    title: String,
) {
    val context = LocalContext.current
    val player = remember { Media3MediaPlayer(context) }
    val watchProgressStore = remember { DataStoreWatchProgressStore(context) }

    val playbackViewModel: PlaybackViewModel = viewModel(
        factory = playbackViewModelFactory(
            player = player,
            watchProgressStore = watchProgressStore,
            videoId = VideoId(videoId),
        ),
    )
    val playbackState by playbackViewModel.playbackState.collectAsState()

    LaunchedEffect(Unit) {
        playbackViewModel.start(manifest)
    }

    // REQ-13: pause on ON_STOP/ON_PAUSE. PlaybackViewModel.pause() also
    // persists the current position in the same call (REQ-12), so this one
    // observer backs both requirements. Deliberately no ON_START/ON_RESUME
    // hook — no auto-resume when the app returns to the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, playbackViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_PAUSE) {
                playbackViewModel.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // REQ-6: release the engine once this surface leaves composition
            // (BACK popping this destination off the NavHost back stack).
            playbackViewModel.releasePlayer()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PlayerSurface(player = player, modifier = Modifier.fillMaxSize())
        PlayerOverlay(
            title = title,
            playbackState = playbackState,
            modifier = Modifier.fillMaxSize(),
            onPlayPause = playbackViewModel::togglePlayPause,
            onSeek = playbackViewModel::seekBy,
            // No related-videos/queue destination in FASE 1 (spec Non-Goals):
            // prev/next/like/dislike/captions/settings stay PlayerOverlay's
            // default no-ops until that feature exists.
        )
    }
}
