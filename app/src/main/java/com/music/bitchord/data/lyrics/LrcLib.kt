package com.music.bitchord.data.lyrics

import com.music.bitchord.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import kotlin.math.abs

/**
 * Lyrics from LRCLIB — a free, key-less, community lyrics database.
 *
 * Two calls: an exact `get` keyed on artist + title + duration, and a fuzzy
 * `search` when that misses (YouTube Music titles carry "(From ...)" and
 * "| Official Video" noise that the exact endpoint won't match). Results are
 * line-synced `[mm:ss.xx]` LRC — LRCLIB has no word-level timing, so
 * highlighting is per line.
 */
object LrcLib {

    private const val BASE = "https://lrclib.net/api"
    private const val AGENT = "BitChord (https://github.com/bitchord)"

    private val json = Json { ignoreUnknownKeys = true }

    /** Synced lyrics artifact for a track, or null when nothing usable is published. */
    suspend fun artifact(title: String, artist: String, durationMs: Long): LyricsArtifact? =
        withContext(Dispatchers.IO) {
            val cleanTitle = title.clean()
            val cleanArtist = artist.clean()
            val seconds = (durationMs / 1000).toInt()

            val exact = runCatching { exactMatch(cleanTitle, cleanArtist, seconds) }.getOrNull()
            val synced = exact ?: runCatching { bestSearchHit(cleanTitle, cleanArtist, seconds) }
                .getOrNull()
            val content = synced ?: return@withContext null
            val lines = parseLrc(content).takeIf { it.isNotEmpty() } ?: return@withContext null
            LyricsArtifact(
                source = LyricsSource.LRCLIB,
                format = LyricsArtifactFormat.LRC,
                content = content,
                lines = lines,
            )
        }

    /** Synced lyrics for a track, or null when nothing usable is published. */
    suspend fun lyrics(title: String, artist: String, durationMs: Long): List<LyricLine>? =
        artifact(title, artist, durationMs)?.lines

    private fun exactMatch(title: String, artist: String, seconds: Int): String? {
        val url = "$BASE/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .addQueryParameter("duration", seconds.toString())
            .build()
        val body = get(url.toString()) ?: return null
        return (json.parseToJsonElement(body) as? JsonObject)
            ?.get("syncedLyrics")?.jsonPrimitive?.contentOrNull
    }

    /**
     * Fuzzy fallback. Prefers whichever hit is closest in length to what we're
     * actually playing — same song, different edit, would drift otherwise.
     */
    private fun bestSearchHit(title: String, artist: String, seconds: Int): String? {
        val url = "$BASE/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .build()
        val body = get(url.toString()) ?: return null
        val hits = json.parseToJsonElement(body) as? JsonArray ?: return null
        return hits.mapNotNull { it as? JsonObject }
            .filter { it["syncedLyrics"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true }
            .minByOrNull {
                val d = it["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                abs(d - seconds)
            }
            ?.get("syncedLyrics")?.jsonPrimitive?.contentOrNull
    }

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", AGENT).build()
        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    /**
     * `[mm:ss.xx] words`. Metadata tags carry no timestamp and fall out on
     * their own. A stamp with no words marks an instrumental break; those are
     * kept, but only where the silence is long enough to be worth showing —
     * otherwise the line would blink out between two sung phrases. A stamp
     * with nothing after it closes the final line, so it always survives.
     */
    internal fun parseLrc(lrc: String): List<LyricLine> {
        val all = lrc.lineSequence().mapNotNull { line ->
            val match = STAMP.find(line) ?: return@mapNotNull null
            val (minutes, seconds, fraction) = match.destructured
            // Two digits mean centiseconds, three mean milliseconds.
            val fractionMs = when (fraction.length) {
                2 -> fraction.toLong() * 10
                3 -> fraction.toLong()
                else -> 0L
            }
            val body = line.substring(match.range.last + 1)
            LyricLine(
                timeMs = minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + fractionMs,
                // Stripped rather than rebuilt from the runs below: the spacing
                // and punctuation between two words belong to the line, and
                // re-joining the words with single spaces would quietly rewrite
                // a line that never had them.
                text = body.replace(WORD_STAMP, "").trim(),
                words = parseWordRuns(body),
            )
        }.sortedBy { it.timeMs }.toList()

        val kept = all.filterIndexed { index, line ->
            if (!line.isGap) return@filterIndexed true
            // A trailing stamp closes off the last line — that's the outro.
            val next = all.getOrNull(index + 1) ?: return@filterIndexed true
            next.timeMs - line.timeMs >= MIN_GAP_MS
        }

        // Nothing stands for the intro — LRC files start at the first sung
        // word — so give the run-up its own break when it's long enough.
        val first = kept.firstOrNull() ?: return kept
        return if (!first.isGap && first.timeMs >= MIN_GAP_MS) {
            listOf(LyricLine(0L, "")) + kept
        } else {
            kept
        }
    }

    /**
     * YouTube Music titles are noisy — "(From "Raees")", "| Official Video",
     * "(Lyrical)" — and LRCLIB matches on the plain song name.
     */
    private fun String.clean(): String = this
        .replace(NOISE, " ")
        .substringBefore(" | ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { this }

    /**
     * The `<mm:ss.xx>` runs of an "enhanced" A2 line, as words.
     *
     * Each run ends where the next one starts, which is why a line written by
     * [toEnhancedLrc] closes with a bare stamp: that last one names no word, it
     * just states where the previous one stopped. A run with no text is
     * therefore a terminator rather than a word, here and in the files other
     * A2 writers produce.
     *
     * Empty for a plain line, which is what keeps [LyricLine.isWordSynced]
     * honest — a line-synced source stays line-synced through this.
     */
    private fun parseWordRuns(body: String): List<LyricWord> {
        val marks = WORD_STAMP.findAll(body).toList()
        if (marks.isEmpty()) return emptyList()
        val runs = marks.mapIndexed { index, mark ->
            val until = marks.getOrNull(index + 1)?.range?.first ?: body.length
            msOf(mark) to body.substring(mark.range.last + 1, until)
        }
        return runs.mapIndexedNotNull { index, (startMs, text) ->
            if (text.isBlank()) return@mapIndexedNotNull null
            // The next run's stamp is this word's end — including when that run
            // is the closing terminator, which is the only thing that gives the
            // last word of a line an end at all.
            val endMs = runs.getOrNull(index + 1)?.first ?: startMs
            LyricWord(startMs = startMs, endMs = maxOf(endMs, startMs), text = text.trim())
        }
    }

    private fun msOf(mark: MatchResult): Long {
        val (minutes, seconds, fraction) = mark.destructured
        val fractionMs = when (fraction.length) {
            2 -> fraction.toLong() * 10
            3 -> fraction.toLong()
            else -> 0L
        }
        return minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + fractionMs
    }

    private val STAMP = Regex("""\[(\d{1,2}):(\d{2})[.:](\d{2,3})]""")
    private val WORD_STAMP = Regex("""<(\d{1,3}):(\d{2})[.:](\d{2,3})>""")
    private val NOISE = Regex(
        """\((?:from|feat\.?|official|lyrical|video|audio|remix)[^)]*\)|\[[^]]*]|""" +
            """\b(?:official (?:video|audio|music video)|lyrical|full song|4k video)\b""",
        RegexOption.IGNORE_CASE,
    )
}
