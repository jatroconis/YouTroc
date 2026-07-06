package com.youtroc.core.domain.catalog

/**
 * [ComposeHomeFeed]'s emission: the shelves resolved so far, plus the lead
 * source's own (bounded) outcome, used to classify a terminal state when
 * [shelves] is empty. [leadOutcome] is set ONCE by the lead's bounded
 * attempt and never mutated by its unbounded late leg (N3) -- a late fill
 * only ever appends to [shelves].
 */
data class HomeFeedSnapshot(
    val shelves: List<Shelf>,
    val leadOutcome: CatalogResult?,
)
