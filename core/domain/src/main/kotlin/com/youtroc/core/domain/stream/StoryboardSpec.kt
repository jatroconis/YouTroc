package com.youtroc.core.domain.stream

/**
 * Source-agnostic set of storyboard sprite-sheet levels for a video (REQ-SB1):
 * extraction (InnerTube's `$L`/`$N`/`$M` spec or NewPipe's `Frameset`) already
 * resolved every level into fully-materialized [StoryboardLevel.pageUrls] --
 * this type carries NO URL-template/`sigh` knowledge, extending
 * [com.youtroc.core.domain.playback.PlaybackManifest]'s opaque-carrier
 * philosophy so `:core:domain` stays free of source-format details.
 */
data class StoryboardSpec(val levels: List<StoryboardLevel>) {

    /**
     * The one time-indexed level to preview from (REQ-SB3, design D2): prefers
     * an L2-class level (taller tiles, e.g. 160x90) over L1 (e.g. 80x45), and
     * EXCLUDES any L0-class recap sheet (`intervalMs == 0`, a single
     * non-time-indexed summary image, not scrub-previewable). The
     * `intervalMs > 0` filter IS the L0 exclusion -- no special-casing needed.
     * `null` when no level qualifies (e.g. only L0 is present).
     */
    fun previewLevel(): StoryboardLevel? =
        levels
            .filter { it.intervalMs > 0 && it.columns > 0 && it.rows > 0 && it.pageUrls.isNotEmpty() }
            .maxByOrNull { it.tileHeightPx }
}

/**
 * One time-indexed storyboard level: a grid of [columns] x [rows] tiles per
 * sprite sheet, sampled every [intervalMs] across [totalFrames] frames, spread
 * across [pageUrls] (one fully-resolved sheet URL per sprite page).
 */
data class StoryboardLevel(
    val tileWidthPx: Int,
    val tileHeightPx: Int,
    val columns: Int,
    val rows: Int,
    val intervalMs: Long,
    val totalFrames: Int,
    val pageUrls: List<String>,
) {

    /**
     * Resolves the sprite tile that previews [positionMs] (REQ-SB4):
     * `frameIndex = min(floor(positionMs / intervalMs), totalFrames - 1)`,
     * `framesPerSprite = columns * rows`, `spriteIndex = floorDiv(frameIndex,
     * framesPerSprite)`, `relativeFrame = frameIndex % framesPerSprite`,
     * `row = floorDiv(relativeFrame, columns)`, `col = relativeFrame % columns`
     * -- `col` MUST use [columns], never [rows] (design D2: NewPipe's own
     * `getFrameBoundsAt` uses `% framesPerPageY` for the column, which is
     * silently wrong on a non-square grid; this corrects that). Both
     * `frameIndex` (past [totalFrames]) and `spriteIndex` (past
     * [pageUrls]'s last index) are clamped, so a `positionMs` at or beyond
     * the video's duration resolves to the last real sheet instead of
     * addressing a nonexistent one.
     *
     * Requires `intervalMs > 0` (gate F7): unreachable in practice, since
     * [StoryboardSpec.previewLevel] already filters out any `intervalMs <= 0`
     * (L0 recap-sheet) level before a [StoryboardLevel] ever reaches here --
     * this is a documented reachability contract, not a real code path.
     */
    fun tileAt(positionMs: Long): StoryboardTile {
        require(intervalMs > 0) {
            "tileAt() requires an interval-indexed level (intervalMs > 0); " +
                "unreachable via StoryboardSpec.previewLevel()."
        }

        val framesPerSprite = columns * rows
        val frameIndex = (positionMs / intervalMs).coerceAtMost((totalFrames - 1).toLong()).toInt()
        val spriteIndex = (frameIndex / framesPerSprite).coerceAtMost(pageUrls.lastIndex)
        val relativeFrame = frameIndex % framesPerSprite
        val row = relativeFrame / columns
        val col = relativeFrame % columns

        return StoryboardTile(
            url = pageUrls[spriteIndex],
            srcXPx = col * tileWidthPx,
            srcYPx = row * tileHeightPx,
            widthPx = tileWidthPx,
            heightPx = tileHeightPx,
        )
    }
}

/** One resolved sprite-sheet crop: [url] is the sheet, the rest is the pixel rect within it. */
data class StoryboardTile(
    val url: String,
    val srcXPx: Int,
    val srcYPx: Int,
    val widthPx: Int,
    val heightPx: Int,
)
