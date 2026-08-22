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
 * Embeds title, artist, album and cover art into a track [Downloads] just
 * finished saving, so it reads correctly in a file manager or another
 * player rather than only inside this app, where the filename is otherwise
 * the only thing carrying that.
 *
 * Best-effort throughout, deliberately: the cover fetch is a network call
 * that can fail for reasons that have nothing to do with the download that
 * already succeeded, and [Mp4Tagger] / [WebmTagger] both already fall back to
 * returning their input unchanged on anything they don't recognise. Every
 * step here is caught rather than left to propagate, because a download this
 * runs after has already landed — a tagging failure should cost the tags,
 * not the file.
 */
object MediaTagger {

    private const val TAG = "BitChord"

    /** Long side of the embedded cover — plenty for a lock screen or a car head unit, without ballooning the file. */
    private const val COVER_MAX_SIDE = 1000

    fun embed(context: Context, uri: Uri, track: Song, extension: String) {
        if (extension != "m4a" && extension != "webm") return
        val original = readAll(context, uri) ?: return
        val cover = fetchCover(track)

        val tagged = runCatching {
            when (extension) {
                "m4a" -> Mp4Tagger.tag(
                    original,
                    track.title,
                    track.artist,
                    track.albumName,
                    cover?.bytes,
                    coverIsPng = false,
                )
                else -> WebmTagger.tag(
                    original,
                    track.title,
                    track.artist,
                    track.albumName,
                    cover?.bytes,
                    cover?.mime ?: "image/jpeg",
                )
            }
        }.getOrNull() ?: return

        // Both taggers hand back the same array reference when there was
        // nothing safe to do — cheaper than a byte comparison, and exact
        // where it matters: it means "don't touch the file that just finished
        // downloading" rather than "these bytes happen to be equal".
        if (tagged === original) return
        writeAll(context, uri, tagged)
    }

    private class Cover(val bytes: ByteArray, val mime: String)

    private fun fetchCover(track: Song): Cover? {
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
                Cover(out.toByteArray(), "image/jpeg")
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
