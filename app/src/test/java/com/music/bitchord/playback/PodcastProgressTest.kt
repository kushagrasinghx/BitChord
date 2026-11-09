package com.music.bitchord.playback

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class PodcastProgressTest {

    private lateinit var prefs: FakeSharedPreferences

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        PodcastProgress.initForTesting(prefs)
    }

    @Test
    fun bookmarkSurvivesAPlaybackServiceRecreationAndIsScopedPerEpisode() {
        PodcastProgress.save("episode-a", 245_000L, 3_600_000L, commit = true)
        PodcastProgress.save("episode-b", 91_000L, 1_800_000L)

        // Reinitialising against the same device preferences models a new service.
        PodcastProgress.initForTesting(prefs)

        assertEquals(245_000L, PodcastProgress.positionMs("episode-a"))
        assertEquals(91_000L, PodcastProgress.positionMs("episode-b"))
        assertEquals(0L, PodcastProgress.positionMs("episode-c"))
    }

    @Test
    fun restoredPositionUsesKnownDurationAndCompletedEntriesAreRemoved() {
        PodcastProgress.save("episode", 250_000L, 0L)
        assertEquals(250_000L, PodcastProgress.positionMs("episode", 300_000L))
        PodcastProgress.save("episode", 500_000L, 0L)
        assertEquals(0L, PodcastProgress.positionMs("episode", 400_000L))
        assertFalse(prefs.contains("episode:episode"))
    }

    @Test
    fun zeroDoesNotEraseAResumePointUnlessItIsExplicitlyCleared() {
        PodcastProgress.save("episode", 75_000L, 600_000L)
        PodcastProgress.save("episode", 0L, 600_000L)
        assertEquals(75_000L, PodcastProgress.positionMs("episode"))

        PodcastProgress.clear("episode", commit = true)
        assertEquals(0L, PodcastProgress.positionMs("episode"))
    }

    @Test
    fun unknownDurationDoesNotForgetTheLastKnownDuration() {
        PodcastProgress.save("episode", 100_000L, 500_000L)
        PodcastProgress.save("episode", 200_000L, 0L)

        // A temporary unknown duration must not make a later end-position
        // look like a valid resume point.
        PodcastProgress.save("episode", 500_000L, 0L)
        assertEquals(0L, PodcastProgress.positionMs("episode"))
    }

    @Test
    fun completedPlaybackDoesNotCreateAStaleBookmark() {
        PodcastProgress.save("episode", 600_000L, 600_000L)
        assertEquals(0L, PodcastProgress.positionMs("episode"))
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = HashMap(data)
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = data[key] as? Set<String> ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Number)?.toInt() ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Number)?.toLong() ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Number)?.toFloat() ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(this)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class Editor(private val prefs: FakeSharedPreferences) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removed = mutableSetOf<String>()
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
                prefs.data.clear()
                pending.clear()
                removed.clear()
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                removed.forEach(prefs.data::remove)
                prefs.data.putAll(pending)
            }
        }
    }
}
