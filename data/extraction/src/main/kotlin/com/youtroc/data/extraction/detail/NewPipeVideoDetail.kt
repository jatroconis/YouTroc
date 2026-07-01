package com.youtroc.data.extraction.detail

import com.youtroc.core.domain.detail.DetailResult
import com.youtroc.core.domain.detail.VideoDetail
import com.youtroc.core.domain.video.VideoId
import com.youtroc.data.extraction.NewPipeBootstrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import kotlin.coroutines.cancellation.CancellationException

/**
 * Adapter: implements the domain [VideoDetail] port over the YouTube
 * `StreamExtractor`, running its OWN anonymous `StreamInfo.getInfo` (metadata
 * + related items resolved in one call — distinct from the playback path in
 * [com.youtroc.data.extraction.NewPipeStreamProvider], which only reads
 * stream formats).
 *
 * It translates NewPipe's world into the domain's ubiquitous language and maps
 * every failure onto a typed [DetailResult] — nothing throws across the port
 * boundary. The NewPipe types stay inside this class; they never leak into the
 * domain — callers pass a plain [regionCode] string, never a NewPipe
 * [ContentCountry], so [org.schabi.newpipe] stays confined to this module.
 *
 * Localization is forced per-call via [localization]/[regionCode] on the
 * stream extractor instance — [NewPipeBootstrap] and its global
 * [Localization.DEFAULT] are never mutated, so playback extraction
 * ([com.youtroc.data.extraction.NewPipeStreamProvider]) is unaffected.
 */
class NewPipeVideoDetail(
    private val bootstrap: () -> Unit = NewPipeBootstrap::ensureInitialized,
    private val localization: Localization = Localization("es"),
    private val regionCode: String? = null,
) : VideoDetail {

    override suspend fun detail(videoId: VideoId): DetailResult = withContext(Dispatchers.IO) {
        bootstrap()
        try {
            val info = fetchStreamInfo(videoId, localization, regionCode)
            DetailResult.Success(info.toVideoDetailInfo(videoId))
        } catch (e: CancellationException) {
            throw e // never swallow cooperative cancellation
        } catch (e: Exception) {
            e.toDetailResult()
        }
    }
}

/**
 * Fetches a single video's [StreamInfo] with [localization]/[regionCode]
 * forced on the extractor BEFORE `getInfo` — mirrors
 * [com.youtroc.data.extraction.search.buildSearchExtractor]'s force-then-fetch
 * policy. The 2-arg `StreamInfo.getInfo(service, url)` the player uses can't
 * force `hl=es` (it relies on the global default); the guessed 3-arg
 * `getInfo(service, url, Localization)` overload does NOT exist in NewPipe
 * v0.26.3 — `getInfo(StreamExtractor)` is the jar-verified path
 * (design-gate-review #4419).
 */
internal fun fetchStreamInfo(
    videoId: VideoId,
    localization: Localization,
    regionCode: String?,
): StreamInfo {
    val extractor = ServiceList.YouTube.getStreamExtractor(watchUrl(videoId))
    extractor.forceLocalization(localization)
    regionCode?.takeIf { it.isNotBlank() }?.let { extractor.forceContentCountry(ContentCountry(it)) }
    return StreamInfo.getInfo(extractor)
}

private fun watchUrl(videoId: VideoId): String =
    "https://www.youtube.com/watch?v=${videoId.value}"
