package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.catalog.Shelf
import com.youtroc.core.domain.catalog.ShelfId
import com.youtroc.core.domain.catalog.Video

/**
 * Regional-shelf detection for `POST youtubei/v1/search` responses (ADR-2,
 * design-gate CORRECTION 1): YouTube's search-proxy sometimes places a
 * channel-promo shelfRenderer (e.g. "Lo último de Noticias Caracol") BEFORE
 * the region-anchoring one -- "first shelfRenderer with items" would render
 * that channel's latest uploads mislabeled as the Home feed. Detection
 * therefore keys on the shelf's TITLE (`"Popular en "` prefix), not its
 * position. This is robust because [INNERTUBE_HL] is pinned to `"es"`
 * app-wide (the app is Spanish-only) -- the shelf title is always Spanish.
 *
 * Pure and network-free: consumes an already-decoded [SearchResponse].
 */
private const val REGIONAL_SHELF_TITLE_PREFIX = "Popular en "

/** Every [RenderItem] across all top-level sections, in source order. */
private fun SearchResponse.renderItems(): List<RenderItem> =
    contents
        ?.twoColumnSearchResultsRenderer
        ?.primaryContents
        ?.sectionListRenderer
        ?.contents
        .orEmpty()
        .flatMap { it.itemSectionRenderer?.contents.orEmpty() }

/**
 * Every `shelfRenderer` whose title starts with [REGIONAL_SHELF_TITLE_PREFIX],
 * in source order, mapped to its verbatim title and its mappable domain
 * [Video]s (reusing the shipped [VideoRenderer.toVideoOrNull]). The videos
 * list MAY be empty: [InnerTubeVideoCatalog]'s labeled-flat fallback (ADR-5
 * layer (a) step 2) needs the title of such a shelf even when it carries zero
 * mappable items, so this stays exposed rather than folded into
 * [regionalShelf].
 */
internal fun SearchResponse.popularEnShelves(): List<Pair<String, List<Video>>> =
    renderItems()
        .mapNotNull { it.shelfRenderer }
        .mapNotNull { shelf ->
            val title = shelf.title?.simpleText?.takeIf { it.startsWith(REGIONAL_SHELF_TITLE_PREFIX) }
                ?: return@mapNotNull null
            val videos = shelf.content?.verticalListRenderer?.items.orEmpty()
                .mapNotNull { it.videoRenderer?.toVideoOrNull() }
            title to videos
        }

/**
 * The primary regional shelf (ADR-2): the FIRST `"Popular en "`-prefixed
 * shelfRenderer that maps to at least one domain [Video]. `null` when no such
 * shelf exists in the response, OR every matching shelf maps to zero
 * mappable videos -- callers should consult [popularEnShelves] directly to
 * implement the ADR-5 labeled-flat fallback for that latter case.
 */
internal fun SearchResponse.regionalShelf(): Shelf? =
    popularEnShelves()
        .firstOrNull { (_, videos) -> videos.isNotEmpty() }
        ?.let { (title, videos) ->
            // Placeholder id: this class only ever seeds the Tendencias shelf today.
            // ComposeHomeFeed re-tags it via `.copy(id = source.id)` once wrapped by a
            // ShelfSource (F3), so this value is never actually observed downstream.
            Shelf(id = ShelfId.TENDENCIAS, title = title, videos = videos)
        }
