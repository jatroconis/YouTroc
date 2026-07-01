package com.youtroc.feature.search

import com.youtroc.core.domain.catalog.Video
import com.youtroc.core.domain.search.SearchResult
import com.youtroc.core.domain.search.SearchVideos
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
 * Exercises [SearchViewModel]'s state mapping end-to-end through a REAL
 * [SearchVideos] wrapping the local fake [FakeVideoSearch] — never a faked
 * `SearchVideos` itself, since the use case is `final` and part of what this
 * suite verifies (mirrors [com.youtroc.feature.catalog.HomeViewModelTest]).
 *
 * Also encodes gate MAJOR-1 (design-gate #4408, closes acceptance-checklist
 * #5): a blank/whitespace confirm MUST stay `Idle` AND MUST NEVER reach the
 * port — [FakeVideoSearch.callCount] staying `0` is the real assertion, the
 * `Idle` state alone is not enough proof.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

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
    fun `initial state is Idle before any query is confirmed`() {
        val viewModel = SearchViewModel(SearchVideos(FakeVideoSearch(SearchResult.Empty)))

        assertEquals(SearchUiState.Idle, viewModel.state.value)
    }

    @Test
    fun `search transitions Idle to Loading before the result resolves`() = runTest {
        val viewModel = SearchViewModel(SearchVideos(FakeVideoSearch(SearchResult.Success(emptyList()))))

        viewModel.search("lofi")
        // Runs the launched coroutine up to (not past) the fake's suspension
        // point, so this actually exercises the Loading assignment instead of
        // only observing the StateFlow's coincidental initial value.
        mainScheduler.runCurrent()
        assertEquals(SearchUiState.Loading, viewModel.state.value)

        mainScheduler.advanceUntilIdle()
    }

    @Test
    fun `search resolves to Results mapping videos with Spanish meta`() = runTest {
        val fake = FakeVideoSearch(SearchResult.Success(listOf(video)))
        val viewModel = SearchViewModel(SearchVideos(fake))

        viewModel.search("rick astley")
        mainScheduler.advanceUntilIdle()

        assertEquals(SearchUiState.Results(listOf(video.toVideoCardUi())), viewModel.state.value)
        assertEquals("rick astley", fake.lastQuery)
        assertEquals(1, fake.callCount)
    }

    @Test
    fun `search resolves to Empty`() = runTest {
        val viewModel = SearchViewModel(SearchVideos(FakeVideoSearch(SearchResult.Empty)))

        viewModel.search("asdkjhaskjdh")
        mainScheduler.advanceUntilIdle()

        assertEquals(SearchUiState.Empty, viewModel.state.value)
    }

    @Test
    fun `search resolves to Offline`() = runTest {
        val viewModel = SearchViewModel(SearchVideos(FakeVideoSearch(SearchResult.Offline)))

        viewModel.search("lofi")
        mainScheduler.advanceUntilIdle()

        assertEquals(SearchUiState.Offline, viewModel.state.value)
    }

    @Test
    fun `search resolves to Error`() = runTest {
        val viewModel = SearchViewModel(
            SearchVideos(FakeVideoSearch(SearchResult.Error(IllegalStateException("boom")))),
        )

        viewModel.search("lofi")
        mainScheduler.advanceUntilIdle()

        assertEquals(SearchUiState.Error, viewModel.state.value)
    }

    @Test
    fun `retry re-invokes search with the last confirmed query, re-emitting Loading first`() = runTest {
        val fake = FakeVideoSearch(SearchResult.Offline)
        val viewModel = SearchViewModel(SearchVideos(fake))
        viewModel.search("lofi")
        mainScheduler.advanceUntilIdle()
        assertEquals(SearchUiState.Offline, viewModel.state.value)

        fake.result = SearchResult.Success(emptyList())
        viewModel.retry()

        mainScheduler.runCurrent()
        assertEquals(SearchUiState.Loading, viewModel.state.value)

        mainScheduler.advanceUntilIdle()
        assertEquals(SearchUiState.Results(emptyList()), viewModel.state.value)
        assertEquals("lofi", fake.lastQuery)
        assertEquals(2, fake.callCount)
    }

    @Test
    fun `blank query confirm stays Idle and never calls the port`() = runTest {
        val fake = FakeVideoSearch(SearchResult.Success(listOf(video)))
        val viewModel = SearchViewModel(SearchVideos(fake))

        viewModel.search("")
        mainScheduler.advanceUntilIdle()

        assertEquals(SearchUiState.Idle, viewModel.state.value)
        assertEquals(0, fake.callCount)
    }

    @Test
    fun `whitespace-only query confirm stays Idle and never calls the port`() = runTest {
        val fake = FakeVideoSearch(SearchResult.Success(listOf(video)))
        val viewModel = SearchViewModel(SearchVideos(fake))

        viewModel.search("   ")
        mainScheduler.advanceUntilIdle()

        assertEquals(SearchUiState.Idle, viewModel.state.value)
        assertEquals(0, fake.callCount)
    }
}
