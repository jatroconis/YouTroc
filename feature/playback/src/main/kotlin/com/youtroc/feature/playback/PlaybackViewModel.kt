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
import kotlinx.coroutines.CoroutineScope
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
 * - [appScope] backs progress persistence (BLOCKER B1): `viewModelScope` is
 *   cancelled the moment this entry's `ViewModelStore` clears — e.g. BACK
 *   popping the destination — which can happen BEFORE an in-flight
 *   `WatchProgressStore.save()` suspend write commits, silently losing the
 *   position. [appScope] is owned by the composition root's `Application`
 *   (outlives any single NavBackStackEntry), so the save launched from
 *   [pause]/[onCleared] survives the teardown that cancels `viewModelScope`.
 * - [player] is exposed publicly (not just as a constructor dependency) so
 *   the composition root can read back the SAME instance for rendering
 *   (MAJOR M1): see `PlaybackRoute`, which builds this player inside the
 *   ViewModel factory's `initializer` (entry-scoped) instead of a
 *   composition-scoped `remember`, so it survives Activity recreation
 *   alongside this ViewModel instead of being torn down and rebuilt on every
 *   recomposition while the retained ViewModel keeps driving the old one.
 */
class PlaybackViewModel(
    val player: MediaPlayer,
    private val watchProgressStore: WatchProgressStore,
    private val videoId: VideoId,
    private val appScope: CoroutineScope,
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

    /**
     * Releases the engine and does a final persist (BLOCKER B1 + MAJOR M1).
     * The framework invokes this ONLY when this entry's `ViewModelStore`
     * actually clears (BACK popping the destination) — NOT on every
     * composition disposal, which is what made the composable's own
     * `onDispose` an unreliable place to release the player across Activity
     * recreation (M1).
     */
    public override fun onCleared() {
        persistProgress()
        player.release()
    }

    /**
     * Persists via [appScope] (BLOCKER B1) — NOT `viewModelScope`, which may
     * already be cancelled (or about to be, mid-write) by the time this
     * entry's `ViewModelStore` clears.
     */
    private fun persistProgress() {
        val state = playbackState.value
        appScope.launch {
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
