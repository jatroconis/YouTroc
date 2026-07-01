package com.youtroc.data.extraction

import com.youtroc.core.domain.stream.StreamResult
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Deterministic, network-free verification of the failure-mapping policy: every
 * NewPipe exception must become a typed [StreamResult], never escape the boundary.
 */
class ExtractionErrorMappingTest {

    @Test
    fun `content-not-available maps to NotAvailable`() {
        assertEquals(
            StreamResult.NotAvailable,
            ContentNotAvailableException("video is private").toStreamResult(),
        )
    }

    @Test
    fun `an IO failure maps to Offline`() {
        assertEquals(
            StreamResult.Offline,
            IOException("network unreachable").toStreamResult(),
        )
    }

    @Test
    fun `a reCaptcha challenge maps to Error`() {
        val result = ReCaptchaException("429", "https://youtube.com").toStreamResult()

        assertIs<StreamResult.Error>(result)
    }

    @Test
    fun `an unexpected failure maps to Error carrying the cause`() {
        val boom = IllegalStateException("boom")

        assertEquals(StreamResult.Error(boom), boom.toStreamResult())
    }
}
