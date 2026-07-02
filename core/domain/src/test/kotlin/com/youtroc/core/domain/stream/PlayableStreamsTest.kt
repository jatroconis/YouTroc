package com.youtroc.core.domain.stream

import com.youtroc.core.domain.playback.PlaybackManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PlayableStreamsTest {

    private val anyStream = Stream(url = "https://cdn/muxed", container = "mp4", kind = StreamKind.MUXED)

    @Test
    fun `requires at least one stream`() {
        assertFailsWith<IllegalArgumentException> {
            PlayableStreams(emptyList())
        }
    }

    @Test
    fun `manifest defaults to null when not provided`() {
        assertNull(PlayableStreams(listOf(anyStream)).manifest)
    }

    @Test
    fun `carries the manifest when provided`() {
        val manifest = PlaybackManifest(kind = PlaybackManifest.Kind.DASH, payload = "<MPD/>", adaptive = true)

        val playableStreams = PlayableStreams(listOf(anyStream), manifest)

        assertEquals(manifest, playableStreams.manifest)
    }

    @Test
    fun `allows empty streams for a live manifest`() {
        val liveManifest = PlaybackManifest(
            kind = PlaybackManifest.Kind.LIVE_HLS,
            payload = "https://cdn/live.m3u8",
            adaptive = true,
        )

        val playableStreams = PlayableStreams(streams = emptyList(), manifest = liveManifest)

        assertEquals(liveManifest, playableStreams.manifest)
    }

    @Test
    fun `still requires a stream for a non-live manifest`() {
        val vodManifest = PlaybackManifest(kind = PlaybackManifest.Kind.DASH, payload = "<MPD/>", adaptive = true)

        assertFailsWith<IllegalArgumentException> {
            PlayableStreams(streams = emptyList(), manifest = vodManifest)
        }
    }
}
