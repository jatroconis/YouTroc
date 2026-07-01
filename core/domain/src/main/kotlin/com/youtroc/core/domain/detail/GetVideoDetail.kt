package com.youtroc.core.domain.detail

import com.youtroc.core.domain.video.VideoId

/**
 * Application entry point for "get this video's detail": resolves it through
 * the [VideoDetail] port.
 *
 * Intentionally thin today. It exists as the seam where detail business
 * rules (session-aware history, watch-progress carry-over — RF-VID-10/11)
 * will attach later, without ever leaking into adapters or UI.
 */
class GetVideoDetail(
    private val videoDetail: VideoDetail,
) {
    suspend operator fun invoke(videoId: VideoId): DetailResult = videoDetail.detail(videoId)
}
