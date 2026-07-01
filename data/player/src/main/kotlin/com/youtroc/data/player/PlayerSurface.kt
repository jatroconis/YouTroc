package com.youtroc.data.player

import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Renders [player]'s video output via a [SurfaceView] — never `TextureView`
 * (RF-PLAY-05) — bound to the underlying ExoPlayer instance.
 *
 * This is the adapter half of the surface-attach requirement (REQ-8): the
 * domain [com.youtroc.core.domain.playback.MediaPlayer] port has no
 * surface-attach method by design (rendering is not a domain concern).
 * `:feature:playback` never sees this composable — it receives the surface
 * only as an opaque slot; `:app` (the composition root) is the only caller
 * that wires the SAME injected [player] to both this composable and the
 * feature's control/state overlay.
 */
@Composable
fun PlayerSurface(player: Media3MediaPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context -> SurfaceView(context).also(player::bindSurface) },
    )
}
