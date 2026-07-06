package com.youtroc.feature.catalog

import com.youtroc.core.domain.catalog.Shelf
import com.youtroc.core.domain.catalog.ShelfId
import com.youtroc.core.domain.catalog.Video
import com.youtroc.core.ui.component.VideoCardUi
import java.util.Locale

/**
 * Feature-edge mapping: turns the pure domain catalog types into presentation
 * models. The Spanish user-facing copy ("N vistas · fecha", shelf titles) lives
 * here — never in `:core:domain` (which stays free of localized strings) nor in
 * `:core:ui` (which stays ignorant of the domain).
 */

/** Maps a domain [Shelf] into a presentation [HomeShelf], applying [shelfDisplayTitle]. */
internal fun toHomeShelf(shelf: Shelf): HomeShelf = HomeShelf(
    id = shelf.id,
    title = shelfDisplayTitle(shelf.id, shelf.title),
    videos = shelf.videos.map { it.toVideoCardUi() },
)

/** Maps a domain [Video] into a [VideoCardUi] with a Spanish [CatalogMetaFormatter] meta line. */
internal fun Video.toVideoCardUi(): VideoCardUi = VideoCardUi(
    id = id.value,
    thumbnailUrl = thumbnailUrl,
    title = title,
    channel = channelName,
    meta = CatalogMetaFormatter.format(viewCount, publishedText),
)

/**
 * Maps a [Shelf] to its Spanish display title, keyed on [id] (N7) -- the
 * composer stamps only [id] plus a PASSTHROUGH [sourceTitle], never Spanish
 * copy, onto [Shelf] (`:core:domain` stays free of localized strings). Every
 * shelf but [ShelfId.TENDENCIAS] has one fixed Spanish title regardless of
 * [sourceTitle]. [ShelfId.TENDENCIAS] is special: InnerTube's own "Popular en
 * {region}" title is ALREADY Spanish and passes through unchanged (M4), while
 * the NewPipe-fallback kiosk's English "Trending" name is mapped to
 * "Tendencias" (REQ-HF6).
 */
internal fun shelfDisplayTitle(id: ShelfId, sourceTitle: String): String = when (id) {
    ShelfId.SEGUIR_VIENDO -> "Seguir viendo"
    ShelfId.SHORTS -> "Shorts"
    ShelfId.MUSICA -> "Música"
    ShelfId.VIDEOJUEGOS -> "Videojuegos"
    ShelfId.NOTICIAS -> "Noticias"
    ShelfId.DEPORTES -> "Deportes"
    ShelfId.CINE -> "Cine y tráilers"
    ShelfId.EN_VIVO -> "En vivo"
    ShelfId.TENDENCIAS -> when (sourceTitle) {
        "Trending" -> "Tendencias"
        else -> sourceTitle
    }
}

/**
 * Formats a raw view count + source-provided textual date into the Spanish
 * "N vistas · fecha" meta line (e.g. "1.6 B vistas · hace 15 a."). Either part
 * may be missing (an unfamiliar/negative view count, or no date from the
 * source) — the formatter simply omits what it doesn't have rather than
 * showing a placeholder.
 */
object CatalogMetaFormatter {

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
