package com.youtroc.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing tokens for the 10-foot TV grid, measured against the real YouTube-for-TV
 * app on the target device (1920x1080 @ 320dpi → 2px = 1dp).
 */
object YouTrocDimens {
    /**
     * Left inset for content so it clears the collapsed nav rail with a gap.
     * YouTube's rail ends ~50dp and its content starts ~76dp (a ~26dp gutter).
     */
    val railContentStart = 76.dp

    /** Overscan-safe margin for the right/end edge and top/bottom. */
    val overscanHorizontal = 58.dp
    val overscanVertical = 28.dp

    val shelfSpacing = 28.dp
    val cardSpacing = 16.dp

    /** ~2.4 cards visible, matching YouTube's Home shelves (not the denser 4-up). */
    val cardWidth = 320.dp
}
