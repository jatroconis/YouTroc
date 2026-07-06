package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.catalog.ShelfId
import com.youtroc.core.domain.catalog.ShelfSource
import com.youtroc.data.extraction.catalog.NewPipeTrendingSearchCatalog
import com.youtroc.data.extraction.search.NewPipeVideoSearch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit.MILLISECONDS

/**
 * Derives a per-source [OkHttpClient] with its own `callTimeout` bound (B1:
 * the REAL per-seed abort mechanism -- a real OkHttp `AsyncTimeout` that can
 * interrupt a blocked socket read, unlike [com.youtroc.core.domain.catalog.ComposeHomeFeed]'s
 * `withTimeoutOrNull` ceiling alone). NEVER mutates [base] (M1) --
 * `newBuilder()` copies config and connection pool, `callTimeout` only
 * applies to the DERIVED instance, so every other caller of [base] (e.g.
 * [InnerTubeVideoSearch]'s default client) keeps its own timeout unchanged.
 */
internal fun deriveTimeoutClient(base: OkHttpClient, timeoutMs: Long): OkHttpClient =
    base.newBuilder().callTimeout(timeoutMs, MILLISECONDS).build()

/**
 * Tunable per-source ceilings, set from ON-DEVICE measurement (TCL 55C6K,
 * cold start, 2026-07-05): with 9 sources fanning out concurrently over
 * fresh connections, single search round-trips measured 1.6-3.5s (TLS
 * handshakes contend on the TV's CPU) and even the local DataStore read
 * took 1.2s -- the original paper budgets (1100/1500ms) timed out EVERY
 * network shelf. Late arrivals are cheap (progressive append, REQ-HF3);
 * a missing shelf is not. So the ceilings are generous: the lead still
 * lands fast in the warm/typical case, and the thematic shelves simply
 * append when ready.
 */
private const val TENDENCIAS_TIMEOUT_MS = 4_000L
private const val THEMATIC_TIMEOUT_MS = 8_000L

/**
 * Factory: builds the 7 production [ShelfSource]s for REQ-HF1, each over its
 * own [deriveTimeoutClient]-derived [OkHttpClient] -- [InnerTubeHttp.client]
 * itself is never mutated, so playback extraction stays unaffected. Order
 * matches REQ-HF1's fixed shelf order; `ComposeHomeFeed`'s slot array
 * preserves it regardless of which source resolves first. [ShortsShelfSource]
 * is NOT included here -- it is wired into Home only at S3.
 */
fun homeShelfSources(regionCode: String?): List<ShelfSource> {
    val base = InnerTubeHttp.client
    return listOf(
        TendenciasLeadShelfSource(
            innerTube = InnerTubeVideoCatalog(client = deriveTimeoutClient(base, TENDENCIAS_TIMEOUT_MS), regionCode = regionCode),
            // Search-backed late leg: NewPipe's default KIOSK now resolves to
            // LIVE (YouTube killed the classic Trending page), which mislabeled
            // live streams into this slot -- see NewPipeTrendingSearchCatalog.
            newPipe = NewPipeTrendingSearchCatalog(NewPipeVideoSearch(regionCode = regionCode)),
            timeoutMs = TENDENCIAS_TIMEOUT_MS,
        ),
        SearchQueryShelfSource(
            id = ShelfId.MUSICA,
            displayTitle = "Música",
            query = "música",
            client = deriveTimeoutClient(base, THEMATIC_TIMEOUT_MS),
            regionCode = regionCode,
            timeoutMs = THEMATIC_TIMEOUT_MS,
        ),
        SearchQueryShelfSource(
            id = ShelfId.VIDEOJUEGOS,
            displayTitle = "Videojuegos",
            query = "videojuegos",
            client = deriveTimeoutClient(base, THEMATIC_TIMEOUT_MS),
            regionCode = regionCode,
            timeoutMs = THEMATIC_TIMEOUT_MS,
        ),
        SearchQueryShelfSource(
            id = ShelfId.NOTICIAS,
            displayTitle = "Noticias",
            query = "noticias",
            client = deriveTimeoutClient(base, THEMATIC_TIMEOUT_MS),
            regionCode = regionCode,
            timeoutMs = THEMATIC_TIMEOUT_MS,
        ),
        SearchQueryShelfSource(
            id = ShelfId.DEPORTES,
            displayTitle = "Deportes",
            query = "deportes",
            client = deriveTimeoutClient(base, THEMATIC_TIMEOUT_MS),
            regionCode = regionCode,
            timeoutMs = THEMATIC_TIMEOUT_MS,
        ),
        SearchQueryShelfSource(
            id = ShelfId.CINE,
            displayTitle = "Cine y tráilers",
            query = "cine y tráilers",
            client = deriveTimeoutClient(base, THEMATIC_TIMEOUT_MS),
            regionCode = regionCode,
            timeoutMs = THEMATIC_TIMEOUT_MS,
        ),
        EnVivoShelfSource(
            client = deriveTimeoutClient(base, THEMATIC_TIMEOUT_MS),
            regionCode = regionCode,
            timeoutMs = THEMATIC_TIMEOUT_MS,
        ),
    )
}

/**
 * Assembles the FULL production Home shelf-source list (S3 wiring, design
 * rev3's shelf-order note): SeguirViendo first, then the Tendencias lead
 * (always [thematicSources]'s first element per REQ-HF1's fixed order), then
 * Shorts, then the remaining 6 thematic sources unchanged. [ComposeHomeFeed]'s
 * slot array (M2) preserves this declaration order in the rendered snapshot
 * regardless of which source resolves first (REQ-HF5).
 *
 * A pure list-assembly seam kept separate from [homeShelfSources] (which stays
 * scoped to its own REQ-HF1 7-source contract) precisely so this ordering
 * invariant is unit-testable without needing a real [android.content.Context]
 * or network access -- [seguirViendo]/[shorts] arrive already constructed by
 * the composition root (`:app`'s `HomeViewModelFactory`).
 */
fun assembleFullHomeShelfSources(
    thematicSources: List<ShelfSource>,
    seguirViendo: ShelfSource,
    shorts: ShelfSource,
): List<ShelfSource> {
    val tendenciasLead = thematicSources.first()
    val remainingThematic = thematicSources.drop(1)
    return buildList {
        add(seguirViendo)
        add(tendenciasLead)
        add(shorts)
        addAll(remainingThematic)
    }
}
