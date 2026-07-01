package com.youtroc.core.domain.search

/**
 * In-memory test double for the [VideoSearch] port. Replays a preconfigured
 * [SearchResult] and records the last query received — no network, no
 * Android. This is the whole point of a port: the domain is testable in
 * isolation.
 */
class FakeVideoSearch(
    private val result: SearchResult,
) : VideoSearch {

    var lastQuery: String? = null
        private set

    override suspend fun search(query: String): SearchResult {
        lastQuery = query
        return result
    }
}
