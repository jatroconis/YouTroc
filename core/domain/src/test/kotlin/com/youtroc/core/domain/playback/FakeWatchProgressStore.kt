package com.youtroc.core.domain.playback

import com.youtroc.core.domain.video.VideoId

/**
 * In-memory test double for the [WatchProgressStore] port. No disk, no
 * Android — this is the whole point of a port: the domain and its consumers
 * are testable in isolation.
 */
class FakeWatchProgressStore : WatchProgressStore {

    private val saved = mutableMapOf<VideoId, WatchHistoryEntry>()

    override suspend fun save(
        videoId: VideoId,
        position: PlaybackPosition,
        durationMs: Long,
        title: String,
        channel: String,
    ) {
        saved[videoId] = WatchHistoryEntry(
            videoId = videoId,
            title = title,
            channel = channel,
            watchedAt = System.currentTimeMillis(),
            position = position,
            durationMs = durationMs,
        )
    }

    override suspend fun load(videoId: VideoId): PlaybackPosition? = saved[videoId]?.position

    override suspend fun readAll(): List<WatchHistoryEntry> = saved.values.sortedByDescending { it.watchedAt }
}
