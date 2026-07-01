package com.youtroc.core.domain.catalog

import com.youtroc.core.domain.video.VideoId

/**
 * A single trending video as resolved by [VideoCatalog].
 *
 * Carries structured metadata only — no Spanish/UI copy. Formatting [viewCount]
 * and [publishedText] into user-facing strings is the feature edge's job, so the
 * domain stays free of localized presentation concerns.
 */
data class Video(
    val id: VideoId,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    /** Raw view count; null when unknown (e.g. the source reports a negative value). */
    val viewCount: Long?,
    /** Source-provided, already-localized textual date (e.g. "2 days ago"); opaque to the domain. */
    val publishedText: String?,
)
