package com.youtroc.data.player

import androidx.media3.common.Player
import com.youtroc.core.domain.playback.PlaybackState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure, JVM-only verification of the ExoPlayer-state -> domain [PlaybackState.Phase]
 * mapping. No ExoPlayer instance is created — only the same `int` constants
 * ExoPlayer emits through `Player.Listener.onPlaybackStateChanged` are fed in,
 * so this needs no device/emulator/Robolectric.
 */
class PlaybackPhaseMapperTest {

    @Test
    fun `maps STATE_IDLE to Idle`() {
        assertEquals(PlaybackState.Phase.Idle, PlaybackPhaseMapper.map(Player.STATE_IDLE, hasError = false))
    }

    @Test
    fun `maps STATE_BUFFERING to Buffering`() {
        assertEquals(PlaybackState.Phase.Buffering, PlaybackPhaseMapper.map(Player.STATE_BUFFERING, hasError = false))
    }

    @Test
    fun `maps STATE_READY to Ready`() {
        assertEquals(PlaybackState.Phase.Ready, PlaybackPhaseMapper.map(Player.STATE_READY, hasError = false))
    }

    @Test
    fun `maps STATE_ENDED to Ended`() {
        assertEquals(PlaybackState.Phase.Ended, PlaybackPhaseMapper.map(Player.STATE_ENDED, hasError = false))
    }

    @Test
    fun `an error takes precedence over any underlying playback state`() {
        assertEquals(PlaybackState.Phase.Error, PlaybackPhaseMapper.map(Player.STATE_READY, hasError = true))
        assertEquals(PlaybackState.Phase.Error, PlaybackPhaseMapper.map(Player.STATE_BUFFERING, hasError = true))
    }
}
