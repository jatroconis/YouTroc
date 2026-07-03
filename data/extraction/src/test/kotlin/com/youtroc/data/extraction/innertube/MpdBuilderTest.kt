package com.youtroc.data.extraction.innertube

import org.w3c.dom.Element
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deterministic, network-free verification of [MpdBuilder]: pure
 * string-building, no I/O, no Android/Media3 type (D1) -- android_vr already
 * supplies `initRange`/`indexRange` inline, so no per-itag CDN probe (unlike
 * [com.youtroc.data.extraction.DashManifestAssembler]) is needed to build a
 * valid `<SegmentBase>`.
 */
class MpdBuilderTest {

    private fun videoFmt(
        itag: Int,
        type: String = "video/webm",
        codecs: String = "vp9",
        url: String = "https://googlevideo.example/videoplayback?itag=$itag",
    ) = Fmt(
        itag = itag,
        url = url,
        type = type,
        codecs = codecs,
        bandwidth = 5_000_000,
        width = 1920,
        height = 1080,
        fps = 30,
        initStart = 0,
        initEnd = 220,
        indexStart = 221,
        indexEnd = 893,
        audioChannels = null,
        audioSamplingRate = null,
    )

    private fun audioFmt(
        itag: Int = 140,
        url: String = "https://googlevideo.example/videoplayback?itag=$itag",
    ) = Fmt(
        itag = itag,
        url = url,
        type = "audio/mp4",
        codecs = "mp4a.40.2",
        bandwidth = 128_000,
        width = null,
        height = null,
        fps = null,
        initStart = 0,
        initEnd = 731,
        indexStart = 732,
        indexEnd = 1027,
        audioChannels = 2,
        audioSamplingRate = 44_100,
    )

    private fun parse(mpdXml: String) =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(InputSource(StringReader(mpdXml)))

    private fun adaptationSets(mpdXml: String): List<Element> {
        val period = parse(mpdXml).documentElement.childElements("Period").first()
        return period.childElements("AdaptationSet")
    }

    private fun Element.childElements(name: String): List<Element> =
        (0 until childNodes.length)
            .mapNotNull { childNodes.item(it) as? Element }
            .filter { it.tagName == name }

    @Test
    fun `builds one video AdaptationSet with mixed-codec Representations and one audio AdaptationSet`() {
        val mpd = MpdBuilder.buildMpd(
            video = listOf(videoFmt(313, type = "video/webm", codecs = "vp9"), videoFmt(137, type = "video/mp4", codecs = "avc1.640028")),
            audio = listOf(audioFmt()),
            durationMs = 213_000L,
        )

        val sets = adaptationSets(mpd)
        assertEquals(2, sets.size)

        val videoSet = sets.first { it.getAttribute("contentType") == "video" }
        val audioSet = sets.first { it.getAttribute("contentType") == "audio" }

        assertEquals(2, videoSet.childElements("Representation").size)
        assertEquals(1, audioSet.childElements("Representation").size)
    }

    @Test
    fun `SegmentBase indexRange and Initialization range match the Fmt's byte offsets`() {
        val mpd = MpdBuilder.buildMpd(video = listOf(videoFmt(313)), audio = listOf(audioFmt()), durationMs = 213_000L)

        val videoSet = adaptationSets(mpd).first { it.getAttribute("contentType") == "video" }
        val representation = videoSet.childElements("Representation").first()
        val segmentBase = representation.childElements("SegmentBase").first()
        val initialization = segmentBase.childElements("Initialization").first()

        assertEquals("221-893", segmentBase.getAttribute("indexRange"))
        assertEquals("0-220", initialization.getAttribute("range"))
    }

    @Test
    fun `mediaPresentationDuration is non-zero and derived from durationMs`() {
        val mpd = MpdBuilder.buildMpd(video = listOf(videoFmt(313)), audio = listOf(audioFmt()), durationMs = 213_000L)

        assertTrue(mpd.contains("mediaPresentationDuration=\"PT213S\""))
    }

    @Test
    fun `the whole MPD is well-formed XML`() {
        val mpd = MpdBuilder.buildMpd(video = listOf(videoFmt(313)), audio = listOf(audioFmt()), durationMs = 213_000L)

        // Throws SAXParseException if malformed -- reaching this line is the assertion.
        parse(mpd)
    }

    @Test
    fun `BaseURL escapes ampersands and never contains a raw ampersand (R3 BLOCKING)`() {
        val heavyUrl = "https://googlevideo.example/videoplayback?" +
            (1..39).joinToString("&") { "p$it=v$it" }
        assertEquals(38, heavyUrl.count { it == '&' })

        val mpd = MpdBuilder.buildMpd(video = listOf(videoFmt(313, url = heavyUrl)), audio = listOf(audioFmt()), durationMs = 213_000L)

        assertTrue(mpd.contains("&amp;"))
        // Every literal '&' in the document must be part of an entity (&amp; / &lt; / &gt; / &quot;),
        // never a raw, unescaped '&' -- which is exactly what would fail XML parsing.
        assertFalse(Regex("&(?!amp;|lt;|gt;|quot;|apos;)").containsMatchIn(mpd))
        parse(mpd) // must still parse successfully with the heavy-ampersand URL
    }

    @Test
    fun `zero durationMs throws instead of emitting an unparseable MPD (R5 BLOCKING)`() {
        assertFailsWith<IllegalArgumentException> {
            MpdBuilder.buildMpd(video = listOf(videoFmt(313)), audio = listOf(audioFmt()), durationMs = 0L)
        }
    }

    @Test
    fun `negative durationMs throws instead of emitting an unparseable MPD (R5 BLOCKING)`() {
        assertFailsWith<IllegalArgumentException> {
            MpdBuilder.buildMpd(video = listOf(videoFmt(313)), audio = listOf(audioFmt()), durationMs = -1L)
        }
    }
}
