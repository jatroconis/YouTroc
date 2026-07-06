package com.youtroc.core.domain.catalog

/** Why [HomeFeedDecision.Terminal] was reached, so the feature edge can pick a Spanish message. */
enum class TerminalKind { OFFLINE, ERROR, EMPTY }

/**
 * The Home screen's rendering decision, derived from a [HomeFeedSnapshot] by
 * [HomeFeedPolicy]. Mirrors [CatalogResult]'s vocabulary at the composed-feed
 * level, monotonic toward [Content]: once any shelf lands, the decision never
 * demotes back to [Terminal] (REQ-HF2/HF13).
 */
sealed interface HomeFeedDecision {
    data object Loading : HomeFeedDecision
    data class Terminal(val kind: TerminalKind) : HomeFeedDecision
    data class Content(val shelves: List<Shelf>) : HomeFeedDecision
}
