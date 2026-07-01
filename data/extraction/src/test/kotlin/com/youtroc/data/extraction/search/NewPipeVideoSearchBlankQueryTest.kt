package com.youtroc.data.extraction.search

import com.youtroc.core.domain.search.SearchResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Deterministic, network-free verification of the blank-query guard: defense
 * in depth for gate MAJOR-1 (the primary guard lives in `SearchViewModel`,
 * WU-2). A blank/whitespace query must never reach NewPipe bootstrap or the
 * network.
 */
class NewPipeVideoSearchBlankQueryTest {

    @Test
    fun `blank query returns Empty without bootstrapping NewPipe`() = runTest {
        var bootstrapCalled = false
        val search = NewPipeVideoSearch(bootstrap = { bootstrapCalled = true })

        val result = search.search("   ")

        assertEquals(SearchResult.Empty, result)
        assertFalse(bootstrapCalled, "bootstrap must not be called for a blank query")
    }

    @Test
    fun `empty string query returns Empty without bootstrapping NewPipe`() = runTest {
        var bootstrapCalled = false
        val search = NewPipeVideoSearch(bootstrap = { bootstrapCalled = true })

        val result = search.search("")

        assertEquals(SearchResult.Empty, result)
        assertFalse(bootstrapCalled, "bootstrap must not be called for a blank query")
    }
}
