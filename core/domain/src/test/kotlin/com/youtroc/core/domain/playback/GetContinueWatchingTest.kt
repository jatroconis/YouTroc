package com.youtroc.core.domain.playback

import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure-JVM verification of the "Seguir viendo" inclusion rule (REQ-HF9): a
 * partially-watched video is included, one watched to >=95% of its duration
 * is excluded (the boundary itself excludes), and ordering is left entirely
 * to [WatchProgressStore.readAll] (REQ-HF8) -- this use case only filters. A
 * blank title/channel is ALSO excluded here as a defense-in-depth net (F1),
 * on top of the store-level guards.
 */
class GetContinueWatchingTest {

    private fun entry(
        idSuffix: String,
        positionMs: Long,
        durationMs: Long,
        title: String = "Title-$idSuffix",
        channel: String = "Channel-$idSuffix",
    ): WatchHistoryEntry = WatchHistoryEntry(
        videoId = VideoId("id-$idSuffix"),
        title = title,
        channel = channel,
        watchedAt = 0L,
        position = PlaybackPosition(positionMs),
        durationMs = durationMs,
    )

    private class FakeStore(private val entries: List<WatchHistoryEntry>) : WatchProgressStore {
        override suspend fun save(videoId: VideoId, position: PlaybackPosition, durationMs: Long, title: String, channel: String) = Unit
        override suspend fun load(videoId: VideoId): PlaybackPosition? = null
        override suspend fun readAll(): List<WatchHistoryEntry> = entries
    }

    private fun namesOf(entries: List<WatchHistoryEntry>): List<String> = entries.map { it.videoId.value }

    @Test
    fun `a video watched to 40 percent of its duration is included`() = runTest {
        val store = FakeStore(listOf(entry("a", positionMs = 40_000L, durationMs = 100_000L)))

        assertEquals(listOf("id-a"), namesOf(GetContinueWatching(store)()))
    }

    @Test
    fun `a video watched to 97 percent of its duration is excluded`() = runTest {
        val store = FakeStore(listOf(entry("a", positionMs = 97_000L, durationMs = 100_000L)))

        assertEquals(emptyList(), namesOf(GetContinueWatching(store)()))
    }

    @Test
    fun `a video watched to exactly 95 percent is excluded -- the ge-95 boundary excludes`() = runTest {
        val store = FakeStore(listOf(entry("a", positionMs = 95_000L, durationMs = 100_000L)))

        assertEquals(emptyList(), namesOf(GetContinueWatching(store)()))
    }

    @Test
    fun `a video watched to 94point9 percent is included -- just under the boundary`() = runTest {
        val store = FakeStore(listOf(entry("a", positionMs = 94_999L, durationMs = 100_000L)))

        assertEquals(listOf("id-a"), namesOf(GetContinueWatching(store)()))
    }

    @Test
    fun `ordering delegates entirely to readAll -- no re-sort`() = runTest {
        val store = FakeStore(
            listOf(
                entry("c", positionMs = 1_000L, durationMs = 100_000L),
                entry("b", positionMs = 1_000L, durationMs = 100_000L),
                entry("a", positionMs = 1_000L, durationMs = 100_000L),
            ),
        )

        assertEquals(listOf("id-c", "id-b", "id-a"), namesOf(GetContinueWatching(store)()))
    }

    @Test
    fun `an entry with a blank title or channel is excluded -- F1 defensive net`() = runTest {
        val store = FakeStore(
            listOf(
                entry("blank-title", positionMs = 1_000L, durationMs = 100_000L, title = ""),
                entry("blank-channel", positionMs = 1_000L, durationMs = 100_000L, channel = ""),
            ),
        )

        assertEquals(emptyList(), namesOf(GetContinueWatching(store)()))
    }
}
