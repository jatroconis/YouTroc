package com.youtroc.data.player

import com.youtroc.core.domain.playback.VideoQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pure, JVM-only verification of the live quality-degrade rule (R1, REQ-L9):
 * for a live manifest, the Calidad menu must collapse to Automatic-only. No
 * Media3/ExoPlayer instance is needed — only [VideoQuality] value objects —
 * so this needs no device/emulator/Robolectric.
 */
class LiveQualityDegradeTest {

    private val catalog = listOf(
        VideoQuality(id = "1080p", label = "1080p", heightPx = 1080),
        VideoQuality(id = "720p", label = "720p", heightPx = 720),
    )

    @Test
    fun `qualities returns empty list when live`() {
        assertEquals(emptyList(), LiveQualityDegrade.qualities(isLive = true, catalog = catalog))
    }

    @Test
    fun `qualities returns the catalog unchanged when not live`() {
        assertEquals(catalog, LiveQualityDegrade.qualities(isLive = false, catalog = catalog))
    }

    @Test
    fun `active returns null when live even if a quality is pinned`() {
        assertNull(LiveQualityDegrade.active(isLive = true, pinned = catalog[0]))
    }

    @Test
    fun `active returns the pinned quality unchanged when not live`() {
        assertEquals(catalog[0], LiveQualityDegrade.active(isLive = false, pinned = catalog[0]))
    }

    @Test
    fun `active returns null when not live and nothing is pinned`() {
        assertNull(LiveQualityDegrade.active(isLive = false, pinned = null))
    }
}
