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
 * Deterministic, network-free verification of the own-first decorator
 * policy. R4 (BLOCKING): DELIBERATELY diverges from
 * [com.youtroc.data.extraction.detail.FallbackVideoDetail] (which passes
 * [StreamResult.NotAvailable] through as a trusted own answer) -- here, own's
 * [StreamResult.NotAvailable] is NOT authoritative: android_vr conflates
 * genuine unavailability with UNPLAYABLE false-negatives (A/B throttling)
 * and ALL live videos, both recoverable by NewPipe. Only [StreamResult.Success]
 * short-circuits; [StreamResult.Error]/[StreamResult.Offline]/
 * [StreamResult.NotAvailable] ALL fall back.
 *
 * [FakeStreamProvider] is defined here rather than reused from `core:domain`'s
 * test sources -- that module's test-scoped fake isn't visible cross-module.
 */
class FallbackStreamProviderTest {

    private val videoId = VideoId("dQw4w9WgXcQ")

    private val someStreams = PlayableStreams(
        streams = listOf(Stream(url = "https://cdn/a", container = "mp4", kind = StreamKind.MUXED)),
    )

    @Test
    fun `own success passes through and NewPipe is never invoked`() = runTest {
        val own = FakeStreamProvider(StreamResult.Success(someStreams))
        val newPipe = FakeStreamProvider(StreamResult.Error(IllegalStateException("should not be called")))
        val decorator = FallbackStreamProvider(own, newPipe)

        val result = decorator.playableStreams(videoId)

        assertEquals(StreamResult.Success(someStreams), result)
        assertFalse(newPipe.wasInvoked)
    }

    @Test
    fun `own NotAvailable falls back to and returns NewPipe's result (R4 BLOCKING divergence)`() = runTest {
        val own = FakeStreamProvider(StreamResult.NotAvailable)
        val newPipe = FakeStreamProvider(StreamResult.Success(someStreams))
        val decorator = FallbackStreamProvider(own, newPipe)

        val result = decorator.playableStreams(videoId)

        assertEquals(StreamResult.Success(someStreams), result)
        assertTrue(newPipe.wasInvoked)
    }

    @Test
    fun `own error falls back to and returns NewPipe's result`() = runTest {
        val own = FakeStreamProvider(StreamResult.Error(IllegalStateException("boom")))
        val newPipe = FakeStreamProvider(StreamResult.Success(someStreams))
        val decorator = FallbackStreamProvider(own, newPipe)

        val result = decorator.playableStreams(videoId)

        assertEquals(StreamResult.Success(someStreams), result)
        assertTrue(newPipe.wasInvoked)
    }

    @Test
    fun `own offline falls back to and returns NewPipe's result`() = runTest {
        val newPipeFailure = StreamResult.Error(IllegalStateException("still down"))
        val own = FakeStreamProvider(StreamResult.Offline)
        val newPipe = FakeStreamProvider(newPipeFailure)
        val decorator = FallbackStreamProvider(own, newPipe)

        val result = decorator.playableStreams(videoId)

        assertEquals(newPipeFailure, result)
        assertTrue(newPipe.wasInvoked)
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
