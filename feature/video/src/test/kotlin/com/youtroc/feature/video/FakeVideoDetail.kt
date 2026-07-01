package com.youtroc.feature.video

import com.youtroc.core.domain.detail.DetailResult
import com.youtroc.core.domain.detail.VideoDetail
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.delay

/**
 * Local in-memory test double for the domain [VideoDetail] port. Test source
 * sets aren't shared across Gradle modules, so `:feature:video` cannot see
 * `:core:domain`'s own test-only `FakeVideoDetail` — mirrors
 * `:feature:search`'s local `FakeVideoSearch` fixture pattern.
 *
 * [result] is mutable (`var`) so a single instance can simulate a changed
 * backend answer between calls (retry test) — the
 * [com.youtroc.core.domain.detail.GetVideoDetail] wrapping this fake is REAL,
 * never faked itself.
 *
 * [detail] suspends via a 1ms virtual [delay] (rather than returning
 * immediately) so [DetailViewModel]'s transient `Loading` state is a REAL,
 * externally observable suspension point under `StandardTestDispatcher` —
 * `TestCoroutineScheduler.runCurrent()` stops right at it, letting tests
 * assert `Loading` before advancing time to the final result (mirrors
 * `:feature:catalog`'s `FakeVideoCatalog`).
 */
class FakeVideoDetail(var result: DetailResult) : VideoDetail {

    var callCount: Int = 0
        private set

    var lastVideoId: VideoId? = null
        private set

    override suspend fun detail(videoId: VideoId): DetailResult {
        callCount++
        lastVideoId = videoId
        delay(1)
        return result
    }
}
