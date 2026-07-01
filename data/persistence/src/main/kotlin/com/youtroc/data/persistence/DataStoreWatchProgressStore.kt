package com.youtroc.data.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.youtroc.core.domain.playback.PlaybackPosition
import com.youtroc.core.domain.playback.WatchProgressStore
import com.youtroc.core.domain.video.VideoId
import kotlinx.coroutines.flow.first

private const val WATCH_PROGRESS_DATASTORE_NAME = "watch_progress"

private val Context.watchProgressDataStore: DataStore<Preferences> by preferencesDataStore(
    name = WATCH_PROGRESS_DATASTORE_NAME,
)

/**
 * DataStore Preferences-backed [WatchProgressStore] adapter (REQ-12): local-only
 * by construction — every read/write here is a file on the device's own storage,
 * no network client exists in this module.
 *
 * Integration-only: exercises real DataStore file I/O through an Android
 * [Context], so it is not unit-tested here. Key derivation and the
 * position/duration encode-decode round trip — the part that carries real
 * logic — is factored out into [WatchProgressPreferencesMapper], which IS
 * unit-tested (RED->GREEN) against in-memory Preferences snapshots.
 */
class DataStoreWatchProgressStore(context: Context) : WatchProgressStore {

    private val dataStore: DataStore<Preferences> = context.watchProgressDataStore

    override suspend fun save(videoId: VideoId, position: PlaybackPosition, durationMs: Long) {
        dataStore.edit { prefs ->
            WatchProgressPreferencesMapper.write(prefs, videoId, position, durationMs)
        }
    }

    override suspend fun load(videoId: VideoId): PlaybackPosition? =
        WatchProgressPreferencesMapper.read(dataStore.data.first(), videoId)
}
