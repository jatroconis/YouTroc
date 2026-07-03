package com.youtroc.data.extraction.innertube

/**
 * A single video-only or audio-only adaptive format, already stripped of
 * android_vr's JSON-string peculiarities (R2 BLOCKING): every numeric field
 * here is a real `Int`, extracted by [InnerTubePlayerMapping] from the raw
 * [PlayerFormat]. `type`/`codecs` are the two halves of `mimeType`
 * (`video/webm; codecs="vp9"` -> `type="video/webm"`, `codecs="vp9"`).
 */
internal data class Fmt(
    val itag: Int,
    val url: String,
    val type: String,
    val codecs: String,
    val bandwidth: Int,
    val width: Int?,
    val height: Int?,
    val fps: Int?,
    val initStart: Int,
    val initEnd: Int,
    val indexStart: Int,
    val indexEnd: Int,
    val audioChannels: Int?,
    val audioSamplingRate: Int?,
)

/**
 * Builds an own, inline DASH MPD from android_vr's `adaptiveFormats` (D1) --
 * pure, no I/O, no Android/Media3 type crosses this object. android_vr
 * already supplies `initRange`/`indexRange` inline, so (unlike
 * [com.youtroc.data.extraction.DashManifestAssembler]) no per-itag CDN probe
 * is needed to produce a valid `<SegmentBase>`.
 *
 * Mixed mp4/webm [Fmt]s for the SAME content type share ONE `<AdaptationSet>`
 * (D2) -- [com.youtroc.data.player.Media3MediaPlayer] already wires
 * `setAllowVideoMixedMimeTypeAdaptiveness(true)`, so ExoPlayer's
 * `DefaultTrackSelector` picks between them for real cross-codec ABR.
 *
 * Every [video]/[audio] entry is trusted to be well-formed -- dropping a
 * malformed `adaptiveFormats` entry (missing `initRange`/`indexRange`) is
 * [InnerTubePlayerMapping]'s job, upstream of this call, since [Fmt]'s byte-
 * range fields are non-nullable.
 */
internal object MpdBuilder {

    fun buildMpd(video: List<Fmt>, audio: List<Fmt>, durationMs: Long): String {
        // R5 (BLOCKING): a missing/zero duration would produce an MPD Media3's
        // DashManifestParser rejects outright. Failing loudly here is defense
        // in depth -- the caller (InnerTubePlayerMapping) is expected to route
        // a non-positive duration to StreamResult.Error BEFORE ever reaching
        // this call, never invoking buildMpd with one.
        require(durationMs > 0) { "durationMs must be > 0 to build a parseable MPD, was $durationMs" }

        val durationSec = (durationMs + 500) / 1000 // rounded to the nearest whole second
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" type=\"static\" ")
            append("profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\" ")
            append("minBufferTime=\"PT1.5S\" mediaPresentationDuration=\"PT${durationSec}S\">")
            append("<Period>")
            appendAdaptationSet("video", video)
            appendAdaptationSet("audio", audio)
            append("</Period>")
            append("</MPD>")
        }
    }

    private fun StringBuilder.appendAdaptationSet(contentType: String, formats: List<Fmt>) {
        append("<AdaptationSet contentType=\"$contentType\" segmentAlignment=\"true\">")
        formats.forEach { appendRepresentation(contentType, it) }
        append("</AdaptationSet>")
    }

    private fun StringBuilder.appendRepresentation(contentType: String, fmt: Fmt) {
        append("<Representation id=\"${fmt.itag}\" mimeType=\"${fmt.type}\" codecs=\"${fmt.codecs}\" bandwidth=\"${fmt.bandwidth}\"")
        if (contentType == "video") {
            fmt.width?.let { append(" width=\"$it\"") }
            fmt.height?.let { append(" height=\"$it\"") }
            fmt.fps?.let { append(" frameRate=\"$it\"") }
        }
        append(">")
        append("<BaseURL>${fmt.url.xmlEscape()}</BaseURL>")
        if (contentType == "audio") {
            fmt.audioChannels?.let {
                append(
                    "<AudioChannelConfiguration " +
                        "schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\" value=\"$it\"/>",
                )
            }
        }
        append("<SegmentBase indexRange=\"${fmt.indexStart}-${fmt.indexEnd}\">")
        append("<Initialization range=\"${fmt.initStart}-${fmt.initEnd}\"/>")
        append("</SegmentBase>")
        append("</Representation>")
    }
}

/**
 * XML-escapes `& < > "` -- googlevideo `BaseURL`s carry ~37 query params
 * joined by `&` (R3 BLOCKING); an unescaped `&` makes the MPD invalid XML
 * and Media3's [androidx.media3.exoplayer.dash.manifest.DashManifestParser]
 * refuses to parse it. Order matters: `&` MUST be replaced FIRST, or the
 * `&amp;`/`&lt;`/... entities this function itself just produced would be
 * double-escaped into `&amp;amp;`.
 */
private fun String.xmlEscape(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
