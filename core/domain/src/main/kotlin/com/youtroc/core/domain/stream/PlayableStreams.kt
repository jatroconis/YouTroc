package com.youtroc.core.domain.stream

/**
 * The set of media tracks that lets a video actually play. A successful
 * extraction always yields at least one stream; an empty set is a contradiction
 * the type refuses to represent.
 */
data class PlayableStreams(
    val streams: List<Stream>,
) {
    init {
        require(streams.isNotEmpty()) { "PlayableStreams must contain at least one stream." }
    }
}
