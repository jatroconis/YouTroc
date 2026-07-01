package com.youtroc.core.domain.playback

/**
 * Pure availability facts the extraction adapter probes for a video, ahead of
 * building a [PlaybackManifest]. Deliberately free of NewPipe types — just the
 * booleans-as-nullable-urls a policy needs to decide delivery.
 */
data class ManifestInputs(
    /** A usable multi-rendition DASH MPD, if the adapter could assemble one. */
    val dashMpd: String?,
    /** The best MUXED (video+audio interleaved) progressive URL, if any. */
    val muxedUrl: String?,
    /** The best VIDEO_ONLY URL, if any. */
    val videoOnlyUrl: String?,
    /** The best AUDIO_ONLY URL, if any. */
    val audioOnlyUrl: String?,
)
