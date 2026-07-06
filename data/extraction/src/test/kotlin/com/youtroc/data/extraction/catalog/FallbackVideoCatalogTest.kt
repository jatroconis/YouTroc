package com.youtroc.data.extraction.catalog

import com.youtroc.core.domain.catalog.CatalogResult
import com.youtroc.core.domain.catalog.Shelf
import com.youtroc.core.domain.catalog.ShelfId
import com.youtroc.core.domain.catalog.Video
import com.youtroc.core.domain.catalog.VideoCatalog
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deterministic, network-free verification of the own-first decorator
 * policy. Mirrors [com.youtroc.data.extraction.stream.LadderStreamProvider]
 * (Success-ONLY pass-through) rather than
 * [com.youtroc.data.extraction.search.FallbackVideoSearch] (which trusts
 * Empty too): a blank Home is a bad terminal UX, so own's
 * [CatalogResult.Empty] is NOT authoritative here -- NewPipe's Trending kiosk
 * is a labeled, structured fallback and always gets a chance to fill Home.
 *
 * [FakeVideoCatalog] is defined here rather than reused from `core:domain`'s
 * test sources -- that module's test-scoped fake isn't visible cross-module.
 */
class FallbackVideoCatalogTest {

    private val someShelves = CatalogResult.Success(
        shelves = listOf(
            Shelf(
                id = ShelfId.TENDENCIAS,
                title = "Popular en Bogotá",
                videos = listOf(
                    Video(
                        id = VideoId("dQw4w9WgXcQ"),
                        title = "Sample",
                        channelName = "Channel",
                        thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hq720.jpg",
                        viewCount = 100L,
                        publishedText = "hace 1 día",
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `own success passes through and NewPipe is never invoked`() = runTest {
        val own = FakeVideoCatalog(someShelves)
        val newPipe = FakeVideoCatalog(CatalogResult.Error(IllegalStateException("should not be called")))
        val decorator = FallbackVideoCatalog(own, newPipe)

        val result = decorator.trending()

        assertEquals(someShelves, result)
        assertFalse(newPipe.wasInvoked)
    }

    @Test
    fun `own empty falls back to and returns NewPipe's result`() = runTest {
        val own = FakeVideoCatalog(CatalogResult.Empty)
        val newPipe = FakeVideoCatalog(someShelves)
        val decorator = FallbackVideoCatalog(own, newPipe)

        val result = decorator.trending()

        assertEquals(someShelves, result)
        assertTrue(newPipe.wasInvoked)
    }

    @Test
    fun `own offline falls back to and returns NewPipe's result`() = runTest {
        val own = FakeVideoCatalog(CatalogResult.Offline)
        val newPipe = FakeVideoCatalog(someShelves)
        val decorator = FallbackVideoCatalog(own, newPipe)

        val result = decorator.trending()

        assertEquals(someShelves, result)
        assertTrue(newPipe.wasInvoked)
    }

    @Test
    fun `own error falls back to and returns NewPipe's result`() = runTest {
        val newPipeFailure = CatalogResult.Error(IllegalStateException("still down"))
        val own = FakeVideoCatalog(CatalogResult.Error(IllegalStateException("boom")))
        val newPipe = FakeVideoCatalog(newPipeFailure)
        val decorator = FallbackVideoCatalog(own, newPipe)

        val result = decorator.trending()

        assertEquals(newPipeFailure, result)
        assertTrue(newPipe.wasInvoked)
    }
}

private class FakeVideoCatalog(private val result: CatalogResult) : VideoCatalog {
    var wasInvoked: Boolean = false
        private set

    override suspend fun trending(): CatalogResult {
        wasInvoked = true
        return result
    }
}
