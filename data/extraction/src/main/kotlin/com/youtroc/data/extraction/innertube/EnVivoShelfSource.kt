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

/** YouTube's protobuf-encoded search filter param for the "Live" content type (spike #4603 Q1). */
private const val LIVE_FILTER_PARAMS = "EgJAAQ=="

/**
 * Adapter: the Home shelf source for currently-live content, reusing the
 * same `youtubei/v1/search` plumbing as [InnerTubeVideoSearch] with
 * YouTube's "Live" content-type filter ([LIVE_FILTER_PARAMS]). The filter
 * alone still returns some non-live matches for a plain "en vivo" query, so
 * results are ADDITIONALLY guarded by [VideoRenderer.hasLiveBadge] (M3) --
 * only videos carrying the "LIVE NOW" badge make the shelf.
 */
class EnVivoShelfSource(
    private val client: OkHttpClient = InnerTubeHttp.client,
    private val regionCode: String? = null,
    override val timeoutMs: Long = 1_500L,
) : ShelfSource {

    override val id: ShelfId = ShelfId.EN_VIVO
    override val displayTitle: String = "En vivo"

    override suspend fun load(): CatalogResult = withContext(Dispatchers.IO) {
        try {
            val request = buildSearchHttpRequest(query = "en vivo", regionCode = regionCode, params = LIVE_FILTER_PARAMS)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("InnerTube en-vivo search returned HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val parsed = innerTubeSearchJson.decodeFromString<SearchResponse>(body)
                val videos = parsed.videoRenderers()
                    .filter { it.hasLiveBadge() }
                    .mapNotNull { it.toVideoOrNull() }
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
