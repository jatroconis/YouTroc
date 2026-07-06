package com.youtroc.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing tokens for the 10-foot TV grid, measured against the real YouTube-for-TV
 * app on the target device (1920x1080 @ 320dpi → 2px = 1dp).
 */
object YouTrocDimens {
    /** Width of the collapsed (icons-only) nav rail column. */
    val railWidth = 64.dp

    /**
     * Content gutter inside the area to the right of the rail. Combined with
     * [railWidth] this lands cards at ~80dp from the screen edge (YouTube ~76dp).
     */
    val railContentStart = 16.dp

    /** Overscan-safe margin for the right/end edge and top/bottom. */
    val overscanHorizontal = 58.dp
    val overscanVertical = 28.dp

    val shelfSpacing = 28.dp
    val cardSpacing = 16.dp

    /** ~2.4 cards visible, matching YouTube's Home shelves (not the denser 4-up). */
    val cardWidth = 320.dp

    /**
     * Portrait Shorts card width (recon #4604: "~5.5 visible per row", roughly
     * half [cardWidth]'s on-screen footprint at a 9:16 aspect instead of 16:9).
     * On-device visual-density tuning is a follow-up task, same as [cardWidth]'s
     * own YouTube-comparison note.
     */
    val shortsCardWidth = 140.dp
}
