package com.youtroc.data.extraction.catalog

import com.youtroc.core.domain.catalog.CatalogResult
import com.youtroc.core.domain.catalog.Video
import com.youtroc.core.domain.search.SearchResult
import com.youtroc.core.domain.search.VideoSearch
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Exercises the pure [SearchResult] -> [CatalogResult] mapping of the
 * search-backed Tendencias fallback. The real NewPipe network call lives in
 * [com.youtroc.data.extraction.search.NewPipeVideoSearch] (own tests); here a
 * fake [VideoSearch] pins the mapping and the query this catalog issues.
 */
class NewPipeTrendingSearchCatalogTest {

    private val video = Video(
        id = VideoId("dQw4w9WgXcQ"),
        title = "A Video",
        channelName = "A Channel",
        thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
        viewCount = null,
        publishedText = null,
    )

    private class FakeSearch(private val result: SearchResult) : VideoSearch {
        var lastQuery: String? = null
        override suspend fun search(query: String): SearchResult {
            lastQuery = query
            return result
        }
    }

    @Test
    fun `success maps to a single Tendencias shelf issuing the trending seed query`() = runTest {
        val fake = FakeSearch(SearchResult.Success(listOf(video)))

        val result = NewPipeTrendingSearchCatalog(fake).trending()

        val success = assertIs<CatalogResult.Success>(result)
        assertEquals(1, success.shelves.size)
        assertEquals("Tendencias", success.shelves.single().title)
        assertEquals(listOf(video), success.shelves.single().videos)
        assertEquals("tendencias", fake.lastQuery)
    }

    @Test
    fun `empty, offline, and error pass through as their catalog mirrors`() = runTest {
        assertEquals(CatalogResult.Empty, NewPipeTrendingSearchCatalog(FakeSearch(SearchResult.Empty)).trending())
        assertEquals(CatalogResult.Offline, NewPipeTrendingSearchCatalog(FakeSearch(SearchResult.Offline)).trending())

        val boom = IllegalStateException("boom")
        val error = NewPipeTrendingSearchCatalog(FakeSearch(SearchResult.Error(boom))).trending()
        assertEquals(boom, assertIs<CatalogResult.Error>(error).cause)
    }
}
