package com.youtroc.core.domain.detail

/**
 * Typed outcome of asking for a single video's detail.
 *
 * Mirrors [com.youtroc.core.domain.stream.StreamResult]: failure is part of
 * the domain vocabulary, not an exception, and there is NO Empty case — an
 * empty related list is still a valid [Success]. Adapters in :data:extraction
 * map their own IO/parse errors onto these cases, so nothing throws across
 * the port boundary and the UI can render a deterministic state for each
 * outcome.
 */
sealed interface DetailResult {

    /** Detail resolved and ready to render — [VideoDetailInfo.related] may be empty. */
    data class Success(val detail: VideoDetailInfo) : DetailResult

    /** The video exists but cannot be resolved anonymously (age-gated, removed, region-locked). */
    data object NotAvailable : DetailResult

    /** No network reachable. */
    data object Offline : DetailResult

    /** Extraction failed unexpectedly; [cause] is for logging, not for control flow. */
    data class Error(val cause: Throwable) : DetailResult
}
