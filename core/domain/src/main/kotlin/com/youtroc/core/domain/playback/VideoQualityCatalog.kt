package com.youtroc.core.domain.playback

/**
 * Builds the deduplicated, sorted list of selectable [VideoQuality] options
 * from the renditions a manifest currently exposes. Pure — no Media3, no I/O.
 */
object VideoQualityCatalog {

    /**
     * Dedupes [renditions] by [VideoRendition.heightPx] across codec groups
     * (e.g. an AV1 and a VP9 rendition both at 1080p collapse to one "1080p"
     * entry, using the highest bitrate seen at that height). Non-positive
     * heights are dropped. Sorted by height descending.
     *
     * A stream with fewer than two distinct heights has nothing meaningful
     * to pick between, so it collapses to an empty catalog — callers show
     * only "Automática" in that case (REQ-Q3).
     */
    fun from(renditions: List<VideoRendition>): List<VideoQuality> {
        val byHeight = renditions
            .filter { it.heightPx > 0 }
            .groupBy { it.heightPx }

        if (byHeight.size < 2) return emptyList()

        return byHeight
            .map { (height, group) ->
                VideoQuality(
                    id = "h$height",
                    label = "${height}p",
                    heightPx = height,
                    bitrate = group.mapNotNull { it.bitrate }.maxOrNull(),
                )
            }
            .sortedByDescending { it.heightPx }
    }
}
