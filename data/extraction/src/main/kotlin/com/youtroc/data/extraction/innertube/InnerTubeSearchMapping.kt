package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.catalog.Video
import com.youtroc.core.domain.search.SearchResult
import com.youtroc.core.domain.video.VideoId
import com.youtroc.data.extraction.catalog.UNKNOWN_CHANNEL_PLACEHOLDER
import com.youtroc.data.extraction.catalog.UNTITLED_VIDEO_PLACEHOLDER
import java.io.IOException

/**
 * Flattens the response tree down to every [VideoRenderer] found under
 * `contents.twoColumnSearchResultsRenderer.primaryContents.sectionListRenderer
 * .contents[].itemSectionRenderer.contents[].videoRenderer`, in source order.
 * Sibling items with no [RenderItem.videoRenderer] (channelRenderer,
 * shelfRenderer, ads, ...) and top-level sections with no
 * [SectionItem.itemSectionRenderer] (e.g. a continuation) are structurally
 * absent from the result -- nothing to filter explicitly.
 */
internal fun SearchResponse.videoRenderers(): List<VideoRenderer> =
    contents
        ?.twoColumnSearchResultsRenderer
        ?.primaryContents
        ?.sectionListRenderer
        ?.contents
        .orEmpty()
        .flatMap { it.itemSectionRenderer?.contents.orEmpty() }
        .mapNotNull { it.videoRenderer }

/**
 * Maps a [VideoRenderer] onto the domain [Video]. Only [Video.id] is
 * guaranteed non-blank by construction (a [VideoId] requires it): a renderer
 * whose `videoId` is missing/blank is dropped (mirrors
 * [com.youtroc.data.extraction.catalog.toVideoOrNull]'s id-drop convention
 * and reuses its same placeholders -- both mappers coalesce blank
 * title/channel to identical copy).
 */
internal fun VideoRenderer.toVideoOrNull(): Video? {
    val id = videoId?.takeIf { it.isNotBlank() } ?: return null
    return Video(
        id = VideoId(id),
        title = title?.runs?.firstOrNull()?.text.orEmpty().ifBlank { UNTITLED_VIDEO_PLACEHOLDER },
        channelName = ownerText?.runs?.firstOrNull()?.text.orEmpty().ifBlank { UNKNOWN_CHANNEL_PLACEHOLDER },
        thumbnailUrl = pickThumbnailUrl(thumbnail, id),
        viewCount = viewCountText.parsedViewCount(),
        publishedText = publishedTimeText?.simpleText?.takeIf { it.isNotBlank() },
    )
}

/**
 * R1: prefers the full localized count in `simpleText` (e.g.
 * "25.463.189 visualizaciones" -> 25463189); falls back to joining `runs[].text`
 * for the live-stream form (e.g. ["1932", " usuarios"] -> "1932 usuarios" ->
 * 1932). Strips everything but digits; null on absent/unparseable input --
 * never throws. `shortViewCountText` is intentionally not consulted: its
 * abbreviated form ("1,2 M") isn't digit-recoverable.
 */
internal fun ViewCountText?.parsedViewCount(): Long? {
    val raw = this?.simpleText ?: this?.runs?.joinToString("") { it.text.orEmpty() }
    return raw.toCountOrNull()
}

/**
 * Picks a card-appropriate thumbnail rendition: the widest available image,
 * or a well-known ytimg template keyed by video id when the source has none.
 * Pure and total, no network. Self-contained rather than reusing
 * [com.youtroc.data.extraction.catalog.pickThumbnail]: that helper is typed
 * over NewPipe's `Image`, which never appears on this adapter's boundary.
 */
internal fun pickThumbnailUrl(thumbnail: Thumbnail?, videoId: String): String =
    thumbnail?.thumbnails.orEmpty().maxByOrNull { it.width }?.url
        ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"

private const val LIVE_BADGE_STYLE = "BADGE_STYLE_TYPE_LIVE_NOW"

/**
 * Whether this renderer carries YouTube's "LIVE NOW" badge -- the guard
 * [com.youtroc.data.extraction.innertube.EnVivoShelfSource] uses to filter
 * live-only results out of a generic keyword search response (M3): a plain
 * search for "en vivo" also returns ordinary non-live videos whose
 * title/description happens to mention it (spike #4603 Q1).
 */
internal fun VideoRenderer.hasLiveBadge(): Boolean =
    badges.orEmpty().any { it.metadataBadgeRenderer?.style == LIVE_BADGE_STYLE }

/**
 * Maps an InnerTube-adapter failure onto the domain's typed outcome.
 * Mirrors [com.youtroc.data.extraction.catalog.toCatalogResult] /
 * [com.youtroc.data.extraction.search.toSearchResult]: an [IOException]
 * (no connectivity, DNS failure, timeout) means [SearchResult.Offline];
 * anything else (non-200 HTTP, malformed JSON) is a [SearchResult.Error]
 * carrying the cause. Cooperative cancellation is handled by the caller
 * BEFORE reaching this function -- it must never be mapped here (S3).
 */
internal fun Throwable.toSearchResult(): SearchResult = when (this) {
    is IOException -> SearchResult.Offline
    else -> SearchResult.Error(this)
}
