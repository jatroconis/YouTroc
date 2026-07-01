package com.youtroc.core.domain.detail

import com.youtroc.core.domain.catalog.Video
import com.youtroc.core.domain.video.VideoId

/**
 * Full detail of a single video, as resolved by [VideoDetail].
 *
 * Carries structured metadata only — no Spanish/UI copy. Formatting
 * [viewCount] and [publishedText] into user-facing strings is the feature
 * edge's job, so the domain stays free of localized presentation concerns.
 * [related] reuses catalog's [Video] — the same shape as the shelves it
 * recurses into.
 */
data class VideoDetailInfo(
    val videoId: VideoId,
    val title: String,
    val channelName: String,
    /** Raw description text; null (or blank at the source) means the UI hides the block. */
    val description: String?,
    /** Raw view count; null when unknown (e.g. the source reports a negative value). */
    val viewCount: Long?,
    /** Source-provided, already-localized textual date (e.g. "2 days ago"); opaque to the domain. */
    val publishedText: String?,
    /** Read-only related videos; empty is a valid outcome, not a separate state. */
    val related: List<Video>,
)
