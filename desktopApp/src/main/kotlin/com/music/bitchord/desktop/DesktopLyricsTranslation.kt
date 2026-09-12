package com.music.bitchord.desktop

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.client.statement.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.path.extension

/**
 * Lyric translation, asked for a line at a time and kept small.
 *
 * A port of Android's `LyricsTranslation`. No model is installed: one per language would cost tens
 * of megabytes, while translated lyric text is a few kilobytes. Requests are batched and the result
 * is kept gzipped under the cache directory, capped so the feature cannot grow without limit.
 */
internal object DesktopLyricsTranslation {

    sealed interface Result {
        data class Translated(
            val lines: List<DesktopLyricLine>,
            val sourceLanguage: String,
            val fromCache: Boolean,
        ) : Result

        /** The lyrics are already in the language they were asked to be put into. */
        data class SameLanguage(val language: String) : Result

        data object Unavailable : Result
    }

    private const val ENDPOINT = "https://translate.googleapis.com/translate_a/single"
    private const val CACHE_VERSION = 1
    private const val MAX_CACHE_BYTES = 2L * 1024L * 1024L
    private const val MAX_BATCH_CHARS = 3_500
    private const val MAX_PARALLEL_REQUESTS = 2

    private val markerRegex = Regex("\\uE000\\d{4}\\uE001")
    private val json = Json { ignoreUnknownKeys = true }
    private val diskMutex = Mutex()
    private val client = HttpClient(CIO)

    /** The last dozen translations, so flipping the button back and forth costs nothing. */
    private val memory = object : LinkedHashMap<String, Cached>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Cached>): Boolean = size > 12
    }

    private val directory: Path by lazy {
        val base = System.getenv("XDG_CACHE_HOME")?.takeIf(String::isNotBlank)
            ?: "${System.getProperty("user.home")}/.cache"
        Path.of(base, "bitchord", "lyrics-translation")
    }

    @Serializable
    private data class Cached(
        val version: Int = CACHE_VERSION,
        val sourceLanguage: String,
        val targetLanguage: String,
        val texts: List<String>,
    )

    /** One piece of text to translate, and where it came from. */
    private data class Slot(
        val lineIndex: Int,
        val background: Boolean,
        val text: String,
        val sectionHeader: Boolean,
    )

    private data class Batch(val slots: List<Slot>, val payload: String)

    private data class Answer(
        val translations: List<String>,
        val sourceLanguage: String,
        val weight: Int,
    )

    suspend fun translate(
        trackId: String,
        lines: List<DesktopLyricLine>,
        targetLanguageTag: String,
    ): Result {
        // Sent as given rather than reduced to a base language: zh-CN and zh-TW are the same
        // language in two scripts, and canonicalising either to "zh" hands back Simplified whichever
        // one was asked for. The narrowing belongs in [sameLanguage], which asks a different
        // question.
        val target = targetLanguageTag.trim()
        if (target.isBlank() || lines.isEmpty()) return Result.Unavailable

        val slots = flatten(lines)
        if (slots.isEmpty()) return Result.Unavailable
        val key = cacheKey(trackId, target, slots)

        val cached = synchronized(memory) { memory[key] } ?: readCache(key)?.also {
            synchronized(memory) { memory[key] = it }
        }
        if (cached != null && cached.version == CACHE_VERSION && cached.texts.size == slots.size) {
            return answer(lines, slots, cached.texts, cached.sourceLanguage, target, fromCache = true)
        }

        val answers = coroutineScope {
            // Two short requests at a time keeps a long lyric fast without competing with playback
            // for every connection in the pool.
            batches(slots).chunked(MAX_PARALLEL_REQUESTS).flatMap { group ->
                group.map { batch -> async { requestBatch(batch, target) } }.awaitAll()
            }
        }
        if (answers.any { it == null }) return Result.Unavailable
        val complete = answers.filterNotNull()
        val source = complete
            .groupBy { canonicalLanguage(it.sourceLanguage) }
            .maxByOrNull { (_, values) -> values.sumOf { it.weight } }
            ?.key
            .orEmpty()
        if (source.isBlank()) return Result.Unavailable

        val translated = complete.flatMap { it.translations }
        if (translated.size != slots.size) return Result.Unavailable
        val entry = Cached(sourceLanguage = source, targetLanguage = target, texts = translated)
        synchronized(memory) { memory[key] = entry }
        writeCache(key, entry)
        return answer(lines, slots, translated, source, target, fromCache = false)
    }

    private fun answer(
        lines: List<DesktopLyricLine>,
        slots: List<Slot>,
        texts: List<String>,
        source: String,
        target: String,
        fromCache: Boolean,
    ): Result = if (sameLanguage(source, target)) {
        Result.SameLanguage(source)
    } else {
        Result.Translated(rebuild(lines, slots, texts), source, fromCache)
    }

    private fun flatten(lines: List<DesktopLyricLine>): List<Slot> = buildList {
        lines.forEachIndexed { index, line ->
            if (line.text.isNotBlank()) {
                val header = isSectionHeader(line.text)
                add(
                    Slot(
                        lineIndex = index,
                        background = false,
                        text = if (header) line.text.removePrefix("[").removeSuffix("]").trim() else line.text,
                        sectionHeader = header,
                    ),
                )
            }
            line.background?.takeIf { it.text.isNotBlank() }?.let {
                add(Slot(index, background = true, it.text, sectionHeader = false))
            }
        }
    }

    /** `[Chorus]`, `[Verse 2]` — a structure marker rather than something sung. */
    private fun isSectionHeader(text: String): Boolean =
        text.startsWith("[") && text.endsWith("]") && text.length > 2

    private fun batches(slots: List<Slot>): List<Batch> {
        val out = mutableListOf<Batch>()
        var current = mutableListOf<Slot>()
        var length = 0

        fun flush() {
            if (current.isEmpty()) return
            out += Batch(current.toList(), payload(current))
            current = mutableListOf()
            length = 0
        }

        slots.forEach { slot ->
            val added = slot.text.length + if (current.isEmpty()) 0 else 8
            if (current.isNotEmpty() && length + added > MAX_BATCH_CHARS) flush()
            current += slot
            length += added
        }
        flush()
        return out
    }

    private fun payload(slots: List<Slot>): String = buildString {
        slots.forEachIndexed { index, slot ->
            if (index > 0) append('\n').append(marker(index)).append('\n')
            append(slot.text)
        }
    }

    /** A separator the translator carries through untouched, so the lines can be split apart again. */
    private fun marker(index: Int): String =
        "\uE000" + index.toString().padStart(4, '0') + "\uE001"

    private suspend fun requestBatch(batch: Batch, target: String): Answer? {
        val body = runCatching {
            val response: HttpResponse = client.submitForm(
                url = ENDPOINT,
                formParameters = Parameters.build {
                    append("client", "dict-chrome-ex")
                    append("sl", "auto")
                    append("tl", target)
                    append("dt", "t")
                    append("q", batch.payload)
                },
            ) {
                header("User-Agent", "BitChord")
                header("Accept", "application/json")
            }
            if (!response.status.isSuccess()) return null
            response.bodyAsText()
        }.getOrNull() ?: return null

        return runCatching {
            val root = json.parseToJsonElement(body).jsonArray
            val translated = root[0].jsonArray.joinToString(separator = "") { segment ->
                segment.jsonArray.getOrNull(0)?.jsonPrimitive?.contentOrNull.orEmpty()
            }
            val source = root.getOrNull(2)?.jsonPrimitive?.contentOrNull.orEmpty()
            val parts = translated.split(markerRegex).map(String::trim)
            if (parts.size != batch.slots.size || parts.any(String::isBlank)) return@runCatching null
            Answer(parts, source, batch.payload.length)
        }.getOrNull()
    }

    private fun rebuild(
        original: List<DesktopLyricLine>,
        slots: List<Slot>,
        translated: List<String>,
    ): List<DesktopLyricLine> {
        val byLine = slots.zip(translated).groupBy { it.first.lineIndex }
        return original.mapIndexed { index, line ->
            val entries = byLine[index].orEmpty()
            val lead = entries.firstOrNull { !it.first.background }
            val backing = entries.firstOrNull { it.first.background }
            val leadText = lead?.let { (slot, text) -> if (slot.sectionHeader) "[$text]" else text }
                ?: line.text
            line.copy(
                text = leadText,
                words = retimeWords(line, leadText),
                background = line.background?.let { source ->
                    val text = backing?.second ?: source.text
                    source.copy(text = text, words = retimeWords(source, text))
                },
            )
        }
    }

    /**
     * The line's own span, as one word.
     *
     * A translation has neither the same words nor the same number of them, so per-word timings
     * cannot survive it. Keeping the bounds keeps the line lighting up when it is sung.
     */
    private fun retimeWords(source: DesktopLyricLine, translated: String): List<DesktopLyricWord> {
        if (source.words.isEmpty() || translated.isEmpty()) return emptyList()
        return listOf(
            DesktopLyricWord(source.words.first().startMs, source.words.last().endMs, translated),
        )
    }

    private fun canonicalLanguage(tag: String): String {
        val language = Locale.forLanguageTag(tag.replace('_', '-')).language.lowercase(Locale.ROOT)
        // The three Java still spells the pre-1989 way.
        return when (language) {
            "iw" -> "he"
            "in" -> "id"
            "ji" -> "yi"
            else -> language
        }
    }

    private fun sameLanguage(first: String, second: String): Boolean =
        canonicalLanguage(first) == canonicalLanguage(second)

    private fun cacheKey(trackId: String, target: String, slots: List<Slot>): String {
        val source = buildString {
            append(trackId).append(' ').append(target)
            slots.forEach { append(' ').append(it.text) }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private suspend fun readCache(key: String): Cached? = withContext(Dispatchers.IO) {
        diskMutex.withLock {
            val file = directory.resolve("$key.json.gz")
            if (!Files.isReadable(file)) return@withLock null
            runCatching {
                val value = GZIPInputStream(Files.newInputStream(file)).bufferedReader().use {
                    json.decodeFromString<Cached>(it.readText())
                }
                // Touched so the trim keeps what is being used.
                Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()))
                value.takeIf { it.version == CACHE_VERSION }
            }.getOrNull()
        }
    }

    private suspend fun writeCache(key: String, value: Cached) = withContext(Dispatchers.IO) {
        diskMutex.withLock {
            runCatching {
                Files.createDirectories(directory)
                val temporary = directory.resolve("$key.tmp")
                GZIPOutputStream(Files.newOutputStream(temporary)).bufferedWriter().use {
                    it.write(json.encodeToString(Cached.serializer(), value))
                }
                Files.move(
                    temporary,
                    directory.resolve("$key.json.gz"),
                    StandardCopyOption.REPLACE_EXISTING,
                )
                trimCache()
            }
        }
        Unit
    }

    /** Newest first, dropping whatever falls past the cap. */
    private fun trimCache() {
        val files = runCatching {
            Files.list(directory).use { stream ->
                stream.filter { it.extension == "gz" }.toList()
                    .sortedByDescending { Files.getLastModifiedTime(it).toMillis() }
            }
        }.getOrDefault(emptyList())
        var kept = 0L
        files.forEach { file ->
            kept += runCatching { Files.size(file) }.getOrDefault(0L)
            if (kept > MAX_CACHE_BYTES) runCatching { Files.delete(file) }
        }
    }
}
