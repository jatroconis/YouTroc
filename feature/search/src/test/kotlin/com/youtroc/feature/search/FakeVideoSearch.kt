package com.youtroc.feature.search

import com.youtroc.core.domain.search.SearchResult
import com.youtroc.core.domain.search.VideoSearch
import kotlinx.coroutines.delay

/**
 * Local in-memory test double for the domain [VideoSearch] port. Test source
 * sets aren't shared across Gradle modules, so `:feature:search` cannot see
 * `:core:domain`'s own test-only `FakeVideoSearch` — mirrors
 * `:feature:catalog`'s local `FakeVideoCatalog` fixture pattern.
 *
 * [callCount] exists specifically to prove gate MAJOR-1 (design-gate #4408):
 * a blank/whitespace [SearchViewModel.search] call must NEVER reach this
 * fake — [callCount] staying `0` is the assertion, not just the resulting
 * state.
 *
 * [result] is mutable (`var`) so a single instance can simulate a changed
 * backend answer between calls (retry test) — the
 * [com.youtroc.core.domain.search.SearchVideos] wrapping this fake is REAL,
 * never faked itself. [search] suspends via a 1ms virtual
 * [delay] so [SearchViewModel]'s transient `Loading` state is a real,
 * externally observable suspension point under `StandardTestDispatcher`.
 */
class FakeVideoSearch(var result: SearchResult) : VideoSearch {

    var callCount: Int = 0
        private set

    var lastQuery: String? = null
        private set

    override suspend fun search(query: String): SearchResult {
        callCount++
        lastQuery = query
        delay(1)
        return result
    }
}
