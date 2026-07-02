package com.youtroc.data.extraction.innertube

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deterministic, network-free verification of `videoRenderer -> Video` mapping
 * against a trimmed, real-captured InnerTube `search` response
 * (`src/test/resources/innertube/search_web.json`): mixed non-video renderers,
 * an ordinary `simpleText` view count, a live/`runs`-form view count (R1), an
 * item missing every optional field, and an id-less item.
 */
class InnerTubeSearchMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val response: SearchResponse by lazy {
        val text = requireNotNull(javaClass.getResourceAsStream("/innertube/search_web.json")) {
            "missing fixture: innertube/search_web.json"
        }.bufferedReader().readText()
        json.decodeFromString(text)
    }

    @Test
    fun `only videoRenderer items are mapped, in source order`() {
        val ids = response.videoRenderers().mapNotNull { it.toVideoOrNull() }.map { it.id.value }

        assertEquals(
            listOf("sF80I-TQiW0", "4xDzrJKXOOY", "CFGLoQIhmow", "missingOptionalX"),
            ids,
        )
    }

    @Test
    fun `a simpleText view count is digit-parsed`() {
        val video = response.videoRenderers().mapNotNull { it.toVideoOrNull() }
            .first { it.id.value == "sF80I-TQiW0" }

        assertEquals(25_463_189L, video.viewCount)
        assertEquals("Emitido hace 2 años", video.publishedText)
        assertEquals("The Japanese Town", video.channelName)
    }

    @Test
    fun `a live runs-form view count joins runs and strips non-digits, with no publishedText`() {
        val video = response.videoRenderers().mapNotNull { it.toVideoOrNull() }
            .first { it.id.value == "4xDzrJKXOOY" }

        assertEquals(1932L, video.viewCount)
        assertNull(video.publishedText)
    }

    @Test
    fun `the widest thumbnail is picked`() {
        val video = response.videoRenderers().mapNotNull { it.toVideoOrNull() }
            .first { it.id.value == "sF80I-TQiW0" }

        // Fixture offers a 360w and a 720w rendition for this item; the wider one must win.
        assertEquals(
            "https://i.ytimg.com/vi/sF80I-TQiW0/hq720.jpg?sqp=-oaymwEXCNAFEJQDSFryq4qpAwkIARUAAIhCGAE=&rs=AOn4CLDjlUuOYLaXBxJWHZSVFlilK8qb2A",
            video.thumbnailUrl,
        )
    }

    @Test
    fun `a videoRenderer missing every optional field degrades gracefully`() {
        val video = response.videoRenderers().mapNotNull { it.toVideoOrNull() }
            .first { it.id.value == "missingOptionalX" }

        assertEquals("Unknown channel", video.channelName)
        assertNull(video.viewCount)
        assertNull(video.publishedText)
        assertEquals("https://i.ytimg.com/vi/missingOptionalX/hq720.jpg", video.thumbnailUrl)
    }

    @Test
    fun `an item missing videoId is dropped`() {
        val ids = response.videoRenderers().mapNotNull { it.toVideoOrNull() }.map { it.id.value }

        assertTrue("An item with no video id" !in ids)
        assertEquals(4, ids.size)
    }

    @Test
    fun `unknown top-level and leaf keys do not break parsing`() {
        // Decoding succeeded to get here at all (responseContext/estimatedResults/
        // trackingParams/lengthText/shortViewCountText are all unmodeled) -- this
        // assertion just proves mapped output is unaffected by that drift.
        assertEquals(4, response.videoRenderers().mapNotNull { it.toVideoOrNull() }.size)
    }
}
