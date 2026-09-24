package com.music.bitchord.alarm

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AlarmStore {
    private lateinit var preferences: SharedPreferences
    private val state = MutableStateFlow(AlarmCollection())
    val alarms = state.asStateFlow()
    @Synchronized
    fun init(context: Context) {
        if (!this::preferences.isInitialized) {
            preferences = context.applicationContext.getSharedPreferences(
                PREFERENCES,
                Context.MODE_PRIVATE,
            )
            state.value = AlarmConfigCodec.decode(preferences.getString(KEY_CONFIG, null))
        }
    }

    @Synchronized
    internal fun current(context: Context): AlarmCollection {
        init(context)
        return state.value
    }

    @Synchronized
    internal fun save(context: Context, value: AlarmCollection) {
        init(context)
        val safe = value.takeIf(AlarmCollection::isValid) ?: AlarmCollection()
        state.value = safe
        preferences.edit().putString(KEY_CONFIG, AlarmConfigCodec.encode(safe)).apply()
    }

    private const val PREFERENCES = "bitchord_alarm"
    private const val KEY_CONFIG = "alarm_collection"
}
