package com.youtroc.core.domain.playback

import com.youtroc.core.domain.video.VideoId

/**
 * Port: persists and retrieves how far a user got into a video, so playback can
 * offer to resume. The domain owns this contract; `:data:persistence` implements
 * it. Local-only by construction — no method here can carry progress data off
 * the device.
 */
interface WatchProgressStore {
    suspend fun save(videoId: VideoId, position: PlaybackPosition, durationMs: Long)
    suspend fun load(videoId: VideoId): PlaybackPosition?
}
