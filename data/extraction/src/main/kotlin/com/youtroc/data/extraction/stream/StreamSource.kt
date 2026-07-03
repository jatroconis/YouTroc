package com.youtroc.data.extraction.stream

/**
 * Which engine actually resolved a video's streams inside
 * [LadderStreamProvider]: the primary own android_vr engine ([ANDROID_VR]),
 * the own ios fallback-rung engine ([IOS]), or the NewPipe safety net
 * ([FALLBACK]). Lives in `:data:extraction` (NOT `:core:domain`) — it is an
 * adapter-internal signal used to gate speculative prefetch, not a domain
 * concept `StreamResult`/`PlayableStreams` need to know about.
 *
 * [ANDROID_VR] is the ONLY value that should ever trigger speculative
 * prefetch of the next up-next video (`PlaybackRoute`'s gate, `:app`-only):
 * both [IOS] and [FALLBACK] mean the primary android_vr engine did not
 * succeed for the current video — presumed throttled — so prefetch SKIPS
 * rather than routing the next video via a non-primary rung.
 */
enum class StreamSource { ANDROID_VR, IOS, FALLBACK }
