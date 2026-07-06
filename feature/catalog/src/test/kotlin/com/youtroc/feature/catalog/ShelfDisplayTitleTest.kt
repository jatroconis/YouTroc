package com.youtroc.feature.catalog

import com.youtroc.core.domain.catalog.ShelfId
import kotlin.test.Test
import kotlin.test.assertEquals

class ShelfDisplayTitleTest {

    @Test
    fun `TENDENCIAS maps the NewPipe fallback's English kiosk name to Spanish`() {
        assertEquals("Tendencias", shelfDisplayTitle(ShelfId.TENDENCIAS, "Trending"))
    }

    @Test
    fun `TENDENCIAS passes through InnerTube's own Popular en region title unchanged`() {
        assertEquals("Popular en Bogotá", shelfDisplayTitle(ShelfId.TENDENCIAS, "Popular en Bogotá"))
    }

    @Test
    fun `SEGUIR_VIENDO maps to Seguir viendo regardless of source title`() {
        assertEquals("Seguir viendo", shelfDisplayTitle(ShelfId.SEGUIR_VIENDO, "anything"))
    }

    @Test
    fun `SHORTS maps to Shorts regardless of source title`() {
        assertEquals("Shorts", shelfDisplayTitle(ShelfId.SHORTS, "anything"))
    }

    @Test
    fun `MUSICA maps to Musica with an accent`() {
        assertEquals("Música", shelfDisplayTitle(ShelfId.MUSICA, "anything"))
    }

    @Test
    fun `VIDEOJUEGOS maps to Videojuegos`() {
        assertEquals("Videojuegos", shelfDisplayTitle(ShelfId.VIDEOJUEGOS, "anything"))
    }

    @Test
    fun `NOTICIAS maps to Noticias`() {
        assertEquals("Noticias", shelfDisplayTitle(ShelfId.NOTICIAS, "anything"))
    }

    @Test
    fun `DEPORTES maps to Deportes`() {
        assertEquals("Deportes", shelfDisplayTitle(ShelfId.DEPORTES, "anything"))
    }

    @Test
    fun `CINE maps to Cine y trailers`() {
        assertEquals("Cine y tráilers", shelfDisplayTitle(ShelfId.CINE, "anything"))
    }

    @Test
    fun `EN_VIVO maps to En vivo`() {
        assertEquals("En vivo", shelfDisplayTitle(ShelfId.EN_VIVO, "anything"))
    }
}
