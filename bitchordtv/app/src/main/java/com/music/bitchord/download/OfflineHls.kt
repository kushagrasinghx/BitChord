package com.music.bitchord.download

import android.content.Context
import android.net.Uri
import com.music.bitchord.data.Http
import com.music.bitchord.data.lyrics.WORD_LYRICS_FIELD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File

/**
 * An offline copy of an unencrypted HLS media playlist.  This deliberately
 * keeps fMP4 samples untouched: Media3's HLS reader is the same one that
 * plays the network stream, and therefore supports codecs MediaMuxer cannot
 * put in a fresh MP4 (notably FLAC and Dolby variants).
 */
internal object OfflineHls {
    suspend fun save(
        context: Context,
        id: String,
        url: String,
        headers: Map<String, String>,
        onProgress: (Long, Long) -> Unit,
        lyrics: LyricsTag.Embeddable?,
        artwork: MediaTagger.Artwork?,
    ): Uri = withContext(Dispatchers.IO) {
        val root = File(File(context.filesDir, "offline-hls"), id.hashCode().toUInt().toString(16))
        root.deleteRecursively()
        if (!root.mkdirs()) error("Could not create offline HLS package")
        try {
            fun request(target: String) = Request.Builder().url(target).apply {
                headers.forEach { (name, value) -> header(name, value) }
            }.build()
            val playlist = Http.client.newCall(request(url)).execute().use { response ->
                if (!response.isSuccessful) error("HLS playlist failed (HTTP ${response.code})")
                response.body?.string() ?: error("Empty HLS playlist")
            }
            if (!playlist.startsWith("#EXTM3U")) error("Invalid HLS playlist")
            if (playlist.contains("#EXT-X-KEY:") && !playlist.contains("METHOD=NONE")) {
                error("Encrypted HLS cannot be saved")
            }
            val base = url.toHttpUrlOrNull() ?: error("Invalid HLS URL")
            val references = playlist.lineSequence().map(String::trim).filter {
                it.isNotEmpty() && !it.startsWith('#')
            }.toMutableList()
            Regex("""#EXT-X-MAP:.*URI=\"([^\"]+)\"""").find(playlist)?.groupValues?.get(1)?.let {
                references.add(0, it)
            }
            if (references.isEmpty()) error("HLS playlist has no fragments")
            val localNames = linkedMapOf<String, String>()
            references.distinct().forEachIndexed { index, remote ->
                coroutineContext.ensureActive()
                val target = base.resolve(remote)?.toString() ?: error("Invalid HLS fragment")
                val local = "segment-${index.toString().padStart(5, '0')}.m4s"
                Http.client.newCall(request(target)).execute().use { response ->
                    if (!response.isSuccessful) error("HLS fragment failed (HTTP ${response.code})")
                    response.body?.byteStream()?.use { input -> File(root, local).outputStream().use(input::copyTo) }
                        ?: error("Empty HLS fragment")
                }
                localNames[remote] = local
                onProgress((index + 1).toLong(), references.distinct().size.toLong())
            }
            val localPlaylist = playlist
                .replace(Regex("""(URI=\")([^\"]+)(\")""")) { match ->
                    match.groupValues[1] + (localNames[match.groupValues[2]] ?: match.groupValues[2]) + match.groupValues[3]
                }
                .lineSequence().joinToString("\n") { line -> localNames[line.trim()] ?: line }
            File(root, "playlist.m3u8").writeText(localPlaylist)
            lyrics?.let { File(root, "lyrics.lrc").writeText(it.enhanced ?: it.plain.orEmpty()) }
            artwork?.let { File(root, "cover.${if (it.mime.contains("png")) "png" else "jpg"}").writeBytes(it.bytes) }
            Uri.fromFile(File(root, "playlist.m3u8"))
        } catch (e: Throwable) {
            root.deleteRecursively()
            throw e
        }
    }
}
