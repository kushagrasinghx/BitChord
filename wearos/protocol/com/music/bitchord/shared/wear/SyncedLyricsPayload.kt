package com.music.bitchord.shared.wear

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
        .put("y", JSONArray().also { arr -> syllables.forEach { arr.put(it.toJson()) } })

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
            )
        }
    }
}

/** Plain, source-agnostic input for [SyncedLyricsPayload.build]. Map `LyricLine` onto this. */
data class RawSyllable(val text: String, val startMs: Long, val endMs: Long)

data class RawLyricLine(
    val text: String,
    val startMs: Long,
    val endMs: Long?,
    val syllables: List<RawSyllable>,
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
            val sorted = raw.filter { it.text.isNotBlank() }.sortedBy { it.startMs }

            val lines = sorted.mapIndexed { i, r ->
                val text = r.text.trim()
                val nextStart = sorted.getOrNull(i + 1)?.startMs
                val fallbackEnd = if (durationMs > r.startMs) durationMs else r.startMs + DEFAULT_LINE_MS
                val end = (r.endMs ?: nextStart ?: fallbackEnd).coerceAtLeast(r.startMs + 1)

                val syllables = if (r.syllables.any { it.text.isNotBlank() }) {
                    mapSyllables(text, r.syllables)
                } else {
                    fallbackSyllables(text, r.startMs, end)
                }
                SyncedLine(
                    index = i,
                    text = text,
                    startMs = r.startMs,
                    endMs = end,
                    syllables = syllables,
                    isChorus = false,
                )
            }

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
            lines.forEach { counts.merge(normalise(it.text), 1, Int::plus) }
            return lines.map {
                val key = normalise(it.text)
                it.copy(isChorus = key.length >= MIN_CHORUS_CHARS && (counts[key] ?: 0) >= 2)
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
    }
}