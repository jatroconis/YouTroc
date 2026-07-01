package com.youtroc.app.ui.search

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
import com.youtroc.feature.search.SearchContent
import com.youtroc.feature.search.SearchViewModel

/**
 * Search destination: railless top-level screen (mirrors
 * [com.youtroc.app.ui.player.PlaybackRoute]/[com.youtroc.app.ui.player.PlayerScreen]'s
 * shape, per design) — search is a drill-in destination, not rail-wrapped
 * Home chrome. Owns the [SearchViewModel] instance via
 * [searchViewModelFactory] and the two [FocusRequester]s [SearchContent]
 * needs.
 */
@Composable
fun SearchRoute(
    onVideoClick: (VideoCardUi) -> Unit,
    onBack: () -> Unit,
) {
    val vm: SearchViewModel = viewModel(factory = searchViewModelFactory())
    val state by vm.state.collectAsState()

    val fieldFocus = remember { FocusRequester() }
    val resultsFocus = remember { FocusRequester() }

    BackHandler { onBack() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        SearchContent(
            state = state,
            onSubmit = vm::search,
            onRetry = vm::retry,
            onVideoClick = onVideoClick,
            fieldFocusRequester = fieldFocus,
            resultsFocusRequester = resultsFocus,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
