package com.youtroc.data.extraction.stream

import com.youtroc.core.domain.stream.StreamProvider
import com.youtroc.core.domain.stream.StreamResult
import com.youtroc.core.domain.video.VideoId

/**
 * Decorator: tries [primary] (own [com.youtroc.data.extraction.innertube.InnerTubeStreamProvider])
 * first and returns its result unchanged on [StreamResult.Success] ONLY --
 * own ABR wins whenever it produced one. On [StreamResult.Error],
 * [StreamResult.Offline], OR [StreamResult.NotAvailable] from [primary],
 * delegates to [fallback] (NewPipe) and returns ITS result unmodified.
 *
 * R4 (BLOCKING): this is a DELIBERATE divergence from
 * [com.youtroc.data.extraction.detail.FallbackVideoDetail]/
 * [com.youtroc.data.extraction.search.FallbackVideoSearch] (which pass
 * `NotAvailable`/`Empty` through as a trusted own answer). Own's
 * [StreamResult.NotAvailable] here is NOT authoritative -- android_vr's VOD
 * scope conflates genuine unavailability with UNPLAYABLE false-negatives
 * (A/B throttling) and ALL live videos (D4: own never attempts a live
 * manifest), both recoverable by NewPipe. The net UI verdict is always
 * NewPipe's considered one whenever own didn't succeed outright.
 *
 * Both [primary]/[fallback] stay behind the same [StreamProvider] port, so
 * this class has no knowledge of InnerTube/NewPipe specifics -- pure
 * composition, unit-testable with fakes, no network.
 *
 * [onResolved] is a defaulted, backward-compatible signal fired with the
 * [StreamSource] that actually served the result -- [StreamSource.OWN] on a
 * primary [StreamResult.Success], [StreamSource.FALLBACK] whenever primary
 * didn't succeed outright. Speculative prefetch (`PrefetchingStreamProvider`,
 * `:app`-only) uses it to gate itself on the current video's own engine being
 * healthy; existing callers that don't pass it are unaffected.
 */
class FallbackStreamProvider(
    private val primary: StreamProvider,
    private val fallback: StreamProvider,
    private val onResolved: (VideoId, StreamSource) -> Unit = { _, _ -> },
) : StreamProvider {

    override suspend fun playableStreams(videoId: VideoId): StreamResult =
        when (val result = primary.playableStreams(videoId)) {
            is StreamResult.Success -> {
                onResolved(videoId, StreamSource.OWN)
                result
            }
            StreamResult.NotAvailable, StreamResult.Offline, is StreamResult.Error -> {
                onResolved(videoId, StreamSource.FALLBACK)
                fallback.playableStreams(videoId)
            }
        }
}
