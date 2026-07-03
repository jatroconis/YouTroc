package com.youtroc.data.extraction.detail

import com.youtroc.core.domain.detail.DetailResult
import com.youtroc.core.domain.detail.VideoDetail
import com.youtroc.core.domain.video.VideoId

/**
 * Decorator: tries [primary] first and returns its result unchanged on
 * [DetailResult.Success] or [DetailResult.NotAvailable] -- a valid own-engine
 * outcome, even "this video isn't available", is never second-guessed
 * (mirrors [com.youtroc.data.extraction.search.FallbackVideoSearch]'s
 * Success/Empty split; there is no Empty case for detail). On
 * [DetailResult.Error]/[DetailResult.Offline] from [primary], delegates to
 * [fallback] and returns ITS result unmodified (no further fallback chain --
 * only two adapters are composed in this slice).
 *
 * Both [primary]/[fallback] stay behind the same [VideoDetail] port, so this
 * class has no knowledge of InnerTube/NewPipe specifics -- pure composition,
 * unit-testable with fakes, no network.
 */
class FallbackVideoDetail(
    private val primary: VideoDetail,
    private val fallback: VideoDetail,
) : VideoDetail {

    override suspend fun detail(videoId: VideoId): DetailResult =
        when (val result = primary.detail(videoId)) {
            is DetailResult.Success, DetailResult.NotAvailable -> result
            DetailResult.Offline, is DetailResult.Error -> fallback.detail(videoId)
        }
}
