/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Adapted from BitChord (GPL-3.0) https://github.com/kushagrasinghx/BitChord
 * (shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/
 *  LyricsHttp.kt, ProviderLyrics.kt, EnhancedLrc.kt, LyricsQuery.kt) —
 * original re-implementation for ArchiveTune.
 */

package moe.rukamori.archivetune.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder
import java.util.Locale

internal fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

internal fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

internal fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

internal fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

internal fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

internal fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

internal object LyricsProviderHttp {
    const val USER_AGENT = "ArchiveTune (https://github.com/rukamori)"

    val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                connectTimeoutMillis = 8_000
                requestTimeoutMillis = 12_000
                socketTimeoutMillis = 12_000
            }

            expectSuccess = false
        }
    }

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): String? =
        try {
            val response =
                client.get(url) {
                    var sentAgent = false
                    headers.forEach { (name, value) ->
                        header(name, value)
                        if (name.equals("User-Agent", ignoreCase = true)) sentAgent = true
                    }
                    if (!sentAgent) header("User-Agent", USER_AGENT)
                }
            if (response.status.isSuccess()) response.bodyAsText() else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    fun parseJson(raw: String): JsonElement? = runCatching { json.parseToJsonElement(raw) }.getOrNull()
}

internal object LyricsPayload {
    fun unwrap(raw: String): String? {
        var value = raw.replace("\uFEFF", "").trim()
        if (value.startsWith("```")) {
            value =
                value
                    .lineSequence()
                    .drop(1)
                    .toList()
                    .let { if (it.lastOrNull()?.trim() == "```") it.dropLast(1) else it }
                    .joinToString("\n")
                    .trim()
        }
        if (value.isBlank()) return null
        val element = LyricsProviderHttp.parseJson(value) ?: return value
        return extract(element)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun extract(element: JsonElement): String? =
        when (element) {
            JsonNull -> null
            is JsonPrimitive ->
                if (element.isString) {
                    val text = element.content.trim()
                    val nested = LyricsProviderHttp.parseJson(text)
                    if (nested != null && nested !is JsonPrimitive) extract(nested) else text
                } else {
                    null
                }
            is JsonArray -> element.mapNotNull(::extract).joinToString("\n").takeIf { it.isNotBlank() }
            is JsonObject -> {
                if (isError(element)) return null
                CONTENT_KEYS.asSequence().mapNotNull { element[it]?.let(::extract) }.firstOrNull()
                    ?: (element["metadata"] as? JsonObject)?.let(::extract)
                    ?: element["words"]?.let(::extract)
            }
        }

    private fun isError(element: JsonObject): Boolean {
        if (element["isError"]?.toString() == "true") return true
        if (element["ok"]?.toString() == "false") return true
        val error = element["error"]
        if (error != null && error !is JsonNull && error.toString() !in BENIGN_ERRORS) return true
        return false
    }

    fun timedAppleToTtml(raw: String): String? {
        val root = LyricsProviderHttp.parseJson(raw) ?: return null
        val content = root.findTimedContent() ?: return null
        val rows = content.mapNotNull { it as? JsonObject }
        if (rows.isEmpty()) return null

        val builder = StringBuilder(TTML_HEAD)
        var wroteLine = false
        rows.forEachIndexed { index, row ->
            val start = row.long("timestamp") ?: return@forEachIndexed
            val wordRows = row.array("text") ?: return@forEachIndexed
            if (wordRows.isEmpty()) return@forEachIndexed
            val nextLine = rows.getOrNull(index + 1)?.long("timestamp")

            val timed = wordRows.mapNotNull { it.asObject() }.filter { it.long("timestamp") != null }
            val lineEnd = nextLine ?: (start + DEFAULT_WORD_MS)

            if (timed.size != wordRows.size) {
                val text = wordRows.mapNotNull { it.asObject()?.string("text") }.joinToString(" ") { it.trim() }
                if (text.isBlank()) return@forEachIndexed
                builder
                    .append("""<p begin="""")
                    .append(seconds(start))
                    .append("""" end="""")
                    .append(seconds(maxOf(lineEnd, start)))
                    .append("""">""")
                    .append(escapeXml(text))
                    .append("</p>")
                wroteLine = true
                return@forEachIndexed
            }

            var lastEnd = start
            val spans = StringBuilder()
            timed.forEachIndexed { wordIndex, word ->
                val text = word.string("text") ?: return@forEachIndexed
                val wordStart = word.long("timestamp") ?: return@forEachIndexed
                val wordEnd =
                    word.long("endtime")
                        ?: (timed.getOrNull(wordIndex + 1))?.long("timestamp")
                        ?: nextLine
                        ?: (wordStart + DEFAULT_WORD_MS)
                val joinsNext = word.boolean("part") == true
                spans.appendSpan(wordStart, maxOf(wordEnd, wordStart), if (joinsNext) text else "$text ")
                lastEnd = maxOf(lastEnd, wordEnd)
            }

            builder.appendParagraphStart(start, nextLine ?: lastEnd)
            builder.append(spans)
            builder.append("</p>")
            wroteLine = true
        }
        if (!wroteLine) return null
        builder.append(TTML_TAIL)
        return builder.toString()
    }

    fun toLyrics(raw: String): String? {
        val body = raw.replace("\uFEFF", "").trim()
        if (body.isEmpty()) return null

        if (isTtmlDocument(body)) return body
        timedAppleToTtml(body)?.let { return it }

        val unescaped = unescapeTtml(body)
        if (isTtmlDocument(unescaped)) return unescaped

        val inner = unwrap(body) ?: return null
        if (isTtmlDocument(inner)) return inner
        val unescapedInner = unescapeTtml(inner)
        if (isTtmlDocument(unescapedInner)) return unescapedInner

        if (inner.trimStart().startsWith("<")) return null

        val text = decodeEntities(inner)
        if (NOT_FOUND_HINT.containsMatchIn(text)) return null
        return text.trim().takeIf { it.isNotEmpty() }
    }

    private fun isTtmlDocument(value: String): Boolean {
        val trimmed = value.trimStart()
        if (trimmed.startsWith("<tt", ignoreCase = true)) return true
        return trimmed.startsWith("<") &&
            trimmed.contains("http://www.w3.org/ns/ttml", ignoreCase = true)
    }

    private fun unescapeTtml(value: String): String =
        if (value.contains("&lt;tt", ignoreCase = true)) {
            value
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&amp;", "&")
        } else {
            value
        }

    fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        return text
            .replace(HEX_ENTITY) { match ->
                runCatching { match.groupValues[1].toInt(16).toChar().toString() }.getOrDefault(match.value)
            }.replace(DEC_ENTITY) { match ->
                runCatching { match.groupValues[1].toInt().toChar().toString() }.getOrDefault(match.value)
            }.replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")

            .replace("&amp;", "&")
    }

    private fun escapeXml(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun seconds(ms: Long): String = String.format(Locale.ROOT, "%.3f", ms / 1000.0)

    private fun JsonElement.findTimedContent(): JsonArray? =
        when (this) {
            is JsonObject ->
                (this["content"] as? JsonArray)?.takeIf { array ->
                    array.any { (it as? JsonObject)?.get("timestamp") != null }
                } ?: values.firstNotNullOfOrNull { it.findTimedContent() }
            is JsonArray -> firstNotNullOfOrNull { it.findTimedContent() }
            else -> null
        }

    private fun StringBuilder.appendParagraphStart(
        startMs: Long,
        endMs: Long,
    ) {
        append("""<p begin="""")
        append(seconds(startMs))
        append("""" end="""")
        append(seconds(endMs))
        .append("""">""")
    }

    private fun StringBuilder.appendSpan(
        startMs: Long,
        endMs: Long,
        text: String,
    ) {
        append("""<span begin="""")
        append(seconds(startMs))
        append("""" end="""")
        append(seconds(endMs))
        append("""">""")
        append(escapeXml(text))
        append("</span>")
    }

    private const val DEFAULT_WORD_MS = 800L

    private const val TTML_HEAD = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div>"""
    private const val TTML_TAIL = """</div></body></tt>"""

    private val CONTENT_KEYS =
        listOf(
            "ttml", "ttmlContent", "lyrics", "lrc", "content", "text",
            "plainLyrics", "syncedLyrics", "line", "lines", "lyric",
            "data", "result", "response",
        )

    private val BENIGN_ERRORS = setOf("false", "\"\"")

    private val NOT_FOUND_HINT =
        Regex("""(?i)\b(?:lyrics?\s+(?:not\s+found|unavailable|not\s+available))\b""")

    private val HEX_ENTITY = Regex("""&#x([0-9a-fA-F]{1,5});""")
    private val DEC_ENTITY = Regex("""&#(\d{1,5});""")
}

internal fun String.forLyricsSearch(): String {
    var name = this
    CREDITS.forEach { pattern -> name = pattern.replace(name, " ") }
    return name
        .replace(SEARCH_WHITESPACE, " ")
        .trim()
        .trimEnd(',', '-', '–', '—')
        .trim()

        .ifBlank { trim() }
}

internal fun String.artistForLyricsSearch(): String = removeSuffix(" - Topic").trim().ifBlank { trim() }

private val SEARCH_WHITESPACE = Regex("""\s+""")

private val CREDITS =
    listOf(

        Regex("""\s*[(\[]\s*(feat|ft|featuring|with)\b[^)\]]*[)\]]""", RegexOption.IGNORE_CASE),

        Regex("""\s+(feat|ft|featuring)\.?\s+.*$""", RegexOption.IGNORE_CASE),

        Regex(
            """\s*[(\[]\s*(official\s*)?(music\s*)?""" +
                """(video|audio|visuali[sz]er|lyrics?\s*video|lyrics?|m/?v|hd|hq|4k|full\s*song)""" +
                """\s*[)\]]""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("""\s*[(\[]\s*official\s*[)\]]""", RegexOption.IGNORE_CASE),
    )
