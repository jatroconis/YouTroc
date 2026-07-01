package com.youtroc.core.domain.stream

import com.youtroc.core.domain.video.VideoId

/**
 * Port: resolves the ad-free playable streams of a video from an external source
 * (YouTube's InnerTube API).
 *
 * The domain owns this contract; adapters in :data:extraction implement it. It
 * returns a typed [StreamResult] and must never throw — turning transport and
 * parsing failures into domain outcomes is the adapter's responsibility.
 */
interface StreamProvider {
    suspend fun playableStreams(videoId: VideoId): StreamResult
}
