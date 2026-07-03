package com.youtroc.data.extraction.innertube

import kotlinx.serialization.Serializable

/**
 * Request/response DTO tree for `POST youtubei/v1/next`. Deliberately
 * minimal: only the path down to `videoPrimaryInfoRenderer`/
 * `videoSecondaryInfoRenderer`/`lockupViewModel` and the leaf fields
 * [InnerTubeDetailMapping] needs are modeled -- everything else (topbar,
 * engagement panels, player overlays, ...) is left unmapped and tolerated
 * via `Json { ignoreUnknownKeys = true }`. These types are `internal`: they
 * never cross [InnerTubeVideoDetail]'s port boundary. Reuses
 * [Context]/[Client]/[Runs]/[Run]/[SimpleText]/[ViewCountText] from
 * `InnerTubeSearchModels.kt` -- same request shape and view-count both-shape
 * ambiguity apply to both endpoints.
 */

// ---- Request ----

@Serializable
internal data class DetailRequest(
    val context: Context,
    val videoId: String,
)

// ---- Response ----

@Serializable
internal data class NextResponse(
    val contents: NextContents? = null,
)

@Serializable
internal data class NextContents(
    val twoColumnWatchNextResults: TwoColumnWatchNextResults? = null,
)

@Serializable
internal data class TwoColumnWatchNextResults(
    val results: WatchNextResultsWrapper? = null,
    val secondaryResults: SecondaryResultsWrapper? = null,
)

@Serializable
internal data class WatchNextResultsWrapper(
    val results: WatchNextResults? = null,
)

@Serializable
internal data class WatchNextResults(
    val contents: List<PrimaryItem> = emptyList(),
)

/** A single primary-column slot: the info renderers we map, or a sibling we skip. */
@Serializable
internal data class PrimaryItem(
    val videoPrimaryInfoRenderer: VideoPrimaryInfoRenderer? = null,
    val videoSecondaryInfoRenderer: VideoSecondaryInfoRenderer? = null,
)

@Serializable
internal data class VideoPrimaryInfoRenderer(
    val title: Runs? = null,
    val viewCount: PrimaryViewCount? = null,
    val dateText: SimpleText? = null,
)

@Serializable
internal data class PrimaryViewCount(
    val videoViewCountRenderer: VideoViewCountRenderer? = null,
)

@Serializable
internal data class VideoViewCountRenderer(
    val viewCount: ViewCountText? = null,
)

@Serializable
internal data class VideoSecondaryInfoRenderer(
    val owner: Owner? = null,
    val attributedDescription: AttributedText? = null,
)

/**
 * R1 (BLOCKING): `videoOwnerRenderer` sits under an `owner` wrapper --
 * `videoSecondaryInfoRenderer.owner.videoOwnerRenderer`, NOT the unwrapped
 * `videoSecondaryInfoRenderer.videoOwnerRenderer` the proposal assumed.
 * Live-validated on 5 video ids.
 */
@Serializable
internal data class Owner(
    val videoOwnerRenderer: VideoOwnerRenderer? = null,
)

@Serializable
internal data class VideoOwnerRenderer(
    val title: Runs? = null,
)

@Serializable
internal data class AttributedText(
    val content: String? = null,
)

@Serializable
internal data class SecondaryResultsWrapper(
    val secondaryResults: SecondaryResults? = null,
)

@Serializable
internal data class SecondaryResults(
    val results: List<SecondaryItem> = emptyList(),
)

/** A single related-column slot: a [lockupViewModel], or a sibling we skip (reel shelf, continuation, ...). */
@Serializable
internal data class SecondaryItem(
    val lockupViewModel: LockupViewModel? = null,
)

@Serializable
internal data class LockupViewModel(
    val contentId: String? = null,
    val contentType: String? = null,
    val metadata: LockupMetadataWrapper? = null,
    val contentImage: LockupContentImage? = null,
)

@Serializable
internal data class LockupMetadataWrapper(
    val lockupMetadataViewModel: LockupMetadataViewModel? = null,
)

@Serializable
internal data class LockupMetadataViewModel(
    val title: AttributedText? = null,
    val metadata: ContentMetadataWrapper? = null,
)

@Serializable
internal data class ContentMetadataWrapper(
    val contentMetadataViewModel: ContentMetadataViewModel? = null,
)

@Serializable
internal data class ContentMetadataViewModel(
    val metadataRows: List<MetadataRow> = emptyList(),
)

@Serializable
internal data class MetadataRow(
    val metadataParts: List<MetadataPart> = emptyList(),
)

@Serializable
internal data class MetadataPart(
    val text: AttributedText? = null,
)

/**
 * Only [thumbnailViewModel] is modeled -- a `LOCKUP_CONTENT_TYPE_PLAYLIST`
 * lockup uses a differently-shaped `collectionThumbnailViewModel` instead,
 * which is intentionally left unmapped: non-video lockups are filtered out
 * before their thumbnail is ever read (see [InnerTubeDetailMapping]).
 */
@Serializable
internal data class LockupContentImage(
    val thumbnailViewModel: LockupThumbnailViewModel? = null,
)

@Serializable
internal data class LockupThumbnailViewModel(
    val image: LockupImage? = null,
)

@Serializable
internal data class LockupImage(
    val sources: List<LockupImageSource> = emptyList(),
)

@Serializable
internal data class LockupImageSource(
    val url: String? = null,
    val width: Int = 0,
)
