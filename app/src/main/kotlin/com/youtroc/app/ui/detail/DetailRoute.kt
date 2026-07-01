package com.youtroc.app.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.youtroc.core.ui.component.VideoCardUi
import com.youtroc.feature.video.DetailContent
import com.youtroc.feature.video.DetailViewModel

/**
 * Video-detail destination: railless top-level screen (mirrors
 * [com.youtroc.app.ui.search.SearchRoute]/[com.youtroc.app.ui.player.PlaybackRoute]'s
 * shape, per design) — detail is a drill-in destination, not rail-wrapped
 * Home chrome. Owns the [DetailViewModel] instance via [detailViewModelFactory]
 * and the [FocusRequester] [DetailContent] needs.
 *
 * `related`→detail navigation is recursive (RF-VID-03): [onRelatedClick] is
 * wired by [com.youtroc.app.ui.AppNavHost] to navigate to the related item's
 * OWN `detail/{relatedId}` route, so each drill-in gets its own
 * [DetailViewModel] instance, entry-scoped by the nav back stack.
 */
@Composable
fun DetailRoute(
    videoId: String,
    title: String,
    onPlay: () -> Unit,
    onRelatedClick: (VideoCardUi) -> Unit,
    onBack: () -> Unit,
) {
    val vm: DetailViewModel = viewModel(factory = detailViewModelFactory(videoId))
    val state by vm.state.collectAsState()

    val contentFocus = remember { FocusRequester() }

    BackHandler { onBack() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        DetailContent(
            fallbackTitle = title,
            state = state,
            onPlay = onPlay,
            onRelatedClick = onRelatedClick,
            onRetry = vm::retry,
            contentFocusRequester = contentFocus,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
