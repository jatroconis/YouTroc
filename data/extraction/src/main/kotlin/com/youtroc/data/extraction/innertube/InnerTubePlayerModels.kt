package com.youtroc.data.extraction.innertube

import kotlinx.serialization.Serializable

/**
 * Request/response DTO tree for `POST youtubei/v1/player` (ANDROID_VR).
 * Deliberately minimal: only the fields [InnerTubePlayerMapping] needs are
 * modeled -- everything else (captions, storyboards, playerConfig,
 * attestation, ...) is left unmapped and tolerated via
 * `Json { ignoreUnknownKeys = true }`. These types are `internal`: they
 * never cross [InnerTubeStreamProvider]'s port boundary. Reuses
 * [Context]/[Client] from `InnerTubeSearchModels.kt`.
 *
 * R2 (BLOCKING): android_vr sends `initRange`/`indexRange.start/end`,
 * `contentLength`, `audioSampleRate`, and `videoDetails.lengthSeconds` as
 * JSON **strings** (e.g. `"221"`), not numbers. kotlinx.serialization's
 * strict decoder THROWS a `SerializationException` if those fields were
 * declared `Int`/`Long` here -- they MUST stay `String`; conversion to a
 * number happens in [InnerTubePlayerMapping], not in this DTO layer.
 */

// ---- Request ----

@Serializable
internal data class PlayerRequest(
    val context: Context,
    val videoId: String,
)

// ---- Response ----

@Serializable
internal data class PlayerResponse(
    val playabilityStatus: PlayabilityStatus? = null,
    val streamingData: StreamingData? = null,
    val videoDetails: VideoDetails? = null,
)

@Serializable
internal data class PlayabilityStatus(
    val status: String? = null,
    val reason: String? = null,
)

@Serializable
internal data class StreamingData(
    val expiresInSeconds: String? = null,
    val formats: List<PlayerFormat> = emptyList(),
    val adaptiveFormats: List<PlayerFormat> = emptyList(),
)

/**
 * One `formats[]`/`adaptiveFormats[]` entry. `width`/`height`/`fps`/`bitrate`
 * arrive as native JSON numbers and decode straight to `Int`; `contentLength`/
 * `audioSampleRate`/[Range]'s `start`/`end` arrive as JSON strings (R2) and
 * stay `String` here.
 */
@Serializable
internal data class PlayerFormat(
    val itag: Int,
    val url: String? = null,
    val mimeType: String? = null,
    val bitrate: Int? = null,
    val averageBitrate: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Int? = null,
    val qualityLabel: String? = null,
    val contentLength: String? = null,
    val approxDurationMs: String? = null,
    val initRange: Range? = null,
    val indexRange: Range? = null,
    val audioSampleRate: String? = null,
    val audioChannels: Int? = null,
)

/** A byte range; `start`/`end` are JSON strings (R2), not numbers. */
@Serializable
internal data class Range(
    val start: String,
    val end: String,
)

@Serializable
internal data class VideoDetails(
    val lengthSeconds: String? = null,
    val isLiveContent: Boolean? = null,
)
