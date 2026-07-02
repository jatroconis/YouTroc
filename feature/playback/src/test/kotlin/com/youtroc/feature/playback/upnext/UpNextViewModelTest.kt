package com.youtroc.feature.playback.upnext

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
 * Exercises [UpNextViewModel]'s LAZY resolution end-to-end through a REAL
 * [GetVideoDetail] wrapping the local fake [FakeVideoDetail] — never a faked
 * `GetVideoDetail` itself (mirrors the deleted `:feature:video`
 * `DetailViewModelTest`, but REWRITTEN for lazy semantics per the
 * player-upnext design gate R5).
 *
 * Unlike the deleted `DetailViewModel` (auto-loaded in `init`), [UpNextViewModel]
 * MUST NOT call [GetVideoDetail] until [UpNextViewModel.ensureLoaded] is
 * invoked (REQ-U3 — no fetch at playback start or overlay reveal), and MUST
 * cache the result for the rest of the video's session (no re-fetch on a
 * second panel open).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpNextViewModelTest {

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
    fun `does not call the port on construction`() = runTest {
        val fake = FakeVideoDetail(DetailResult.Success(detail))
        val viewModel = UpNextViewModel(videoId = "dQw4w9WgXcQ", getVideoDetail = GetVideoDetail(fake))

        mainScheduler.runCurrent()

        assertEquals(0, fake.callCount)
        assertEquals(DetailUiState.Loading, viewModel.state.value)
    }

    @Test
    fun `ensureLoaded calls the port exactly once and resolves to Content`() = runTest {
        val fake = FakeVideoDetail(DetailResult.Success(detail))
        val viewModel = UpNextViewModel(videoId = "dQw4w9WgXcQ", getVideoDetail = GetVideoDetail(fake))

        viewModel.ensureLoaded()
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
        assertEquals(1, fake.callCount)
        assertEquals(VideoId("dQw4w9WgXcQ"), fake.lastVideoId)
    }

    @Test
    fun `a second ensureLoaded call reuses the cached result and does not call the port again`() = runTest {
        val fake = FakeVideoDetail(DetailResult.Success(detail))
        val viewModel = UpNextViewModel(videoId = "dQw4w9WgXcQ", getVideoDetail = GetVideoDetail(fake))

        viewModel.ensureLoaded()
        mainScheduler.advanceUntilIdle()
        assertEquals(1, fake.callCount)

        viewModel.ensureLoaded()
        mainScheduler.advanceUntilIdle()

        assertEquals(1, fake.callCount)
    }

    @Test
    fun `ensureLoaded resolves to Content with an empty related list when detail has no related videos`() = runTest {
        val fake = FakeVideoDetail(DetailResult.Success(detail.copy(related = emptyList())))
        val viewModel = UpNextViewModel(videoId = "dQw4w9WgXcQ", getVideoDetail = GetVideoDetail(fake))

        viewModel.ensureLoaded()
        mainScheduler.advanceUntilIdle()

        val content = viewModel.state.value as DetailUiState.Content
        assertEquals(emptyList(), content.detail.related)
    }

    @Test
    fun `ensureLoaded resolves to Content with an empty description when the source description is null`() = runTest {
        val fake = FakeVideoDetail(DetailResult.Success(detail.copy(description = null)))
        val viewModel = UpNextViewModel(videoId = "dQw4w9WgXcQ", getVideoDetail = GetVideoDetail(fake))

        viewModel.ensureLoaded()
        mainScheduler.advanceUntilIdle()

        val content = viewModel.state.value as DetailUiState.Content
        assertEquals("", content.detail.description)
    }

    @Test
    fun `ensureLoaded resolves to NotAvailable`() = runTest {
        val fake = FakeVideoDetail(DetailResult.NotAvailable)
        val viewModel = UpNextViewModel(videoId = "dQw4w9WgXcQ", getVideoDetail = GetVideoDetail(fake))

        viewModel.ensureLoaded()
        mainScheduler.advanceUntilIdle()

        assertEquals(DetailUiState.NotAvailable, viewModel.state.value)
    }

    @Test
    fun `ensureLoaded resolves to Offline`() = runTest {
        val fake = FakeVideoDetail(DetailResult.Offline)
        val viewModel = UpNextViewModel(videoId = "dQw4w9WgXcQ", getVideoDetail = GetVideoDetail(fake))

        viewModel.ensureLoaded()
        mainScheduler.advanceUntilIdle()

        assertEquals(DetailUiState.Offline, viewModel.state.value)
    }

    @Test
    fun `ensureLoaded resolves to Error`() = runTest {
        val fake = FakeVideoDetail(DetailResult.Error(IllegalStateException("boom")))
        val viewModel = UpNextViewModel(videoId = "dQw4w9WgXcQ", getVideoDetail = GetVideoDetail(fake))

        viewModel.ensureLoaded()
        mainScheduler.advanceUntilIdle()

        assertEquals(DetailUiState.Error, viewModel.state.value)
    }

    @Test
    fun `retry re-invokes the port regardless of cache state, re-emitting Loading before the new result`() = runTest {
        val fake = FakeVideoDetail(DetailResult.Offline)
        val viewModel = UpNextViewModel(videoId = "dQw4w9WgXcQ", getVideoDetail = GetVideoDetail(fake))
        viewModel.ensureLoaded()
        mainScheduler.advanceUntilIdle()
        assertEquals(DetailUiState.Offline, viewModel.state.value)
        assertEquals(1, fake.callCount)

        fake.result = DetailResult.Success(detail)
        viewModel.retry()

        mainScheduler.runCurrent()
        assertEquals(DetailUiState.Loading, viewModel.state.value)

        mainScheduler.advanceUntilIdle()
        assertEquals(DetailUiState.Content(detail.toDetailUi()), viewModel.state.value)
        assertEquals(2, fake.callCount)

        // A cache flag already set by the earlier ensureLoaded() must not be
        // reset by retry() — a subsequent ensureLoaded() reuses the retried
        // result instead of firing a third call.
        viewModel.ensureLoaded()
        mainScheduler.advanceUntilIdle()
        assertEquals(2, fake.callCount)
    }

    @Test
    fun `ensureLoaded on a blank videoId resolves to NotAvailable without calling the port`() = runTest {
        val fake = FakeVideoDetail(DetailResult.Success(detail))
        val viewModel = UpNextViewModel(videoId = "", getVideoDetail = GetVideoDetail(fake))

        viewModel.ensureLoaded()
        mainScheduler.advanceUntilIdle()

        assertEquals(DetailUiState.NotAvailable, viewModel.state.value)
        assertEquals(0, fake.callCount)
    }
}
