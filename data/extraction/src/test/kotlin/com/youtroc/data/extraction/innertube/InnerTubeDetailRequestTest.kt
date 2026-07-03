package com.youtroc.data.extraction.innertube

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Deterministic, network-free verification of the detail request-body-shaping
 * policy: pure construction, no I/O, so it's directly unit-testable without
 * the opt-in live test. Mirrors [InnerTubeSearchRequestTest].
 */
class InnerTubeDetailRequestTest {

    @Test
    fun `builds a WEB client request with hl=es and the given videoId`() {
        val request = buildDetailRequest("dQw4w9WgXcQ", regionCode = null)

        assertEquals("WEB", request.context.client.clientName)
        assertEquals("es", request.context.client.hl)
        assertEquals("dQw4w9WgXcQ", request.videoId)
    }

    @Test
    fun `omits gl for a null regionCode`() {
        assertNull(buildDetailRequest("dQw4w9WgXcQ", regionCode = null).context.client.gl)
    }

    @Test
    fun `omits gl for a blank regionCode`() {
        assertNull(buildDetailRequest("dQw4w9WgXcQ", regionCode = "   ").context.client.gl)
    }

    @Test
    fun `passes through a non-blank regionCode as gl`() {
        assertEquals("AR", buildDetailRequest("dQw4w9WgXcQ", regionCode = "AR").context.client.gl)
    }

    @Test
    fun `a videoId round-trips through JSON encoding`() {
        val payload = buildDetailRequest("dQw4w9WgXcQ", regionCode = null)

        val encoded = Json.encodeToString(payload)
        val decoded = Json.decodeFromString<DetailRequest>(encoded)

        assertEquals("dQw4w9WgXcQ", decoded.videoId)
    }
}
