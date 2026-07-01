package com.youtroc.feature.video

import com.youtroc.core.ui.component.VideoCardUi

/**
 * Deterministic states the video-detail screen can render. Mirrors the
 * domain's [com.youtroc.core.domain.detail.DetailResult] vocabulary 1:1 —
 * NO `Empty` case (an empty [VideoDetailUi.related] list is still valid
 * [Content], mirrors [com.youtroc.feature.catalog.HomeUiState]'s auto-load
 * shape rather than [com.youtroc.feature.search.SearchUiState]'s `Idle`).
 */
sealed interface DetailUiState {

    /** Resolving the requested video's detail — the initial state, auto-loads. */
    data object Loading : DetailUiState

    /** Detail resolved and ready to render. */
    data class Content(val detail: VideoDetailUi) : DetailUiState

    /** The video exists but cannot be resolved anonymously (age-gated, removed, region-locked). */
    data object NotAvailable : DetailUiState

    /** No network reachable. */
    data object Offline : DetailUiState

    /** Extraction failed unexpectedly. */
    data object Error : DetailUiState
}

/**
 * Presentation model for a resolved video's detail. Carries [related] as
 * `:core:ui` [VideoCardUi]s, ready for [com.youtroc.core.ui.component.ShelfRow].
 */
data class VideoDetailUi(
    val title: String,
    val channel: String,
    val meta: String,
    val description: String,
    val related: List<VideoCardUi>,
)
