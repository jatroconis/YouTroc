package com.youtroc.feature.playback.settings

/** Action a "Ajustes" row performs when selected. FASE-1 has only one. */
enum class SettingsAction { OpenQuality }

/** A single row in the "Ajustes" menu. */
data class SettingsMenuRow(
    val label: String,
    val action: SettingsAction,
    val value: String?,
)

/**
 * Builds the row list for the "Ajustes" menu (REQ-S1/REQ-S7): pure — no
 * Compose, no Media3 — so it is unit-tested directly; `SettingsMenu.kt` only
 * renders whatever this returns. FASE-1: exactly one "Calidad" row.
 */
object SettingsMenuItems {

    fun build(activeQualityLabel: String?): List<SettingsMenuRow> =
        listOf(
            SettingsMenuRow(
                label = "Calidad",
                action = SettingsAction.OpenQuality,
                value = activeQualityLabel ?: "Automática",
            ),
        )
}
