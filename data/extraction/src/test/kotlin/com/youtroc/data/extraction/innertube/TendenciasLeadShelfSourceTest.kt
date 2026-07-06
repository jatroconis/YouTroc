package com.youtroc.data.extraction.innertube

import com.youtroc.core.domain.catalog.CatalogResult
import com.youtroc.core.domain.catalog.ShelfId
import com.youtroc.core.domain.catalog.VideoCatalog
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TendenciasLeadShelfSourceTest {

    @Test
    fun `id is TENDENCIAS`() {
        val source = TendenciasLeadShelfSource(
            innerTube = FakeVideoCatalog(CatalogResult.Empty),
            newPipe = FakeVideoCatalog(CatalogResult.Empty),
        )

        assertEquals(ShelfId.TENDENCIAS, source.id)
    }

    @Test
    fun `load delegates to the injected InnerTube catalog`() = runTest {
        val expected = CatalogResult.Empty
        val source = TendenciasLeadShelfSource(
            innerTube = FakeVideoCatalog(expected),
            newPipe = FakeVideoCatalog(CatalogResult.Offline),
        )

        assertEquals(expected, source.load())
    }

    @Test
    fun `loadFallback delegates to the injected NewPipe catalog`() = runTest {
        val expected = CatalogResult.Offline
        val source = TendenciasLeadShelfSource(
            innerTube = FakeVideoCatalog(CatalogResult.Empty),
            newPipe = FakeVideoCatalog(expected),
        )

        assertEquals(expected, source.loadFallback())
    }
}

private class FakeVideoCatalog(private val result: CatalogResult) : VideoCatalog {
    override suspend fun trending(): CatalogResult = result
}
