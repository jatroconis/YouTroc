package com.youtroc.core.domain.catalog

/**
 * Port: a single Home shelf's content source. [ComposeHomeFeed] fans out over
 * many [ShelfSource]s concurrently, each bounded by its own [timeoutMs] via
 * [load] -- implementations must never throw; failures are typed
 * [CatalogResult] outcomes, same contract as [VideoCatalog].
 */
interface ShelfSource {
    val id: ShelfId
    val displayTitle: String?
    val timeoutMs: Long

    /** Bounded attempt -- [ComposeHomeFeed] wraps this with its own timeout ceiling (B1 residual). */
    suspend fun load(): CatalogResult

    /**
     * Optional UNBOUNDED late leg, run only when [load] fails to produce a
     * non-empty shelf before the ceiling. Defaults to `null` (no late leg);
     * only the Tendencias lead source overrides it (N3, owner decision #4599).
     */
    suspend fun loadFallback(): CatalogResult? = null
}
