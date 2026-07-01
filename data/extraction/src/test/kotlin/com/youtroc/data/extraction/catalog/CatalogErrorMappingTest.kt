package com.youtroc.data.extraction.catalog

import com.youtroc.core.domain.catalog.CatalogResult
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Deterministic, network-free verification of the failure-mapping policy: every
 * NewPipe exception must become a typed [CatalogResult], never escape the boundary.
 *
 * Mirrors [com.youtroc.data.extraction.ExtractionErrorMappingTest].
 */
class CatalogErrorMappingTest {

    @Test
    fun `content-not-available maps to Empty`() {
        assertEquals(
            CatalogResult.Empty,
            ContentNotAvailableException("trending is degraded").toCatalogResult(),
        )
    }

    @Test
    fun `an IO failure maps to Offline`() {
        assertEquals(
            CatalogResult.Offline,
            IOException("network unreachable").toCatalogResult(),
        )
    }

    @Test
    fun `a reCaptcha challenge maps to Error`() {
        val result = ReCaptchaException("429", "https://youtube.com").toCatalogResult()

        assertIs<CatalogResult.Error>(result)
    }

    @Test
    fun `an unexpected failure maps to Error carrying the cause`() {
        val boom = IllegalStateException("boom")

        assertEquals(CatalogResult.Error(boom), boom.toCatalogResult())
    }
}
