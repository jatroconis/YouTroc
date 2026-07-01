package com.youtroc.data.extraction

import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * NewPipeExtractor v0.26.3 does not expose an `isOtf`/`isPostLiveDvr` flag
 * directly on a stream, so [DashFragmentSourceSelector] infers it from the two
 * public signals that correlate with YouTube's player-response fields: the
 * [StreamInfo][org.schabi.newpipe.extractor.stream.StreamInfo]'s
 * [StreamType], and whether `targetDurationSec` was populated for the itag.
 * This is exactly the assembly decision flagged for on-device validation in
 * the design's risk register.
 *
 * [DeliveryMethod] eligibility is folded into the same pure decision:
 * NewPipe marks OTF/post-live-DVR renditions [DeliveryMethod.DASH], but marks
 * ordinary direct-URL adaptive VOD renditions (the common case)
 * [DeliveryMethod.PROGRESSIVE_HTTP] — BOTH are DASH-assembly-eligible (see
 * [YoutubeProgressiveDashManifestCreator][org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator]),
 * only [DeliveryMethod.HLS]/[DeliveryMethod.SS]/[DeliveryMethod.TORRENT] are not.
 */
class DashFragmentSourceSelectorTest {

    @Test
    fun `a normal VOD itag with no target duration selects PROGRESSIVE`() {
        assertEquals(
            DashFragmentSource.PROGRESSIVE,
            DashFragmentSourceSelector.select(
                DeliveryMethod.DASH,
                StreamType.VIDEO_STREAM,
                ItagItem.TARGET_DURATION_SEC_UNKNOWN,
            ),
        )
    }

    @Test
    fun `a known targetDurationSec selects OTF`() {
        assertEquals(
            DashFragmentSource.OTF,
            DashFragmentSourceSelector.select(DeliveryMethod.DASH, StreamType.VIDEO_STREAM, 5),
        )
    }

    @Test
    fun `a post-live-stream type selects POST_LIVE_DVR regardless of target duration`() {
        assertEquals(
            DashFragmentSource.POST_LIVE_DVR,
            DashFragmentSourceSelector.select(
                DeliveryMethod.DASH,
                StreamType.POST_LIVE_STREAM,
                ItagItem.TARGET_DURATION_SEC_UNKNOWN,
            ),
        )
    }

    @Test
    fun `a post-live-audio-stream type selects POST_LIVE_DVR`() {
        assertEquals(
            DashFragmentSource.POST_LIVE_DVR,
            DashFragmentSourceSelector.select(DeliveryMethod.DASH, StreamType.POST_LIVE_AUDIO_STREAM, 5),
        )
    }

    @Test
    fun `a PROGRESSIVE_HTTP direct-URL adaptive VOD stream is eligible and routes to PROGRESSIVE`() {
        // This IS the common case: NewPipe marks normal (non-OTF) direct-URL
        // adaptive VOD renditions PROGRESSIVE_HTTP, not DASH. Rejecting it
        // here means DASH assembly never runs for ordinary videos (BLOCKER #1).
        assertEquals(
            DashFragmentSource.PROGRESSIVE,
            DashFragmentSourceSelector.select(
                DeliveryMethod.PROGRESSIVE_HTTP,
                StreamType.VIDEO_STREAM,
                ItagItem.TARGET_DURATION_SEC_UNKNOWN,
            ),
        )
    }

    @Test
    fun `a PROGRESSIVE_HTTP stream with post-live-stream type still routes to POST_LIVE_DVR`() {
        assertEquals(
            DashFragmentSource.POST_LIVE_DVR,
            DashFragmentSourceSelector.select(
                DeliveryMethod.PROGRESSIVE_HTTP,
                StreamType.POST_LIVE_STREAM,
                ItagItem.TARGET_DURATION_SEC_UNKNOWN,
            ),
        )
    }

    @Test
    fun `an HLS delivery method is ineligible for DASH assembly`() {
        assertNull(
            DashFragmentSourceSelector.select(
                DeliveryMethod.HLS,
                StreamType.VIDEO_STREAM,
                ItagItem.TARGET_DURATION_SEC_UNKNOWN,
            ),
        )
    }

    @Test
    fun `a TORRENT delivery method is ineligible for DASH assembly`() {
        assertNull(
            DashFragmentSourceSelector.select(
                DeliveryMethod.TORRENT,
                StreamType.VIDEO_STREAM,
                ItagItem.TARGET_DURATION_SEC_UNKNOWN,
            ),
        )
    }
}
