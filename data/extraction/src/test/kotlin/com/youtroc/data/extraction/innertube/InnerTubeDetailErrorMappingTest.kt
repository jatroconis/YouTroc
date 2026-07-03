package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.detail.DetailResult
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Deterministic, network-free verification of the failure-mapping policy --
 * mirrors [InnerTubeSearchErrorMappingTest] -- plus the cancellation-
 * propagation contract: cooperative cancellation must never be swallowed
 * into a [DetailResult.Error].
 */
class InnerTubeDetailErrorMappingTest {

    @Test
    fun `unknown host maps to Offline`() {
        assertEquals(DetailResult.Offline, UnknownHostException("no dns").toDetailResult())
    }

    @Test
    fun `socket timeout maps to Offline`() {
        assertEquals(DetailResult.Offline, SocketTimeoutException("timed out").toDetailResult())
    }

    @Test
    fun `a generic IO failure maps to Offline`() {
        assertEquals(DetailResult.Offline, IOException("network unreachable").toDetailResult())
    }

    @Test
    fun `a malformed payload maps to Error carrying the cause`() {
        val boom = SerializationException("malformed payload")

        assertEquals(DetailResult.Error(boom), boom.toDetailResult())
    }

    @Test
    fun `a non-200 response maps to Error carrying the cause`() {
        val boom = IllegalStateException("InnerTube next returned HTTP 500")

        assertEquals(DetailResult.Error(boom), boom.toDetailResult())
    }

    @Test
    fun `cooperative cancellation propagates instead of mapping to Error`() = runTest {
        // kotlin.coroutines.cancellation.CancellationException (NOT
        // java.util.concurrent.CancellationException) must be caught and
        // rethrown by the adapter BEFORE the generic Exception catch --
        // never converted to DetailResult.Error. A client whose interceptor
        // throws mid-request is the network-free way to prove that path.
        val client = OkHttpClient.Builder()
            .addInterceptor { throw CancellationException("cancelled") }
            .build()
        val detail = InnerTubeVideoDetail(client = client)

        assertFailsWith<CancellationException> {
            detail.detail(VideoId("dQw4w9WgXcQ"))
        }
    }
}
