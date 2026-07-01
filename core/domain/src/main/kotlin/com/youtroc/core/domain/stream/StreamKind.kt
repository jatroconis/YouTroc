package com.youtroc.core.domain.stream

/** How media tracks are packaged within a [Stream]. */
enum class StreamKind {
    /** Video and audio interleaved in one track (legacy progressive). */
    MUXED,

    /** Video without audio (adaptive DASH); pairs with an [AUDIO_ONLY] track. */
    VIDEO_ONLY,

    /** Audio without video (adaptive DASH). */
    AUDIO_ONLY,
}
