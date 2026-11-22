package com.music.bitchord.data.remote

import android.content.Context
import android.net.Uri
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Fragments change Coil's URI/disk identities without changing the requested HTTP or SMB path.
internal fun scopedRemoteCoverUrl(url: String, sourceScope: Long): String =
    "${url.substringBefore('#')}#bitchord-art-$sourceScope"

internal fun remoteCoverScope(url: String): Long? =
    url.substringAfter('#', "").takeIf { it.startsWith("bitchord-art-") }
        ?.removePrefix("bitchord-art-")?.toLongOrNull()

internal fun embeddedArtKey(song: Song, sourceScope: Long): String =
    "$sourceScope:${song.videoId.length}:${song.videoId}${song.localUri.orEmpty()}"

/**
 * Embedded covers for remote tracks, extracted lazily and cached.
 *
 * Listings never touch audio bytes, so a track whose art lives inside the
 * file arrives with no thumbnail. The first surface that wants to draw it —
 * a row, an album card, the player — asks here instead: memory first, then
 * a disk cache of extracted pictures, and only then the bounded ranged
 * reads that pull the picture out of the file. Concurrent asks for one
 * track share a single extraction.
 *
 * Disk names are content-blind hashes of the scoped track source: ids embed full URLs
 * with slashes and escapes that must never reach the filesystem. A
 * server-side overwrite keeps serving the stale copy — accepted, since
 * revalidating would cost a request per row per list.
 */
object RemoteArtworkStore {

    private const val MAX_DIR_BYTES = 100L * 1024 * 1024

    private val memory = ConcurrentHashMap<String, String>()
    private val reads = RemoteArtworkReads()

    fun supports(song: Song): Boolean = song.videoId.startsWith("webdav:") || song.videoId.startsWith("smb:")

    /** A UI can use this in its first frame without checking disk or network. */
    fun cachedArt(song: Song, sourceScope: Long): String? {
        if (!supports(song)) return song.thumbnailUrl
        return song.thumbnailUrl?.let { scopedRemoteCoverUrl(it, sourceScope) }
            ?: memory[embeddedArtKey(song, sourceScope)]
    }

    /**
     * A drawable art URI for [song]: its own thumbnail when it has one, else
     * the extracted embedded picture, else null. Propagates caller cancellation.
     */
    suspend fun resolveArt(
        context: Context, song: Song, sourceScope: Long, speculative: Boolean = false,
    ): String? {
        val auth = AppSettings.remoteArtworkAuth(sourceScope) ?: return null
        cachedArt(song, sourceScope)?.let { return it }
        val key = embeddedArtKey(song, sourceScope)
        val appContext = context.applicationContext
        return reads.read(key, speculative) {
            withContext(Dispatchers.IO) {
                if (AppSettings.remoteArtworkAuth(sourceScope) !== auth) return@withContext null
                (memory[key] ?: diskHit(appContext, key) ?: extractAndStore(appContext, song, key, auth, speculative))
                    ?.also { memory[key] = it }
            }
        }.takeIf { AppSettings.remoteArtworkAuth(sourceScope) === auth }
    }

    private suspend fun extractAndStore(
        context: Context, song: Song, key: String, auth: AppSettings.RemoteArtworkAuth, speculative: Boolean,
    ): String? =
        withContext(Dispatchers.IO) {
            val url = song.localUri?.takeIf { it.startsWith("http") || it.startsWith("smb://") }
                ?: return@withContext null
            val picture = RemoteArtReader.picture(url, url.startsWith("smb://"), speculative, auth)
                ?: return@withContext null
            if (auth.scope != AppSettings.remoteArtworkScope.value) return@withContext null
            val dir = File(context.cacheDir, "remote_art").apply { mkdirs() }
            val file = File(dir, "${cacheKey(key)}.${extensionFor(picture.mime)}")
            runCatching {
                file.outputStream().use { it.write(picture.bytes) }
                evict(dir)
            }.getOrNull() ?: return@withContext null
            Uri.fromFile(file).toString()
        }

    private fun diskHit(context: Context, key: String): String? {
        val dir = File(context.cacheDir, "remote_art")
        if (!dir.isDirectory) return null
        // The extension depends on the sniffed mime, so match by stem.
        val match = dir.listFiles { _, name -> name.startsWith("${cacheKey(key)}.") }
            ?.filter { it.isFile && it.length() > 0 }
            ?.maxByOrNull { it.lastModified() }
        return match?.let { Uri.fromFile(it).toString() }
    }

    private fun extensionFor(mime: String): String = when (mime.lowercase()) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/bmp" -> "bmp"
        else -> "bin"
    }

    private fun cacheKey(videoId: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        return digest.digest(videoId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun evict(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_DIR_BYTES) return
        files.sortedBy { it.lastModified() }.forEach {
            if (total <= MAX_DIR_BYTES) return
            total -= it.length()
            it.delete()
        }
    }
}
