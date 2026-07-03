package com.youtroc.data.extraction.innertube

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Deterministic, network-free verification of `next` response -> [VideoDetailInfo]
 * mapping against a trimmed, real-captured InnerTube `next` response
 * (`src/test/resources/innertube/next_web.json`).
 */
class InnerTubeDetailMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val response: NextResponse by lazy {
        val text = requireNotNull(javaClass.getResourceAsStream("/innertube/next_web.json")) {
            "missing fixture: innertube/next_web.json"
        }.bufferedReader().readText()
        json.decodeFromString(text)
    }

    @Test
    fun `title, channelName, description, viewCount and publishedText are mapped from the fixture`() {
        val info = requireNotNull(response.videoDetailInfoOrNull("dQw4w9WgXcQ"))

        assertEquals(
            "Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)",
            info.title,
        )
        // R1: channelName must resolve via the `owner` wrapper -- would fail
        // against the old unwrapped `videoSecondaryInfoRenderer.videoOwnerRenderer` path.
        assertEquals("Rick Astley", info.channelName)
        assertEquals("24 oct 2009", info.publishedText)
        assertEquals(
            "The official video for “Never Gonna Give You Up” by Rick Astley.",
            info.description?.lineSequence()?.first(),
        )
    }

    @Test
    fun `a runs-form primary viewCount is digit-parsed`() {
        // R2: primary viewCount is the SHARED both-shape ViewCountText DTO --
        // this fixture's primary viewCount is in `runs` form ("4715" + " usuarios viéndolo ahora").
        val info = requireNotNull(response.videoDetailInfoOrNull("dQw4w9WgXcQ"))

        assertEquals(4715L, info.viewCount)
    }

    @Test
    fun `only LOCKUP_CONTENT_TYPE_VIDEO lockups are mapped, in source order, dropping the playlist, reel shelf and continuation siblings`() {
        val info = requireNotNull(response.videoDetailInfoOrNull("dQw4w9WgXcQ"))

        assertEquals(listOf("djV11Xbc914", "tSi6Dn1H36Y"), info.related.map { it.id.value })
    }

    @Test
    fun `a video lockup maps its title, channelName, viewCount, publishedText and widest thumbnail`() {
        val info = requireNotNull(response.videoDetailInfoOrNull("dQw4w9WgXcQ"))
        val related = info.related.first { it.id.value == "djV11Xbc914" }

        assertEquals("a-ha - Take On Me (Video Oficial)", related.title)
        assertEquals("a-ha", related.channelName)
        assertEquals(2438L, related.viewCount)
        assertEquals("hace 16 años", related.publishedText)
        assertEquals(
            "https://i.ytimg.com/vi/djV11Xbc914/hqdefault.jpg?sqp=-oaymwEjCNACELwBSFryq4qpAxUIARUAAAAAGAElAADIQj0AgKJDeAE=&rs=AOn4CLDjCgpUC_e_8rg9tIrX2mYOtlPqzg",
            related.thumbnailUrl,
        )
    }

    @Test
    fun `a lockup missing metadata row1 degrades viewCount and publishedText to null`() {
        val info = requireNotNull(response.videoDetailInfoOrNull("dQw4w9WgXcQ"))
        val related = info.related.first { it.id.value == "tSi6Dn1H36Y" }

        assertEquals("DJ Nick", related.channelName)
        assertNull(related.viewCount)
        assertNull(related.publishedText)
    }

    @Test
    fun `absence of videoPrimaryInfoRenderer maps to a null VideoDetailInfo`() {
        val unavailableText = requireNotNull(javaClass.getResourceAsStream("/innertube/next_unavailable.json")) {
            "missing fixture: innertube/next_unavailable.json"
        }.bufferedReader().readText()
        val unavailableResponse = json.decodeFromString<NextResponse>(unavailableText)

        assertNull(unavailableResponse.videoDetailInfoOrNull("zzzzzzzzzzz"))
    }
}
