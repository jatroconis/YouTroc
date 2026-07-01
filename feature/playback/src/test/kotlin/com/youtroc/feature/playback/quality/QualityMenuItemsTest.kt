package com.youtroc.feature.playback.quality

import com.youtroc.core.domain.playback.VideoQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises the pure row-model builder behind the "Calidad" menu (REQ-Q2/
 * REQ-Q3). No Compose involved — see `QualityMenu.kt` for the composable
 * that renders these rows, which is integration-only (validated on device).
 */
class QualityMenuItemsTest {

    private val q1080 = VideoQuality(id = "h1080", label = "1080p", heightPx = 1080)
    private val q720 = VideoQuality(id = "h720", label = "720p", heightPx = 720)
    private val q480 = VideoQuality(id = "h480", label = "480p", heightPx = 480)

    @Test
    fun `Automatica is the first row and is checked when nothing is pinned`() {
        val rows = QualityMenuItems.build(available = listOf(q1080, q720), active = null)

        assertEquals("Automática", rows.first().label)
        assertTrue(rows.first().isActive)
        assertEquals(null, rows.first().quality)
    }

    @Test
    fun `the row matching the active quality id is checked, not Automatica`() {
        val rows = QualityMenuItems.build(available = listOf(q1080, q720), active = q720)

        assertFalse(rows.first { it.quality == null }.isActive)
        assertTrue(rows.first { it.quality?.id == "h720" }.isActive)
        assertFalse(rows.first { it.quality?.id == "h1080" }.isActive)
    }

    @Test
    fun `resolution rows preserve the catalog order after Automatica`() {
        val rows = QualityMenuItems.build(available = listOf(q1080, q720, q480), active = null)

        assertEquals(listOf("Automática", "1080p", "720p", "480p"), rows.map { it.label })
    }

    @Test
    fun `an empty catalog shows only Automatica, MAJOR-6`() {
        val rows = QualityMenuItems.build(available = emptyList(), active = null)

        assertEquals(1, rows.size)
        assertEquals("Automática", rows.single().label)
        assertTrue(rows.single().isActive)
    }

    @Test
    fun `a single-quality catalog also collapses to Automatica only, MAJOR-6`() {
        val rows = QualityMenuItems.build(available = listOf(q1080), active = null)

        assertEquals(1, rows.size)
        assertEquals("Automática", rows.single().label)
        assertTrue(rows.single().isActive)
    }
}
