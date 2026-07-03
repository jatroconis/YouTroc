package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.catalog.CatalogResult
import com.youtroc.core.domain.catalog.Shelf
import com.youtroc.core.domain.catalog.VideoCatalog
import com.youtroc.data.extraction.catalog.toCatalogResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import kotlin.coroutines.cancellation.CancellationException

/** Seed order, ADR-1 (gate-corrected): lead with `"tendencias"`, fall back to `"noticias"`. */
private val SEED_QUERIES = listOf("tendencias", "noticias")

/**
 * Adapter: own-engine implementation of the domain [VideoCatalog] port over
 * YouTube's internal `youtubei/v1/search` endpoint, reused as a
 * region-anchoring seed-query search (no dedicated "trending" endpoint
 * exists on this API) -- no API key, no `visitorData`, no PoToken. Second
 * strangler-fig slice of the own InnerTube engine (after
 * [InnerTubeVideoSearch]); selected ahead of
 * [com.youtroc.data.extraction.catalog.NewPipeVideoCatalog] by
 * [com.youtroc.data.extraction.catalog.FallbackVideoCatalog].
 *
 * ADR-5 layer (a) -- owns seed rotation + title-prefix shelf detection +
 * degradation WITHIN the own engine:
 * 1. For each seed in [SEED_QUERIES]: fetch + parse; [SearchResponse.regionalShelf]
 *    (title-prefix `"Popular en "` + mappable items, ADR-2). If non-null,
 *    return [CatalogResult.Success] with that single [Shelf] and STOP.
 * 2. Same response: a `"Popular en "` shelf exists but mapped to zero videos,
 *    yet flat [SearchResponse.videoRenderers] is non-empty -- return
 *    [CatalogResult.Success] with a [Shelf] labeled by that shelf's verbatim
 *    title, backed by the flat results (LABELED FLAT; only fires when a
 *    `"Popular en "` title exists, so no invented copy is ever needed).
 * 3. No `"Popular en "` shelf found after every seed -- [CatalogResult.Empty].
 *
 * OkHttp/kotlinx.serialization/InnerTube DTO types never cross this class --
 * callers only ever see [CatalogResult] / [com.youtroc.core.domain.catalog.Video].
 * Mirrors [InnerTubeVideoSearch]'s shape: `withContext(Dispatchers.IO)`,
 * cancellation rethrown before the generic catch, everything else mapped via
 * [toCatalogResult].
 */
class InnerTubeVideoCatalog(
    private val client: OkHttpClient = InnerTubeHttp.client,
    private val regionCode: String? = null,
) : VideoCatalog {

    override suspend fun trending(): CatalogResult = withContext(Dispatchers.IO) {
        try {
            for (seed in SEED_QUERIES) {
                val request = buildSearchHttpRequest(seed, regionCode)
                val parsed = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("InnerTube catalog search returned HTTP ${response.code}")
                    }
                    val body = response.body?.string().orEmpty()
                    innerTubeSearchJson.decodeFromString<SearchResponse>(body)
                }

                val shelf = parsed.regionalShelf()
                if (shelf != null) {
                    return@withContext CatalogResult.Success(listOf(shelf))
                }

                val labeledTitle = parsed.popularEnShelves().firstOrNull()?.first
                if (labeledTitle != null) {
                    val flatVideos = parsed.videoRenderers().mapNotNull { it.toVideoOrNull() }
                    if (flatVideos.isNotEmpty()) {
                        return@withContext CatalogResult.Success(listOf(Shelf(title = labeledTitle, videos = flatVideos)))
                    }
                }
            }
            CatalogResult.Empty
        } catch (e: CancellationException) {
            throw e // never swallow cooperative cancellation
        } catch (e: Exception) {
            e.toCatalogResult()
        }
    }
}
