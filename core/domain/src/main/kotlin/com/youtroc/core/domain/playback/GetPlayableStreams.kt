package com.youtroc.core.domain.playback

import com.youtroc.core.domain.stream.StreamProvider
import com.youtroc.core.domain.stream.StreamResult
import com.youtroc.core.domain.video.VideoId

/**
 * Application entry point for "play this video": resolves its streams through the
 * [StreamProvider] port.
 *
 * Intentionally thin today. It exists as the seam where playback business rules
 * (resume position, quality policy, SponsorBlock) will attach later, without ever
 * leaking into adapters or UI.
 */
class GetPlayableStreams(
    private val streamProvider: StreamProvider,
) {
    suspend operator fun invoke(videoId: VideoId): StreamResult =
        streamProvider.playableStreams(videoId)
}
