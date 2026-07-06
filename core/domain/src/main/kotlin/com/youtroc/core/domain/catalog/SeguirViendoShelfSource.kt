package com.youtroc.core.domain.catalog

import com.youtroc.core.domain.playback.GetContinueWatching

/**
 * Adapter-free [ShelfSource] for "Seguir viendo" (REQ-HF9): reads local
 * watch history via [getContinueWatching] and maps each resumable entry to a
 * [Video]. [timeoutMs] is a local DataStore read -- the ceiling is
 * structurally required by [ShelfSource] but not expected to actually bind.
 *
 * Watch-history entries carry no thumbnail of their own, so one is
 * synthesized from YouTube's `i.ytimg.com` CDN template keyed by video id
 * (N8) -- the same convention already used elsewhere in this codebase for a
 * source that has no thumbnail to offer.
 */
class SeguirViendoShelfSource(
    private val getContinueWatching: GetContinueWatching,
    override val timeoutMs: Long = 5_000L,
) : ShelfSource {

    override val id: ShelfId = ShelfId.SEGUIR_VIENDO
    override val displayTitle: String = "Seguir viendo"

    override suspend fun load(): CatalogResult {
        val videos = getContinueWatching().map { entry ->
            Video(
                id = entry.videoId,
                title = entry.title,
                channelName = entry.channel,
                thumbnailUrl = "https://i.ytimg.com/vi/${entry.videoId.value}/hqdefault.jpg",
                viewCount = null,
                publishedText = null,
            )
        }
        return if (videos.isEmpty()) {
            CatalogResult.Empty
        } else {
            CatalogResult.Success(listOf(Shelf(id = id, title = displayTitle, videos = videos)))
        }
    }
}
