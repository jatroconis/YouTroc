package com.youtroc.core.domain.catalog

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Deterministic verification of [HomeFeedPolicy.decide]: non-empty
 * [HomeFeedSnapshot.shelves] ALWAYS wins (REQ-HF2/HF13 -- monotonic toward
 * Content, never demoted by a later lead-outcome change); only an empty
 * snapshot consults [HomeFeedSnapshot.leadOutcome] to classify a terminal
 * kind.
 */
class HomeFeedPolicyTest {

    private val tendenciasShelf = Shelf(id = ShelfId.TENDENCIAS, title = "Tendencias", videos = emptyList())

    @Test
    fun `empty shelves with no lead outcome yet stays Loading`() {
        val snapshot = HomeFeedSnapshot(shelves = emptyList(), leadOutcome = null)

        assertEquals(HomeFeedDecision.Loading, HomeFeedPolicy.decide(snapshot))
    }

    @Test
    fun `non-empty shelves decide Content even when the lead outcome is Offline`() {
        val snapshot = HomeFeedSnapshot(shelves = listOf(tendenciasShelf), leadOutcome = CatalogResult.Offline)

        assertEquals(HomeFeedDecision.Content(listOf(tendenciasShelf)), HomeFeedPolicy.decide(snapshot))
    }

    @Test
    fun `empty shelves with an Offline lead outcome decide Terminal OFFLINE`() {
        val snapshot = HomeFeedSnapshot(shelves = emptyList(), leadOutcome = CatalogResult.Offline)

        assertEquals(HomeFeedDecision.Terminal(TerminalKind.OFFLINE), HomeFeedPolicy.decide(snapshot))
    }

    @Test
    fun `empty shelves with an Error lead outcome decide Terminal ERROR`() {
        val snapshot = HomeFeedSnapshot(
            shelves = emptyList(),
            leadOutcome = CatalogResult.Error(IllegalStateException("boom")),
        )

        assertEquals(HomeFeedDecision.Terminal(TerminalKind.ERROR), HomeFeedPolicy.decide(snapshot))
    }

    @Test
    fun `empty shelves with an Empty lead outcome decide Terminal EMPTY`() {
        val snapshot = HomeFeedSnapshot(shelves = emptyList(), leadOutcome = CatalogResult.Empty)

        assertEquals(HomeFeedDecision.Terminal(TerminalKind.EMPTY), HomeFeedPolicy.decide(snapshot))
    }

    @Test
    fun `REQ-HF13 -- total thematic failure with Tendencias succeeding decides Content with one shelf`() {
        val snapshot = HomeFeedSnapshot(
            shelves = listOf(tendenciasShelf),
            leadOutcome = CatalogResult.Success(listOf(tendenciasShelf)),
        )

        assertEquals(HomeFeedDecision.Content(listOf(tendenciasShelf)), HomeFeedPolicy.decide(snapshot))
    }
}
