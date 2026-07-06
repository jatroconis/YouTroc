package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.catalog.CatalogResult
import com.youtroc.core.domain.catalog.ShelfId
import com.youtroc.core.domain.catalog.ShelfSource
import com.youtroc.core.domain.catalog.VideoCatalog

/**
 * Adapter: the Tendencias shelf's [ShelfSource], the composer's LEAD (N3,
 * owner decision #4599). [load] is bounded (delegates to [innerTube],
 * wrapped by [com.youtroc.core.domain.catalog.ComposeHomeFeed]'s own timeout
 * ceiling); [loadFallback] is the UNBOUNDED late leg (delegates to
 * [newPipe]'s Trending kiosk) that may append the shelf late instead of
 * leaving Home terminal. [displayTitle] stays `null`: both delegates already
 * supply a real [com.youtroc.core.domain.catalog.Shelf.title] of their own
 * ("Popular en {region}" or the NewPipe kiosk name).
 */
class TendenciasLeadShelfSource(
    private val innerTube: VideoCatalog,
    private val newPipe: VideoCatalog,
    override val timeoutMs: Long = 1_100L,
) : ShelfSource {

    override val id: ShelfId = ShelfId.TENDENCIAS
    override val displayTitle: String? = null

    override suspend fun load(): CatalogResult = innerTube.trending()

    override suspend fun loadFallback(): CatalogResult = newPipe.trending()
}
