package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.catalog.CatalogResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Live, network-dependent validation that a real InnerTube `search` seed
 * query resolves an actual "Popular en {region}" shelf via the own adapter.
 *
 * Opt-in only (set YOUTROC_LIVE=1). The default build stays deterministic:
 * YouTube's internal API can change shape, the region-anchoring shelf can
 * disappear from a seed's response, or the pinned `clientVersion` can go
 * stale without notice (see design ADR-7). This test exists to prove the
 * hypothesis on demand, not to gate the build -- when it fails,
 * [FallbackVideoCatalog][com.youtroc.data.extraction.catalog.FallbackVideoCatalog]
 * already masks the failure at runtime by falling back to NewPipe's Trending
 * kiosk. Mirrors [InnerTubeVideoSearchLiveTest].
 */
@EnabledIfEnvironmentVariable(named = "YOUTROC_LIVE", matches = "1")
class InnerTubeVideoCatalogLiveTest {

    @Test
    fun `resolves a real Popular en shelf from YouTube's InnerTube endpoint`() = runTest {
        val catalog = InnerTubeVideoCatalog(regionCode = "CO")

        val result = catalog.trending()

        val success = assertIs<CatalogResult.Success>(result)
        val shelf = success.shelves.first()
        assertTrue(
            shelf.title.startsWith("Popular en "),
            "expected the shelf title to start with \"Popular en \", was: ${shelf.title}",
        )
        assertTrue(shelf.videos.isNotEmpty(), "expected at least one video in the regional shelf")
        assertTrue(
            shelf.videos.all { it.id.value.isNotBlank() },
            "every resolved video should have a non-blank id",
        )
        assertTrue(
            shelf.videos.all { it.thumbnailUrl.startsWith("http") },
            "every resolved video should have an http(s) thumbnail url",
        )
    }
}
