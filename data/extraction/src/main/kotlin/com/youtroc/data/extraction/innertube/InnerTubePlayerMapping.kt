package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.playback.PlaybackManifest
import com.youtroc.core.domain.stream.HdrFormat
import com.youtroc.core.domain.stream.PlayableStreams
import com.youtroc.core.domain.stream.Stream
import com.youtroc.core.domain.stream.StreamKind
import com.youtroc.core.domain.stream.StreamResult
import com.youtroc.data.extraction.toDomainVideoCodec
import java.io.IOException

private val codecsPattern = Regex("codecs=\"([^\"]*)\"")

/**
 * Extracts every well-formed video-only [Fmt] from `adaptiveFormats`.
 * android_vr's `mimeType` starting with `video/` marks a video-only adaptive
 * format (`formats[]`'s muxed itag 18 never appears here). A malformed entry
 * (missing `initRange`/`indexRange`, or one whose string byte-range fields
 * don't parse to a number) is dropped, not fatal -- mirrors
 * [com.youtroc.data.extraction.DashManifestAssembler]'s per-itag best-effort
 * convention.
 */
internal fun StreamingData.videoFmts(): List<Fmt> =
    adaptiveFormats.filter { it.mimeType?.startsWith("video/") == true }.mapNotNull { it.toFmtOrNull() }

/** Extracts every well-formed audio-only [Fmt] from `adaptiveFormats` (see [videoFmts]). */
internal fun StreamingData.audioFmts(): List<Fmt> =
    adaptiveFormats.filter { it.mimeType?.startsWith("audio/") == true }.mapNotNull { it.toFmtOrNull() }

/**
 * Converts one raw [PlayerFormat] into a well-formed [Fmt] for
 * [MpdBuilder], or `null` when a required piece is missing/unparseable
 * (malformed-entry-is-skipped-not-fatal). R2 (BLOCKING): `contentLength`/
 * `audioSampleRate`/[Range]'s `start`/`end` are JSON strings on the wire --
 * the `toIntOrNull()`/`toLongOrNull()` conversions here are where that
 * String -> number step actually happens; a failed conversion drops the
 * entry rather than throwing.
 */
internal fun PlayerFormat.toFmtOrNull(): Fmt? {
    val streamUrl = url?.takeIf { it.isNotBlank() } ?: return null
    val mime = mimeType ?: return null
    val type = mime.substringBefore(';').trim()
    val codecs = codecsPattern.find(mime)?.groupValues?.get(1) ?: return null
    val init = initRange ?: return null
    val index = indexRange ?: return null
    val initStart = init.start.toIntOrNull() ?: return null
    val initEnd = init.end.toIntOrNull() ?: return null
    val indexStart = index.start.toIntOrNull() ?: return null
    val indexEnd = index.end.toIntOrNull() ?: return null
    val bandwidth = bitrate ?: averageBitrate ?: return null

    return Fmt(
        itag = itag,
        url = streamUrl,
        type = type,
        codecs = codecs,
        bandwidth = bandwidth,
        width = width,
        height = height,
        fps = fps,
        initStart = initStart,
        initEnd = initEnd,
        indexStart = indexStart,
        indexEnd = indexEnd,
        audioChannels = audioChannels,
        audioSamplingRate = audioSampleRate?.toIntOrNull(),
    )
}

/** Maps an already-extracted [Fmt] onto the domain [Stream], reusing [toDomainVideoCodec]. */
internal fun Fmt.toDomainStream(kind: StreamKind): Stream =
    Stream(
        url = url,
        container = type.substringAfter('/', missingDelimiterValue = ""),
        kind = kind,
        codec = toDomainVideoCodec(codecs),
        heightPx = height,
        bitrateBps = bandwidth,
    )

/**
 * Maps android_vr's `colorInfo.transferCharacteristics` onto the domain's
 * [HdrFormat] (REQ-H2). `endsWith` rather than exact match: YouTube's wire
 * strings are namespaced (e.g. `COLOR_TRANSFER_CHARACTERISTICS_SMPTEST2084`),
 * so this tolerates any prefix. Total function -- null, unknown, or garbage
 * input (BT709, missing colorInfo, malformed string) always falls through to
 * [HdrFormat.SDR], never throws.
 */
internal fun ColorInfo?.toHdrFormat(): HdrFormat = when {
    this?.transferCharacteristics?.endsWith("SMPTEST2084") == true -> HdrFormat.HDR10
    this?.transferCharacteristics?.endsWith("ARIB_STD_B67") == true -> HdrFormat.HLG
    else -> HdrFormat.SDR
}

/**
 * Maps a raw `formats[]`/`adaptiveFormats[]` entry directly onto the domain
 * [Stream] -- unlike [toFmtOrNull], this does NOT require `initRange`/
 * `indexRange` (the muxed `formats[]` entries never carry them, since they
 * are plain progressive URLs, not DASH-segmented). Used to build
 * [PlayableStreams.streams] for every [StreamKind], including [StreamKind.MUXED].
 */
internal fun PlayerFormat.toDomainStreamOrNull(kind: StreamKind): Stream? {
    val streamUrl = url?.takeIf { it.isNotBlank() } ?: return null
    val mime = mimeType ?: return null
    val type = mime.substringBefore(';').trim()
    val codecs = codecsPattern.find(mime)?.groupValues?.get(1)

    return Stream(
        url = streamUrl,
        container = type.substringAfter('/', missingDelimiterValue = ""),
        kind = kind,
        codec = codecs?.let { toDomainVideoCodec(it) },
        heightPx = height,
        bitrateBps = bitrate ?: averageBitrate,
        hdr = colorInfo.toHdrFormat(),
    )
}

/**
 * The full domain [Stream] list: `formats[]` (muxed) plus every
 * `adaptiveFormats[]` entry split by its `video/`/`audio/` `mimeType`
 * prefix (REQ-Format-Mapping).
 */
private fun StreamingData.toDomainStreams(): List<Stream> = buildList {
    formats.forEach { it.toDomainStreamOrNull(StreamKind.MUXED)?.let(::add) }
    adaptiveFormats.forEach { format ->
        val kind = when {
            format.mimeType?.startsWith("video/") == true -> StreamKind.VIDEO_ONLY
            format.mimeType?.startsWith("audio/") == true -> StreamKind.AUDIO_ONLY
            else -> null
        }
        if (kind != null) format.toDomainStreamOrNull(kind)?.let(::add)
    }
}

/**
 * Resolves the video's duration in milliseconds: prefers the MAX
 * `approxDurationMs` across `adaptiveFormats` (SUGGESTED flip -- each
 * rendition's own duration is closer to the actual segment timeline than
 * the video-level `lengthSeconds`), falling back to
 * `videoDetails.lengthSeconds` (whole seconds) when no adaptive format
 * reports one. `null` when NEITHER source yields a positive duration -- the
 * R5 (BLOCKING) signal the caller uses to route to [StreamResult.Error]
 * WITHOUT ever calling [MpdBuilder.buildMpd].
 */
internal fun PlayerResponse.resolveDurationMs(): Long? {
    val maxApprox = streamingData?.adaptiveFormats.orEmpty()
        .mapNotNull { it.approxDurationMs?.toLongOrNull() }
        .filter { it > 0 }
        .maxOrNull()
    if (maxApprox != null) return maxApprox

    val lengthSeconds = videoDetails?.lengthSeconds?.toLongOrNull()
    return lengthSeconds?.takeIf { it > 0 }?.times(1000)
}

/**
 * playabilityStatus + format-shape -> [StreamResult] (REQ:
 * "playabilityStatus + format-shape -> StreamResult mapping"):
 * - `status != "OK"` (UNPLAYABLE/LOGIN_REQUIRED/age/geo/live/...) -> [StreamResult.NotAvailable].
 *   VOD-only (D4): this adapter parses no `hlsManifestUrl`/`dashManifestUrl` and
 *   attempts no live-manifest code path -- a live video's `UNPLAYABLE`
 *   status already lands here.
 * - `status == "OK"` but missing/insufficient adaptive video+audio formats
 *   (D5: BOTH are required, the itag-18-only throttle signature) -> [StreamResult.Error]
 *   (insufficient) -- never a degraded muxed-only [StreamResult.Success].
 * - `status == "OK"` with no usable [resolveDurationMs] (R5 BLOCKING) -> [StreamResult.Error];
 *   [MpdBuilder.buildMpd] is NEVER invoked with a non-positive duration.
 * - Otherwise -> [StreamResult.Success] carrying the own-built DASH [PlaybackManifest]
 *   plus a scrub-preview [com.youtroc.core.domain.stream.StoryboardSpec] (REQ-SB1),
 *   shared by both the android_vr and ios ladder rungs since they call this SAME
 *   function. Parsing failures NEVER fail stream resolution (REQ-SB2, design D6):
 *   [storyboards]' `spec` is routed through [toStoryboardSpecOrNull] inside
 *   `runCatching{}.getOrNull()`, so an absent node, a malformed spec, or any
 *   unexpected exception all resolve to a `null` storyboard.
 */
internal fun PlayerResponse.toStreamResult(): StreamResult {
    if (playabilityStatus?.status != "OK") return StreamResult.NotAvailable

    val streamingData = streamingData
        ?: return StreamResult.Error(IllegalStateException("InnerTube player: OK status but missing streamingData"))

    val video = streamingData.videoFmts()
    val audio = streamingData.audioFmts()
    if (video.isEmpty() || audio.isEmpty()) {
        return StreamResult.Error(
            IllegalStateException("InnerTube player: insufficient adaptiveFormats (video=${video.size}, audio=${audio.size})"),
        )
    }

    val durationMs = resolveDurationMs()
        ?: return StreamResult.Error(IllegalStateException("InnerTube player: no usable duration"))

    val mpd = MpdBuilder.buildMpd(video, audio, durationMs)
    val manifest = PlaybackManifest(kind = PlaybackManifest.Kind.DASH, payload = mpd, adaptive = true)
    val storyboard = runCatching { storyboards?.playerStoryboardSpecRenderer?.spec?.toStoryboardSpecOrNull() }.getOrNull()
    return StreamResult.Success(PlayableStreams(streamingData.toDomainStreams(), manifest, storyboard))
}

/**
 * Maps an [InnerTubeStreamProvider] adapter-level failure onto the domain's
 * typed outcome. Mirrors [com.youtroc.data.extraction.toStreamResult] (the
 * NewPipe adapter's mapping, a DIFFERENT function in a different package):
 * an [IOException] (no connectivity, DNS failure, timeout) means
 * [StreamResult.Offline]; anything else (non-200 HTTP, malformed JSON) is a
 * [StreamResult.Error] carrying the cause. Cooperative cancellation is
 * handled by the caller BEFORE reaching this function -- it must never be
 * mapped here.
 */
internal fun Throwable.toStreamResult(): StreamResult = when (this) {
    is IOException -> StreamResult.Offline
    else -> StreamResult.Error(this)
}
