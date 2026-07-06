package com.youtroc.data.extraction.innertube

import kotlinx.serialization.Serializable

/**
 * Additional DTOs for the Shorts shelf (spike #4603, Q2): an anonymous WEB
 * search response for a Shorts-only query ("shorts") returns the NEW
 * view-model shape (`gridShelfViewModel`/`shortsLockupViewModel`), not the
 * `videoRenderer`/`reelItemRenderer` shapes probed and ruled out by the
 * spike. Field names are verbatim from the spike's live-captured shape.
 * [RenderItem.gridShelfViewModel] is additive/nullable-defaulted so it stays
 * INERT for every other search response -- [videoRenderers] never reads it.
 */

@Serializable
internal data class GridShelfViewModel(
    val header: GridShelfHeader? = null,
    val contents: List<GridShelfContent> = emptyList(),
)

@Serializable
internal data class GridShelfHeader(
    val sectionHeaderViewModel: SectionHeaderViewModel? = null,
)

@Serializable
internal data class SectionHeaderViewModel(
    val headline: ShortsText? = null,
)

@Serializable
internal data class GridShelfContent(
    val shortsLockupViewModel: ShortsLockupViewModel? = null,
)

/**
 * A single Shorts card. [entityId] is a synthetic view-model id (e.g.
 * `"shorts-shelf-item-<videoId>"`), NOT the canonical video id -- the
 * playable id lives at [onTap]'s [ReelWatchEndpoint.videoId].
 */
@Serializable
internal data class ShortsLockupViewModel(
    val entityId: String? = null,
    val onTap: ShortsOnTap? = null,
    val overlayMetadata: ShortsOverlayMetadata? = null,
    val thumbnailViewModel: ShortsThumbnailWrapper? = null,
)

@Serializable
internal data class ShortsOnTap(
    val innertubeCommand: ShortsInnertubeCommand? = null,
)

@Serializable
internal data class ShortsInnertubeCommand(
    val reelWatchEndpoint: ReelWatchEndpoint? = null,
)

@Serializable
internal data class ReelWatchEndpoint(
    val videoId: String? = null,
)

@Serializable
internal data class ShortsOverlayMetadata(
    val primaryText: ShortsText? = null,
    val secondaryText: ShortsText? = null,
)

@Serializable
internal data class ShortsText(
    val content: String? = null,
)

/**
 * The outer wrapper is itself keyed `thumbnailViewModel` -- SAME name as its
 * parent [ShortsLockupViewModel.thumbnailViewModel] field, one level down
 * (verbatim spike shape, not a typo).
 */
@Serializable
internal data class ShortsThumbnailWrapper(
    val thumbnailViewModel: ShortsThumbnailImage? = null,
)

@Serializable
internal data class ShortsThumbnailImage(
    val image: ShortsImageSources? = null,
)

@Serializable
internal data class ShortsImageSources(
    val sources: List<ShortsImageSource> = emptyList(),
)

@Serializable
internal data class ShortsImageSource(
    val url: String? = null,
    val width: Int = 0,
    val height: Int = 0,
)
