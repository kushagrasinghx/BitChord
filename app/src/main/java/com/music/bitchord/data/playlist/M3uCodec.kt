package com.music.bitchord.data.playlist

import com.music.bitchord.data.model.Song
import java.util.Locale

enum class M3uFormat(val extension: String, val mimeType: String) {
    M3U8("m3u8", "application/vnd.apple.mpegurl"),
    M3U("m3u", "audio/x-mpegurl"),
}

enum class M3uEntryKind {
    ABSOLUTE_PATH,
    RELATIVE_PATH,
    FILE_URI,
    CONTENT_URI,
    HTTP_URL,
}

data class M3uEntry(
    val location: String,
    val kind: M3uEntryKind,
    val durationSeconds: Int? = null,
    val title: String? = null,
    val artist: String? = null,
    val rawExtInf: String? = null,
)

data class M3uDocument(
    val hasExtM3uHeader: Boolean,
    val playlistTitle: String?,
    val entries: List<M3uEntry>,
)

data class M3uSerializeResult(
    val content: String,
    val format: M3uFormat,
    val exportedCount: Int,
    val localCount: Int,
    val remoteCount: Int,
    val missingLocalCount: Int,
    val skippedCount: Int,
)

/**
 * Pure-Kotlin M3U / M3U8 parser and serializer.
 *
 * Kept free of Android framework dependencies so all parsing, formatting,
 * escaping, and round-trip behavior can be unit-tested on the JVM.
 */
object M3uCodec {

    private const val HEADER = "#EXTM3U"
    private const val EXTINF = "#EXTINF:"
    private const val PLAYLIST_DIRECTIVE = "#PLAYLIST:"
    private const val UTF8_BOM = '\uFEFF'

    private val YOUTUBE_VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")
    private val WINDOWS_DRIVE_PATH_REGEX = Regex("^[A-Za-z]:[\\\\/].*")
    private val YOUTUBE_WATCH_ID_REGEX = Regex(
        """(?:https?://)?(?:music\.youtube\.com|www\.youtube\.com|m\.youtube\.com|youtube\.com)/watch\?(?:[^#]*&)?v=([A-Za-z0-9_-]{11})(?:[&#]|$)""",
        RegexOption.IGNORE_CASE,
    )
    private val YOUTU_BE_ID_REGEX = Regex(
        """(?:https?://)?youtu\.be/([A-Za-z0-9_-]{11})(?:[?&#]|$)""",
        RegexOption.IGNORE_CASE,
    )

    val SUPPORTED_AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "mp4", "flac", "wav", "ogg", "opus",
        "aac", "webm", "dsf", "dff", "3gp", "wma", "aiff", "alac",
    )

    /**
     * Serializes [songs] into standard M3U or M3U8 text, preserving order.
     *
     * @param isLocalMissing optional predicate returning true when a local song's
     *   backing file is known to be missing; missing entries are still written
     *   using their recorded path/URI without silently corrupting the playlist,
     *   and counted in [M3uSerializeResult.missingLocalCount].
     */
    fun serialize(
        songs: List<Song>,
        format: M3uFormat = M3uFormat.M3U8,
        playlistTitle: String? = null,
        isLocalMissing: (Song) -> Boolean = { false },
    ): M3uSerializeResult {
        val sb = StringBuilder()
        sb.append(HEADER).append('\n')
        val cleanTitle = playlistTitle?.let(::sanitizeSingleLine)?.takeIf { it.isNotBlank() }
        if (cleanTitle != null) {
            sb.append(PLAYLIST_DIRECTIVE).append(cleanTitle).append('\n')
        }

        var localCount = 0
        var remoteCount = 0
        var missingLocalCount = 0
        var skippedCount = 0

        for (song in songs) {
            val location = exportLocationFor(song)
            if (location == null) {
                skippedCount++
                continue
            }
            if (location.isRemote) {
                remoteCount++
            } else {
                localCount++
                if (isLocalMissing(song)) {
                    missingLocalCount++
                }
            }

            val durationSec = parseDurationSeconds(song.durationText) ?: -1
            val display = formatExtInfTitle(song)
            sb.append(EXTINF).append(durationSec).append(',').append(display).append('\n')
            sb.append(location.value).append('\n')
        }

        return M3uSerializeResult(
            content = sb.toString(),
            format = format,
            exportedCount = localCount + remoteCount,
            localCount = localCount,
            remoteCount = remoteCount,
            missingLocalCount = missingLocalCount,
            skippedCount = skippedCount,
        )
    }

    /**
     * Parses an M3U or M3U8 document, handling UTF-8 BOM, CRLF/LF/CR line endings,
     * `#EXTINF` metadata, `#PLAYLIST:` titles, and malformed lines gracefully.
     */
    fun parse(rawContent: String): M3uDocument {
        val content = rawContent.removePrefix(UTF8_BOM.toString())
        val lines = content.split('\n').map { it.trimEnd('\r') }

        var hasHeader = false
        var playlistTitle: String? = null
        var pendingDuration: Int? = null
        var pendingTitle: String? = null
        var pendingArtist: String? = null
        var pendingRawExtInf: String? = null
        val entries = mutableListOf<M3uEntry>()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#")) {
                when {
                    line.equals(HEADER, ignoreCase = true) -> {
                        hasHeader = true
                    }
                    line.startsWith(PLAYLIST_DIRECTIVE, ignoreCase = true) -> {
                        val title = line.substring(PLAYLIST_DIRECTIVE.length).trim()
                        if (title.isNotEmpty()) playlistTitle = title
                    }
                    line.startsWith(EXTINF, ignoreCase = true) -> {
                        val parsed = parseExtInf(line.substring(EXTINF.length))
                        pendingDuration = parsed.durationSeconds
                        pendingTitle = parsed.title
                        pendingArtist = parsed.artist
                        pendingRawExtInf = parsed.rawTitle
                    }
                }
                continue
            }

            entries += M3uEntry(
                location = line,
                kind = classifyLocation(line),
                durationSeconds = pendingDuration,
                title = pendingTitle,
                artist = pendingArtist,
                rawExtInf = pendingRawExtInf,
            )
            pendingDuration = null
            pendingTitle = null
            pendingArtist = null
            pendingRawExtInf = null
        }

        return M3uDocument(
            hasExtM3uHeader = hasHeader,
            playlistTitle = playlistTitle,
            entries = entries,
        )
    }

    fun classifyLocation(location: String): M3uEntryKind {
        val lower = location.lowercase(Locale.ROOT)
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") -> M3uEntryKind.HTTP_URL
            lower.startsWith("file://") -> M3uEntryKind.FILE_URI
            lower.startsWith("content://") -> M3uEntryKind.CONTENT_URI
            location.startsWith("/") || WINDOWS_DRIVE_PATH_REGEX.matches(location) -> M3uEntryKind.ABSOLUTE_PATH
            else -> M3uEntryKind.RELATIVE_PATH
        }
    }

    fun extractYouTubeVideoId(url: String): String? {
        YOUTUBE_WATCH_ID_REGEX.find(url)?.groupValues?.getOrNull(1)?.let { return it }
        YOUTU_BE_ID_REGEX.find(url)?.groupValues?.getOrNull(1)?.let { return it }
        return null
    }

    fun isValidYouTubeVideoId(id: String?): Boolean =
        id != null && YOUTUBE_VIDEO_ID_REGEX.matches(id)

    fun hasSupportedAudioExtension(pathOrName: String): Boolean {
        val clean = pathOrName.substringBefore('?').substringBefore('#')
        val ext = clean.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext.isNotEmpty() && ext in SUPPORTED_AUDIO_EXTENSIONS
    }

    /**
     * Resolves a relative path against [baseDir] using `/` separators and
     * normalizing `.` and `..` segments cleanly.
     */
    fun resolveRelativePath(baseDir: String, relativePath: String): String {
        val baseParts = baseDir.replace('\\', '/').trimEnd('/').split('/').filter { it.isNotEmpty() }.toMutableList()
        val relParts = relativePath.replace('\\', '/').split('/')
        for (part in relParts) {
            when (part) {
                "", "." -> Unit
                ".." -> if (baseParts.isNotEmpty()) baseParts.removeAt(baseParts.lastIndex)
                else -> baseParts.add(part)
            }
        }
        val prefix = if (baseDir.startsWith("/")) "/" else ""
        return prefix + baseParts.joinToString("/")
    }

    fun sanitizeFileName(raw: String, fallback: String = "playlist"): String {
        val cleaned = raw
            .replace(Regex("""[\\/:*?"<>|\x00-\x1F]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('.')
        return cleaned.ifBlank { fallback }.take(120)
    }

    fun formatDurationSeconds(seconds: Int?): String? {
        if (seconds == null || seconds <= 0) return null
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hrs > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.ROOT, "%d:%02d", mins, secs)
        }
    }

    fun parseDurationSeconds(durationText: String?): Int? {
        val text = durationText?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parts = text.split(':')
        return when (parts.size) {
            1 -> parts[0].toIntOrNull()?.takeIf { it >= 0 }
            2 -> {
                val m = parts[0].toIntOrNull() ?: return null
                val s = parts[1].toIntOrNull() ?: return null
                if (m >= 0 && s in 0..59) m * 60 + s else null
            }
            3 -> {
                val h = parts[0].toIntOrNull() ?: return null
                val m = parts[1].toIntOrNull() ?: return null
                val s = parts[2].toIntOrNull() ?: return null
                if (h >= 0 && m in 0..59 && s in 0..59) h * 3600 + m * 60 + s else null
            }
            else -> null
        }
    }

    private data class ExportLocation(val value: String, val isRemote: Boolean)

    private fun exportLocationFor(song: Song): ExportLocation? {
        val path = song.localPath?.trim()?.takeIf { it.isNotEmpty() }
        if (path != null) return ExportLocation(path, isRemote = false)

        val uri = song.localUri?.trim()?.takeIf { it.isNotEmpty() }
        if (uri != null) return ExportLocation(uri, isRemote = false)

        if (isValidYouTubeVideoId(song.videoId)) {
            return ExportLocation("https://music.youtube.com/watch?v=${song.videoId}", isRemote = true)
        }
        return null
    }

    private fun formatExtInfTitle(song: Song): String {
        val title = sanitizeSingleLine(song.title)
        val artist = sanitizeSingleLine(song.artist)
        val hasArtist = artist.isNotEmpty() && !artist.equals("Unknown Artist", ignoreCase = true)
        return when {
            hasArtist && title.isNotEmpty() -> "$artist - $title"
            title.isNotEmpty() -> title
            hasArtist -> artist
            else -> song.localPath?.substringAfterLast('/')
                ?.substringAfterLast('\\')
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }
                ?: "Unknown Track"
        }
    }

    private fun sanitizeSingleLine(raw: String): String =
        raw.replace('\r', ' ').replace('\n', ' ').trim()

    private data class ParsedExtInf(
        val durationSeconds: Int?,
        val artist: String?,
        val title: String?,
        val rawTitle: String?,
    )

    private fun parseExtInf(body: String): ParsedExtInf {
        val commaIdx = body.indexOf(',')
        val durationPart = (if (commaIdx >= 0) body.substring(0, commaIdx) else body)
            .trim()
            .substringBefore(' ')
        val duration = durationPart.toDoubleOrNull()?.toInt()?.takeIf { it >= 0 }
        val rawTitle = if (commaIdx >= 0) {
            body.substring(commaIdx + 1).trim().takeIf { it.isNotEmpty() }
        } else {
            null
        }
        if (rawTitle == null) {
            return ParsedExtInf(duration, null, null, null)
        }
        val dashIdx = rawTitle.indexOf(" - ")
        return if (dashIdx > 0 && dashIdx + 3 < rawTitle.length) {
            val artist = rawTitle.substring(0, dashIdx).trim().takeIf { it.isNotEmpty() }
            val title = rawTitle.substring(dashIdx + 3).trim().takeIf { it.isNotEmpty() }
            ParsedExtInf(duration, artist, title, rawTitle)
        } else {
            ParsedExtInf(duration, null, rawTitle, rawTitle)
        }
    }
}
