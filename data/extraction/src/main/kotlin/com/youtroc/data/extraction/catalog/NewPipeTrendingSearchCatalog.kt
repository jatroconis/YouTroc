package com.youtroc.data.extraction.catalog

import com.youtroc.core.domain.catalog.CatalogResult
import com.youtroc.core.domain.catalog.Shelf
import com.youtroc.core.domain.catalog.ShelfId
import com.youtroc.core.domain.catalog.VideoCatalog
import com.youtroc.core.domain.search.SearchResult
import com.youtroc.core.domain.search.VideoSearch

/**
 * Adapter: the Tendencias late-leg [VideoCatalog] over a NewPipe SEARCH for
 * the trending seed query — NOT the kiosk. YouTube removed the classic
 * Trending page server-side (2025-07-21, see CHANGELOG), so NewPipe's default
 * kiosk no longer resolves to trending content: on-device diagnosis (TCL,
 * 2026-07-05) showed [NewPipeVideoCatalog]'s `getDefaultKioskExtractor`
 * returning the LIVE kiosk — 15 live streams labeled into the Tendencias
 * slot, the same wrong-shelf behavior the owner had long observed as
 * "a veces sale el live". Searching the regional seed instead keeps the
 * owner-mandated NewPipe safety net (decision #4599) delivering actual
 * trending-flavored content.
 *
 * Pure mapping over the [VideoSearch] port; the shelf id is a placeholder the
 * composer re-tags (`.copy(id = source.id)`, F3 convention).
 */
class NewPipeTrendingSearchCatalog(
    private val search: VideoSearch,
    private val query: String = "tendencias",
) : VideoCatalog {

    override suspend fun trending(): CatalogResult = when (val result = search.search(query)) {
        is SearchResult.Success -> CatalogResult.Success(
            listOf(Shelf(id = ShelfId.TENDENCIAS, title = "Tendencias", videos = result.videos)),
        )

        SearchResult.Empty -> CatalogResult.Empty
        SearchResult.Offline -> CatalogResult.Offline
        is SearchResult.Error -> CatalogResult.Error(result.cause)
    }
}
