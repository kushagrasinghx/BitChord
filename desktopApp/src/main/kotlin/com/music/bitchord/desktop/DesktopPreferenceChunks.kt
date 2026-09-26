package com.music.bitchord.desktop

import java.util.prefs.Preferences

/** Reads and writes one logical value as numbered chunks. */
internal object DesktopPreferenceChunks {

    /**
     * Half the platform limit, so a chunk stays clear of it however the value is encoded on the way
     * to disk.
     */
    private const val CHUNK_SIZE = Preferences.MAX_VALUE_LENGTH / 2

    /** The stored value, or null when nothing is stored under [key]. */
    fun read(preferences: Preferences, key: String): String? {
        val first = preferences.get("$key.0", null)
            // A value written before this was chunked is still readable.
            ?: return preferences.get(key, null)
        return buildString {
            append(first)
            var index = 1
            while (true) {
                val chunk = preferences.get("$key.$index", null) ?: break
                append(chunk)
                index++
            }
        }
    }

    /** Replaces what is stored under [key]. */
    fun write(preferences: Preferences, key: String, value: String) {
        val chunks = value.xmlSafe().chunked(CHUNK_SIZE).ifEmpty { listOf("") }
        chunks.forEachIndexed { index, chunk -> preferences.put("$key.$index", chunk) }
        // Now that the value is readable in its new form, retire the old one: the unchunked key,
        // and any chunks left over from a longer value.
        preferences.remove(key)
        var index = chunks.size
        while (preferences.get("$key.$index", null) != null) {
            preferences.remove("$key.$index")
            index++
        }
        runCatching { preferences.flush() }
    }

    /** Drops the characters XML cannot carry, because the backing store is XML. */
    private fun String.xmlSafe(): String {
        // XML 1.0 allows tab, newline and carriage return out of the C0 block, and nothing else
        // below 0x20.
        if (none { it < ' ' && it != '\t' && it != '\n' && it != '\r' }) return this
        return filterNot { it < ' ' && it != '\t' && it != '\n' && it != '\r' }
    }
}
