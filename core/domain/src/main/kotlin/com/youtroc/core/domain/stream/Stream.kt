package com.youtroc.core.domain.stream

/** A single playable media track resolved for a video. */
data class Stream(
    val url: String,
    val container: String,
    val kind: StreamKind,
    /** Video codec, when known. Null for audio-only streams or unparsed metadata. */
    val codec: VideoCodec? = null,
    /** Vertical resolution in pixels, when known. Null for audio-only streams. */
    val heightPx: Int? = null,
    /** Bitrate in bits per second, when known. */
    val bitrateBps: Int? = null,
    /** HDR intent (SDR/HDR10/HLG), when known. Defaults to SDR -- absent signal is safe. */
    val hdr: HdrFormat = HdrFormat.SDR,
) {
    init {
        require(url.isNotBlank()) { "Stream url must not be blank." }
    }
}
