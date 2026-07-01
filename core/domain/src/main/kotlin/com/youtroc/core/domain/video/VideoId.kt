package com.youtroc.core.domain.video

/**
 * Identity of a YouTube video within the domain's ubiquitous language.
 *
 * Wrapping the raw string in a value class makes the type system enforce that a
 * "video id" can never be silently confused with any other string (a title, a
 * URL, a channel id). It carries no framework dependency: this is the center of
 * the hexagon.
 */
@JvmInline
value class VideoId(val value: String) {
    init {
        require(value.isNotBlank()) { "VideoId must not be blank." }
    }
}
