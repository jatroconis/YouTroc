package com.youtroc.data.persistence

import androidx.datastore.preferences.core.emptyPreferences
import com.youtroc.core.domain.playback.PlaybackPosition
import com.youtroc.core.domain.video.VideoId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Unit tests for the pure videoId->key derivation and PlaybackPosition<->stored-longs
 * mapping. No real DataStore<Preferences> instance is involved here — [emptyPreferences]
 * and its mutable snapshot are plain in-memory data, so this is fully testable without
 * disk I/O or an Android Context. The disk-backed adapter is [DataStoreWatchProgressStore]
 * (integration-only, no unit test).
 */
class WatchProgressPreferencesMapperTest {

    private val videoId = VideoId("dQw4w9WgXcQ")
    private val otherVideoId = VideoId("otherVideoId")

    @Test
    fun `read returns null when nothing was ever written`() {
        assertNull(WatchProgressPreferencesMapper.read(emptyPreferences(), videoId))
    }

    @Test
    fun `write then read round trips the exact saved position`() {
        val prefs = emptyPreferences().toMutablePreferences()

        WatchProgressPreferencesMapper.write(prefs, videoId, PlaybackPosition(42_000L), durationMs = 120_000L)

        assertEquals(PlaybackPosition(42_000L), WatchProgressPreferencesMapper.read(prefs, videoId))
    }

    @Test
    fun `a saved position for one videoId is invisible when reading another videoId`() {
        val prefs = emptyPreferences().toMutablePreferences()
        WatchProgressPreferencesMapper.write(prefs, videoId, PlaybackPosition(42_000L), durationMs = 120_000L)

        assertNull(WatchProgressPreferencesMapper.read(prefs, otherVideoId))
    }

    @Test
    fun `position and duration keys differ for the same videoId`() {
        assertNotEquals(
            WatchProgressPreferencesMapper.positionKey(videoId).name,
            WatchProgressPreferencesMapper.durationKey(videoId).name,
        )
    }

    @Test
    fun `position keys differ across videoIds`() {
        assertNotEquals(
            WatchProgressPreferencesMapper.positionKey(videoId).name,
            WatchProgressPreferencesMapper.positionKey(otherVideoId).name,
        )
    }
}
