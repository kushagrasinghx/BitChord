package com.music.bitchord.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/** Public Apple lyrics plus optional authenticated Spotify and Musixmatch routes. */
object PaxSenix {
    private const val API = "https://api.paxsenix.org"
    private const val PUBLIC_PROXY = "https://lyrics.paxsenix.org"
    private const val APPLE_SEARCH = "https://amp-api.music.apple.com/v1/catalog/us/search"
    private const val MINIMUM_MATCH_SCORE = 10

    private val tokenMutex = Mutex()
    private val cachedAppleToken = AtomicReference<String?>(null)

    @Volatile
    private var apiKey: String = ""

    fun setApiKey(value: String) {
        apiKey = normalizePaxSenixApiKey(value)
    }

    /** The original keyless Apple Music provider used by the existing source id. */
    suspend fun lyrics(
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
    ): List<LyricLine>? = withContext(Dispatchers.IO) {
        val id = searchPublicAppleTrackId(title, artist, durationMs)
            ?: return@withContext null
        val url = publicUrl("apple-music/lyrics")
            .addQueryParameter("id", id)
            .addQueryParameter("ttml", "true")
            .build()
        lyricsGet(url.toString())
            ?.let(::parseResponse)
    }

    suspend fun spotifyLyrics(
        title: String,
        artist: String,
        durationMs: Long,
    ): List<LyricLine>? = withContext(Dispatchers.IO) {
        val id = searchTrackId("spotify/search", title, artist, durationMs)
        id?.let {
            apiBody(apiUrl("lyrics/spotify").addQueryParameter("id", it).build())
                ?.let(::parseResponse)
        } ?: genericAuthenticatedLyrics(title, artist, durationMs)
    }

    suspend fun musixmatchLyrics(
        title: String,
        artist: String,
        durationMs: Long,
    ): List<LyricLine>? = withContext(Dispatchers.IO) {
        val query: HttpUrl.Builder.() -> Unit = {
            addQueryParameter("t", title)
            addQueryParameter("a", artist)
            addQueryParameter("d", (durationMs / 1000).toString())
        }
        apiBody(apiUrl("lyrics/musixmatch").apply(query).build())
            ?.let(::parseResponse)
            ?: genericAuthenticatedLyrics(title, artist, durationMs)
    }

    private suspend fun searchPublicAppleTrackId(
        title: String,
        artist: String,
        durationMs: Long,
    ): String? {
        val token = appleToken() ?: return null
        val url = APPLE_SEARCH.toHttpUrl().newBuilder()
            .addQueryParameter("term", "$title $artist")
            .addQueryParameter("types", "songs")
            .addQueryParameter("limit", "10")
            .addQueryParameter("l", "en-US")
            .build()
        val root = lyricsGetAuthorized(url.toString(), token)
            ?.let { runCatching { lyricsJson.parseToJsonElement(it) }.getOrNull() }
            ?: return null
        return bestCandidate(root, title, artist, durationMs)?.id
    }

    private suspend fun appleToken(): String? = cachedAppleToken.get() ?: tokenMutex.withLock {
        cachedAppleToken.get() ?: scrapeAppleToken()?.also(cachedAppleToken::set)
    }

    private fun scrapeAppleToken(): String? {
        val page = lyricsGet("https://music.apple.com/us/new") ?: return null
        val scriptPath = APPLE_INDEX_SCRIPT.find(page)?.value ?: return null
        val script = lyricsGet("https://music.apple.com$scriptPath") ?: return null
        return APPLE_TOKEN.find(script)?.value
    }

    private fun searchTrackId(
        path: String,
        title: String,
        artist: String,
        durationMs: Long,
    ): String? {
        val query: HttpUrl.Builder.() -> Unit = {
            addQueryParameter("q", "$title $artist")
        }
        val root = apiBody(apiUrl(path).apply(query).build())
            ?.let { runCatching { lyricsJson.parseToJsonElement(it) }.getOrNull() }
            ?: return null
        return bestCandidate(root, title, artist, durationMs)?.id
    }

    private fun genericAuthenticatedLyrics(
        title: String,
        artist: String,
        durationMs: Long,
    ): List<LyricLine>? {
        val url = apiUrl("lyrics/lrcget")
            .addQueryParameter("q", "$title $artist")
            .build()
        return apiBody(url)?.let { parseLrcGet(it, title, artist, durationMs) }
    }

    /**
     * The general endpoint returns search candidates, not lyric lines. Parsing
     * its array as one document concatenates every candidate and makes each
     * song's timestamps restart at zero, which appears as repeated lines that
     * cannot stay in sync. Select one recording before parsing its lyrics.
     */
    internal fun parseLrcGet(
        raw: String,
        title: String,
        artist: String,
        durationMs: Long,
    ): List<LyricLine>? {
        val root = runCatching { lyricsJson.parseToJsonElement(raw) }.getOrNull()
            ?: return null
        val payload = (root as? JsonObject)?.get("lyrics")
        val documents = (payload as? JsonArray).orEmpty()
        if (documents.isEmpty()) return parseResponse(raw)

        return documents.mapNotNull { document ->
            val lines = parseResponse(document.toString()) ?: return@mapNotNull null
            val metadataScore = (document as? JsonObject)
                ?.lyricCandidateScore(title, artist, durationMs) ?: 0
            ParsedDocument(lines, metadataScore, durationDistance(lines, durationMs))
        }.maxWithOrNull(
            compareBy<ParsedDocument> { it.metadataScore }
                .thenBy { -it.durationDistanceMs }
                .thenBy { document -> document.lines.count { it.timeMs > 0L } }
                .thenBy { document -> document.lines.count { it.isWordSynced } },
        )?.lines
    }

    private fun durationDistance(lines: List<LyricLine>, durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        val lastTimestamp = lines.maxOfOrNull { line ->
            maxOf(
                line.timeMs,
                line.sungUntilMs ?: 0L,
                line.words.maxOfOrNull(LyricWord::endMs) ?: 0L,
            )
        } ?: return Long.MAX_VALUE
        return abs(lastTimestamp - durationMs)
    }

    private fun JsonObject.lyricCandidateScore(
        title: String,
        artist: String,
        durationMs: Long,
    ): Int {
        val details = this["attributes"] as? JsonObject ?: this
        val candidate = Candidate(
            id = firstString(ID_KEYS) ?: "",
            title = details.firstString(TITLE_KEYS).orEmpty(),
            artist = details.firstString(ARTIST_KEYS) ?: details.artistNames().orEmpty(),
            durationMs = details.firstLong(DURATION_KEYS).toDurationMs(),
        )
        return candidate.score(title, artist, durationMs)
    }

    private fun bestCandidate(
        root: JsonElement,
        title: String,
        artist: String,
        durationMs: Long,
    ): Candidate? {
        val candidates = buildList { root.collectCandidates(this) }
        return candidates.map { it to it.score(title, artist, durationMs) }
            .maxByOrNull { it.second }
            ?.takeIf { it.second >= MINIMUM_MATCH_SCORE }
            ?.first
    }

    private fun parseResponse(raw: String): List<LyricLine>? =
        parseTimedApple(raw) ?: ProviderLyrics.parse(raw)

    /** Preserve word timestamps when PaxSenix returns its structured Apple payload. */
    internal fun parseTimedApple(raw: String): List<LyricLine>? {
        val root = runCatching { lyricsJson.parseToJsonElement(raw) }.getOrNull() ?: return null
        val content = root.findTimedContent() ?: return null
        val rows = content.mapNotNull { it as? JsonObject }
        val lines = rows.mapIndexedNotNull { index, row ->
            val start = row.long("timestamp") ?: return@mapIndexedNotNull null
            val wordRows = row["text"] as? JsonArray ?: return@mapIndexedNotNull null
            val texts = wordRows.mapNotNull { (it as? JsonObject)?.string("text") }
            if (texts.isEmpty()) return@mapIndexedNotNull null
            val nextLine = rows.getOrNull(index + 1)?.long("timestamp")
            val timed = wordRows.mapIndexedNotNull { wordIndex, element ->
                val word = element as? JsonObject ?: return@mapIndexedNotNull null
                val text = word.string("text")?.trim()?.takeIf(String::isNotEmpty)
                    ?: return@mapIndexedNotNull null
                val wordStart = word.long("timestamp") ?: return@mapIndexedNotNull null
                val wordEnd = (wordRows.getOrNull(wordIndex + 1) as? JsonObject)?.long("timestamp")
                    ?: nextLine ?: wordStart + 800
                LyricWord(wordStart, wordEnd.coerceAtLeast(wordStart), text)
            }
            LyricLine(
                timeMs = minOf(start, timed.firstOrNull()?.startMs ?: start),
                text = texts.joinToString(" ") { it.trim() },
                words = timed.takeIf { it.size == texts.size }.orEmpty(),
                sungUntilMs = nextLine,
            )
        }
        return lines.withInstrumentalGaps().takeIf { it.any { line -> line.text.isNotBlank() } }
    }

    private fun JsonElement.findTimedContent(): JsonArray? = when (this) {
        is JsonObject -> {
            (this["content"] as? JsonArray)?.takeIf { array ->
                array.any { (it as? JsonObject)?.get("timestamp") != null }
            } ?: values.firstNotNullOfOrNull { it.findTimedContent() }
        }
        is JsonArray -> firstNotNullOfOrNull { it.findTimedContent() }
        else -> null
    }

    private fun JsonElement.collectCandidates(into: MutableList<Candidate>) {
        when (this) {
            is JsonArray -> forEach { it.collectCandidates(into) }
            is JsonObject -> {
                toCandidate()?.let(into::add)
                values.forEach { it.collectCandidates(into) }
            }
            else -> Unit
        }
    }

    private fun JsonObject.toCandidate(): Candidate? {
        val details = this["attributes"] as? JsonObject ?: this
        val id = firstString(ID_KEYS) ?: details.firstString(ID_KEYS) ?: return null
        val title = details.firstString(TITLE_KEYS) ?: return null
        val artist = details.firstString(ARTIST_KEYS) ?: details.artistNames().orEmpty()
        return Candidate(id, title, artist, details.firstLong(DURATION_KEYS).toDurationMs())
    }

    private fun JsonObject.artistNames(): String? = when (val artists = this["artists"] ?: this["artist"]) {
        is JsonPrimitive -> artists.contentOrNull
        is JsonObject -> artists.firstString(listOf("name", "artistName", "title"))
        is JsonArray -> artists.mapNotNull {
            when (it) {
                is JsonPrimitive -> it.contentOrNull
                is JsonObject -> it.firstString(listOf("name", "artistName", "title"))
                else -> null
            }
        }.joinToString(", ").takeIf(String::isNotEmpty)
        else -> null
    }

    private fun JsonObject.firstString(keys: List<String>): String? =
        keys.firstNotNullOfOrNull { string(it)?.trim()?.takeIf(String::isNotEmpty) }

    private fun JsonObject.firstLong(keys: List<String>): Long? =
        keys.firstNotNullOfOrNull { key -> (this[key] as? JsonPrimitive)?.longOrNull }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

    private fun Long?.toDurationMs(): Long = when {
        this == null || this <= 0 -> 0
        this < 10_000 -> this * 1000
        else -> this
    }

    private fun Candidate.score(wantedTitle: String, wantedArtist: String, wantedDuration: Long): Int {
        var score = textScore(title, wantedTitle, 20, 10) + textScore(artist, wantedArtist, 15, 5)
        if (wantedDuration > 0 && durationMs > 0) score += when {
            abs(durationMs - wantedDuration) < 3_000 -> 10
            abs(durationMs - wantedDuration) < 10_000 -> 5
            else -> 0
        }
        return score
    }

    private fun textScore(candidate: String, wanted: String, exact: Int, partial: Int): Int = when {
        candidate.isBlank() || wanted.isBlank() -> 0
        candidate.equals(wanted, ignoreCase = true) -> exact
        candidate.contains(wanted, ignoreCase = true) || wanted.contains(candidate, ignoreCase = true) -> partial
        else -> 0
    }

    private fun apiUrl(path: String) = "$API/$path".toHttpUrl().newBuilder()

    private fun publicUrl(path: String) = "$PUBLIC_PROXY/$path".toHttpUrl().newBuilder()

    private fun apiBody(url: HttpUrl): String? = apiKey.takeIf(String::isNotBlank)?.let { key ->
        lyricsGetBearer(url.toString(), key)
    }

    private data class Candidate(val id: String, val title: String, val artist: String, val durationMs: Long)
    private data class ParsedDocument(
        val lines: List<LyricLine>,
        val metadataScore: Int,
        val durationDistanceMs: Long,
    )

    private val ID_KEYS = listOf("id", "trackId", "track_id", "realId")
    private val TITLE_KEYS = listOf("name", "title", "trackName", "track_name")
    private val ARTIST_KEYS = listOf("artistName", "artist_name")
    private val DURATION_KEYS = listOf("durationInMillis", "durationMs", "duration_ms", "duration")

    private val APPLE_INDEX_SCRIPT = Regex("""/assets/index~[^\"]+\.js""")
    private val APPLE_TOKEN = Regex("""eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
}

internal fun normalizePaxSenixApiKey(value: String): String {
    val trimmed = value.trim()
    return if (trimmed.startsWith("Bearer ", ignoreCase = true)) {
        trimmed.substringAfter(' ').trim()
    } else {
        trimmed
    }
}
