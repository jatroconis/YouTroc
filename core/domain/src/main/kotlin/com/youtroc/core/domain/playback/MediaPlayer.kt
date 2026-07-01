package com.youtroc.core.domain.playback

import kotlinx.coroutines.flow.StateFlow

/**
 * Port: control surface + observable state for playing a single [PlaybackManifest]
 * at a time. `:data:player` implements this over Media3; `:core:domain` and
 * `:feature:playback` depend only on this interface and never see Media3 types.
 *
 * Deliberately has no surface-attach method — rendering the video is an adapter
 * concern (a `PlayerSurface` composable in `:data:player`), not a domain one.
 */
interface MediaPlayer {

    /** Current playback snapshot; observers react to phase/position/duration changes. */
    val state: StateFlow<PlaybackState>

    /** Loads [manifest] and, once ready, seeks to [startAt] if provided. */
    fun setMedia(manifest: PlaybackManifest, startAt: PlaybackPosition?)

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    /** Pins video to [quality]. Audio selection and codec-chain fallback keep adapting. */
    fun selectQuality(quality: VideoQuality)

    /** Clears any pinned quality and restores automatic (ABR) rendition selection. */
    fun selectAuto()

    /** Releases underlying engine resources. Must be safe to call more than once. */
    fun release()
}
