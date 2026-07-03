package com.youtroc.data.player

import android.net.Uri
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals

/**
 * R1 (BLOCKING GATE): proves a fixture-shaped MPD -- mirroring
 * `com.youtroc.data.extraction.innertube.MpdBuilder`'s own output form ([mpd])
 * -- is byte-acceptable to Media3's real
 * [androidx.media3.exoplayer.dash.manifest.DashManifestParser], the exact
 * parser [Media3MediaPlayer.dashMediaSource] feeds every own-built DASH
 * manifest to. `:data:player` has no dependency on `:data:extraction`
 * (hexagon boundary), so [mpd] is a self-contained literal, not an import of
 * `MpdBuilder` itself -- design-gate-review already confirmed the two forms
 * match structurally; this test is the executable proof.
 *
 * Needs Robolectric: `android.net.Uri`/`XmlPullParser` have no real
 * implementation on a plain JVM -- [RobolectricTestRunner] provides shadow
 * Android classes. Bridged into this module's JUnit5 `useJUnitPlatform()`
 * run via the JUnit4 + `junit-vintage-engine` combination (`@RunWith` is a
 * JUnit4 API).
 *
 * The DECISIVE proof remains the on-device play gate (see
 * `InnerTubeStreamProvider`'s KDoc / apply-progress) -- this test only
 * proves the manifest PARSES, not that ExoPlayer can actually buffer and
 * play the referenced googlevideo segments.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DashManifestParseGateTest {

    private val mpd = """
        <?xml version="1.0" encoding="UTF-8"?>
        <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" profiles="urn:mpeg:dash:profile:isoff-on-demand:2011" minBufferTime="PT1.5S" mediaPresentationDuration="PT213S">
          <Period>
            <AdaptationSet contentType="video" segmentAlignment="true">
              <Representation id="313" mimeType="video/webm" codecs="vp9" bandwidth="18076636" width="3840" height="2160" frameRate="25">
                <BaseURL>https://rr4---sn-vgqsrnld.googlevideo.com/videoplayback?itag=313&amp;id=abc&amp;expire=123</BaseURL>
                <SegmentBase indexRange="221-893">
                  <Initialization range="0-220"/>
                </SegmentBase>
              </Representation>
              <Representation id="137" mimeType="video/mp4" codecs="avc1.640028" bandwidth="4334157" width="1920" height="1080" frameRate="25">
                <BaseURL>https://rr4---sn-vgqsrnld.googlevideo.com/videoplayback?itag=137&amp;id=abc&amp;expire=123</BaseURL>
                <SegmentBase indexRange="742-1229">
                  <Initialization range="0-741"/>
                </SegmentBase>
              </Representation>
            </AdaptationSet>
            <AdaptationSet contentType="audio" segmentAlignment="true">
              <Representation id="139" mimeType="audio/mp4" codecs="mp4a.40.5" bandwidth="50152">
                <BaseURL>https://rr4---sn-vgqsrnld.googlevideo.com/videoplayback?itag=139&amp;id=abc&amp;expire=123</BaseURL>
                <AudioChannelConfiguration schemeIdUri="urn:mpeg:dash:23003:3:audio_channel_configuration:2011" value="2"/>
                <SegmentBase indexRange="732-1027">
                  <Initialization range="0-731"/>
                </SegmentBase>
              </Representation>
            </AdaptationSet>
          </Period>
        </MPD>
    """.trimIndent()

    @Test
    fun `Media3's real DashManifestParser accepts the own-built MPD form`() {
        val manifest = DashManifestParser().parse(
            Uri.EMPTY,
            ByteArrayInputStream(mpd.toByteArray(StandardCharsets.UTF_8)),
        )

        assertEquals(1, manifest.periodCount)
        val period = manifest.getPeriod(0)
        assertEquals(2, period.adaptationSets.size)

        val videoSet = period.adaptationSets.first { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO }
        val audioSet = period.adaptationSets.first { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }

        assertEquals(2, videoSet.representations.size)
        assertEquals(1, audioSet.representations.size)

        val topVideoRepresentation = videoSet.representations.first { it.format.id == "313" }
        val segmentBase = topVideoRepresentation.presentationTimeOffsetUs // touches parsed segment data without over-asserting internals

        // The parsed manifest's total duration must round-trip mediaPresentationDuration="PT213S".
        assertEquals(213_000L, manifest.durationMs)
        // Non-null/no-throw access above is itself part of the proof: a parser
        // that rejected the manifest never reaches this line.
        assertEquals(true, segmentBase >= 0L)
    }
}
