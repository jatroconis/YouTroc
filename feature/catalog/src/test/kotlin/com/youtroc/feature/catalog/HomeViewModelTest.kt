package com.youtroc.feature.catalog

import com.youtroc.core.domain.catalog.CatalogResult
import com.youtroc.core.domain.catalog.GetHomeFeed
import com.youtroc.core.domain.catalog.Shelf
import com.youtroc.core.domain.catalog.Video
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * [GetHomeFeed] wrapping the local fake [FakeVideoCatalog] — never a faked
 * `GetHomeFeed` itself, since the use case is `final` and part of what this
 * suite verifies (mirrors [com.youtroc.feature.playback.PlaybackViewModelTest]).
 *
 * Uses its own [StandardTestDispatcher] (not Unconfined) for `Dispatchers.Main`
 * so the transient `Loading` state is actually observable before the feed
 * resolves — mirrors the separately-scheduled `appScope` dispatcher already
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

    @Test
    fun `starts Loading before the feed resolves`() = runTest {
        val viewModel = HomeViewModel(GetHomeFeed(FakeVideoCatalog(CatalogResult.Success(emptyList()))))

        // Runs the launched coroutine up to (not past) the fake's suspension
        // point, so this actually exercises `load()`'s Loading assignment
        // instead of only observing the StateFlow's coincidental initial value.
        mainScheduler.runCurrent()
        assertEquals(HomeUiState.Loading, viewModel.state.value)

        mainScheduler.advanceUntilIdle()
    }

    @Test
    fun `resolves to Content mapping shelves with Spanish meta and display title`() = runTest {
        val shelf = Shelf(title = "Trending", videos = listOf(video))
        val viewModel = HomeViewModel(GetHomeFeed(FakeVideoCatalog(CatalogResult.Success(listOf(shelf)))))

        mainScheduler.advanceUntilIdle()

        val expected = HomeUiState.Content(
            listOf(HomeShelf(title = "Tendencia", videos = listOf(video.toVideoCardUi()))),
        )
        assertEquals(expected, viewModel.state.value)
    }

    @Test
    fun `resolves to Empty`() = runTest {
        val viewModel = HomeViewModel(GetHomeFeed(FakeVideoCatalog(CatalogResult.Empty)))

        mainScheduler.advanceUntilIdle()

        assertEquals(HomeUiState.Empty, viewModel.state.value)
    }

    @Test
    fun `resolves to Offline`() = runTest {
        val viewModel = HomeViewModel(GetHomeFeed(FakeVideoCatalog(CatalogResult.Offline)))

        mainScheduler.advanceUntilIdle()

        assertEquals(HomeUiState.Offline, viewModel.state.value)
    }

    @Test
    fun `resolves to Error`() = runTest {
        val viewModel = HomeViewModel(
            GetHomeFeed(FakeVideoCatalog(CatalogResult.Error(IllegalStateException("boom")))),
        )

        mainScheduler.advanceUntilIdle()

        assertEquals(HomeUiState.Error, viewModel.state.value)
    }

    @Test
    fun `load re-invokes the feed, re-emitting Loading before the new result`() = runTest {
        val catalog = FakeVideoCatalog(CatalogResult.Offline)
        val viewModel = HomeViewModel(GetHomeFeed(catalog))
        mainScheduler.advanceUntilIdle()
        assertEquals(HomeUiState.Offline, viewModel.state.value)

        catalog.result = CatalogResult.Success(emptyList())
        viewModel.load()

        mainScheduler.runCurrent()
        assertEquals(HomeUiState.Loading, viewModel.state.value)

        mainScheduler.advanceUntilIdle()

        assertEquals(HomeUiState.Content(emptyList()), viewModel.state.value)
    }
}
