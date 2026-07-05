package com.youtroc.core.domain.stream

import com.youtroc.core.domain.playback.PlaybackManifest

/**
 * The set of media tracks that lets a video actually play. A successful
 * extraction always yields at least one stream; an empty set is a contradiction
 * the type refuses to represent.
 *
 * [manifest] is the delivery [PlaybackSelectionPolicy][com.youtroc.core.domain.playback.PlaybackSelectionPolicy]
 * decided on (DASH/PROGRESSIVE/MERGED), when one could be assembled. Additive
 * and defaulted to null so existing REQ-1..7 construction keeps compiling
 * unchanged.
 *
 * [storyboard] is the scrub-preview sprite data (REQ-SB5), populated from
 * whichever ladder rung actually resolved streams. Additive and defaulted to
 * null for the same reason as [manifest] -- absent (extraction failure, or a
 * live video, which never builds one) means the caller falls back to today's
 * plain scrubber with no thumbnail.
 */
data class PlayableStreams(
    val streams: List<Stream>,
    val manifest: PlaybackManifest? = null,
    val storyboard: StoryboardSpec? = null,
) {
    init {
        require(streams.isNotEmpty() || manifest?.kind?.isLive == true) {
            "PlayableStreams needs at least one stream, unless it carries a live manifest."
        }
    }

    /**
     * The aggregate HDR intent across [streams]: the first HDR stream's
     * format, or [HdrFormat.SDR] when none is HDR (or [streams] is empty,
     * the live-manifest case). Empty-safe by construction -- NEVER
     * `maxOf`/`maxWith`, which throws on an empty list and would otherwise
     * compare [HdrFormat] by meaningless enum ordinal.
     */
    val hdr: HdrFormat get() = streams.firstOrNull { it.hdr.isHdr }?.hdr ?: HdrFormat.SDR
}
