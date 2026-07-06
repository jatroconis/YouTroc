package com.youtroc.feature.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtroc.core.domain.playback.MediaPlayer
import com.youtroc.core.domain.playback.PlaybackManifest
import com.youtroc.core.domain.playback.PlaybackPosition
import com.youtroc.core.domain.playback.PlaybackState
import com.youtroc.core.domain.playback.ResumeDecision
import com.youtroc.core.domain.playback.VideoQuality
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
 * - [title]/[channel] (REQ-HF7) back "Seguir viendo" watch history --
 *   defaulted to "" so existing positional constructions keep compiling;
 *   the composition root (`PlaybackViewModelFactory`) always supplies both,
 *   threaded all the way from the nav-arg route.
 */
class PlaybackViewModel(
    val player: MediaPlayer,
    private val watchProgressStore: WatchProgressStore,
    private val videoId: VideoId,
    private val appScope: CoroutineScope,
    private val title: String = "",
    private val channel: String = "",
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

    /**
     * Seeks to the ABSOLUTE [positionMs], clamped to `[0, duration]`. This is
     * the commit path for the overlay's decoupled scrub: the scrubber moves a
     * preview cursor freely without touching the engine, then commits a single
     * seek here once the user settles — instead of seeking on every D-pad tick,
     * which re-buffered per step and stalled the timeline.
     */
    fun seekTo(positionMs: Long) {
        val duration = playbackState.value.durationMs.coerceAtLeast(0L)
        player.seekTo(positionMs.coerceIn(0L, duration))
    }

    /**
     * Pins video to [quality] (REQ-Q4); pure delegation to the port. Audio
     * selection and the codec-chain fallback (REQ-10) keep adapting —
     * [playbackState] already surfaces the result via [MediaPlayer.state],
     * no new flow needed (REQ-Q1).
     */
    fun onSelectQuality(quality: VideoQuality) = player.selectQuality(quality)

    /** Clears any pinned quality and restores automatic (ABR) selection (REQ-Q4). */
    fun onSelectAuto() = player.selectAuto()

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
     *
     * A live stream (M3) has no meaningful "position" to resume -- it is
     * never written to watch history.
     */
    private fun persistProgress() {
        val state = playbackState.value
        if (state.isLive) return
        appScope.launch {
            watchProgressStore.save(videoId, PlaybackPosition(state.positionMs), state.durationMs, title, channel)
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
