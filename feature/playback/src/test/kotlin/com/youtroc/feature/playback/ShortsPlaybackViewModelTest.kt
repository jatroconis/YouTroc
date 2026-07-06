package com.youtroc.feature.playback

import com.youtroc.core.domain.playback.GetPlayableStreams
import com.youtroc.core.domain.playback.PlaybackManifest
import com.youtroc.core.domain.stream.PlayableStreams
import com.youtroc.core.domain.stream.Stream
import com.youtroc.core.domain.stream.StreamKind
import com.youtroc.core.domain.stream.StreamProvider
import com.youtroc.core.domain.stream.StreamResult
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises [ShortsPlaybackViewModel]'s pager over fakes — no Media3/Android
 * involved, mirrors [PlaybackViewModelTest]'s own conventions. Uses its own
 * [StandardTestDispatcher] (not Unconfined) so the still-suspended prior
 * resolution can be observed before it is discarded (mirrors
 * `HomeViewModelTest`'s "load cancels a still-open prior collection" case,
 * the closest existing precedent for this exact flatMapLatest-cancellation
 * shape, N5).
 *
 * [ShortsPlaybackViewModel]'s constructor structurally has NO
 * [com.youtroc.core.domain.playback.WatchProgressStore] parameter anywhere in
 * this file (R6, store-less) -- Shorts never write watch history.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShortsPlaybackViewModelTest {

    private val mainScheduler = TestCoroutineScheduler()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(mainScheduler))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val minimalStream = Stream(url = "https://cdn/av1", container = "webm", kind = StreamKind.VIDEO_ONLY)

    private fun manifestFor(id: String) = PlaybackManifest(
        kind = PlaybackManifest.Kind.PROGRESSIVE,
        payload = "https://example.com/$id.mp4",
        adaptive = false,
    )

    private fun successFor(id: String) = StreamResult.Success(PlayableStreams(listOf(minimalStream), manifest = manifestFor(id)))

    private fun itemsOf(vararg ids: String) = ids.map { ShortsQueueItem(id = it, title = "title-$it", channel = "channel-$it") }

    /** A [StreamProvider] whose per-videoId delay/result are independently configurable. */
    private class FakeShortsStreamProvider(
        private val results: Map<String, StreamResult>,
        private val delaysMs: Map<String, Long> = emptyMap(),
    ) : StreamProvider {
        val requested = mutableListOf<VideoId>()
        override suspend fun playableStreams(videoId: VideoId): StreamResult {
            requested.add(videoId)
            delay(delaysMs[videoId.value] ?: 0L)
            return results.getValue(videoId.value)
        }
    }

    @Test
    fun `resolves the start index, hands the manifest to the player, and plays`() = runTest {
        val player = FakeMediaPlayer()
        val provider = FakeShortsStreamProvider(mapOf("A" to successFor("A")))
        val viewModel = ShortsPlaybackViewModel(player, GetPlayableStreams(provider), itemsOf("A", "B", "C"), startIndex = 0)

        mainScheduler.advanceUntilIdle()

        assertEquals(manifestFor("A"), player.lastManifest)
        assertTrue(player.isPlaying)
    }

    /**
     * N5: advancing the index while the PRIOR index's resolution is still
     * suspended must discard it -- only the NEW index's resolution may ever
     * reach [com.youtroc.core.domain.playback.MediaPlayer.setMedia].
     */
    @Test
    fun `advancing past a still-suspended resolution discards it -- only the new index lands`() = runTest {
        val player = FakeMediaPlayer()
        val provider = FakeShortsStreamProvider(
            results = mapOf("A" to successFor("A"), "B" to successFor("B")),
            delaysMs = mapOf("A" to 30_000L, "B" to 0L),
        )
        val viewModel = ShortsPlaybackViewModel(player, GetPlayableStreams(provider), itemsOf("A", "B"), startIndex = 0)

        // Let A's resolution start and suspend on its long delay -- WITHOUT
        // advancing past it, so it is still genuinely open.
        mainScheduler.runCurrent()
        assertNull(player.lastManifest)

        viewModel.next() // DOWN: switch to index 1 (B) while A is still in-flight

        mainScheduler.runCurrent()
        assertEquals(manifestFor("B"), player.lastManifest)

        // Safe to fully drain now: flatMapLatest cancelled A's collector, so
        // its setMedia call (gated behind the still-pending 30s delay) can
        // never land even once the virtual clock is fully advanced.
        mainScheduler.advanceUntilIdle()
        assertEquals(manifestFor("B"), player.lastManifest)
        assertEquals(listOf(VideoId("A"), VideoId("B")), provider.requested)
    }

    @Test
    fun `previous clamps at index zero instead of going negative`() = runTest {
        val player = FakeMediaPlayer()
        val provider = FakeShortsStreamProvider(mapOf("A" to successFor("A"), "B" to successFor("B")))
        val viewModel = ShortsPlaybackViewModel(player, GetPlayableStreams(provider), itemsOf("A", "B"), startIndex = 0)
        mainScheduler.advanceUntilIdle()

        viewModel.previous()
        mainScheduler.advanceUntilIdle()

        assertEquals(0, viewModel.currentIndex.value)
        assertEquals(manifestFor("A"), player.lastManifest)
    }

    @Test
    fun `next clamps at the last index instead of overflowing`() = runTest {
        val player = FakeMediaPlayer()
        val provider = FakeShortsStreamProvider(mapOf("A" to successFor("A"), "B" to successFor("B")))
        val viewModel = ShortsPlaybackViewModel(player, GetPlayableStreams(provider), itemsOf("A", "B"), startIndex = 1)

        viewModel.next()
        mainScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.currentIndex.value)
        assertEquals(manifestFor("B"), player.lastManifest)
    }

    @Test
    fun `onCleared releases the player`() = runTest {
        val player = FakeMediaPlayer()
        val provider = FakeShortsStreamProvider(mapOf("A" to successFor("A")))
        val viewModel = ShortsPlaybackViewModel(player, GetPlayableStreams(provider), itemsOf("A"), startIndex = 0)
        mainScheduler.advanceUntilIdle()

        viewModel.onCleared()

        assertTrue(player.released)
    }
}
