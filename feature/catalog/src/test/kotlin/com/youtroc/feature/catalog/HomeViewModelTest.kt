package com.youtroc.feature.catalog

import com.youtroc.core.domain.catalog.CatalogResult
import com.youtroc.core.domain.catalog.ComposeHomeFeed
import com.youtroc.core.domain.catalog.Shelf
import com.youtroc.core.domain.catalog.ShelfId
import com.youtroc.core.domain.catalog.ShelfSource
import com.youtroc.core.domain.catalog.Video
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

/**
 * Exercises [HomeViewModel]'s state mapping end-to-end through a REAL
 * [ComposeHomeFeed] wrapping fake [ShelfSource]s -- never a faked
 * `ComposeHomeFeed` itself, mirroring [com.youtroc.feature.playback.PlaybackViewModelTest].
 *
 * Uses its own [StandardTestDispatcher] (not Unconfined) for `Dispatchers.Main`
 * so the transient `Loading` state is actually observable before the feed
 * resolves -- mirrors the separately-scheduled `appScope` dispatcher already
 * used by `PlaybackViewModelTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val mainScheduler = TestCoroutineScheduler()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(mainScheduler))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val video = Video(
        id = VideoId("dQw4w9WgXcQ"),
        title = "Never Gonna Give You Up",
        channelName = "Rick Astley",
        thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hq720.jpg",
        viewCount = 1_600_000_000L,
        publishedText = "hace 15 a.",
    )

    /** A single-source [ShelfSource] whose [load] answers a fixed [CatalogResult] after a 1ms suspension. */
    private fun sourceOf(id: ShelfId, result: CatalogResult): ShelfSource = object : ShelfSource {
        override val id: ShelfId = id
        override val displayTitle: String? = null
        override val timeoutMs: Long = 10_000L
        override suspend fun load(): CatalogResult {
            delay(1)
            return result
        }
    }

    @Test
    fun `starts Loading before the composer's first snapshot`() = runTest {
        val viewModel = HomeViewModel(ComposeHomeFeed(listOf(sourceOf(ShelfId.TENDENCIAS, CatalogResult.Success(emptyList())))))

        // Runs the launched coroutine up to (not past) the fake's suspension
        // point, so this actually exercises `load()`'s Loading assignment
        // instead of only observing the StateFlow's coincidental initial value.
        mainScheduler.runCurrent()
        assertEquals(HomeUiState.Loading, viewModel.state.value)

        mainScheduler.advanceUntilIdle()
    }

    @Test
    fun `resolves to Content mapping shelves with Spanish display titles`() = runTest {
        val shelf = Shelf(id = ShelfId.TENDENCIAS, title = "Trending", videos = listOf(video))
        val viewModel = HomeViewModel(ComposeHomeFeed(listOf(sourceOf(ShelfId.TENDENCIAS, CatalogResult.Success(listOf(shelf))))))

        mainScheduler.advanceUntilIdle()

        val expected = HomeUiState.Content(
            listOf(HomeShelf(id = ShelfId.TENDENCIAS, title = "Tendencias", videos = listOf(video.toVideoCardUi()))),
        )
        assertEquals(expected, viewModel.state.value)
    }

    @Test
    fun `resolves to Empty when the lead outcome is Empty and no shelf ever lands`() = runTest {
        val viewModel = HomeViewModel(ComposeHomeFeed(listOf(sourceOf(ShelfId.TENDENCIAS, CatalogResult.Empty))))

        mainScheduler.advanceUntilIdle()

        assertEquals(HomeUiState.Empty, viewModel.state.value)
    }

    @Test
    fun `resolves to Offline when the lead outcome is Offline and no shelf ever lands`() = runTest {
        val viewModel = HomeViewModel(ComposeHomeFeed(listOf(sourceOf(ShelfId.TENDENCIAS, CatalogResult.Offline))))

        mainScheduler.advanceUntilIdle()

        assertEquals(HomeUiState.Offline, viewModel.state.value)
    }

    @Test
    fun `resolves to Error when the lead outcome is Error and no shelf ever lands`() = runTest {
        val viewModel = HomeViewModel(
            ComposeHomeFeed(listOf(sourceOf(ShelfId.TENDENCIAS, CatalogResult.Error(IllegalStateException("boom"))))),
        )

        mainScheduler.advanceUntilIdle()

        assertEquals(HomeUiState.Error, viewModel.state.value)
    }

    @Test
    fun `load cancels a still-open prior collection and re-seeds Loading instead of racing it`() = runTest {
        val source = MutableFakeShelfSource(id = ShelfId.TENDENCIAS, result = CatalogResult.Offline, delayMs = 30_000L)
        val viewModel = HomeViewModel(ComposeHomeFeed(listOf(source)))

        // Let the first collection's source start and suspend on its long delay --
        // WITHOUT advancing past it, so it is still genuinely open.
        mainScheduler.runCurrent()
        assertEquals(HomeUiState.Loading, viewModel.state.value)
        assertEquals(1, source.invocationCount)

        val secondShelf = Shelf(id = ShelfId.TENDENCIAS, title = "Trending", videos = listOf(video))
        source.result = CatalogResult.Success(listOf(secondShelf))
        source.delayMs = 1L
        viewModel.load()

        mainScheduler.runCurrent()
        assertEquals(HomeUiState.Loading, viewModel.state.value)
        assertEquals(2, source.invocationCount) // proves the retry re-invoked the source fresh

        // Safe to fully drain now: the first collection (and its 30s delay) was
        // cancelled by the retry, so it can never reach `_state` again -- only the
        // second (short) collection remains scheduled.
        mainScheduler.advanceUntilIdle()
        assertEquals(
            HomeUiState.Content(
                listOf(HomeShelf(id = ShelfId.TENDENCIAS, title = "Tendencias", videos = listOf(video.toVideoCardUi()))),
            ),
            viewModel.state.value,
        )
    }
}

/** A mutable single-source fake letting a test change the NEXT [load] call's answer/delay. */
private class MutableFakeShelfSource(
    override val id: ShelfId,
    var result: CatalogResult,
    var delayMs: Long,
) : ShelfSource {
    override val displayTitle: String? = null
    override val timeoutMs: Long = 60_000L

    var invocationCount = 0
        private set

    override suspend fun load(): CatalogResult {
        invocationCount++
        delay(delayMs)
        return result
    }
}
