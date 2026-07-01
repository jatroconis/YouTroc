package com.youtroc.core.domain.detail

import com.youtroc.core.domain.video.VideoId

/**
 * In-memory test double for the [VideoDetail] port. Replays a preconfigured
 * [DetailResult] and records the last videoId received — no network, no
 * Android. This is the whole point of a port: the domain is testable in
 * isolation.
 */
class FakeVideoDetail(
    private val result: DetailResult,
) : VideoDetail {

    var lastVideoId: VideoId? = null
        private set

    override suspend fun detail(videoId: VideoId): DetailResult {
        lastVideoId = videoId
        return result
    }
}
