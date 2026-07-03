package com.youtroc.data.extraction.innertube

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Deterministic, network-free verification of the ANDROID_VR player
 * request-body-shaping policy: pure construction, no I/O. Regression guard
 * (BLOCKING): [ANDROID_VR_CLIENT_NAME]/[ANDROID_VR_CLIENT_VERSION] and the
 * device fields MUST be separate from [INNERTUBE_CLIENT_NAME]/[INNERTUBE_CLIENT_VERSION]
 * (WEB) -- this test asserts the ANDROID_VR identity directly, and
 * [InnerTubeDetailRequestTest]/[InnerTubeSearchRequestTest] already pin the
 * WEB identity, so any accidental cross-contamination fails one suite or the
 * other.
 */
class InnerTubePlayerRequestTest {

    @Test
    fun `builds an ANDROID_VR client request with the spike-confirmed device identity`() {
        val request = buildPlayerRequest("dQw4w9WgXcQ", regionCode = null)

        assertEquals("ANDROID_VR", request.context.client.clientName)
        assertEquals("1.60.19", request.context.client.clientVersion)
        assertEquals("Oculus", request.context.client.deviceMake)
        assertEquals("Quest 3", request.context.client.deviceModel)
        assertEquals(32, request.context.client.androidSdkVersion)
        assertEquals("es", request.context.client.hl)
        assertEquals("dQw4w9WgXcQ", request.videoId)
    }

    @Test
    fun `omits gl for a null regionCode`() {
        assertNull(buildPlayerRequest("dQw4w9WgXcQ", regionCode = null).context.client.gl)
    }

    @Test
    fun `omits gl for a blank regionCode`() {
        assertNull(buildPlayerRequest("dQw4w9WgXcQ", regionCode = "   ").context.client.gl)
    }

    @Test
    fun `passes through a non-blank regionCode as gl`() {
        assertEquals("AR", buildPlayerRequest("dQw4w9WgXcQ", regionCode = "AR").context.client.gl)
    }

    @Test
    fun `a videoId round-trips through JSON encoding`() {
        val payload = buildPlayerRequest("dQw4w9WgXcQ", regionCode = null)

        val encoded = Json.encodeToString(payload)
        val decoded = Json.decodeFromString<PlayerRequest>(encoded)

        assertEquals("dQw4w9WgXcQ", decoded.videoId)
    }

    @Test
    fun `the default 2-arg call and an explicit ANDROID_VR context produce byte-identical requests (regression guard)`() {
        val default = buildPlayerRequest("dQw4w9WgXcQ", regionCode = "AR")
        val explicit = buildPlayerRequest("dQw4w9WgXcQ", regionCode = "AR", context = PlayerClientContext.ANDROID_VR)

        assertEquals(Json.encodeToString(default), Json.encodeToString(explicit))
    }

    @Test
    fun `builds an IOS client request with the spike-confirmed device identity`() {
        val request = buildPlayerRequest("dQw4w9WgXcQ", regionCode = null, context = PlayerClientContext.IOS)

        assertEquals("IOS", request.context.client.clientName)
        assertEquals("21.02.3", request.context.client.clientVersion)
        assertEquals("Apple", request.context.client.deviceMake)
        assertEquals("iPhone16,2", request.context.client.deviceModel)
        assertNull(request.context.client.androidSdkVersion)
        assertEquals("es", request.context.client.hl)
        assertEquals("dQw4w9WgXcQ", request.videoId)
    }

    @Test
    fun `buildRequest attaches a User-Agent header for the ios context`() {
        val request = buildRequest("dQw4w9WgXcQ", regionCode = null, context = PlayerClientContext.IOS)

        assertEquals(IOS_USER_AGENT, request.header("User-Agent"))
    }

    @Test
    fun `buildRequest attaches no User-Agent header for the default android_vr context`() {
        val request = buildRequest("dQw4w9WgXcQ", regionCode = null)

        assertNull(request.header("User-Agent"))
    }
}
