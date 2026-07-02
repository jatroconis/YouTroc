package com.youtroc.feature.playback.overlay

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure ⚙→Ajustes→Calidad state-machine tests (player-settings-menu REQ-S1/
 * REQ-S3/REQ-S4): no Compose/Android involved, so this needs no
 * device/emulator. Mirrors [OverlayReducerTest] in shape.
 */
class PlayerMenuReducerTest {

    @Test
    fun `open from Closed lands on Ajustes`() {
        assertEquals(PlayerMenu.Ajustes, PlayerMenuReducer.open(PlayerMenu.Closed))
    }

    @Test
    fun `open from Ajustes stays on Ajustes`() {
        assertEquals(PlayerMenu.Ajustes, PlayerMenuReducer.open(PlayerMenu.Ajustes))
    }

    @Test
    fun `open from Calidad always resets to the Ajustes root, REQ-S1`() {
        assertEquals(PlayerMenu.Ajustes, PlayerMenuReducer.open(PlayerMenu.Calidad))
    }

    @Test
    fun `openQuality from Ajustes opens Calidad`() {
        assertEquals(PlayerMenu.Calidad, PlayerMenuReducer.openQuality(PlayerMenu.Ajustes))
    }

    @Test
    fun `selectResolution from Calidad closes the entire menu, REQ-S3`() {
        assertEquals(PlayerMenu.Closed, PlayerMenuReducer.selectResolution(PlayerMenu.Calidad))
    }

    @Test
    fun `back from Calidad unwinds one level to Ajustes`() {
        assertEquals(PlayerMenu.Ajustes, PlayerMenuReducer.back(PlayerMenu.Calidad))
    }

    @Test
    fun `back from Ajustes closes the menu entirely`() {
        assertEquals(PlayerMenu.Closed, PlayerMenuReducer.back(PlayerMenu.Ajustes))
    }

    @Test
    fun `back from Closed stays Closed, exhaustive gate S5`() {
        assertEquals(PlayerMenu.Closed, PlayerMenuReducer.back(PlayerMenu.Closed))
    }
}
