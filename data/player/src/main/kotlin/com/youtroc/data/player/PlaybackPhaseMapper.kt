package com.youtroc.data.player

import androidx.media3.common.Player
import com.youtroc.core.domain.playback.PlaybackState

/**
 * Maps ExoPlayer's raw state ([Player.STATE_*] + error presence) to the
 * domain's [PlaybackState.Phase]. Pure and total — no ExoPlayer instance is
 * needed, so it is unit-tested directly with the same `int` constants
 * ExoPlayer emits through `Player.Listener.onPlaybackStateChanged`.
 */
internal object PlaybackPhaseMapper {

    fun map(playbackState: Int, hasError: Boolean): PlaybackState.Phase = when {
        hasError -> PlaybackState.Phase.Error
        playbackState == Player.STATE_IDLE -> PlaybackState.Phase.Idle
        playbackState == Player.STATE_BUFFERING -> PlaybackState.Phase.Buffering
        playbackState == Player.STATE_READY -> PlaybackState.Phase.Ready
        playbackState == Player.STATE_ENDED -> PlaybackState.Phase.Ended
        else -> PlaybackState.Phase.Idle
    }
}
