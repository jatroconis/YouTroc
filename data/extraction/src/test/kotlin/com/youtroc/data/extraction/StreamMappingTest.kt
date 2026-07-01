package com.youtroc.data.extraction

import com.youtroc.core.domain.stream.Stream
import com.youtroc.core.domain.stream.StreamKind
import com.youtroc.core.domain.stream.VideoCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Deterministic, network-free verification of the two pure pieces that feed
 * [PlaybackManifest][com.youtroc.core.domain.playback.PlaybackManifest]
 * assembly: mapping NewPipe's raw codec strings to the domain vocabulary, and
 * picking the "best" URL among same-kind candidates for
 * [ManifestInputs][com.youtroc.core.domain.playback.ManifestInputs].
 */
class StreamMappingTest {

    @Test
    fun `maps an AV1 codec string to VideoCodec AV1`() {
        assertEquals(VideoCodec.AV1, toDomainVideoCodec("av01.0.05M.08"))
    }

    @Test
    fun `maps a VP9 codec string to VideoCodec VP9`() {
        assertEquals(VideoCodec.VP9, toDomainVideoCodec("vp09.00.41.08"))
        assertEquals(VideoCodec.VP9, toDomainVideoCodec("vp9"))
    }

    @Test
    fun `maps an H264 codec string to VideoCodec H264`() {
        assertEquals(VideoCodec.H264, toDomainVideoCodec("avc1.640028"))
        assertEquals(VideoCodec.H264, toDomainVideoCodec("h264"))
    }

    @Test
    fun `maps an unrecognized codec string to VideoCodec OTHER`() {
        assertEquals(VideoCodec.OTHER, toDomainVideoCodec("opus"))
    }

    @Test
    fun `bestByQualityOrNull returns null for an empty list`() {
        assertNull(emptyList<Stream>().bestByQualityOrNull())
    }

    @Test
    fun `bestByQualityOrNull prefers the higher-preference codec`() {
        val av1 = Stream(url = "https://cdn/av1", container = "webm", kind = StreamKind.VIDEO_ONLY, codec = VideoCodec.AV1, heightPx = 720)
        val h264 = Stream(url = "https://cdn/h264", container = "mp4", kind = StreamKind.VIDEO_ONLY, codec = VideoCodec.H264, heightPx = 1080)

        assertEquals(av1, listOf(h264, av1).bestByQualityOrNull())
    }

    @Test
    fun `bestByQualityOrNull prefers taller resolution when codec ties`() {
        val short = Stream(url = "https://cdn/short", container = "mp4", kind = StreamKind.VIDEO_ONLY, codec = VideoCodec.AV1, heightPx = 720)
        val tall = Stream(url = "https://cdn/tall", container = "mp4", kind = StreamKind.VIDEO_ONLY, codec = VideoCodec.AV1, heightPx = 1080)

        assertEquals(tall, listOf(short, tall).bestByQualityOrNull())
    }

    @Test
    fun `bestByQualityOrNull prefers higher bitrate when codec and height tie`() {
        val low = Stream(url = "https://cdn/low", container = "mp4", kind = StreamKind.AUDIO_ONLY, bitrateBps = 128_000)
        val high = Stream(url = "https://cdn/high", container = "mp4", kind = StreamKind.AUDIO_ONLY, bitrateBps = 256_000)

        assertEquals(high, listOf(low, high).bestByQualityOrNull())
    }
}
