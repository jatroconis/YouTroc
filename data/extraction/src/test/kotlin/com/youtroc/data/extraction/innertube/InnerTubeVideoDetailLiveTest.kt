package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.detail.DetailResult
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Live, network-dependent validation that a real InnerTube `next` request
 * resolves actual video detail via the own adapter.
 *
 * Opt-in only (set YOUTROC_LIVE=1). The default build stays deterministic:
 * YouTube's internal API can change shape or start requiring a PoToken/
 * visitorData without notice, and the pinned `clientVersion` going stale is a
 * known maintenance knob (see design). This test exists to prove the
 * hypothesis on demand, not to gate the build -- when it fails,
 * [FallbackVideoDetail][com.youtroc.data.extraction.detail.FallbackVideoDetail]
 * already masks the failure at runtime by falling back to NewPipe. Mirrors
 * [InnerTubeVideoSearchLiveTest].
 */
@EnabledIfEnvironmentVariable(named = "YOUTROC_LIVE", matches = "1")
class InnerTubeVideoDetailLiveTest {

    @Test
    fun `resolves real video detail from YouTube's InnerTube endpoint`() = runTest {
        val detail = InnerTubeVideoDetail()

        val result = detail.detail(VideoId("dQw4w9WgXcQ"))

        val success = assertIs<DetailResult.Success>(result)
        assertTrue(success.detail.title.isNotBlank(), "expected a non-blank title")
        assertTrue(success.detail.related.isNotEmpty(), "expected at least one related video")
    }
}
