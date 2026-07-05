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
import com.youtroc.core.domain.playback.PlaybackState
import com.youtroc.core.domain.stream.HdrFormat
import com.youtroc.core.domain.video.VideoId
import com.youtroc.core.ui.component.VideoCardUi
import com.youtroc.data.extraction.stream.StreamSource
import com.youtroc.data.persistence.DataStoreWatchProgressStore
import com.youtroc.data.player.Media3MediaPlayer
import com.youtroc.data.player.PlayerSurface
import com.youtroc.feature.playback.PlaybackViewModel
import com.youtroc.feature.playback.PlayerOverlay
import com.youtroc.feature.playback.upnext.UpNextViewModel

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
 *
 * Also builds the entry-scoped [UpNextViewModel] (player-upnext REQ-U3,
 * design D5) via [upNextViewModelFactory] — the SAME composition-root seam
 * already injecting [Media3MediaPlayer]/[DataStoreWatchProgressStore], kept
 * as a SEPARATE ViewModel/lifecycle from [PlaybackViewModel] (Media3 engine
 * vs. detail extraction are different responsibilities). Its
 * [UpNextViewModel.ensureLoaded] is wired to [PlayerOverlay]'s
 * `onPanelOpened`, so `GetVideoDetail` is invoked lazily on the FIRST panel
 * open, never at playback start.
 */
@Composable
fun PlaybackRoute(
    videoId: String,
    manifest: PlaybackManifest,
    title: String,
    hdr: HdrFormat,
    onUpNextClick: (VideoCardUi) -> Unit = {},
) {
    HdrDisplayController(hdr)

    val context = LocalContext.current
    val appScope = remember { (context.applicationContext as YouTrocApp).applicationScope }
    val watchProgressStore = remember { DataStoreWatchProgressStore(context) }
    val streamProvider = remember { (context.applicationContext as YouTrocApp).streamProvider }

    val playbackViewModel: PlaybackViewModel = viewModel(
        factory = playbackViewModelFactory(
            context = context,
            watchProgressStore = watchProgressStore,
            videoId = VideoId(videoId),
            appScope = appScope,
        ),
    )
    val upNextViewModel: UpNextViewModel = viewModel(factory = upNextViewModelFactory(videoId))
    val upNextState by upNextViewModel.state.collectAsState()
    val nextUpNextId by upNextViewModel.nextUpNextId.collectAsState()
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

    // Speculative prefetch (spec: Conservative Single Prefetch Trigger).
    // stablePlay gates BOTH the early up-next id learning (warmNextId, never
    // touches the panel's state -- design D2) and the actual prefetch. A
    // rebuffer (Ready->Buffering->Ready) re-fires this effect, but
    // UpNextViewModel.warmNextId() is latched (gate C6) so GetVideoDetail is
    // resolved at most once per video.
    val stablePlay = playbackState.phase == PlaybackState.Phase.Ready && playbackState.isPlaying
    LaunchedEffect(stablePlay) {
        if (stablePlay) upNextViewModel.warmNextId()
    }
    LaunchedEffect(stablePlay, nextUpNextId) {
        val next = nextUpNextId
        if (stablePlay && next != null && next != videoId) { // C3: never prefetch the current video itself
            val src = runCatching { streamProvider.lastSourceFor(VideoId(videoId)) }.getOrNull()
            if (src == StreamSource.ANDROID_VR) { // C1: POSITIVE gate -- null/IOS/FALLBACK ALL skip
                runCatching { VideoId(next) }.getOrNull()?.let(streamProvider::prefetch)
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { streamProvider.invalidate() }
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
            onSeekTo = playbackViewModel::seekTo,
            availableQualities = playbackState.availableQualities,
            activeQuality = playbackState.activeQuality,
            onSelectQuality = playbackViewModel::onSelectQuality,
            onSelectAuto = playbackViewModel::onSelectAuto,
            isLive = playbackState.isLive,
            // player-upnext REQ-U1..U6: in-player Info+Up-Next panel state
            // and callbacks — see the UpNextViewModel KDoc above.
            upNextState = upNextState,
            onUpNextClick = onUpNextClick,
            onUpNextRetry = upNextViewModel::retry,
            onPanelOpened = upNextViewModel::ensureLoaded,
        )
    }
}
