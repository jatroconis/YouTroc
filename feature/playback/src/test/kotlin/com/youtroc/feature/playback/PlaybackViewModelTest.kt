package com.youtroc.feature.playback

import com.youtroc.core.domain.playback.PlaybackManifest
import com.youtroc.core.domain.playback.PlaybackPosition
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises [PlaybackViewModel]'s decision logic end-to-end with fakes — no
 * Media3/Android/Compose involved (REQ-12/REQ-13 acceptance criteria).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackViewModelTest {

    private val videoId = VideoId("dQw4w9WgXcQ")
    private val manifest = PlaybackManifest(
        kind = PlaybackManifest.Kind.PROGRESSIVE,
        payload = "https://example.com/video.mp4",
        adaptive = false,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `plays from zero when nothing was saved`() = runTest {
        val player = FakeMediaPlayer()
        val store = FakeWatchProgressStore()
        val viewModel = PlaybackViewModel(player, store, videoId)

        viewModel.start(manifest)
        player.emitReady(durationMs = 200_000L)

        assertTrue(player.isPlaying)
        assertEquals(emptyList<Long>(), player.seekedTo)
    }

    @Test
    fun `auto-seeks to the saved position once duration is known`() = runTest {
        val player = FakeMediaPlayer()
        val store = FakeWatchProgressStore()
        store.save(videoId, PlaybackPosition(50_000L), durationMs = 200_000L)
        val viewModel = PlaybackViewModel(player, store, videoId)

        viewModel.start(manifest)
        player.emitReady(durationMs = 200_000L)

        assertEquals(listOf(50_000L), player.seekedTo)
    }

    @Test
    fun `does not resume when the saved position is not resumable`() = runTest {
        val player = FakeMediaPlayer()
        val store = FakeWatchProgressStore()
        store.save(videoId, PlaybackPosition(9_000L), durationMs = 200_000L) // under the 10s floor
        val viewModel = PlaybackViewModel(player, store, videoId)

        viewModel.start(manifest)
        player.emitReady(durationMs = 200_000L)

        assertEquals(emptyList<Long>(), player.seekedTo)
    }

    @Test
    fun `resume is only applied once per media load`() = runTest {
        val player = FakeMediaPlayer()
        val store = FakeWatchProgressStore()
        store.save(videoId, PlaybackPosition(50_000L), durationMs = 200_000L)
        val viewModel = PlaybackViewModel(player, store, videoId)

        viewModel.start(manifest)
        player.emitReady(durationMs = 200_000L)
        player.emitReady(durationMs = 200_000L, positionMs = 60_000L) // a later state pulse, e.g. the position ticker

        assertEquals(listOf(50_000L), player.seekedTo)
    }

    @Test
    fun `pause pauses the player and persists the current position`() = runTest {
        val player = FakeMediaPlayer()
        val store = FakeWatchProgressStore()
        val viewModel = PlaybackViewModel(player, store, videoId)
        viewModel.start(manifest)
        player.emitReady(durationMs = 200_000L, positionMs = 42_000L)

        viewModel.pause()

        assertFalse(player.isPlaying)
        assertEquals(PlaybackPosition(42_000L), store.load(videoId))
    }

    @Test
    fun `seekBy clamps within the video bounds`() = runTest {
        val player = FakeMediaPlayer()
        val store = FakeWatchProgressStore()
        val viewModel = PlaybackViewModel(player, store, videoId)
        viewModel.start(manifest)
        player.emitReady(durationMs = 200_000L, positionMs = 5_000L)

        viewModel.seekBy(-30_000L)

        assertEquals(listOf(0L), player.seekedTo)
    }

    @Test
    fun `togglePlayPause pauses a playing video and persists position`() = runTest {
        val player = FakeMediaPlayer()
        val store = FakeWatchProgressStore()
        val viewModel = PlaybackViewModel(player, store, videoId)
        viewModel.start(manifest)
        player.emitReady(durationMs = 200_000L)

        viewModel.togglePlayPause()

        assertFalse(player.isPlaying)
    }
}
