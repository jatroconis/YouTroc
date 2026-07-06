package com.youtroc.core.domain.catalog

import com.youtroc.core.domain.playback.GetContinueWatching
import com.youtroc.core.domain.playback.PlaybackPosition
import com.youtroc.core.domain.playback.WatchHistoryEntry
import com.youtroc.core.domain.playback.WatchProgressStore
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The "Seguir viendo" [ShelfSource] (REQ-HF9): wraps [GetContinueWatching]
 * and maps each [WatchHistoryEntry] to a [Video], synthesizing a thumbnail
 * URL from the ytimg CDN template (N8) since watch-history entries carry no
 * thumbnail of their own. Empty when there is nothing to resume (HF2
 * isolation net -- same contract as every other [ShelfSource]).
 */
class SeguirViendoShelfSourceTest {

    private class FakeStore(private val entries: List<WatchHistoryEntry>) : WatchProgressStore {
        override suspend fun save(videoId: VideoId, position: PlaybackPosition, durationMs: Long, title: String, channel: String) = Unit
        override suspend fun load(videoId: VideoId): PlaybackPosition? = null
        override suspend fun readAll(): List<WatchHistoryEntry> = entries
    }

    private fun entry(idSuffix: String): WatchHistoryEntry = WatchHistoryEntry(
        videoId = VideoId("id-$idSuffix"),
        title = "Title-$idSuffix",
        channel = "Channel-$idSuffix",
        watchedAt = 0L,
        position = PlaybackPosition(1_000L),
        durationMs = 100_000L,
    )

    @Test
    fun `two resumable entries map to a Seguir viendo shelf with synthesized thumbnails`() = runTest {
        val store = FakeStore(listOf(entry("a"), entry("b")))
        val source = SeguirViendoShelfSource(GetContinueWatching(store))

        val result = source.load()

        val expected = CatalogResult.Success(
            listOf(
                Shelf(
                    id = ShelfId.SEGUIR_VIENDO,
                    title = source.displayTitle ?: "",
                    videos = listOf(
                        Video(
                            id = VideoId("id-a"),
                            title = "Title-a",
                            channelName = "Channel-a",
                            thumbnailUrl = "https://i.ytimg.com/vi/id-a/hqdefault.jpg",
                            viewCount = null,
                            publishedText = null,
                        ),
                        Video(
                            id = VideoId("id-b"),
                            title = "Title-b",
                            channelName = "Channel-b",
                            thumbnailUrl = "https://i.ytimg.com/vi/id-b/hqdefault.jpg",
                            viewCount = null,
                            publishedText = null,
                        ),
                    ),
                ),
            ),
        )
        assertEquals(expected, result)
    }

    @Test
    fun `no resumable entries yields Empty -- HF2 isolation net`() = runTest {
        val source = SeguirViendoShelfSource(GetContinueWatching(FakeStore(emptyList())))

        assertEquals(CatalogResult.Empty, source.load())
    }
}
