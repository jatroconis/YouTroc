package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.playback.PlaybackManifest
import com.youtroc.core.domain.stream.StreamResult
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * End-to-end, network-free verification of [InnerTubeStreamProvider]: a fake
 * [Interceptor] short-circuits the real network call with a canned response
 * built from the same fixtures [InnerTubePlayerMappingTest] already
 * validates the decode shape against -- mirrors
 * [com.youtroc.data.extraction.innertube.InnerTubeDetailErrorMappingTest]'s
 * cancellation test, extended into a full request/response round trip.
 */
class InnerTubeStreamProviderTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/innertube/$name")) { "missing fixture $name" }
            .bufferedReader()
            .readText()

    private fun fakeClient(interceptor: Interceptor): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(interceptor).build()

    private fun jsonResponse(request: okhttp3.Request, code: Int, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "error")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    @Test
    fun `a healthy 200 response with populated adaptiveFormats maps to Success with a DASH manifest`() = runTest {
        val client = fakeClient { chain -> jsonResponse(chain.request(), 200, fixture("player_android_vr.json")) }
        val provider = InnerTubeStreamProvider(client = client)

        val result = provider.playableStreams(VideoId("dQw4w9WgXcQ"))

        assertIs<StreamResult.Success>(result)
        assertEquals(PlaybackManifest.Kind.DASH, result.streams.manifest?.kind)
    }

    @Test
    fun `a throttled itag-18-only response maps to Error, never a degraded Success`() = runTest {
        val client = fakeClient { chain -> jsonResponse(chain.request(), 200, fixture("player_android_vr_throttled.json")) }
        val provider = InnerTubeStreamProvider(client = client)

        val result = provider.playableStreams(VideoId("dQw4w9WgXcQ"))

        assertIs<StreamResult.Error>(result)
    }

    @Test
    fun `an UNPLAYABLE live response maps to NotAvailable`() = runTest {
        val client = fakeClient { chain -> jsonResponse(chain.request(), 200, fixture("player_android_vr_unavailable.json")) }
        val provider = InnerTubeStreamProvider(client = client)

        val result = provider.playableStreams(VideoId("jfKfPfyJRdk"))

        assertEquals(StreamResult.NotAvailable, result)
    }

    @Test
    fun `a non-200 response maps to Error`() = runTest {
        val client = fakeClient { chain -> jsonResponse(chain.request(), 500, "{}") }
        val provider = InnerTubeStreamProvider(client = client)

        val result = provider.playableStreams(VideoId("dQw4w9WgXcQ"))

        assertIs<StreamResult.Error>(result)
    }

    @Test
    fun `malformed JSON maps to Error`() = runTest {
        val client = fakeClient { chain -> jsonResponse(chain.request(), 200, "{not valid json") }
        val provider = InnerTubeStreamProvider(client = client)

        val result = provider.playableStreams(VideoId("dQw4w9WgXcQ"))

        assertIs<StreamResult.Error>(result)
    }

    @Test
    fun `an IOException from the network layer maps to Offline`() = runTest {
        val client = fakeClient { throw IOException("network unreachable") }
        val provider = InnerTubeStreamProvider(client = client)

        val result = provider.playableStreams(VideoId("dQw4w9WgXcQ"))

        assertEquals(StreamResult.Offline, result)
    }

    @Test
    fun `cooperative cancellation propagates instead of mapping to Error`() = runTest {
        val client = fakeClient { throw CancellationException("cancelled") }
        val provider = InnerTubeStreamProvider(client = client)

        assertFailsWith<CancellationException> {
            provider.playableStreams(VideoId("dQw4w9WgXcQ"))
        }
    }
}
