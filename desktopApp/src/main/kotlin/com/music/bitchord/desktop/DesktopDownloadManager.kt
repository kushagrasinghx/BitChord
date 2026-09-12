package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap
import java.net.HttpURLConnection
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
            val stream = DesktopMusicSources.resolve(song, quality).getOrThrow()
            val directory = Path.of(System.getProperty("user.home"), "Music", "BitChord")
            Files.createDirectories(directory)
            val safeName = buildString {
                append(song.artist)
                append(" - ")
                append(song.title)
            }.replace(ILLEGAL_FILENAME, "_").trim().take(180).ifBlank { song.videoId }
            val extension = stream.format.fileExtension()
            val target = directory.resolve("$safeName.$extension")
            val temporary = directory.resolve(".$safeName.$extension.part")
            if (Files.isRegularFile(target) && Files.size(target) > 0L) {
                return Result.success(song.copy(localUri = target.toUri().toString(), localPath = target.toString()))
            }

            val existing = if (Files.isRegularFile(temporary)) Files.size(temporary) else 0L
            val connection = URI(stream.url).toURL().openConnection() as? HttpURLConnection
                ?: error("Downloads require an HTTP audio stream")
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", DOWNLOAD_USER_AGENT)
            stream.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (existing > 0L) connection.setRequestProperty("Range", "bytes=$existing-")
            try {
                val status = connection.responseCode
                check(status in 200..299) { "Download failed (HTTP $status)" }
                val append = existing > 0L && status == HttpURLConnection.HTTP_PARTIAL
                val offset = if (append) existing else 0L
                val total = connection.getHeaderField("Content-Range")
                    ?.substringAfterLast('/')
                    ?.toLongOrNull()
                    ?.takeIf { it > 0L }
                    ?: connection.contentLengthLong.takeIf { it > 0L }?.let { it + offset }
                if (!append && existing > 0L) Files.deleteIfExists(temporary)
                Files.newOutputStream(
                    temporary,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    if (append) StandardOpenOption.APPEND else StandardOpenOption.TRUNCATE_EXISTING,
                ).use { output ->
                    connection.inputStream.use { input ->
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
            } finally {
                connection.disconnect()
            }
            check(Files.size(temporary) > 0L) { "Download failed: nothing was sent" }
            runCatching {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.getOrElse {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
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
        return runCatching { Files.deleteIfExists(file) }
            .onFailure { DesktopTrackLog.log("could not delete the download of '${song.title}': ${it.message}") }
            .getOrDefault(false)
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

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val DOWNLOAD_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
    private val ILLEGAL_FILENAME = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")
}
