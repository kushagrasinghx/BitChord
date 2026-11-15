package com.music.bitchord.wear.lyrics

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * One syllable with millisecond timing and the character range it covers in [SyncedLine.text]
 * ([charStart] inclusive, [charEnd] exclusive).
 */
data class SyncedSyllable(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val charStart: Int,
    val charEnd: Int,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(1L)

    internal fun toJson(): JSONObject = JSONObject()
        .put("t", text).put("s", startMs).put("e", endMs)
        .put("a", charStart).put("b", charEnd)

    internal companion object {
        fun fromJson(o: JSONObject) = SyncedSyllable(
            text = o.getString("t"),
            startMs = o.getLong("s"),
            endMs = o.getLong("e"),
            charStart = o.getInt("a"),
            charEnd = o.getInt("b"),
        )
    }
}

data class SyncedLine(
    val index: Int,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val syllables: List<SyncedSyllable>,
    val isChorus: Boolean,
    /** An instrumental stretch (no text): drawn as the three dots, [startMs]..[endMs]. */
    val isGap: Boolean = false,
    /** Index of this line in the list the phone got from its lyrics source; -1 for an invented gap. */
    val sourceIndex: Int = -1,
    /** The answering vocal sung under this line, if any: shown small, on its own clock. */
    val bgText: String? = null,
    val bgStartMs: Long = 0L,
    val bgEndMs: Long = 0L,
) {
    /** Index of the last syllable whose start <= [positionMs], or -1 if none has started. */
    fun syllableIndexAt(positionMs: Long): Int {
        var lo = 0
        var hi = syllables.lastIndex
        var result = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (syllables[mid].startMs <= positionMs) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }

    internal fun toJson(): JSONObject = JSONObject()
        .put("i", index).put("x", text).put("s", startMs).put("e", endMs)
        .put("c", isChorus)
        .put("g", isGap)
        .put("o", sourceIndex)
        .put("y", JSONArray().also { arr -> syllables.forEach { arr.put(it.toJson()) } })
        .also {
            if (bgText != null) it.put("bt", bgText).put("bs", bgStartMs).put("be", bgEndMs)
        }

    internal companion object {
        fun fromJson(o: JSONObject): SyncedLine {
            val arr = o.getJSONArray("y")
            return SyncedLine(
                index = o.getInt("i"),
                text = o.getString("x"),
                startMs = o.getLong("s"),
                endMs = o.getLong("e"),
                syllables = List(arr.length()) { SyncedSyllable.fromJson(arr.getJSONObject(it)) },
                isChorus = o.optBoolean("c", false),
                isGap = o.optBoolean("g", false),
                sourceIndex = o.optInt("o", -1),
                bgText = o.optString("bt", "").ifEmpty { null },
                bgStartMs = o.optLong("bs", 0L),
                bgEndMs = o.optLong("be", 0L),
            )
        }
    }
}

/** Plain, source-agnostic input for [SyncedLyricsPayload.build]. Map `LyricLine` onto this. */
data class RawSyllable(val text: String, val startMs: Long, val endMs: Long)

data class RawBackground(val text: String, val startMs: Long, val endMs: Long)

data class RawLyricLine(
    val text: String,
    val startMs: Long,
    val endMs: Long?,
    val syllables: List<RawSyllable>,
    /** Blank text is an instrumental stretch (LRC files mark those with a bare timestamp). */
    val isGap: Boolean = false,
    val sourceIndex: Int = -1,
    val background: RawBackground? = null,
)

/**
 * Full synced-lyrics document transferred phone -> watch.
 * Serialised as compact JSON, gzipped, and shipped as a Wearable [com.google.android.gms.wearable.Asset]
 * (DataItems are capped at ~100 KB; assets are not, and identical assets are de-duplicated).
 */
data class SyncedLyricsPayload(
    val trackId: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val lines: List<SyncedLine>,
    val contentHash: String,
    val schemaVersion: Int = SCHEMA_VERSION,
) {

    /** Index of the last line whose start <= [positionMs], or -1 before the first line. */
    fun lineIndexAt(positionMs: Long): Int {
        var lo = 0
        var hi = lines.lastIndex
        var result = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].startMs <= positionMs) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }

    fun toJson(): String = JSONObject()
        .put("v", schemaVersion).put("id", trackId).put("ti", title).put("ar", artist)
        .put("d", durationMs).put("h", contentHash)
        .put("l", linesToJson(lines))
        .toString()

    fun toGzipBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(toJson().toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    companion object {
        const val SCHEMA_VERSION = 1

        fun fromJson(json: String): SyncedLyricsPayload {
            val o = JSONObject(json)
            val arr = o.getJSONArray("l")
            return SyncedLyricsPayload(
                trackId = o.getString("id"),
                title = o.optString("ti", ""),
                artist = o.optString("ar", ""),
                durationMs = o.optLong("d", 0L),
                lines = List(arr.length()) { SyncedLine.fromJson(arr.getJSONObject(it)) },
                contentHash = o.getString("h"),
                schemaVersion = o.optInt("v", SCHEMA_VERSION),
            )
        }

        fun fromGzipStream(input: InputStream): SyncedLyricsPayload =
            GZIPInputStream(input).bufferedReader(Charsets.UTF_8).use { fromJson(it.readText()) }

        fun fromGzipBytes(bytes: ByteArray): SyncedLyricsPayload =
            fromGzipStream(bytes.inputStream())

        /**
         * Normalises raw lyric lines into a payload:
         *  - drops blank lines, sorts by start time, resolves missing end times
         *  - computes syllable character ranges (falls back to word-level timing if a line has none)
         *  - flags chorus lines (normalised text that repeats within the song)
         *  - computes a stable content hash used for the watch-side cache
         */
        fun build(
            trackId: String,
            title: String,
            artist: String,
            durationMs: Long,
            raw: List<RawLyricLine>,
        ): SyncedLyricsPayload {
            val sorted = raw.sortedBy { it.startMs }

            val converted = sorted.mapIndexed { i, r ->
                val text = r.text.trim()
                val nextStart = sorted.getOrNull(i + 1)?.startMs
                val fallbackEnd = if (durationMs > r.startMs) durationMs else r.startMs + DEFAULT_LINE_MS
                val end = (r.endMs ?: nextStart ?: fallbackEnd).coerceAtLeast(r.startMs + 1)

                if (r.isGap || text.isEmpty()) {
                    SyncedLine(
                        index = 0, text = "", startMs = r.startMs,
                        endMs = (nextStart ?: end).coerceAtLeast(r.startMs + 1),
                        syllables = emptyList(), isChorus = false, isGap = true, sourceIndex = r.sourceIndex,
                    )
                } else {
                    val syllables = if (r.syllables.any { it.text.isNotBlank() }) {
                        mapSyllables(text, r.syllables)
                    } else {
                        fallbackSyllables(text, r.startMs, end)
                    }
                    val bg = r.background?.takeIf { it.text.isNotBlank() }
                    SyncedLine(
                        index = 0, text = text, startMs = r.startMs, endMs = end,
                        syllables = syllables, isChorus = false, sourceIndex = r.sourceIndex,
                        bgText = bg?.text?.trim(),
                        bgStartMs = bg?.startMs ?: 0L,
                        bgEndMs = bg?.let { maxOf(it.endMs, it.startMs + MIN_BG_MS) } ?: 0L,
                    )
                }
            }

            // Gaps worth a countdown: the ones the source marks, and the ones its line ends reveal.
            val endStated = sorted.map { it.endMs != null }
            val withGaps = ArrayList<SyncedLine>()
            val firstReal = converted.firstOrNull { !it.isGap }
            if (firstReal != null && converted.first() === firstReal && firstReal.startMs >= INTRO_GAP_MIN_MS) {
                withGaps += gapLine(0L, firstReal.startMs, -1)
            }
            converted.forEachIndexed { i, line ->
                if (line.isGap) {
                    if (line.endMs - line.startMs >= GAP_MIN_MS) withGaps += line
                    return@forEachIndexed
                }
                withGaps += line
                val next = converted.getOrNull(i + 1)
                if (next != null && !next.isGap) {
                    // A line-timed source does not say when a line stops, only when the next starts. How
                    // long it takes to sing is then judged from its length, and a longer silence is
                    // asked for before calling it an instrumental break.
                    val stated = endStated[i]
                    val sungEnd = if (stated) line.endMs else minOf(line.endMs, line.startMs + estimatedSungMs(line.text))
                    val from = sungEnd + GAP_LEAD_MS
                    val needed = if (stated) GAP_MIN_MS else GAP_MIN_UNSTATED_MS
                    if (next.startMs - from >= needed) withGaps += gapLine(from, next.startMs, -1)
                }
            }
            val lines = withGaps.mapIndexed { i, l -> l.copy(index = i) }

            val marked = markChoruses(lines)
            return SyncedLyricsPayload(
                trackId = trackId,
                title = title,
                artist = artist,
                durationMs = durationMs,
                lines = marked,
                contentHash = hashOf(marked),
            )
        }

        private fun estimatedSungMs(text: String): Long = (text.length * 110L).coerceIn(2_500L, 8_000L)

        private fun gapLine(start: Long, end: Long, sourceIndex: Int) = SyncedLine(
            index = 0, text = "", startMs = start, endMs = end, syllables = emptyList(),
            isChorus = false, isGap = true, sourceIndex = sourceIndex,
        )

        private fun mapSyllables(text: String, raw: List<RawSyllable>): List<SyncedSyllable> {
            var cursor = 0
            return raw.filter { it.text.isNotBlank() }.map { r ->
                val needle = r.text.trim()
                var at = text.indexOf(needle, cursor)
                if (at < 0) at = cursor.coerceAtMost(text.length)
                val end = (at + needle.length).coerceAtMost(text.length)
                cursor = end
                SyncedSyllable(
                    text = r.text,
                    startMs = r.startMs,
                    endMs = r.endMs.coerceAtLeast(r.startMs + 1),
                    charStart = at,
                    charEnd = end,
                )
            }
        }

        private fun fallbackSyllables(text: String, start: Long, end: Long): List<SyncedSyllable> {
            val words = Regex("\\S+").findAll(text).toList()
            if (words.isEmpty()) return emptyList()
            val totalChars = words.sumOf { it.value.length }.coerceAtLeast(1)
            val span = (end - start).coerceAtLeast(words.size.toLong())
            var acc = 0
            return words.map { w ->
                val s = start + span * acc / totalChars
                acc += w.value.length
                val e = start + span * acc / totalChars
                SyncedSyllable(w.value, s, e, w.range.first, w.range.last + 1)
            }
        }

        private fun normalise(text: String): String =
            text.lowercase().filter { it.isLetterOrDigit() }

        private fun markChoruses(lines: List<SyncedLine>): List<SyncedLine> {
            val counts = HashMap<String, Int>()
            lines.filter { !it.isGap }.forEach { counts.merge(normalise(it.text), 1, Int::plus) }
            return lines.map {
                val key = normalise(it.text)
                it.copy(isChorus = !it.isGap && key.length >= MIN_CHORUS_CHARS && (counts[key] ?: 0) >= 2)
            }
        }

        private fun linesToJson(lines: List<SyncedLine>): JSONArray =
            JSONArray().also { arr -> lines.forEach { arr.put(it.toJson()) } }

        private fun hashOf(lines: List<SyncedLine>): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(linesToJson(lines).toString().toByteArray(Charsets.UTF_8))
            return digest.take(8).joinToString("") { "%02x".format(it) }
        }

        private const val DEFAULT_LINE_MS = 4_000L
        private const val MIN_CHORUS_CHARS = 6

        /** A silence shorter than this is not worth a countdown. */
        private const val GAP_MIN_MS = 5_000L
        private const val GAP_MIN_UNSTATED_MS = 6_000L
        private const val INTRO_GAP_MIN_MS = 4_000L
        private const val GAP_LEAD_MS = 300L
        private const val MIN_BG_MS = 1_200L
    }
}
