package com.youtroc.feature.video

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
 * Presentational video-detail content: título/canal/meta, a "Reproducir"
 * action, a focusable+scrollable description, and a read-only
 * related-videos shelf reusing `:core:ui` [ShelfRow]/[VideoCardUi].
 *
 * Every INTERACTIVE state binds [contentFocusRequester] to a D-pad-reachable
 * target — the project's recurring focus-trap risk (real-catalog design-gate
 * #4399, video-search design-gate #4408, reapplied here per design-gate
 * #4419): the "Reproducir" button for [DetailUiState.Content] — a composed
 * CONTAINER child inside the `verticalScroll` [Column], eagerly composed and
 * NEVER a lazy item (the search MINOR-1 lesson) — a message anchor for
 * [DetailUiState.NotAvailable] (no retry — a resolvable "no disponible" is
 * terminal, BackHandler is the way out), and Retry for
 * [DetailUiState.Offline]/[DetailUiState.Error]. [DetailUiState.Loading] is
 * the sole exception — transient, shows [fallbackTitle] with no focus
 * target.
 *
 * The description [Box] (gate-review correction #4) is independently
 * focusable and scrollable: D-pad DOWN from "Reproducir" can land on it and
 * scroll a long description before continuing to the related shelf — a
 * standard TV pattern, no separate [FocusRequester] needed since it is
 * simply the next node in traversal order.
 *
 * Compose/focus/scroll glue is integration-tested only (validated on
 * device), matching this project's convention (`HomeContent`/`SearchContent`/
 * `PlayerOverlay` carry no headless Compose test either).
 */
@Composable
fun DetailContent(
    fallbackTitle: String,
    state: DetailUiState,
    onPlay: () -> Unit,
    onRelatedClick: (VideoCardUi) -> Unit,
    onRetry: () -> Unit,
    contentFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state::class) {
        when (state) {
            is DetailUiState.Content, DetailUiState.NotAvailable, DetailUiState.Offline, DetailUiState.Error ->
                runCatching { contentFocusRequester.requestFocus() }

            DetailUiState.Loading -> Unit
        }
    }

    when (state) {
        DetailUiState.Loading -> LoadingHeader(title = fallbackTitle, modifier = modifier)

        is DetailUiState.Content -> DetailBody(
            detail = state.detail,
            onPlay = onPlay,
            onRelatedClick = onRelatedClick,
            playFocusRequester = contentFocusRequester,
            modifier = modifier,
        )

        DetailUiState.NotAvailable -> DetailMessage(
            text = "Este video no está disponible.",
            focusRequester = contentFocusRequester,
            modifier = modifier,
        )

        DetailUiState.Offline -> DetailMessage(
            text = "Sin conexión. Revisá tu red.",
            onRetry = onRetry,
            focusRequester = contentFocusRequester,
            modifier = modifier,
        )

        DetailUiState.Error -> DetailMessage(
            text = "Algo salió mal al cargar el video.",
            onRetry = onRetry,
            focusRequester = contentFocusRequester,
            modifier = modifier,
        )
    }
}

/** Shows the nav-arg title immediately, before [DetailUiState.Content] resolves — no blank flash. */
@Composable
private fun LoadingHeader(title: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = YouTrocDimens.overscanHorizontal, vertical = YouTrocDimens.overscanVertical),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall, color = OnDark)
        Spacer(Modifier.height(8.dp))
        Text(text = "Cargando…", style = MaterialTheme.typography.titleMedium, color = OnDarkMuted)
    }
}

@Composable
private fun DetailBody(
    detail: VideoDetailUi,
    onPlay: () -> Unit,
    onRelatedClick: (VideoCardUi) -> Unit,
    playFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = YouTrocDimens.overscanHorizontal, vertical = YouTrocDimens.overscanVertical),
    ) {
        Text(text = detail.title, style = MaterialTheme.typography.headlineSmall, color = OnDark)
        Spacer(Modifier.height(8.dp))

        // Channel name is plain, non-focusable text — RF-VID-04, channel-page
        // navigation is explicitly out of scope this slice (Fase 2+).
        Text(text = detail.channel, style = MaterialTheme.typography.titleMedium, color = OnDarkMuted)
        Text(text = detail.meta, style = MaterialTheme.typography.bodyMedium, color = OnDarkMuted)
        Spacer(Modifier.height(20.dp))

        PlayButton(onClick = onPlay, modifier = Modifier.focusRequester(playFocusRequester))

        if (detail.description.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .focusable()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = detail.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnDarkMuted,
                )
            }
        }

        if (detail.related.isNotEmpty()) {
            Spacer(Modifier.height(YouTrocDimens.shelfSpacing))
            ShelfRow(title = "Relacionados", videos = detail.related, onVideoClick = onRelatedClick)
        }
    }
}

/** Focusable primary action — same tv-material Surface pattern as `RetryButton`/`RailItem`. */
@Composable
private fun PlayButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            text = "Reproducir",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

/**
 * Centered status message. When [onRetry] is set, renders a focusable Retry
 * action bound to [focusRequester]; otherwise (the [DetailUiState.NotAvailable]
 * case) the message text itself becomes the focusable anchor — prevents a
 * D-pad dead end (mirrors `:feature:catalog`'s `HomeContent` Empty pattern).
 */
@Composable
private fun DetailMessage(
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

/** Focusable retry action — same tv-material Surface pattern as `PlayButton`/`RailItem`. */
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
