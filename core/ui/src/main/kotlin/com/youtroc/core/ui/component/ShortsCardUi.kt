package com.youtroc.core.ui.component

import androidx.compose.runtime.Immutable

/**
 * Immutable presentation model for a Shorts card (recon #4604). Deliberately
 * narrower than [VideoCardUi] — no `channel`/`meta` fields — because the
 * Shorts card renders neither: title overlays the thumbnail itself, with no
 * channel/views/age line beneath it (unlike the landscape [TvVideoCard]).
 */
@Immutable
data class ShortsCardUi(
    val id: String,
    val thumbnailUrl: String,
    val title: String,
)
