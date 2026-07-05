package com.youtroc.data.extraction

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [storyboardSpecFrom] takes PRIMITIVE args, not a real NewPipe
 * [org.schabi.newpipe.extractor.stream.Frameset] (design D8c) -- a fake
 * drives the mapping directly here, so this test stays valid even if
 * NewPipeExtractor's own [org.schabi.newpipe.extractor.stream.Frameset]
 * accessor names ever change (this module's Open Question, resolved by
 * decompiling v0.26.3 at apply time -- see [Frameset.toStoryboardSpecOrNull]).
 */
class StoryboardFramesetMapperTest {

    @Test
    fun `maps Frameset primitive fields field-for-field into a single-level StoryboardSpec`() {
        val spec = storyboardSpecFrom(
            urls = listOf("https://cdn/L0/M0.jpg", "https://cdn/L0/M1.jpg"),
            frameWidth = 160,
            frameHeight = 90,
            totalCount = 200,
            durationPerFrame = 2000,
            framesPerPageX = 5,
            framesPerPageY = 5,
        )

        val level = requireNotNull(spec.previewLevel())
        assertEquals(160, level.tileWidthPx)
        assertEquals(90, level.tileHeightPx)
        assertEquals(5, level.columns)
        assertEquals(5, level.rows)
        assertEquals(2000L, level.intervalMs)
        assertEquals(200, level.totalFrames)
        assertEquals(listOf("https://cdn/L0/M0.jpg", "https://cdn/L0/M1.jpg"), level.pageUrls)
    }
}
