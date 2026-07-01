package com.youtroc.data.extraction

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult as XmlStreamResult

/**
 * Merges NewPipe's per-itag, single-Representation DASH MPD fragments into
 * ONE multi-rendition, multi-codec manifest.
 *
 * NewPipe's `YoutubeXDashManifestCreator`s each build a COMPLETE, valid MPD
 * document for exactly one itag (one `<Period><AdaptationSet><Representation>`).
 * That is correct for a single format in isolation, but useless for adaptive
 * playback: ExoPlayer's `DashMediaSource` only treats `<Representation>`
 * elements sharing ONE `<AdaptationSet>` as alternative renditions it may pick
 * between — the exact mechanism REQ-9's ABR and REQ-10's codec-chain fallback
 * both need. This function re-parents every fragment's `<Representation>`
 * into two shared groups (one video `<AdaptationSet>`, one audio
 * `<AdaptationSet>`) inside a single combined `<MPD>`, using the first video
 * fragment as the structural template — duration/profiles/etc. are identical
 * across fragments for the same video since NewPipe derives them from the
 * same `StreamInfo`.
 *
 * Pure and total: never throws. Malformed input degrades to `null` so one bad
 * fragment cannot crash extraction — the caller falls back to
 * PROGRESSIVE/MERGED delivery ([com.youtroc.core.domain.playback.PlaybackSelectionPolicy]).
 */
internal object DashManifestMerger {

    fun merge(videoFragments: List<String>, audioFragments: List<String>): String? {
        if (videoFragments.isEmpty() || audioFragments.isEmpty()) return null

        return try {
            val templateDoc = parse(videoFragments.first())
            val period = templateDoc.documentElement.childElement("Period") ?: return null

            // The template's own single AdaptationSet gets rebuilt from every fragment.
            period.childElements("AdaptationSet").forEach(period::removeChild)

            val videoGroup = period.appendNewAdaptationSet(templateDoc, id = "0", contentType = "video")
            val audioGroup = period.appendNewAdaptationSet(templateDoc, id = "1", contentType = "audio")

            videoFragments.forEach { appendRepresentations(it, templateDoc, videoGroup) }
            audioFragments.forEach { appendRepresentations(it, templateDoc, audioGroup) }

            if (!videoGroup.hasChildNodes() || !audioGroup.hasChildNodes()) return null

            serialize(templateDoc)
        } catch (e: Exception) {
            null
        }
    }

    private fun appendRepresentations(fragmentXml: String, targetDoc: Document, targetGroup: Element) {
        val fragmentAdaptationSet = parse(fragmentXml).documentElement
            .childElement("Period")
            ?.childElement("AdaptationSet")
            ?: return
        val inheritedMimeType = fragmentAdaptationSet.getAttribute("mimeType").takeIf { it.isNotBlank() }

        fragmentAdaptationSet.childElements("Representation").forEach { representation ->
            if (inheritedMimeType != null && representation.getAttribute("mimeType").isBlank()) {
                representation.setAttribute("mimeType", inheritedMimeType)
            }
            targetGroup.appendChild(targetDoc.importNode(representation, true))
        }
    }

    private fun Element.appendNewAdaptationSet(doc: Document, id: String, contentType: String): Element =
        doc.createElement("AdaptationSet").also {
            it.setAttribute("id", id)
            it.setAttribute("contentType", contentType)
            it.setAttribute("segmentAlignment", "true")
            appendChild(it)
        }

    private fun parse(xml: String): Document =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))

    private fun serialize(doc: Document): String {
        val writer = StringWriter()
        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        }.transform(DOMSource(doc), XmlStreamResult(writer))
        return writer.toString()
    }

    private fun Element.childElement(name: String): Element? = childElements(name).firstOrNull()

    private fun Element.childElements(name: String): List<Element> =
        (0 until childNodes.length)
            .mapNotNull { childNodes.item(it) as? Element }
            .filter { it.tagName == name }
}
