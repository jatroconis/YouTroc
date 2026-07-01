package com.youtroc.data.extraction

import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamType

/** Which NewPipe DASH manifest creator produces a correct fragment for one itag stream. */
internal enum class DashFragmentSource { PROGRESSIVE, OTF, POST_LIVE_DVR }

/**
 * Decides whether a stream is DASH-assembly-eligible and, if so, which of
 * NewPipe's three `YoutubeXDashManifestCreator`s must build its per-itag
 * manifest fragment. Pure, total, and the single seam both questions go
 * through — kept together because eligibility and routing are the same
 * per-stream decision.
 *
 * **Eligibility ([DeliveryMethod]):** NewPipe marks OTF and post-live-DVR
 * renditions [DeliveryMethod.DASH], but marks ORDINARY direct-URL adaptive
 * VOD renditions — the common case — [DeliveryMethod.PROGRESSIVE_HTTP].
 * BOTH are DASH-assembly-eligible: `YoutubeProgressiveDashManifestCreator`
 * exists precisely to build a DASH fragment from a `PROGRESSIVE_HTTP`
 * itag's direct URL. Only [DeliveryMethod.HLS]/[DeliveryMethod.SS]/
 * [DeliveryMethod.TORRENT] cannot be turned into a DASH fragment at all.
 * Filtering on `DASH` alone silently drops the common case and DASH
 * assembly (and therefore ABR/codec-chain fallback, REQ-9/REQ-10) never
 * engages for normal videos — the bug this eligibility check fixes.
 *
 * **Routing (once eligible):** NewPipeExtractor v0.26.3 does not expose an
 * `isOtf`/`isPostLiveDvr` flag directly on [ItagItem] or `Stream` — this uses
 * the two public signals that DO correlate with the underlying YouTube
 * player-response fields: an ended-livestream recording reports
 * [StreamType.POST_LIVE_STREAM]/[StreamType.POST_LIVE_AUDIO_STREAM] on the
 * `StreamInfo`, and YouTube only populates `targetDurationSec` for
 * OTF-delivered formats (surfaced via [ItagItem.getTargetDurationSec]).
 * Best-effort by construction — this is exactly the assembly step flagged for
 * on-device validation in the design's risk register.
 */
internal object DashFragmentSourceSelector {

    fun select(deliveryMethod: DeliveryMethod, streamType: StreamType, targetDurationSec: Int): DashFragmentSource? = when {
        deliveryMethod != DeliveryMethod.DASH && deliveryMethod != DeliveryMethod.PROGRESSIVE_HTTP -> null

        streamType == StreamType.POST_LIVE_STREAM || streamType == StreamType.POST_LIVE_AUDIO_STREAM ->
            DashFragmentSource.POST_LIVE_DVR

        targetDurationSec != ItagItem.TARGET_DURATION_SEC_UNKNOWN -> DashFragmentSource.OTF

        else -> DashFragmentSource.PROGRESSIVE
    }
}
