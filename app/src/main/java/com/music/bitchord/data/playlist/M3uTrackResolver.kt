package com.music.bitchord.data.playlist

import com.music.bitchord.data.model.Song
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

enum class M3uImportIssueKind {
    MISSING,
    UNSUPPORTED,
    REMOTE_UNSUPPORTED,
    DUPLICATE,
}

data class M3uImportIssue(
    val location: String,
    val label: String,
    val kind: M3uImportIssueKind,
)

data class M3uImportPreview(
    val suggestedTitle: String,
    val totalEntries: Int,
    val matchedSongs: List<Song>,
    val missingEntries: List<M3uImportIssue>,
    val unsupportedEntries: List<M3uImportIssue>,
    val duplicateCount: Int,
    val hasExtM3uHeader: Boolean,
) {
    val matchedCount: Int get() = matchedSongs.size
    val missingCount: Int get() = missingEntries.size
    val unsupportedCount: Int get() = unsupportedEntries.size
}

/**
 * Pure-Kotlin track resolver that matches parsed [M3uEntry] items against
 * indexed local/downloaded tracks and optional filesystem/MediaStore probes.
 */
object M3uTrackResolver {

    class LocalTrackIndex(songs: List<Song>) {
        private val byUri = HashMap<String, Song>(songs.size)
        private val byPath = HashMap<String, Song>(songs.size)
        private val byFileName = HashMap<String, MutableList<Song>>(songs.size)
        private val byVideoId = HashMap<String, Song>(songs.size)

        init {
            for (song in songs) {
                song.localUri?.let { byUri.putIfAbsent(it, song) }
                song.localPath?.let { path ->
                    byPath.putIfAbsent(normalizePath(path), song)
                    val name = fileNameOf(path).lowercase(Locale.ROOT)
                    if (name.isNotEmpty()) {
                        byFileName.getOrPut(name) { mutableListOf() }.add(song)
                    }
                }
                if (M3uCodec.isValidYouTubeVideoId(song.videoId) && song.localUri != null) {
                    byVideoId.putIfAbsent(song.videoId, song)
                }
            }
        }

        fun findByUri(uri: String): Song? = byUri[uri]

        fun findByPath(path: String): Song? = byPath[normalizePath(path)]

        fun findByVideoId(videoId: String): Song? = byVideoId[videoId]

        fun findByRelativeSuffix(relativePath: String): Song? {
            val normalizedRel = relativePath.replace('\\', '/').trimStart('/').lowercase(Locale.ROOT)
            if (normalizedRel.isEmpty()) return null
            val name = fileNameOf(normalizedRel)
            val candidates = byFileName[name] ?: return null
            if (normalizedRel.contains('/')) {
                val suffixMatches = candidates.filter { candidate ->
                    candidate.localPath?.replace('\\', '/')?.lowercase(Locale.ROOT)?.endsWith("/$normalizedRel") == true
                }
                if (suffixMatches.size == 1) return suffixMatches.first()
            }
            return if (candidates.size == 1) candidates.first() else null
        }

        fun findUniqueByFileName(path: String): Song? {
            val name = fileNameOf(path).lowercase(Locale.ROOT)
            if (name.isEmpty()) return null
            val candidates = byFileName[name] ?: return null
            return if (candidates.size == 1) candidates.first() else null
        }
    }

    fun resolve(
        document: M3uDocument,
        suggestedTitle: String,
        index: LocalTrackIndex,
        baseDir: String? = null,
        searchDirs: List<String> = emptyList(),
        fileExists: (String) -> Boolean = { false },
        songFromLocalPath: (String) -> Song? = { null },
        songFromContentUri: (String) -> Song? = { null },
    ): M3uImportPreview {
        val matched = mutableListOf<Song>()
        val seenUris = HashSet<String>()
        val seenRawLocations = HashSet<String>()
        val missing = mutableListOf<M3uImportIssue>()
        val unsupported = mutableListOf<M3uImportIssue>()
        var duplicateCount = 0

        for (entry in document.entries) {
            val label = entry.rawExtInf
                ?: fileNameOf(entry.location).ifBlank { entry.location }

            when (entry.kind) {
                M3uEntryKind.HTTP_URL -> {
                    val ytId = M3uCodec.extractYouTubeVideoId(entry.location)
                    if (ytId != null) {
                        val downloaded = index.findByVideoId(ytId)
                        if (downloaded != null) {
                            val key = downloaded.localUri ?: downloaded.videoId
                            if (!seenUris.add(key)) {
                                duplicateCount++
                            } else {
                                matched += applyExtInfFallback(downloaded, entry)
                            }
                        } else {
                            unsupported += M3uImportIssue(
                                location = entry.location,
                                label = label,
                                kind = M3uImportIssueKind.REMOTE_UNSUPPORTED,
                            )
                        }
                    } else {
                        unsupported += M3uImportIssue(
                            location = entry.location,
                            label = label,
                            kind = M3uImportIssueKind.UNSUPPORTED,
                        )
                    }
                }

                M3uEntryKind.CONTENT_URI -> {
                    val song = index.findByUri(entry.location) ?: songFromContentUri(entry.location)
                    val key = song?.localUri
                    if (song != null && key != null) {
                        if (!seenUris.add(key)) {
                            duplicateCount++
                        } else {
                            matched += applyExtInfFallback(song, entry)
                        }
                    } else {
                        if (!seenRawLocations.add(entry.location)) {
                            duplicateCount++
                        } else {
                            missing += M3uImportIssue(entry.location, label, M3uImportIssueKind.MISSING)
                        }
                    }
                }

                M3uEntryKind.FILE_URI -> {
                    val decodedPath = decodeFileUriPath(entry.location)
                    if (decodedPath == null || !M3uCodec.hasSupportedAudioExtension(decodedPath)) {
                        unsupported += M3uImportIssue(entry.location, label, M3uImportIssueKind.UNSUPPORTED)
                        continue
                    }
                    val resolved = resolvePath(
                        path = decodedPath,
                        rawUri = entry.location,
                        index = index,
                        fileExists = fileExists,
                        songFromLocalPath = songFromLocalPath,
                    )
                    val key = resolved?.localUri
                    if (resolved != null && key != null) {
                        if (!seenUris.add(key)) {
                            duplicateCount++
                        } else {
                            matched += applyExtInfFallback(resolved, entry)
                        }
                    } else {
                        if (!seenRawLocations.add(entry.location)) {
                            duplicateCount++
                        } else {
                            missing += M3uImportIssue(entry.location, label, M3uImportIssueKind.MISSING)
                        }
                    }
                }

                M3uEntryKind.ABSOLUTE_PATH -> {
                    if (!M3uCodec.hasSupportedAudioExtension(entry.location)) {
                        unsupported += M3uImportIssue(entry.location, label, M3uImportIssueKind.UNSUPPORTED)
                        continue
                    }
                    val resolved = resolvePath(
                        path = entry.location,
                        rawUri = null,
                        index = index,
                        fileExists = fileExists,
                        songFromLocalPath = songFromLocalPath,
                    )
                    val key = resolved?.localUri
                    if (resolved != null && key != null) {
                        if (!seenUris.add(key)) {
                            duplicateCount++
                        } else {
                            matched += applyExtInfFallback(resolved, entry)
                        }
                    } else {
                        if (!seenRawLocations.add(entry.location)) {
                            duplicateCount++
                        } else {
                            missing += M3uImportIssue(entry.location, label, M3uImportIssueKind.MISSING)
                        }
                    }
                }

                M3uEntryKind.RELATIVE_PATH -> {
                    if (!M3uCodec.hasSupportedAudioExtension(entry.location)) {
                        unsupported += M3uImportIssue(entry.location, label, M3uImportIssueKind.UNSUPPORTED)
                        continue
                    }
                    val candidateDirs = buildList {
                        if (!baseDir.isNullOrBlank()) add(baseDir)
                        addAll(searchDirs)
                    }
                    var resolved: Song? = null
                    for (dir in candidateDirs) {
                        val fullPath = M3uCodec.resolveRelativePath(dir, entry.location)
                        resolved = resolveExactPath(fullPath, index, fileExists, songFromLocalPath)
                        if (resolved != null) break
                    }
                    if (resolved == null) {
                        resolved = index.findByRelativeSuffix(entry.location)
                    }
                    val key = resolved?.localUri
                    if (resolved != null && key != null) {
                        if (!seenUris.add(key)) {
                            duplicateCount++
                        } else {
                            matched += applyExtInfFallback(resolved, entry)
                        }
                    } else {
                        if (!seenRawLocations.add(entry.location)) {
                            duplicateCount++
                        } else {
                            missing += M3uImportIssue(entry.location, label, M3uImportIssueKind.MISSING)
                        }
                    }
                }
            }
        }

        val finalTitle = document.playlistTitle?.takeIf { it.isNotBlank() }
            ?: suggestedTitle.ifBlank { "Imported Playlist" }

        return M3uImportPreview(
            suggestedTitle = finalTitle,
            totalEntries = document.entries.size,
            matchedSongs = matched,
            missingEntries = missing,
            unsupportedEntries = unsupported,
            duplicateCount = duplicateCount,
            hasExtM3uHeader = document.hasExtM3uHeader,
        )
    }

    private fun resolvePath(
        path: String,
        rawUri: String?,
        index: LocalTrackIndex,
        fileExists: (String) -> Boolean,
        songFromLocalPath: (String) -> Song?,
    ): Song? {
        if (rawUri != null) {
            index.findByUri(rawUri)?.let { return it }
        }
        resolveExactPath(path, index, fileExists, songFromLocalPath)?.let { return it }
        return index.findUniqueByFileName(path)
    }

    private fun resolveExactPath(
        path: String,
        index: LocalTrackIndex,
        fileExists: (String) -> Boolean,
        songFromLocalPath: (String) -> Song?,
    ): Song? {
        index.findByPath(path)?.let { return it }
        if (fileExists(path)) {
            songFromLocalPath(path)?.let { return it }
        }
        return null
    }

    private fun applyExtInfFallback(song: Song, entry: M3uEntry): Song {
        val updatedTitle = if (song.title.isBlank() && !entry.title.isNullOrBlank()) entry.title else song.title
        val updatedArtist = if ((song.artist.isBlank() || song.artist == "Unknown Artist") && !entry.artist.isNullOrBlank()) {
            entry.artist
        } else {
            song.artist
        }
        val updatedDuration = song.durationText ?: M3uCodec.formatDurationSeconds(entry.durationSeconds)
        return if (updatedTitle == song.title && updatedArtist == song.artist && updatedDuration == song.durationText) {
            song
        } else {
            song.copy(title = updatedTitle, artist = updatedArtist, durationText = updatedDuration)
        }
    }

    internal fun decodeFileUriPath(fileUri: String): String? {
        val raw = when {
            fileUri.startsWith("file:///", ignoreCase = true) -> fileUri.substring("file://".length)
            fileUri.startsWith("file://", ignoreCase = true) -> {
                val rest = fileUri.substring("file://".length)
                if (rest.startsWith("localhost/", ignoreCase = true)) {
                    rest.substring("localhost".length)
                } else {
                    rest
                }
            }
            else -> return null
        }
        return runCatching {
            URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        }.getOrNull()
    }

    private fun normalizePath(path: String): String =
        path.replace('\\', '/').trimEnd('/').lowercase(Locale.ROOT)

    private fun fileNameOf(path: String): String =
        path.replace('\\', '/').substringAfterLast('/').substringBefore('?').substringBefore('#')
}
