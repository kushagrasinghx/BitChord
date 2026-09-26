package com.music.bitchord.data.lyrics

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Turns the wire formats used by the smaller providers into BitChord lyrics. */
object ProviderLyrics {

    fun parse(raw: String): List<LyricLine>? {
        val content = unescapeTtml(unwrap(raw) ?: return null)
        val lines = when {
            content.contains(Regex("""<tt(?:\s|>)""", RegexOption.IGNORE_CASE)) ||
                content.contains("http://www.w3.org/ns/ttml", ignoreCase = true) -> TtmlLyrics.parse(content)
            KaraokeLrc.looksLike(content) -> KaraokeLrc.parse(content)
            content.trimStart().startsWith("<") -> emptyList()
            else -> EnhancedLrc.parse(content).ifEmpty { LrcLib.parseLrc(content) }
                .ifEmpty { plain(content) }
        }
        return lines.takeIf { found -> found.any { it.text.isNotBlank() } }
    }

    private fun plain(content: String): List<LyricLine> {
        if (content.contains(Regex("""(?i)\b(?:lyrics? (?:not found|unavailable)|error)\b"""))) return emptyList()
        return content.lineSequence().map(String::trim).filter(String::isNotEmpty)
            .filterNot { it.matches(Regex("""\[[A-Za-z]+:.*]""")) }
            .map { LyricLine(0L, it) }.toList()
    }

    /** Providers sometimes wrap the same lyric string in one or two JSON envelopes. */
    internal fun unwrap(raw: String): String? {
        var value = raw.replace("\uFEFF", "").trim()
        if (value.startsWith("```")) {
            value = value.lineSequence().drop(1).toList()
                .let { if (it.lastOrNull()?.trim() == "```") it.dropLast(1) else it }
                .joinToString("\n").trim()
        }
        if (value.isBlank()) return null
        val json = runCatching { lyricsJson.parseToJsonElement(value) }.getOrNull() ?: return value
        return extract(json)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun extract(element: JsonElement): String? = when (element) {
        JsonNull -> null
        is JsonPrimitive -> if (element.isString) {
            val text = element.content.trim()
            val nested = runCatching { lyricsJson.parseToJsonElement(text) }.getOrNull()
            if (nested != null && nested !is JsonPrimitive) extract(nested) else text
        } else null
        is JsonArray -> element.mapNotNull(::extract).joinToString("\n").takeIf { it.isNotBlank() }
        is JsonObject -> {
            if (element["isError"]?.toString() == "true" || element["ok"]?.toString() == "false" ||
                element["error"]?.let { it !is JsonNull && it.toString() !in setOf("false", "\"\"") } == true
            ) return null
            CONTENT_KEYS.asSequence().mapNotNull { element[it]?.let(::extract) }.firstOrNull()
                ?: (element["metadata"] as? JsonObject)?.let(::extract)
                ?: element["words"]?.let(::extract)
        }
    }

    private fun unescapeTtml(value: String): String = if (value.contains("&lt;tt", ignoreCase = true)) {
        value.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&")
    } else value

    private val CONTENT_KEYS = listOf(
        "ttml", "ttmlContent", "lyrics", "lrc", "content", "text",
        "plainLyrics", "syncedLyrics", "line", "lines", "lyric",
        "data", "result", "response",
    )
}

/** QQ/QRC and NetEase YRC: millisecond line and word ranges. */
object KaraokeLrc {
    private val LINE = Regex("""^\[(\d{1,8}),(\d{1,8})](.*)$""")
    private val PREFIX_WORD = Regex("""\((\d{1,8}),(\d{1,8})(?:,\d{1,8})?\)([^()]*)""")
    private val SUFFIX_WORD = Regex("""([^()]*)\((\d{1,8}),(\d{1,8})(?:,\d{1,8})?\)""")
    private val WORD_TIME = Regex("""\(\d{1,8},\d{1,8}(?:,\d{1,8})?\)""")
    private val CONTENT = Regex("""LyricContent\s*=\s*\"([^\"]*)\"""", RegexOption.IGNORE_CASE)

    fun looksLike(raw: String): Boolean = lyricContent(raw).lineSequence().any { line ->
        LINE.matchEntire(line.trim())?.groupValues?.get(3)?.let {
            PREFIX_WORD.containsMatchIn(it) || SUFFIX_WORD.containsMatchIn(it)
        } == true
    }

    fun parse(raw: String): List<LyricLine> {
        val rows = lyricContent(raw).lineSequence().mapNotNull { source ->
            val match = LINE.matchEntire(source.trim()) ?: return@mapNotNull null
            val lineStart = match.groupValues[1].toLong()
            val lineDuration = match.groupValues[2].toLong()
            val body = match.groupValues[3]
            val prefixed = PREFIX_WORD.findAll(body).mapNotNull { word ->
                timedWord(word.groupValues[3], word.groupValues[1], word.groupValues[2])
            }.toList()
            val suffixed = SUFFIX_WORD.findAll(body).mapNotNull { word ->
                timedWord(word.groupValues[1], word.groupValues[2], word.groupValues[3])
            }.toList()
            val words = if (prefixed.sumOf { it.text.length } >= suffixed.sumOf { it.text.length }) prefixed else suffixed
            if (words.isEmpty()) return@mapNotNull null
            LyricLine(
                timeMs = minOf(lineStart, words.first().startMs),
                text = EnhancedLrc.decodeEntities(body.replace(WORD_TIME, "")).trim(),
                words = words,
                sungUntilMs = (lineStart + lineDuration).takeIf { lineDuration > 0 },
            )
        }.filter { it.text.isNotEmpty() }.sortedBy { it.timeMs }.toList()
        return rows.withInstrumentalGaps()
    }

    private fun timedWord(text: String, start: String, duration: String): LyricWord? {
        val clean = EnhancedLrc.decodeEntities(text).trim()
        if (clean.isEmpty()) return null
        val startMs = start.toLong()
        return LyricWord(startMs, startMs + duration.toLong(), clean)
    }

    private fun lyricContent(raw: String): String = CONTENT.find(raw)?.groupValues?.get(1)
        ?.replace("&quot;", "\"")?.replace("&apos;", "'")?.replace("&lt;", "<")
        ?.replace("&gt;", ">")?.replace("&amp;", "&") ?: raw
}
