package com.youtroc.data.extraction.search

import com.youtroc.core.domain.search.SearchResult
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Deterministic, network-free verification of the failure-mapping policy: every
 * NewPipe exception must become a typed [SearchResult], never escape the boundary.
 *
 * Mirrors [com.youtroc.data.extraction.catalog.CatalogErrorMappingTest].
 */
class SearchErrorMappingTest {

    @Test
    fun `content-not-available maps to Empty`() {
        assertEquals(
            SearchResult.Empty,
            ContentNotAvailableException("search yielded nothing usable").toSearchResult(),
        )
    }

    @Test
    fun `an IO failure maps to Offline`() {
        assertEquals(
            SearchResult.Offline,
            IOException("network unreachable").toSearchResult(),
        )
    }

    @Test
    fun `a reCaptcha challenge maps to Error`() {
        val result = ReCaptchaException("429", "https://youtube.com").toSearchResult()

        assertIs<SearchResult.Error>(result)
    }

    @Test
    fun `an unexpected failure maps to Error carrying the cause`() {
        val boom = IllegalStateException("boom")

        assertEquals(SearchResult.Error(boom), boom.toSearchResult())
    }
}
