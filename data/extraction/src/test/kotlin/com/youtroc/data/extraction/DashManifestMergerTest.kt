package com.youtroc.data.extraction

import org.w3c.dom.Element
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * NewPipe's `YoutubeXDashManifestCreator`s each build a COMPLETE, valid MPD
 * document for exactly ONE itag (one `<Period><AdaptationSet><Representation>`).
 * These fixtures mimic that per-itag shape (following the standard MPEG-DASH
 * vocabulary NewPipe's `YoutubeDashManifestCreatorsUtils` emits — Period,
 * AdaptationSet, Representation, SegmentBase); [DashManifestMerger] re-groups
 * them into ONE multi-rendition manifest without any network dependency, so
 * this is fully unit-testable.
 */
class DashManifestMergerTest {

    private fun fragment(id: String, mimeType: String, codecs: String, bandwidth: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" profiles="urn:mpeg:dash:profile:isoff-main:2011" type="static" mediaPresentationDuration="PT213S" minBufferTime="PT1.5S">
          <Period>
            <AdaptationSet id="0" contentType="${mimeType.substringBefore('/')}" mimeType="$mimeType" segmentAlignment="true">
              <Representation id="$id" codecs="$codecs" bandwidth="$bandwidth" width="1920" height="1080">
                <BaseURL>https://example.com/$id</BaseURL>
                <SegmentBase indexRange="1000-2000">
                  <Initialization range="0-999"/>
                </SegmentBase>
              </Representation>
            </AdaptationSet>
          </Period>
        </MPD>
    """.trimIndent()

    private fun parseAdaptationSets(mpdXml: String): List<Element> {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(InputSource(StringReader(mpdXml)))
        val period = doc.documentElement.childNodes
        val adaptationSets = mutableListOf<Element>()
        for (i in 0 until period.length) {
            val node = period.item(i)
            if (node is Element && node.tagName == "Period") {
                val children = node.childNodes
                for (j in 0 until children.length) {
                    val child = children.item(j)
                    if (child is Element && child.tagName == "AdaptationSet") adaptationSets.add(child)
                }
            }
        }
        return adaptationSets
    }

    private fun representations(adaptationSet: Element): List<Element> {
        val result = mutableListOf<Element>()
        val children = adaptationSet.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == "Representation") result.add(child)
        }
        return result
    }

    @Test
    fun `groups multiple video codec renditions into one AdaptationSet, separate from audio`() {
        val av1 = fragment("399", "video/mp4", "av01.0.05M.08", "500000")
        val vp9 = fragment("400", "video/webm", "vp09.00.41.08", "480000")
        val audio = fragment("140", "audio/mp4", "mp4a.40.2", "128000")

        val merged = DashManifestMerger.merge(videoFragments = listOf(av1, vp9), audioFragments = listOf(audio))

        requireNotNull(merged)
        val adaptationSets = parseAdaptationSets(merged)
        assertEquals(2, adaptationSets.size)

        val videoGroup = adaptationSets.first { it.getAttribute("contentType") == "video" }
        val audioGroup = adaptationSets.first { it.getAttribute("contentType") == "audio" }

        assertEquals(2, representations(videoGroup).size)
        assertEquals(1, representations(audioGroup).size)
    }

    @Test
    fun `every representation retains its own mimeType so mixed containers stay distinguishable`() {
        val av1 = fragment("399", "video/mp4", "av01.0.05M.08", "500000")
        val vp9 = fragment("400", "video/webm", "vp09.00.41.08", "480000")
        val audio = fragment("140", "audio/mp4", "mp4a.40.2", "128000")

        val merged = DashManifestMerger.merge(videoFragments = listOf(av1, vp9), audioFragments = listOf(audio))

        requireNotNull(merged)
        val videoGroup = parseAdaptationSets(merged).first { it.getAttribute("contentType") == "video" }
        val mimeTypes = representations(videoGroup).map { it.getAttribute("mimeType") }.toSet()

        assertEquals(setOf("video/mp4", "video/webm"), mimeTypes)
    }

    @Test
    fun `returns null when there are no video fragments`() {
        val audio = fragment("140", "audio/mp4", "mp4a.40.2", "128000")

        assertNull(DashManifestMerger.merge(videoFragments = emptyList(), audioFragments = listOf(audio)))
    }

    @Test
    fun `returns null when there are no audio fragments (never audio-less)`() {
        val av1 = fragment("399", "video/mp4", "av01.0.05M.08", "500000")

        assertNull(DashManifestMerger.merge(videoFragments = listOf(av1), audioFragments = emptyList()))
    }

    @Test
    fun `degrades to null instead of throwing on malformed fragment XML`() {
        val av1 = fragment("399", "video/mp4", "av01.0.05M.08", "500000")
        val malformed = "<not-even-xml"

        assertNull(DashManifestMerger.merge(videoFragments = listOf(av1, malformed), audioFragments = listOf(av1)))
    }

    @Test
    fun `preserves the template MPD's own attributes such as duration`() {
        val av1 = fragment("399", "video/mp4", "av01.0.05M.08", "500000")
        val audio = fragment("140", "audio/mp4", "mp4a.40.2", "128000")

        val merged = DashManifestMerger.merge(videoFragments = listOf(av1), audioFragments = listOf(audio))

        requireNotNull(merged)
        assertTrue(merged.contains("mediaPresentationDuration=\"PT213S\""))
    }
}
