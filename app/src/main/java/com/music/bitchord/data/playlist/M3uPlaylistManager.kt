package com.music.bitchord.data.playlist

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.download.DownloadStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Android Storage Access Framework (SAF) integration for M3U/M3U8 playlist
 * import and export.
 */
object M3uPlaylistManager {

    private const val MAX_PLAYLIST_BYTES = 10 * 1024 * 1024 // 10 MB

    fun suggestedExportFileName(playlistTitle: String, format: M3uFormat): String {
        val base = M3uCodec.sanitizeFileName(playlistTitle, fallback = "playlist")
        return "$base.${format.extension}"
    }

    /**
     * Serializes [songs] and writes the M3U/M3U8 file to [targetUri] via SAF.
     * Never copies audio files and never mutates the source playlist.
     */
    suspend fun exportToUri(
        context: Context,
        targetUri: Uri,
        songs: List<Song>,
        format: M3uFormat,
        playlistTitle: String? = null,
    ): Result<M3uSerializeResult> = withContext(Dispatchers.IO) {
        runCatching {
            val serialized = M3uCodec.serialize(
                songs = songs,
                format = format,
                playlistTitle = playlistTitle,
                isLocalMissing = { song -> isLocalSongMissing(context, song) },
            )
            context.contentResolver.openOutputStream(targetUri, "wt")?.use { out ->
                out.write(serialized.content.toByteArray(StandardCharsets.UTF_8))
                out.flush()
            } ?: error("Could not open destination file for writing")
            serialized
        }
    }

    /**
     * Reads an M3U/M3U8 document from [sourceUri] via SAF, parses it, and
     * resolves entries against downloaded music and indexed MediaStore tracks.
     */
    suspend fun prepareImport(
        context: Context,
        sourceUri: Uri,
    ): Result<M3uImportPreview> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = queryDisplayName(context, sourceUri)
            val bytes = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                val buffer = input.readNBytes(MAX_PLAYLIST_BYTES + 1)
                if (buffer.size > MAX_PLAYLIST_BYTES) {
                    error("Playlist file is too large")
                }
                buffer
            } ?: error("Could not open playlist file")

            if (bytes.any { it == 0.toByte() }) {
                error("File does not appear to be a valid M3U/M3U8 text playlist")
            }

            val content = bytes.toString(StandardCharsets.UTF_8)
            val document = M3uCodec.parse(content)
            val suggestedTitle = displayName
                ?.substringBeforeLast('.')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "Imported Playlist"

            val downloadedSongs = LocalMediaRepository.getDownloadedSongs(context)
            val localSongs = LocalMediaRepository.getLocalMusic(context)
            val index = M3uTrackResolver.LocalTrackIndex(downloadedSongs + localSongs)

            val baseDir = resolveDocumentDirectory(context, sourceUri)
            val searchDirs = buildSearchDirs()

            M3uTrackResolver.resolve(
                document = document,
                suggestedTitle = suggestedTitle,
                index = index,
                baseDir = baseDir,
                searchDirs = searchDirs,
                fileExists = { path ->
                    runCatching {
                        val f = File(path)
                        f.isFile && f.canRead()
                    }.getOrDefault(false)
                },
                songFromLocalPath = { path ->
                    runCatching {
                        val uri = Uri.fromFile(File(path))
                        runBlocking { LocalMediaRepository.songFromUri(context, uri) }
                    }.getOrNull()
                },
                songFromContentUri = { uriStr ->
                    runCatching {
                        val uri = Uri.parse(uriStr)
                        val readable = context.contentResolver.openFileDescriptor(uri, "r")?.use { true } == true
                        if (!readable) return@runCatching null
                        runBlocking { LocalMediaRepository.songFromUri(context, uri) }
                    }.getOrNull()
                },
            )
        }
    }

    private fun isLocalSongMissing(context: Context, song: Song): Boolean {
        val path = song.localPath?.takeIf { it.isNotBlank() }
        if (path != null) {
            val existsOnDisk = runCatching { File(path).exists() }.getOrDefault(false)
            if (existsOnDisk) return false
        }
        val uri = song.localUri?.takeIf { it.isNotBlank() } ?: return false
        return !DownloadStore.exists(context, Uri.parse(uri))
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun resolveDocumentDirectory(context: Context, uri: Uri): String? = runCatching {
        if (uri.scheme == "file") {
            return@runCatching uri.path?.let { File(it).parent }
        }
        if (DocumentsContract.isDocumentUri(context, uri) &&
            uri.authority == "com.android.externalstorage.documents"
        ) {
            val docId = DocumentsContract.getDocumentId(uri)
            if (docId.startsWith("raw:")) {
                val path = docId.removePrefix("raw:")
                return@runCatching File(path).parent
            }
            val volume = docId.substringBefore(':')
            val rel = docId.substringAfter(':', "")
            val root = if (volume.equals("primary", ignoreCase = true)) {
                "/storage/emulated/0"
            } else {
                "/storage/$volume"
            }
            val fullPath = listOf(root, rel).filter { it.isNotBlank() }.joinToString("/")
            return@runCatching File(fullPath).parent
        }
        null
    }.getOrNull()

    private fun buildSearchDirs(): List<String> = buildList {
        AppSettings.downloadFolderUri.value.takeIf { it.isNotBlank() }?.let { uri ->
            LocalMediaRepository.selectedFolder(uri)?.absolutePath?.let(::add)
        }
        AppSettings.localMusicFolderUri.value.takeIf { it.isNotBlank() }?.let { uri ->
            LocalMediaRepository.selectedFolder(uri)?.absolutePath?.let(::add)
        }
        runCatching {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            add(File(musicDir, DownloadStore.FOLDER).absolutePath)
            add(File(musicDir, DownloadStore.LEGACY_FOLDER).absolutePath)
            add(musicDir.absolutePath)
        }
    }.distinct()
}
