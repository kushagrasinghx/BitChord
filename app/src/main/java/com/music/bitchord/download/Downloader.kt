package com.music.bitchord.download

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.Http
import com.music.bitchord.data.innertube.PlayerClient
import com.music.bitchord.data.innertube.StreamResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.OutputStream
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
     *   different rung of the selected audio ladder, and the length check that guards the
     *   resume would then fail a retry that had nothing wrong with it.
     * @param requireM4a whether a re-resolve must remain AAC-in-MP4 for an
     *   exported Music-folder destination.
     * @param onProgress called as bytes land, with the running total and the
     *   full size. Never called with a total of zero.
     * @return how many bytes were written.
     */
    suspend fun fetch(
        videoId: String,
        stream: StreamResolver.Stream,
        maxKbps: Int,
        requireM4a: Boolean,
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
            val length = minOf(CHUNK_BYTES, PlayerClient.rangeBytesFor(url), total - position)

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
                url = StreamResolver.resolveForDownload(videoId, maxKbps, requireM4a).url
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
     * A container a source's file can be kept in, named for what its opening
     * bytes say rather than for what the source called it.
     *
     * The bytes are the only witness worth believing here. Sources label
     * loosely — `aac` for both raw ADTS and AAC-in-MP4, `flac` on an HLS
     * playlist, nothing at all on an extensionless URL — and a download has to
     * name the file before it is written. Filed under the wrong extension, a
     * file either won't open or opens as the wrong thing.
     *
     * AIFF is deliberately absent: the player has no extractor for it, so a
     * saved AIFF would be a download that can never be played back here.
     *
     * @param codec the codec this container settles on its own, or null when
     *   it can carry several and only the source's own word can say which.
     */
    internal enum class Container(val extension: String, val mimeType: String, val codec: String?) {
        FLAC("flac", "audio/flac", "flac"),
        WAV("wav", "audio/x-wav", "wav"),
        MP4("m4a", "audio/mp4", null),
        MP3("mp3", "audio/mpeg", "mp3"),
        ADTS("aac", "audio/aac", "aac"),
        OGG("ogg", "audio/ogg", null),
        WEBM("webm", "audio/webm", null),
    }

    /** What [probe] found at the end of a source's URL. */
    internal sealed interface Probe {
        /** Audio, in [container], [totalBytes] long when the server said. */
        data class File(val container: Container, val totalBytes: Long?) : Probe

        /** An index of the audio rather than the audio — HLS, or DASH when [dash]. */
        data class Manifest(val dash: Boolean) : Probe
    }

    /**
     * Read the opening of a source URL and say what is there, or null when it
     * is nothing a download can keep.
     *
     * Called on every candidate before it is allowed to win, not only on the
     * winner. Some backends label an HLS playlist as FLAC (the reference addon
     * serves one from an extensionless `/dash/{id}`), and fetched as a file
     * that is a tiny `.flac` no player can open, reported as a success. A
     * candidate that cannot be kept also must not win only to be thrown away,
     * because that sends the download to YouTube past a copy that could have
     * been kept.
     *
     * @param statedCodec what the source called it, which only breaks the tie
     *   for an ID3-prefixed file. The bytes decide everything else.
     */
    internal suspend fun probe(
        url: String,
        headers: Map<String, String>,
        statedCodec: String? = null,
    ): Probe? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url)
                .header("Range", "bytes=0-${PROBE_BYTES - 1}")
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .build()
            // Awaited rather than executed: this runs inside the download
            // lookup's race, which has a deadline to keep, and a blocking call
            // would hold that race open past it on a server that stalled.
            Http.client.newCall(request).awaitResponse().use { response ->
                if (response.code !in 200..299) {
                    Log.w(TAG, "probe refused (HTTP ${response.code})")
                    return@use null
                }
                // A 206 states the whole length after the slash; a 200 is a
                // server that ignored the range and is sending all of it.
                val total = if (response.code == 206) {
                    response.header("Content-Range")?.substringAfter('/', "")?.toLongOrNull()
                } else {
                    response.header("Content-Length")?.toLongOrNull()
                }?.takeIf { it > 0 }
                val source = response.body?.source() ?: return@use null
                source.request(PROBE_BYTES.toLong())
                val head = source.buffer.readByteArray(minOf(source.buffer.size, PROBE_BYTES.toLong()))
                sniff(head, response.header("Content-Type").orEmpty(), statedCodec, total)
            }
        }.onFailure {
            if (it is CancellationException) throw it
            Log.w(TAG, "could not probe source file: ${it.message}")
        }.getOrNull()
    }

    /** [Call.execute], but given up on — and the call cancelled — when the coroutine is. */
    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }
        })
    }

    /** [probe]'s judgement, apart from the request so it can be tested on bytes alone. */
    internal fun sniff(head: ByteArray, contentType: String, statedCodec: String?, totalBytes: Long?): Probe? {
        val type = contentType.lowercase()
        // Read byte for byte, so a UTF-8 byte-order mark arrives as its three
        // Latin-1 characters rather than as one.
        val text = String(head, Charsets.ISO_8859_1).removePrefix("ï»¿").trimStart()
        if ("mpegurl" in type || text.startsWith("#EXTM3U")) return Probe.Manifest(dash = false)
        if ("dash+xml" in type || "<MPD" in text) return Probe.Manifest(dash = true)
        return containerOf(head, statedCodec)?.let { Probe.File(it, totalBytes) }
    }

    private fun containerOf(head: ByteArray, statedCodec: String?): Container? {
        fun at(offset: Int, ascii: String) = head.size >= offset + ascii.length &&
            ascii.indices.all { head[offset + it] == ascii[it].code.toByte() }
        return when {
            at(0, "fLaC") -> Container.FLAC
            at(0, "RIFF") && at(8, "WAVE") -> Container.WAV
            at(4, "ftyp") -> Container.MP4
            at(0, "OggS") -> Container.OGG
            head.size >= 4 && head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() &&
                head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte() -> Container.WEBM
            // An ID3 tag can run to megabytes of artwork, so what follows it is
            // usually out of reach. MP3 is what nearly always sits behind one;
            // a source that said AAC gets the benefit of the doubt.
            at(0, "ID3") -> if (statedCodec.equals("aac", ignoreCase = true)) Container.ADTS else Container.MP3
            head.size >= 2 && head[0] == 0xFF.toByte() -> {
                val second = head[1].toInt() and 0xFF
                when {
                    // ADTS: twelve sync bits, then layer bits that are always zero.
                    second and 0xF6 == 0xF0 -> Container.ADTS
                    // MPEG audio: eleven sync bits and a layer that isn't the reserved zero.
                    second and 0xE0 == 0xE0 && second and 0x06 != 0 -> Container.MP3
                    else -> null
                }
            }
            else -> null
        }
    }

    /** Enough to see past an XML prolog to an `<MPD`, and far more than any magic number needs. */
    private const val PROBE_BYTES = 512

    private fun open(url: String, position: Long, length: Long) = Http.client
        .newCall(
            Request.Builder()
                .url(url)
                .header("Range", "bytes=$position-${position + length - 1}")
                .apply {
                    StreamResolver.mediaHeadersFor(url)
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
