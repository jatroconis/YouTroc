package com.youtroc.feature.playback

import com.youtroc.core.domain.playback.PlaybackPosition
import com.youtroc.core.domain.playback.WatchProgressStore
import com.youtroc.core.domain.video.VideoId

/**
 * In-memory test double for the [WatchProgressStore] port. Test source sets
 * aren't shared across Gradle modules, so this mirrors
 * `core/domain`'s `FakeWatchProgressStore` locally for this module's tests.
 */
class FakeWatchProgressStore : WatchProgressStore {

    private val saved = mutableMapOf<VideoId, Pair<PlaybackPosition, Long>>()

    override suspend fun save(videoId: VideoId, position: PlaybackPosition, durationMs: Long) {
        saved[videoId] = position to durationMs
    }

    override suspend fun load(videoId: VideoId): PlaybackPosition? = saved[videoId]?.first
}
