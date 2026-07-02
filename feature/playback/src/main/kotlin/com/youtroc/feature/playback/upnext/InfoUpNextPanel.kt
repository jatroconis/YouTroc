package com.youtroc.feature.playback.upnext

import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import com.youtroc.core.ui.theme.YouTrocRed

/**
 * Presentational Info+Up-Next panel: channel/meta, a focusable+scrollable
 * description, and a read-only related-videos rail reusing `:core:ui`
 * [ShelfRow]/[VideoCardUi] (REQ-U2). Re-homed from the deleted
 * `:feature:video` module's `DetailContent`, surgically trimmed per the
 * player-upnext design gate (R4):
 *
 * - DROPS the "Reproducir" play button entirely — golden rule, a card
 *   confirm already plays the video directly; this panel is a browse
 *   surface, never a re-confirm-to-play gate.
 * - DROPS the state-change auto-focus `LaunchedEffect` — it would steal
 *   focus away from playback the moment the lazily-resolved detail arrives,
 *   even while the panel is closed. Entry focus is the caller's
 *   responsibility (see `PlayerOverlay`'s deferred focus effect, WU-2).
 * - DROPS the title line entirely — the overlay's top-left title already
 *   shows it; REQ-U2 forbids repeating it inside the panel.
 *
 * Every remaining INTERACTIVE state still binds [contentFocusRequester] to a
 * D-pad-reachable target — the project's recurring focus-trap risk (real-catalog
 * design-gate #4399, video-search design-gate #4408, video-detail design-gate
 * #4419): a message anchor for [DetailUiState.NotAvailable] (no retry — a
 * resolvable "no disponible" is terminal, BACK is the way out), and Retry for
 * [DetailUiState.Offline]/[DetailUiState.Error]. For [DetailUiState.Content],
 * NO explicit requester is bound — the description [Box] (or, when the
 * description is blank, the first related card) is simply the first
 * focusable node the container's own focus traversal reaches, exactly like
 * the original description-Box design already assumed (gate-review
 * correction #4 in the deleted module).
 *
 * Not yet referenced by `PlayerOverlay` — wiring (containment `focusGroup`,
 * entry/exit `focusProperties`) is set at the WU-2 call site, not here.
 *
 * Compose/focus/scroll glue is integration-tested only (validated on
 * device), matching this project's convention (`HomeContent`/`SearchContent`/
 * `PlayerOverlay` carry no headless Compose test either). PENDING-ON-DEVICE.
 */
@Composable
fun InfoUpNextPanel(
    state: DetailUiState,
    onRelatedClick: (VideoCardUi) -> Unit,
    onRetry: () -> Unit,
    contentFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    when (state) {
        DetailUiState.Loading -> LoadingHeader(modifier = modifier)

        is DetailUiState.Content -> PanelBody(
            detail = state.detail,
            onRelatedClick = onRelatedClick,
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

/** Transient placeholder while the lazily-resolved detail is loading. No title — the overlay's top-left title is not repeated (REQ-U2). */
@Composable
private fun LoadingHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = YouTrocDimens.overscanHorizontal, vertical = YouTrocDimens.overscanVertical),
    ) {
        Text(text = "Cargando…", style = MaterialTheme.typography.titleMedium, color = OnDarkMuted)
    }
}

@Composable
private fun PanelBody(
    detail: VideoDetailUi,
    onRelatedClick: (VideoCardUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = YouTrocDimens.overscanHorizontal, vertical = YouTrocDimens.overscanVertical),
    ) {
        // Channel name is plain, non-focusable text — RF-VID-04, channel-page
        // navigation is explicitly out of scope this slice (Fase 2+).
        Text(text = detail.channel, style = MaterialTheme.typography.titleMedium, color = OnDark)
        Text(text = detail.meta, style = MaterialTheme.typography.bodyMedium, color = OnDarkMuted)

        if (detail.description.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            var descriptionFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .onFocusChanged { descriptionFocused = it.isFocused }
                    .focusable()
                    // Focused-only border so a D-pad user landing here (and
                    // otherwise seeing no visual change) can tell focus
                    // reached the description and it is scrollable.
                    .then(
                        if (descriptionFocused) {
                            Modifier.border(2.dp, YouTrocRed, RoundedCornerShape(8.dp))
                        } else {
                            Modifier
                        },
                    )
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
            ShelfRow(title = "A continuación", videos = detail.related, onVideoClick = onRelatedClick)
        }
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

/** Focusable retry action — same tv-material Surface pattern as `RailItem`. */
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
