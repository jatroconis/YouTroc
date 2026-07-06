package com.youtroc.core.domain.playback

import com.youtroc.core.domain.video.VideoId

/**
 * Port: persists and retrieves how far a user got into a video, so playback can
 * offer to resume. The domain owns this contract; `:data:persistence` implements
 * it. Local-only by construction — no method here can carry progress data off
 * the device.
 *
 * [save]'s [title]/[channel] (REQ-HF7) back "Seguir viendo" history --
 * defaulted so the pre-existing progress-only call shape keeps compiling; a
 * real caller (`PlaybackViewModel`) always supplies both. [readAll]
 * enumerates every full (title/channel-bearing) entry, most-recently-watched
 * first (REQ-HF8) -- an adapter MUST NOT surface a legacy position-only
 * record through it (REQ-HF10).
 */
interface WatchProgressStore {
    suspend fun save(
        videoId: VideoId,
        position: PlaybackPosition,
        durationMs: Long,
        title: String = "",
        channel: String = "",
    )

    suspend fun load(videoId: VideoId): PlaybackPosition?

    /** All watch-history entries, most-recently-watched first (REQ-HF8). */
    suspend fun readAll(): List<WatchHistoryEntry>
}
