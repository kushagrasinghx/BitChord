package com.music.bitchord.data.sources

/**
 * The shape of a source-backed track's id — `src:<configId>::<trackId>` — as the
 * queue carries it. Parsed here, where the stream stats can read it too; the
 * phone's SourceRegistry mints them.
 */
object SourceTrackKeys {
    const val PREFIX = "src:"
    const val SEPARATOR = "::"

    /** The `(configId, trackId)` inside a key, or null if this is an ordinary YouTube id. */
    fun parse(key: String): Pair<String, String>? {
        if (!key.startsWith(PREFIX)) return null
        val body = key.removePrefix(PREFIX)
        val cut = body.indexOf(SEPARATOR)
        if (cut <= 0) return null
        return body.substring(0, cut) to body.substring(cut + SEPARATOR.length)
    }
}
