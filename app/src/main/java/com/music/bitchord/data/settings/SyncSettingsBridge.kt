package com.music.bitchord.data.settings

import android.content.SharedPreferences
import com.music.bitchord.sync.SyncPreferencePolicy
import com.music.bitchord.sync.SyncSettingsPort
import com.music.bitchord.sync.SyncSnapshot
import com.music.bitchord.sync.SyncValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The narrow adapter between SharedPreferences and the shared sync policy. */
internal class SyncSettingsBridge(private val prefs: SharedPreferences) {
    fun export(): Map<String, SyncValue> = SyncPreferencePolicy.export(prefs.all)

    /**
     * Applies only values whose SharedPreferences value is still the one the
     * coordinator observed before its write. AppSettingsSyncPort invokes this
     * synchronously on Main, so normal UI changes cannot interleave the check
     * and commit.
     */
    fun apply(snapshot: SyncSnapshot, expectedCurrent: Map<String, SyncValue>): Boolean {
        val stored = prefs.all
        val current = SyncPreferencePolicy.export(stored)
        val unchangedFields = snapshot.fields.filter { (key, _) -> current[key] == expectedCurrent[key] }
        val delta = SyncPreferencePolicy.diff(stored, unchangedFields)
        if (delta.upserts.isEmpty() && delta.removals.isEmpty()) return false

        val editor = prefs.edit()
        delta.removals.forEach { editor.remove(it) }
        delta.upserts.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        check(editor.commit()) { "Couldn't persist synced preferences" }
        return true
    }
}

/** The production sync port delegates to the initialized app settings singleton. */
internal object AppSettingsSyncPort : SyncSettingsPort {
    override suspend fun export(): Map<String, SyncValue> = withContext(Dispatchers.Main.immediate) {
        AppSettings.exportSyncablePrefs()
    }

    override suspend fun apply(snapshot: SyncSnapshot, expectedCurrent: Map<String, SyncValue>) =
        withContext(Dispatchers.Main.immediate) {
            AppSettings.applySyncSnapshot(snapshot, expectedCurrent)
        }
}
