package com.music.bitchord.data.sources

import com.music.bitchord.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * How big a stream's file is, without reading any of it — for catching an
 * addon that answers a full-length row with a short clip.
 *
 * The case this exists for: an addon that cannot serve a track in full hands
 * back its 30-second preview instead, under the same row, which still claims
 * the full runtime. Nothing in the catalogue answer can tell those apart. The
 * file's size can, because a file has to be big enough to hold the audio it
 * claims to.
 *
 * Only Direct Source Stream asks — see [SourceResolver.directStream]. Every
 * other path that puts an addon's copy under the listener auditions it first,
 * and the audition reads the decoded runtime, which is the better evidence and
 * makes this unnecessary there.
 */
internal object StreamLengthProbe {

    /**
     * The file's total size in bytes, or null when the server will not say.
     *
     * One ranged GET for a single byte, which every server that streams audio
     * has to support for seeking, and which answers the total in
     * `Content-Range`. A server that ignores the range answers 200 with the
     * whole file's `Content-Length`; the body is closed unread.
     */
    suspend fun bytesOf(stream: SourceStream): Long? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(stream.url)
                .apply { stream.headers.forEach { (name, value) -> header(name, value) } }
                .header("Range", "bytes=0-0")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    206 -> response.header("Content-Range")
                        ?.substringAfterLast('/')
                        ?.trim()
                        ?.toLongOrNull()
                    200 -> response.header("Content-Length")?.toLongOrNull()
                    else -> null
                }?.takeIf { it > 0 }
            }
        } catch (e: Exception) {
            // Unmeasured is not short. Cancellation included: the caller's
            // own scope still sees it at its next suspension point.
            null
        }
    }

    /**
     * Whether [bytes] of [format] is too small to hold [expectedSec] of audio,
     * give or take [slackSec].
     *
     * Asked of the most audio the file *could* hold, not the most likely: the
     * bytes divided by the lowest bitrate this kind of stream plausibly runs
     * at. A file that falls short even then cannot be the recording. Leaning
     * this way means a real copy is never refused for an optimistic bitrate
     * claim, and container overhead or embedded artwork only ever makes a file
     * look longer than it is.
     *
     * A 30-second 128 kbps preview is 480 kB, room for at most a minute at
     * the lossy floor, against a three-minute song — refused. The same song
     * in full at 96 kbps is 2.1 MB, room for well over four — kept. A short
     * song with a high-bitrate preview can slip past; the decoded-length check
     * in playback catches it a moment later.
     */
    fun tooShort(bytes: Long, format: StreamFormat, expectedSec: Int, slackSec: Int): Boolean {
        if (bytes <= 0 || expectedSec <= 0) return false
        val floorKbps = if (format.isLossless == true) {
            LOSSLESS_FLOOR_KBPS
        } else {
            minOf(format.kbps?.takeIf { it > 0 } ?: LOSSY_FLOOR_KBPS, LOSSY_FLOOR_KBPS)
        }
        val mostSec = bytes * 8.0 / (floorKbps * 1000.0)
        return mostSec < expectedSec - slackSec
    }

    /** Below what any 16-bit stereo FLAC of music runs at; quiet or mono material sits well above it. */
    private const val LOSSLESS_FLOOR_KBPS = 200

    /** Below every lossy tier an addon offers for music, so only a file that is genuinely short falls under it. */
    private const val LOSSY_FLOOR_KBPS = 64

    /** A size question, so it gets a size question's time: it runs before the first note. */
    private val client: OkHttpClient by lazy {
        Http.client.newBuilder()
            .callTimeout(PROBE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .build()
    }

    private const val PROBE_TIMEOUT_SEC = 4L
}
