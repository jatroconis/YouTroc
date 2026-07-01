package com.youtroc.app.ui.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.youtroc.core.ui.theme.OnDark
import com.youtroc.core.ui.theme.OnDarkMuted

/**
 * Presentational player. It renders whatever [PlayerUiState] it is handed and owns
 * the Media3 [ExoPlayer] lifecycle for the [PlayerUiState.Ready] case. BACK always
 * pops, regardless of state, so a stuck extraction never traps the user.
 */
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (state) {
            is PlayerUiState.Ready -> {
                VideoSurface(url = state.url)
                TitleScrim(title = state.title)
            }

            PlayerUiState.Loading -> StatusView(message = "Cargando…", showRetry = false, onRetry = onRetry, onBack = onBack)
            PlayerUiState.NotAvailable -> StatusView(message = "Este video no está disponible.", showRetry = false, onRetry = onRetry, onBack = onBack)
            PlayerUiState.Offline -> StatusView(message = "Sin conexión. Revisá tu red.", showRetry = true, onRetry = onRetry, onBack = onBack)
            PlayerUiState.Failed -> StatusView(message = "Algo salió mal al preparar el video.", showRetry = true, onRetry = onRetry, onBack = onBack)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // remember(url) builds the player for this URL; DisposableEffect(url) releases it
    // when the URL changes or the surface leaves composition. Together: no leak.
    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(url) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
    )
}

/** Top gradient scrim carrying the video title — pure decoration, never focusable. */
@Composable
private fun TitleScrim(title: String, modifier: Modifier = Modifier) {
    if (title.isBlank()) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0.0f to Color.Black.copy(alpha = 0.55f),
                    1.0f to Color.Transparent,
                ),
            )
            .padding(horizontal = 40.dp, vertical = 24.dp),
    ) {
        Text(
            text = title,
            color = OnDark,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusView(
    message: String,
    showRetry: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
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
