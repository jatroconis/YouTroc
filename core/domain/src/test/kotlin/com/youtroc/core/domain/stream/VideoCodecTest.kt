package com.youtroc.core.domain.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoCodecTest {

    @Test
    fun `prefers AV1 over VP9 over H264 over anything else`() {
        assertTrue(VideoCodec.AV1 < VideoCodec.VP9)
        assertTrue(VideoCodec.VP9 < VideoCodec.H264)
        assertTrue(VideoCodec.H264 < VideoCodec.OTHER)
    }

    @Test
    fun `declares entries in hardware decode preference order`() {
        assertEquals(
            listOf(VideoCodec.AV1, VideoCodec.VP9, VideoCodec.H264, VideoCodec.OTHER),
            VideoCodec.entries,
        )
    }
}
