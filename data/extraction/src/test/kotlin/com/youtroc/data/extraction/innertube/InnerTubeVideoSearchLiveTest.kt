package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.search.SearchResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Live, network-dependent validation that a real InnerTube `search` request
 * resolves actual results via the own adapter.
 *
 * Opt-in only (set YOUTROC_LIVE=1). The default build stays deterministic:
 * YouTube's internal API can change shape or start requiring a PoToken/
 * visitorData without notice, and the pinned `clientVersion` going stale is a
 * known maintenance knob (see design). This test exists to prove the
 * hypothesis on demand, not to gate the build -- when it fails,
 * [FallbackVideoSearch][com.youtroc.data.extraction.search.FallbackVideoSearch]
 * already masks the failure at runtime by falling back to NewPipe. Mirrors
 * [com.youtroc.data.extraction.search.NewPipeVideoSearchLiveTest].
 */
@EnabledIfEnvironmentVariable(named = "YOUTROC_LIVE", matches = "1")
class InnerTubeVideoSearchLiveTest {

    @Test
    fun `resolves real search results from YouTube's InnerTube endpoint`() = runTest {
        val search = InnerTubeVideoSearch()

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
