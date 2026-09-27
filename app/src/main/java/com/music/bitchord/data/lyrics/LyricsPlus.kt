package com.music.bitchord.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.atomic.AtomicReference

/**
 * Syllable-timed lyrics from LyricsPlus, the open backend behind the YouLy+
 * extension. It aggregates Apple Music, QQ Music and Musixmatch, and its v2
 * response is the finest-grained of the providers here — Apple's own syllable
 * splits, not just word boundaries.
 *
 * The catch is hosting: it runs on volunteer mirrors, and at any given moment
 * most of them are rate-limited, out of Vercel credit or simply gone. The
 * extension's answer, copied here, is to ask all of them at once and take the
 * first real answer. The winner is remembered so the next track goes straight
 * to a host that was up a minute ago instead of paying for the race again.
 */
object LyricsPlus {

    private val MIRRORS = listOf(
        "https://lyricsplus.prjktla.my.id",
        "https://lyricsplus.atomix.one",
        "https://lyricsplus.binimum.org",
        "https://lyricsplus.prjktla.workers.dev",
        "https://lyricsplus-seven.vercel.app",
        "https://lyrics-plus-backend.vercel.app",
    )

    private val lastGood = AtomicReference<String?>(null)

    suspend fun lyrics(
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
        isrc: String? = null,
    ): List<LyricLine>? = coroutineScope {
        val hosts = lastGood.get()
            ?.let { listOf(it) + MIRRORS.filterNot { mirror -> mirror == it } }
            ?: MIRRORS

        val pending = hosts.map { host ->
            host to async(Dispatchers.IO) { fetch(host, title, artist, durationMs, album, isrc) }
        }.toMutableList()

        // Take the first mirror to answer with something usable rather than
        // the first to answer at all — a mirror that 404s this track shouldn't
        // beat one that has it.
        try {
            while (pending.isNotEmpty()) {
                val (host, lines) = select {
                    pending.forEach { (host, job) -> job.onAwait { host to it } }
                }
                pending.removeAll { it.first == host }
                if (!lines.isNullOrEmpty()) {
                    lastGood.set(host)
                    return@coroutineScope lines
                }
            }
            null
        } finally {
            pending.forEach { it.second.cancel() }
        }
    }

    private suspend fun fetch(
        host: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
        isrc: String?,
    ): List<LyricLine>? = withContext(Dispatchers.IO) {
        val url = "$host/v2/lyrics/get".toHttpUrl().newBuilder()
            .addQueryParameter("title", title)
            .addQueryParameter("artist", artist)
            .apply {
                val seconds = durationMs / 1000
                if (seconds > 0) addQueryParameter("duration", seconds.toString())
                if (!album.isNullOrBlank()) addQueryParameter("album", album)
                // Sent alongside the name rather than instead of it: unlike
                // [BiniLyrics] this backend aggregates several catalogues, and
                // the ones with no ISRC index still need something to match on.
                if (!isrc.isNullOrBlank()) addQueryParameter("isrc", isrc)
            }
            .build()

        val body = lyricsGet(url.toString()) ?: return@withContext null
        val response = runCatching { lyricsJson.decodeFromString<Response>(body) }.getOrNull()
            ?: return@withContext null
        parse(response).takeIf { it.isNotEmpty() }
    }

    internal fun parse(response: Response): List<LyricLine> {
        val sung = response.lyrics.orEmpty().mapNotNull { line ->
            val start = line.time ?: return@mapNotNull null
            val words = mergeSyllables(line.syllabus.orEmpty())
            val built = when {
                words.isNotEmpty() -> LyricLine(
                    timeMs = minOf(start, words.first().startMs),
                    text = words.joinToString(" ") { it.text },
                    words = words,
                )
                // Some sources are only line-synced; still worth showing.
                // The line's duration is the only end it gets, and without it
                // an interlude can't be told from a slowly sung line.
                !line.text.isNullOrBlank() -> LyricLine(
                    timeMs = start,
                    text = line.text.trim(),
                    sungUntilMs = line.duration?.takeIf { it > 0 }?.let { start + it },
                )
                else -> null
            }
            built?.to(line.element)
        }.sortedBy { it.first.timeMs }

        val sides = lineAlignments(sung.map { singerOf(it.second) }, response.agentTypes())
        return sung.mapIndexed { index, (line, element) ->
            // A payload that names no voices at all can still mark a line as the
            // answering side, which is how the older shape of this API said the
            // same thing. Taken only where the walk found nothing, so a song
            // that does name its voices is laid out from those.
            val side = if (sides[index] == LyricAlignment.End || element.saysOpposite()) {
                LyricAlignment.End
            } else {
                LyricAlignment.Start
            }
            line.copy(alignment = side)
        }.withInstrumentalGaps()
    }

    /**
     * Who sang a line. `element` is an object carrying a `singer` on the current
     * shape of the API and a bare array of tags on the older one, so it is read
     * as raw JSON: declared as either concrete type, a payload using the other
     * would fail to decode and take the whole track's lyrics down with it.
     */
    private fun singerOf(element: JsonElement?): String? =
        (element as? JsonObject)?.get("singer")?.let { it as? JsonPrimitive }?.contentOrNull

    /** The older shape: `element` as a list of tags, one of which is the side. */
    private fun cleanContent(primitive: JsonPrimitive): String? =
        if (primitive.isString) primitive.content else null

    private fun JsonElement?.saysOpposite(): Boolean {
        val array = runCatching { this?.jsonArray }.getOrNull() ?: return false
        return array.any { entry ->
            val tag = runCatching { entry.jsonPrimitive }.getOrNull()?.let(::cleanContent)
            tag == "opposite" || tag == "right"
        }
    }

    /**
     * Glues syllables back into words.
     *
     * The API's own spacing is the word boundary — it emits `"e"` then
     * `"nough "`, and the trailing space is the only thing saying those are
     * one word. Splitting on the syllable instead would render "e nough".
     */
    private fun mergeSyllables(syllables: List<Syllable>): List<LyricWord> {
        val words = mutableListOf<LyricWord>()
        val current = StringBuilder()
        var start = 0L
        var end = 0L

        syllables.forEach { syllable ->
            val text = syllable.text ?: return@forEach
            if (text.isBlank()) return@forEach
            val time = syllable.time ?: return@forEach
            if (current.isEmpty()) start = time
            current.append(text.trim())
            end = time + (syllable.duration ?: 0L)
            if (text.last().isWhitespace()) {
                words += LyricWord(start, end, current.toString())
                current.setLength(0)
            }
        }
        if (current.isNotEmpty()) words += LyricWord(start, end, current.toString())
        return words
    }

    @Serializable
    internal data class Response(
        val type: String? = null,
        val lyrics: List<Line>? = null,
        val metadata: Metadata? = null,
    ) {
        /**
         * The declared voices, keyed the way the lines refer to them — by alias
         * where one is given, which is what a line's `singer` actually holds.
         */
        fun agentTypes(): Map<String, String> = buildMap {
            metadata?.agents?.forEach { (id, agent) ->
                val type = agent.type ?: return@forEach
                put(agent.alias ?: id, type)
            }
        }
    }

    @Serializable
    internal data class Metadata(
        val agents: Map<String, Agent>? = null,
    )

    @Serializable
    internal data class Agent(
        val type: String? = null,
        val alias: String? = null,
    )

    @Serializable
    internal data class Line(
        val time: Long? = null,
        val duration: Long? = null,
        val text: String? = null,
        @SerialName("syllabus") val syllabus: List<Syllable>? = null,
        /** Raw: its shape differs between API versions. See [singerOf]. */
        val element: JsonElement? = null,
    )

    @Serializable
    internal data class Syllable(
        val time: Long? = null,
        val duration: Long? = null,
        val text: String? = null,
    )
}
