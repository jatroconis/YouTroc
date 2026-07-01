package com.youtroc.data.extraction.search

import com.youtroc.core.domain.search.SearchResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Live, network-dependent validation that a real YouTube search resolves
 * actual videos-only results.
 *
 * Opt-in only (set YOUTROC_LIVE=1). The default build stays deterministic
 * because YouTube extraction is inherently flaky (SABR / bot-checks); a live
 * assertion in CI would flap. This test exists to prove the hypothesis on
 * demand, not to gate the build. It is also the ONLY real proof of the
 * `searchQHFactory`/`getSearchExtractor`/VIDEOS API surface end-to-end.
 * Mirrors [com.youtroc.data.extraction.catalog.NewPipeVideoCatalogLiveTest].
 */
@EnabledIfEnvironmentVariable(named = "YOUTROC_LIVE", matches = "1")
class NewPipeVideoSearchLiveTest {

    @Test
    fun `resolves real videos-only search results from YouTube`() = runTest {
        val search = NewPipeVideoSearch()

        val result = search.search("lofi")

        val success = assertIs<SearchResult.Success>(result)
        assertTrue(success.videos.isNotEmpty(), "expected at least one search result")
        assertTrue(
            success.videos.all { it.id.value.isNotBlank() },
            "every resolved video should have a non-blank id",
        )
        assertTrue(
            success.videos.all { it.thumbnailUrl.startsWith("http") },
            "every resolved video should have an http(s) thumbnail url",
        )
    }
}
