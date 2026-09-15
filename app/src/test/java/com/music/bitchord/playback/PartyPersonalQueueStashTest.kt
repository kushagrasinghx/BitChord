package com.music.bitchord.playback

import android.content.SharedPreferences
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PartyPersonalQueueStashTest {

    private lateinit var fakePrefs: FakeSharedPreferences

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        PartyPersonalQueueStash.initForTesting(fakePrefs)
    }

    @Test
    fun testStashAndLoad() {
        assertFalse(PartyPersonalQueueStash.hasStash())
        assertNull(PartyPersonalQueueStash.load())

        val songs = listOf(
            Song(videoId = "vid1", title = "Song 1", artist = "Artist 1", thumbnailUrl = "url1"),
            Song(videoId = "vid2", title = "Song 2", artist = "Artist 2", thumbnailUrl = "url2"),
            Song(videoId = "vid3", title = "Song 3", artist = "Artist 3", thumbnailUrl = "url3"),
        )

        PartyPersonalQueueStash.stash(
            songs = songs,
            index = 1,
            positionMs = 45000L,
            wasPlaying = true,
        )

        assertTrue(PartyPersonalQueueStash.hasStash())
        val snapshot = PartyPersonalQueueStash.load()
        assertNotNull(snapshot)
        assertEquals(3, snapshot!!.songs.size)
        assertEquals("vid1", snapshot.songs[0].videoId)
        assertEquals("vid2", snapshot.songs[1].videoId)
        assertEquals("vid3", snapshot.songs[2].videoId)
        assertEquals(1, snapshot.index)
        assertEquals(45000L, snapshot.positionMs)
        assertTrue(snapshot.wasPlaying)
    }

    @Test
    fun testClearRemovesStash() {
        val songs = listOf(
            Song(videoId = "vid1", title = "Song 1", artist = "Artist 1", thumbnailUrl = null),
        )
        PartyPersonalQueueStash.stash(songs, index = 0, positionMs = 1200L, wasPlaying = false)
        assertTrue(PartyPersonalQueueStash.hasStash())

        PartyPersonalQueueStash.clear()
        assertFalse(PartyPersonalQueueStash.hasStash())
        assertNull(PartyPersonalQueueStash.load())
    }

    @Test
    fun testEmptySongsDoesNotStash() {
        PartyPersonalQueueStash.stash(emptyList(), index = 0, positionMs = 0L, wasPlaying = false)
        assertFalse(PartyPersonalQueueStash.hasStash())
        assertNull(PartyPersonalQueueStash.load())
    }

    @Test
    fun testIndexClamping() {
        val songs = listOf(
            Song(videoId = "vid1", title = "Song 1", artist = "Artist 1", thumbnailUrl = null),
            Song(videoId = "vid2", title = "Song 2", artist = "Artist 2", thumbnailUrl = null),
        )
        PartyPersonalQueueStash.stash(songs, index = 99, positionMs = 0L, wasPlaying = false)
        val snapshot = PartyPersonalQueueStash.load()
        assertNotNull(snapshot)
        assertEquals(1, snapshot!!.index) // Clamped to last index
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = HashMap(data)
        override fun getString(key: String?, defValue: String?): String? =
            data[key] as? String ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? =
            data[key] as? Set<String> ?: defValues

        override fun getInt(key: String?, defValue: Int): Int =
            (data[key] as? Number)?.toInt() ?: defValue

        override fun getLong(key: String?, defValue: Long): Long =
            (data[key] as? Number)?.toLong() ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            (data[key] as? Number)?.toFloat() ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            data[key] as? Boolean ?: defValue

        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor(this)

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) {}

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) {}

        private class FakeEditor(private val prefs: FakeSharedPreferences) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removed = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putStringSet(key: String?, values: Set<String>?): SharedPreferences.Editor {
                if (key != null) pending[key] = values
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) removed.add(key)
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearAll = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearAll) {
                    prefs.data.clear()
                }
                for (key in removed) {
                    prefs.data.remove(key)
                }
                prefs.data.putAll(pending)
            }
        }
    }
}
