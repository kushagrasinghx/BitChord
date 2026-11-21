package com.music.bitchord.data.playlist

import com.music.bitchord.data.model.Song

data class CsvPlaylist(
    val title: String? = null,
    val songs: List<Song> = emptyList(),
)

object CsvPlaylistParser {

    private val VIDEO_ID_REGEX = Regex("""^[a-zA-Z0-9_-]{11}$""")
    private val YT_URL_REGEX = Regex("""(?:v=|/v/|youtu\.be/|/embed/|/shorts/)([a-zA-Z0-9_-]{11})""")

    /**
     * Parses standard CSV text into a [CsvPlaylist].
     * Supports RFC 4180 quotes, commas, multiline entries, and flexible column mappings.
     */
    fun parse(csvContent: String, defaultTitle: String? = null): CsvPlaylist {
        val rows = parseCsvRows(csvContent)
        if (rows.isEmpty()) return CsvPlaylist(title = defaultTitle, songs = emptyList())

        val headerRow = rows.firstOrNull() ?: return CsvPlaylist(title = defaultTitle)

        var titleIdx = -1
        var artistIdx = -1
        var albumIdx = -1
        var videoIdIdx = -1

        for ((idx, col) in headerRow.withIndex()) {
            val normalized = col.trim().lowercase()
            when {
                normalized in listOf("title", "track", "song", "name", "track name", "song title") -> titleIdx = idx
                normalized in listOf("artist", "artists", "artist name", "artist name(s)", "channel") -> artistIdx = idx
                normalized in listOf("album", "album name", "release") -> albumIdx = idx
                normalized in listOf("youtube video id", "youtube id", "video id", "videoid", "video_id", "youtube link", "url", "id", "video") -> videoIdIdx = idx
            }
        }

        val hasHeader = titleIdx != -1 || artistIdx != -1 || videoIdIdx != -1
        val dataRows = if (hasHeader) rows.drop(1) else rows

        // If no explicit header was recognized, use column positions based on width
        if (!hasHeader && rows.isNotEmpty()) {
            val sample = rows.first()
            when (sample.size) {
                1 -> {
                    videoIdIdx = 0
                }
                2 -> {
                    titleIdx = 0
                    videoIdIdx = 1
                }
                3 -> {
                    titleIdx = 0
                    artistIdx = 1
                    videoIdIdx = 2
                }
                else -> {
                    titleIdx = 0
                    artistIdx = 1
                    albumIdx = 2
                    videoIdIdx = 3
                }
            }
        }

        val songs = mutableListOf<Song>()
        for ((index, row) in dataRows.withIndex()) {
            val title = (if (titleIdx in row.indices) row[titleIdx] else "").trim()
            val artist = (if (artistIdx in row.indices) row[artistIdx] else "").trim()
            val album = (if (albumIdx in row.indices) row[albumIdx] else "").trim()
            val rawVideoId = (if (videoIdIdx in row.indices) row[videoIdIdx] else "").trim()

            val videoId = extractVideoId(rawVideoId)
            val effectiveVideoId = when {
                videoId.isNotEmpty() -> videoId
                title.isNotEmpty() -> "csv_${index + 1}_${title.hashCode().toUInt().toString(16)}"
                else -> continue
            }

            val effectiveTitle = title.ifBlank { "Track ${index + 1}" }
            val effectiveArtist = artist.ifBlank { "Unknown Artist" }
            val thumb = if (videoId.isNotEmpty()) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else null

            songs.add(
                Song(
                    videoId = effectiveVideoId,
                    title = effectiveTitle,
                    artist = effectiveArtist,
                    thumbnailUrl = thumb,
                    albumName = album.ifBlank { null },
                ),
            )
        }

        return CsvPlaylist(
            title = defaultTitle?.trim()?.ifBlank { null },
            songs = songs,
        )
    }

    fun extractVideoId(raw: String): String {
        val trimmed = raw.trim()
        if (VIDEO_ID_REGEX.matches(trimmed)) return trimmed
        val match = YT_URL_REGEX.find(trimmed)
        if (match != null) return match.groupValues[1]
        return ""
    }

    /**
     * RFC 4180 compliant CSV line tokenizer handling multiline fields and escaped quotes.
     */
    internal fun parseCsvRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false
        var i = 0
        val len = text.length

        while (i < len) {
            val c = text[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < len && text[i + 1] == '"') {
                        currentField.append('"')
                        i++ // Skip escaped quote
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    currentRow.add(currentField.toString())
                    currentField.setLength(0)
                }
                (c == '\r' || c == '\n') && !inQuotes -> {
                    if (c == '\r' && i + 1 < len && text[i + 1] == '\n') {
                        i++ // Skip CRLF
                    }
                    currentRow.add(currentField.toString())
                    currentField.setLength(0)
                    if (currentRow.any { it.isNotBlank() }) {
                        rows.add(currentRow.toList())
                    }
                    currentRow.clear()
                }
                else -> {
                    currentField.append(c)
                }
            }
            i++
        }

        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString())
            if (currentRow.any { it.isNotBlank() }) {
                rows.add(currentRow.toList())
            }
        }

        return rows
    }
}
