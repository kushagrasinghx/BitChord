package com.music.bitchord.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

    suspend fun artifact(
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
    ): LyricsArtifact? = coroutineScope {
        val hosts = lastGood.get()
            ?.let { listOf(it) + MIRRORS.filterNot { mirror -> mirror == it } }
            ?: MIRRORS

        val pending = hosts.map { host ->
            host to async(Dispatchers.IO) { fetch(host, title, artist, durationMs, album) }
        }.toMutableList()

        // Take the first mirror to answer with something usable rather than
        // the first to answer at all — a mirror that 404s this track shouldn't
        // beat one that has it.
        try {
            while (pending.isNotEmpty()) {
                val (host, artifact) = select {
                    pending.forEach { (host, job) -> job.onAwait { host to it } }
                }
                pending.removeAll { it.first == host }
                if (artifact != null) {
                    lastGood.set(host)
                    return@coroutineScope artifact
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
    ): LyricsArtifact? = withContext(Dispatchers.IO) {
        val url = "$host/v2/lyrics/get".toHttpUrl().newBuilder()
            .addQueryParameter("title", title)
            .addQueryParameter("artist", artist)
            .apply {
                val seconds = durationMs / 1000
                if (seconds > 0) addQueryParameter("duration", seconds.toString())
                if (!album.isNullOrBlank()) addQueryParameter("album", album)
            }
            .build()

        val body = lyricsGet(url.toString()) ?: return@withContext null
        val response = runCatching { lyricsJson.decodeFromString<Response>(body) }.getOrNull()
            ?: return@withContext null
        val lines = parse(response).takeIf { it.isNotEmpty() } ?: return@withContext null
        LyricsSerializer.fromLines(LyricsSource.LYRICS_PLUS, lines)
    }

    suspend fun lyrics(
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
    ): List<LyricLine>? = artifact(title, artist, durationMs, album)?.lines

    internal fun parse(response: Response): List<LyricLine> =
        response.lyrics.orEmpty().mapNotNull { line ->
            val start = line.time ?: return@mapNotNull null
            val words = mergeSyllables(line.syllabus.orEmpty())
            when {
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
        }.sortedBy { it.timeMs }.withInstrumentalGaps()

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
    )

    @Serializable
    internal data class Line(
        val time: Long? = null,
        val duration: Long? = null,
        val text: String? = null,
        @SerialName("syllabus") val syllabus: List<Syllable>? = null,
    )

    @Serializable
    internal data class Syllable(
        val time: Long? = null,
        val duration: Long? = null,
        val text: String? = null,
    )
}
