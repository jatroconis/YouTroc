package com.youtroc.data.extraction.innertube

import kotlinx.serialization.Serializable

/**
 * Request/response DTO tree for `POST youtubei/v1/search`. Deliberately
 * minimal: only the path down to `videoRenderer` and the leaf fields
 * [InnerTubeSearchMapping.toVideoOrNull] needs are modeled -- everything else
 * (ads, shelves, playlist/channel renderers, tracking params, ...) is left
 * unmapped and tolerated via `Json { ignoreUnknownKeys = true }`. These types
 * are `internal`: they never cross [InnerTubeVideoSearch]'s port boundary.
 */

// ---- Request ----

@Serializable
internal data class SearchRequest(
    val context: Context,
    val query: String,
    /**
     * YouTube's protobuf-encoded search filter (e.g. `"EgJAAQ=="` = the Live
     * content-type filter, spike #4603 Q1). Additive/nullable-defaulted so
     * every existing caller keeps sending a plain query untouched;
     * `explicitNulls = false` (see [innerTubeSearchJson]) drops it from the
     * encoded body entirely when null, rather than sending `"params":null`.
     */
    val params: String? = null,
)

@Serializable
internal data class Context(
    val client: Client,
)

@Serializable
internal data class Client(
    val clientName: String,
    val clientVersion: String,
    val hl: String,
    /** Region code (e.g. "AR"); omitted entirely for a blank/absent region. */
    val gl: String? = null,
    /**
     * ANDROID_VR device identity, used only by [com.youtroc.data.extraction.innertube.InnerTubeStreamProvider]'s
     * player request. Nullable + defaulted so the shared WEB search/detail
     * requests keep omitting these fields untouched (`explicitNulls = false`
     * drops a null field entirely from the encoded JSON body).
     */
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: Int? = null,
)

// ---- Response ----

@Serializable
internal data class SearchResponse(
    val contents: Contents? = null,
)

@Serializable
internal data class Contents(
    val twoColumnSearchResultsRenderer: TwoColumnSearchResultsRenderer? = null,
)

@Serializable
internal data class TwoColumnSearchResultsRenderer(
    val primaryContents: PrimaryContents? = null,
)

@Serializable
internal data class PrimaryContents(
    val sectionListRenderer: SectionListRenderer? = null,
)

@Serializable
internal data class SectionListRenderer(
    val contents: List<SectionItem> = emptyList(),
)

/** A top-level section: either an [itemSectionRenderer] or something we skip (e.g. a continuation). */
@Serializable
internal data class SectionItem(
    val itemSectionRenderer: ItemSectionRenderer? = null,
)

@Serializable
internal data class ItemSectionRenderer(
    val contents: List<RenderItem> = emptyList(),
)

/**
 * A single search result slot: a video, a shelf (region-anchoring or
 * channel-promo, see [ShelfRenderer]), or a non-video renderer we skip
 * (channel/ad/...). [shelfRenderer] is additive/nullable-defaulted so it
 * stays INERT for the shipped search slice -- [videoRenderers] reads only
 * [videoRenderer], never this field.
 */
@Serializable
internal data class RenderItem(
    val videoRenderer: VideoRenderer? = null,
    val shelfRenderer: ShelfRenderer? = null,
    /** Shorts shelf (spike #4603 Q2) -- see [GridShelfViewModel]. */
    val gridShelfViewModel: GridShelfViewModel? = null,
)

/**
 * A titled group of videos within a search response (e.g. `"Popular en
 * Bogotá"`, or a channel-promo shelf like `"Lo último de Noticias Caracol"`).
 * Used by [InnerTubeCatalogMapping] to detect the region-anchoring shelf by
 * title prefix (ADR-2) -- never consulted by the search slice.
 */
@Serializable
internal data class ShelfRenderer(
    val title: SimpleText? = null,
    val content: ShelfContent? = null,
)

@Serializable
internal data class ShelfContent(
    val verticalListRenderer: VerticalListRenderer? = null,
)

@Serializable
internal data class VerticalListRenderer(
    /** Items reuse [RenderItem]/[VideoRenderer] unchanged -- every child observed is a `videoRenderer`. */
    val items: List<RenderItem> = emptyList(),
)

@Serializable
internal data class VideoRenderer(
    val videoId: String? = null,
    val title: Runs? = null,
    val ownerText: Runs? = null,
    val viewCountText: ViewCountText? = null,
    val publishedTimeText: SimpleText? = null,
    val thumbnail: Thumbnail? = null,
    /** Liveness marker (spike #4603 Q1) -- see [VideoRenderer.hasLiveBadge]. */
    val badges: List<Badge>? = null,
)

@Serializable
internal data class Badge(
    val metadataBadgeRenderer: MetadataBadgeRenderer? = null,
)

@Serializable
internal data class MetadataBadgeRenderer(
    val style: String? = null,
)

@Serializable
internal data class Runs(
    val runs: List<Run> = emptyList(),
)

@Serializable
internal data class Run(
    val text: String? = null,
)

@Serializable
internal data class SimpleText(
    val simpleText: String? = null,
)

/**
 * R1: `viewCountText` is EITHER `{simpleText: "..."}` (most videos) OR
 * `{runs: [...]}` (live streams mid-broadcast, e.g. two runs joining into
 * "1932 usuarios"). Both forms are modeled so [InnerTubeSearchMapping]'s
 * `parsedViewCount` can prefer `simpleText` and fall back to joined `runs`.
 */
@Serializable
internal data class ViewCountText(
    val simpleText: String? = null,
    val runs: List<Run>? = null,
)

@Serializable
internal data class Thumbnail(
    val thumbnails: List<ThumbnailItem> = emptyList(),
)

@Serializable
internal data class ThumbnailItem(
    val url: String? = null,
    val width: Int = 0,
)
