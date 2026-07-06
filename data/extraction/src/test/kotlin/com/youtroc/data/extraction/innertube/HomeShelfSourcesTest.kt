package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.catalog.CatalogResult
import com.youtroc.core.domain.catalog.ShelfId
import com.youtroc.core.domain.catalog.ShelfSource
import okhttp3.OkHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Deterministic verification of [deriveTimeoutClient] (M1 -- never mutates
 * the shared base client, only derives), [homeShelfSources] (REQ-HF1's
 * fixed 7-shelf order), and [assembleFullHomeShelfSources] (S3 wiring --
 * design rev3's full 9-shelf ordering note: SeguirViendo, Tendencias, Shorts,
 * then the remaining 6 thematic sources).
 */
class HomeShelfSourcesTest {

    private fun stubSource(id: ShelfId): ShelfSource = object : ShelfSource {
        override val id: ShelfId = id
        override val displayTitle: String? = null
        override val timeoutMs: Long = 1_000L
        override suspend fun load(): CatalogResult = CatalogResult.Empty
    }

    @Test
    fun `deriveTimeoutClient sets callTimeout on a derived client without mutating the base client`() {
        val base = OkHttpClient()

        val derived = deriveTimeoutClient(base, 1_234L)

        assertEquals(1_234L, derived.callTimeoutMillis.toLong())
        assertEquals(0L, base.callTimeoutMillis.toLong())
    }

    @Test
    fun `homeShelfSources builds the 7 production sources in REQ-HF1 order`() {
        val sources = homeShelfSources(regionCode = "CO")

        assertEquals(
            listOf(
                ShelfId.TENDENCIAS,
                ShelfId.MUSICA,
                ShelfId.VIDEOJUEGOS,
                ShelfId.NOTICIAS,
                ShelfId.DEPORTES,
                ShelfId.CINE,
                ShelfId.EN_VIVO,
            ),
            sources.map { it.id },
        )
    }

    @Test
    fun `assembleFullHomeShelfSources inserts SeguirViendo first and Shorts right after the Tendencias lead`() {
        val thematic = homeShelfSources(regionCode = "CO")
        val seguirViendo = stubSource(ShelfId.SEGUIR_VIENDO)
        val shorts = stubSource(ShelfId.SHORTS)

        val full = assembleFullHomeShelfSources(thematic, seguirViendo = seguirViendo, shorts = shorts)

        assertEquals(
            listOf(
                ShelfId.SEGUIR_VIENDO,
                ShelfId.TENDENCIAS,
                ShelfId.SHORTS,
                ShelfId.MUSICA,
                ShelfId.VIDEOJUEGOS,
                ShelfId.NOTICIAS,
                ShelfId.DEPORTES,
                ShelfId.CINE,
                ShelfId.EN_VIVO,
            ),
            full.map { it.id },
        )
    }
}
