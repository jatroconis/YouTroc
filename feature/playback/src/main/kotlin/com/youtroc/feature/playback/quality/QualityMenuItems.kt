package com.youtroc.feature.playback.quality

import com.youtroc.core.domain.playback.VideoQuality

/** A single row in the "Calidad" menu. The Auto row has [quality] == `null`. */
data class QualityMenuRow(
    val label: String,
    val quality: VideoQuality?,
    val isActive: Boolean,
)

/**
 * Builds the row list for the "Calidad" menu (REQ-Q2/REQ-Q3): pure — no
 * Compose, no Media3 — so it is unit-tested directly; `QualityMenu.kt` only
 * renders whatever this returns.
 */
object QualityMenuItems {

    /**
     * "Automática" is always first. A catalog with fewer than 2 distinct
     * qualities has nothing meaningful to pick between, so the menu shows
     * ONLY "Automática" (MAJOR-6, design gate #4431) — `VideoQualityCatalog`
     * already collapses such catalogs to empty (WU-1), but this stays
     * defensive against a size-1 [available] too.
     */
    fun build(available: List<VideoQuality>, active: VideoQuality?): List<QualityMenuRow> {
        if (available.size < 2) {
            return listOf(QualityMenuRow(label = "Automática", quality = null, isActive = true))
        }
        return listOf(QualityMenuRow(label = "Automática", quality = null, isActive = active == null)) +
            available.map { QualityMenuRow(label = it.label, quality = it, isActive = active?.id == it.id) }
    }
}
