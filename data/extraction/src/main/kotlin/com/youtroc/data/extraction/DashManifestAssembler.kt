package com.youtroc.data.extraction

import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.CreationException
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubePostLiveStreamDvrDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.Stream as NewPipeStream

/**
 * Assembles a single multi-rendition, multi-codec DASH MPD for [info] by
 * generating a per-itag manifest fragment via the matching NewPipe creator
 * (see [DashFragmentSourceSelector]) for every itag-adaptive video-only and
 * audio-only stream, then re-grouping them with [DashManifestMerger].
 *
 * Integration-only, by necessity: each fragment generation performs a real
 * HTTP call against the stream's CDN URL to read init/index segment metadata
 * (NewPipe's `getInitializationResponse`), so this cannot be meaningfully
 * unit-tested with fakes — the shape of a correctly-assembled manifest can
 * only be confirmed against real YouTube videos. It is exercised by
 * [NewPipeStreamProviderLiveTest] (opt-in, `YOUTROC_LIVE=1`) and MUST be
 * smoke-tested on-device (TCL 55C6K) before REQ-9/REQ-10 are treated as
 * closed — this is the design's PRIMARY open risk. The two pieces it
 * composes — [DashFragmentSourceSelector] (which creator to call) and
 * [DashManifestMerger] (how fragments get grouped) — are both pure and are
 * unit-tested.
 */
internal object DashManifestAssembler {

    fun assemble(info: StreamInfo): String? {
        val videoFragments = info.videoOnlyStreams.mapNotNull { it.fragmentOrNull(info) }
        val audioFragments = info.audioStreams.mapNotNull { it.fragmentOrNull(info) }
        return DashManifestMerger.merge(videoFragments, audioFragments)
    }

    private fun NewPipeStream.fragmentOrNull(info: StreamInfo): String? {
        val itagItem = itagItem ?: return null
        val url = content
        if (url.isNullOrBlank()) return null
        val source = DashFragmentSourceSelector.select(deliveryMethod, info.streamType, itagItem.targetDurationSec)
            ?: return null // delivery method cannot be turned into a DASH fragment (e.g. HLS/TORRENT)

        return try {
            when (source) {
                DashFragmentSource.OTF ->
                    YoutubeOtfDashManifestCreator.fromOtfStreamingUrl(url, itagItem, info.duration)

                DashFragmentSource.POST_LIVE_DVR ->
                    YoutubePostLiveStreamDvrDashManifestCreator.fromPostLiveStreamDvrStreamingUrl(
                        url,
                        itagItem,
                        itagItem.targetDurationSec,
                        info.duration,
                    )

                DashFragmentSource.PROGRESSIVE ->
                    YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(url, itagItem, info.duration)
            }
        } catch (e: CreationException) {
            null // this itag's fragment could not be generated — skip it, don't fail the whole extraction
        }
    }
}
