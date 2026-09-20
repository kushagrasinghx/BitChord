package com.music.bitchord.alarm

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Versioned JSON boundary for the alarm preference. */
object AlarmConfigCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(config: AlarmConfig): String = json.encodeToString(config)

    /** Corrupt, unsupported, or invalid data fails closed to a disabled alarm. */
    fun decode(value: String?): AlarmConfig {
        if (value.isNullOrBlank()) return AlarmConfig()
        return runCatching { json.decodeFromString<AlarmConfig>(value) }
            .getOrNull()
            ?.takeIf(AlarmConfig::isStructurallyValid)
            ?: AlarmConfig()
    }
}
