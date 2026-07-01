package com.youtroc.core.domain.playback

/**
 * Decides how a video should be delivered from the facts an extraction adapter
 * probed. Pure and total: given the same [ManifestInputs] it always returns the
 * same [PlaybackManifest], with no I/O and no exceptions.
 *
 * Priority: DASH (real ABR) > MUXED progressive (no ABR) > MERGED video+audio
 * (last resort, no ABR) > null (nothing playable). A lone video-only track is
 * NEVER selected — this is the fix for the delivered `.first()` fallback bug
 * that could yield audio-less playback.
 */
object PlaybackSelectionPolicy {

    fun select(inputs: ManifestInputs): PlaybackManifest? = when {
        inputs.dashMpd != null ->
            PlaybackManifest(kind = PlaybackManifest.Kind.DASH, payload = inputs.dashMpd, adaptive = true)

        inputs.muxedUrl != null ->
            PlaybackManifest(kind = PlaybackManifest.Kind.PROGRESSIVE, payload = inputs.muxedUrl, adaptive = false)

        inputs.videoOnlyUrl != null && inputs.audioOnlyUrl != null ->
            PlaybackManifest(
                kind = PlaybackManifest.Kind.MERGED,
                payload = inputs.videoOnlyUrl,
                secondaryAudioUrl = inputs.audioOnlyUrl,
                adaptive = false,
            )

        else -> null
    }
}
