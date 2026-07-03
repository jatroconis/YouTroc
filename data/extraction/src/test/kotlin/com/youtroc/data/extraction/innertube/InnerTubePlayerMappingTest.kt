package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.stream.StreamKind
import com.youtroc.core.domain.stream.StreamResult
import com.youtroc.core.domain.stream.VideoCodec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val lenientJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

private fun fixture(name: String): String =
    checkNotNull(object {}.javaClass.getResourceAsStream("/innertube/$name")) { "missing fixture $name" }
        .bufferedReader()
        .readText()

/**
 * Deterministic, network-free verification of the android_vr `player`
 * response decode shape against a real, live-captured fixture
 * ([fixture] `player_android_vr.json`, dQw4w9WgXcQ, captured 2026-07-02).
 *
 * R2 (BLOCKING): android_vr sends `initRange`/`indexRange.start/end`,
 * `contentLength`, `audioSampleRate`, and `videoDetails.lengthSeconds` as
 * JSON **strings** (e.g. `"221"`), not numbers -- kotlinx.serialization's
 * strict mode THROWS a [kotlinx.serialization.SerializationException] if
 * those DTO fields were declared `Int`/`Long`. [InnerTubePlayerModels]
 * declares them `String`; this test proves the fixture decodes without
 * throwing and that every such field parses cleanly to a number.
 */
class InnerTubePlayerMappingTest {

    private val response: PlayerResponse = lenientJson.decodeFromString(fixture("player_android_vr.json"))

    @Test
    fun `decodes without throwing and reports a healthy playability status`() {
        assertEquals("OK", response.playabilityStatus?.status)
    }

    @Test
    fun `native numeric fields decode as real Int, not String`() {
        val video = response.streamingData?.adaptiveFormats.orEmpty().first { it.itag == 313 }

        assertEquals(313, video.itag)
        assertEquals(3840, video.width)
        assertEquals(2160, video.height)
        assertEquals(25, video.fps)
        assertEquals(18076636, video.bitrate)
    }

    @Test
    fun `contentLength is a String that parses cleanly to Long (R2)`() {
        val video = response.streamingData?.adaptiveFormats.orEmpty().first { it.itag == 313 }

        assertNotNull(video.contentLength)
        assertEquals(358608461L, video.contentLength?.toLongOrNull())
    }

    @Test
    fun `audioSampleRate is a String that parses cleanly to Int (R2)`() {
        val audio = response.streamingData?.adaptiveFormats.orEmpty().first { it.itag == 139 }

        assertNotNull(audio.audioSampleRate)
        assertEquals(22050, audio.audioSampleRate?.toIntOrNull())
    }

    @Test
    fun `initRange and indexRange start-end are Strings that parse cleanly to Long (R2)`() {
        val video = response.streamingData?.adaptiveFormats.orEmpty().first { it.itag == 313 }

        assertNotNull(video.initRange)
        assertNotNull(video.indexRange)
        assertEquals(0L, video.initRange?.start?.toLongOrNull())
        assertEquals(220L, video.initRange?.end?.toLongOrNull())
        assertEquals(221L, video.indexRange?.start?.toLongOrNull())
        assertEquals(893L, video.indexRange?.end?.toLongOrNull())
    }

    @Test
    fun `videoDetails lengthSeconds is a String that parses cleanly to Long (R2)`() {
        assertEquals("213", response.videoDetails?.lengthSeconds)
        assertEquals(213L, response.videoDetails?.lengthSeconds?.toLongOrNull())
    }

    @Test
    fun `approxDurationMs is a String that parses cleanly to Long (R2)`() {
        val video = response.streamingData?.adaptiveFormats.orEmpty().first { it.itag == 313 }

        assertEquals("213040", video.approxDurationMs)
        assertEquals(213040L, video.approxDurationMs?.toLongOrNull())
    }

    @Test
    fun `fixture carries at least one video and one audio adaptive format`() {
        val adaptive = response.streamingData?.adaptiveFormats.orEmpty()

        assertTrue(adaptive.any { it.mimeType?.startsWith("video/") == true })
        assertTrue(adaptive.any { it.mimeType?.startsWith("audio/") == true })
    }

    @Test
    fun `muxed itag 18 format is present under formats, not adaptiveFormats`() {
        val muxed = response.streamingData?.formats.orEmpty()

        assertTrue(muxed.any { it.itag == 18 })
    }

    // ---- Phase 4: Fmt extraction + duration resolution + result mapping ----

    @Test
    fun `videoFmts and audioFmts split adaptiveFormats by mimeType, converting String ranges to Int`() {
        val sd = requireNotNull(response.streamingData)

        val video = sd.videoFmts()
        val audio = sd.audioFmts()

        assertEquals(3, video.size) // 313 (vp9), 401 (av1), 137 (avc1) in the trimmed fixture
        assertEquals(2, audio.size) // 139 (mp4a), 251 (opus)

        val vp9 = video.first { it.itag == 313 }
        assertEquals(0, vp9.initStart)
        assertEquals(220, vp9.initEnd)
        assertEquals(221, vp9.indexStart)
        assertEquals(893, vp9.indexEnd)
        assertEquals("video/webm", vp9.type)
        assertEquals("vp9", vp9.codecs)
    }

    @Test
    fun `Fmt maps to a domain Stream reusing toDomainVideoCodec`() {
        val vp9 = requireNotNull(response.streamingData).videoFmts().first { it.itag == 313 }

        val stream = vp9.toDomainStream(StreamKind.VIDEO_ONLY)

        assertEquals(StreamKind.VIDEO_ONLY, stream.kind)
        assertEquals(VideoCodec.VP9, stream.codec)
        assertEquals(2160, stream.heightPx)
        assertEquals("webm", stream.container)
    }

    @Test
    fun `duration resolution prefers max approxDurationMs over videoDetails lengthSeconds (SUGGESTED flip)`() {
        // The fixture's approxDurationMs values (~213040ms) and lengthSeconds
        // (213s = 213000ms) are close but not identical -- resolveDurationMs()
        // must pick the max approxDurationMs among adaptiveFormats, not the
        // videoDetails-derived value.
        val durationMs = response.resolveDurationMs()

        assertEquals(213_159L, durationMs) // max approxDurationMs across the fixture's adaptiveFormats
    }

    @Test
    fun `healthy fixture maps to Success carrying a DASH manifest`() {
        val result = response.toStreamResult()

        assertIs<StreamResult.Success>(result)
        val manifest = requireNotNull(result.streams.manifest)
        assertEquals(com.youtroc.core.domain.playback.PlaybackManifest.Kind.DASH, manifest.kind)
        assertTrue(result.streams.streams.isNotEmpty())
    }

    @Test
    fun `throttled fixture (empty adaptiveFormats) maps to Error, never a degraded Success (D5 BLOCKING)`() {
        val throttled: PlayerResponse = lenientJson.decodeFromString(fixture("player_android_vr_throttled.json"))

        val result = throttled.toStreamResult()

        assertIs<StreamResult.Error>(result)
    }

    @Test
    fun `unavailable-live fixture maps to NotAvailable, no live manifest code path runs (D4)`() {
        val unavailable: PlayerResponse = lenientJson.decodeFromString(fixture("player_android_vr_unavailable.json"))

        val result = unavailable.toStreamResult()

        assertEquals(StreamResult.NotAvailable, result)
    }
}
