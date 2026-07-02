package com.youtroc.data.extraction.search

import com.youtroc.core.domain.search.SearchResult
import com.youtroc.core.domain.search.VideoSearch

/**
 * Decorator: tries [primary] first and returns its result unchanged on
 * [SearchResult.Success] or [SearchResult.Empty] -- a valid own-engine
 * outcome, even a zero-match one, is never second-guessed. On
 * [SearchResult.Error]/[SearchResult.Offline] from [primary], delegates to
 * [fallback] and returns ITS result unmodified (no further fallback chain --
 * only two adapters are composed in this slice).
 *
 * Both [primary]/[fallback] stay behind the same [VideoSearch] port, so this
 * class has no knowledge of InnerTube/NewPipe specifics -- pure composition,
 * unit-testable with fakes, no network.
 */
class FallbackVideoSearch(
    private val primary: VideoSearch,
    private val fallback: VideoSearch,
) : VideoSearch {

    override suspend fun search(query: String): SearchResult =
        when (val result = primary.search(query)) {
            is SearchResult.Success, SearchResult.Empty -> result
            SearchResult.Offline, is SearchResult.Error -> fallback.search(query)
        }
}
