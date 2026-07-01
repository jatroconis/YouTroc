package com.youtroc.core.domain.detail

import com.youtroc.core.domain.catalog.Video
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetVideoDetailTest {

    private val sampleDetail = VideoDetailInfo(
        videoId = VideoId("dQw4w9WgXcQ"),
        title = "A video",
        channelName = "A channel",
        description = "A description",
        viewCount = 1_000,
        publishedText = "1 day ago",
        related = listOf(
            Video(
                id = VideoId("abc123"),
                title = "Related video",
                channelName = "Related channel",
                thumbnailUrl = "https://i.ytimg.com/vi/abc123/hq720.jpg",
                viewCount = 500,
                publishedText = "2 days ago",
            ),
        ),
    )

    @Test
    fun `returns the detail the port resolves`() = runTest {
        val useCase = GetVideoDetail(FakeVideoDetail(DetailResult.Success(sampleDetail)))

        val result = useCase(VideoId("dQw4w9WgXcQ"))

        assertEquals(DetailResult.Success(sampleDetail), result)
    }

    @Test
    fun `propagates NotAvailable without throwing`() = runTest {
        val useCase = GetVideoDetail(FakeVideoDetail(DetailResult.NotAvailable))

        assertEquals(DetailResult.NotAvailable, useCase(VideoId("dQw4w9WgXcQ")))
    }

    @Test
    fun `propagates Offline without throwing`() = runTest {
        val useCase = GetVideoDetail(FakeVideoDetail(DetailResult.Offline))

        assertEquals(DetailResult.Offline, useCase(VideoId("dQw4w9WgXcQ")))
    }

    @Test
    fun `propagates Error without throwing`() = runTest {
        val cause = IllegalStateException("boom")
        val useCase = GetVideoDetail(FakeVideoDetail(DetailResult.Error(cause)))

        assertEquals(DetailResult.Error(cause), useCase(VideoId("dQw4w9WgXcQ")))
    }

    @Test
    fun `passes the videoId through to the port`() = runTest {
        val fake = FakeVideoDetail(DetailResult.NotAvailable)
        val useCase = GetVideoDetail(fake)

        useCase(VideoId("dQw4w9WgXcQ"))

        assertEquals(VideoId("dQw4w9WgXcQ"), fake.lastVideoId)
    }
}
