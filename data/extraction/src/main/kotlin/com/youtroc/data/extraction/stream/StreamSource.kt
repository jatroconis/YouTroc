package com.youtroc.data.extraction.stream

/**
 * Which engine actually resolved a video's streams inside
 * [FallbackStreamProvider]: the own android_vr engine ([OWN]) or the NewPipe
 * safety net ([FALLBACK]). Lives in `:data:extraction` (NOT `:core:domain`) —
 * it is an adapter-internal signal used to gate speculative prefetch, not a
 * domain concept `StreamResult`/`PlayableStreams` need to know about.
 */
enum class StreamSource { OWN, FALLBACK }
