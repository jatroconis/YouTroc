package com.youtroc.feature.playback.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Exercises the pure row-model builder behind the "Ajustes" menu
 * (player-settings-menu REQ-S7). No Compose involved — see
 * `SettingsMenu.kt` for the composable that renders these rows, which is
 * integration-only (validated on device).
 */
class SettingsMenuItemsTest {

    @Test
    fun `FASE-1 returns exactly one Calidad row that opens the quality panel`() {
        val rows = SettingsMenuItems.build(activeQualityLabel = null)

        assertEquals(1, rows.size)
        assertEquals("Calidad", rows.single().label)
        assertEquals(SettingsAction.OpenQuality, rows.single().action)
    }

    @Test
    fun `Calidad row value reflects the active quality label when present`() {
        val rows = SettingsMenuItems.build(activeQualityLabel = "1080p")

        assertEquals("1080p", rows.single().value)
    }

    @Test
    fun `Calidad row value falls back to Automatica when nothing is pinned`() {
        val rows = SettingsMenuItems.build(activeQualityLabel = null)

        assertEquals("Automática", rows.single().value)
    }
}
