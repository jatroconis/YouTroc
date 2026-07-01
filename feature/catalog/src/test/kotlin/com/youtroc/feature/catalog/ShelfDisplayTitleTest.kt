package com.youtroc.feature.catalog

import kotlin.test.Test
import kotlin.test.assertEquals

class ShelfDisplayTitleTest {

    @Test
    fun `maps the Trending kiosk name to the Spanish shelf title`() {
        assertEquals("Tendencia", shelfDisplayTitle("Trending"))
    }

    @Test
    fun `passes through unknown shelf titles unchanged`() {
        assertEquals("Music", shelfDisplayTitle("Music"))
    }
}
