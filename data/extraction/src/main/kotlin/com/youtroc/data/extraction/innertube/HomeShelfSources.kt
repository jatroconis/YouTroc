package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.catalog.ShelfId
import com.youtroc.core.domain.catalog.ShelfSource
import com.youtroc.data.extraction.catalog.NewPipeVideoCatalog
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

/** Tunable per-source ceilings -- on-device seed/timeout tuning is a slice-1 task (B1 residual). */
private const val TENDENCIAS_TIMEOUT_MS = 1_100L
private const val THEMATIC_TIMEOUT_MS = 1_500L

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
            newPipe = NewPipeVideoCatalog(regionCode = regionCode),
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
