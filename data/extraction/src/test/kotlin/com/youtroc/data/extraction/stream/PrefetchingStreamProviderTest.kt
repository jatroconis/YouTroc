package com.youtroc.data.extraction.stream

import com.youtroc.core.domain.stream.PlayableStreams
import com.youtroc.core.domain.stream.Stream
import com.youtroc.core.domain.stream.StreamKind
import com.youtroc.core.domain.stream.StreamProvider
import com.youtroc.core.domain.stream.StreamResult
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Deterministic, network-free verification of [PrefetchingStreamProvider]'s
 * concurrency core (Mutex-guarded 1-entry cache, ≤1 in-flight job,
 * cancel-on-change, Success-only caching, id-guarded [PrefetchingStreamProvider.lastSourceFor]).
 * Mirrors [LadderStreamProviderTest]'s fakes-only, `runTest`/`TestScope`
 * style, plus an injected [PrefetchingStreamProvider] `now` clock so
 * staleness is deterministic (no real time dependency; `:core:domain` stays
 * pure per D4 -- `now` lives here in `:data:extraction`).
 *
 * [FakeStreamProvider] is a NESTED private class (not top-level) so its JVM
 * class name doesn't collide with [LadderStreamProviderTest]'s own
 * file-private `FakeStreamProvider` in the same package.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrefetchingStreamProviderTest {

    private val videoIdA = VideoId("videoIdA")
    private val videoIdB = VideoId("videoIdB")

    private val streamsA = PlayableStreams(
        streams = listOf(Stream(url = "https://cdn/a", container = "mp4", kind = StreamKind.MUXED)),
    )
    private val streamsB = PlayableStreams(
        streams = listOf(Stream(url = "https://cdn/b", container = "mp4", kind = StreamKind.MUXED)),
    )

    /**
     * Records every invocation. By default each call SUSPENDS on a
     * per-call [CompletableDeferred] (held open until the test explicitly
     * [complete]s it) -- needed to observe in-flight/cancellation behavior.
     * When [immediateResult] is set, calls resolve to it immediately instead
     * (simpler for cache/staleness/discard scenarios that don't need to hold
     * a call open).
     */
    private class FakeStreamProvider : StreamProvider {
        var callCount: Int = 0
            private set

        val calls = mutableListOf<VideoId>()

        var immediateResult: StreamResult? = null

        private val deferredByCall = mutableListOf<CompletableDeferred<StreamResult>>()

        override suspend fun playableStreams(videoId: VideoId): StreamResult {
            callCount++
            calls.add(videoId)
            immediateResult?.let { return it }
            val deferred = CompletableDeferred<StreamResult>()
            deferredByCall.add(deferred)
            return deferred.await()
        }

        fun complete(callIndex: Int, result: StreamResult) {
            deferredByCall[callIndex].complete(result)
        }

        fun completeExceptionally(callIndex: Int, throwable: Throwable) {
            deferredByCall[callIndex].completeExceptionally(throwable)
        }
    }

    @Test
    fun `cache hit returns the cached Success without a new delegate call`() = runTest {
        val fake = FakeStreamProvider()
        val provider = PrefetchingStreamProvider(delegate = fake, scope = this, now = { 0L })

        provider.prefetch(videoIdA)
        runCurrent()
        fake.complete(0, StreamResult.Success(streamsA))
        advanceUntilIdle()
        assertEquals(1, fake.callCount)

        val result = provider.playableStreams(videoIdA)

        assertEquals(StreamResult.Success(streamsA), result)
        assertEquals(1, fake.callCount)
    }

    @Test
    fun `cache miss falls through to the delegate`() = runTest {
        val fake = FakeStreamProvider().apply { immediateResult = StreamResult.Success(streamsA) }
        val provider = PrefetchingStreamProvider(delegate = fake, scope = this, now = { 0L })

        val result = provider.playableStreams(videoIdA)

        assertEquals(StreamResult.Success(streamsA), result)
        assertEquals(1, fake.callCount)
    }

    @Test
    fun `a stale entry past the ttl is treated as a miss and falls through`() = runTest {
        val fake = FakeStreamProvider().apply { immediateResult = StreamResult.Success(streamsA) }
        var now = 0L
        val provider = PrefetchingStreamProvider(delegate = fake, scope = this, now = { now }, ttlMillis = 1_000L)

        provider.prefetch(videoIdA)
        advanceUntilIdle()
        assertEquals(1, fake.callCount)

        now = 2_000L
        val result = provider.playableStreams(videoIdA)

        assertEquals(StreamResult.Success(streamsA), result)
        assertEquals(2, fake.callCount)
    }

    @Test
    fun `an entry cached for a different id is a miss for the requested id`() = runTest {
        val fake = FakeStreamProvider().apply { immediateResult = StreamResult.Success(streamsA) }
        val provider = PrefetchingStreamProvider(delegate = fake, scope = this, now = { 0L })

        provider.prefetch(videoIdA)
        advanceUntilIdle()
        assertEquals(1, fake.callCount)

        fake.immediateResult = StreamResult.Success(streamsB)
        val result = provider.playableStreams(videoIdB)

        assertEquals(StreamResult.Success(streamsB), result)
        assertEquals(2, fake.callCount)
    }

    @Test
    fun `prefetch caches only a Success result`() = runTest {
        val fake = FakeStreamProvider().apply { immediateResult = StreamResult.Success(streamsA) }
        val provider = PrefetchingStreamProvider(delegate = fake, scope = this, now = { 0L })

        provider.prefetch(videoIdA)
        advanceUntilIdle()
        assertEquals(1, fake.callCount)

        fake.immediateResult = StreamResult.Error(IllegalStateException("must not be called -- A should be a cache hit"))
        val result = provider.playableStreams(videoIdA)

        assertEquals(StreamResult.Success(streamsA), result)
        assertEquals(1, fake.callCount)
    }

    @Test
    fun `a NotAvailable prefetch result is discarded, never cached, never surfaced`() = runTest {
        val fake = FakeStreamProvider().apply { immediateResult = StreamResult.NotAvailable }
        val provider = PrefetchingStreamProvider(delegate = fake, scope = this, now = { 0L })

        provider.prefetch(videoIdA)
        advanceUntilIdle()

        fake.immediateResult = StreamResult.Success(streamsA)
        val result = provider.playableStreams(videoIdA)

        assertEquals(StreamResult.Success(streamsA), result)
        assertEquals(2, fake.callCount)
    }

    @Test
    fun `an Offline prefetch result is discarded, never cached, never surfaced`() = runTest {
        val fake = FakeStreamProvider().apply { immediateResult = StreamResult.Offline }
        val provider = PrefetchingStreamProvider(delegate = fake, scope = this, now = { 0L })

        provider.prefetch(videoIdA)
        advanceUntilIdle()

        fake.immediateResult = StreamResult.Success(streamsA)
        val result = provider.playableStreams(videoIdA)

        assertEquals(StreamResult.Success(streamsA), result)
        assertEquals(2, fake.callCount)
    }

    @Test
    fun `an Error prefetch result is discarded, never cached, never surfaced`() = runTest {
        val fake = FakeStreamProvider().apply { immediateResult = StreamResult.Error(IllegalStateException("boom")) }
        val provider = PrefetchingStreamProvider(delegate = fake, scope = this, now = { 0L })

        provider.prefetch(videoIdA)
        advanceUntilIdle()

        fake.immediateResult = StreamResult.Success(streamsA)
        val result = provider.playableStreams(videoIdA)

        assertEquals(StreamResult.Success(streamsA), result)
        assertEquals(2, fake.callCount)
    }

    @Test
    fun `a thrown exception from the delegate during prefetch is discarded and never surfaced`() = runTest {
        val fake = FakeStreamProvider()
        val provider = PrefetchingStreamProvider(delegate = fake, scope = this, now = { 0L })

        provider.prefetch(videoIdA)
        runCurrent()
        fake.completeExceptionally(0, IllegalStateException("network blew up"))
        advanceUntilIdle()

        fake.immediateResult = StreamResult.Success(streamsA)
        val result = provider.playableStreams(videoIdA)

        assertEquals(StreamResult.Success(streamsA), result)
        assertEquals(2, fake.callCount)
    }

    @Test
    fun `at most one prefetch is in flight -- a new target cancels the prior job`() = runTest {
        val fake = FakeStreamProvider()
        val provider = PrefetchingStreamProvider(delegate = fake, scope = this, now = { 0L })

        provider.prefetch(videoIdA)
        runCurrent()
        assertEquals(1, fake.callCount)

        provider.prefetch(videoIdB)
        runCurrent()
        assertEquals(2, fake.callCount)

        fake.complete(1, StreamResult.Success(streamsB))
        advanceUntilIdle()

        fake.immediateResult = StreamResult.Error(IllegalStateException("must not be called -- B should be a cache hit"))
        val result = provider.playableStreams(videoIdB)

        assertEquals(StreamResult.Success(streamsB), result)
    }

    @Test
    fun `a second prefetch for the same id dedupes -- the delegate is invoked once`() = runTest {
        val fake = FakeStreamProvider()
        val provider = PrefetchingStreamProvider(delegate = fake, scope = this, now = { 0L })

        provider.prefetch(videoIdA)
        runCurrent()
        assertEquals(1, fake.callCount)

        provider.prefetch(videoIdA)
        runCurrent()
        assertEquals(1, fake.callCount)

        fake.complete(0, StreamResult.Success(streamsA))
        advanceUntilIdle()

        fake.immediateResult = StreamResult.Error(IllegalStateException("must not be called -- A should be a cache hit"))
        val result = provider.playableStreams(videoIdA)

        assertEquals(StreamResult.Success(streamsA), result)
    }

    @Test
    fun `a cancelled, superseded prefetch job performs no cache write`() = runTest {
        val fake = FakeStreamProvider()
        val provider = PrefetchingStreamProvider(delegate = fake, scope = this, now = { 0L })

        provider.prefetch(videoIdA)
        runCurrent()
        assertEquals(1, fake.callCount)

        provider.prefetch(videoIdB)
        runCurrent()
        fake.complete(1, StreamResult.Success(streamsB))
        advanceUntilIdle()

        fake.immediateResult = StreamResult.Success(streamsA)
        val callsBefore = fake.callCount
        val result = provider.playableStreams(videoIdA)

        assertEquals(StreamResult.Success(streamsA), result)
        assertEquals(callsBefore + 1, fake.callCount)
    }

    @Test
    fun `invalidate cancels an in-flight prefetch and keeps the existing cached entry`() = runTest {
        val fake = FakeStreamProvider()
        val provider = PrefetchingStreamProvider(delegate = fake, scope = this, now = { 0L })

        provider.prefetch(videoIdA)
        runCurrent()
        fake.complete(0, StreamResult.Success(streamsA))
        advanceUntilIdle()

        provider.prefetch(videoIdB)
        runCurrent()
        assertEquals(2, fake.callCount)

        provider.invalidate()
        advanceUntilIdle()

        fake.immediateResult = StreamResult.Error(IllegalStateException("must not be called -- A should still be cached"))
        val result = provider.playableStreams(videoIdA)

        assertEquals(StreamResult.Success(streamsA), result)
        assertEquals(2, fake.callCount)
    }

    @Test
    fun `prefetch no-ops when a fresh entry already exists for that id`() = runTest {
        val fake = FakeStreamProvider()
        val provider = PrefetchingStreamProvider(delegate = fake, scope = this, now = { 0L })

        provider.prefetch(videoIdA)
        runCurrent()
        fake.complete(0, StreamResult.Success(streamsA))
        advanceUntilIdle()
        assertEquals(1, fake.callCount)

        provider.prefetch(videoIdA)
        advanceUntilIdle()

        assertEquals(1, fake.callCount)
    }

    @Test
    fun `lastSourceFor is null when the last recorded source is for a different id`() = runTest {
        val fake = FakeStreamProvider()
        val provider = PrefetchingStreamProvider(delegate = fake, scope = this, now = { 0L })

        provider.recordSource(videoIdA, StreamSource.ANDROID_VR)

        assertNull(provider.lastSourceFor(videoIdB))
    }

    @Test
    fun `lastSourceFor is null before any source has been recorded`() = runTest {
        val fake = FakeStreamProvider()
        val provider = PrefetchingStreamProvider(delegate = fake, scope = this, now = { 0L })

        assertNull(provider.lastSourceFor(videoIdA))
    }

    @Test
    fun `lastSourceFor returns the recorded source when the id matches`() = runTest {
        val fake = FakeStreamProvider()
        val provider = PrefetchingStreamProvider(delegate = fake, scope = this, now = { 0L })

        provider.recordSource(videoIdA, StreamSource.ANDROID_VR)

        assertEquals(StreamSource.ANDROID_VR, provider.lastSourceFor(videoIdA))
    }

    @Test
    fun `lastSourceFor returns IOS when IOS was the recorded source (3-valued signal)`() = runTest {
        val fake = FakeStreamProvider()
        val provider = PrefetchingStreamProvider(delegate = fake, scope = this, now = { 0L })

        provider.recordSource(videoIdA, StreamSource.IOS)

        assertEquals(StreamSource.IOS, provider.lastSourceFor(videoIdA))
    }
}
