package com.music.bitchord.alarm

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object AlarmConfigCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(value: AlarmCollection): String = json.encodeToString(value)
    fun decode(value: String?): AlarmCollection = value?.takeIf(String::isNotBlank)
        ?.let { runCatching { json.decodeFromString<AlarmCollection>(it) }.getOrNull() }
        ?.takeIf(AlarmCollection::isValid) ?: AlarmCollection()
}
