package com.music.bitchord.data

import com.music.bitchord.data.model.Song

/**
 * Serializes a playlist's tracks into an RFC 4180 CSV document for backup or diffing
 * against imported playlists.
 */
object PlaylistCsvExporter {

    private const val HEADER = "Title,Artist,Album,Duration,Video ID,URL"

    fun buildCsv(songs: List<Song>): String {
        val sb = StringBuilder(songs.size * 80 + HEADER.length + 2)
        sb.append(HEADER).append('\n')
        for (song in songs) {
            val url = if (song.videoId.isNotBlank() && !song.videoId.startsWith("local:")) {
                "https://music.youtube.com/watch?v=${song.videoId}"
            } else {
                ""
            }
            sb.append(escapeField(song.title)).append(',')
                .append(escapeField(song.artist)).append(',')
                .append(escapeField(song.albumName.orEmpty())).append(',')
                .append(escapeField(song.durationText.orEmpty())).append(',')
                .append(escapeField(song.videoId)).append(',')
                .append(escapeField(url)).append('\n')
        }
        return sb.toString()
    }

    fun sanitizeFileName(title: String): String {
        val cleaned = title.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim('.')
            .ifEmpty { "playlist" }
        return "$cleaned.csv"
    }

    internal fun escapeField(value: String): String {
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuoting) return value
        return buildString(value.length + 4) {
            append('"')
            for (ch in value) {
                if (ch == '"') append("\"\"") else append(ch)
            }
            append('"')
        }
    }
}
