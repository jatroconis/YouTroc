package com.youtroc.core.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackSelectionPolicyTest {

    @Test
    fun `prefers DASH when a manifest is available`() {
        val inputs = ManifestInputs(
            dashMpd = "<MPD/>",
            muxedUrl = "https://cdn/muxed",
            videoOnlyUrl = "https://cdn/video-only",
            audioOnlyUrl = "https://cdn/audio-only",
        )

        val manifest = PlaybackSelectionPolicy.select(inputs)

        assertEquals(
            PlaybackManifest(kind = PlaybackManifest.Kind.DASH, payload = "<MPD/>", adaptive = true),
            manifest,
        )
    }

    @Test
    fun `falls back to MUXED progressive when no DASH manifest exists`() {
        val inputs = ManifestInputs(
            dashMpd = null,
            muxedUrl = "https://cdn/muxed",
            videoOnlyUrl = "https://cdn/video-only",
            audioOnlyUrl = "https://cdn/audio-only",
        )

        val manifest = PlaybackSelectionPolicy.select(inputs)

        assertEquals(
            PlaybackManifest(kind = PlaybackManifest.Kind.PROGRESSIVE, payload = "https://cdn/muxed", adaptive = false),
            manifest,
        )
    }

    @Test
    fun `merges video-only and audio-only as a last resort when no MUXED or DASH exists`() {
        val inputs = ManifestInputs(
            dashMpd = null,
            muxedUrl = null,
            videoOnlyUrl = "https://cdn/video-only",
            audioOnlyUrl = "https://cdn/audio-only",
        )

        val manifest = PlaybackSelectionPolicy.select(inputs)

        assertEquals(
            PlaybackManifest(
                kind = PlaybackManifest.Kind.MERGED,
                payload = "https://cdn/video-only",
                secondaryAudioUrl = "https://cdn/audio-only",
                adaptive = false,
            ),
            manifest,
        )
    }

    @Test
    fun `never selects a lone video-only track without paired audio`() {
        val inputs = ManifestInputs(
            dashMpd = null,
            muxedUrl = null,
            videoOnlyUrl = "https://cdn/video-only",
            audioOnlyUrl = null,
        )

        assertNull(PlaybackSelectionPolicy.select(inputs))
    }

    @Test
    fun `returns null when nothing playable is available`() {
        val inputs = ManifestInputs(
            dashMpd = null,
            muxedUrl = null,
            videoOnlyUrl = null,
            audioOnlyUrl = null,
        )

        assertNull(PlaybackSelectionPolicy.select(inputs))
    }
}
