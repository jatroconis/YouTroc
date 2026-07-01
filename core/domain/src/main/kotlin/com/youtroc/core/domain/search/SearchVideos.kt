package com.youtroc.core.domain.search

/**
 * Application entry point for "search YouTube for a query": resolves matching
 * videos through the [VideoSearch] port.
 *
 * Intentionally thin today. It exists as the seam where search business rules
 * (query normalization, history, session-aware filtering — RF-SRCH-10) will
 * attach later, without ever leaking into adapters or UI.
 */
class SearchVideos(
    private val videoSearch: VideoSearch,
) {
    suspend operator fun invoke(query: String): SearchResult = videoSearch.search(query)
}
