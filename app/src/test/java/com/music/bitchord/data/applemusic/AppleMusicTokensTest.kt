package com.music.bitchord.data.applemusic

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleMusicTokensTest {
    @Test
    fun `encoded playback storefront is restored and removed on account replacement or sign out`() {
        val prefs = MemoryPreferences()
        val store = AppleMusicUserTokenStore()
        store.init { prefs }
        store.saveUserToken("user-secret", "us", "143441-1,29")
        val restored = AppleMusicUserTokenStore()
        restored.init { prefs }
        assertEquals("us", restored.storefront())
        assertEquals("143441-1,29", restored.playbackStorefront())
        restored.saveUserToken("other-user", "gb")
        assertNull(restored.playbackStorefront())
        assertFalse(prefs.contains("playback_storefront"))
        restored.saveUserToken("other-user", "gb", "143444-1,29")
        restored.clear()
        assertNull(restored.playbackStorefront())
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun `local initialization restores user credentials and connected state only once`() {
        val prefs = MemoryPreferences()
        prefs.edit().putString("user_token", "user-secret").putString("storefront", "us").apply()
        val store = AppleMusicUserTokenStore()
        assertFalse(store.connected.value)
        assertNull(store.userToken())
        assertNull(store.storefront())
        var opens = 0
        store.init { opens++; prefs }
        store.init { error("Repeated init must not reopen storage") }
        assertEquals(1, opens)
        assertTrue(store.connected.value)
        assertEquals("user-secret", store.userToken())
        assertEquals("us", store.storefront())
    }

    @Test
    fun `saving persists only the user credential and optional storefront`() {
        val prefs = MemoryPreferences()
        val store = AppleMusicUserTokenStore()
        store.init { prefs }
        store.saveUserToken("user-secret", " gb ")
        assertTrue(store.connected.value)
        assertEquals("user-secret", store.userToken())
        assertEquals("gb", store.storefront())
        assertEquals(mapOf("user_token" to "user-secret", "storefront" to "gb"), prefs.all)

        val restored = AppleMusicUserTokenStore()
        restored.init { prefs }
        assertTrue(restored.connected.value)
        assertEquals("user-secret", restored.userToken())
        assertEquals("gb", restored.storefront())
    }

    @Test
    fun `a missing or blank storefront removes the previous account storefront`() {
        val prefs = MemoryPreferences()
        val store = AppleMusicUserTokenStore()
        store.init { prefs }
        store.saveUserToken("first-user", "us")
        store.saveUserToken("second-user", null)
        assertNull(store.storefront())
        assertFalse(prefs.contains("storefront"))
        store.saveUserToken("second-user", "us")
        store.saveUserToken("third-user", " ")
        assertNull(store.storefront())
        assertFalse(prefs.contains("storefront"))
        assertTrue(store.connected.value)
    }

    @Test
    fun `clear disconnects and permanently removes both stored values`() {
        val prefs = MemoryPreferences()
        val store = AppleMusicUserTokenStore()
        store.init { prefs }
        store.saveUserToken("user-secret", "us")
        store.clear()
        store.clear()
        assertFalse(store.connected.value)
        assertNull(store.userToken())
        assertNull(store.storefront())
        assertTrue(prefs.all.isEmpty())

        val restored = AppleMusicUserTokenStore()
        restored.init { prefs }
        assertFalse(restored.connected.value)
        assertNull(restored.userToken())
        assertNull(restored.storefront())
    }

    @Test
    fun `clear before initialization does not resurrect stored credentials`() {
        val prefs = MemoryPreferences()
        prefs.edit().putString("user_token", "old-user").putString("storefront", "us").apply()
        val store = AppleMusicUserTokenStore()
        store.clear()
        store.init { prefs }
        assertFalse(store.connected.value)
        assertNull(store.userToken())
        assertNull(store.storefront())
        assertTrue(prefs.all.isEmpty())
        store.saveUserToken("new-user", "ca")
        assertTrue(store.connected.value)
        assertEquals("new-user", store.userToken())
    }

    @Test
    fun `blank stored credentials and orphaned storefronts are not connected`() {
        for (token in listOf(null, "", " ")) {
            val prefs = MemoryPreferences()
            prefs.edit().putString("user_token", token).putString("storefront", "us").apply()
            val store = AppleMusicUserTokenStore()
            store.init { prefs }
            assertFalse(store.connected.value)
            assertNull(store.userToken())
            assertNull(store.storefront())
        }
    }

    @Test
    fun `saving before initialization fails explicitly without exposing the credential`() {
        val store = AppleMusicUserTokenStore()
        try {
            store.saveUserToken("user-secret", "us")
            throw AssertionError("Expected uninitialized storage failure")
        } catch (error: IllegalStateException) {
            assertFalse(error.stackTraceToString().contains("user-secret"))
        }
        assertFalse(store.connected.value)
        assertNull(store.userToken())
    }

    @Test
    fun `blank saves do not overwrite a connected account`() {
        val prefs = MemoryPreferences()
        val store = AppleMusicUserTokenStore()
        store.init { prefs }
        store.saveUserToken("user-secret", "us")
        for (token in listOf("", " ")) {
            try {
                store.saveUserToken(token, "gb")
                throw AssertionError("Expected blank credential failure")
            } catch (_: IllegalArgumentException) {
                assertEquals("user-secret", store.userToken())
                assertEquals("us", store.storefront())
                assertTrue(store.connected.value)
            }
        }
        assertEquals(mapOf("user_token" to "user-secret", "storefront" to "us"), prefs.all)
    }

    private class MemoryPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()
        override fun getAll(): Map<String, *> = data.toMap()
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = data[key] as? Set<String> ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false
            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }
            override fun putStringSet(key: String?, values: Set<String>?): SharedPreferences.Editor = apply { if (key != null) pending[key] = values?.toSet() }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?): SharedPreferences.Editor = apply { if (key != null) pending[key] = null }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) data.clear()
                pending.forEach { (key, value) ->
                    if (value == null) data.remove(key) else data[key] = value
                }
            }
        }
    }
}
