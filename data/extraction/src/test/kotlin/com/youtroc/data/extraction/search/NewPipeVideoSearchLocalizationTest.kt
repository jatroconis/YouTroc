package com.youtroc.data.extraction.search

import com.youtroc.data.extraction.NewPipeBootstrap
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Deterministic, network-free verification that `hl=es` (and, when provided, a
 * region) is applied to the search extractor BEFORE `fetchPage()` is ever
 * called. Building a `SearchExtractor` and forcing its localization does not
 * touch the network — only `fetchPage()` does — so this exercises the exact
 * same construction path [NewPipeVideoSearch.search] uses, without requiring
 * the opt-in live test. [NewPipeBootstrap] must run first: the extractor's
 * constructor requires NewPipe's global downloader to be registered, even
 * though it performs no I/O itself.
 */
class NewPipeVideoSearchLocalizationTest {

    @BeforeTest
    fun bootstrap() {
        NewPipeBootstrap.ensureInitialized()
    }

    @Test
    fun `forces hl=es localization before fetchPage is ever called`() {
        val extractor = buildSearchExtractor(
            query = "lofi",
            localization = Localization("es"),
            regionCode = null,
        )

        assertEquals(Localization("es"), extractor.extractorLocalization)
    }

    @Test
    fun `forces the region content country when a non-blank regionCode is given`() {
        val extractor = buildSearchExtractor(
            query = "lofi",
            localization = Localization("es"),
            regionCode = "AR",
        )

        assertEquals(ContentCountry("AR"), extractor.extractorContentCountry)
    }

    @Test
    fun `does not force a content country for a blank regionCode`() {
        val extractor = buildSearchExtractor(
            query = "lofi",
            localization = Localization("es"),
            regionCode = "   ",
        )

        assertEquals(ContentCountry.DEFAULT, extractor.extractorContentCountry)
    }
}
