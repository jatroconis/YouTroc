package com.youtroc.core.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PlaybackManifestTest {

    @Test
    fun `rejects a blank payload`() {
        assertFailsWith<IllegalArgumentException> {
            PlaybackManifest(
                kind = PlaybackManifest.Kind.PROGRESSIVE,
                payload = "   ",
                adaptive = false,
            )
        }
    }

    @Test
    fun `requires a secondary audio url when kind is MERGED`() {
        assertFailsWith<IllegalArgumentException> {
            PlaybackManifest(
                kind = PlaybackManifest.Kind.MERGED,
                payload = "https://cdn/video-only",
                secondaryAudioUrl = null,
                adaptive = false,
            )
        }
    }

    @Test
    fun `accepts MERGED with a secondary audio url`() {
        val manifest = PlaybackManifest(
            kind = PlaybackManifest.Kind.MERGED,
            payload = "https://cdn/video-only",
            secondaryAudioUrl = "https://cdn/audio-only",
            adaptive = false,
        )

        assertEquals("https://cdn/audio-only", manifest.secondaryAudioUrl)
    }

    @Test
    fun `accepts DASH without a secondary audio url`() {
        val manifest = PlaybackManifest(
            kind = PlaybackManifest.Kind.DASH,
            payload = "<MPD/>",
            adaptive = true,
        )

        assertNull(manifest.secondaryAudioUrl)
    }
}
