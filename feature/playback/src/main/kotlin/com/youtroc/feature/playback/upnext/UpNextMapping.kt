package com.youtroc.feature.playback.upnext

import com.youtroc.core.domain.catalog.Video
import com.youtroc.core.domain.detail.VideoDetailInfo
import com.youtroc.core.ui.component.VideoCardUi
import java.util.Locale

/**
 * Feature-edge mapping: turns the pure domain [VideoDetailInfo]/[Video]
 * types into the presentation [VideoDetailUi]/[VideoCardUi]. The Spanish
 * user-facing copy ("N vistas · fecha") lives here — never in
 * `:core:domain` (which stays free of localized strings) nor in `:core:ui`
 * (which stays ignorant of the domain).
 *
 * [DetailMetaFormatter] is a deliberate duplicate of `:feature:search`'s
 * `SearchMetaFormatter`/`:feature:catalog`'s `CatalogMetaFormatter` (design's
 * rule-of-three deferral, now DUE — kept duplicated for zero-touch on shipped
 * modules; consolidating into a shared `:core:ui` helper is a separate future
 * cleanup).
 */

/** Maps a domain [VideoDetailInfo] into a [VideoDetailUi] with a Spanish [DetailMetaFormatter] meta line. */
internal fun VideoDetailInfo.toDetailUi(): VideoDetailUi = VideoDetailUi(
    title = title,
    channel = channelName,
    meta = DetailMetaFormatter.format(viewCount, publishedText),
    description = description.orEmpty(),
    related = related.map { it.toVideoCardUi() },
)

/** Maps a domain [Video] into a [VideoCardUi] with a Spanish [DetailMetaFormatter] meta line. */
internal fun Video.toVideoCardUi(): VideoCardUi = VideoCardUi(
    id = id.value,
    thumbnailUrl = thumbnailUrl,
    title = title,
    channel = channelName,
    meta = DetailMetaFormatter.format(viewCount, publishedText),
)

/**
 * Formats a raw view count + source-provided textual date into the Spanish
 * "N vistas · fecha" meta line (e.g. "1.6 B vistas · hace 15 a."). Either part
 * may be missing (an unfamiliar/negative view count, or no date from the
 * source) — the formatter simply omits what it doesn't have rather than
 * showing a placeholder.
 */
object DetailMetaFormatter {

    private const val BILLION = 1_000_000_000L
    private const val MILLION = 1_000_000L
    private const val THOUSAND = 1_000L

    fun format(viewCount: Long?, publishedText: String?): String {
        val parts = listOfNotNull(
            viewCount?.let { "${abbreviate(it)} vistas" },
            publishedText?.takeIf { it.isNotBlank() },
        )
        return parts.joinToString(" · ")
    }

    private fun abbreviate(count: Long): String = when {
        count >= BILLION -> "%.1f B".format(Locale.US, count / BILLION.toDouble())
        count >= MILLION -> "%.1f M".format(Locale.US, count / MILLION.toDouble())
        count >= THOUSAND -> "${count / THOUSAND} K"
        else -> count.toString()
    }
}
