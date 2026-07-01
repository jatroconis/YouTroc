package com.youtroc.feature.video

import com.youtroc.core.domain.catalog.Video
import com.youtroc.core.domain.detail.DetailResult
import com.youtroc.core.domain.detail.GetVideoDetail
import com.youtroc.core.domain.detail.VideoDetailInfo
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
 * Exercises [DetailViewModel]'s state mapping end-to-end through a REAL
 * [GetVideoDetail] wrapping the local fake [FakeVideoDetail] — never a faked
 * `GetVideoDetail` itself, since the use case is `final` and part of what
 * this suite verifies (mirrors [com.youtroc.feature.catalog.HomeViewModelTest]).
 *
 * [DetailViewModel] auto-loads in `init` (like `HomeViewModel`, NOT `Idle`
 * like `SearchViewModel` — the design's ADR for the auto-load shape), and
 * keeps `videoId: String` as its constructor param, building [VideoId]
 * INSIDE the load coroutine (gate-review correction #3): `VideoId`'s
 * `require(non-blank)` init check must not throw at ViewModel construction
 * time, mirrors [com.youtroc.app.ui.player.PlayerViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    private val mainScheduler = TestCoroutineScheduler()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(mainScheduler))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val relatedVideo = Video(
        id = VideoId("relatedVid1"),
        title = "Related video",
        channelName = "Related channel",
        thumbnailUrl = "https://i.ytimg.com/vi/relatedVid1/hq720.jpg",
        viewCount = 500,
        publishedText = "hace 2 días",
    )

    private val detail = VideoDetailInfo(
        videoId = VideoId("dQw4w9WgXcQ"),
        title = "Never Gonna Give You Up",
        channelName = "Rick Astley",
        description = "The official video",
        viewCount = 1_600_000_000L,
        publishedText = "hace 15 a.",
        related = listOf(relatedVideo),
    )

    @Test
    fun `starts Loading before the detail resolves`() = runTest {
        val viewModel = DetailViewModel(
            videoId = "dQw4w9WgXcQ",
            getVideoDetail = GetVideoDetail(FakeVideoDetail(DetailResult.Success(detail))),
        )

        // Runs the launched coroutine up to (not past) the fake's suspension
        // point, so this actually exercises `load()`'s Loading assignment
        // instead of only observing the StateFlow's coincidental initial value.
        mainScheduler.runCurrent()
        assertEquals(DetailUiState.Loading, viewModel.state.value)

        mainScheduler.advanceUntilIdle()
    }

    @Test
    fun `resolves to Content mapping detail with Spanish meta and related videos`() = runTest {
        val viewModel = DetailViewModel(
            videoId = "dQw4w9WgXcQ",
            getVideoDetail = GetVideoDetail(FakeVideoDetail(DetailResult.Success(detail))),
        )

        mainScheduler.advanceUntilIdle()

        val expected = DetailUiState.Content(
            VideoDetailUi(
                title = "Never Gonna Give You Up",
                channel = "Rick Astley",
                meta = "1.6 B vistas · hace 15 a.",
                description = "The official video",
                related = listOf(relatedVideo.toVideoCardUi()),
            ),
        )
        assertEquals(expected, viewModel.state.value)
    }

    @Test
    fun `resolves to Content with an empty related list when detail has no related videos`() = runTest {
        val fake = FakeVideoDetail(DetailResult.Success(detail.copy(related = emptyList())))
        val viewModel = DetailViewModel(videoId = "dQw4w9WgXcQ", getVideoDetail = GetVideoDetail(fake))

        mainScheduler.advanceUntilIdle()

        val content = viewModel.state.value as DetailUiState.Content
        assertEquals(emptyList(), content.detail.related)
    }

    @Test
    fun `resolves to Content with an empty description when the source description is null`() = runTest {
        val fake = FakeVideoDetail(DetailResult.Success(detail.copy(description = null)))
        val viewModel = DetailViewModel(videoId = "dQw4w9WgXcQ", getVideoDetail = GetVideoDetail(fake))

        mainScheduler.advanceUntilIdle()

        val content = viewModel.state.value as DetailUiState.Content
        assertEquals("", content.detail.description)
    }

    @Test
    fun `resolves to NotAvailable`() = runTest {
        val viewModel = DetailViewModel(
            videoId = "dQw4w9WgXcQ",
            getVideoDetail = GetVideoDetail(FakeVideoDetail(DetailResult.NotAvailable)),
        )

        mainScheduler.advanceUntilIdle()

        assertEquals(DetailUiState.NotAvailable, viewModel.state.value)
    }

    @Test
    fun `resolves to Offline`() = runTest {
        val viewModel = DetailViewModel(
            videoId = "dQw4w9WgXcQ",
            getVideoDetail = GetVideoDetail(FakeVideoDetail(DetailResult.Offline)),
        )

        mainScheduler.advanceUntilIdle()

        assertEquals(DetailUiState.Offline, viewModel.state.value)
    }

    @Test
    fun `resolves to Error`() = runTest {
        val viewModel = DetailViewModel(
            videoId = "dQw4w9WgXcQ",
            getVideoDetail = GetVideoDetail(FakeVideoDetail(DetailResult.Error(IllegalStateException("boom")))),
        )

        mainScheduler.advanceUntilIdle()

        assertEquals(DetailUiState.Error, viewModel.state.value)
    }

    @Test
    fun `retry re-invokes the port, re-emitting Loading before the new result`() = runTest {
        val fake = FakeVideoDetail(DetailResult.Offline)
        val viewModel = DetailViewModel(videoId = "dQw4w9WgXcQ", getVideoDetail = GetVideoDetail(fake))
        mainScheduler.advanceUntilIdle()
        assertEquals(DetailUiState.Offline, viewModel.state.value)

        fake.result = DetailResult.Success(detail)
        viewModel.retry()

        mainScheduler.runCurrent()
        assertEquals(DetailUiState.Loading, viewModel.state.value)

        mainScheduler.advanceUntilIdle()
        assertEquals(DetailUiState.Content(detail.toDetailUi()), viewModel.state.value)
        assertEquals(2, fake.callCount)
    }

    @Test
    fun `passes the constructor videoId through to the port as a VideoId`() = runTest {
        val fake = FakeVideoDetail(DetailResult.NotAvailable)
        DetailViewModel(videoId = "dQw4w9WgXcQ", getVideoDetail = GetVideoDetail(fake))

        mainScheduler.advanceUntilIdle()

        assertEquals(VideoId("dQw4w9WgXcQ"), fake.lastVideoId)
    }

    @Test
    fun `resolves to NotAvailable without throwing when videoId is blank, and never calls the port`() = runTest {
        val fake = FakeVideoDetail(DetailResult.Success(detail))
        val viewModel = DetailViewModel(videoId = "", getVideoDetail = GetVideoDetail(fake))

        // Must not escape as an uncaught IllegalArgumentException from
        // VideoId's require(non-blank) — a blank videoId is a deterministic
        // NotAvailable state, not a crash.
        mainScheduler.advanceUntilIdle()

        assertEquals(DetailUiState.NotAvailable, viewModel.state.value)
        assertEquals(0, fake.callCount)
    }
}
