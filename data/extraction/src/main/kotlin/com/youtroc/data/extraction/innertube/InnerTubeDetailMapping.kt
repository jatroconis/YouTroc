package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.catalog.Video
import com.youtroc.core.domain.detail.DetailResult
import com.youtroc.core.domain.detail.VideoDetailInfo
import com.youtroc.core.domain.video.VideoId
import com.youtroc.data.extraction.catalog.UNKNOWN_CHANNEL_PLACEHOLDER
import com.youtroc.data.extraction.catalog.UNTITLED_VIDEO_PLACEHOLDER
import java.io.IOException

private const val LOCKUP_CONTENT_TYPE_VIDEO = "LOCKUP_CONTENT_TYPE_VIDEO"

/**
 * Maps a decoded `next` [NextResponse] onto the domain [VideoDetailInfo], or
 * `null` when [VideoPrimaryInfoRenderer] is absent -- the ONLY unavailability
 * signal `next` gives us (design decision: NotAvailable by absence, `next`
 * carries no `playabilityStatus`). [videoId] is passed in rather than
 * re-derived from the response, mirroring
 * [com.youtroc.data.extraction.detail.toVideoDetailInfo]'s convention.
 */
internal fun NextResponse.videoDetailInfoOrNull(videoId: String): VideoDetailInfo? {
    val contents = contents
        ?.twoColumnWatchNextResults
        ?.results
        ?.results
        ?.contents
        .orEmpty()

    val primary = contents.firstNotNullOfOrNull { it.videoPrimaryInfoRenderer } ?: return null
    val secondary = contents.firstNotNullOfOrNull { it.videoSecondaryInfoRenderer }

    val related = this
        .contents
        ?.twoColumnWatchNextResults
        ?.secondaryResults
        ?.secondaryResults
        ?.results
        .orEmpty()
        .mapNotNull { it.lockupViewModel }
        .mapNotNull { it.toVideoOrNull() }

    return VideoDetailInfo(
        videoId = VideoId(videoId),
        title = primary.title?.runs?.firstOrNull()?.text.orEmpty().ifBlank { UNTITLED_VIDEO_PLACEHOLDER },
        channelName = secondary
            ?.owner
            ?.videoOwnerRenderer
            ?.title
            ?.runs
            ?.firstOrNull()
            ?.text
            .orEmpty()
            .ifBlank { UNKNOWN_CHANNEL_PLACEHOLDER },
        description = secondary?.attributedDescription?.content?.takeIf { it.isNotBlank() },
        viewCount = primary.viewCount?.videoViewCountRenderer?.viewCount.parsedViewCount(),
        publishedText = primary.dateText?.simpleText?.takeIf { it.isNotBlank() },
        related = related,
    )
}

/**
 * Maps a related [LockupViewModel] onto the domain [Video]. Non-video
 * lockups (`LOCKUP_CONTENT_TYPE_PLAYLIST`, and any sibling shelf/continuation
 * item that has no `lockupViewModel` at all) are dropped -- their
 * `contentImage` uses a differently-shaped `collectionThumbnailViewModel`
 * this mapper never reads. A blank/absent `contentId` also drops the item
 * (mirrors [VideoRenderer.toVideoOrNull]'s id-drop convention). Each field
 * beyond id/title/thumbnail is read via null-safe `getOrNull` navigation so a
 * lockup missing a metadata row degrades to a null field instead of throwing.
 */
internal fun LockupViewModel.toVideoOrNull(): Video? {
    if (contentType != LOCKUP_CONTENT_TYPE_VIDEO) return null
    val id = contentId?.takeIf { it.isNotBlank() } ?: return null

    val metadataRows = metadata
        ?.lockupMetadataViewModel
        ?.metadata
        ?.contentMetadataViewModel
        ?.metadataRows
        .orEmpty()
    val row0 = metadataRows.getOrNull(0)?.metadataParts
    val row1 = metadataRows.getOrNull(1)?.metadataParts

    return Video(
        id = VideoId(id),
        title = metadata
            ?.lockupMetadataViewModel
            ?.title
            ?.content
            .orEmpty()
            .ifBlank { UNTITLED_VIDEO_PLACEHOLDER },
        channelName = row0?.getOrNull(0)?.text?.content.orEmpty().ifBlank { UNKNOWN_CHANNEL_PLACEHOLDER },
        thumbnailUrl = pickLockupThumbnailUrl(contentImage?.thumbnailViewModel?.image?.sources, id),
        viewCount = row1?.getOrNull(0)?.text?.content.toCountOrNull(),
        publishedText = row1?.getOrNull(1)?.text?.content?.takeIf { it.isNotBlank() },
    )
}

/**
 * Picks a card-appropriate thumbnail rendition for a `lockupViewModel`: the
 * widest available image, or a well-known ytimg template keyed by video id
 * when the source has none. Mirrors [pickThumbnailUrl]'s policy but is
 * self-contained: `lockupViewModel`'s `contentImage...sources[]` is a
 * different shape than `videoRenderer.thumbnail.thumbnails[]`.
 */
internal fun pickLockupThumbnailUrl(sources: List<LockupImageSource>?, videoId: String): String =
    sources.orEmpty().maxByOrNull { it.width }?.url
        ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"

/**
 * Maps an InnerTube-detail-adapter failure onto the domain's typed outcome.
 * Mirrors [toSearchResult]: an [IOException] (no connectivity, DNS failure,
 * timeout) means [DetailResult.Offline]; anything else (non-200 HTTP,
 * malformed JSON) is a [DetailResult.Error] carrying the cause. Cooperative
 * cancellation is handled by the caller BEFORE reaching this function -- it
 * must never be mapped here.
 */
internal fun Throwable.toDetailResult(): DetailResult = when (this) {
    is IOException -> DetailResult.Offline
    else -> DetailResult.Error(this)
}
