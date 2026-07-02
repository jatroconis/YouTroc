package com.youtroc.core.domain.playback

/**
 * Opaque, cross-hexagon carrier of "how to build a media source" for this video.
 *
 * The domain never parses [payload] — it is DASH MPD XML for [Kind.DASH] or a
 * plain media URL for [Kind.PROGRESSIVE]/[Kind.MERGED]. Only the adapter that
 * consumes it (`:data:player`) knows how to turn it into a real media source.
 * Keeping it opaque (String/enum/Boolean only) is what lets it cross from
 * `:data:extraction` through `:core:domain` without leaking NewPipe or Media3
 * types into the center of the hexagon.
 */
data class PlaybackManifest(
    val kind: Kind,
    val payload: String,
    val secondaryAudioUrl: String? = null,
    val adaptive: Boolean,
) {
    init {
        require(payload.isNotBlank()) { "PlaybackManifest payload must not be blank." }
        require(kind != Kind.MERGED || secondaryAudioUrl != null) {
            "PlaybackManifest with kind MERGED requires a secondaryAudioUrl; audio-less playback is never allowed."
        }
    }

    /** How [payload] must be interpreted to build a media source. */
    enum class Kind {
        /** Multi-rendition DASH MPD; [payload] is the manifest XML. Enables real ABR. */
        DASH,

        /** A single muxed (video+audio) URL; [payload] is that URL. No ABR. */
        PROGRESSIVE,

        /** Separate video-only + audio-only URLs merged at playback time; last resort. */
        MERGED,

        /** Live HLS; [payload] is a plain playback URL, not inline manifest content. */
        LIVE_HLS,

        /** Live DASH; [payload] is a plain manifest URL (fetched at playback time), contrast [DASH]'s inline XML. */
        LIVE_DASH,
        ;

        /** True for the live kinds, where [payload] is always a URL and streams may be empty. */
        val isLive: Boolean get() = this == LIVE_HLS || this == LIVE_DASH
    }
}
