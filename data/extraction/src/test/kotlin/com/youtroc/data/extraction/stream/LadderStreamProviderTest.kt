package com.youtroc.data.extraction.stream

import com.youtroc.core.domain.stream.PlayableStreams
import com.youtroc.core.domain.stream.Stream
import com.youtroc.core.domain.stream.StreamKind
import com.youtroc.core.domain.stream.StreamProvider
import com.youtroc.core.domain.stream.StreamResult
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deterministic, network-free verification of the ordered N-rung ladder
 * policy. R4 (BLOCKING): DELIBERATELY diverges from
 * [com.youtroc.data.extraction.detail.FallbackVideoDetail] (which passes
 * [StreamResult.NotAvailable] through as a trusted own answer) -- here, a
 * rung's [StreamResult.NotAvailable] is NOT authoritative: android_vr
 * conflates genuine unavailability with UNPLAYABLE false-negatives (A/B
 * throttling) and ALL live videos, both recoverable by a lower rung. Only
 * [StreamResult.Success] short-circuits; [StreamResult.Error]/
 * [StreamResult.Offline]/[StreamResult.NotAvailable] ALL fall through to the
 * next rung; the terminal rung's result is always returned RAW (no further
 * mapping).
 *
 * [FakeStreamProvider] is defined here rather than reused from `core:domain`'s
 * test sources -- that module's test-scoped fake isn't visible cross-module.
 */
class LadderStreamProviderTest {

    private val videoId = VideoId("dQw4w9WgXcQ")

    private val someStreams = PlayableStreams(
        streams = listOf(Stream(url = "https://cdn/a", container = "mp4", kind = StreamKind.MUXED)),
    )

    @Test
    fun `android_vr success passes through and lower rungs are never invoked`() = runTest {
        var resolved: Pair<VideoId, StreamSource>? = null
        val androidVr = FakeStreamProvider(StreamResult.Success(someStreams))
        val newPipe = FakeStreamProvider(StreamResult.Error(IllegalStateException("should not be called")))
        val ladder = LadderStreamProvider(
            rungs = listOf(
                StreamRung(StreamSource.ANDROID_VR, androidVr),
                StreamRung(StreamSource.FALLBACK, newPipe),
            ),
            onResolved = { id, s -> resolved = id to s },
        )

        val result = ladder.playableStreams(videoId)

        assertEquals(StreamResult.Success(someStreams), result)
        assertFalse(newPipe.wasInvoked)
        assertEquals(videoId to StreamSource.ANDROID_VR, resolved)
    }

    @Test
    fun `android_vr NotAvailable falls through to and returns NewPipe's result (R4 BLOCKING divergence)`() = runTest {
        var resolved: Pair<VideoId, StreamSource>? = null
        val androidVr = FakeStreamProvider(StreamResult.NotAvailable)
        val newPipe = FakeStreamProvider(StreamResult.Success(someStreams))
        val ladder = LadderStreamProvider(
            rungs = listOf(
                StreamRung(StreamSource.ANDROID_VR, androidVr),
                StreamRung(StreamSource.FALLBACK, newPipe),
            ),
            onResolved = { id, s -> resolved = id to s },
        )

        val result = ladder.playableStreams(videoId)

        assertEquals(StreamResult.Success(someStreams), result)
        assertTrue(newPipe.wasInvoked)
        assertEquals(videoId to StreamSource.FALLBACK, resolved)
    }

    @Test
    fun `android_vr error falls through to and returns NewPipe's result`() = runTest {
        var resolved: Pair<VideoId, StreamSource>? = null
        val androidVr = FakeStreamProvider(StreamResult.Error(IllegalStateException("boom")))
        val newPipe = FakeStreamProvider(StreamResult.Success(someStreams))
        val ladder = LadderStreamProvider(
            rungs = listOf(
                StreamRung(StreamSource.ANDROID_VR, androidVr),
                StreamRung(StreamSource.FALLBACK, newPipe),
            ),
            onResolved = { id, s -> resolved = id to s },
        )

        val result = ladder.playableStreams(videoId)

        assertEquals(StreamResult.Success(someStreams), result)
        assertTrue(newPipe.wasInvoked)
        assertEquals(videoId to StreamSource.FALLBACK, resolved)
    }

    @Test
    fun `android_vr offline falls through to and returns NewPipe's result`() = runTest {
        var resolved: Pair<VideoId, StreamSource>? = null
        val newPipeFailure = StreamResult.Error(IllegalStateException("still down"))
        val androidVr = FakeStreamProvider(StreamResult.Offline)
        val newPipe = FakeStreamProvider(newPipeFailure)
        val ladder = LadderStreamProvider(
            rungs = listOf(
                StreamRung(StreamSource.ANDROID_VR, androidVr),
                StreamRung(StreamSource.FALLBACK, newPipe),
            ),
            onResolved = { id, s -> resolved = id to s },
        )

        val result = ladder.playableStreams(videoId)

        assertEquals(newPipeFailure, result)
        assertTrue(newPipe.wasInvoked)
        assertEquals(videoId to StreamSource.FALLBACK, resolved)
    }

    @Test
    fun `onResolved defaults to a no-op when not provided`() = runTest {
        val androidVr = FakeStreamProvider(StreamResult.Success(someStreams))
        val newPipe = FakeStreamProvider(StreamResult.Error(IllegalStateException("should not be called")))
        val ladder = LadderStreamProvider(
            rungs = listOf(
                StreamRung(StreamSource.ANDROID_VR, androidVr),
                StreamRung(StreamSource.FALLBACK, newPipe),
            ),
        )

        val result = ladder.playableStreams(videoId)

        assertEquals(StreamResult.Success(someStreams), result)
    }

    @Test
    fun `ios wins on a 3-rung fixture -- android_vr fails, ios succeeds, NewPipe is never invoked`() = runTest {
        var resolved: Pair<VideoId, StreamSource>? = null
        val androidVr = FakeStreamProvider(StreamResult.NotAvailable)
        val ios = FakeStreamProvider(StreamResult.Success(someStreams))
        val newPipe = FakeStreamProvider(StreamResult.Error(IllegalStateException("should not be called")))
        val ladder = LadderStreamProvider(
            rungs = listOf(
                StreamRung(StreamSource.ANDROID_VR, androidVr),
                StreamRung(StreamSource.IOS, ios),
                StreamRung(StreamSource.FALLBACK, newPipe),
            ),
            onResolved = { id, s -> resolved = id to s },
        )

        val result = ladder.playableStreams(videoId)

        assertEquals(StreamResult.Success(someStreams), result)
        assertFalse(newPipe.wasInvoked)
        assertEquals(videoId to StreamSource.IOS, resolved)
    }

    @Test
    fun `all rungs fail on a 3-rung fixture -- the terminal rung's raw result is returned and reports FALLBACK`() = runTest {
        var resolved: Pair<VideoId, StreamSource>? = null
        val androidVr = FakeStreamProvider(StreamResult.NotAvailable)
        val ios = FakeStreamProvider(StreamResult.Offline)
        val newPipeFailure = StreamResult.Error(IllegalStateException("still down"))
        val newPipe = FakeStreamProvider(newPipeFailure)
        val ladder = LadderStreamProvider(
            rungs = listOf(
                StreamRung(StreamSource.ANDROID_VR, androidVr),
                StreamRung(StreamSource.IOS, ios),
                StreamRung(StreamSource.FALLBACK, newPipe),
            ),
            onResolved = { id, s -> resolved = id to s },
        )

        val result = ladder.playableStreams(videoId)

        assertEquals(newPipeFailure, result)
        assertTrue(newPipe.wasInvoked)
        assertEquals(videoId to StreamSource.FALLBACK, resolved)
    }
}

private class FakeStreamProvider(private val result: StreamResult) : StreamProvider {
    var wasInvoked: Boolean = false
        private set

    override suspend fun playableStreams(videoId: VideoId): StreamResult {
        wasInvoked = true
        return result
    }
}
