package com.youtroc.feature.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtroc.core.domain.playback.MediaPlayer
import com.youtroc.core.domain.playback.PlaybackManifest
import com.youtroc.core.domain.playback.PlaybackPosition
import com.youtroc.core.domain.playback.PlaybackState
import com.youtroc.core.domain.playback.ResumeDecision
import com.youtroc.core.domain.playback.WatchProgressStore
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Container that drives the [MediaPlayer] port for a single video. Knows
 * nothing about Media3/ExoPlayer or Compose — only the domain ports — so it
 * is fully testable with fakes (see `PlaybackViewModelTest`).
 *
 * - [start] loads a [PlaybackManifest] and begins playback. [ResumeDecision]
 *   (REQ-12) needs the CURRENT video's duration, which the manifest itself
 *   does not carry — only the live player learns it once buffered — so the
 *   auto-seek is applied lazily, the first time [playbackState] reports a
 *   real `durationMs` (see [maybeApplyResume]), via [MediaPlayer.seekTo].
 * - [pause] pauses playback AND persists the current position in one call —
 *   this single method backs both REQ-13 (pause on `ON_STOP`/`ON_PAUSE`) and
 *   REQ-12 (position saved when the user leaves before the video ends);
 *   design's lifecycle judgment call wires a `LifecycleEventObserver` to it.
 */
class PlaybackViewModel(
    private val player: MediaPlayer,
    private val watchProgressStore: WatchProgressStore,
    private val videoId: VideoId,
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = player.state

    private var savedPosition: PlaybackPosition? = null
    private var resumeApplied = false

    init {
        viewModelScope.launch {
            player.state.collect(::maybeApplyResume)
        }
    }

    /** Loads [manifest] and begins playback; resume is applied once duration is known. */
    fun start(manifest: PlaybackManifest) {
        resumeApplied = false
        viewModelScope.launch {
            savedPosition = watchProgressStore.load(videoId)
            player.setMedia(manifest, startAt = null)
            player.play()
        }
    }

    fun togglePlayPause() {
        if (playbackState.value.isPlaying) pause() else player.play()
    }

    /** Seeks by [deltaMs] (negative = backward), clamped to `[0, duration]`. */
    fun seekBy(deltaMs: Long) {
        val current = playbackState.value
        val duration = current.durationMs.coerceAtLeast(0L)
        val target = (current.positionMs + deltaMs).coerceIn(0L, duration)
        player.seekTo(target)
    }

    /** Pauses AND persists the current position — see class doc. */
    fun pause() {
        player.pause()
        persistProgress()
    }

    /** Releases the underlying engine — called when the surface leaves composition (REQ-6). */
    fun releasePlayer() {
        player.release()
    }

    private fun persistProgress() {
        val state = playbackState.value
        viewModelScope.launch {
            watchProgressStore.save(videoId, PlaybackPosition(state.positionMs), state.durationMs)
        }
    }

    private fun maybeApplyResume(state: PlaybackState) {
        if (resumeApplied || state.durationMs <= 0L) return
        resumeApplied = true
        val startAt = ResumeDecision.startAt(savedPosition, state.durationMs)
        if (startAt != null) {
            player.seekTo(startAt.positionMs)
        }
    }
}
