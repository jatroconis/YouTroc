package com.youtroc.data.extraction

import com.youtroc.core.domain.stream.StreamResult
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Live, network-dependent validation of the project's #1 risk: can we extract an
 * ad-free playable stream from real YouTube?
 *
 * Opt-in only (set YOUTROC_LIVE=1). The default build stays deterministic because
 * YouTube extraction is inherently flaky (SABR / bot-checks); a live assertion in CI
 * would flap. This test exists to prove the hypothesis on demand, not to gate the build.
 */
@EnabledIfEnvironmentVariable(named = "YOUTROC_LIVE", matches = "1")
class NewPipeStreamProviderLiveTest {

    @Test
    fun `extracts ad-free playable streams for a stable public video`() = runTest {
        val provider = NewPipeStreamProvider()

        val result = provider.playableStreams(VideoId("dQw4w9WgXcQ"))

        val success = assertIs<StreamResult.Success>(result)
        assertTrue(
            success.streams.streams.isNotEmpty(),
            "expected at least one playable stream",
        )
        assertTrue(
            success.streams.streams.all { it.url.startsWith("http") },
            "every resolved stream should be an http(s) URL",
        )
    }
}
