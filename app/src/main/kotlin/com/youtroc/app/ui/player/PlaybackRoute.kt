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
import com.youtroc.app.YouTrocApp
import com.youtroc.core.domain.playback.PlaybackManifest
import com.youtroc.core.domain.video.VideoId
import com.youtroc.data.persistence.DataStoreWatchProgressStore
import com.youtroc.data.player.Media3MediaPlayer
import com.youtroc.data.player.PlayerSurface
import com.youtroc.feature.playback.PlaybackViewModel
import com.youtroc.feature.playback.PlayerOverlay

/**
 * Composition root for a resolved playback session (REQ-8..14): the only
 * place in the app that constructs the concrete [DataStoreWatchProgressStore]
 * adapter and injects it — via [playbackViewModelFactory] — into
 * `:feature:playback`'s [PlaybackViewModel]. No DI framework: manual wiring,
 * this composable IS the single seam.
 *
 * The [Media3MediaPlayer] itself is built by [playbackViewModelFactory]
 * INSIDE the ViewModel's `initializer` (MAJOR M1), not here via `remember` —
 * that tied its construction to Compose's COMPOSITION scope, which is torn
 * down and rebuilt on every Activity recreation (day/night, density,
 * fontScale, uiMode, locale — none declared in the manifest's
 * `configChanges`) while the NavBackStackEntry-scoped [PlaybackViewModel]
 * survives, leaving the retained ViewModel driving an already-released
 * player. [playbackViewModel]`.player` reads back that SAME entry-scoped
 * instance for [PlayerSurface] instead.
 */
@Composable
fun PlaybackRoute(
    videoId: String,
    manifest: PlaybackManifest,
    title: String,
) {
    val context = LocalContext.current
    val appScope = remember { (context.applicationContext as YouTrocApp).applicationScope }
    val watchProgressStore = remember { DataStoreWatchProgressStore(context) }

    val playbackViewModel: PlaybackViewModel = viewModel(
        factory = playbackViewModelFactory(
            context = context,
            watchProgressStore = watchProgressStore,
            videoId = VideoId(videoId),
            appScope = appScope,
        ),
    )
    // MAJOR M1: the concrete adapter the factory built for the ViewModel —
    // read back here (never re-constructed) so PlayerSurface renders into the
    // exact engine the ViewModel drives. Safe: this factory is the ONLY
    // production seam that builds a PlaybackViewModel, and it always injects
    // a Media3MediaPlayer as the domain port.
    val player = playbackViewModel.player as Media3MediaPlayer
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
            // MAJOR M1: the engine is released in PlaybackViewModel.onCleared()
            // (REQ-6), invoked by the framework only when this entry's
            // ViewModelStore actually clears (BACK) — NOT here, since this
            // composable is ALSO disposed on every Activity recreation while
            // the retained ViewModel (and its player) stays alive.
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
