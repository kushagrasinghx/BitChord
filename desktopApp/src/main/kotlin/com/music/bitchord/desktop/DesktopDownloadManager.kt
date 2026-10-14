package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** Downloads resolved audio into the user's own BitChord folder. */
object DesktopDownloadManager {
    private val activeJobs = ConcurrentHashMap<String, Job>()

    /** Keeps an interrupted transfer in place and resumes it when the user tries again. */
    suspend fun download(
        song: Song,
        quality: String = DesktopPersistence().string("download_quality", "LOSSLESS"),
        onProgress: (downloaded: Long, total: Long?) -> Unit = { _, _ -> },
    ): Result<Song> {
        val job = currentCoroutineContext()[Job]
        if (job != null) activeJobs[song.videoId] = job
        return try {
            require(song.localPath == null) { "This track is already local" }
            val stream = DesktopMusicSources.resolve(song, quality, forDownload = true).getOrThrow()
            val persistence = DesktopPersistence()
            val baseDir = persistence.downloadFolder() ?: Path.of(System.getProperty("user.home"), "Music", "BitChord")
            val isOrganized = persistence.downloadOrganizeFolders()
            val albumArtist = resolveAlbumArtistString(song).replace(ILLEGAL_FILENAME, "_").trim().ifBlank { "Unknown Artist" }
            val albumName = song.albumName?.replace(ILLEGAL_FILENAME, "_")?.trim()?.takeIf { it.isNotBlank() }
            val directory = if (isOrganized) {
                if (albumName != null) {
                    baseDir.resolve(albumArtist).resolve(albumName)
                } else {
                    baseDir.resolve(albumArtist)
                }
            } else {
                baseDir
            }
            Files.createDirectories(directory)

            if (persistence.downloadCreateNomedia()) {
                runCatching {
                    val rootNomedia = baseDir.resolve(".nomedia")
                    if (!Files.exists(rootNomedia)) Files.createFile(rootNomedia)
                    if (isOrganized) {
                        val subNomedia = directory.resolve(".nomedia")
                        if (!Files.exists(subNomedia)) Files.createFile(subNomedia)
                    }
                }
            }

            val artistSeparator = persistence.downloadArtistSeparator()
            val resolvedArtist = resolveArtistsString(song, artistSeparator)
            val safeArtist = resolvedArtist.replace(ILLEGAL_FILENAME, "_").trim()
            val safeTitle = song.title.replace(ILLEGAL_FILENAME, "_").trim()
            val safeName = buildString {
                if (safeArtist.isNotEmpty()) append(safeArtist)
                if (safeArtist.isNotEmpty() && safeTitle.isNotEmpty()) append(" - ")
                if (safeTitle.isNotEmpty()) append(safeTitle)
            }.take(180).trim().ifBlank { song.videoId }
            // A manifest is an index of the audio, not the audio: fetched as a file it saved a few
            // KB of XML as `.flac`. Its segments are fMP4, so it can only become FLAC or M4A.
            val manifest = DesktopManifestDownload.isManifest(stream)
            val extension = stream.format.fileExtension().let { if (manifest && it != "flac") "m4a" else it }
            val target = directory.resolve("$safeName.$extension")
            val temporary = directory.resolve(".$safeName.$extension.part")
            if (Files.isRegularFile(target) && Files.size(target) > 0L) {
                return Result.success(song.copy(localUri = target.toUri().toString(), localPath = target.toString()))
            }

            if (manifest) {
                // Segments are fetched and joined whole, so there is no partial file to resume.
                Files.deleteIfExists(temporary)
                Files.newOutputStream(temporary).buffered().use { output ->
                    DesktopManifestDownload.save(
                        stream,
                        extension,
                        output,
                        tempDir = java.io.File(System.getProperty("java.io.tmpdir")),
                        userAgent = DOWNLOAD_USER_AGENT,
                    ) { done, total -> onProgress(done, total) }
                }
                check(Files.size(temporary) > 0L) { "Download failed: nothing was sent" }
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
                saveCompanions(song, safeName, directory, isOrganized, persistence)
                return Result.success(song.copy(localUri = target.toUri().toString(), localPath = target.toString()))
            }

            val existing = if (Files.isRegularFile(temporary)) Files.size(temporary) else 0L
            val request = DesktopDownloadHttp.request(
                stream.url,
                DOWNLOAD_USER_AGENT,
                stream.headers,
                rangeFrom = existing.takeIf { it > 0L },
            )
            DesktopDownloadHttp.client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Download failed (HTTP ${response.code})" }
                val body = response.body
                val append = existing > 0L && response.code == HTTP_PARTIAL
                val offset = if (append) existing else 0L
                val total = response.header("Content-Range")
                    ?.substringAfterLast('/')
                    ?.toLongOrNull()
                    ?.takeIf { it > 0L }
                    ?: body.contentLength().takeIf { it > 0L }?.let { it + offset }
                if (!append && existing > 0L) Files.deleteIfExists(temporary)
                Files.newOutputStream(
                    temporary,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    if (append) StandardOpenOption.APPEND else StandardOpenOption.TRUNCATE_EXISTING,
                ).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = offset
                        onProgress(downloaded, total)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                    }
                }
            }
            check(Files.size(temporary) > 0L) { "Download failed: nothing was sent" }
            runCatching {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.getOrElse {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
            saveCompanions(song, safeName, directory, isOrganized, persistence)
            Result.success(song.copy(localUri = target.toUri().toString(), localPath = target.toString()))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Result.failure(failure)
        } finally {
            if (job != null) activeJobs.remove(song.videoId, job)
        }
    }

    fun cancel(videoId: String) {
        activeJobs[videoId]?.cancel(CancellationException("Download cancelled"))
    }

    /**
     * The file saved for [song], or null when the record has outlived it.
     *
     * A download record is a claim about a folder this app does not own: the listener is expected
     * to manage it with a file manager, so an entry can name a file that has since been moved or
     * deleted. Android checks before it answers for the same reason — without it the player is
     * handed a path that is simply not there and the track refuses to play, saying only "could not
     * open stream".
     */
    fun savedFile(song: Song): Path? {
        val path = song.localPath?.let { runCatching { Path.of(it) }.getOrNull() }
            ?: song.localUri?.takeIf { it.startsWith("file:") }
                ?.let { runCatching { Path.of(URI(it)) }.getOrNull() }
            ?: return null
        return path.takeIf { Files.isRegularFile(it) && runCatching { Files.size(it) > 0 }.getOrDefault(false) }
    }

    /** Whether [song] is really on this computer, rather than merely recorded as being. */
    fun isSaved(song: Song): Boolean = savedFile(song) != null

    /**
     * The records that still name a file, with the rest dropped.
     *
     * A claim that has just been shown to be false is not worth keeping to be shown false again on
     * the next play.
     */
    fun verified(downloads: List<Song>): List<Song> = downloads.filter(::isSaved)

    /** Deletes the file saved for [song]. Returns whether there was one to delete. */
    fun delete(song: Song): Boolean {
        val file = savedFile(song) ?: return false
        val parent = file.parent
        val stem = file.fileName.toString().substringBeforeLast('.')
        if (parent != null) {
            runCatching { Files.deleteIfExists(parent.resolve("$stem.lrc")) }
            runCatching { Files.deleteIfExists(parent.resolve("$stem.jpg")) }
        }
        return runCatching { Files.deleteIfExists(file) }
            .onFailure { DesktopTrackLog.log("could not delete the download of '${song.title}': ${it.message}") }
            .getOrDefault(false)
    }

    private suspend fun saveCompanions(
        song: Song,
        safeName: String,
        directory: Path,
        isOrganized: Boolean,
        persistence: DesktopPersistence,
    ) {
        // 1. Line-synced lyrics (.lrc)
        if (persistence.downloadSaveLrc()) {
            runCatching {
                val lyricsRes = DesktopLyricsClient.lookup(song).getOrNull()
                val lrcText = lyricsRes?.lines?.takeIf { it.isNotEmpty() }?.joinToString("\n") { line ->
                    val mm = (line.timeMs / 60000).toString().padStart(2, '0')
                    val ss = ((line.timeMs % 60000) / 1000).toString().padStart(2, '0')
                    val xx = ((line.timeMs % 1000) / 10).toString().padStart(2, '0')
                    "[$mm:$ss.$xx] ${line.text}"
                }
                if (!lrcText.isNullOrBlank()) {
                    val lrcFile = directory.resolve("$safeName.lrc")
                    Files.writeString(lrcFile, lrcText, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                }
            }
        }

        // 2. Artwork (cover.jpg and <safeName>.jpg)
        val thumbUrl = song.thumbnailUrl
        if (thumbUrl != null && (persistence.downloadSaveAlbumCover() || persistence.downloadSaveTrackCover())) {
            runCatching {
                val artReq = DesktopDownloadHttp.request(thumbUrl, DOWNLOAD_USER_AGENT, emptyMap())
                DesktopDownloadHttp.client.newCall(artReq).execute().use { artResp ->
                    if (artResp.isSuccessful) {
                        val artBytes = artResp.body.bytes()
                        if (artBytes.isNotEmpty()) {
                            // Cover.jpg strictly gated on isOrganized and non-blank albumName
                            if (persistence.downloadSaveAlbumCover() && isOrganized && !song.albumName.isNullOrBlank()) {
                                val coverFile = directory.resolve("cover.jpg")
                                if (!Files.exists(coverFile)) {
                                    Files.write(coverFile, artBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                                }
                            }
                            if (persistence.downloadSaveTrackCover()) {
                                val trackCover = directory.resolve("$safeName.jpg")
                                Files.write(trackCover, artBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun DesktopStreamFormat.fileExtension(): String {
        val codec = codec.orEmpty().substringAfterLast('/').substringBefore(';').lowercase()
        return when (codec) {
            "flac" -> "flac"
            "wav", "wave" -> "wav"
            "alac" -> "m4a"
            "aac" -> "m4a"
            "mp3" -> "mp3"
            "ogg", "opus" -> "ogg"
            "webm" -> "webm"
            else -> "m4a"
        }
    }

    fun resolveArtistsString(song: Song, separator: String): String {
        val names = song.artists.map { it.name.trim() }.filter { it.isNotBlank() }
        if (names.isNotEmpty()) return names.joinToString(separator)
        return song.artist.trim().ifBlank { "Unknown Artist" }
    }

    fun resolveAlbumArtistString(song: Song): String {
        val lead = song.artists.firstOrNull()?.name?.trim()?.takeIf { it.isNotBlank() }
        if (lead != null) return lead
        val firstSplit = song.artist.split(',', ';', '/', '&').firstOrNull()?.trim()
        if (!firstSplit.isNullOrBlank()) return firstSplit
        return song.artist.trim().ifBlank { "Unknown Artist" }
    }

    private const val HTTP_PARTIAL = 206
    private const val DOWNLOAD_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
    private val ILLEGAL_FILENAME = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")
}
