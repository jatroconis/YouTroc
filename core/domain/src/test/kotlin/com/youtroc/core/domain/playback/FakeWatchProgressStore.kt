package com.youtroc.core.domain.playback

import com.youtroc.core.domain.video.VideoId

/**
 * In-memory test double for the [WatchProgressStore] port. No disk, no
 * Android — this is the whole point of a port: the domain and its consumers
 * are testable in isolation.
 */
class FakeWatchProgressStore : WatchProgressStore {

    private val saved = mutableMapOf<VideoId, Pair<PlaybackPosition, Long>>()

    override suspend fun save(videoId: VideoId, position: PlaybackPosition, durationMs: Long) {
        saved[videoId] = position to durationMs
    }

    override suspend fun load(videoId: VideoId): PlaybackPosition? = saved[videoId]?.first
}
