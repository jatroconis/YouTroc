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
 * Live, network-dependent validation that the PRODUCTION storyboard pipeline
 * ([StoryboardSpecParser][toStoryboardSpecOrNull] -> [InnerTubePlayerMapping.toStreamResult] ->
 * [com.youtroc.core.domain.stream.PlayableStreams.storyboard]) resolves a REAL,
 * fetchable sprite sheet from a live YouTube response -- not just the
 * synthetic fixture [StoryboardSpecParserTest] exercises. Confirms the
 * `$L`/`$N`/`$M` substitution (gate F4) produces a URL the real CDN actually
 * serves, and that the per-level `&sigh=` is still valid at request time.
 *
 * Opt-in only (set YOUTROC_LIVE=1). Mirrors [InnerTubeStreamProviderLiveTest]:
 * the default build stays deterministic, and a regression here is already
 * masked in production by [com.youtroc.core.domain.stream.PlayableStreams.storyboard]
 * simply resolving to `null` (REQ-SB2) -- never a broken player. Adapted
 * (apply-time task 5.1) from a throwaway raw-JSON probe (probe #4581) into a
 * real assertion against production code, rather than hand-parsed JSON.
 *
 * What this test does NOT prove: on-device rendering
 * ([com.youtroc.feature.playback.ScrubPreviewThumbnail]/
 * [com.youtroc.core.ui.component.SpriteTile] crop/positioning) -- that is the
 * TCL 55C6K manual check (task 4.4, owner-owned).
 */
@EnabledIfEnvironmentVariable(named = "YOUTROC_LIVE", matches = "1")
class StoryboardLiveVerificationTest {

    @Test
    fun `resolves a real fetchable storyboard sprite from YouTube's android_vr player endpoint`() = runTest {
        val provider = InnerTubeStreamProvider.androidVr()

        val result = provider.playableStreams(VideoId("dQw4w9WgXcQ"))

        val success = assertIs<StreamResult.Success>(result)
        val storyboard = requireNotNull(success.streams.storyboard) {
            "expected a parsed storyboard from a healthy android_vr response"
        }
        val level = requireNotNull(storyboard.previewLevel()) {
            "expected a time-indexed level (L1/L2), never only an L0 recap sheet"
        }
        val tile = level.tileAt(positionMs = 30_000)

        val client = OkHttpClient()
        val get = Request.Builder().url(tile.url).get().build()
        client.newCall(get).execute().use { response ->
            assertTrue(response.isSuccessful, "expected HTTP 2xx fetching a resolved sprite sheet, got ${response.code}")
            val bytes = response.body?.bytes()?.size ?: 0
            assertTrue(bytes > 0, "expected a non-empty sprite sheet body")
        }
    }

    /**
     * Same verification against the FALLBACK-RUNG-ONLY ios identity, using a
     * different, historically stable videoId (see
     * [InnerTubeStreamProviderLiveTest]'s KDoc for why) so this rung's
     * storyboard coverage never shares a single point of failure with
     * android_vr's above.
     */
    @Test
    fun `resolves a real fetchable storyboard sprite from YouTube's ios player endpoint`() = runTest {
        val provider = InnerTubeStreamProvider.ios()

        val result = provider.playableStreams(VideoId("jNQXAC9IVRw"))

        val success = assertIs<StreamResult.Success>(result)
        val storyboard = requireNotNull(success.streams.storyboard) {
            "expected a parsed storyboard from a healthy ios response"
        }
        val level = requireNotNull(storyboard.previewLevel()) {
            "expected a time-indexed level (L1/L2), never only an L0 recap sheet"
        }
        val tile = level.tileAt(positionMs = 30_000)

        val client = OkHttpClient()
        val get = Request.Builder().url(tile.url).get().build()
        client.newCall(get).execute().use { response ->
            assertTrue(response.isSuccessful, "expected HTTP 2xx fetching a resolved sprite sheet, got ${response.code}")
            val bytes = response.body?.bytes()?.size ?: 0
            assertTrue(bytes > 0, "expected a non-empty sprite sheet body")
        }
    }
}
