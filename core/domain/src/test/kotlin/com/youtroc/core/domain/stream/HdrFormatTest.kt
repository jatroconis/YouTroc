package com.youtroc.core.domain.stream

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HdrFormatTest {

    @Test
    fun `SDR is not HDR`() {
        assertFalse(HdrFormat.SDR.isHdr)
    }

    @Test
    fun `HDR10 is HDR`() {
        assertTrue(HdrFormat.HDR10.isHdr)
    }

    @Test
    fun `HLG is HDR`() {
        assertTrue(HdrFormat.HLG.isHdr)
    }
}
