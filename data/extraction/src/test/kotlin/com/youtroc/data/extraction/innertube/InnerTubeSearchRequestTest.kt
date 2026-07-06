package com.youtroc.data.extraction.innertube

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Deterministic, network-free verification of the request-body-shaping
 * policy: pure construction, no I/O, so it's directly unit-testable without
 * the opt-in live test. Mirrors
 * [com.youtroc.data.extraction.search.NewPipeVideoSearchLocalizationTest]'s
 * localization-forcing coverage for the NewPipe adapter.
 */
class InnerTubeSearchRequestTest {

    @Test
    fun `builds a WEB client request with hl=es`() {
        val request = buildSearchRequest("lofi", regionCode = null)

        assertEquals("WEB", request.context.client.clientName)
        assertEquals("es", request.context.client.hl)
        assertEquals("lofi", request.query)
    }

    @Test
    fun `omits gl for a null regionCode`() {
        assertNull(buildSearchRequest("lofi", regionCode = null).context.client.gl)
    }

    @Test
    fun `omits gl for a blank regionCode`() {
        // R2: blank must NOT be sent as "" -- mirrors
        // NewPipeVideoSearch's `regionCode?.takeIf { it.isNotBlank() }`.
        assertNull(buildSearchRequest("lofi", regionCode = "   ").context.client.gl)
    }

    @Test
    fun `passes through a non-blank regionCode as gl`() {
        assertEquals("AR", buildSearchRequest("lofi", regionCode = "AR").context.client.gl)
    }

    @Test
    fun `a query containing quotes and backslashes round-trips through JSON encoding`() {
        val query = "say \"hi\" \\ ok"
        val payload = buildSearchRequest(query, regionCode = null)

        val encoded = Json.encodeToString(payload)
        val decoded = Json.decodeFromString<SearchRequest>(encoded)

        assertEquals(query, decoded.query)
    }

    @Test
    fun `passes through a non-null params as the request's params field`() {
        assertEquals("EgJAAQ==", buildSearchRequest("en vivo", regionCode = null, params = "EgJAAQ==").params)
    }

    @Test
    fun `omits params when null`() {
        assertNull(buildSearchRequest("lofi", regionCode = null, params = null).params)
    }
}
