package com.youtroc.core.domain.catalog

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetHomeFeedTest {

    @Test
    fun `returns the shelves the catalog resolves`() = runTest {
        val shelves = listOf(Shelf(title = "Trending", videos = emptyList()))
        val useCase = GetHomeFeed(FakeVideoCatalog(CatalogResult.Success(shelves)))

        val result = useCase()

        assertEquals(CatalogResult.Success(shelves), result)
    }

    @Test
    fun `propagates Empty without throwing`() = runTest {
        val useCase = GetHomeFeed(FakeVideoCatalog(CatalogResult.Empty))

        assertEquals(CatalogResult.Empty, useCase())
    }

    @Test
    fun `propagates Offline without throwing`() = runTest {
        val useCase = GetHomeFeed(FakeVideoCatalog(CatalogResult.Offline))

        assertEquals(CatalogResult.Offline, useCase())
    }

    @Test
    fun `propagates Error without throwing`() = runTest {
        val cause = IllegalStateException("boom")
        val useCase = GetHomeFeed(FakeVideoCatalog(CatalogResult.Error(cause)))

        assertEquals(CatalogResult.Error(cause), useCase())
    }
}
