package com.music.bitchord.download

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.Http
import com.music.bitchord.data.innertube.PlayerClient
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.data.sources.StreamFormat
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
 * typical AAC track is four megabytes: splitting that across connections buys
 * nothing a user could perceive and costs the temp files and reassembly that a
 * cancelled download would then have to clean up.
 *
 * None of the above is true of a stream from a configured source rather than
 * from YouTube, which is what [fetchDirect] is for.
 */
object Downloader {

    private const val TAG = "BitChord"

    /** Matches the range size read-ahead settled on; large enough to amortise, small enough to cancel promptly. */
    private const val CHUNK_BYTES = 2L * 1024 * 1024

    private const val BUFFER_BYTES = 64 * 1024

    /**
     * Fetch all of [stream] into [sink].
     *
     * @param maxKbps the ceiling [stream] was resolved under, needed again for
     *   the re-resolve below. Resolving at a different one would pick a
     *   different rung of the AAC ladder, and the length check that guards the
     *   resume would then fail a retry that had nothing wrong with it.
     * @param onProgress called as bytes land, with the running total and the
     *   full size. Never called with a total of zero.
     * @return how many bytes were written.
     */
    suspend fun fetch(
        videoId: String,
        stream: StreamResolver.Stream,
        maxKbps: Int,
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
                url = StreamResolver.resolveForDownload(videoId, maxKbps).url
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

    /**
     * Fetch all of [url] into [sink], for a stream that isn't googlevideo's.
     *
     * Deliberately not a parameterised [fetch], on two counts that both matter:
     *
     *  - **One GET, not ranges.** The chunk loop above exists to defeat
     *    googlevideo's pacing, and nothing else paces bytes that way. Worse, a
     *    server that ignores `Range` answers 200 with the whole file rather
     *    than 206 with the slice asked for — and that loop would then write the
     *    opening of the file into the middle of the output and commit something
     *    the right length and unplayable.
     *  - **The source's own headers.** A source hands them over alongside the
     *    URL — see [SourceStream.headers][com.music.bitchord.data.sources.SourceStream.headers]
     *    — and they are whatever that server binds its links to. The
     *    [PlayerClient] identity [fetch] recovers from a googlevideo URL means
     *    nothing here.
     *
     * There is no re-resolve on a refusal either. [fetch] has one because a
     * googlevideo URL is minted against a session that can be stood down
     * mid-download; a source's URL that stops working has a server behind it
     * with its own reasons, and the caller falling back to YouTube is a better
     * answer than asking again.
     *
     * @param onProgress called as bytes land, and only when the response stated
     *   a length to measure against — an unstated one leaves the progress
     *   indeterminate rather than dividing by zero.
     * @return how many bytes were written.
     */
    suspend fun fetchDirect(
        url: String,
        headers: Map<String, String>,
        sink: OutputStream,
        onProgress: (written: Long, total: Long) -> Unit,
    ): Long = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()

        Http.client.newCall(request).execute().use { response ->
            if (response.code !in 200..299) error("Download failed (HTTP ${response.code})")
            val body = response.body ?: error("Download failed: empty response")
            val total = response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
            val source = body.byteStream()
            val buffer = ByteArray(BUFFER_BYTES)
            var written = 0L

            while (true) {
                coroutineContext.ensureActive()
                val read = source.read(buffer)
                if (read == -1) break
                sink.write(buffer, 0, read)
                written += read
                if (total != null) onProgress(written, total)
            }
            sink.flush()

            // A stated length that didn't all arrive is the one failure the
            // pending-row dance cannot catch on its own: nothing threw, so
            // committing would publish a file that looks whole and stops
            // halfway through the song.
            if (total != null && written < total) {
                error("Download stopped at ${written}B of $total — try again")
            }
            if (written == 0L) error("Download failed: nothing was sent")
            Log.d(TAG, "fetched ${written}B directly")
            written
        }
    }

    /**
     * Verify that a source URL is a file in the container it advertised before
     * it is allowed to become a download route.
     *
     * Some module backends label an HLS master playlist as FLAC. Downloading
     * that text faithfully produces a tiny `.flac` which no player can open;
     * rejecting it here lets the ordinary YouTube fallback take over instead.
     */
    suspend fun isDirectAudioFile(
        url: String,
        headers: Map<String, String>,
        format: StreamFormat,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url)
                .header("Range", "bytes=0-31")
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .build()
            Http.client.newCall(request).execute().use { response ->
                if (response.code !in 200..299) return@use false
                val type = response.header("Content-Type").orEmpty().lowercase()
                if ("mpegurl" in type || "vnd.apple.mpegurl" in type) return@use false
                val head = response.body?.byteStream()?.readNBytes(32) ?: return@use false
                if (head.startsWith("#EXTM3U".toByteArray())) return@use false
                when (format.codec?.lowercase()) {
                    "flac", "x-flac" -> head.startsWith("fLaC".toByteArray())
                    "m4a", "mp4", "alac" -> head.size >= 8 &&
                        head.copyOfRange(4, 8).contentEquals("ftyp".toByteArray())
                    else -> head.isNotEmpty()
                }
            }
        }.onFailure { Log.w(TAG, "could not probe source file: ${it.message}") }.getOrDefault(false)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

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
