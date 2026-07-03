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

/** A single search result slot: a video, or a non-video renderer we skip (channel/shelf/ad/...). */
@Serializable
internal data class RenderItem(
    val videoRenderer: VideoRenderer? = null,
)

@Serializable
internal data class VideoRenderer(
    val videoId: String? = null,
    val title: Runs? = null,
    val ownerText: Runs? = null,
    val viewCountText: ViewCountText? = null,
    val publishedTimeText: SimpleText? = null,
    val thumbnail: Thumbnail? = null,
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
