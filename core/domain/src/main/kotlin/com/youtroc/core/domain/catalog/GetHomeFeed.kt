package com.youtroc.core.domain.catalog

/**
 * Application entry point for "show me the Home feed": resolves trending videos
 * through the [VideoCatalog] port.
 *
 * Intentionally thin today. It exists as the seam where feed business rules
 * (personalization, session-aware filtering) will attach later, without ever
 * leaking into adapters or UI.
 */
class GetHomeFeed(
    private val videoCatalog: VideoCatalog,
) {
    suspend operator fun invoke(): CatalogResult = videoCatalog.trending()
}
