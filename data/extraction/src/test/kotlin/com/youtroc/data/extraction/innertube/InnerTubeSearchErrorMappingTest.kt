package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.search.SearchResult
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
 * mirrors [com.youtroc.data.extraction.search.SearchErrorMappingTest] /
 * [com.youtroc.data.extraction.catalog.CatalogErrorMappingTest] -- plus the
 * cancellation-propagation contract (S3): cooperative cancellation must never
 * be swallowed into a [SearchResult.Error].
 */
class InnerTubeSearchErrorMappingTest {

    @Test
    fun `unknown host maps to Offline`() {
        assertEquals(SearchResult.Offline, UnknownHostException("no dns").toSearchResult())
    }

    @Test
    fun `socket timeout maps to Offline`() {
        assertEquals(SearchResult.Offline, SocketTimeoutException("timed out").toSearchResult())
    }

    @Test
    fun `a generic IO failure maps to Offline`() {
        assertEquals(SearchResult.Offline, IOException("network unreachable").toSearchResult())
    }

    @Test
    fun `a malformed payload maps to Error carrying the cause`() {
        val boom = SerializationException("malformed payload")

        assertEquals(SearchResult.Error(boom), boom.toSearchResult())
    }

    @Test
    fun `a non-200 response maps to Error carrying the cause`() {
        val boom = IllegalStateException("InnerTube search returned HTTP 500")

        assertEquals(SearchResult.Error(boom), boom.toSearchResult())
    }

    @Test
    fun `cooperative cancellation propagates instead of mapping to Error`() = runTest {
        // S3: kotlin.coroutines.cancellation.CancellationException (NOT
        // java.util.concurrent.CancellationException) must be caught and
        // rethrown by the adapter BEFORE the generic Exception catch --
        // never converted to SearchResult.Error. A client whose interceptor
        // throws mid-request is the network-free way to prove that path.
        val client = OkHttpClient.Builder()
            .addInterceptor { throw CancellationException("cancelled") }
            .build()
        val search = InnerTubeVideoSearch(client = client)

        assertFailsWith<CancellationException> {
            search.search("lofi")
        }
    }
}
