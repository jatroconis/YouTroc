package com.youtroc.core.domain.playback

import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract test for [WatchProgressStore]: any implementation (the in-memory
 * fake here, a DataStore adapter later) must satisfy this round trip.
 */
class WatchProgressStoreContractTest {

    private val store: WatchProgressStore = FakeWatchProgressStore()
    private val videoId = VideoId("dQw4w9WgXcQ")

    @Test
    fun `loads back exactly what was saved`() = runTest {
        val position = PlaybackPosition(42_000L)

        store.save(videoId, position, durationMs = 120_000L)

        assertEquals(position, store.load(videoId))
    }

    @Test
    fun `returns null for a video that was never saved`() = runTest {
        assertNull(store.load(VideoId("neverSavedVideoId")))
    }

    @Test
    fun `save with title and channel is retrievable via readAll`() = runTest {
        store.save(videoId, PlaybackPosition(42_000L), durationMs = 120_000L, title = "T", channel = "C")

        val entry = store.readAll().single { it.videoId == videoId }

        assertEquals("T", entry.title)
        assertEquals("C", entry.channel)
        assertTrue(entry.watchedAt > 0, "expected a positive watchedAt timestamp, got ${entry.watchedAt}")
    }
}
