package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.search.SearchResult
import com.youtroc.core.domain.search.VideoSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.cancellation.CancellationException

/** Promoted to `internal` (was `private`) so [InnerTubeVideoCatalog] reuses it verbatim -- ADR-6. */
internal const val INNERTUBE_SEARCH_URL = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"

/** Promoted to `internal` (was `private`) so [InnerTubeVideoCatalog] reuses it verbatim -- ADR-6. */
internal val innerTubeSearchJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

/**
 * Shared [OkHttpClient] for [InnerTubeVideoSearch] instances: a single
 * instance reuses the connection pool (HTTP/2 + keep-alive) instead of
 * paying a fresh TLS handshake per search (S5) -- injected as the
 * constructor default, not built per call.
 */
internal object InnerTubeHttp {
    val client: OkHttpClient = OkHttpClient()
}

/**
 * Adapter: own-engine implementation of the domain [VideoSearch] port over
 * YouTube's internal `youtubei/v1/search` endpoint -- no API key, no
 * `visitorData`, no PoToken. First strangler-fig slice of the own InnerTube
 * engine; selected ahead of
 * [com.youtroc.data.extraction.search.NewPipeVideoSearch] by
 * [com.youtroc.data.extraction.search.FallbackVideoSearch].
 *
 * OkHttp/kotlinx.serialization/InnerTube DTO types never cross this class --
 * callers only ever see [SearchResult] / [com.youtroc.core.domain.catalog.Video].
 * Mirrors [com.youtroc.data.extraction.search.NewPipeVideoSearch]'s shape:
 * blank-query guard, `withContext(Dispatchers.IO)`, cancellation rethrown
 * before the generic catch, everything else mapped via [toSearchResult].
 */
class InnerTubeVideoSearch(
    private val client: OkHttpClient = InnerTubeHttp.client,
    private val regionCode: String? = null,
) : VideoSearch {

    override suspend fun search(query: String): SearchResult = withContext(Dispatchers.IO) {
        // Defense-in-depth: the primary blank guard lives in SearchViewModel
        // (WU-2). This short-circuits before touching the network.
        if (query.isBlank()) return@withContext SearchResult.Empty
        try {
            val request = buildSearchHttpRequest(query, regionCode)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("InnerTube search returned HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val parsed = innerTubeSearchJson.decodeFromString<SearchResponse>(body)
                val videos = parsed.videoRenderers().mapNotNull { it.toVideoOrNull() }
                if (videos.isEmpty()) SearchResult.Empty else SearchResult.Success(videos)
            }
        } catch (e: CancellationException) {
            throw e // never swallow cooperative cancellation
        } catch (e: Exception) {
            e.toSearchResult()
        }
    }
}

/**
 * Builds the InnerTube search POST request from [buildSearchRequest]'s pure
 * DTO -- the only I/O-touching step is `OkHttpClient.newCall(...).execute()`
 * in [InnerTubeVideoSearch.search], never this function. Promoted to
 * `internal` (was `private fun buildRequest`) so [InnerTubeVideoCatalog]
 * reuses it verbatim -- ADR-6. No new headers: only `Content-Type` is set,
 * matching the design-gate-confirmed real request shape. [params] is
 * additive/nullable-defaulted (spike #4603 Q1) so every existing caller
 * keeps building a plain query request untouched.
 */
internal fun buildSearchHttpRequest(query: String, regionCode: String?, params: String? = null): Request {
    val payload = buildSearchRequest(query, regionCode, params)
    val body = innerTubeSearchJson.encodeToString(payload).toRequestBody("application/json".toMediaType())
    return Request.Builder()
        .url(INNERTUBE_SEARCH_URL)
        .post(body)
        .build()
}

/**
 * Builds the InnerTube search request body -- pure construction, no I/O, so
 * the shaping policy (`hl=es`, `gl` from [regionCode], `params`) is directly
 * unit-testable. R2: `gl` mirrors
 * [com.youtroc.data.extraction.search.buildSearchExtractor]'s blank-region
 * convention -- blank/empty regions omit `gl` rather than sending `""`
 * (`explicitNulls = false` alone would NOT omit an empty string, only a
 * null one). [params] (spike #4603 Q1, e.g. [EnVivoShelfSource]'s Live
 * content-type filter) is additive/nullable-defaulted: passing `null` keeps
 * every existing caller's plain query request unchanged.
 */
internal fun buildSearchRequest(query: String, regionCode: String?, params: String? = null): SearchRequest =
    SearchRequest(
        context = Context(
            client = Client(
                clientName = INNERTUBE_CLIENT_NAME,
                clientVersion = INNERTUBE_CLIENT_VERSION,
                hl = INNERTUBE_HL,
                gl = regionCode?.takeIf { it.isNotBlank() },
            ),
        ),
        query = query,
        params = params,
    )
