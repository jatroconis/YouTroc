package com.youtroc.feature.catalog

import com.youtroc.core.domain.catalog.CatalogResult
import com.youtroc.core.domain.catalog.VideoCatalog
import kotlinx.coroutines.delay

/**
 * Local in-memory test double for the domain [VideoCatalog] port. Test source
 * sets aren't shared across Gradle modules, so `:feature:catalog` cannot see
 * `:core:domain`'s own test-only `FakeVideoCatalog` — this mirrors
 * `:feature:playback`'s `FakeWatchProgressStore` local-fixture pattern.
 *
 * [result] is mutable (`var`, not `val`) so a single instance can simulate a
 * changed backend answer between [HomeViewModel.load] calls (see the retry
 * test) — [com.youtroc.core.domain.catalog.GetHomeFeed] wrapping this fake is
 * REAL, never faked itself.
 *
 * [trending] suspends via a 1ms virtual [delay] (rather than returning
 * immediately) so [HomeViewModel]'s transient `Loading` state is a REAL,
 * externally observable suspension point under `StandardTestDispatcher` —
 * `TestCoroutineScheduler.runCurrent()` stops right at it, letting tests
 * assert `Loading` before advancing time to the final result.
 */
class FakeVideoCatalog(var result: CatalogResult) : VideoCatalog {
    override suspend fun trending(): CatalogResult {
        delay(1)
        return result
    }
}
