package com.youtroc.feature.playback.overlay

/**
 * Three-level ⚙→Ajustes→Calidad menu state (player-settings-menu REQ-S1/
 * REQ-S4). Pure — driven by [PlayerMenuReducer], never touches Compose/
 * Android types directly, mirroring [OverlayState]/[OverlayReducer].
 */
sealed interface PlayerMenu {
    data object Closed : PlayerMenu
    data object Ajustes : PlayerMenu
    data object Calidad : PlayerMenu
}

/**
 * Pure transitions for [PlayerMenu] (REQ-S1/REQ-S3/REQ-S4). [open] always
 * lands on the "Ajustes" root regardless of the current state — ⚙ never
 * reopens a previously-viewed "Calidad" sub-panel (REQ-S1, "always fresh").
 * [back] is exhaustive over all three states (gate S5) so BACK unwinds
 * exactly one level per press: Calidad -> Ajustes -> Closed -> Closed.
 */
object PlayerMenuReducer {

    fun open(current: PlayerMenu): PlayerMenu = PlayerMenu.Ajustes

    fun openQuality(current: PlayerMenu): PlayerMenu = PlayerMenu.Calidad

    /** Applying a resolution (or "Automática") closes the ENTIRE menu, not just Calidad — REQ-S3. */
    fun selectResolution(current: PlayerMenu): PlayerMenu = PlayerMenu.Closed

    fun back(current: PlayerMenu): PlayerMenu = when (current) {
        PlayerMenu.Calidad -> PlayerMenu.Ajustes
        PlayerMenu.Ajustes -> PlayerMenu.Closed
        PlayerMenu.Closed -> PlayerMenu.Closed
    }
}
