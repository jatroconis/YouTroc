package com.youtroc.feature.playback

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.youtroc.core.domain.stream.StoryboardLevel
import com.youtroc.core.domain.stream.StoryboardSpec
import com.youtroc.core.ui.component.SpriteTile
import com.youtroc.core.ui.component.SpriteTileWidth
import com.youtroc.core.ui.component.spriteTileHeightFor

/** Vertical gap between the preview thumbnail and the scrubber track it sits above. */
private val ThumbnailTrackGap = 12.dp

/**
 * Netflix-style scrub-preview thumbnail (REQ-SB6, design D5): gated on
 * `scrubbing && storyboard != null` -- when either is false, this renders
 * nothing, so a VOD without a storyboard and live playback (never scrubbing)
 * are entirely unaffected. Positioned above the scrub cursor by reusing the
 * SAME `fullWidth`/`fraction` the scrubber's thumb dot already uses
 * (PlayerOverlay.kt's `Scrubber`), clamped so it never renders past either
 * screen edge (REQ-SB6's edge scenario) -- unlike the thumb dot, which only
 * clamps its LEFT edge.
 *
 * Delegates the actual crop-and-render to `:core:ui`'s [SpriteTile] --
 * `:feature:playback` never imports Coil directly (module boundary,
 * design D3). Never touches the Media3 engine: the storyboard is a static
 * per-video sidecar, resolved once at extraction, not through `MediaPlayer`.
 */
@Composable
internal fun ScrubPreviewThumbnail(
    storyboard: StoryboardSpec?,
    scrubbing: Boolean,
    positionMs: Long,
    fullWidth: Dp,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    if (!scrubbing || storyboard == null) return
    val level = storyboard.previewLevel() ?: return
    val tile = level.tileAt(positionMs)

    val previewWidth = SpriteTileWidth
    val previewHeight = spriteTileHeightFor(tile.widthPx, tile.heightPx)
    val maxX = (fullWidth - previewWidth).coerceAtLeast(0.dp)
    val clampedX = (fullWidth * fraction - previewWidth / 2).coerceIn(0.dp, maxX)

    // REQ-SB7/gate F5: StoryboardTile carries only the resolved sheet URL, not
    // its page index -- [spriteIndexAt] recomputes it purely to resolve the
    // NEIGHBOR (p-1/p+1) page URLs for SpriteTile's prewarm.
    val spriteIndex = level.spriteIndexAt(positionMs)
    val prefetchUrls = listOfNotNull(
        level.pageUrls.getOrNull(spriteIndex - 1),
        level.pageUrls.getOrNull(spriteIndex + 1),
    )

    // The host is the scrubber's 16dp-tall track box: without the unbounded
    // TopStart escape below, that parent COERCES this whole thumbnail window
    // to 16dp tall and only a sliver of the tile renders (observed on-device
    // as a squashed strip). `wrapContentSize(unbounded)` lets the content
    // measure at its true size; TopStart keeps it anchored (not centered) so
    // the negative offset math stays exact.
    Box(
        modifier = modifier
            .offset(x = clampedX, y = -(previewHeight + ThumbnailTrackGap))
            .wrapContentSize(align = Alignment.TopStart, unbounded = true),
    ) {
        SpriteTile(
            url = tile.url,
            srcXPx = tile.srcXPx,
            srcYPx = tile.srcYPx,
            tileWidthPx = tile.widthPx,
            tileHeightPx = tile.heightPx,
            prefetchUrls = prefetchUrls,
        )
    }
}

/**
 * Mirrors [StoryboardLevel.tileAt]'s spriteIndex math (gate F5): resolves
 * ONLY which sprite page a position falls on, never the tile crop itself
 * (that stays [StoryboardLevel.tileAt]'s job) -- needed here because
 * [com.youtroc.core.domain.stream.StoryboardTile] deliberately does not
 * carry its own page index.
 */
private fun StoryboardLevel.spriteIndexAt(positionMs: Long): Int {
    val framesPerSprite = columns * rows
    val frameIndex = (positionMs / intervalMs).coerceAtMost((totalFrames - 1).toLong()).toInt()
    return (frameIndex / framesPerSprite).coerceAtMost(pageUrls.lastIndex)
}
