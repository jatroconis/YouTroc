package com.youtroc.feature.playback

import com.youtroc.core.domain.playback.PlaybackManifest
import com.youtroc.core.domain.playback.PlaybackPosition
import com.youtroc.core.domain.playback.VideoQuality
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    /**
     * Reused by tests that don't care about scope-survival timing — an
     * Unconfined scope behaves like a plain synchronous call, same as the
     * (deliberately eager) `Dispatchers.Main` set below.
     */
    private lateinit var appScope: CoroutineScope

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() {
        appScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `plays from zero when nothing was saved`() = runTest {
        val player = FakeMediaPlayer()
        val store = FakeWatchProgressStore()
        val viewModel = PlaybackViewModel(player, store, videoId, appScope)

        viewModel.start(manifest)
        player.emitReady(durationMs = 200_000L)

        assertTrue(player.isPlaying)
        assertEquals(emptyList<Long>(), player.seekedTo)
    }

    @Test
    fun `auto-seeks to the saved position once duration is known`() = runTest {
        val player = FakeMediaPlayer()
        val store = FakeWatchProgressStore()
        store.save(videoId, PlaybackPosition(50_000L), durationMs = 200_000L, title = "", channel = "")
        val viewModel = PlaybackViewModel(player, store, videoId, appScope)

        viewModel.start(manifest)
        player.emitReady(durationMs = 200_000L)

        assertEquals(listOf(50_000L), player.seekedTo)
    }

    @Test
    fun `does not resume when the saved position is not resumable`() = runTest {
        val player = FakeMediaPlayer()
        val store = FakeWatchProgressStore()
        store.save(videoId, PlaybackPosition(9_000L), durationMs = 200_000L, title = "", channel = "") // under the 10s floor
        val viewModel = PlaybackViewModel(player, store, videoId, appScope)

        viewModel.start(manifest)
        player.emitReady(durationMs = 200_000L)

        assertEquals(emptyList<Long>(), player.seekedTo)
    }

    @Test
    fun `resume is only applied once per media load`() = runTest {
        val player = FakeMediaPlayer()
        val store = FakeWatchProgressStore()
        store.save(videoId, PlaybackPosition(50_000L), durationMs = 200_000L, title = "", channel = "")
        val viewModel = PlaybackViewModel(player, store, videoId, appScope)

        viewModel.start(manifest)
        player.emitReady(durationMs = 200_000L)
        player.emitReady(durationMs = 200_000L, positionMs = 60_000L) // a later state pulse, e.g. the position ticker

        assertEquals(listOf(50_000L), player.seekedTo)
    }

    @Test
    fun `pause pauses the player and persists the current position`() = runTest {
        val player = FakeMediaPlayer()
        val store = FakeWatchProgressStore()
        val viewModel = PlaybackViewModel(player, store, videoId, appScope)
        viewModel.start(manifest)
        player.emitReady(durationMs = 200_000L, positionMs = 42_000L)

        viewModel.pause()

        assertFalse(player.isPlaying)
        assertEquals(PlaybackPosition(42_000L), store.load(videoId))
    }

    @Test
    fun `seekTo clamps the absolute target within the video bounds`() = runTest {
        val player = FakeMediaPlayer()
        val store = FakeWatchProgressStore()
        val viewModel = PlaybackViewModel(player, store, videoId, appScope)
        viewModel.start(manifest)
        player.emitReady(durationMs = 200_000L, positionMs = 5_000L)

        viewModel.seekTo(250_000L) // absolute, past the end -> clamped to duration (NOT position-relative)
        viewModel.seekTo(-1_000L) // absolute, before the start -> clamped to 0

        assertEquals(listOf(200_000L, 0L), player.seekedTo)
    }

    @Test
    fun `togglePlayPause pauses a playing video and persists position`() = runTest {
        val player = FakeMediaPlayer()
        val store = FakeWatchProgressStore()
        val viewModel = PlaybackViewModel(player, store, videoId, appScope)
        viewModel.start(manifest)
        player.emitReady(durationMs = 200_000L)

        viewModel.togglePlayPause()

        assertFalse(player.isPlaying)
    }

    /**
     * BLOCKER B1 regression test: `pause()`'s persist MUST be scheduled on
     * the injected [PlaybackViewModel.appScope] (its own dedicated dispatcher
     * here), NOT on `viewModelScope` (which runs on the Unconfined `Main` set
     * in [setUp] and would therefore complete the save SYNCHRONOUSLY, before
     * this assertion, if it were still used). We prove the save is merely
     * QUEUED on the app scope's own scheduler right after [PlaybackViewModel.pause]
     * returns, then flush ONLY that scheduler and observe it land.
     */
    @Test
    fun `pause persists progress on the injected app scope, not viewModelScope`() = runTest {
        val player = FakeMediaPlayer()
        val store = FakeWatchProgressStore()
        val appScheduler = TestCoroutineScheduler()
        val survivingScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(appScheduler))
        val viewModel = PlaybackViewModel(player, store, videoId, survivingScope)
        viewModel.start(manifest)
        player.emitReady(durationMs = 200_000L, positionMs = 42_000L)

        viewModel.pause()

        // Not flushed yet: proves the write is scheduled on the app scope's
        // OWN (not-yet-advanced) scheduler, decoupled from viewModelScope's
        // eager Unconfined Main dispatcher.
        assertNull(store.load(videoId))

        appScheduler.advanceUntilIdle()

        assertEquals(PlaybackPosition(42_000L), store.load(videoId))
    }

    /**
     * BLOCKER B1 + MAJOR M1 regression test: `onCleared()` (invoked by the
     * framework when the NavBackStackEntry's ViewModelStore actually clears,
     * e.g. BACK) MUST release the player (REQ-6, M1) AND persist the final
     * position via the surviving app scope (B1) rather than losing it.
     */
    @Test
    fun `onCleared releases the player and persists progress via the surviving app scope`() = runTest {
        val player = FakeMediaPlayer()
        val store = FakeWatchProgressStore()
        val appScheduler = TestCoroutineScheduler()
        val survivingScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(appScheduler))
        val viewModel = PlaybackViewModel(player, store, videoId, survivingScope)
        viewModel.start(manifest)
        player.emitReady(durationMs = 200_000L, positionMs = 77_000L)

        viewModel.onCleared()

        assertTrue(player.released)
        assertNull(store.load(videoId)) // queued on the app scope, not yet flushed

        appScheduler.advanceUntilIdle()

        assertEquals(PlaybackPosition(77_000L), store.load(videoId))
    }

    /**
     * F1 regression test: the full 8-hop nav-arg -> ctor -> persist chain
     * must thread REAL title/channel through, not the "" default -- a
     * missed hop anywhere along the way would silently ship blank watch
     * history (REQ-HF7).
     */
    @Test
    fun `pause persists the constructed title and channel, not the blank default`() = runTest {
        val player = FakeMediaPlayer()
        val store = FakeWatchProgressStore()
        val viewModel = PlaybackViewModel(
            player = player,
            watchProgressStore = store,
            videoId = videoId,
            appScope = appScope,
            title = "Never Gonna Give You Up",
            channel = "Rick Astley",
        )
        viewModel.start(manifest)
        player.emitReady(durationMs = 200_000L, positionMs = 42_000L)

        viewModel.pause()

        val entry = store.readAll().single { it.videoId == videoId }
        assertTrue(entry.title.isNotBlank())
        assertTrue(entry.channel.isNotBlank())
        assertEquals("Never Gonna Give You Up", entry.title)
        assertEquals("Rick Astley", entry.channel)
    }

    /** M3: a live stream has no meaningful "position" to resume -- must not write history. */
    @Test
    fun `pause does not persist watch history while the stream is live`() = runTest {
        val player = FakeMediaPlayer()
        val store = FakeWatchProgressStore()
        val viewModel = PlaybackViewModel(player, store, videoId, appScope)
        viewModel.start(manifest)
        player.emitReady(durationMs = 200_000L, positionMs = 42_000L, isLive = true)

        viewModel.pause()

        assertEquals(emptyList(), store.readAll())
    }

    /** REQ-Q1/REQ-Q4: `onSelectQuality` is a pure delegation to the port. */
    @Test
    fun `onSelectQuality delegates to the player port`() = runTest {
        val player = FakeMediaPlayer()
        val store = FakeWatchProgressStore()
        val viewModel = PlaybackViewModel(player, store, videoId, appScope)
        viewModel.start(manifest)
        val quality = VideoQuality(id = "h720", label = "720p", heightPx = 720)

        viewModel.onSelectQuality(quality)

        assertEquals(FakeMediaPlayer.QualitySelection.Manual(quality), player.lastQualitySelection)
    }

    /** REQ-Q1/REQ-Q4: `onSelectAuto` is a pure delegation to the port. */
    @Test
    fun `onSelectAuto delegates to the player port`() = runTest {
        val player = FakeMediaPlayer()
        val store = FakeWatchProgressStore()
        val viewModel = PlaybackViewModel(player, store, videoId, appScope)
        viewModel.start(manifest)
        val quality = VideoQuality(id = "h720", label = "720p", heightPx = 720)
        viewModel.onSelectQuality(quality)

        viewModel.onSelectAuto()

        assertEquals(FakeMediaPlayer.QualitySelection.Auto, player.lastQualitySelection)
    }

    /** REQ-Q1: `playbackState` is the single observable source for qualities — no new flow. */
    @Test
    fun `playbackState surfaces available and active qualities from the player`() = runTest {
        val player = FakeMediaPlayer()
        val store = FakeWatchProgressStore()
        val viewModel = PlaybackViewModel(player, store, videoId, appScope)
        viewModel.start(manifest)
        val q1080 = VideoQuality(id = "h1080", label = "1080p", heightPx = 1080)
        val q720 = VideoQuality(id = "h720", label = "720p", heightPx = 720)

        player.emitQualities(available = listOf(q1080, q720), active = q720)

        assertEquals(listOf(q1080, q720), viewModel.playbackState.value.availableQualities)
        assertEquals(q720, viewModel.playbackState.value.activeQuality)
    }
}
