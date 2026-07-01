package com.youtroc.core.domain.search

import com.youtroc.core.domain.catalog.Video
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchVideosTest {

    private val sampleVideos = listOf(
        Video(
            id = VideoId("abc123"),
            title = "A video",
            channelName = "A channel",
            thumbnailUrl = "https://i.ytimg.com/vi/abc123/hq720.jpg",
            viewCount = 1_000,
            publishedText = "1 day ago",
        ),
    )

    @Test
    fun `returns the videos the port resolves`() = runTest {
        val useCase = SearchVideos(FakeVideoSearch(SearchResult.Success(sampleVideos)))

        val result = useCase("lofi")

        assertEquals(SearchResult.Success(sampleVideos), result)
    }

    @Test
    fun `propagates Empty without throwing`() = runTest {
        val useCase = SearchVideos(FakeVideoSearch(SearchResult.Empty))

        assertEquals(SearchResult.Empty, useCase("lofi"))
    }

    @Test
    fun `propagates Offline without throwing`() = runTest {
        val useCase = SearchVideos(FakeVideoSearch(SearchResult.Offline))

        assertEquals(SearchResult.Offline, useCase("lofi"))
    }

    @Test
    fun `propagates Error without throwing`() = runTest {
        val cause = IllegalStateException("boom")
        val useCase = SearchVideos(FakeVideoSearch(SearchResult.Error(cause)))

        assertEquals(SearchResult.Error(cause), useCase("lofi"))
    }

    @Test
    fun `passes the query through to the port`() = runTest {
        val fake = FakeVideoSearch(SearchResult.Empty)
        val useCase = SearchVideos(fake)

        useCase("lofi beats")

        assertEquals("lofi beats", fake.lastQuery)
    }
}
