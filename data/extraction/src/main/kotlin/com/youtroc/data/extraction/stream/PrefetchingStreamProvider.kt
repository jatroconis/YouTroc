package com.youtroc.data.extraction.stream

import com.youtroc.core.domain.stream.PlayableStreams
import com.youtroc.core.domain.stream.StreamProvider
import com.youtroc.core.domain.stream.StreamResult
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

/**
 * Caching decorator over [delegate] (a [StreamProvider], concretely the
 * composed [FallbackStreamProvider]) that speculatively prefetches the next
 * up-next video's streams while the current one plays. The read path
 * ([playableStreams]) is cache-transparent -- [com.youtroc.core.domain.playback.GetPlayableStreams]
 * and every other domain consumer see zero difference. [prefetch]/
 * [invalidate]/[lastSourceFor]/[recordSource] are concrete, `:app`-only
 * members -- NOT part of the [StreamProvider] port (`:core:domain` stays
 * pure; design D3).
 *
 * Concurrency (design D4, gate-confirmed sound): a single [mutex] guards
 * [entry]/[inFlight]/[inFlightId] so a cache check-then-store is always
 * atomic; at most one prefetch [Job] runs at a time (a new target cancels
 * the prior one, same-id dedupes, a fresh cache hit no-ops); [lastResolved]
 * is a plain `@Volatile` ref since it is a single atomic write from a
 * non-suspend callback ([recordSource]), no [Mutex] needed. [now] is
 * injected so staleness is deterministic in tests and `:core:domain` never
 * needs a time source.
 */
class PrefetchingStreamProvider(
    private val delegate: StreamProvider,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val ttlMillis: Long = 18_000_000L, // 5h safety margin under googlevideo's ~6h URL window
) : StreamProvider {

    private data class Entry(val videoId: VideoId, val streams: PlayableStreams, val storedAt: Long)

    private val mutex = Mutex()
    private var entry: Entry? = null
    private var inFlight: Job? = null
    private var inFlightId: VideoId? = null

    @Volatile
    private var lastResolved: Pair<VideoId, StreamSource>? = null

    private fun isFresh(e: Entry) = now() - e.storedAt < ttlMillis

    override suspend fun playableStreams(videoId: VideoId): StreamResult {
        val hit = mutex.withLock { entry?.takeIf { it.videoId == videoId && isFresh(it) } }
        return if (hit != null) StreamResult.Success(hit.streams) else delegate.playableStreams(videoId)
    }

    /** `:app`-only trigger. Fires at most one in-flight resolution; a new target cancels the prior one. */
    fun prefetch(videoId: VideoId) = scope.launch {
        val job = coroutineContext.job
        val start = mutex.withLock {
            when {
                inFlightId == videoId -> false // dedupe same id
                entry?.let { it.videoId == videoId && isFresh(it) } == true -> false // fresh hit -> skip
                else -> {
                    inFlight?.cancel()
                    inFlight = job
                    inFlightId = videoId
                    true
                }
            }
        }
        if (!start) return@launch

        val result = try {
            delegate.playableStreams(videoId)
        } catch (c: CancellationException) {
            throw c // never swallow cancellation
        } catch (t: Throwable) {
            null // silent: discard errors, never surfaced
        }

        ensureActive() // a cancelled/superseded job performs NO write past this point

        mutex.withLock {
            if (inFlightId == videoId) { // still the current target
                if (result is StreamResult.Success) {
                    entry = Entry(videoId, result.streams, now())
                }
                inFlight = null
                inFlightId = null
            }
        }
    }

    /** `:app`-only. Cancels a wasted in-flight prefetch on nav-away/BACK; deliberately KEEPS [entry]. */
    fun invalidate() = scope.launch {
        mutex.withLock {
            inFlight?.cancel()
            inFlight = null
            inFlightId = null
        }
    }

    /** Called by the delegate's `onResolved` callback -- records which engine served [videoId]. */
    fun recordSource(videoId: VideoId, source: StreamSource) {
        lastResolved = videoId to source
    }

    /** `:app`-only. `null` unless the LAST resolve was for this exact [videoId] (id-guarded). */
    fun lastSourceFor(videoId: VideoId): StreamSource? =
        lastResolved?.takeIf { it.first == videoId }?.second
}
