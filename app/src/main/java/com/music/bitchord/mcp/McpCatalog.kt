package com.music.bitchord.mcp

import android.content.Context
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.*
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import java.util.UUID

/** Read-only bounded catalogue. Caller must supply an auth/session-specific scope on every call. */
object McpCatalog {
    private val browseFields = setOf("id", "type", "title", "subtitle")
    private data class Snapshot(val scope: String, val request: JsonObject, val at: Long, val rows: List<JsonObject>)
    private val snapshots = LinkedHashMap<String, Snapshot>()
    internal fun type(args: JsonObject): String {
        val t = (args["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        require(t in setOf("song", "album", "artist", "playlist")) { "type must be song|album|artist|playlist" }
        return t!!
    }
    internal fun fields(type: String, args: JsonObject): Set<String> {
        val requested = args["fields"]?.let { raw ->
            require(raw is JsonArray && raw.all { it is JsonPrimitive && it.isString }) { "fields must be a string array" }
            raw.map { it.jsonPrimitive.content }
        } ?: emptyList()
        if (type == "song") return McpMetadata.select(requested)
        require(requested.size <= 32 && requested.all { it in browseFields }) { "Unsupported catalog fields" }
        return requested.ifEmpty { listOf("id", "type", "title", "subtitle") }.toSet()
    }
    internal fun number(args: JsonObject, key: String, default: Int, range: IntRange): Int {
        val n = args[key]?.let { raw ->
            val p = raw as? JsonPrimitive
            require(p != null && !p.isString && p.intOrNull != null) { "Invalid $key" }
            p.int
        } ?: default
        require(n in range) { "$key must be ${range.first}..${range.last}" }
        return n
    }
    internal fun validateReadId(type: String, id: String) {
        require(when (type) {
            "song" -> Regex("[A-Za-z0-9_-]{11}").matches(id)
            "album" -> Regex("MPREb[A-Za-z0-9_-]{1,180}").matches(id)
            "artist" -> Regex("UC[A-Za-z0-9_-]{22}").matches(id)
            else -> false
        }) { "Unsupported type/id; playlist reads require playlist permission tools" }
    }
    private fun page(rows: List<JsonObject>, limit: Int, scope: String, request: JsonObject): JsonObject {
        val remaining = rows.drop(limit)
        val cursor = if (remaining.isEmpty()) null else UUID.randomUUID().toString().also { token ->
            synchronized(snapshots) {
                val now = System.nanoTime()
                snapshots.entries.removeAll { now - it.value.at >= 60_000_000_000L }
                if (snapshots.size >= 32) snapshots.remove(snapshots.keys.first())
                snapshots[token] = Snapshot(scope, request, now, remaining)
            }
        }
        return buildJsonObject {
            put("results", JsonArray(rows.take(limit))); put("exhaustive", false)
            put("pagination", "bounded_snapshot"); put("snapshot_limit", 40); put("cursor_ttl_seconds", 60)
            put("next_cursor", cursor?.let(::JsonPrimitive) ?: JsonNull)
        }
    }
    /** scope must change on MCP token rotation, account/channel change, or permission changes. */
    suspend fun search(context: Context, args: JsonObject, scope: String): JsonObject {
        require(scope.isNotBlank()) { "Missing auth/session scope" }
        val type = type(args)
        val selected = fields(type, args)
        val limit = number(args, "limit", 10, 1..20)
        val query = (args["query"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim().orEmpty()
        require(query.isNotEmpty() && query.length <= 300) { "Invalid query" }
        val criteria = args["criteria"]?.let { it as? JsonObject ?: throw IllegalArgumentException("criteria must be an object") } ?: buildJsonObject {}
        if (type == "song") McpMetadata.validateCriteria(criteria)
        else require(criteria.isEmpty()) { "Criteria are supported only for songs" }
        val request = JsonObject(args.filterKeys { it != "cursor" })
        args["cursor"]?.let { raw ->
            val token = (raw as? JsonPrimitive)?.takeIf { it.isString }?.content
            require(token != null && token.length <= 64) { "Invalid cursor" }
            val snapshot = synchronized(snapshots) {
                val found = snapshots[token]
                require(found != null && found.scope == scope && found.request == request && System.nanoTime() - found.at < 60_000_000_000L) { "Expired or mismatched cursor" }
                snapshots.remove(token)!!
            }
            return page(snapshot.rows, limit, scope, request)
        }
        if (type == "song") {
            // Preserve the metadata helper's ten-enrichment/11-second budget and unknown reporting.
            // This helper stops at its result limit; do not fabricate a continuation.
            val result = McpMetadata.search(context, JsonObject(args.filterKeys { it !in setOf("type", "cursor") }))
            return JsonObject(result + mapOf("next_cursor" to JsonNull, "exhaustive" to JsonPrimitive(false)))
        }
        val filter = when (type) { "album" -> SearchFilter.ALBUMS; "artist" -> SearchFilter.ARTISTS; else -> SearchFilter.PLAYLISTS }
        val expected = when (type) { "album" -> BrowseType.ALBUM; "artist" -> BrowseType.ARTIST; else -> BrowseType.PLAYLIST }
        val rows = withTimeout(11_000) { YtMusicRepository.searchPage(query, filter, anonymous = true).getOrThrow().rows }
            .mapNotNull { (it as? SearchResult.Browse)?.item }.filter { it.type == expected }.distinctBy { it.browseId }.take(40)
            .map { item ->
                val values = buildJsonObject { put("id", item.browseId); put("type", type); put("title", item.title); put("subtitle", item.subtitle) }
                JsonObject(values.filterKeys { it in selected })
            }
        return page(rows, limit, scope, request)
    }
    suspend fun read(context: Context, args: JsonObject): JsonObject = withTimeout(11_000) {
        val type = type(args)
        val id = (args["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
        validateReadId(type, id)
        val limit = number(args, "limit", 10, 1..20)
        val offset = number(args, "offset", 0, 0..200)
        if (type == "song") {
            require(offset == 0) { "song offset is unsupported" }
            return@withTimeout McpMetadata.details(context, id, fields(type, args).toList())
        }
        require("fields" !in args) { "fields supported only for song detail" }
        val songs = if (type == "album") YtMusicRepository.browseSongs(id).getOrThrow().songs
            else YtMusicRepository.artistLanding(id).getOrThrow().songs
        buildJsonObject {
            put("id", id); put("type", type); put("offset", offset)
            put("songs", JsonArray(songs.drop(offset).take(limit).map(McpMetadata::basic)))
            put("exhaustive", false); put("pagination", "first_upstream_page_only")
            put("has_more_in_page", songs.size > offset + limit)
        }
    }
}
