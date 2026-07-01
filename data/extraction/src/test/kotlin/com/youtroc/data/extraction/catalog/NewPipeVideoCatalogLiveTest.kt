package com.youtroc.data.extraction.catalog

import com.youtroc.core.domain.catalog.CatalogResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Live, network-dependent validation that the Home trending feed resolves real
 * YouTube Trending kiosk data.
 *
 * Opt-in only (set YOUTROC_LIVE=1). The default build stays deterministic because
 * YouTube extraction is inherently flaky (SABR / bot-checks); a live assertion in CI
 * would flap. This test exists to prove the hypothesis on demand, not to gate the
 * build. Mirrors [com.youtroc.data.extraction.NewPipeStreamProviderLiveTest].
 */
@EnabledIfEnvironmentVariable(named = "YOUTROC_LIVE", matches = "1")
class NewPipeVideoCatalogLiveTest {

    @Test
    fun `resolves real trending videos from the YouTube kiosk`() = runTest {
        val catalog = NewPipeVideoCatalog()

        val result = catalog.trending()

        val success = assertIs<CatalogResult.Success>(result)
        val videos = success.shelves.flatMap { it.videos }
        assertTrue(videos.isNotEmpty(), "expected at least one trending video")
        assertTrue(
            videos.all { it.id.value.isNotBlank() },
            "every resolved video should have a non-blank id",
        )
        assertTrue(
            videos.all { it.thumbnailUrl.startsWith("http") },
            "every resolved video should have an http(s) thumbnail url",
        )
    }
}
