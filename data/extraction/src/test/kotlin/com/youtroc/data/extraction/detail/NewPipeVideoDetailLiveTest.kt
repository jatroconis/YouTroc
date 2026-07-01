package com.youtroc.data.extraction.detail

import com.youtroc.core.domain.detail.DetailResult
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Live, network-dependent validation that a real YouTube video detail
 * resolves actual metadata + related items end-to-end.
 *
 * Opt-in only (set YOUTROC_LIVE=1). The default build stays deterministic
 * because YouTube extraction is inherently flaky (SABR / bot-checks); a live
 * assertion in CI would flap. This test exists to prove the hypothesis on
 * demand, not to gate the build. It is also the ONLY real proof of the
 * `getStreamExtractor`/`StreamInfo.getInfo(extractor)` detail API surface
 * end-to-end. Mirrors [com.youtroc.data.extraction.search.NewPipeVideoSearchLiveTest].
 */
@EnabledIfEnvironmentVariable(named = "YOUTROC_LIVE", matches = "1")
class NewPipeVideoDetailLiveTest {

    @Test
    fun `resolves real video detail from YouTube`() = runTest {
        val detail = NewPipeVideoDetail()

        val result = detail.detail(VideoId("dQw4w9WgXcQ"))

        val success = assertIs<DetailResult.Success>(result)
        assertTrue(success.detail.title.isNotBlank(), "expected a non-blank title")
        assertTrue(success.detail.channelName.isNotBlank(), "expected a non-blank channel name")
        assertTrue(
            success.detail.viewCount == null || success.detail.viewCount!! >= 0,
            "expected a null or non-negative view count",
        )
        assertTrue(
            success.detail.related.all { it.id.value.isNotBlank() },
            "every related video should have a non-blank id",
        )
        assertTrue(
            success.detail.related.all { it.thumbnailUrl.startsWith("http") },
            "every related video should have an http(s) thumbnail url",
        )
    }
}
