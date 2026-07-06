package com.youtroc.core.domain.catalog

/**
 * A titled group of trending [Video]s (e.g. the "Trending" kiosk), tagged
 * with [id] so the feature edge can key stable UI identity and Spanish title
 * mapping on something more durable than [title]. No default: every call
 * site must state which shelf it is building (R1).
 */
data class Shelf(
    val id: ShelfId,
    val title: String,
    val videos: List<Video>,
)
