package com.youtroc.feature.catalog

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.youtroc.core.ui.component.ShelfRow
import com.youtroc.core.ui.component.VideoCardUi
import com.youtroc.core.ui.theme.AlmostBlack
import com.youtroc.core.ui.theme.ElevatedSurface
import com.youtroc.core.ui.theme.OnDark
import com.youtroc.core.ui.theme.OnDarkMuted
import com.youtroc.core.ui.theme.YouTrocDimens

/**
 * Presentational Home content — the "presentational HomeShell" the spec/design
 * describe, named [HomeContent] to avoid clashing with `:app`'s `HomeShell`
 * (which keeps the rail chrome + owns the [HomeViewModel] instance).
 *
 * Renders every [HomeUiState] deterministically. Every INTERACTIVE state
 * (Content, Offline, Error, Empty) attaches [contentFocusRequester] to a
 * D-pad-reachable target — never only [HomeUiState.Content]. This is the
 * project's recurring focus-trap risk: if the caller only re-requests focus
 * for Content, launching straight into Offline (no network at start) leaves
 * the Retry button unreachable and the user stuck with no way off the rail.
 * [HomeUiState.Loading] is the sole exception — it is transient and has no
 * interactive element to reach.
 *
 * Compose/focus glue like this is integration-tested only (validated on
 * device), per this project's established convention — see
 * `PlayerOverlay`/`HomeShell`, neither of which carries a headless Compose
 * test either.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    onVideoClick: (VideoCardUi) -> Unit,
    onRetry: () -> Unit,
    contentFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    when (state) {
        HomeUiState.Loading -> HomeMessage(text = "Cargando…", modifier = modifier)

        is HomeUiState.Content -> LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .focusRequester(contentFocusRequester)
                .focusProperties {
                    // Keep vertical navigation inside the grid; only LEFT reaches
                    // the rail (mirrors :app's original HomeShell containment).
                    @Suppress("DEPRECATION")
                    exit = { direction ->
                        if (direction == FocusDirection.Up || direction == FocusDirection.Down) {
                            FocusRequester.Cancel
                        } else {
                            FocusRequester.Default
                        }
                    }
                }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(YouTrocDimens.shelfSpacing),
            contentPadding = PaddingValues(bottom = YouTrocDimens.overscanVertical),
        ) {
            itemsIndexed(
                items = state.shelves,
                key = { _, shelf -> shelf.title },
                contentType = { _, _ -> "shelf" },
            ) { _, shelf ->
                ShelfRow(
                    title = shelf.title,
                    videos = shelf.videos,
                    onVideoClick = onVideoClick,
                )
            }
        }

        HomeUiState.Empty -> HomeMessage(
            text = "No hay tendencias disponibles ahora.",
            modifier = modifier,
            focusRequester = contentFocusRequester,
        )

        HomeUiState.Offline -> HomeMessage(
            text = "Sin conexión. Revisá tu red.",
            modifier = modifier,
            onRetry = onRetry,
            focusRequester = contentFocusRequester,
        )

        HomeUiState.Error -> HomeMessage(
            text = "Algo salió mal al cargar.",
            modifier = modifier,
            onRetry = onRetry,
            focusRequester = contentFocusRequester,
        )
    }
}

/**
 * Centered status message. When [onRetry] is set, renders a focusable Retry
 * action bound to [focusRequester]; otherwise (the [HomeUiState.Empty] case)
 * the message text itself becomes the focusable anchor, so this state is
 * never a D-pad dead end either — only [HomeUiState.Loading] renders with no
 * [focusRequester] at all.
 */
@Composable
private fun HomeMessage(
    text: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = OnDarkMuted,
                modifier = if (onRetry == null && focusRequester != null) {
                    Modifier.focusRequester(focusRequester).focusable()
                } else {
                    Modifier
                },
            )
            if (onRetry != null) {
                Spacer(Modifier.height(16.dp))
                RetryButton(
                    onClick = onRetry,
                    modifier = if (focusRequester != null) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

/** Focusable retry action — same tv-material Surface pattern as `RailItem`/`TvVideoCard`. */
@Composable
private fun RetryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = ElevatedSurface,
            focusedContainerColor = OnDark,
            contentColor = OnDark,
            focusedContentColor = AlmostBlack,
        ),
        modifier = modifier,
    ) {
        Text(
            text = "Reintentar",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}
