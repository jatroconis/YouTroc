package com.youtroc.data.extraction.catalog

import com.youtroc.core.domain.catalog.CatalogResult
import com.youtroc.core.domain.catalog.Video
import com.youtroc.core.domain.video.VideoId
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException

/**
 * Maps a NewPipe failure onto the domain's typed outcome. Kept as an internal
 * top-level function so the mapping policy is deterministic and unit-testable
 * without any network.
 *
 * Order matters: [ContentNotAvailableException] is a subtype of [ExtractionException].
 * Mirrors [com.youtroc.data.extraction.toStreamResult], with [ContentNotAvailableException]
 * mapping to [CatalogResult.Empty] instead of a "not available" case — a degraded
 * trending kiosk reads as an empty feed, not a per-video unavailability.
 */
internal fun Throwable.toCatalogResult(): CatalogResult = when (this) {
    is ContentNotAvailableException -> CatalogResult.Empty
    is IOException -> CatalogResult.Offline
    is ExtractionException -> CatalogResult.Error(this)
    else -> CatalogResult.Error(this)
}

// internal (not private): reused by the InnerTube mapping in the innertube
// package (com.youtroc.data.extraction.innertube.InnerTubeSearchMapping) so
// both source-specific mappers coalesce blank fields to the same copy.
internal const val UNTITLED_VIDEO_PLACEHOLDER = "Untitled video"
internal const val UNKNOWN_CHANNEL_PLACEHOLDER = "Unknown channel"

/**
 * Maps a kiosk [StreamInfoItem] onto the domain [Video]. Only [Video.id] is
 * guaranteed non-blank by construction (a [VideoId] requires it): items whose
 * url yields no id are dropped (mirrors `NewPipeStreamProvider.toDomainOrNull`).
 * A blank title/channel name from the source is coalesced to a safe placeholder
 * rather than propagated, so the UI never renders an empty card label.
 */
internal fun StreamInfoItem.toVideoOrNull(): Video? {
    val id = runCatching { ServiceList.YouTube.streamLHFactory.getId(url) }.getOrNull()
    if (id.isNullOrBlank()) return null
    return Video(
        id = VideoId(id),
        title = name.orEmpty().ifBlank { UNTITLED_VIDEO_PLACEHOLDER },
        channelName = uploaderName.orEmpty().ifBlank { UNKNOWN_CHANNEL_PLACEHOLDER },
        thumbnailUrl = pickThumbnail(thumbnails, id),
        viewCount = viewCount.takeIf { it >= 0 },
        publishedText = textualUploadDate,
    )
}

/**
 * Picks a card-appropriate thumbnail rendition: the widest available image, or
 * a well-known ytimg template keyed by video id when the source has none. Pure
 * and total, no network — unit-testable in isolation.
 */
internal fun pickThumbnail(images: List<Image>, videoId: String): String =
    images.maxByOrNull { it.width }?.url ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"
