package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.catalog.CatalogResult
import com.youtroc.core.domain.catalog.Shelf
import com.youtroc.core.domain.catalog.ShelfId
import com.youtroc.core.domain.catalog.ShelfSource
import com.youtroc.core.domain.catalog.Video
import com.youtroc.core.domain.video.VideoId
import com.youtroc.data.extraction.catalog.UNKNOWN_CHANNEL_PLACEHOLDER
import com.youtroc.data.extraction.catalog.UNTITLED_VIDEO_PLACEHOLDER
import com.youtroc.data.extraction.catalog.toCatalogResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import kotlin.coroutines.cancellation.CancellationException

/**
 * Adapter: the Shorts shelf's [ShelfSource]. Reuses the same
 * `youtubei/v1/search` plumbing as [SearchQueryShelfSource], but a plain
 * "shorts" query returns the NEW view-model shape
 * (`gridShelfViewModel`/`shortsLockupViewModel`, spike #4603 Q2) instead of
 * `videoRenderer` -- mapped separately here rather than via
 * [VideoRenderer.toVideoOrNull]. Wired into `ComposeHomeFeed` only at S3
 * (Home wiring); this class is self-contained and independently testable
 * ahead of that wiring.
 */
/** Same measured on-device ceiling as the thematic shelves (see HomeShelfSources). */
internal const val SHORTS_TIMEOUT_MS = 8_000L

class ShortsShelfSource(
    // Derived-by-default so the composition root (`:app`, which cannot call the
    // internal deriveTimeoutClient) still gets a REAL per-call bound (B1) --
    // the bare shared client carries no callTimeout at all.
    private val client: OkHttpClient = deriveTimeoutClient(InnerTubeHttp.client, SHORTS_TIMEOUT_MS),
    private val regionCode: String? = null,
    override val timeoutMs: Long = SHORTS_TIMEOUT_MS,
) : ShelfSource {

    override val id: ShelfId = ShelfId.SHORTS
    override val displayTitle: String = "Shorts"

    override suspend fun load(): CatalogResult = withContext(Dispatchers.IO) {
        try {
            val request = buildSearchHttpRequest(query = "shorts", regionCode = regionCode, params = null)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("InnerTube shorts search returned HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val parsed = innerTubeSearchJson.decodeFromString<SearchResponse>(body)
                val videos = parsed.gridShelfContents()
                    .mapNotNull { it.shortsLockupViewModel }
                    .mapNotNull { it.toVideoOrNull() }
                if (videos.isEmpty()) {
                    CatalogResult.Empty
                } else {
                    CatalogResult.Success(listOf(Shelf(id = id, title = displayTitle, videos = videos)))
                }
            }
        } catch (e: CancellationException) {
            throw e // never swallow cooperative cancellation
        } catch (e: Exception) {
            e.toCatalogResult()
        }
    }
}

/** Every `gridShelfViewModel.contents[]` across all top-level sections (Shorts shelf), in source order. */
private fun SearchResponse.gridShelfContents(): List<GridShelfContent> =
    contents
        ?.twoColumnSearchResultsRenderer
        ?.primaryContents
        ?.sectionListRenderer
        ?.contents
        .orEmpty()
        .flatMap { it.itemSectionRenderer?.contents.orEmpty() }
        .mapNotNull { it.gridShelfViewModel }
        .flatMap { it.contents }

/**
 * Maps a [ShortsLockupViewModel] onto the domain [Video]. The canonical
 * video id lives at [ShortsLockupViewModel.onTap]'s nested
 * [ReelWatchEndpoint.videoId] -- NOT [ShortsLockupViewModel.entityId], which
 * is a synthetic view-model id (e.g. `"shorts-shelf-item-<videoId>"`). A
 * missing/blank id is dropped, mirroring [VideoRenderer.toVideoOrNull]'s
 * id-drop convention. No channel field exists on this source shape, so
 * [Video.channelName] is always the shared placeholder;
 * [Video.viewCount]/[Video.publishedText] stay null -- the source's
 * abbreviated view text (e.g. "3,6 M de visualizaciones") isn't
 * digit-recoverable.
 */
private fun ShortsLockupViewModel.toVideoOrNull(): Video? {
    val id = onTap?.innertubeCommand?.reelWatchEndpoint?.videoId?.takeIf { it.isNotBlank() } ?: return null
    return Video(
        id = VideoId(id),
        title = overlayMetadata?.primaryText?.content.orEmpty().ifBlank { UNTITLED_VIDEO_PLACEHOLDER },
        channelName = UNKNOWN_CHANNEL_PLACEHOLDER,
        thumbnailUrl = thumbnailViewModel?.thumbnailViewModel?.image?.sources.orEmpty()
            .maxByOrNull { it.width }?.url
            ?: "https://i.ytimg.com/vi/$id/hq720.jpg",
        viewCount = null,
        publishedText = null,
    )
}
