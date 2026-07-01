package com.youtroc.core.domain.catalog

/**
 * In-memory test double for the [VideoCatalog] port. Replays a preconfigured
 * [CatalogResult] — no network, no Android. This is the whole point of a port:
 * the domain is testable in isolation.
 */
class FakeVideoCatalog(
    private val result: CatalogResult,
) : VideoCatalog {

    override suspend fun trending(): CatalogResult = result
}
