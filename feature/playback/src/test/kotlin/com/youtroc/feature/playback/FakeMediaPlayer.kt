package com.youtroc.feature.playback

import com.youtroc.core.domain.playback.MediaPlayer
import com.youtroc.core.domain.playback.PlaybackManifest
import com.youtroc.core.domain.playback.PlaybackPosition
import com.youtroc.core.domain.playback.PlaybackState
import com.youtroc.core.domain.playback.VideoQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory test double for the [MediaPlayer] port — no Media3/Android
 * involved. [emitReady] simulates the engine reporting a real duration once
 * buffered, which is what [PlaybackViewModel] waits for before applying
 * [com.youtroc.core.domain.playback.ResumeDecision].
 */
class FakeMediaPlayer : MediaPlayer {

    private val mutableState = MutableStateFlow(
        PlaybackState(phase = PlaybackState.Phase.Idle, isPlaying = false, positionMs = 0L, durationMs = 0L),
    )
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    val isPlaying: Boolean get() = mutableState.value.isPlaying

    var lastManifest: PlaybackManifest? = null
        private set
    var lastStartAt: PlaybackPosition? = null
        private set
    val seekedTo: MutableList<Long> = mutableListOf()
    var released: Boolean = false
        private set

    /** Last call made through [selectQuality]/[selectAuto], for assertions. */
    var lastQualitySelection: QualitySelection? = null
        private set

    sealed interface QualitySelection {
        data class Manual(val quality: VideoQuality) : QualitySelection
        data object Auto : QualitySelection
    }

    override fun setMedia(manifest: PlaybackManifest, startAt: PlaybackPosition?) {
        lastManifest = manifest
        lastStartAt = startAt
        mutableState.value = mutableState.value.copy(phase = PlaybackState.Phase.Buffering)
    }

    override fun play() {
        mutableState.value = mutableState.value.copy(isPlaying = true)
    }

    override fun pause() {
        mutableState.value = mutableState.value.copy(isPlaying = false)
    }

    override fun seekTo(positionMs: Long) {
        seekedTo.add(positionMs)
        mutableState.value = mutableState.value.copy(positionMs = positionMs)
    }

    override fun selectQuality(quality: VideoQuality) {
        lastQualitySelection = QualitySelection.Manual(quality)
        mutableState.value = mutableState.value.copy(activeQuality = quality)
    }

    override fun selectAuto() {
        lastQualitySelection = QualitySelection.Auto
        mutableState.value = mutableState.value.copy(activeQuality = null)
    }

    override fun release() {
        released = true
    }

    /** Test helper: simulates the engine reporting readiness with a known duration. */
    fun emitReady(durationMs: Long, positionMs: Long = 0L, isLive: Boolean = false) {
        mutableState.value = mutableState.value.copy(
            phase = PlaybackState.Phase.Ready,
            positionMs = positionMs,
            durationMs = durationMs,
            isLive = isLive,
        )
    }

    /** Test helper: simulates the engine publishing the qualities the current manifest exposes. */
    fun emitQualities(available: List<VideoQuality>, active: VideoQuality? = null) {
        mutableState.value = mutableState.value.copy(availableQualities = available, activeQuality = active)
    }
}
