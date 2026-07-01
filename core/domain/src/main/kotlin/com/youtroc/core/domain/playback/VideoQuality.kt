package com.youtroc.core.domain.playback

/**
 * A single selectable video rendition surfaced to the user, e.g. "1080p".
 * Pure value type — no Media3, no I/O.
 */
data class VideoQuality(
    val id: String,
    val label: String,
    val heightPx: Int,
    val bitrate: Int? = null,
)

/**
 * A raw video rendition an adapter observed in the live manifest, before it
 * is deduped/labeled into a [VideoQuality] by [VideoQualityCatalog]. Exists
 * so [VideoQualityCatalog] and [QualitySelectionPolicy] stay pure and
 * unit-testable without needing a real Media3 `Format` (which cannot be
 * constructed off-device).
 */
data class VideoRendition(
    val heightPx: Int,
    val widthPx: Int,
    val bitrate: Int? = null,
)
