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

    @Test
    fun `isLive is true for LIVE_HLS and LIVE_DASH`() {
        assertEquals(true, PlaybackManifest.Kind.LIVE_HLS.isLive)
        assertEquals(true, PlaybackManifest.Kind.LIVE_DASH.isLive)
    }

    @Test
    fun `isLive is false for DASH, PROGRESSIVE and MERGED`() {
        assertEquals(false, PlaybackManifest.Kind.DASH.isLive)
        assertEquals(false, PlaybackManifest.Kind.PROGRESSIVE.isLive)
        assertEquals(false, PlaybackManifest.Kind.MERGED.isLive)
    }

    @Test
    fun `accepts LIVE_HLS with a URL payload and no secondary audio url`() {
        val manifest = PlaybackManifest(
            kind = PlaybackManifest.Kind.LIVE_HLS,
            payload = "https://cdn/live.m3u8",
            adaptive = true,
        )

        assertNull(manifest.secondaryAudioUrl)
    }

    @Test
    fun `accepts LIVE_DASH with a URL payload and no secondary audio url`() {
        val manifest = PlaybackManifest(
            kind = PlaybackManifest.Kind.LIVE_DASH,
            payload = "https://cdn/live.mpd",
            adaptive = true,
        )

        assertNull(manifest.secondaryAudioUrl)
    }
}
