package com.youtroc.core.domain.stream

import com.youtroc.core.domain.video.VideoId

/**
 * In-memory test double for the [StreamProvider] port. It records the last video
 * it was asked about and replays a preconfigured [StreamResult] — no network, no
 * Android. This is the whole point of a port: the domain is testable in isolation.
 */
class FakeStreamProvider(
    private val result: StreamResult,
) : StreamProvider {

    var lastRequested: VideoId? = null
        private set

    override suspend fun playableStreams(videoId: VideoId): StreamResult {
        lastRequested = videoId
        return result
    }
}
