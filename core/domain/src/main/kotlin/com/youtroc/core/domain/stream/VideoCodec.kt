package com.youtroc.core.domain.stream

/**
 * Video codecs the domain expresses a hardware-decode PREFERENCE for, ordered
 * AV1 > VP9 > H.264 > anything else. Declaration order is the preference order
 * — Kotlin enums are naturally [Comparable] by ordinal, so `AV1 < VP9` reads as
 * "AV1 is preferred over VP9".
 *
 * This is only a preference: whether a codec is actually hardware-decodable on
 * a given device is an adapter concern (`:data:player`'s `MediaCodecSelector`),
 * not something the domain can know.
 */
enum class VideoCodec {
    AV1,
    VP9,
    H264,

    /** Any codec the domain does not have an explicit preference for. */
    OTHER,
}
