package com.youtroc.core.domain.catalog

/** A titled group of trending [Video]s (e.g. the "Trending" kiosk). */
data class Shelf(
    val title: String,
    val videos: List<Video>,
)
