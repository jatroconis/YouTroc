package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.stream.HdrFormat
import com.youtroc.core.domain.stream.StreamKind
import com.youtroc.core.domain.stream.StreamResult
import com.youtroc.core.domain.stream.VideoCodec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    // ---- HDR: colorInfo deserialization (REQ-H1) + toHdrFormat() mapping (REQ-H2) ----
    // Inline JSON literals, not the shared player_android_vr.json fixture -- that real
    // capture (dQw4w9WgXcQ) is an SDR video and carries no colorInfo at all.

    @Test
    fun `colorInfo with SMPTEST2084 transfer characteristics round-trips through the DTO`() {
        val json = """
            {"itag":313,"colorInfo":{"primaries":"COLOR_PRIMARIES_BT2020","transferCharacteristics":"COLOR_TRANSFER_CHARACTERISTICS_SMPTEST2084","matrixCoefficients":"COLOR_MATRIX_COEFFICIENTS_BT2020_NCL"}}
        """.trimIndent()

        val format: PlayerFormat = lenientJson.decodeFromString(json)

        assertNotNull(format.colorInfo)
        assertEquals("COLOR_PRIMARIES_BT2020", format.colorInfo?.primaries)
        assertEquals("COLOR_TRANSFER_CHARACTERISTICS_SMPTEST2084", format.colorInfo?.transferCharacteristics)
        assertEquals("COLOR_MATRIX_COEFFICIENTS_BT2020_NCL", format.colorInfo?.matrixCoefficients)
    }

    @Test
    fun `colorInfo with ARIB_STD_B67 transfer characteristics round-trips through the DTO`() {
        val json = """
            {"itag":313,"colorInfo":{"primaries":"COLOR_PRIMARIES_BT2020","transferCharacteristics":"COLOR_TRANSFER_CHARACTERISTICS_ARIB_STD_B67"}}
        """.trimIndent()

        val format: PlayerFormat = lenientJson.decodeFromString(json)

        assertNotNull(format.colorInfo)
        assertEquals("COLOR_TRANSFER_CHARACTERISTICS_ARIB_STD_B67", format.colorInfo?.transferCharacteristics)
    }

    @Test
    fun `omitted colorInfo decodes to null, never fabricated`() {
        val json = """{"itag":18}"""

        val format: PlayerFormat = lenientJson.decodeFromString(json)

        assertNull(format.colorInfo)
    }

    @Test
    fun `toHdrFormat maps a SMPTEST2084 transfer characteristic to HDR10`() {
        val colorInfo = ColorInfo(transferCharacteristics = "COLOR_TRANSFER_CHARACTERISTICS_SMPTEST2084")

        assertEquals(HdrFormat.HDR10, colorInfo.toHdrFormat())
    }

    @Test
    fun `toHdrFormat maps an ARIB_STD_B67 transfer characteristic to HLG`() {
        val colorInfo = ColorInfo(transferCharacteristics = "COLOR_TRANSFER_CHARACTERISTICS_ARIB_STD_B67")

        assertEquals(HdrFormat.HLG, colorInfo.toHdrFormat())
    }

    @Test
    fun `toHdrFormat maps a null colorInfo to SDR`() {
        val colorInfo: ColorInfo? = null

        assertEquals(HdrFormat.SDR, colorInfo.toHdrFormat())
    }

    @Test
    fun `toHdrFormat maps an unknown or BT709 transfer characteristic to SDR, never throws`() {
        val bt709 = ColorInfo(transferCharacteristics = "COLOR_TRANSFER_CHARACTERISTICS_BT709")
        val garbage = ColorInfo(transferCharacteristics = "not-a-real-value")

        assertEquals(HdrFormat.SDR, bt709.toHdrFormat())
        assertEquals(HdrFormat.SDR, garbage.toHdrFormat())
    }

    @Test
    fun `HDR10 colorInfo maps through toDomainStreamOrNull into Stream hdr`() {
        val json = """
            {"itag":313,"url":"https://cdn/hdr","mimeType":"video/webm; codecs=\"vp09.02.51.10\"","bitrate":18076636,
             "colorInfo":{"primaries":"COLOR_PRIMARIES_BT2020","transferCharacteristics":"COLOR_TRANSFER_CHARACTERISTICS_SMPTEST2084"}}
        """.trimIndent()
        val format: PlayerFormat = lenientJson.decodeFromString(json)

        val stream = format.toDomainStreamOrNull(StreamKind.VIDEO_ONLY)

        assertNotNull(stream)
        assertEquals(HdrFormat.HDR10, stream?.hdr)
    }

    @Test
    fun `SDR format with no colorInfo maps through toDomainStreamOrNull into Stream hdr SDR`() {
        val json = """{"itag":18,"url":"https://cdn/sdr","mimeType":"video/mp4; codecs=\"avc1.42001E\"","bitrate":500000}"""
        val format: PlayerFormat = lenientJson.decodeFromString(json)

        val stream = format.toDomainStreamOrNull(StreamKind.MUXED)

        assertNotNull(stream)
        assertEquals(HdrFormat.SDR, stream?.hdr)
    }

    // ---- REQ-SB1/SB2: storyboards.playerStoryboardSpecRenderer.spec threading ----
    // Inline JSON literals (like the colorInfo section above): the shared
    // player_android_vr.json fixture predates this capability and carries no
    // storyboards node at all.

    private fun playerResponseJson(storyboardsNode: String): String = """
        {
          "playabilityStatus": {"status": "OK"},
          "videoDetails": {"lengthSeconds": "213"},
          $storyboardsNode
          "streamingData": {
            "adaptiveFormats": [
              {"itag":313,"url":"https://cdn/video","mimeType":"video/webm; codecs=\"vp09.02.51.10\"","bitrate":18076636,
               "width":3840,"height":2160,"initRange":{"start":"0","end":"220"},"indexRange":{"start":"221","end":"893"},
               "approxDurationMs":"213040"},
              {"itag":139,"url":"https://cdn/audio","mimeType":"audio/mp4; codecs=\"mp4a.40.5\"","bitrate":48000,
               "initRange":{"start":"0","end":"31"},"indexRange":{"start":"32","end":"570"},"audioSampleRate":"22050",
               "approxDurationMs":"213040"}
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `toStreamResult parses a present storyboards spec into PlayableStreams storyboard`() {
        val storyboardsNode = """
            "storyboards": {"playerStoryboardSpecRenderer": {"spec":
              "https://i.ytimg.com/sb/vid/storyboard3_L${'$'}L/${'$'}N.jpg?sqp=-e30%2C|48#27#1#1#1#0#M${'$'}M#rs${'$'}AOn4CLA1|160#90#107#5#5#2000#M${'$'}M#rs${'$'}AOn4CLA3"
            }},
        """.trimIndent()
        val response: PlayerResponse = lenientJson.decodeFromString(playerResponseJson(storyboardsNode))

        val result = response.toStreamResult()

        assertIs<StreamResult.Success>(result)
        val storyboard = requireNotNull(result.streams.storyboard)
        assertEquals(160, requireNotNull(storyboard.previewLevel()).tileWidthPx) // L2 wins over L0
    }

    @Test
    fun `toStreamResult resolves storyboard to null, without failing Success, when the spec is malformed`() {
        val storyboardsNode = """"storyboards": {"playerStoryboardSpecRenderer": {"spec": "not-a-real-spec"}},"""
        val response: PlayerResponse = lenientJson.decodeFromString(playerResponseJson(storyboardsNode))

        val result = response.toStreamResult()

        assertIs<StreamResult.Success>(result)
        assertNull(result.streams.storyboard)
    }

    @Test
    fun `toStreamResult resolves storyboard to null when the storyboards node is entirely absent`() {
        val result = response.toStreamResult() // shared fixture carries no storyboards node

        assertIs<StreamResult.Success>(result)
        assertNull(result.streams.storyboard)
    }
}
