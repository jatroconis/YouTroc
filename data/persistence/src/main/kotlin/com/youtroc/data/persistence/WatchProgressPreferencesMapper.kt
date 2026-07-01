package com.youtroc.data.persistence

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.youtroc.core.domain.playback.PlaybackPosition
import com.youtroc.core.domain.video.VideoId

/**
 * Pure translation between the domain's [com.youtroc.core.domain.playback.WatchProgressStore]
 * vocabulary (videoId / [PlaybackPosition] / durationMs) and DataStore Preferences
 * keys and stored longs.
 *
 * This object touches no disk and no [android.content.Context]: [Preferences] and
 * [MutablePreferences] are plain in-memory snapshots, so key derivation and the
 * position/duration encode-decode round trip are fully unit-testable. The actual
 * disk-backed `DataStore<Preferences>` I/O lives in [DataStoreWatchProgressStore]
 * (integration-only).
 */
internal object WatchProgressPreferencesMapper {

    private const val POSITION_KEY_PREFIX = "watch_progress_position_"
    private const val DURATION_KEY_PREFIX = "watch_progress_duration_"

    fun positionKey(videoId: VideoId): Preferences.Key<Long> =
        longPreferencesKey(POSITION_KEY_PREFIX + videoId.value)

    fun durationKey(videoId: VideoId): Preferences.Key<Long> =
        longPreferencesKey(DURATION_KEY_PREFIX + videoId.value)

    /** Writes [position] and [durationMs] for [videoId] into a mutable preferences snapshot. */
    fun write(prefs: MutablePreferences, videoId: VideoId, position: PlaybackPosition, durationMs: Long) {
        prefs[positionKey(videoId)] = position.positionMs
        prefs[durationKey(videoId)] = durationMs
    }

    /** Reads back the saved position for [videoId], or null if it was never saved. */
    fun read(prefs: Preferences, videoId: VideoId): PlaybackPosition? =
        prefs[positionKey(videoId)]?.let(::PlaybackPosition)
}
