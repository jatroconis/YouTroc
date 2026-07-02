package com.youtroc.data.extraction.search

import com.youtroc.core.domain.catalog.Video
import com.youtroc.core.domain.search.SearchResult
import com.youtroc.core.domain.search.VideoSearch
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deterministic, network-free verification of the own-first decorator
 * policy: own [SearchResult.Success]/[SearchResult.Empty] pass through
 * untouched with no fallback invocation; own [SearchResult.Error]/
 * [SearchResult.Offline] delegate to (and return) the fallback's result.
 *
 * [FakeVideoSearch] is defined here rather than reused from `core:domain`'s
 * test sources -- that module's test-scoped fake isn't visible cross-module.
 */
class FallbackVideoSearchTest {

    private val aVideo = Video(
        id = VideoId("dQw4w9WgXcQ"),
        title = "A video",
        channelName = "A channel",
        thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hq720.jpg",
        viewCount = 1_000L,
        publishedText = "1 day ago",
    )

    @Test
    fun `own success passes through and the fallback is never invoked`() = runTest {
        val own = FakeVideoSearch(SearchResult.Success(listOf(aVideo)))
        val fallback = FakeVideoSearch(SearchResult.Error(IllegalStateException("should not be called")))
        val decorator = FallbackVideoSearch(own, fallback)

        val result = decorator.search("lofi")

        assertEquals(SearchResult.Success(listOf(aVideo)), result)
        assertFalse(fallback.wasInvoked)
    }

    @Test
    fun `own empty passes through and the fallback is never invoked`() = runTest {
        val own = FakeVideoSearch(SearchResult.Empty)
        val fallback = FakeVideoSearch(SearchResult.Success(listOf(aVideo)))
        val decorator = FallbackVideoSearch(own, fallback)

        val result = decorator.search("lofi")

        assertEquals(SearchResult.Empty, result)
        assertFalse(fallback.wasInvoked)
    }

    @Test
    fun `own error falls back to NewPipe's result`() = runTest {
        val own = FakeVideoSearch(SearchResult.Error(IllegalStateException("boom")))
        val fallback = FakeVideoSearch(SearchResult.Success(listOf(aVideo)))
        val decorator = FallbackVideoSearch(own, fallback)

        val result = decorator.search("lofi")

        assertEquals(SearchResult.Success(listOf(aVideo)), result)
        assertTrue(fallback.wasInvoked)
    }

    @Test
    fun `own offline delegates to and returns the fallback's result`() = runTest {
        val fallbackFailure = SearchResult.Error(IllegalStateException("still down"))
        val own = FakeVideoSearch(SearchResult.Offline)
        val fallback = FakeVideoSearch(fallbackFailure)
        val decorator = FallbackVideoSearch(own, fallback)

        val result = decorator.search("lofi")

        assertEquals(fallbackFailure, result)
        assertTrue(fallback.wasInvoked)
    }
}

private class FakeVideoSearch(private val result: SearchResult) : VideoSearch {
    var wasInvoked: Boolean = false
        private set

    override suspend fun search(query: String): SearchResult {
        wasInvoked = true
        return result
    }
}
