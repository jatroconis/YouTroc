package com.youtroc.core.ui.component

import androidx.compose.runtime.Immutable

/**
 * Immutable presentation model for a video card. Deliberately carries no domain
 * type: the design system knows nothing about the domain or data layers.
 */
@Immutable
data class VideoCardUi(
    val id: String,
    val title: String,
    val subtitle: String,
)
