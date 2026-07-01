package com.youtroc.core.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoQualityCatalogTest {

    @Test
    fun `dedupes renditions by height across codec groups and sorts descending`() {
        val renditions = listOf(
            VideoRendition(heightPx = 1080, widthPx = 1920, bitrate = 6_000_000), // AV1
            VideoRendition(heightPx = 1080, widthPx = 1920, bitrate = 8_000_000), // VP9
            VideoRendition(heightPx = 720, widthPx = 1280, bitrate = 3_000_000), // H264
        )

        val catalog = VideoQualityCatalog.from(renditions)

        assertEquals(
            listOf(
                VideoQuality(id = "h1080", label = "1080p", heightPx = 1080, bitrate = 8_000_000),
                VideoQuality(id = "h720", label = "720p", heightPx = 720, bitrate = 3_000_000),
            ),
            catalog,
        )
    }

    @Test
    fun `drops renditions with a non-positive height`() {
        val renditions = listOf(
            VideoRendition(heightPx = 1080, widthPx = 1920, bitrate = 6_000_000),
            VideoRendition(heightPx = 0, widthPx = 0, bitrate = null),
            VideoRendition(heightPx = -1, widthPx = -1, bitrate = null),
            VideoRendition(heightPx = 720, widthPx = 1280, bitrate = 3_000_000),
        )

        val catalog = VideoQualityCatalog.from(renditions)

        assertEquals(listOf(1080, 720), catalog.map { it.heightPx })
    }

    @Test
    fun `bitrate is the max within its height group`() {
        val renditions = listOf(
            VideoRendition(heightPx = 1080, widthPx = 1920, bitrate = 4_000_000),
            VideoRendition(heightPx = 1080, widthPx = 1920, bitrate = 9_000_000),
            VideoRendition(heightPx = 1080, widthPx = 1920, bitrate = 6_000_000),
            VideoRendition(heightPx = 480, widthPx = 854, bitrate = 1_000_000),
        )

        val catalog = VideoQualityCatalog.from(renditions)

        assertEquals(9_000_000, catalog.first { it.heightPx == 1080 }.bitrate)
    }

    @Test
    fun `a single distinct height collapses to an empty catalog (Auto-only)`() {
        val renditions = listOf(
            VideoRendition(heightPx = 1080, widthPx = 1920, bitrate = 6_000_000),
            VideoRendition(heightPx = 1080, widthPx = 1920, bitrate = 6_500_000), // same height, different codec group
        )

        assertTrue(VideoQualityCatalog.from(renditions).isEmpty())
    }

    @Test
    fun `no renditions collapses to an empty catalog (Auto-only)`() {
        assertTrue(VideoQualityCatalog.from(emptyList()).isEmpty())
    }
}
