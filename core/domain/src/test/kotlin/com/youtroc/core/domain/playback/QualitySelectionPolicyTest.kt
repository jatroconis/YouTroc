package com.youtroc.core.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QualitySelectionPolicyTest {

    private val catalog = listOf(
        VideoQuality(id = "h1080", label = "1080p", heightPx = 1080),
        VideoQuality(id = "h720", label = "720p", heightPx = 720),
        VideoQuality(id = "h480", label = "480p", heightPx = 480),
    )

    @Test
    fun `resolves to the exact height when present`() {
        assertEquals(catalog[1], QualitySelectionPolicy.resolve(720, catalog))
    }

    @Test
    fun `falls back to the nearest height at or below the request when the exact height is missing`() {
        assertEquals(catalog[1], QualitySelectionPolicy.resolve(900, catalog)) // 900 missing -> 720
    }

    @Test
    fun `snaps up to the smallest available quality when the request is below all of them`() {
        assertEquals(catalog[2], QualitySelectionPolicy.resolve(240, catalog)) // 240 below all -> 480
    }

    @Test
    fun `resolves to null (Auto) when the catalog is empty`() {
        assertNull(QualitySelectionPolicy.resolve(1080, emptyList()))
    }
}
