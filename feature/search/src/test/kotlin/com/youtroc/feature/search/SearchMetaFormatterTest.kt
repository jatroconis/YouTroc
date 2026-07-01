package com.youtroc.feature.search

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchMetaFormatterTest {

    @Test
    fun `abbreviates billions with one decimal`() {
        assertEquals("1.6 B vistas", SearchMetaFormatter.format(1_600_000_000L, null))
    }

    @Test
    fun `abbreviates millions with one decimal`() {
        assertEquals("5.4 M vistas", SearchMetaFormatter.format(5_400_000L, null))
    }

    @Test
    fun `abbreviates thousands without a decimal`() {
        assertEquals("532 K vistas", SearchMetaFormatter.format(532_000L, null))
    }

    @Test
    fun `keeps counts under a thousand as a plain number`() {
        assertEquals("842 vistas", SearchMetaFormatter.format(842L, null))
    }

    @Test
    fun `assembles views and published date with a middle dot`() {
        assertEquals(
            "1.6 B vistas · hace 15 a.",
            SearchMetaFormatter.format(1_600_000_000L, "hace 15 a."),
        )
    }

    @Test
    fun `omits the views part when viewCount is null`() {
        assertEquals("hace 15 a.", SearchMetaFormatter.format(null, "hace 15 a."))
    }

    @Test
    fun `omits the date part when publishedText is null`() {
        assertEquals("1.6 B vistas", SearchMetaFormatter.format(1_600_000_000L, null))
    }

    @Test
    fun `omits the date part when publishedText is blank`() {
        assertEquals("1.6 B vistas", SearchMetaFormatter.format(1_600_000_000L, "   "))
    }

    @Test
    fun `returns an empty string when both parts are missing`() {
        assertEquals("", SearchMetaFormatter.format(null, null))
    }
}
