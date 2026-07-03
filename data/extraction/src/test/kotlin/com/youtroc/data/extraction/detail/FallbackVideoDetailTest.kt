package com.youtroc.data.extraction.detail

import com.youtroc.core.domain.detail.DetailResult
import com.youtroc.core.domain.detail.VideoDetail
import com.youtroc.core.domain.detail.VideoDetailInfo
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deterministic, network-free verification of the own-first decorator
 * policy: own [DetailResult.Success]/[DetailResult.NotAvailable] pass
 * through untouched with no fallback invocation -- NotAvailable is a
 * legitimate own answer, never a fallback trigger (mirrors search's
 * [DetailResult.Success]/[com.youtroc.core.domain.search.SearchResult.Empty]
 * split, but there is no Empty case for detail). Own
 * [DetailResult.Error]/[DetailResult.Offline] delegate to (and return) the
 * fallback's result.
 *
 * [FakeVideoDetail] is defined here rather than reused from `core:domain`'s
 * test sources -- that module's test-scoped fake isn't visible cross-module.
 */
class FallbackVideoDetailTest {

    private val videoId = VideoId("dQw4w9WgXcQ")

    private val aDetail = VideoDetailInfo(
        videoId = videoId,
        title = "A video",
        channelName = "A channel",
        description = "A description",
        viewCount = 1_000L,
        publishedText = "1 day ago",
        related = emptyList(),
    )

    @Test
    fun `own success passes through and the fallback is never invoked`() = runTest {
        val own = FakeVideoDetail(DetailResult.Success(aDetail))
        val fallback = FakeVideoDetail(DetailResult.Error(IllegalStateException("should not be called")))
        val decorator = FallbackVideoDetail(own, fallback)

        val result = decorator.detail(videoId)

        assertEquals(DetailResult.Success(aDetail), result)
        assertFalse(fallback.wasInvoked)
    }

    @Test
    fun `own NotAvailable passes through and the fallback is never invoked`() = runTest {
        val own = FakeVideoDetail(DetailResult.NotAvailable)
        val fallback = FakeVideoDetail(DetailResult.Success(aDetail))
        val decorator = FallbackVideoDetail(own, fallback)

        val result = decorator.detail(videoId)

        assertEquals(DetailResult.NotAvailable, result)
        assertFalse(fallback.wasInvoked)
    }

    @Test
    fun `own error falls back to NewPipe's result`() = runTest {
        val own = FakeVideoDetail(DetailResult.Error(IllegalStateException("boom")))
        val fallback = FakeVideoDetail(DetailResult.Success(aDetail))
        val decorator = FallbackVideoDetail(own, fallback)

        val result = decorator.detail(videoId)

        assertEquals(DetailResult.Success(aDetail), result)
        assertTrue(fallback.wasInvoked)
    }

    @Test
    fun `own offline delegates to and returns the fallback's result`() = runTest {
        val fallbackFailure = DetailResult.Error(IllegalStateException("still down"))
        val own = FakeVideoDetail(DetailResult.Offline)
        val fallback = FakeVideoDetail(fallbackFailure)
        val decorator = FallbackVideoDetail(own, fallback)

        val result = decorator.detail(videoId)

        assertEquals(fallbackFailure, result)
        assertTrue(fallback.wasInvoked)
    }
}

private class FakeVideoDetail(private val result: DetailResult) : VideoDetail {
    var wasInvoked: Boolean = false
        private set

    override suspend fun detail(videoId: VideoId): DetailResult {
        wasInvoked = true
        return result
    }
}
