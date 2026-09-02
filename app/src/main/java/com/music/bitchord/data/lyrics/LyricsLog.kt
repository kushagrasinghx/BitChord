package com.music.bitchord.data.lyrics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Live logger for lyrics fetching operations.
 *
 * Records connection attempts to various lyric APIs (LRCLIB, Musixmatch, BetterLyrics, etc.)
 * and the step-by-step scraping progress of the Genius fallback.
 * Displayed in the lyrics menu/panel when enabled in Settings.
 */
object LyricsLog {

    enum class Level {
        INFO,
        SUCCESS,
        WARN,
        ERROR
    }

    data class Entry(
        val timestamp: Long = System.currentTimeMillis(),
        val tag: String,
        val message: String,
        val level: Level = Level.INFO,
    ) {
        val formattedTime: String by lazy {
            timeFormat.format(Date(timestamp))
        }
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
    private const val MAX_ENTRIES = 120

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
    }

    @Synchronized
    fun log(tag: String, message: String, level: Level = Level.INFO) {
        val entry = Entry(tag = tag, message = message, level = level)
        val current = _entries.value
        _entries.value = if (current.size >= MAX_ENTRIES) {
            current.drop(current.size - MAX_ENTRIES + 1) + entry
        } else {
            current + entry
        }
    }

    fun i(tag: String, message: String) = log(tag, message, Level.INFO)
    fun s(tag: String, message: String) = log(tag, message, Level.SUCCESS)
    fun w(tag: String, message: String) = log(tag, message, Level.WARN)
    fun e(tag: String, message: String) = log(tag, message, Level.ERROR)
}
