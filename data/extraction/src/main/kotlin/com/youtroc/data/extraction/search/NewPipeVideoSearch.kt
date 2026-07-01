package com.youtroc.data.extraction.search

import com.youtroc.core.domain.search.SearchResult
import com.youtroc.core.domain.search.VideoSearch
import com.youtroc.data.extraction.NewPipeBootstrap
import com.youtroc.data.extraction.catalog.toVideoOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import kotlin.coroutines.cancellation.CancellationException

/**
 * Adapter: implements the domain [VideoSearch] port over the YouTube
 * `SearchExtractor`, restricted to the VIDEOS content filter (ADR-9.1).
 *
 * It translates NewPipe's world into the domain's ubiquitous language and maps
 * every failure onto a typed [SearchResult] — nothing throws across the port
 * boundary. The NewPipe types stay inside this class; they never leak into the
 * domain — callers pass a plain [regionCode] string, never a NewPipe
 * [ContentCountry], so [org.schabi.newpipe] stays confined to this module.
 *
 * Localization is forced per-call via [localization]/[regionCode] on the
 * search extractor instance — [NewPipeBootstrap] and its global
 * [Localization.DEFAULT] are never mutated, so playback extraction
 * ([com.youtroc.data.extraction.NewPipeStreamProvider]) is unaffected.
 */
class NewPipeVideoSearch(
    private val bootstrap: () -> Unit = NewPipeBootstrap::ensureInitialized,
    private val localization: Localization = Localization("es"),
    private val regionCode: String? = null,
) : VideoSearch {

    override suspend fun search(query: String): SearchResult = withContext(Dispatchers.IO) {
        // Defense-in-depth: the primary blank guard lives in SearchViewModel
        // (WU-2). This short-circuits before touching bootstrap or network.
        if (query.isBlank()) return@withContext SearchResult.Empty
        bootstrap()
        try {
            val extractor = buildSearchExtractor(query, localization, regionCode)
            extractor.fetchPage()
            // A SearchExtractor's items are mixed InfoItems (channels/playlists
            // can appear even with the VIDEOS filter); narrow to videos only.
            val videos = extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { it.toVideoOrNull() }
            if (videos.isEmpty()) SearchResult.Empty else SearchResult.Success(videos)
        } catch (e: CancellationException) {
            throw e // never swallow cooperative cancellation
        } catch (e: Exception) {
            e.toSearchResult()
        }
    }
}

/**
 * Builds a YouTube [SearchExtractor] scoped to the VIDEOS content filter, with
 * [localization]/[regionCode] applied — but does NOT call `fetchPage()`, so
 * this is pure construction with no network I/O. Kept as a top-level function
 * so the localization-forcing policy is directly unit-testable (the extractor
 * exposes pre-fetch getters for both) without gating on the opt-in live test.
 */
internal fun buildSearchExtractor(
    query: String,
    localization: Localization,
    regionCode: String?,
): SearchExtractor {
    val queryHandler = ServiceList.YouTube.searchQHFactory
        .fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
    val extractor = ServiceList.YouTube.getSearchExtractor(queryHandler)
    extractor.forceLocalization(localization)
    regionCode?.takeIf { it.isNotBlank() }?.let { extractor.forceContentCountry(ContentCountry(it)) }
    return extractor
}
