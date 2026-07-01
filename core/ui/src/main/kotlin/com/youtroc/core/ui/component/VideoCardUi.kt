package com.youtroc.core.ui.component

import androidx.compose.runtime.Immutable

/**
 * Immutable presentation model for a video card. Carries no domain type — the design
 * system stays ignorant of the domain and data layers. The thumbnail is a plain URL
 * string (not a Painter) to preserve Compose strong-skipping.
 */
@Immutable
data class VideoCardUi(
    val id: String,
    val thumbnailUrl: String,
    val title: String,
    val channel: String,
    val meta: String,
)
