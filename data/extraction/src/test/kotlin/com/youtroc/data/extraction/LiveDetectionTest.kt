package com.youtroc.data.extraction

import org.schabi.newpipe.extractor.stream.StreamType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure, no-network validation of the live/VOD routing predicate. `POST_LIVE_STREAM`
 * (an ended-broadcast recording with DVR itags) is deliberately NOT live — it must
 * keep flowing through the existing VOD `collectStreams` path.
 */
class LiveDetectionTest {

    @Test
    fun `LIVE_STREAM is live`() {
        assertTrue(StreamType.LIVE_STREAM.isLive())
    }

    @Test
    fun `AUDIO_LIVE_STREAM is live`() {
        assertTrue(StreamType.AUDIO_LIVE_STREAM.isLive())
    }

    @Test
    fun `POST_LIVE_STREAM is not live`() {
        assertFalse(StreamType.POST_LIVE_STREAM.isLive())
    }

    @Test
    fun `POST_LIVE_AUDIO_STREAM is not live`() {
        assertFalse(StreamType.POST_LIVE_AUDIO_STREAM.isLive())
    }

    @Test
    fun `VIDEO_STREAM is not live`() {
        assertFalse(StreamType.VIDEO_STREAM.isLive())
    }

    @Test
    fun `AUDIO_STREAM is not live`() {
        assertFalse(StreamType.AUDIO_STREAM.isLive())
    }
}
