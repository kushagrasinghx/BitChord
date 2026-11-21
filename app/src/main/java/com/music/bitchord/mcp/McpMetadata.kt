package com.music.bitchord.mcp

import android.content.Context
import com.music.bitchord.data.SongInfo
import com.music.bitchord.data.SongInfoRepository
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.*
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.stats.ArtistFacts
import com.music.bitchord.playback.smart.AnalysisStore
import com.music.bitchord.playback.smart.TrackAnalysis
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*

/** Read-only metadata. Never starts audio analysis or exposes local/stream URLs. */
object McpMetadata {
    internal val allowedFields = setOf("video_id", "title", "artist", "album", "duration", "artist_id", "artists", "album_id", "explicit", "is_video", "source", "genres", "year", "release_date", "views", "likes", "comments", "analysis", "lyrics", "track_number", "radio_name", "queue_tier", "queue_entry_id", "playback_source", "playback_source_id", "download_format")
    private val infoFields = setOf("year", "release_date", "views", "likes", "comments")
    private val defaultFields = listOf("video_id", "title", "artist", "album", "duration")
    private val gate = Semaphore(3)
    private data class Cached<T>(val at: Long, val value: T)
    private val cache = LinkedHashMap<String, Cached<Any>>()
    private suspend fun <T : Any> cached(key: String, load: suspend () -> T): T {
        @Suppress("UNCHECKED_CAST")
        synchronized(cache) { cache[key]?.takeIf { System.nanoTime() - it.at < 300_000_000_000L }?.let { return it.value as T } }
        return gate.withPermit {
            val value = load()
            synchronized(cache) {
                if (cache.size >= 128) cache.remove(cache.keys.first())
                cache[key] = Cached(System.nanoTime(), value)
            }
            value
        }
    }
    internal fun select(fields: List<String>): Set<String> {
        require(fields.size <= 32) { "At most 32 fields" }
        require(fields.all { it in allowedFields }) { "Unsupported fields: ${fields.filterNot { it in allowedFields }}" }
        return (fields.ifEmpty { defaultFields }).toSet()
    }
    fun basic(song: Song): JsonObject = buildJsonObject {
        put("video_id", song.videoId); put("title", song.title); put("artist", song.artist)
        song.albumName?.let { put("album", it) }; song.durationText?.let { put("duration", it) }
    }
    fun summary(song: Song): JsonObject = buildJsonObject {
        basic(song).forEach { (k, v) -> put(k, v) }
        song.artistId?.let { put("artist_id", it) }; song.albumId?.let { put("album_id", it) }
        putJsonArray("artists") { song.artists.forEach { a -> add(buildJsonObject { put("name", a.name); a.browseId?.let { put("id", it) } }) } }
        song.isExplicit?.let { put("explicit", it) }; put("is_video", song.isVideo)
        putJsonObject("source") {
            put("local", song.localUri != null); put("video_origin", song.isVideoOrigin)
            song.sourceQuality?.let { put("quality", it) }; put("hi_res", song.sourceHiRes)
        }
        put("genres", JsonArray(ArtistFacts.genresFor(song.artist).map(::JsonPrimitive)))
    }
    suspend fun details(context: Context, id: String, fields: List<String>): JsonObject {
        val selected = select(fields)
        require(Regex("[A-Za-z0-9_-]{11}").matches(id)) { "Invalid video_id" }
        return withTimeoutOrNull(11_000) {
            val song = withTimeoutOrNull(3_000) { cached("track:$id") { YtMusicRepository.trackLinks(id).getOrThrow() } } ?: return@withTimeoutOrNull null
            project(context, song, selected)
        } ?: buildJsonObject {
            selected.forEach { put(it, JsonNull) }
            put("status", "partial")
        }
    }
    private suspend fun info(song: Song): SongInfo = cached("info:${song.videoId}") { SongInfoRepository.load(song) }
    private suspend fun project(context: Context, song: Song, fields: Set<String>): JsonObject {
        val values = summary(song).toMutableMap()
        song.trackNumber?.let { values["track_number"] = JsonPrimitive(it) }
        song.radioName?.let { values["radio_name"] = JsonPrimitive(it) }
        values["queue_tier"] = JsonPrimitive(song.queueTier.name.lowercase())
        song.queueEntryId?.let { values["queue_entry_id"] = JsonPrimitive(it) }
        song.playbackSource?.let { values["playback_source"] = JsonPrimitive(it) }
        song.playbackSourceId?.let { values["playback_source_id"] = JsonPrimitive(it) }
        song.downloadFormat?.let { values["download_format"] = JsonPrimitive(it) }
        if (fields.any { it in infoFields }) {
            val i = withTimeoutOrNull(3_000) { info(song) } ?: SongInfo(failed = true)
            i.releaseYear?.let { values["year"] = JsonPrimitive(it) }
            i.releaseDate?.let { values["release_date"] = JsonPrimitive(it) }
            i.views?.let { values["views"] = JsonPrimitive(it) }; i.likes?.let { values["likes"] = JsonPrimitive(it) }
            i.comments?.let { values["comments"] = JsonPrimitive(it) }
        }
        if ("analysis" in fields) {
            val a = AnalysisStore { context.filesDir }.load(song.videoId) ?: TrackAnalysis(trackId = song.videoId)
            values["analysis"] = buildJsonObject {
                put("status", a.status.ifBlank { "unknown" })
                if (a.bpm.isFinite() && a.bpm > 0) { put("bpm", a.bpm); put("beat_confidence", a.beatConfidence) }
                if (a.key.isNotBlank()) { put("key", a.key); put("key_confidence", a.keyConfidence) }
            }
        }
        if ("lyrics" in fields) {
            // Respect the user's enabled providers and priority; never cache across preference changes.
            val lyrics = gate.withPermit { withTimeoutOrNull(3_000) {
                LyricsRepository.lyrics(song.videoId, song.title, song.artist, (duration(song.durationText) ?: 0L) * 1000,
                    album = song.albumName, sources = AppSettings.lyricsSources.value,
                    order = AppSettings.lyricsSourceOrder.value, prioritizeSyllableSync = AppSettings.prioritizeSyllableSync.value)
            } }
            values["lyrics"] = lyrics?.let { r -> buildJsonObject {
                put("source", r.source.name); put("truncated", r.lines.size > 200 || r.lines.any { it.text.length > 500 })
                putJsonArray("lines") { r.lines.take(200).forEach { l -> add(buildJsonObject { put("time_ms", l.timeMs); put("text", l.text.take(500)) }) } }
            } } ?: JsonNull
        }
        return buildJsonObject {
            fields.forEach { put(it, values[it] ?: JsonNull) }
            put("status", if (fields.any { values[it] == null || values[it] == JsonNull }) "partial" else "complete")
        }
    }
    internal fun duration(text: String?): Long? {
        val parts = text?.split(':') ?: return null
        if (parts.size !in 2..3) return null
        val n = parts.map { it.toLongOrNull()?.takeIf { v -> v in 0..9999 } ?: return null }
        if (n.drop(1).any { it >= 60 }) return null
        return n.fold(0L) { a, b -> a * 60 + b }
    }
    private val criteriaKeys = setOf("artist", "title", "album", "explicit", "is_video", "duration_min", "duration_max", "genre", "year", "year_min", "year_max", "include_artists", "exclude_artists", "include_genres", "exclude_genres")
    internal fun validateCriteria(c: JsonObject) {
        require(c.keys.all { it in criteriaKeys }) { "Unsupported criteria: ${c.keys - criteriaKeys}" }
        c.forEach { (k, v) ->
            if (k in setOf("include_artists", "exclude_artists", "include_genres", "exclude_genres")) {
                require(v is JsonArray && v.size in 1..20 && v.all { it is JsonPrimitive && it.isString && it.content.isNotBlank() && it.content.length <= 256 }) { "Invalid criterion: $k" }
                return@forEach
            }
            val p = v as? JsonPrimitive ?: throw IllegalArgumentException("Invalid criterion: $k")
            require(when (k) {
                "explicit", "is_video" -> !p.isString && p.booleanOrNull != null
                "duration_min", "duration_max" -> !p.isString && p.longOrNull?.let { it in 0..86400 } == true
                "year", "year_min", "year_max" -> !p.isString && p.intOrNull?.let { it in 1000..2999 } == true
                else -> p.isString && p.content.isNotBlank() && p.content.length <= 256
            }) { "Invalid criterion: $k" }
        }
        require((c["year_min"]?.jsonPrimitive?.intOrNull ?: 1000) <= (c["year_max"]?.jsonPrimitive?.intOrNull ?: 2999)) { "Invalid year range" }
        require((c["duration_min"]?.jsonPrimitive?.longOrNull ?: 0) <= (c["duration_max"]?.jsonPrimitive?.longOrNull ?: 86400)) { "Invalid duration range" }
    }
    /** null is unknown, not a match. Called by production and unit tests. */
    internal fun match(song: Song, key: String, value: JsonPrimitive, year: String? = null): Boolean? = when (key) {
        "artist" -> song.artist.contains(value.content, true)
        "title" -> song.title.contains(value.content, true)
        "album" -> song.albumName?.contains(value.content, true)
        "explicit" -> song.isExplicit?.let { it == value.boolean }
        "is_video" -> song.isVideo == value.boolean
        "duration_min" -> duration(song.durationText)?.let { it >= value.long }
        "duration_max" -> duration(song.durationText)?.let { it <= value.long }
        "genre" -> ArtistFacts.genresFor(song.artist).takeIf { it.isNotEmpty() }?.any { it.equals(value.content, true) }
        "year" -> year?.toIntOrNull()?.let { it == value.int }
        "year_min" -> year?.toIntOrNull()?.let { it >= value.int }
        "year_max" -> year?.toIntOrNull()?.let { it <= value.int }
        else -> null
    }
    internal fun matchCriterion(song: Song, key: String, value: JsonElement, year: String? = null): Boolean? {
        if (value !is JsonArray) return match(song, key, value.jsonPrimitive, year)
        val genres = key.endsWith("genres")
        val names = if (genres) ArtistFacts.genresFor(song.artist) else
            (song.artists.map { it.name } + song.artist).filter { it.isNotBlank() }
        if (names.isEmpty()) return null
        val hit = value.any { v -> names.any { it.equals(v.jsonPrimitive.content, true) } }
        return if (key.startsWith("exclude_")) !hit else hit
    }
    suspend fun search(context: Context, args: JsonObject): JsonObject =
        withTimeoutOrNull(11_000) { searchWithinBudget(context, args) } ?: buildJsonObject {
            put("results", JsonArray(emptyList())); put("status", "partial"); put("reason", "time_budget")
        }
    private suspend fun searchWithinBudget(context: Context, args: JsonObject): JsonObject {
        val query = (args["query"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
        require(query.isNotEmpty() && query.length <= 300) { "Invalid query" }
        val criteria = args["criteria"]?.let { it as? JsonObject ?: error("criteria must be an object") } ?: buildJsonObject { }
        validateCriteria(criteria)
        val fields = select(args["fields"]?.let { raw ->
            require(raw is JsonArray && raw.all { it is JsonPrimitive && it.isString }) { "fields must be a string array" }
            raw.map { it.jsonPrimitive.content }
        } ?: emptyList())
        val limit = args["limit"]?.let { (it as? JsonPrimitive)?.intOrNull ?: error("Invalid limit") } ?: 10
        require(limit in 1..20) { "limit must be 1..20" }
        val filter = if (criteria["is_video"]?.jsonPrimitive?.booleanOrNull == true) SearchFilter.VIDEOS else SearchFilter.SONGS
        val candidates = cached("search:$filter:$query") { YtMusicRepository.search(query, filter).getOrThrow() }
            .mapNotNull { when (it) { is SearchResult.Track -> it.song; is SearchResult.TopTrack -> it.song; else -> null } }.distinctBy { it.videoId }.take(40)
        val unknown = mutableMapOf<String, Int>()
        val matches = mutableListOf<Song>()
        var enriched = 0
        for (song in candidates) {
            currentCoroutineContext().ensureActive()
            // Cheap predicates first; only ten surviving candidates may request year metadata.
            val cheap = criteria.filterKeys { it !in setOf("year", "year_min", "year_max") }.mapValues { (k, v) -> matchCriterion(song, k, v) }
            cheap.filterValues { it == null }.keys.forEach { unknown[it] = (unknown[it] ?: 0) + 1 }
            if (cheap.values.any { it != true }) continue
            if (criteria.keys.any { it in setOf("year", "year_min", "year_max") }) {
                val year = if (enriched < 10) { enriched++; withTimeoutOrNull(1_000) { info(song).releaseYear } } else null
                val m = criteria.filterKeys { it in setOf("year", "year_min", "year_max") }.all { (k, v) -> matchCriterion(song, k, v, year) == true }
                if (year == null) unknown["year"] = (unknown["year"] ?: 0) + 1
                if (m != true) continue
            }
            matches += song
            if (matches.size >= limit) break
        }
        val results = matches.take(limit).map { project(context, it, fields) }
        return buildJsonObject {
            put("results", JsonArray(results)); put("scanned", candidates.size); put("candidate_limit", 40)
            put("result_limit", limit); put("year_enriched", enriched); put("exhaustive", false)
            put("scan_limited", candidates.size == 40); put("truncated", matches.size > limit)
            putJsonObject("unknown_criteria") { unknown.forEach { (k, v) -> put(k, v) } }
        }
    }
}
