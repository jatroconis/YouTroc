package com.youtroc.data.extraction.stream

import com.youtroc.core.domain.stream.StreamProvider
import com.youtroc.core.domain.stream.StreamResult
import com.youtroc.core.domain.video.VideoId

/**
 * One ordered rung in a [LadderStreamProvider]: [source] is the
 * [StreamSource] reported via `onResolved` when THIS rung's [provider] wins
 * with a [StreamResult.Success]. [provider] stays behind the domain
 * [StreamProvider] port, so this type has no knowledge of InnerTube/NewPipe
 * specifics.
 */
data class StreamRung(val source: StreamSource, val provider: StreamProvider)

/**
 * Decorator: tries each [StreamRung] in [rungs], IN ORDER, and returns the
 * result of the FIRST rung that produces [StreamResult.Success], WITHOUT
 * invoking any lower rung. On [StreamResult.Error], [StreamResult.Offline],
 * OR [StreamResult.NotAvailable], the ladder advances to the next rung; the
 * TERMINAL rung's result is always returned RAW (unmodified), even when it
 * did not succeed -- there is nothing left to fall back to.
 *
 * R4 (BLOCKING): this is a DELIBERATE divergence from
 * [com.youtroc.data.extraction.detail.FallbackVideoDetail]/
 * [com.youtroc.data.extraction.search.FallbackVideoSearch] (which pass
 * `NotAvailable`/`Empty` through as a trusted own answer). A rung's
 * [StreamResult.NotAvailable] here is NOT authoritative -- android_vr's VOD
 * scope conflates genuine unavailability with UNPLAYABLE false-negatives
 * (A/B throttling) and ALL live videos (D4: android_vr never attempts a live
 * manifest), both potentially recoverable by a lower rung. Own-engine rungs
 * (android_vr, ios) are NEVER raced against each other -- ios is reachable
 * ONLY once android_vr did not succeed (owner decision, design D1).
 *
 * Every rung stays behind the same [StreamProvider] port, so this class has
 * no knowledge of InnerTube/NewPipe specifics -- pure composition,
 * unit-testable with fakes, no network.
 *
 * [onResolved] is a defaulted, backward-compatible signal fired with the
 * [StreamSource] of the rung that actually served the result. Speculative
 * prefetch (`PrefetchingStreamProvider`, `:app`-only) uses it to gate itself
 * on the current video's PRIMARY (android_vr) engine being healthy --
 * [StreamSource.IOS]/[StreamSource.FALLBACK] both mean android_vr is
 * presumed throttled, so prefetch SKIPS; existing callers that don't pass it
 * are unaffected.
 */
class LadderStreamProvider(
    private val rungs: List<StreamRung>,
    private val onResolved: (VideoId, StreamSource) -> Unit = { _, _ -> },
) : StreamProvider {

    init {
        require(rungs.isNotEmpty()) { "LadderStreamProvider requires at least one rung" }
    }

    override suspend fun playableStreams(videoId: VideoId): StreamResult {
        rungs.forEachIndexed { index, rung ->
            val result = rung.provider.playableStreams(videoId)
            if (result is StreamResult.Success || index == rungs.lastIndex) {
                onResolved(videoId, rung.source)
                return result
            }
        }
        error("unreachable: a non-empty ladder always returns from within the loop")
    }
}
