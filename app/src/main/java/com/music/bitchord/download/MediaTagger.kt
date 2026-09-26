package com.music.bitchord.download

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.Http
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlin.math.max

/**
 * Embeds title, artist, album, lyrics and cover art into a track [Downloads]
 * just finished saving, so it reads correctly in a file manager or another
 * player rather than only inside this app, where the filename is otherwise
 * the only thing carrying that.
 *
 * Best-effort throughout, deliberately: the cover fetch is a network call
 * that can fail for reasons that have nothing to do with the download that
 * already succeeded, and [Mp4Tagger], [WebmTagger] and [FlacTagger] all fall
 * back to returning their input unchanged on anything they don't recognise.
 * Every step here is caught rather than left to propagate, because a download
 * this runs after has already landed — a tagging failure should cost the tags,
 * not the file.
 *
 * The lyrics are the one thing not fetched here. They come in as text from
 * [LyricsTag], started by [Downloads] early enough to overlap the transfer,
 * because that lookup races four services and is the one piece of this worth
 * not paying for in wall-clock time after the last byte has landed.
 */
object MediaTagger {

    private const val TAG = "BitChord"

    /** Long side of the embedded cover — plenty for a lock screen or a car head unit, without ballooning the file. */
    private const val COVER_MAX_SIDE = 1000

    /**
     * How much of a FLAC has to be read to be sure the whole metadata chain is
     * in hand — see [tagFlacStreaming].
     *
     * Enormously more than any real file needs: `STREAMINFO` is 34 bytes, a
     * `SEEKTABLE` runs to tens of kilobytes on a long track, and the only block
     * that could be large is an existing `PICTURE`, which this replaces anyway.
     * Sized this way because the cost of guessing short is a fall back to the
     * in-memory path, and the cost of this number being generous is a read that
     * stops at the end of a small file regardless.
     */
    private const val FLAC_HEAD_BYTES = 16 * 1024 * 1024

    private const val COPY_BUFFER = 64 * 1024

    /**
     * The containers there is a tagger for.
     *
     * A download can land as something else — `.wav` from a source that serves
     * it, see [DownloadStore.storable] — and that file keeps the tags its
     * filename carries and nothing more. Worth having no tagger for rather than
     * a half-written one: a WAV's metadata lives in RIFF chunks that a good
     * number of players ignore outright.
     */
    private val TAGGABLE = setOf("m4a", "webm", "flac")

    /**
     * Whether a file of [extension] gets tags at all.
     *
     * Asked by [Downloads] before it starts fetching anything that exists only
     * to be tagged, so a `.wav` download doesn't spend four lyric lookups on a
     * field it has nowhere to put.
     */
    fun carriesTags(extension: String): Boolean = extension in TAGGABLE

    /** @param lyrics what [LyricsTag] found, or null when there are none to write. */
    internal class Artwork(val bytes: ByteArray, val mime: String)

    /** Fetch artwork before publication so batch downloads can overlap it with audio. */
    internal fun artworkFor(context: Context, track: Song): Artwork? {
        val thumbnailUrl = track.thumbnailUrl
        if (thumbnailUrl.isNullOrBlank()) {
            // Said out loud rather than returned as a quiet null. An album's own
            // track rows carry no artwork — the release is billed once in the
            // header — so a row that gets this far without having the release's
            // cover filled in on the way (`MainViewModel.withArtwork`, and again
            // at the tap in `MainActivity.startDownload`) saves a file with no
            // cover in it, permanently and without complaint. This line is how
            // that gets noticed.
            Log.d(TAG, "no artwork url for ${track.videoId}; saving it without a cover")
            return null
        }
        cachedArtwork(context, thumbnailUrl)?.let { return it }
        return fetchCover(track)
    }

    /**
     * One tagging pass at a time, across every download worker.
     *
     * Not about correctness — the four workers write four different files and
     * never touch each other's. It is about the heap. [embed] reads the whole
     * finished track into a `ByteArray`, hands it to a tagger that builds a
     * second copy of it in a [ByteArrayOutputStream], and takes a third on
     * `toByteArray()`; a 40MB FLAC is therefore ~120MB in flight for the length
     * of one rewrite. Four of those at once is half a gigabyte of byte arrays
     * on a heap this app already runs near the ceiling of, and the way it fails
     * is the reason this is worth a lock: the `runCatching` below catches
     * [Throwable], so an [OutOfMemoryError] thrown mid-rewrite was swallowed
     * whole and the download completed — untagged, with no cover and no lyrics,
     * and nothing in the log to say why. Which is exactly what "sometimes it
     * saves the lyrics and sometimes it doesn't" looks like from outside.
     *
     * Serialising costs nothing that matters. Tagging is a memcpy against a
     * transfer that just spent seconds on the network, and the workers are
     * overlapping *lookups*, not rewrites.
     */
    private val taggingLock = Mutex()

    /** Serialises creation of the shared artwork sidecar for album batches. */
    private val artworkFileLock = Any()

    /**
     * Keep a local copy of artwork independently of the audio container.
     *
     * Android's metadata readers do not consistently expose Matroska/WebM
     * attachments, even when the cover is valid and other players can read it.
     * The app therefore must not depend on extracting the attachment again to
     * draw a downloaded Opus track. The same sidecar also gives downloaded
     * album and playlist records an image that remains available offline.
     */
    internal fun persistArtwork(context: Context, sourceUrl: String?, artwork: Artwork?): String? {
        if (sourceUrl.isNullOrBlank() || artwork == null || artwork.bytes.isEmpty()) return null
        return runCatching {
            val folder = File(context.filesDir, "download-artwork")
            val target = artworkFile(context, sourceUrl)
            val name = target.name
            synchronized(artworkFileLock) {
                if (!target.exists()) {
                    if (!folder.exists() && !folder.mkdirs()) error("Could not create artwork folder")
                    val temp = File(folder, ".$name-${System.nanoTime()}.part")
                    try {
                        temp.writeBytes(artwork.bytes)
                        if (!temp.renameTo(target) && !target.exists()) {
                            temp.copyTo(target, overwrite = false)
                        }
                    } finally {
                        temp.delete()
                    }
                }
            }
            Uri.fromFile(target).toString()
        }.onFailure {
            Log.w(TAG, "could not save artwork sidecar: ${it.message}")
        }.getOrNull()
    }

    /** Fetch and persist a collection cover before its remote URL goes stale. */
    internal fun cacheArtwork(context: Context, sourceUrl: String?): String? {
        if (sourceUrl.isNullOrBlank()) return null
        artworkFile(context, sourceUrl).takeIf { it.isFile }?.let { return Uri.fromFile(it).toString() }
        val artwork = fetchCover(sourceUrl.artworkAt(1200) ?: sourceUrl, "collection") ?: return null
        return persistArtwork(context, sourceUrl, artwork)
    }

    private fun cachedArtwork(context: Context, sourceUrl: String): Artwork? = runCatching {
        artworkFile(context, sourceUrl).takeIf { it.isFile }
            ?.readBytes()
            ?.takeIf { it.isNotEmpty() }
            ?.let { Artwork(it, "image/jpeg") }
    }.getOrNull()

    private fun artworkFile(context: Context, sourceUrl: String): File {
        val name = MessageDigest.getInstance("SHA-256")
            .digest(sourceUrl.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) } + ".jpg"
        return File(File(context.filesDir, "download-artwork"), name)
    }

    internal suspend fun embed(
        context: Context,
        uri: Uri,
        track: Song,
        extension: String,
        lyrics: LyricsTag.Embeddable? = null,
        artwork: Artwork? = null,
    ) {
        if (!carriesTags(extension)) return
        taggingLock.withLock { embedNow(context, uri, track, extension, lyrics, artwork) }
    }

    private fun embedNow(
        context: Context,
        uri: Uri,
        track: Song,
        extension: String,
        lyrics: LyricsTag.Embeddable?,
        artwork: Artwork?,
    ) {
        // The portable field and this app's own. Split here rather than inside
        // each tagger so all three agree on which string goes where.
        val plain = lyrics?.plain
        val words = lyrics?.enhanced

        // FLAC is the format worth not doing in memory, and the only one that
        // can be done any other way — see [tagFlacStreaming].
        if (extension == "flac" &&
            tagFlacStreaming(context, uri, track, plain, words, artwork)
        ) {
            return
        }

        val original = readAll(context, uri) ?: return
        val tagged = runCatching {
            when (extension) {
                "m4a" -> Mp4Tagger.tag(
                    original,
                    track.title,
                    track.artist,
                    track.albumName,
                    plain,
                    artwork?.bytes,
                    coverIsPng = false,
                    wordLyrics = words,
                )
                "flac" -> FlacTagger.tag(
                    original,
                    track.title,
                    track.artist,
                    track.albumName,
                    plain,
                    artwork?.bytes,
                    artwork?.mime ?: "image/jpeg",
                    wordLyrics = words,
                )
                else -> WebmTagger.tag(
                    original,
                    track.title,
                    track.artist,
                    track.albumName,
                    plain,
                    artwork?.bytes,
                    artwork?.mime ?: "image/jpeg",
                    wordLyrics = words,
                )
            }
        }.onFailure {
            // Named, because the two things that land here are the two things
            // the user actually notices going missing. An OutOfMemoryError on a
            // large lossless file is the common one and reads as a track that
            // simply saved without its cover or its words.
            Log.w(TAG, "could not build tags for ${track.videoId} (${original.size}B $extension): $it")
        }.getOrNull() ?: return

        // Every tagger hands back the same array reference when there was
        // nothing safe to do — cheaper than a byte comparison, and exact
        // where it matters: it means "don't touch the file that just finished
        // downloading" rather than "these bytes happen to be equal".
        if (tagged === original) return
        writeAll(context, uri, tagged)
    }

    /**
     * Tag a FLAC without ever holding it in memory.
     *
     * The in-memory route below is the general one because MP4 and WebM need it
     * — an `moov` that grows shifts every `stco` offset after it, and a
     * `Segment` size has to be rewritten in place — so both taggers have to be
     * able to reach any byte of the file. FLAC has no such constraint: the
     * metadata lives in a block chain at the front, nothing addresses itself by
     * an absolute file offset, and the frames are copied through untouched. So
     * the only part that has to be *read* is the front of the file, and the rest
     * is a stream copy.
     *
     * That distinction is worth the extra path because FLAC is also the format
     * this matters for. It is the one a download at the Lossless rung produces,
     * and a 40MB track through [readAll] plus a tagger's two working copies is
     * ~120MB of byte arrays for a job whose actual output is a few kilobytes of
     * text and a cover. On a hi-res file it is enough to fail outright, and the
     * way it failed was invisible: [OutOfMemoryError] is a [Throwable], the
     * `runCatching` below catches those, and the download finished untagged with
     * nothing said. Here the ceiling is the metadata region plus one 64kB
     * buffer, whatever the track's length.
     *
     * @return true when the file has been dealt with — rewritten, or found to
     *   have nothing to write. False asks the caller to fall back, which happens
     *   only for a metadata region larger than [FLAC_HEAD_BYTES] or an I/O
     *   failure, and is safe at any point: the original is untouched until the
     *   finished copy replaces it in one move.
     */
    private fun tagFlacStreaming(
        context: Context,
        uri: Uri,
        track: Song,
        lyrics: String?,
        wordLyrics: String?,
        artwork: Artwork?,
    ): Boolean {
        val head = readPrefix(context, uri, FLAC_HEAD_BYTES) ?: return false
        // Short of the cap means the read hit the end of the file, so this *is*
        // the whole file and a refusal below is a final answer rather than a
        // prefix that was cut too fine.
        val wholeFile = head.size < FLAC_HEAD_BYTES
        val rewrite = FlacTagger.header(
            head,
            track.title,
            track.artist,
            track.albumName,
            lyrics,
            artwork?.bytes,
            artwork?.mime ?: "image/jpeg",
            wordLyrics = wordLyrics,
        ) ?: return wholeFile

        val temp = File(context.cacheDir, "tagging-${track.videoId.hashCode()}-${System.nanoTime()}.flac")
        return runCatching {
            temp.outputStream().buffered().use { sink ->
                sink.write(rewrite.metadata)
                openInput(context, uri)?.use { source ->
                    source.skipFully(rewrite.audioStart.toLong())
                    source.copyTo(sink, COPY_BUFFER)
                } ?: error("could not reopen $uri")
            }
            replaceWith(context, uri, temp)
            true
        }.onFailure {
            Log.w(TAG, "streaming flac tag failed for ${track.videoId}: ${it.message}")
            temp.delete()
        }.getOrDefault(false)
    }

    /** Move [temp] over whatever [uri] names, leaving nothing behind either way. */
    private fun replaceWith(context: Context, uri: Uri, temp: File) {
        if (uri.scheme == "file") {
            val target = File(requireNotNull(uri.path))
            // Both live under the app's own data directory, so this is a rename
            // within one filesystem and the file is never half-replaced.
            if (temp.renameTo(target)) return
        }
        temp.inputStream().use { source ->
            val sink = if (uri.scheme == "file") {
                File(requireNotNull(uri.path)).outputStream()
            } else {
                context.contentResolver.openOutputStream(uri, "wt")
                    ?: error("could not open $uri for writing")
            }
            sink.use { source.copyTo(it, COPY_BUFFER) }
        }
        temp.delete()
    }

    /** At most [max] bytes from the front of [uri], or null if it can't be read. */
    private fun readPrefix(context: Context, uri: Uri, max: Int): ByteArray? = runCatching {
        openInput(context, uri)?.use { source ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(COPY_BUFFER)
            while (out.size() < max) {
                val read = source.read(buffer, 0, minOf(buffer.size, max - out.size()))
                if (read == -1) break
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }.onFailure { Log.w(TAG, "could not read the start of $uri: ${it.message}") }.getOrNull()

    private fun openInput(context: Context, uri: Uri): InputStream? =
        if (uri.scheme == "file") {
            File(requireNotNull(uri.path)).inputStream()
        } else {
            context.contentResolver.openInputStream(uri)
        }

    /**
     * [InputStream.skip] is allowed to skip fewer bytes than asked for and does,
     * on the wrapped streams a `content://` uri hands back. Landing one byte
     * short here would splice the tail of a frame header onto the new metadata
     * and produce a file that is the right length and unplayable.
     */
    private fun InputStream.skipFully(count: Long) {
        var left = count
        while (left > 0) {
            val skipped = skip(left)
            if (skipped > 0) {
                left -= skipped
                continue
            }
            // A stream that reports zero is not necessarily finished, so fall
            // back to reading the bytes away before believing it is.
            if (read() == -1) error("file ended ${left}B before its audio frames")
            left--
        }
    }

    private fun fetchCover(track: Song): Artwork? {
        val url = track.artworkAt(1200) ?: return null
        return fetchCover(url, track.videoId)
    }

    private fun fetchCover(url: String, logId: String): Artwork? {
        return runCatching {
            val request = okhttp3.Request.Builder().url(url).build()
            Http.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val raw = response.body?.bytes() ?: return@runCatching null
                val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return@runCatching null
                val scaled = downscale(bitmap, COVER_MAX_SIDE)
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
                Artwork(out.toByteArray(), "image/jpeg")
            }
        }.onFailure { Log.d(TAG, "no cover saved for $logId: ${it.message}") }.getOrNull()
    }

    private fun downscale(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val scale = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun readAll(context: Context, uri: Uri): ByteArray? = runCatching {
        if (uri.scheme == "file") {
            File(requireNotNull(uri.path)).readBytes()
        } else {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
    }.onFailure { Log.w(TAG, "could not read $uri for tagging: ${it.message}") }.getOrNull()

    private fun writeAll(context: Context, uri: Uri, bytes: ByteArray) {
        runCatching {
            if (uri.scheme == "file") {
                File(requireNotNull(uri.path)).writeBytes(bytes)
            } else {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            }
        }.onFailure { Log.w(TAG, "could not write tags to $uri: ${it.message}") }
    }
}
