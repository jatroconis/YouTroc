@file:OptIn(ExperimentalComposeUiApi::class)

package com.youtroc.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.youtroc.app.YouTrocApp
import com.youtroc.data.player.Media3MediaPlayer
import com.youtroc.data.player.PlayerSurface
import com.youtroc.feature.playback.ShortsOverlay
import com.youtroc.feature.playback.ShortsPlaybackViewModel
import com.youtroc.feature.playback.ShortsQueueItem

/**
 * Composition root for the Shorts pager screen (REQ-HF12, F4 binding
 * decision): wraps [PlayerSurface] in an aspect-ratio box sized from
 * [Media3MediaPlayer.videoAspectRatio] (N6) — this box lives HERE, not in
 * `:data:player`/`:feature:playback`, mirroring [PlaybackRoute]'s existing
 * M1 convention of casting `player as Media3MediaPlayer` in the composition
 * root, so [PlayerSurface] itself and landscape playback stay completely
 * unaware of Shorts.
 *
 * DOWN/UP page through [items] via [ShortsPlaybackViewModel.next]/
 * [ShortsPlaybackViewModel.previous]; BACK always pops to Home. There is no
 * other focusable chrome (recon #4604: Suscribirse/like/comments omitted),
 * so this screen's own root is the sole D-pad target — no deferred-focus
 * dance is needed the way [PlayerOverlay] requires for its many zones.
 */
@Composable
fun ShortsPlayerRoute(
    items: List<ShortsQueueItem>,
    startId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val streamProvider = remember { (context.applicationContext as YouTrocApp).streamProvider }
    val startIndex = remember(items, startId) {
        items.indexOfFirst { it.id == startId }.let { if (it >= 0) it else 0 }
    }

    val viewModel: ShortsPlaybackViewModel = viewModel(
        factory = shortsPlaybackViewModelFactory(context, streamProvider, items, startIndex),
    )
    // MAJOR M1 (mirrors PlaybackRoute): the concrete adapter the factory built
    // for the ViewModel, read back here — never re-constructed — so the
    // surface + aspect box render into the exact engine the ViewModel drives.
    val player = viewModel.player as Media3MediaPlayer
    val aspect by player.videoAspectRatio.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val currentItem = items[currentIndex]
    val nextTitle = items.getOrNull(currentIndex + 1)?.title

    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { rootFocus.requestFocus() }
    }

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionDown -> { viewModel.next(); true }
                        Key.DirectionUp -> { viewModel.previous(); true }
                        else -> false
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(aspect)
                .align(Alignment.Center),
        ) {
            PlayerSurface(player = player, modifier = Modifier.fillMaxSize())
        }
        ShortsOverlay(
            title = currentItem.title,
            channel = currentItem.channel,
            nextTitle = nextTitle,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
