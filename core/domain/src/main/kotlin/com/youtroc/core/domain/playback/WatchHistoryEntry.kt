package com.youtroc.core.domain.playback

import com.youtroc.core.domain.video.VideoId

/**
 * One on-device watch-history record (REQ-HF7): the metadata and progress
 * needed to render and resume a "Seguir viendo" card. [watchedAt] is the
 * wall-clock time (epoch millis) the entry was last saved -- used to order
 * history most-recently-watched first (REQ-HF8) and, at the
 * `:data:persistence` adapter, to distinguish this shape from a legacy
 * position-only record that predates title/channel/watchedAt (REQ-HF10).
 */
data class WatchHistoryEntry(
    val videoId: VideoId,
    val title: String,
    val channel: String,
    val watchedAt: Long,
    val position: PlaybackPosition,
    val durationMs: Long,
)
