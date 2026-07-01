package com.youtroc.core.domain.search

/**
 * Port: resolves videos-only YouTube search results for a free-text query.
 *
 * The domain owns this contract; adapters in :data:extraction implement it. It
 * returns a typed [SearchResult] and must never throw — turning transport and
 * parsing failures into domain outcomes is the adapter's responsibility.
 */
interface VideoSearch {
    suspend fun search(query: String): SearchResult
}
