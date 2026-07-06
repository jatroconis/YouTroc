package com.youtroc.feature.playback

/**
 * A single item in the Shorts pager queue: enough identity + display metadata
 * to resolve streams ([id]) and render [ShortsOverlay] ([title]/[channel]),
 * without ever needing a full [com.youtroc.core.ui.component.VideoCardUi]
 * (thumbnail/meta are never shown in the player).
 *
 * [id] stays a plain `String` (NOT
 * [com.youtroc.core.domain.video.VideoId]) for the same reason
 * `PlayerViewModel`/`UpNextViewModel` keep their own nav-arg-sourced ids as
 * `String` — the composition root threads this straight from a Navigation
 * Compose argument, and `VideoId`'s `require(non-blank)` init check must not
 * throw before this reaches a guarded conversion.
 *
 * This type — not the literal 4-param ctor the tasks doc enumerates
 * (`player`, `getPlayableStreams`, `shortsIds: List<VideoId>`, `startIndex`)
 * — is this batch's one necessary, documented deviation: [ShortsOverlay]
 * (task 3.3.3) requires real title/`@channel` text, which bare
 * [com.youtroc.core.domain.video.VideoId]s cannot carry. See apply-progress
 * for the full rationale.
 */
data class ShortsQueueItem(
    val id: String,
    val title: String,
    val channel: String,
)
