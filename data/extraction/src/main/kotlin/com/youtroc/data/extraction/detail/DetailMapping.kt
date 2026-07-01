package com.youtroc.data.extraction.detail

import com.youtroc.core.domain.detail.DetailResult
import com.youtroc.core.domain.detail.VideoDetailInfo
import com.youtroc.core.domain.video.VideoId
import com.youtroc.data.extraction.catalog.toVideoOrNull
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException

/**
 * Maps a NewPipe failure onto the domain's typed outcome. Kept as an internal
 * top-level function so the mapping policy is deterministic and unit-testable
 * without any network.
 *
 * Order matters: [ContentNotAvailableException] is a subtype of [ExtractionException].
 * Mirrors [com.youtroc.data.extraction.toStreamResult] (NOT catalog/search's
 * Empty mapping): a single unresolvable video is a per-video unavailability.
 */
internal fun Throwable.toDetailResult(): DetailResult = when (this) {
    is ContentNotAvailableException -> DetailResult.NotAvailable
    is IOException -> DetailResult.Offline
    is ExtractionException -> DetailResult.Error(this)
    else -> DetailResult.Error(this)
}

private const val UNTITLED_VIDEO_PLACEHOLDER = "Untitled video"
private const val UNKNOWN_CHANNEL_PLACEHOLDER = "Unknown channel"

/**
 * Maps a [StreamInfo] onto the domain [VideoDetailInfo]. [videoId] is passed
 * in rather than re-derived from `id`, so the caller's already-validated
 * [VideoId] is carried through untouched.
 *
 * `getDescription()` returns a [org.schabi.newpipe.extractor.stream.Description]
 * object, NOT a plain String — `.content` is the actual text (jar-verified,
 * design-gate-review #4419). Related items REUSE the catalog mapping
 * ([toVideoOrNull]) — same module, already covered by
 * `StreamInfoItemMappingTest`; an empty related list is a valid mapping, not
 * an error.
 */
internal fun StreamInfo.toVideoDetailInfo(videoId: VideoId): VideoDetailInfo = VideoDetailInfo(
    videoId = videoId,
    title = name.orEmpty().ifBlank { UNTITLED_VIDEO_PLACEHOLDER },
    channelName = uploaderName.orEmpty().ifBlank { UNKNOWN_CHANNEL_PLACEHOLDER },
    description = description?.content?.takeIf { it.isNotBlank() },
    viewCount = viewCount.takeIf { it >= 0 },
    publishedText = textualUploadDate,
    related = relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideoOrNull() },
)
