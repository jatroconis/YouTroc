package com.youtroc.core.domain.detail

import com.youtroc.core.domain.video.VideoId

/**
 * Port: resolves the full detail of a single video — metadata plus a
 * read-only related-videos shelf — for anonymous playback (RF-VID-01..05).
 *
 * The domain owns this contract; adapters in :data:extraction implement it.
 * It returns a typed [DetailResult] and must never throw — turning transport
 * and parsing failures into domain outcomes is the adapter's responsibility.
 */
interface VideoDetail {
    suspend fun detail(videoId: VideoId): DetailResult
}
