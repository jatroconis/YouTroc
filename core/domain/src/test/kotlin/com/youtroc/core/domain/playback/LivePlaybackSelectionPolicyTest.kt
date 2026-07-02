package com.youtroc.core.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LivePlaybackSelectionPolicyTest {

    @Test
    fun `selects LIVE_HLS when hlsUrl is present`() {
        val manifest = LivePlaybackSelectionPolicy.select(
            hlsUrl = "https://cdn/live.m3u8",
            dashMpdUrl = null,
        )

        assertEquals(PlaybackManifest.Kind.LIVE_HLS, manifest?.kind)
        assertEquals("https://cdn/live.m3u8", manifest?.payload)
    }

    @Test
    fun `falls back to LIVE_DASH when hlsUrl is blank and dashMpdUrl is present`() {
        val manifest = LivePlaybackSelectionPolicy.select(
            hlsUrl = "   ",
            dashMpdUrl = "https://cdn/live.mpd",
        )

        assertEquals(PlaybackManifest.Kind.LIVE_DASH, manifest?.kind)
        assertEquals("https://cdn/live.mpd", manifest?.payload)
    }

    @Test
    fun `prefers LIVE_HLS when both hlsUrl and dashMpdUrl are present`() {
        val manifest = LivePlaybackSelectionPolicy.select(
            hlsUrl = "https://cdn/live.m3u8",
            dashMpdUrl = "https://cdn/live.mpd",
        )

        assertEquals(PlaybackManifest.Kind.LIVE_HLS, manifest?.kind)
        assertEquals("https://cdn/live.m3u8", manifest?.payload)
    }

    @Test
    fun `returns null when both urls are null`() {
        assertNull(LivePlaybackSelectionPolicy.select(hlsUrl = null, dashMpdUrl = null))
    }

    @Test
    fun `returns null when both urls are blank`() {
        assertNull(LivePlaybackSelectionPolicy.select(hlsUrl = "", dashMpdUrl = "   "))
    }
}
