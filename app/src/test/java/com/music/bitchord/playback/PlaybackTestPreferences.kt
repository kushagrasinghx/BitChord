package com.music.bitchord.playback

import android.content.SharedPreferences
import java.util.concurrent.Executor

/** Counts complete preference-file writes, rather than just modified keys. */
internal class PlaybackTestPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()
    var commits = 0
        private set
    var applies = 0
        private set
    var allowCommit = true

    override fun getAll(): Map<String, *> = HashMap(values)
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = values[key] as? Set<String> ?: defValues
    override fun getInt(key: String?, defValue: Int) = (values[key] as? Number)?.toInt() ?: defValue
    override fun getLong(key: String?, defValue: Long) = (values[key] as? Number)?.toLong() ?: defValue
    override fun getFloat(key: String?, defValue: Float) = (values[key] as? Number)?.toFloat() ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun contains(key: String?) = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class Editor : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private var clearAll = false

        private fun put(key: String?, value: Any?): SharedPreferences.Editor = apply { if (key != null) changes[key] = value }
        override fun putString(key: String?, value: String?) = put(key, value)
        override fun putStringSet(key: String?, values: Set<String>?) = put(key, values)
        override fun putInt(key: String?, value: Int) = put(key, value)
        override fun putLong(key: String?, value: Long) = put(key, value)
        override fun putFloat(key: String?, value: Float) = put(key, value)
        override fun putBoolean(key: String?, value: Boolean) = put(key, value)
        override fun remove(key: String?) = put(key, null)
        override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
        override fun commit(): Boolean {
            commits++
            if (!allowCommit) return false
            write()
            return true
        }
        override fun apply() { applies++; write() }
        private fun write() {
            if (clearAll) values.clear()
            changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
        }
    }
}

internal class QueuedPlaybackWriter : Executor {
    private val tasks = ArrayDeque<Runnable>()
    override fun execute(command: Runnable) { tasks.addLast(command) }
    fun runAll() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
}
