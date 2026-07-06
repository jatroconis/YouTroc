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

class SearchQueryShelfSourceTest {

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

    private fun responseBody(videoIds: List<String>): String {
        val renderers = videoIds.map { RenderItem(videoRenderer = VideoRenderer(videoId = it)) }
        val response = SearchResponse(
            contents = Contents(
                twoColumnSearchResultsRenderer = TwoColumnSearchResultsRenderer(
                    primaryContents = PrimaryContents(
                        sectionListRenderer = SectionListRenderer(
                            contents = listOf(SectionItem(itemSectionRenderer = ItemSectionRenderer(contents = renderers))),
                        ),
                    ),
                ),
            ),
        )
        return innerTubeSearchJson.encodeToString(response)
    }

    @Test
    fun `mappable videoRenderers become a Shelf tagged with the ctor id and title`() = runTest {
        val source = SearchQueryShelfSource(
            id = ShelfId.MUSICA,
            displayTitle = "Música",
            query = "música",
            client = fakeClient(responseBody(listOf("m1", "m2", "m3"))),
        )

        val result = source.load()

        val success = assertIs<CatalogResult.Success>(result)
        val shelf = success.shelves.single()
        assertEquals(ShelfId.MUSICA, shelf.id)
        assertEquals("Música", shelf.title)
        assertEquals(listOf("m1", "m2", "m3"), shelf.videos.map { it.id.value })
    }

    @Test
    fun `zero mappable videoRenderers yield Empty`() = runTest {
        val source = SearchQueryShelfSource(
            id = ShelfId.VIDEOJUEGOS,
            displayTitle = "Videojuegos",
            query = "videojuegos",
            client = fakeClient(responseBody(emptyList())),
        )

        val result = source.load()

        assertEquals(CatalogResult.Empty, result)
    }
}
