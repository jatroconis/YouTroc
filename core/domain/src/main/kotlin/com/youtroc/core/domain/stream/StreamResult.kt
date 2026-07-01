package com.youtroc.core.domain.stream

/**
 * Typed outcome of asking for a video's playable streams.
 *
 * Failure is part of the domain vocabulary, not an exception. Adapters in
 * :data:extraction map their own IO/parse errors onto these cases, so nothing
 * throws across the port boundary and the UI can render a deterministic state
 * for each outcome.
 */
sealed interface StreamResult {

    /** Streams resolved and ready to play — ad-free by construction. */
    data class Success(val streams: PlayableStreams) : StreamResult

    /** The video exists but cannot be played anonymously (age-gated, removed, region-locked). */
    data object NotAvailable : StreamResult

    /** No network reachable. */
    data object Offline : StreamResult

    /** Extraction failed unexpectedly; [cause] is for logging, not for control flow. */
    data class Error(val cause: Throwable) : StreamResult
}
