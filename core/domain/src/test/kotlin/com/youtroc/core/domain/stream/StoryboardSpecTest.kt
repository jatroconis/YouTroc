package com.youtroc.core.domain.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoryboardSpecTest {

    // ---- REQ-SB3: storyboard level selection policy ----

    @Test
    fun `previewLevel prefers L2 over L1 when both are present`() {
        val l1 = level(tileWidthPx = 90, tileHeightPx = 45, columns = 5, rows = 5, intervalMs = 5000, totalFrames = 40)
        val l2 = level(tileWidthPx = 160, tileHeightPx = 90, columns = 5, rows = 5, intervalMs = 2000, totalFrames = 200)

        val spec = StoryboardSpec(listOf(l1, l2))

        assertEquals(l2, spec.previewLevel())
    }

    @Test
    fun `previewLevel falls back to L1 when only L0 and L1 are present`() {
        val l0 = level(tileWidthPx = 90, tileHeightPx = 45, columns = 5, rows = 5, intervalMs = 0, totalFrames = 1)
        val l1 = level(tileWidthPx = 90, tileHeightPx = 45, columns = 5, rows = 5, intervalMs = 5000, totalFrames = 40)

        val spec = StoryboardSpec(listOf(l0, l1))

        assertEquals(l1, spec.previewLevel())
    }

    @Test
    fun `previewLevel is null when only L0 is present, excluding the non-time-indexed recap sheet`() {
        val l0 = level(tileWidthPx = 90, tileHeightPx = 45, columns = 5, rows = 5, intervalMs = 0, totalFrames = 1)

        val spec = StoryboardSpec(listOf(l0))

        assertNull(spec.previewLevel())
    }

    // ---- REQ-SB4: tile-index math ----

    @Test
    fun `tileAt resolves frameIndex spriteIndex row col and pixel offset for an L2 160x90 5x5 grid`() {
        val l2 = level(tileWidthPx = 160, tileHeightPx = 90, columns = 5, rows = 5, intervalMs = 2000, totalFrames = 200)

        val tile = l2.tileAt(positionMs = 137_000)

        assertEquals("https://cdn/M2.jpg", tile.url) // spriteIndex=2 -> pageUrls[2]
        assertEquals(480, tile.srcXPx) // col=3 * tileWidthPx=160
        assertEquals(270, tile.srcYPx) // row=3 * tileHeightPx=90
        assertEquals(160, tile.widthPx)
        assertEquals(90, tile.heightPx)
    }

    @Test
    fun `tileAt uses columns, not rows, to derive col on a non-square grid`() {
        // framesPerSprite = 10*5 = 50; frameIndex=23 -> spriteIndex=0, relativeFrame=23
        val nonSquare = level(tileWidthPx = 48, tileHeightPx = 27, columns = 10, rows = 5, intervalMs = 1000, totalFrames = 50)

        val tile = nonSquare.tileAt(positionMs = 23_000)

        assertEquals(27 * 2, tile.srcYPx) // row = floorDiv(relativeFrame=23, cols=10) = 2
        assertEquals(48 * 3, tile.srcXPx) // col = 23 % cols=10 = 3, confirming col uses cols, not rows
    }

    @Test
    fun `tileAt clamps frameIndex to the last frame and spriteIndex to the last page instead of overrunning`() {
        val l2 = level(tileWidthPx = 160, tileHeightPx = 90, columns = 5, rows = 5, intervalMs = 2000, totalFrames = 200)

        val tile = l2.tileAt(positionMs = 999_999)

        // frameIndex clamps to 199 (not 200, which would address a nonexistent M8 sheet):
        // spriteIndex = floorDiv(199, 25) = 7 -> pageUrls[7], covering frames 175-199.
        assertEquals("https://cdn/M7.jpg", tile.url)
        assertEquals(4 * 90, tile.srcYPx) // row = floorDiv(199 % 25 = 24, cols=5) = 4
        assertEquals(4 * 160, tile.srcXPx) // col = 24 % 5 = 4
    }

    private fun level(
        tileWidthPx: Int,
        tileHeightPx: Int,
        columns: Int,
        rows: Int,
        intervalMs: Long,
        totalFrames: Int,
    ): StoryboardLevel {
        val framesPerSprite = columns * rows
        val pageCount = (totalFrames + framesPerSprite - 1) / framesPerSprite
        return StoryboardLevel(
            tileWidthPx = tileWidthPx,
            tileHeightPx = tileHeightPx,
            columns = columns,
            rows = rows,
            intervalMs = intervalMs,
            totalFrames = totalFrames,
            pageUrls = (0 until pageCount).map { "https://cdn/M$it.jpg" },
        )
    }
}
