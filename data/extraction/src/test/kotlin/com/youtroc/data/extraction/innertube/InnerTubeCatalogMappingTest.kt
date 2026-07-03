package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.video.VideoId
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Deterministic, network-free verification of regional-shelf detection
 * (ADR-2, design-gate CORRECTION 1): TITLE PREFIX (`"Popular en "`), NOT
 * "first shelf with items" -- a channel-promo shelfRenderer (e.g. "Lo último
 * de Noticias Caracol") can precede the region-anchoring one in a real
 * response.
 *
 * Fixtures:
 * - `search_web_regional_shelf.json`: live-captured (`tendencias` seed, WEB,
 *   hl=es, gl=CO, stale clientVersion, CO egress) -- "Popular en Bogotá" is
 *   the only shelfRenderer, trimmed to 3 items.
 * - `search_web_channel_then_regional.json`: real shelves captured live
 *   (`noticias` seed, WEB, hl=es, gl=CO, fresh clientVersion 2.20260630.03.00,
 *   CO egress) -- "Lo último de Noticias Caracol" (channel-promo,
 *   trimmed to 2 items) and "Popular en Bogotá" (trimmed to 2 items) are both
 *   genuine captured shelves, hand-reordered (channel shelf placed first) to
 *   deterministically lock in title-prefix-over-first-shelf detection, since
 *   YouTube's live shelf ordering is not stable across requests.
 */
class InnerTubeCatalogMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun fixture(name: String): SearchResponse {
        val text = requireNotNull(javaClass.getResourceAsStream("/innertube/$name")) {
            "missing fixture: innertube/$name"
        }.bufferedReader().readText()
        return json.decodeFromString(text)
    }

    @Test
    fun `title prefix beats first-shelf-with-items when a channel-promo shelf precedes the regional one`() {
        val response = fixture("search_web_channel_then_regional.json")

        val shelf = response.regionalShelf()

        assertEquals("Popular en Bogotá", shelf?.title)
        assertEquals(
            listOf("TSAXSpy4ujM", "Caeq0CL1wis"),
            shelf?.videos?.map { it.id.value },
        )
    }

    @Test
    fun `the matched shelf title is verbatim`() {
        val response = fixture("search_web_regional_shelf.json")

        val shelf = response.regionalShelf()

        assertEquals("Popular en Bogotá", shelf?.title)
    }

    @Test
    fun `shelf videos are mapped in source order via toVideoOrNull`() {
        val response = fixture("search_web_regional_shelf.json")

        val shelf = response.regionalShelf()

        assertEquals(
            listOf(VideoId("JAcIoFmaVg0"), VideoId("nedSgeaf0s4"), VideoId("oI1OfhVIR-U")),
            shelf?.videos?.map { it.id },
        )
    }

    @Test
    fun `a Popular en shelf with zero mappable items yields null, deferring to the labeled-flat fallback`() {
        val response = SearchResponse(
            contents = Contents(
                twoColumnSearchResultsRenderer = TwoColumnSearchResultsRenderer(
                    primaryContents = PrimaryContents(
                        sectionListRenderer = SectionListRenderer(
                            contents = listOf(
                                SectionItem(
                                    itemSectionRenderer = ItemSectionRenderer(
                                        contents = listOf(
                                            RenderItem(
                                                shelfRenderer = ShelfRenderer(
                                                    title = SimpleText(simpleText = "Popular en Medellín"),
                                                    content = ShelfContent(
                                                        verticalListRenderer = VerticalListRenderer(
                                                            // Every item lacks a videoId -> zero mappable videos.
                                                            items = listOf(RenderItem(videoRenderer = VideoRenderer(videoId = null))),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertNull(response.regionalShelf())
    }

    @Test
    fun `no Popular en shelf anywhere yields null`() {
        val response = SearchResponse(
            contents = Contents(
                twoColumnSearchResultsRenderer = TwoColumnSearchResultsRenderer(
                    primaryContents = PrimaryContents(
                        sectionListRenderer = SectionListRenderer(
                            contents = listOf(
                                SectionItem(
                                    itemSectionRenderer = ItemSectionRenderer(
                                        contents = listOf(
                                            RenderItem(videoRenderer = VideoRenderer(videoId = "someVideo")),
                                            RenderItem(
                                                shelfRenderer = ShelfRenderer(
                                                    title = SimpleText(simpleText = "Canales nuevos para ti"),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertNull(response.regionalShelf())
    }
}
