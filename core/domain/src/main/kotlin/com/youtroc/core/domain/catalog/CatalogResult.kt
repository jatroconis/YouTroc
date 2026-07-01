package com.youtroc.core.domain.catalog

/**
 * Typed outcome of asking for the Home trending feed.
 *
 * Mirrors [com.youtroc.core.domain.stream.StreamResult]: failure is part of the
 * domain vocabulary, not an exception. Adapters in :data:extraction map their own
 * IO/parse errors onto these cases, so nothing throws across the port boundary
 * and the UI can render a deterministic state for each outcome.
 */
sealed interface CatalogResult {

    /** Trending shelves resolved and ready to render. */
    data class Success(val shelves: List<Shelf>) : CatalogResult

    /** The feed resolved but returned zero items. */
    data object Empty : CatalogResult

    /** No network reachable. */
    data object Offline : CatalogResult

    /** Extraction failed unexpectedly; [cause] is for logging, not for control flow. */
    data class Error(val cause: Throwable) : CatalogResult
}
