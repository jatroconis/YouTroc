package com.youtroc.data.extraction.search

import com.youtroc.core.domain.search.SearchResult
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import java.io.IOException

/**
 * Maps a NewPipe failure onto the domain's typed outcome. Kept as an internal
 * top-level function so the mapping policy is deterministic and unit-testable
 * without any network.
 *
 * Order matters: [ContentNotAvailableException] is a subtype of [ExtractionException].
 * Mirrors [com.youtroc.data.extraction.catalog.toCatalogResult]. `StreamInfoItem`
 * -> [com.youtroc.core.domain.catalog.Video] mapping (`toVideoOrNull`/`pickThumbnail`)
 * is REUSED from `com.youtroc.data.extraction.catalog.CatalogMapping` — same module,
 * already covered by `StreamInfoItemMappingTest`.
 */
internal fun Throwable.toSearchResult(): SearchResult = when (this) {
    is ContentNotAvailableException -> SearchResult.Empty
    is IOException -> SearchResult.Offline
    is ExtractionException -> SearchResult.Error(this)
    else -> SearchResult.Error(this)
}
