package com.youtroc.data.extraction.catalog

import com.youtroc.core.domain.catalog.CatalogResult
import com.youtroc.core.domain.catalog.Shelf
import com.youtroc.core.domain.catalog.VideoCatalog
import com.youtroc.data.extraction.NewPipeBootstrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import kotlin.coroutines.cancellation.CancellationException

/**
 * Adapter: implements the domain [VideoCatalog] port over the YouTube Trending
 * kiosk (NewPipeExtractor's default kiosk).
 *
 * It translates NewPipe's world into the domain's ubiquitous language and maps
 * every failure onto a typed [CatalogResult] — nothing throws across the port
 * boundary. The NewPipe types stay inside this class; they never leak into the
 * domain — callers pass a plain [regionCode] string, never a NewPipe
 * [ContentCountry], so [org.schabi.newpipe] stays confined to this module.
 *
 * Localization is forced per-call via [localization]/[regionCode] on the
 * kiosk extractor instance — [NewPipeBootstrap] and its global
 * [Localization.DEFAULT] are never mutated, so playback extraction
 * ([com.youtroc.data.extraction.NewPipeStreamProvider]) is unaffected.
 */
class NewPipeVideoCatalog(
    private val bootstrap: () -> Unit = NewPipeBootstrap::ensureInitialized,
    private val localization: Localization = Localization("es"),
    private val regionCode: String? = null,
) : VideoCatalog {

    override suspend fun trending(): CatalogResult = withContext(Dispatchers.IO) {
        bootstrap()
        try {
            // YouTube's default kiosk IS "Trending" (no magic string needed).
            val extractor = ServiceList.YouTube.kioskList.getDefaultKioskExtractor(null, localization)
            regionCode?.takeIf { it.isNotBlank() }
                ?.let { extractor.forceContentCountry(ContentCountry(it)) }
            extractor.fetchPage()
            // KioskList is not generic; the kiosk item type is known statically for
            // YouTube's default kiosk (Trending), so a runtime filter (not a cast)
            // narrows to the type toVideoOrNull() maps.
            val videos = extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { it.toVideoOrNull() }
            if (videos.isEmpty()) {
                CatalogResult.Empty
            } else {
                CatalogResult.Success(listOf(Shelf(title = extractor.name, videos = videos)))
            }
        } catch (e: CancellationException) {
            throw e // never swallow cooperative cancellation
        } catch (e: Exception) {
            e.toCatalogResult()
        }
    }
}
