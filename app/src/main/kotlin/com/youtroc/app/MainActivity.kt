package com.youtroc.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.youtroc.core.domain.playback.GetPlayableStreams
import com.youtroc.core.domain.stream.StreamKind
import com.youtroc.core.domain.stream.StreamResult
import com.youtroc.core.domain.video.VideoId
import com.youtroc.data.extraction.NewPipeStreamProvider
import kotlinx.coroutines.launch

/**
 * Hito 0 walking skeleton: resolve a hardcoded video's ad-free streams through the
 * domain use case and the extraction adapter, then play the first one with Media3.
 *
 * No real UI yet — this exists only to prove the vertical slice end-to-end on the TV:
 * InnerTube extraction -> playable URL -> ExoPlayer -> pixels, ad-free.
 */
@UnstableApi
class MainActivity : ComponentActivity() {

    private val getPlayableStreams = GetPlayableStreams(NewPipeStreamProvider())

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val playerView = PlayerView(this)
        setContentView(playerView)

        val exo = ExoPlayer.Builder(this).build()
        player = exo
        playerView.player = exo

        lifecycleScope.launch {
            when (val result = getPlayableStreams(VideoId(DEMO_VIDEO_ID))) {
                is StreamResult.Success -> {
                    // Prefer a progressive (muxed) stream: a single URL with video+audio,
                    // the simplest thing to prove playback. Adaptive DASH comes later.
                    val stream = result.streams.streams.firstOrNull { it.kind == StreamKind.MUXED }
                        ?: result.streams.streams.first()
                    exo.setMediaItem(MediaItem.fromUri(stream.url))
                    exo.prepare()
                    exo.playWhenReady = true
                }

                else -> {
                    // Hito 0: surface the typed outcome in logcat; real error/empty/offline
                    // UI states arrive with the Compose milestone.
                    Log.w(TAG, "playback unavailable: $result")
                }
            }
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    private companion object {
        const val TAG = "youtroc"
        const val DEMO_VIDEO_ID = "dQw4w9WgXcQ"
    }
}
