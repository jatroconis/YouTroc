package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.catalog.CatalogResult
import com.youtroc.core.domain.catalog.ShelfId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ShortsShelfSourceTest {

    private fun fakeClient(body: String): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

    private fun lockup(videoId: String?, title: String, viewsText: String, url: String, width: Int): GridShelfContent =
        GridShelfContent(
            shortsLockupViewModel = ShortsLockupViewModel(
                entityId = "shorts-shelf-item-$videoId",
                onTap = ShortsOnTap(
                    innertubeCommand = ShortsInnertubeCommand(reelWatchEndpoint = ReelWatchEndpoint(videoId = videoId)),
                ),
                overlayMetadata = ShortsOverlayMetadata(
                    primaryText = ShortsText(content = title),
                    secondaryText = ShortsText(content = viewsText),
                ),
                thumbnailViewModel = ShortsThumbnailWrapper(
                    thumbnailViewModel = ShortsThumbnailImage(
                        image = ShortsImageSources(
                            sources = listOf(ShortsImageSource(url = url, width = width, height = width * 16 / 9)),
                        ),
                    ),
                ),
            ),
        )

    private fun responseBody(contents: List<GridShelfContent>): String {
        val response = SearchResponse(
            contents = Contents(
                twoColumnSearchResultsRenderer = TwoColumnSearchResultsRenderer(
                    primaryContents = PrimaryContents(
                        sectionListRenderer = SectionListRenderer(
                            contents = listOf(
                                SectionItem(
                                    itemSectionRenderer = ItemSectionRenderer(
                                        contents = listOf(RenderItem(gridShelfViewModel = GridShelfViewModel(contents = contents))),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        return innerTubeSearchJson.encodeToString(response)
    }

    @Test
    fun `well-formed shorts entries map per-field, dropping the one with a missing videoId`() = runTest {
        val contents = listOf(
            lockup("short1", "First short", "3,6 M de visualizaciones", "https://example.com/short1_405.jpg", 405),
            lockup(null, "No id short", "1 M de visualizaciones", "https://example.com/noid.jpg", 405),
            lockup("short2", "Second short", "500 K de visualizaciones", "https://example.com/short2_405.jpg", 405),
            lockup("short3", "Third short", "12 de visualizaciones", "https://example.com/short3_405.jpg", 405),
        )
        val source = ShortsShelfSource(client = fakeClient(responseBody(contents)))

        val result = source.load()

        val success = assertIs<CatalogResult.Success>(result)
        val shelf = success.shelves.single()
        assertEquals(ShelfId.SHORTS, shelf.id)
        assertEquals(listOf("short1", "short2", "short3"), shelf.videos.map { it.id.value })

        val first = shelf.videos.first()
        assertEquals("First short", first.title)
        assertEquals("Unknown channel", first.channelName)
        assertEquals("https://example.com/short1_405.jpg", first.thumbnailUrl)
        assertNull(first.viewCount)
        assertNull(first.publishedText)
    }

    @Test
    fun `zero mappable shorts entries yield Empty`() = runTest {
        val source = ShortsShelfSource(client = fakeClient(responseBody(emptyList())))

        val result = source.load()

        assertEquals(CatalogResult.Empty, result)
    }
}
