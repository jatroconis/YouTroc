package com.youtroc.data.extraction

import com.youtroc.core.domain.stream.Stream
import com.youtroc.core.domain.stream.VideoCodec

/**
 * Maps NewPipe's raw codec string (e.g. `av01.0.05M.08`, `vp09.00.41.08`,
 * `avc1.640028`) to the domain's [VideoCodec] preference vocabulary. Pure and
 * total — every unrecognized string (audio codecs like `opus`/`mp4a` included)
 * resolves to [VideoCodec.OTHER].
 */
internal fun toDomainVideoCodec(rawCodec: String): VideoCodec = when {
    rawCodec.startsWith("av01", ignoreCase = true) -> VideoCodec.AV1
    rawCodec.startsWith("vp9", ignoreCase = true) || rawCodec.startsWith("vp09", ignoreCase = true) -> VideoCodec.VP9
    rawCodec.startsWith("avc1", ignoreCase = true) || rawCodec.startsWith("h264", ignoreCase = true) -> VideoCodec.H264
    else -> VideoCodec.OTHER
}

/**
 * The best rendition among same-[com.youtroc.core.domain.stream.StreamKind]
 * candidates, used to pick the URLs that feed
 * [ManifestInputs][com.youtroc.core.domain.playback.ManifestInputs]: prefer
 * the higher-preference codec first (domain order, AV1 < VP9 < H264 < OTHER),
 * then the tallest resolution, then the highest bitrate.
 */
internal fun List<Stream>.bestByQualityOrNull(): Stream? =
    minWithOrNull(
        compareBy(
            { (it.codec ?: VideoCodec.OTHER).ordinal },
            { -(it.heightPx ?: 0) },
            { -(it.bitrateBps ?: 0) },
        ),
    )
