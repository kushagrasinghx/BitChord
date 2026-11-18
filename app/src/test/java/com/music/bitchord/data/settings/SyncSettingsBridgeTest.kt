package com.music.bitchord.data.settings

import android.content.SharedPreferences
import com.music.bitchord.sync.FieldClock
import com.music.bitchord.sync.SyncField
import com.music.bitchord.sync.SyncSnapshot
import com.music.bitchord.sync.SyncValue
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncSettingsBridgeTest {
    @Test
    fun exportsOnlyThePortableSettingsAllowlist() {
        val preferences = MemoryPreferences(
            mapOf(
                "theme_mode" to "DARK",
                "lyrics_offset_ms" to 120,
                "lastfm_session_key" to "private-session",
                "onboarding_complete" to true,
                "signature_api_url" to "http://private-host",
                "future_preference" to "keep-local",
            ),
        )

        assertEquals(
            mapOf(
                "theme_mode" to SyncValue("string", "DARK"),
                "lyrics_offset_ms" to SyncValue("int", "120"),
            ),
            SyncSettingsBridge(preferences.sharedPreferences).export(),
        )
    }

    @Test
    fun applyingSnapshotChangesAllowlistedValuesAndPreservesLocalData() {
        val preferences = MemoryPreferences(
            mapOf(
                "theme_mode" to "DARK",
                "pinned_playlists" to "PL-1",
                "lastfm_session_key" to "private-session",
                "onboarding_complete" to true,
                "signature_api_url" to "http://private-host",
                "future_preference" to "keep-local",
            ),
        )
        val snapshot = SyncSnapshot(
            fields = mapOf(
                "theme_mode" to SyncField(FieldClock(1, "phone-a"), SyncValue("string", "LIGHT")),
                "pinned_playlists" to SyncField(FieldClock(2, "phone-a"), null),
                "lastfm_session_key" to SyncField(FieldClock(3, "phone-b"), SyncValue("string", "do-not-import")),
                "onboarding_complete" to SyncField(FieldClock(4, "phone-b"), SyncValue("bool", "false")),
                "future_preference" to SyncField(FieldClock(5, "phone-b"), SyncValue("string", "do-not-import")),
                "lyrics_offset_ms" to SyncField(FieldClock(6, "phone-b"), SyncValue("string", "wrong-type")),
            ),
        )

        assertTrue(SyncSettingsBridge(preferences.sharedPreferences).apply(snapshot, SyncSettingsBridge(preferences.sharedPreferences).export()))
        assertEquals(
            mapOf(
                "theme_mode" to "LIGHT",
                "lastfm_session_key" to "private-session",
                "onboarding_complete" to true,
                "signature_api_url" to "http://private-host",
                "future_preference" to "keep-local",
            ),
            preferences.values,
        )
    }

    @Test
    fun concurrentLocalEditIsNotOverwrittenAfterCoordinatorSnapshotCheck() {
        val preferences = MemoryPreferences(mapOf("theme_mode" to "DARK"))
        val bridge = SyncSettingsBridge(preferences.sharedPreferences)
        val expectedAtCheck = bridge.export()
        val remote = SyncSnapshot(fields = mapOf(
            "theme_mode" to SyncField(FieldClock(2, "phone-b"), SyncValue("string", "REMOTE")),
        ))

        // A user edit arrives after the coordinator's final export/check but before the bridge write.
        preferences.values["theme_mode"] = "LOCAL-EDIT"

        assertFalse(bridge.apply(remote, expectedAtCheck))
        assertEquals("LOCAL-EDIT", preferences.values["theme_mode"])
        assertEquals(0, preferences.commitCount)
    }

    @Test
    fun identicalSnapshotDoesNotWritePreferencesAgain() {
        val preferences = MemoryPreferences(mapOf("theme_mode" to "DARK"))
        val bridge = SyncSettingsBridge(preferences.sharedPreferences)
        val snapshot = SyncSnapshot(fields = mapOf(
            "theme_mode" to SyncField(FieldClock(1, "phone-a"), SyncValue("string", "DARK")),
        ))

        assertFalse(bridge.apply(snapshot, bridge.export()))
        assertEquals(0, preferences.commitCount)
    }

    private class MemoryPreferences(initial: Map<String, Any?>) {
        val values = initial.toMutableMap()
        var commitCount = 0
            private set

        val sharedPreferences: SharedPreferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getAll" -> values.toMap()
                "edit" -> editor()
                "contains" -> values.containsKey("unused")
                else -> null
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            var clear = false
            val upserts = linkedMapOf<String, Any?>()
            val removals = linkedSetOf<String>()
            lateinit var proxy: SharedPreferences.Editor
            proxy = Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java),
            ) { _, method, args ->
                val key = args?.firstOrNull() as? String
                when (method.name) {
                    "putBoolean", "putInt", "putLong", "putFloat", "putString" -> {
                        upserts[key!!] = args!![1]
                        removals.remove(key)
                        proxy
                    }
                    "putStringSet" -> {
                        upserts[key!!] = (args!![1] as? Set<*>)?.filterIsInstance<String>()?.toSet()
                        removals.remove(key)
                        proxy
                    }
                    "remove" -> {
                        removals += key!!
                        upserts.remove(key)
                        proxy
                    }
                    "clear" -> {
                        clear = true
                        removals.clear()
                        upserts.clear()
                        proxy
                    }
                    "commit" -> {
                        if (clear) values.clear()
                        removals.forEach(values::remove)
                        upserts.forEach { (name, value) -> if (value != null) values[name] = value }
                        commitCount++
                        true
                    }
                    "apply" -> {
                        if (clear) values.clear()
                        removals.forEach(values::remove)
                        upserts.forEach { (name, value) -> if (value != null) values[name] = value }
                        commitCount++
                        null
                    }
                    else -> if (method.returnType == SharedPreferences.Editor::class.java) proxy else null
                }
            } as SharedPreferences.Editor
            return proxy
        }
    }
}
