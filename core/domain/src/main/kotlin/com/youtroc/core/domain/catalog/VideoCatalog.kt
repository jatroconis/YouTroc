package com.youtroc.core.domain.catalog

/**
 * Port: resolves the anonymous Home trending feed from an external source
 * (YouTube's Trending kiosk).
 *
 * The domain owns this contract; adapters in :data:extraction implement it. It
 * returns a typed [CatalogResult] and must never throw — turning transport and
 * parsing failures into domain outcomes is the adapter's responsibility.
 */
interface VideoCatalog {
    suspend fun trending(): CatalogResult
}
