package com.youtroc.data.persistence

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.youtroc.core.domain.playback.PlaybackPosition
import com.youtroc.core.domain.playback.WatchHistoryEntry
import com.youtroc.core.domain.video.VideoId

/**
 * Pure translation between the domain's [com.youtroc.core.domain.playback.WatchProgressStore]
 * vocabulary (videoId / [PlaybackPosition] / durationMs / title / channel) and
 * DataStore Preferences keys and stored values.
 *
 * This object touches no disk and no [android.content.Context]: [Preferences] and
 * [MutablePreferences] are plain in-memory snapshots, so key derivation and the
 * position/duration/title/channel/watchedAt encode-decode round trip are fully
 * unit-testable. The actual disk-backed `DataStore<Preferences>` I/O lives in
 * [DataStoreWatchProgressStore] (integration-only).
 *
 * [write]'s `title`/`channel` are nullable so the pre-existing 4-arg call shape
 * (position+duration only) keeps writing a LEGACY row -- no [watchedAtKey]
 * stamped. [watchedAtKey]'s presence IS the marker [readAll] gates on to skip
 * legacy rows (REQ-HF10): a full write always stamps title+channel+watchedAt
 * together, so a row missing any of them is never surfaced by [readAll].
 */
internal object WatchProgressPreferencesMapper {

    private const val POSITION_KEY_PREFIX = "watch_progress_position_"
    private const val DURATION_KEY_PREFIX = "watch_progress_duration_"
    private const val TITLE_KEY_PREFIX = "watch_progress_title_"
    private const val CHANNEL_KEY_PREFIX = "watch_progress_channel_"
    private const val WATCHED_AT_KEY_PREFIX = "watch_progress_watched_at_"

    fun positionKey(videoId: VideoId): Preferences.Key<Long> =
        longPreferencesKey(POSITION_KEY_PREFIX + videoId.value)

    fun durationKey(videoId: VideoId): Preferences.Key<Long> =
        longPreferencesKey(DURATION_KEY_PREFIX + videoId.value)

    fun titleKey(videoId: VideoId): Preferences.Key<String> =
        stringPreferencesKey(TITLE_KEY_PREFIX + videoId.value)

    fun channelKey(videoId: VideoId): Preferences.Key<String> =
        stringPreferencesKey(CHANNEL_KEY_PREFIX + videoId.value)

    fun watchedAtKey(videoId: VideoId): Preferences.Key<Long> =
        longPreferencesKey(WATCHED_AT_KEY_PREFIX + videoId.value)

    /**
     * Writes [position] and [durationMs] for [videoId] into a mutable preferences
     * snapshot. When [title] and [channel] are BOTH supplied, ALSO stamps them
     * plus [now]'s watch-history marker (REQ-HF7) -- omitting either keeps this
     * a legacy, [readAll]-invisible row (R3/REQ-HF10).
     */
    fun write(
        prefs: MutablePreferences,
        videoId: VideoId,
        position: PlaybackPosition,
        durationMs: Long,
        title: String? = null,
        channel: String? = null,
        now: () -> Long = System::currentTimeMillis,
    ) {
        prefs[positionKey(videoId)] = position.positionMs
        prefs[durationKey(videoId)] = durationMs
        if (title != null && channel != null) {
            prefs[titleKey(videoId)] = title
            prefs[channelKey(videoId)] = channel
            prefs[watchedAtKey(videoId)] = now()
        }
    }

    /** Reads back the saved position for [videoId], or null if it was never saved. */
    fun read(prefs: Preferences, videoId: VideoId): PlaybackPosition? =
        prefs[positionKey(videoId)]?.let(::PlaybackPosition)

    /**
     * All watch-history entries stamped by a full [write] (marker-gated on
     * [watchedAtKey], R3), most-recently-watched first (REQ-HF8), excluding
     * any entry whose title/channel round-tripped blank (F1 defensive net).
     */
    fun readAll(prefs: Preferences): List<WatchHistoryEntry> =
        prefs.asMap().keys
            .filter { it.name.startsWith(WATCHED_AT_KEY_PREFIX) }
            .map { VideoId(it.name.removePrefix(WATCHED_AT_KEY_PREFIX)) }
            .mapNotNull { videoId -> readEntry(prefs, videoId) }
            .filter { it.title.isNotBlank() && it.channel.isNotBlank() }
            .sortedByDescending { it.watchedAt }

    private fun readEntry(prefs: Preferences, videoId: VideoId): WatchHistoryEntry? {
        val watchedAt = prefs[watchedAtKey(videoId)] ?: return null
        val position = prefs[positionKey(videoId)] ?: return null
        val durationMs = prefs[durationKey(videoId)] ?: return null
        val title = prefs[titleKey(videoId)] ?: return null
        val channel = prefs[channelKey(videoId)] ?: return null
        return WatchHistoryEntry(
            videoId = videoId,
            title = title,
            channel = channel,
            watchedAt = watchedAt,
            position = PlaybackPosition(position),
            durationMs = durationMs,
        )
    }
}
