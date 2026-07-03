package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.stream.StreamResult
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Live, network-dependent validation that a real android_vr `player` request
 * resolves ACTUAL playable bytes -- not just a well-shaped [StreamResult].
 * `HEAD`-ing a resolved stream URL proves the format's `url` is directly
 * playable (no cipher/n-param descrambling needed, spike-confirmed) and
 * carries a real `Content-Length`.
 *
 * Opt-in only (set YOUTROC_LIVE=1). Mirrors
 * [InnerTubeVideoDetailLiveTest]/[InnerTubeVideoSearchLiveTest]: the default
 * build stays deterministic, and when this fails at runtime
 * [com.youtroc.data.extraction.stream.FallbackStreamProvider] already masks
 * it by falling back to NewPipe.
 *
 * What this test does NOT prove: whether the OWN-BUILT DASH manifest itself
 * parses and plays in Media3/ExoPlayer -- that is
 * [com.youtroc.data.player.DashManifestParseGateTest] (parse) and the
 * on-device TCL 55C6K play gate (documented PENDING in apply-progress).
 */
@EnabledIfEnvironmentVariable(named = "YOUTROC_LIVE", matches = "1")
class InnerTubeStreamProviderLiveTest {

    @Test
    fun `resolves real playable stream bytes from YouTube's android_vr player endpoint`() = runTest {
        val provider = InnerTubeStreamProvider()

        val result = provider.playableStreams(VideoId("dQw4w9WgXcQ"))

        val success = assertIs<StreamResult.Success>(result)
        assertTrue(success.streams.streams.isNotEmpty(), "expected at least one resolved stream")
        val manifest = success.streams.manifest
        assertTrue(manifest != null, "expected a DASH manifest to have been built")

        val streamUrl = success.streams.streams.first().url
        val client = OkHttpClient()
        val head = Request.Builder().url(streamUrl).head().build()

        client.newCall(head).execute().use { response ->
            assertTrue(response.isSuccessful, "expected HTTP 2xx from a HEAD on a resolved stream URL, got ${response.code}")
            val contentLength = response.header("Content-Length")?.toLongOrNull()
            assertTrue(contentLength != null && contentLength > 0, "expected a real Content-Length, got $contentLength")
        }
    }
}
