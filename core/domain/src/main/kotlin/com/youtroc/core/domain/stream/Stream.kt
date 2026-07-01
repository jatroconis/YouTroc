package com.youtroc.core.domain.stream

/** A single playable media track resolved for a video. */
data class Stream(
    val url: String,
    val container: String,
    val kind: StreamKind,
) {
    init {
        require(url.isNotBlank()) { "Stream url must not be blank." }
    }
}
