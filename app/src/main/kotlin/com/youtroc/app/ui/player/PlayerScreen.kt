package com.youtroc.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.youtroc.core.ui.component.VideoCardUi
import com.youtroc.core.ui.theme.OnDark
import com.youtroc.core.ui.theme.OnDarkMuted
import com.youtroc.core.ui.theme.YouTrocRed

/**
 * Player destination: dispatches on the extraction [PlayerUiState] (REQ-2)
 * into either a status view (Loading/NotAvailable/Offline/Failed) or, once a
 * [PlayerUiState.Ready] manifest is resolved, the real playback session
 * ([PlaybackRoute]) — the composition root that wires the `:data:player`/
 * `:data:persistence` adapters and the `:feature:playback` overlay together.
 *
 * [title] (the nav-arg title, MAJOR M5) is rendered as a persistent top-left
 * label for every NON-`Ready` state — `Loading`/`NotAvailable`/`Offline`/`Failed`
 * — so it is drawn immediately, before extraction (and therefore
 * [PlaybackRoute]) exists at all, instead of only once `Ready`. In `Ready`,
 * [PlaybackRoute] -> `PlayerOverlay` (`:feature:playback`) renders its OWN
 * always-on title, so this label is skipped there (MINOR fix #2) — rendering
 * both at once double-drew the same title stacked on top of itself.
 *
 * BACK always pops to Home (REQ-5) from every state. [PlaybackRoute]'s
 * overlay collapses itself first while visible (its own `BackHandler` is
 * enabled only then); once hidden, that handler is disabled and this
 * unconditional one wins.
 */
@Composable
fun PlayerScreen(
    videoId: String,
    title: String,
    state: PlayerUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onUpNextClick: (VideoCardUi) -> Unit = {},
) {
    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (state) {
            is PlayerUiState.Ready -> PlaybackRoute(
                videoId = videoId,
                manifest = state.manifest,
                title = state.title,
                hdr = state.hdr,
                onUpNextClick = onUpNextClick,
            )

            PlayerUiState.Loading -> StatusView(
                message = "Cargando…",
                showSpinner = true,
                showRetry = false,
                onRetry = onRetry,
                onBack = onBack,
            )

            PlayerUiState.NotAvailable -> StatusView(
                message = "Este video no está disponible.",
                showSpinner = false,
                showRetry = false,
                onRetry = onRetry,
                onBack = onBack,
            )

            PlayerUiState.Offline -> StatusView(
                message = "Sin conexión. Revisá tu red.",
                showSpinner = false,
                showRetry = true,
                onRetry = onRetry,
                onBack = onBack,
            )

            PlayerUiState.Failed -> StatusView(
                message = "Algo salió mal al preparar el video.",
                showSpinner = false,
                showRetry = true,
                onRetry = onRetry,
                onBack = onBack,
            )
        }

        // MINOR fix #2: skip this label in Ready — PlaybackRoute's PlayerOverlay
        // already renders its own always-on title there; rendering both
        // double-drew the title stacked on top of itself.
        if (state !is PlayerUiState.Ready) {
            TitleLabel(title = title, modifier = Modifier.align(Alignment.TopStart))
        }
    }
}

/**
 * Persistent top-left title label (REQ-14, MAJOR M5) — drawn from the nav-arg
 * title for every non-`Ready` [PlayerUiState] (call site above skips it in
 * `Ready`, MINOR fix #2), so it never waits on extraction. Local duplicate of
 * `:feature:playback`'s private `TitleLabel` (same convention as
 * [StatusSpinner] below): that composable is private to the feature module
 * and only exists once [PlaybackRoute] composes (`Ready`), which is exactly
 * the gap the non-`Ready` rendering fixes.
 */
@Composable
private fun TitleLabel(title: String, modifier: Modifier = Modifier) {
    if (title.isBlank()) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0.0f to Color.Black.copy(alpha = 0.55f), 1.0f to Color.Transparent))
            .padding(horizontal = 40.dp, vertical = 24.dp),
    ) {
        Text(text = title, color = OnDark, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
private fun StatusView(
    message: String,
    showSpinner: Boolean,
    showRetry: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (showSpinner) {
                StatusSpinner()
            }
            Text(text = message, color = OnDarkMuted, style = MaterialTheme.typography.titleMedium)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showRetry) {
                    Button(onClick = onRetry) { Text("Reintentar") }
                }
                Button(onClick = onBack) { Text("Volver") }
            }
        }
    }
}

/**
 * Real buffering indicator for the pre-extraction [PlayerUiState.Loading]
 * state (REQ-14 — "not text-only"). Same Canvas-based approach as
 * `:feature:playback`'s in-stream buffering spinner (neither
 * `androidx.tv.material3` 1.1.0 nor this module ships a progress indicator),
 * kept local here since it renders before any manifest/player exists — this
 * screen never depends on the feature module's private composables.
 */
@Composable
private fun StatusSpinner(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 900, easing = LinearEasing)),
        label = "loadingRotation",
    )
    Canvas(modifier = modifier.size(48.dp).rotate(rotation)) {
        drawArc(
            color = YouTrocRed,
            startAngle = 0f,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
