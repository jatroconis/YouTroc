package com.youtroc.data.extraction.detail

import com.youtroc.core.domain.detail.DetailResult
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Deterministic, network-free verification of the failure-mapping policy: every
 * NewPipe exception must become a typed [DetailResult], never escape the boundary.
 *
 * Mirrors [com.youtroc.data.extraction.toStreamResult] (NOT catalog/search's
 * Empty mapping) — a single unresolvable video is a per-video unavailability.
 */
class DetailErrorMappingTest {

    @Test
    fun `content-not-available maps to NotAvailable`() {
        assertEquals(
            DetailResult.NotAvailable,
            ContentNotAvailableException("age-gated").toDetailResult(),
        )
    }

    @Test
    fun `an IO failure maps to Offline`() {
        assertEquals(
            DetailResult.Offline,
            IOException("network unreachable").toDetailResult(),
        )
    }

    @Test
    fun `a reCaptcha challenge maps to Error`() {
        val result = ReCaptchaException("429", "https://youtube.com").toDetailResult()

        assertIs<DetailResult.Error>(result)
    }

    @Test
    fun `an unexpected failure maps to Error carrying the cause`() {
        val boom = IllegalStateException("boom")

        assertEquals(DetailResult.Error(boom), boom.toDetailResult())
    }
}
