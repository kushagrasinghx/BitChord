package com.music.bitchord.download

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.Http
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import java.io.ByteArrayOutputStream
import java.io.File
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
    internal fun artworkFor(track: Song): Artwork? = fetchCover(track)

    internal fun embed(
        context: Context,
        uri: Uri,
        track: Song,
        extension: String,
        lyrics: LyricsTag.Embeddable? = null,
        artwork: Artwork? = null,
    ) {
        if (!carriesTags(extension)) return
        val original = readAll(context, uri) ?: return
        // The portable field and this app's own. Split here rather than inside
        // each tagger so all three agree on which string goes where.
        val plain = lyrics?.plain
        val words = lyrics?.enhanced

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
        }.getOrNull() ?: return

        // Every tagger hands back the same array reference when there was
        // nothing safe to do — cheaper than a byte comparison, and exact
        // where it matters: it means "don't touch the file that just finished
        // downloading" rather than "these bytes happen to be equal".
        if (tagged === original) return
        writeAll(context, uri, tagged)
    }

    private fun fetchCover(track: Song): Artwork? {
        val url = track.artworkAt(1200) ?: return null
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
        }.onFailure { Log.d(TAG, "no cover embedded for ${track.videoId}: ${it.message}") }.getOrNull()
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
