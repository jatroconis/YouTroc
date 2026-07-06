package com.youtroc.core.domain.playback

/**
 * Application entry point for "Seguir viendo" (REQ-HF9): filters
 * [WatchProgressStore.readAll] down to videos worth resuming -- excluding
 * anything watched to >=95% of its duration (finished, in practice) -- and
 * excludes any entry whose title/channel is blank as a defense-in-depth net
 * (F1), on top of the guards already applied by the store's own adapter.
 * Ordering is entirely [WatchProgressStore.readAll]'s responsibility
 * (REQ-HF8) -- this use case never re-sorts.
 */
class GetContinueWatching(
    private val store: WatchProgressStore,
) {
    suspend operator fun invoke(): List<WatchHistoryEntry> =
        store.readAll().filter {
            it.durationMs > 0 &&
                it.position.positionMs < it.durationMs * 95 / 100 &&
                it.title.isNotBlank() &&
                it.channel.isNotBlank()
        }
}
