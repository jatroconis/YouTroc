package com.youtroc.data.player

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.view.SurfaceView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.youtroc.core.domain.playback.MediaPlayer as MediaPlayerPort
import com.youtroc.core.domain.playback.PlaybackManifest
import com.youtroc.core.domain.playback.PlaybackPosition
import com.youtroc.core.domain.playback.PlaybackState
import com.youtroc.core.domain.playback.QualitySelectionPolicy
import com.youtroc.core.domain.playback.VideoQuality
import com.youtroc.core.domain.playback.VideoQualityCatalog
import com.youtroc.core.domain.playback.VideoRendition
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * [MediaPlayerPort] implementation over Media3's [ExoPlayer]. Builds the
 * media source from [PlaybackManifest.kind]:
 * - **DASH** (primary): the assembled multi-rendition MPD is parsed in-memory
 *   into a [androidx.media3.exoplayer.dash.manifest.DashManifest] and handed
 *   straight to [DashMediaSource.Factory] — no extra network fetch of the
 *   manifest itself, since `:data:extraction` already assembled it. Real ABR
 *   (REQ-9) and codec-chain fallback across renditions (REQ-10) both come
 *   from here.
 * - **PROGRESSIVE**: a single muxed [ProgressiveMediaSource]. No ABR.
 * - **MERGED**: [MergingMediaSource] of a video-only + an audio-only
 *   [ProgressiveMediaSource] — last resort; [PlaybackManifest] itself already
 *   forbids a lone audio-less video source.
 *
 * Hardware-first codec selection ([HW_FIRST_MEDIA_CODEC_SELECTOR]) and
 * `setEnableDecoderFallback(true)` are wired through [DefaultRenderersFactory].
 * Codec-CHAIN fallback (AV1 -> VP9 -> H.264 across DASH renditions) comes from
 * [DefaultTrackSelector]'s mixed-MIME-type adaptiveness, which is a DIFFERENT
 * mechanism from decoder fallback (same-format HW/SW swap) — see REQ-10.
 *
 * Integration-only: exercising this class needs a real ExoPlayer instance
 * bound to Android's MediaCodec/Surface stack, so it cannot be meaningfully
 * unit-tested with fakes — it is validated on-device (TCL 55C6K). The pure
 * ExoPlayer-state -> [PlaybackState.Phase] mapping it delegates to
 * ([PlaybackPhaseMapper]) IS unit-tested.
 *
 * **Must be constructed on the main thread.** [ExoPlayer.Builder.build] binds
 * the player to the constructing thread's [Looper]; constructing this off the
 * main thread produces a player that silently can't be driven from the UI
 * thread. The constructor fails fast with [IllegalArgumentException] if
 * called off the main thread rather than surfacing a confusing ExoPlayer
 * error later.
 */
class Media3MediaPlayer(context: Context) : MediaPlayerPort {

    init {
        require(Looper.myLooper() == Looper.getMainLooper()) {
            "Media3MediaPlayer must be constructed on the main thread: " +
                "ExoPlayer.Builder binds to the constructing thread's Looper."
        }
    }

    private val appContext = context.applicationContext
    private val dataSourceFactory = DefaultDataSource.Factory(appContext)

    private val renderersFactory = DefaultRenderersFactory(appContext)
        .setMediaCodecSelector(HW_FIRST_MEDIA_CODEC_SELECTOR)
        .setEnableDecoderFallback(true)

    private val trackSelector = DefaultTrackSelector(appContext).apply {
        setParameters(
            parameters.buildUpon()
                .setPreferredVideoMimeTypes(MimeTypes.VIDEO_AV1, MimeTypes.VIDEO_VP9, MimeTypes.VIDEO_H264)
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                // DashManifestMerger groups mp4a/opus audio into one shared AdaptationSet;
                // this lets the selector adapt audio across codecs within that group too.
                .setAllowAudioMixedMimeTypeAdaptiveness(true)
                .setMaxVideoSize(MAX_VIDEO_WIDTH, MAX_VIDEO_HEIGHT) // C6K caps out at 4K
                .build(),
        )
    }

    private val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setRenderersFactory(renderersFactory)
        .setTrackSelector(trackSelector)
        .build()

    private val mutableState = MutableStateFlow(
        PlaybackState(phase = PlaybackState.Phase.Idle, isPlaying = false, positionMs = 0L, durationMs = 0L),
    )

    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    /** Tied to this player's lifecycle; cancelled in [release]. Main dispatcher: publishState() only touches player/state on the constructing (main) thread. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Emits [publishState] every [POSITION_TICK_MS] while playing so position-dependent UI (scrubber, resume-save) sees a live position, not one frozen between phase/isPlaying/error callbacks. */
    private var positionTickerJob: Job? = null

    /** Qualities the current manifest exposes, refreshed on every [Player.Listener.onTracksChanged]. */
    private var availableQualities: List<VideoQuality> = emptyList()

    /** User-pinned height intent; `null` means Automatic (ABR). Drives [PlaybackState.activeQuality]. */
    private var pinnedHeight: Int? = null

    /** True when the active manifest is a live delivery (LIVE_HLS/LIVE_DASH); set in [setMedia] BEFORE `prepare()` so the first [publishState] reflects it. Source of truth is the manifest kind, not ExoPlayer timing (S1). */
    private var isLive = false

    init {
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) = publishState()
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    publishState()
                    updatePositionTicker(isPlaying)
                }
                override fun onPlayerErrorChanged(error: PlaybackException?) = publishState()
                override fun onPlayerError(error: PlaybackException) = recoverFromUnservableRendition(error)

                override fun onTracksChanged(tracks: Tracks) {
                    availableQualities = VideoQualityCatalog.from(videoRenditionsOf(tracks))
                    // MAJOR-7: a pinned height may no longer be available after the manifest
                    // refreshes — re-resolve against the new catalog instead of only reacting
                    // to selectQuality() calls, so the fallback (REQ-Q5) applies reactively.
                    pinnedHeight?.let { pinned ->
                        val resolved = QualitySelectionPolicy.resolve(pinned, availableQualities)
                        if (resolved != null) applyHeightWindow(resolved.heightPx) else applyAutoWindow()
                    }
                    publishState()
                }
            },
        )
    }

    private fun updatePositionTicker(isPlaying: Boolean) {
        positionTickerJob?.cancel()
        positionTickerJob = if (isPlaying) {
            scope.launch {
                while (isActive) {
                    delay(POSITION_TICK_MS)
                    publishState()
                }
            }
        } else {
            null
        }
    }

    /**
     * Binds the surface Media3 renders into. Deliberately NOT a [MediaPlayerPort]
     * method — surface attach is an adapter concern (design's surface-as-adapter
     * decision), wired here and from [PlayerSurface] only.
     */
    internal fun bindSurface(surfaceView: SurfaceView) {
        player.setVideoSurfaceView(surfaceView)
    }

    override fun setMedia(manifest: PlaybackManifest, startAt: PlaybackPosition?) {
        // REQ-Q6: each new video starts on Automatic; the prior video's pin never carries over.
        applyAutoWindow()
        availableQualities = emptyList()
        // S1: set BEFORE setMediaSource()/prepare() so the first publishState() already reflects live.
        isLive = manifest.kind.isLive
        val startPositionMs = startAt?.positionMs ?: C.TIME_UNSET
        player.setMediaSource(mediaSourceFor(manifest), startPositionMs)
        player.prepare()
    }

    override fun play() = player.play()

    override fun pause() = player.pause()

    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    override fun selectQuality(quality: VideoQuality) {
        val target = QualitySelectionPolicy.resolve(quality.heightPx, availableQualities)
        if (target != null) applyHeightWindow(target.heightPx) else applyAutoWindow()
        publishState()
    }

    override fun selectAuto() {
        applyAutoWindow()
        publishState()
    }

    /**
     * Recover from a FATAL segment-load error instead of dying to a black
     * screen. Observed on-device (TCL 55C6K): selecting 4K on the android_vr
     * manifest makes the 2160p Representation's googlevideo segment return HTTP
     * 403, ExoPlayer raises [PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS] as
     * a fatal error, the player goes IDLE and tears down the codec/surface — the
     * black screen. A rendition whose bytes are Forbidden must NOT end playback:
     * cap the size window strictly below the failed ceiling so neither the pin
     * nor ABR re-selects the un-servable rung, drop back to Automatic, and
     * `prepare()` retries from the retained position. Each 403 ratchets the cap
     * down one real rung, so a chain of forbidden high-res formats degrades
     * gracefully and terminates once a servable quality (or the floor) is
     * reached. Non-403 fatal errors keep the prior surface-through behaviour
     * (publish the error phase for the UI).
     *
     * The sibling `onPlayerErrorChanged` also publishes for the same
     * [PlaybackException] — deliberate, not accidental duplication: publishes
     * are idempotent snapshots, and both fire in the same main-thread tick so
     * the conflated [state] flow only ever exposes the post-recovery snapshot.
     */
    private fun recoverFromUnservableRendition(error: PlaybackException) {
        if (error.errorCode != PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
            publishState()
            return
        }
        val failedCeilingPx = trackSelector.parameters.maxVideoHeight
        val nextRungPx = availableQualities.map { it.heightPx }.filter { it < failedCeilingPx }.maxOrNull()
        if (nextRungPx == null) {
            publishState()
            return
        }
        pinnedHeight = null
        trackSelector.setParameters(
            trackSelector.parameters.buildUpon()
                .setMinVideoSize(0, 0)
                .setMaxVideoSize(MAX_VIDEO_WIDTH, nextRungPx)
                .build(),
        )
        player.prepare()
        publishState()
    }

    /** Pins video to [heightPx] via a min==max size window; audio + codec-chain fallback (REQ-10) keep adapting. */
    private fun applyHeightWindow(heightPx: Int) {
        pinnedHeight = heightPx
        trackSelector.setParameters(
            trackSelector.parameters.buildUpon()
                .setMinVideoSize(0, heightPx)
                .setMaxVideoSize(MAX_VIDEO_WIDTH, heightPx)
                .build(),
        )
    }

    /** Restores the shipped 4K-capped default window (NOT [DefaultTrackSelector.Parameters.Builder.clearVideoSizeConstraints], which would drop the cap). */
    private fun applyAutoWindow() {
        pinnedHeight = null
        trackSelector.setParameters(
            trackSelector.parameters.buildUpon()
                .setMinVideoSize(0, 0)
                .setMaxVideoSize(MAX_VIDEO_WIDTH, MAX_VIDEO_HEIGHT)
                .build(),
        )
    }

    override fun release() {
        positionTickerJob?.cancel()
        scope.cancel()
        player.release()
    }

    private fun mediaSourceFor(manifest: PlaybackManifest): MediaSource = when (manifest.kind) {
        PlaybackManifest.Kind.DASH -> dashMediaSource(manifest.payload)
        PlaybackManifest.Kind.PROGRESSIVE -> progressiveMediaSource(manifest.payload)
        PlaybackManifest.Kind.MERGED -> MergingMediaSource(
            progressiveMediaSource(manifest.payload),
            progressiveMediaSource(requireNotNull(manifest.secondaryAudioUrl)),
        )
        PlaybackManifest.Kind.LIVE_HLS -> HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(manifest.payload))
        PlaybackManifest.Kind.LIVE_DASH -> liveDashMediaSource(manifest.payload)
    }

    private fun dashMediaSource(mpdXml: String): DashMediaSource {
        val dashManifest = DashManifestParser().parse(
            Uri.EMPTY,
            ByteArrayInputStream(mpdXml.toByteArray(StandardCharsets.UTF_8)),
        )
        return DashMediaSource.Factory(dataSourceFactory)
            .createMediaSource(dashManifest, MediaItem.fromUri(Uri.EMPTY))
    }

    /**
     * Live DASH: [mpdUrl] is fetched at playback time via the URL-based
     * [DashMediaSource.Factory.createMediaSource] overload — distinct from
     * [dashMediaSource]'s inline-XML VOD path, which already has the MPD
     * assembled in-memory and never re-fetches it.
     */
    private fun liveDashMediaSource(mpdUrl: String): DashMediaSource =
        DashMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(mpdUrl))

    private fun progressiveMediaSource(url: String): ProgressiveMediaSource =
        ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(url))

    /**
     * Maps live [tracks] to pure [VideoRendition]s: only video-typed groups,
     * only renderer-supported entries ([Tracks.Group.isTrackSupported] is a
     * renderer-capability check, orthogonal to the size-window selection, so
     * enumeration stays complete even while a height is pinned), and only
     * formats reporting a real height.
     */
    private fun videoRenditionsOf(tracks: Tracks): List<VideoRendition> =
        tracks.groups
            .filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { group ->
                (0 until group.length)
                    .filter { group.isTrackSupported(it) }
                    .map { group.getTrackFormat(it) }
                    .filter { it.height != Format.NO_VALUE && it.height > 0 }
                    .map { format ->
                        VideoRendition(
                            heightPx = format.height,
                            widthPx = format.width,
                            bitrate = format.bitrate.takeIf { it != Format.NO_VALUE },
                        )
                    }
            }

    private fun publishState() {
        val pinnedQuality = pinnedHeight?.let { pinned -> availableQualities.firstOrNull { it.heightPx == pinned } }
        mutableState.value = PlaybackState(
            phase = PlaybackPhaseMapper.map(player.playbackState, hasError = player.playerError != null),
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition,
            durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L,
            // R1: quality degrade for live happens HERE, at the source — never in the overlay.
            availableQualities = LiveQualityDegrade.qualities(isLive, availableQualities),
            activeQuality = LiveQualityDegrade.active(isLive, pinnedQuality),
            isLive = isLive,
        )
    }

    private companion object {
        const val MAX_VIDEO_WIDTH = 3840
        const val MAX_VIDEO_HEIGHT = 2160
        const val POSITION_TICK_MS = 300L

        /** HW-first: same decoder list ExoPlayer's default would try, hardware ones first. */
        val HW_FIRST_MEDIA_CODEC_SELECTOR = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            MediaCodecSelector.DEFAULT
                .getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                .sortedByDescending { it.hardwareAccelerated }
        }
    }
}
