package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.catalog.CatalogResult
import com.youtroc.core.domain.catalog.Shelf
import com.youtroc.core.domain.catalog.ShelfId
import com.youtroc.core.domain.catalog.ShelfSource
import com.youtroc.data.extraction.catalog.toCatalogResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import kotlin.coroutines.cancellation.CancellationException

/**
 * Adapter: a generic per-topic Home shelf source over `youtubei/v1/search`,
 * reusing [InnerTubeVideoSearch]'s request-building/parsing plumbing. One
 * instance per thematic shelf (Música, Videojuegos, Noticias, Deportes,
 * Cine) -- [query]/[displayTitle]/[id] are the only per-instance knobs,
 * wired by [homeShelfSources].
 */
class SearchQueryShelfSource(
    override val id: ShelfId,
    override val displayTitle: String,
    private val query: String,
    private val client: OkHttpClient = InnerTubeHttp.client,
    private val regionCode: String? = null,
    override val timeoutMs: Long = 1_500L,
) : ShelfSource {

    override suspend fun load(): CatalogResult = withContext(Dispatchers.IO) {
        try {
            val request = buildSearchHttpRequest(query = query, regionCode = regionCode, params = null)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("InnerTube search for \"$query\" returned HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val parsed = innerTubeSearchJson.decodeFromString<SearchResponse>(body)
                val videos = parsed.videoRenderers().mapNotNull { it.toVideoOrNull() }
                if (videos.isEmpty()) {
                    CatalogResult.Empty
                } else {
                    CatalogResult.Success(listOf(Shelf(id = id, title = displayTitle, videos = videos)))
                }
            }
        } catch (e: CancellationException) {
            throw e // never swallow cooperative cancellation
        } catch (e: Exception) {
            e.toCatalogResult()
        }
    }
}
