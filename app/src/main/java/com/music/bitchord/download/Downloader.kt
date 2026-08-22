package com.music.bitchord.download

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.Http
import com.music.bitchord.data.innertube.PlayerClient
import com.music.bitchord.data.innertube.StreamResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

/**
 * Pulls a resolved stream onto disk.
 *
 * Two things here are not obvious and both are load-bearing:
 *
 *  - **Bounded ranges, not one long GET.** googlevideo paces a continuous
 *    response down to roughly playback speed — about 15kB/s, against 5.7MB/s
 *    for the same bytes asked for as ranges. That is the difference between a
 *    four-minute track saving in a second and saving in four minutes, and it is
 *    the same finding [ChunkedDataSource][com.music.bitchord.playback.ChunkedDataSource]
 *    exists for.
 *  - **The client's own headers.** googlevideo bakes the identity that minted a
 *    URL into it as `c=`/`cver=` and compares that against the headers of the
 *    request that comes back for the bytes. Fetching with anything else is a
 *    403 — so the fetch is dressed as whatever the URL says made it, which
 *    [PlayerClient.forStreamUrl] can recover from the URL alone.
 *
 * Sequential rather than parallel. Ranges are served at line rate, and a
 * typical Opus track is five megabytes: splitting that across connections buys
 * nothing a user could perceive and costs the temp files and reassembly that a
 * cancelled download would then have to clean up.
 */
object Downloader {

    private const val TAG = "BitChord"

    /** Matches the range size read-ahead settled on; large enough to amortise, small enough to cancel promptly. */
    private const val CHUNK_BYTES = 2L * 1024 * 1024

    private const val BUFFER_BYTES = 64 * 1024

    /**
     * Fetch all of [stream] into [sink].
     *
     * @param onProgress called as bytes land, with the running total and the
     *   full size. Never called with a total of zero.
     * @return how many bytes were written.
     */
    suspend fun fetch(
        videoId: String,
        stream: StreamResolver.Stream,
        sink: OutputStream,
        onProgress: (written: Long, total: Long) -> Unit,
    ): Long = withContext(Dispatchers.IO) {
        var url = stream.url
        val total = contentLength(url) ?: error("Track unavailable: no length to fetch")

        var position = 0L
        var reresolved = false
        val buffer = ByteArray(BUFFER_BYTES)

        while (position < total) {
            coroutineContext.ensureActive()
            val length = minOf(CHUNK_BYTES, total - position)

            val response = try {
                open(url, position, length)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "range at $position failed for $videoId: ${e.message}")
                throw e
            }

            // A URL that served its opening and then refuses is the one failure
            // worth a second attempt: it means the identity behind it has been
            // stood down mid-download, not that the track is gone. Telling the
            // resolver is what stops the next track failing the same way.
            if (response.code in REFUSAL_CODES) {
                response.close()
                StreamResolver.onPlaybackRefused(url, response.code)
                if (reresolved) error("Download refused after ${position}B (HTTP ${response.code})")
                reresolved = true
                Log.w(TAG, "re-resolving $videoId after HTTP ${response.code} at $position")
                url = StreamResolver.resolveForDownload(videoId).url
                // Resolving again re-runs the whole client walk, and a
                // different client can answer with a different format. Resuming
                // one stream into the middle of another produces a file that is
                // the right length and unplayable, so a length that has moved
                // is a failure rather than something to work around.
                if (contentLength(url) != total) error("The stream changed mid-download — try again")
                continue
            }

            response.use {
                if (it.code !in 200..299) error("Download failed (HTTP ${it.code})")
                val body = it.body ?: error("Download failed: empty response")
                val source = body.byteStream()
                var readForChunk = 0L
                while (readForChunk < length) {
                    coroutineContext.ensureActive()
                    val wanted = minOf(buffer.size.toLong(), length - readForChunk).toInt()
                    val read = source.read(buffer, 0, wanted)
                    if (read == -1) break
                    sink.write(buffer, 0, read)
                    readForChunk += read
                    position += read
                    onProgress(position, total)
                }
                // A range that stops short is not fatal on its own — the next
                // pass simply asks for what is left — but one that yields
                // nothing at all would loop here forever.
                if (readForChunk == 0L) error("Download stalled at ${position}B")
            }
        }

        sink.flush()
        position
    }

    private fun open(url: String, position: Long, length: Long) = Http.client
        .newCall(
            Request.Builder()
                .url(url)
                .header("Range", "bytes=$position-${position + length - 1}")
                .apply {
                    PlayerClient.forStreamUrl(url).mediaHeaders()
                        .forEach { (name, value) -> header(name, value) }
                }
                .build(),
        )
        .execute()

    /**
     * How many bytes the whole track is.
     *
     * Every progressive googlevideo URL carries it as `clen`, which costs no
     * request at all. The `bytes=0-0` probe behind it is for the URLs that
     * don't — the extraction failsafe can produce one — and reads the total out
     * of the `Content-Range` header of a one-byte response.
     */
    private fun contentLength(url: String): Long? {
        url.toHttpUrlOrNull()?.queryParameter("clen")?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?.let { return it }

        return runCatching {
            open(url, 0, 1).use { response ->
                response.header("Content-Range")
                    ?.substringAfter('/', "")
                    ?.toLongOrNull()
                    ?.takeIf { it > 0 }
            }
        }.onFailure { Log.w(TAG, "could not measure the track: ${it.message}") }.getOrNull()
    }

    private val REFUSAL_CODES = setOf(403, 404, 410)
}
