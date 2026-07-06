package com.youtroc.core.domain.catalog

/**
 * Pure decision function turning a [HomeFeedSnapshot] into a
 * [HomeFeedDecision]. Non-empty [HomeFeedSnapshot.shelves] ALWAYS wins
 * (REQ-HF2/HF13 -- a shelf lands and never gets un-rendered by a later
 * lead-outcome change); only an empty snapshot consults
 * [HomeFeedSnapshot.leadOutcome] to pick a terminal kind, and a null/pending
 * lead outcome with nothing else resolved stays [HomeFeedDecision.Loading].
 * Every branch matches an EXPLICIT `snapshot.leadOutcome is CatalogResult.X`
 * subject (F6 -- fixes a bare-subject typo from an earlier design revision).
 */
object HomeFeedPolicy {

    fun decide(snapshot: HomeFeedSnapshot): HomeFeedDecision = when {
        snapshot.shelves.isNotEmpty() -> HomeFeedDecision.Content(snapshot.shelves)
        snapshot.leadOutcome is CatalogResult.Offline -> HomeFeedDecision.Terminal(TerminalKind.OFFLINE)
        snapshot.leadOutcome is CatalogResult.Error -> HomeFeedDecision.Terminal(TerminalKind.ERROR)
        snapshot.leadOutcome != null -> HomeFeedDecision.Terminal(TerminalKind.EMPTY)
        else -> HomeFeedDecision.Loading
    }
}
