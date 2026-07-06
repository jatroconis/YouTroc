package com.youtroc.data.persistence

import androidx.datastore.preferences.core.emptyPreferences
import com.youtroc.core.domain.playback.PlaybackPosition
import com.youtroc.core.domain.video.VideoId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `a full write round trips title, channel, and the injected clock's watchedAt via readAll`() {
        val prefs = emptyPreferences().toMutablePreferences()

        WatchProgressPreferencesMapper.write(
            prefs,
            videoId,
            PlaybackPosition(42_000L),
            durationMs = 120_000L,
            title = "Never Gonna Give You Up",
            channel = "Rick Astley",
            now = { 555_000L },
        )

        val entry = WatchProgressPreferencesMapper.readAll(prefs).single { it.videoId == videoId }
        assertEquals("Never Gonna Give You Up", entry.title)
        assertEquals("Rick Astley", entry.channel)
        assertEquals(555_000L, entry.watchedAt)
        assertEquals(PlaybackPosition(42_000L), entry.position)
        assertEquals(120_000L, entry.durationMs)
    }

    @Test
    fun `a legacy position-only row (R3) is silently skipped by readAll, alongside a full row`() {
        val prefs = emptyPreferences().toMutablePreferences()
        // Legacy shape: the pre-existing 4-arg call, no title/channel/marker.
        WatchProgressPreferencesMapper.write(prefs, videoId, PlaybackPosition(10_000L), durationMs = 50_000L)
        WatchProgressPreferencesMapper.write(
            prefs,
            otherVideoId,
            PlaybackPosition(20_000L),
            durationMs = 100_000L,
            title = "Full Row",
            channel = "A Channel",
            now = { 1_000L },
        )

        val ids = WatchProgressPreferencesMapper.readAll(prefs).map { it.videoId }

        assertEquals(listOf(otherVideoId), ids)
    }

    @Test
    fun `readAll orders entries most-recently-watched first -- REQ-HF8`() {
        val prefs = emptyPreferences().toMutablePreferences()
        val a = VideoId("aaaaaaaaaaa")
        val b = VideoId("bbbbbbbbbbb")
        val c = VideoId("ccccccccccc")
        // Written in A, B, C order with C the most recent -- storage order must not leak through.
        WatchProgressPreferencesMapper.write(prefs, a, PlaybackPosition(10_000L), durationMs = 100_000L, title = "A", channel = "Ch", now = { 1_000L })
        WatchProgressPreferencesMapper.write(prefs, b, PlaybackPosition(20_000L), durationMs = 100_000L, title = "B", channel = "Ch", now = { 2_000L })
        WatchProgressPreferencesMapper.write(prefs, c, PlaybackPosition(30_000L), durationMs = 100_000L, title = "C", channel = "Ch", now = { 3_000L })

        assertEquals(listOf(c, b, a), WatchProgressPreferencesMapper.readAll(prefs).map { it.videoId })
    }

    @Test
    fun `a full-shaped row with a blank title or channel is excluded by readAll -- F1 defensive net`() {
        val prefs = emptyPreferences().toMutablePreferences()
        WatchProgressPreferencesMapper.write(
            prefs,
            videoId,
            PlaybackPosition(10_000L),
            durationMs = 50_000L,
            title = "",
            channel = "",
            now = { 1_000L },
        )

        assertTrue(WatchProgressPreferencesMapper.readAll(prefs).isEmpty())
    }
}
