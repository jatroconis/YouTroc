package com.youtroc.core.domain.search

import com.youtroc.core.domain.catalog.Video

/**
 * Typed outcome of asking for search results for a free-text query.
 *
 * Mirrors [com.youtroc.core.domain.catalog.CatalogResult]: failure is part of
 * the domain vocabulary, not an exception. Adapters in :data:extraction map
 * their own IO/parse errors onto these cases, so nothing throws across the
 * port boundary and the UI can render a deterministic state for each outcome.
 */
sealed interface SearchResult {

    /** Matching videos resolved and ready to render. */
    data class Success(val videos: List<Video>) : SearchResult

    /** The query resolved but returned zero matches (or the query was blank). */
    data object Empty : SearchResult

    /** No network reachable. */
    data object Offline : SearchResult

    /** Extraction failed unexpectedly; [cause] is for logging, not for control flow. */
    data class Error(val cause: Throwable) : SearchResult
}
