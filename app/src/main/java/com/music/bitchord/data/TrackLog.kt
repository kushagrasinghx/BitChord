package com.music.bitchord.data

import android.os.Build
import android.util.Log
import com.music.bitchord.BuildConfig
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.SourceResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The app's own record of how each track came to be playing, as text you can
 * paste somewhere.
 *
 * Diagnosing why a track played from the wrong source, at the wrong bitrate,
 * or not at all has meant plugging the phone in and reading `adb logcat` — and
 * the answer is usually in a stretch lasting a few seconds that has already
 * scrolled past by the time anyone notices something sounded wrong. This keeps
 * that stretch.
 *
 * ### Why not read logcat
 *
 * The obvious implementation shells out to `logcat`, and it works. But from
 * Android 13 an app that does so trips a system consent dialog — *"Allow
 * BitChord to access all device logs?"* — which appears whenever the process
 * happens to spawn, asks for far more than this needs, and puts every other
 * app's output within reach of a paste made from a music player. None of that
 * is a reasonable price for a debug button.
 *
 * So the lines are kept here on the way past instead. Nothing is read back
 * from the system, no permission is involved, no dialog can appear, and what
 * ends up on the clipboard is only ever what this app itself wrote.
 *
 * ### What gets kept
 *
 * Only the paths that decide how a track plays: the resolver, the module
 * sandbox, the source ladder, the cache and the player. Deliberately not the
 * feeds, the artwork, the lyrics or the library — a paste that includes
 * everything is one nobody reads to the end of, and none of it has ever been
 * the answer to "why did this song sound wrong".
 *
 * Call [d], [w] and [e] exactly where `Log.d`/`w`/`e` would go; they forward
 * to logcat as well, so `adb logcat -s BitChord` is unchanged.
 */
object TrackLog {

    // ── Writing ─────────────────────────────────────────────────────────────

    // logcat is only worth writing to in a debug build — nothing in prod ever
    // reads it (see the class doc), so a release build skips straight to
    // record(), which is what Copy Log actually depends on.

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
        record('D', message)
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.i(tag, message)
        record('I', message)
    }

    fun w(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.w(tag, message)
        record('W', message)
    }

    fun w(tag: String, message: String, error: Throwable) {
        if (BuildConfig.DEBUG) Log.w(tag, message, error)
        record('W', "$message\n${error.stackTraceToString()}")
    }

    fun e(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.e(tag, message)
        record('E', message)
    }

    fun e(tag: String, message: String, error: Throwable) {
        if (BuildConfig.DEBUG) Log.e(tag, message, error)
        record('E', "$message\n${error.stackTraceToString()}")
    }

    private class Line(val at: Long, val level: Char, val text: String)

    private val lines = ArrayDeque<Line>()

    /** Total characters held, so the buffer is bounded by size rather than by count. */
    private var held = 0

    /**
     * Bounded by bytes rather than by line count: one `callExport result` line
     * carrying a search response is worth several hundred ordinary lines, and a
     * limit that counts them the same either wastes memory or throws away the
     * history that matters.
     */
    private fun record(level: Char, message: String) {
        val text = if (message.length > MAX_LINE_CHARS) {
            message.take(MAX_LINE_CHARS) + "…(${message.length - MAX_LINE_CHARS} more)"
        } else {
            message
        }
        synchronized(lines) {
            lines.addLast(Line(System.currentTimeMillis(), level, text))
            held += text.length
            while (held > MAX_HELD_CHARS && lines.isNotEmpty()) {
                held -= lines.removeFirst().text.length
            }
        }
    }

    // ── Reading ─────────────────────────────────────────────────────────────

    /**
     * Wall-clock times at which each track became the current one.
     *
     * A track's story starts when the queue reached it, not when sound finally
     * came out: the resolve that decides everything runs before the player
     * announces the change.
     */
    private val startedAt = ConcurrentHashMap<String, Long>()

    fun onTrackStarted(videoId: String) {
        if (startedAt.size >= MAX_REMEMBERED) startedAt.clear()
        startedAt[videoId] = System.currentTimeMillis()
    }

    /**
     * The log for [song], from a little before its track was selected up to
     * now.
     *
     * Falls back to everything held when the start isn't known, which is the
     * case for the track a cold start resumes on. That is more log rather than
     * less, and more is the right way to be wrong here.
     */
    suspend fun forTrack(song: Song, stats: NerdStats.Snapshot?): String = withContext(Dispatchers.Default) {
        val from = startedAt[song.videoId]?.minus(LEAD_IN_MS)
        val window = synchronized(lines) {
            if (from == null) lines.toList() else lines.filter { it.at >= from }
        }
        header(song, stats, from, window.size) + "\n" +
            window.joinToString("\n") { "${CLOCK.format(Date(it.at))} ${it.level} ${it.text}" } +
            "\n"
    }

    // ── The part that isn't the log ─────────────────────────────────────────

    /**
     * What the lines alone can't say: which build produced them, on what, and
     * what the player believed it was playing when the log was taken.
     */
    private fun header(song: Song, stats: NerdStats.Snapshot?, from: Long?, count: Int) = buildString {
        appendLine("BitChord log — ${song.title} — ${song.artist}")
        appendLine("id=${song.videoId} duration=${song.durationText ?: "?"} album=${song.albumName ?: "?"}")
        appendLine("playing: ${stats.describe()}")
        appendLine(
            "sources: substitution=${SourceResolver.canSubstituteForYouTube()} " +
                "request=${SourceResolver.requestForNow()}",
        )
        appendLine(
            "window: ${from?.let { CLOCK.format(Date(it)) } ?: "everything held"} → " +
                "${CLOCK.format(Date())} ($count lines)",
        )
        appendLine("build: ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})")
        appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
    }

    private fun NerdStats.Snapshot?.describe(): String {
        if (this == null) return "nothing reported"
        val measured = listOfNotNull(
            mimeType,
            bitDepth?.let { "$it-bit" },
            bitrateKbps?.let { "$it kbps" },
            sampleRateHz?.let { "$it Hz" },
            channels?.let { "${it}ch" },
        ).joinToString(" · ").ifEmpty { "nothing reported" }
        val promised = claimed?.summary?.let { " (source said: $it)" }.orEmpty()
        val tier = when {
            isHiRes -> " [Hi-Res Lossless]"
            isLossless -> " [Lossless]"
            isHiQuality -> " [Hi-Quality]"
            else -> ""
        }
        return measured + promised + tier
    }

    private val CLOCK = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /**
     * How far back of the track's selection to reach.
     *
     * The resolve runs on the loader thread before the player reports the item
     * as current, and on a cold module search that head start is the whole
     * story — cut it off and the paste starts after the decision it is meant
     * to explain.
     */
    private const val LEAD_IN_MS = 20_000L

    /** Roughly the last few tracks' worth, and small enough to hold without thinking about it. */
    private const val MAX_HELD_CHARS = 512_000

    /** Enough for a stack trace or a search response's opening; not a whole catalogue page. */
    private const val MAX_LINE_CHARS = 2_000

    private const val MAX_REMEMBERED = 32
}
