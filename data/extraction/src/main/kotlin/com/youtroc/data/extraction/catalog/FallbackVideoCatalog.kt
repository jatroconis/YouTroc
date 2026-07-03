package com.youtroc.data.extraction.catalog

import com.youtroc.core.domain.catalog.CatalogResult
import com.youtroc.core.domain.catalog.VideoCatalog

/**
 * Decorator: tries [primary] (own
 * [com.youtroc.data.extraction.innertube.InnerTubeVideoCatalog]) first and
 * returns its result unchanged on [CatalogResult.Success] ONLY -- own's
 * regional shelf wins whenever it produced one. On [CatalogResult.Empty],
 * [CatalogResult.Offline], OR [CatalogResult.Error] from [primary], delegates
 * to [fallback] (NewPipe's Trending kiosk) and returns ITS result unmodified.
 *
 * DELIBERATE divergence from
 * [com.youtroc.data.extraction.search.FallbackVideoSearch] (which passes
 * [CatalogResult]-analogous `Empty` through as a trusted own answer) --
 * MIRRORS [com.youtroc.data.extraction.stream.LadderStreamProvider]
 * instead: own's [CatalogResult.Empty] is NOT authoritative here. A blank
 * Home is a bad terminal UX, so every non-[CatalogResult.Success] outcome
 * from [primary] gives [fallback] a chance to fill the feed.
 *
 * Both [primary]/[fallback] stay behind the same [VideoCatalog] port, so this
 * class has no knowledge of InnerTube/NewPipe specifics -- pure composition,
 * unit-testable with fakes, no network.
 */
class FallbackVideoCatalog(
    private val primary: VideoCatalog,
    private val fallback: VideoCatalog,
) : VideoCatalog {

    override suspend fun trending(): CatalogResult =
        when (val result = primary.trending()) {
            is CatalogResult.Success -> result
            CatalogResult.Empty, CatalogResult.Offline, is CatalogResult.Error -> fallback.trending()
        }
}
