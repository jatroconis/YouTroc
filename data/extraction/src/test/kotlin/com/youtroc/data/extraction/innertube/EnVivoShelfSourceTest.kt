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

/**
 * Network-free verification of [EnVivoShelfSource]'s live-badge filter (M3):
 * a fake [okhttp3.Interceptor] short-circuits the real network call with a
 * canned response, mirroring [InnerTubeStreamProviderTest].
 */
class EnVivoShelfSourceTest {

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

    private fun renderer(id: String, live: Boolean): RenderItem = RenderItem(
        videoRenderer = VideoRenderer(
            videoId = id,
            badges = if (live) {
                listOf(Badge(metadataBadgeRenderer = MetadataBadgeRenderer(style = "BADGE_STYLE_TYPE_LIVE_NOW")))
            } else {
                null
            },
        ),
    )

    private fun responseBody(renderers: List<RenderItem>): String {
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
    fun `only live-badged renderers make the shelf`() = runTest {
        val renderers = listOf(
            renderer("live1", live = true),
            renderer("live2", live = true),
            renderer("notLive1", live = false),
            renderer("live3", live = true),
            renderer("notLive2", live = false),
        )
        val source = EnVivoShelfSource(client = fakeClient(responseBody(renderers)))

        val result = source.load()

        val success = assertIs<CatalogResult.Success>(result)
        val shelf = success.shelves.single()
        assertEquals(ShelfId.EN_VIVO, shelf.id)
        assertEquals(listOf("live1", "live2", "live3"), shelf.videos.map { it.id.value })
    }

    @Test
    fun `zero live-badged renderers yield Empty`() = runTest {
        val renderers = listOf(renderer("notLive1", live = false), renderer("notLive2", live = false))
        val source = EnVivoShelfSource(client = fakeClient(responseBody(renderers)))

        val result = source.load()

        assertEquals(CatalogResult.Empty, result)
    }
}
