package com.music.bitchord.alarm

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** SharedPreferences-backed owner of the single local alarm record. */
object AlarmStore {
    private lateinit var preferences: SharedPreferences
    private val _config = MutableStateFlow(AlarmConfig())

    val config: StateFlow<AlarmConfig> = _config.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (this::preferences.isInitialized) return
        preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        _config.value = AlarmConfigCodec.decode(preferences.getString(KEY_CONFIG, null))
    }

    @Synchronized
    internal fun current(context: Context): AlarmConfig {
        init(context)
        return _config.value
    }

    @Synchronized
    internal fun save(context: Context, config: AlarmConfig) {
        init(context)
        val safe = config.takeIf(AlarmConfig::isStructurallyValid) ?: AlarmConfig()
        _config.value = safe
        preferences.edit().putString(KEY_CONFIG, AlarmConfigCodec.encode(safe)).apply()
    }

    private const val PREFERENCES = "bitchord_alarm"
    private const val KEY_CONFIG = "alarm_config"
}
