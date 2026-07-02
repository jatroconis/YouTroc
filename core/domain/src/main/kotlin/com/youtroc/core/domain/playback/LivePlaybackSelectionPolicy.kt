package com.youtroc.core.domain.playback

/**
 * Decides how a LIVE broadcast should be delivered from the facts an extraction
 * adapter probed. Pure and total: given the same inputs it always returns the
 * same [PlaybackManifest], with no I/O and no exceptions.
 *
 * Priority: HLS (robust anonymous playback) > DASH manifest URL (fallback) >
 * null (nothing playable — DRM/premium/geo-restricted).
 */
object LivePlaybackSelectionPolicy {

    fun select(hlsUrl: String?, dashMpdUrl: String?): PlaybackManifest? = when {
        !hlsUrl.isNullOrBlank() ->
            PlaybackManifest(kind = PlaybackManifest.Kind.LIVE_HLS, payload = hlsUrl, adaptive = true)

        !dashMpdUrl.isNullOrBlank() ->
            PlaybackManifest(kind = PlaybackManifest.Kind.LIVE_DASH, payload = dashMpdUrl, adaptive = true)

        else -> null
    }
}
