package com.music.bitchord.mcp

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.download.Downloads
import com.music.bitchord.playback.PlaybackService
import com.music.bitchord.playback.playSongs
import com.music.bitchord.playback.toSong
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*

/** Deliberately accepts catalogue IDs, never stream URLs, paths or client metadata. */
object McpTools {
    private val songCache = CatalogueCache<String, Song>(200, 15 * 60_000L)
    private val historyCache = CatalogueCache<String, JsonObject>(4, 5_000L)
    private val searchCache = CatalogueCache<String, List<Song>>(20, 5 * 60_000L)

    private data class Tool(val name: String, val permission: String, val description: String,
                            val fields: Map<String, String> = emptyMap(), val required: List<String> = emptyList())
    private val tools = listOf(
        Tool("now_playing", "now_playing", "Current track and playback position"),
        Tool("listening_history", "history", "Device-local persisted listening totals per track, sorted by last listened time; not a per-play event log", mapOf("limit" to "integer")),
        Tool("account_history", "history", "YouTube Music account history; deduplicated, no timestamps", mapOf("limit" to "integer")),
        Tool("current_queue", "queue", "Current queue with previous tracks retained by the player", mapOf("offset" to "integer", "limit" to "integer")),
        Tool("play", "playback", "Play a YouTube catalogue video ID, replacing the queue; omit ID to resume", mapOf("video_id" to "string")),
        Tool("pause", "playback", "Pause playback"),
        Tool("resume", "playback", "Resume the current queue"),
        Tool("seek", "seek", "Seek within the current track in milliseconds", mapOf("position_ms" to "integer"), listOf("position_ms")),
        Tool("search", "search", "Search YouTube Music songs", mapOf("query" to "string", "limit" to "integer"), listOf("query")),
        Tool("playlist_list", "playlist_read", "List only explicitly allowed local playlists"),
        Tool("playlist_read", "playlist_read", "Read an explicitly allowed local playlist", mapOf("playlist_id" to "string", "offset" to "integer", "limit" to "integer"), listOf("playlist_id")),
        Tool("playlist_create", "playlist_create", "Create an empty local playlist and automatically grant playlist access; global read/write permissions still apply", mapOf("title" to "string"), listOf("title")),
        Tool("playlist_add", "playlist_write", "Append a YouTube catalogue track to an allowed local playlist", mapOf("playlist_id" to "string", "video_id" to "string"), listOf("playlist_id", "video_id")),
        Tool("playlist_remove", "playlist_write", "Remove one occurrence by zero-based index from an allowed local playlist", mapOf("playlist_id" to "string", "index" to "integer"), listOf("playlist_id", "index")),
    )

    fun listTools(context: Context): JsonArray = buildJsonArray {
        tools.filter { McpSettings.allowed(it.permission) }.forEach { tool ->
            add(buildJsonObject {
                put("name", tool.name)
                put("description", tool.description)
                putJsonObject("inputSchema") {
                    put("type", "object")
                    put("additionalProperties", false)
                    putJsonObject("properties") {
                        tool.fields.forEach { (name, type) -> putJsonObject(name) { put("type", type) } }
                    }
                    put("required", JsonArray(tool.required.map(::JsonPrimitive)))
                }
            })
        }
    }

    /** MCP CallToolResult envelope; transport must not wrap a second time. */
    suspend fun call(context: Context, name: String, args: JsonObject): JsonElement {
        var result = callRaw(context, name, args)
        // Recheck after every suspended read, before any metadata reaches the client.
        val tool = tools.firstOrNull { it.name == name }
        if (tool != null && !McpSettings.allowed(tool.permission)) {
            result = error("permission_denied", "Permission was revoked")
        }
        if (name in setOf("playlist_read", "playlist_add", "playlist_remove")) {
            val id = (args["playlist_id"] as? JsonPrimitive)?.content
            if (id != null && !McpSettings.playlistAllowed(id))
                result = error("permission_denied", "Playlist permission was revoked")
        }
        if (name == "playlist_list" && result is JsonObject) {
            val rows = result["playlists"] as? JsonArray
            if (rows != null) result = buildJsonObject {
                put("playlists", JsonArray(rows.filter { row ->
                    val id = ((row as? JsonObject)?.get("id") as? JsonPrimitive)?.content
                    id != null && McpSettings.playlistAllowed(id)
                }))
            }
        }
        return buildJsonObject {
            put("isError", result is JsonObject && "error" in result)
            putJsonArray("content") {
                add(buildJsonObject { put("type", "text"); put("text", result.toString()) })
            }
        }
    }

    private suspend fun callRaw(context: Context, name: String, args: JsonObject): JsonElement {
        val tool = tools.firstOrNull { it.name == name } ?: return error("unknown_tool", "Unknown tool")
        // Never rely on discovery filtering: permissions can change while a client stays connected.
        if (!McpSettings.allowed(tool.permission)) return error("permission_denied", "Permission disabled: ${tool.permission}")
        return try {
            validateToolArguments(args, tool.fields, tool.required)
            currentCoroutineContext().ensureActive()
            withTimeout(20_000) {
                withContext(Dispatchers.IO) {
                    when (name) {
                        "search" -> {
                            val query = args.text("query", 300)
                            val limit = args.limit()
                            var cached = false
                            val songs = try {
                                YtMusicRepository.search(query, SearchFilter.SONGS).getOrThrow()
                                    .filterIsInstance<SearchResult.Track>().map { it.song }.take(200).also { rows ->
                                        currentCoroutineContext().ensureActive()
                                        checkPermission(tool.permission)
                                        searchCache.put(query, rows)
                                        rows.forEach { songCache.put(it.videoId, it) }
                                    }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                if (!e.isCatalogueDnsFailure()) throw e
                                searchCache.get(query)?.also { cached = true } ?: throw e
                            }
                            buildJsonObject { put("songs", songsJson(songs.take(limit))); put("cached", cached) }
                        }
                        "listening_history" -> {
                            historyCache.get("local:${args.limit()}")?.let { return@withContext it }
                            val tracks = ListeningStats.exportAll().flatMap { it.tracks }.groupBy { it.id }
                                .values.sortedByDescending { entries -> entries.maxOf { it.last } }
                                .take(args.limit())
                            buildJsonObject {
                                put("source", "device_listening_stats")
                                put("aggregated_per_track", true)
                                put("event_log_available", false)
                                put("tracks", buildJsonArray {
                                    tracks.forEach { entries ->
                                        val newest = entries.maxByOrNull { it.last }!!
                                        add(buildJsonObject {
                                            put("track", songJson(Song(newest.id, newest.title, newest.artist, null, albumName = newest.album)))
                                            put("last_listened_at_ms", newest.last)
                                            put("listened_ms", entries.sumOf { it.ms })
                                            put("plays", entries.sumOf { it.plays })
                                        })
                                    }
                                })
                            }.also {
                                currentCoroutineContext().ensureActive()
                                checkPermission(tool.permission)
                                historyCache.put("local:${args.limit()}", it)
                            }
                        }
                        "account_history" -> buildJsonObject {
                            put("source", "youtube_music_account")
                            put("deduplicated", true)
                            put("songs", songsJson(YtMusicRepository.history().getOrThrow().take(args.limit())))
                        }
                        "playlist_list" -> buildJsonObject {
                            put("playlists", buildJsonArray {
                                LocalPlaylistStore.playlists.value.filter { McpSettings.playlistAllowed(it.id) }
                                    .forEach { p -> add(buildJsonObject {
                                        put("id", p.id); put("title", p.title); put("song_count", p.songs.size)
                                    }) }
                            })
                        }
                        "playlist_create" -> {
                            withContext(Dispatchers.Main) {
                                currentCoroutineContext().ensureActive()
                                checkPermission(tool.permission)
                                val p = LocalPlaylistStore.savePlaylist(args.text("title", 200), emptyList())
                                McpSettings.grantPlaylist(p.id)
                                buildJsonObject { put("created_id", p.id); put("requires_user_approval", false) }
                            }
                        }
                        "playlist_read", "playlist_add", "playlist_remove" -> {
                            val id = args.text("playlist_id", 200)
                            // Check before lookup: disallowed and nonexistent IDs are indistinguishable.
                            requirePlaylist(id)
                            val p = LocalPlaylistStore.getPlaylist(id)
                                ?: return@withContext error("not_found", "Allowed playlist not found")
                            when (name) {
                                "playlist_read" -> buildJsonObject {
                                    put("id", p.id); put("title", p.title); put("total", p.songs.size)
                                    put("songs", songsJson(p.songs.drop(args.offset()).take(args.limit())))
                                }
                                "playlist_add" -> {
                                    val song = catalogueSong(context, args.text("video_id", 11))
                                    withContext(Dispatchers.Main) {
                                        currentCoroutineContext().ensureActive()
                                        checkPermission(tool.permission); requirePlaylist(id)
                                        check(LocalPlaylistStore.addSong(p.id, song)) { "Playlist no longer exists" }
                                        ok()
                                    }
                                }
                                else -> {
                                    val index = args.integer("index")
                                    require(index in p.songs.indices) { "Index outside playlist" }
                                    withContext(Dispatchers.Main) {
                                        currentCoroutineContext().ensureActive()
                                        checkPermission(tool.permission); requirePlaylist(id)
                                        check(LocalPlaylistStore.removeSong(p.id, index, p.songs)) { "Playlist changed; read it again" }
                                        ok()
                                    }
                                }
                            }
                        }
                        else -> {
                            val song = if (name == "play" && "video_id" in args)
                                catalogueSong(context, args.text("video_id", 11)) else null
                            controller(context) { c ->
                                currentCoroutineContext().ensureActive()
                                checkPermission(tool.permission)
                                when (name) {
                                    "now_playing" -> buildJsonObject {
                                        put("track", c.currentMediaItem?.toSong()?.let(::songJson) ?: JsonNull)
                                        put("is_playing", c.isPlaying); put("position_ms", c.currentPosition)
                                        put("duration_ms", if (c.duration == C.TIME_UNSET) JsonNull else JsonPrimitive(c.duration))
                                    }
                                    "current_queue" -> buildJsonObject {
                                        put("total", c.mediaItemCount); put("current_index", c.currentMediaItemIndex)
                                        put("offset", args.offset())
                                        put("tracks", buildJsonArray {
                                            val end = minOf(c.mediaItemCount.toLong(), args.offset().toLong() + args.limit()).toInt()
                                            for (i in args.offset() until end) add(buildJsonObject {
                                                put("index", i); put("previous", i < c.currentMediaItemIndex)
                                                put("track", songJson(c.getMediaItemAt(i).toSong()))
                                            })
                                        })
                                    }
                                    "pause" -> { c.pause(); ok() }
                                    "play", "resume" -> {
                                        if (song != null) c.playSongs(listOf(song), 0) else {
                                            check(c.mediaItemCount > 0) { "Queue is empty" }
                                            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0L)
                                            if (c.playbackState == Player.STATE_IDLE) c.prepare()
                                            c.play()
                                        }
                                        ok()
                                    }
                                    "seek" -> {
                                        val value = args["position_ms"] as? JsonPrimitive
                                        require(value != null && !value.isString) { "position_ms must be an integer" }
                                        val position = value.longOrNull
                                            ?: throw IllegalArgumentException("position_ms must be an integer")
                                        require(position >= 0) { "Position must be nonnegative" }
                                        check(c.currentMediaItem != null) { "No current track" }
                                        require(c.duration == C.TIME_UNSET || position <= c.duration) { "Position exceeds duration" }
                                        // A restored IDLE queue has not prepared its timeline yet.
                                        // Prepare without starting playback, then submit Media3's pending seek.
                                        // Controller position is optimistic, not proof the service applied it.
                                        val wasPlayWhenReady = c.playWhenReady
                                        val wasIdle = c.playbackState == Player.STATE_IDLE
                                        if (wasIdle) {
                                            c.prepare()
                                            c.playWhenReady = wasPlayWhenReady
                                        }
                                        c.seekTo(position)
                                        buildJsonObject {
                                            put("ok", true)
                                            put("status", "pending")
                                            put("requested_position_ms", position)
                                            put("prepared_from_idle", wasIdle)
                                            put("play_when_ready", wasPlayWhenReady)
                                            put("verification", "Read now_playing after the player has prepared")
                                        }
                                    }
                                    else -> error("unknown_tool", "Unknown tool")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive() // Never swallow the transport's cancellation.
            error("timeout", "Music operation timed out")
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            error("permission_denied", "Access is not allowed")
        } catch (e: IllegalArgumentException) {
            error("invalid_arguments", e.message ?: "Invalid argument")
        } catch (e: Exception) {
            // Backend exceptions may contain URLs, account data or filesystem paths.
            if (e.isCatalogueDnsFailure()) error("network_unavailable", "Catalogue hostname could not be resolved; check network or DNS and retry")
            else error("operation_failed", "Music operation failed; check playback/account availability")
        }
    }

    private suspend fun <T> controller(context: Context, block: suspend (MediaController) -> T): T =
        withContext(Dispatchers.Main) {
            val app = context.applicationContext
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            try { block(future.await()) } finally { MediaController.releaseFuture(future) }
        }

    private fun checkPermission(permission: String) {
        if (!McpSettings.allowed(permission)) throw SecurityException("Permission revoked")
    }
    private fun requirePlaylist(id: String) {
        // Canonical IDs only: aliases must not bypass the explicit per-playlist allowlist.
        if (id.startsWith("local:playlist:") || !McpSettings.playlistAllowed(id)) throw SecurityException("Playlist not allowed")
    }
    private suspend fun catalogueSong(context: Context, id: String): Song {
        require(Regex("[A-Za-z0-9_-]{11}").matches(id)) { "Expected an 11-character YouTube video ID, not a URL or path" }
        // Only trusted app-owned metadata. Never accept playback URIs or metadata from the client.
        val queued = if (McpSettings.allowed("queue") || McpSettings.allowed("now_playing")) try {
            withTimeoutOrNull(1_000) {
                controller(context) { c ->
                    c.currentMediaItem?.takeIf { it.mediaId == id }?.toSong()
                        ?: (0 until (if (McpSettings.allowed("queue")) minOf(c.mediaItemCount, 500) else 0)).firstOrNull {
                            c.getMediaItemAt(it).mediaId == id
                        }?.let { c.getMediaItemAt(it).toSong() }
                }
            }
        } catch (e: CancellationException) { throw e }
          catch (_: Exception) { null } else null
        currentCoroutineContext().ensureActive()
        if (queued != null) return queued
        // Do not use a non-allowed playlist as a metadata oracle.
        LocalPlaylistStore.playlists.value.asSequence()
            .filter { McpSettings.allowed("playlist_read") && McpSettings.playlistAllowed(it.id) }
            .flatMap { it.songs.asSequence() }.firstOrNull { it.videoId == id }?.let { return it }
        if (McpSettings.allowed("search")) songCache.get(id)?.let { return it }
        val downloaded = try {
            withTimeoutOrNull(1_500) { Downloads.getDownloadedSongs(context).firstOrNull { it.videoId == id } }
        } catch (e: CancellationException) { throw e }
          catch (_: Exception) { null }
        if (downloaded != null) return downloaded
        return YtMusicRepository.trackLinks(id).getOrThrow().also {
            require(it.videoId == id) { "Catalogue returned a different track" }
            currentCoroutineContext().ensureActive()
            if (McpSettings.allowed("search")) songCache.put(id, it)
        }
    }
    private fun JsonObject.text(key: String, max: Int): String {
        val p = get(key) as? JsonPrimitive
        require(p != null && p.isString) { "$key must be a string" }
        return p.content.trim().also { require(it.isNotEmpty() && it.length <= max) { "Invalid $key length" } }
    }
    private fun JsonObject.integer(key: String): Int {
        val p = get(key) as? JsonPrimitive
        require(p != null && !p.isString) { "$key must be an integer" }
        return p.intOrNull ?: throw IllegalArgumentException("$key must be an integer")
    }
    private fun JsonObject.limit(): Int = if ("limit" in this) integer("limit").also {
        require(it in 1..200) { "limit must be 1..200" }
    } else 50
    private fun JsonObject.offset(): Int = if ("offset" in this) integer("offset").also {
        require(it >= 0) { "offset must be nonnegative" }
    } else 0
    private fun songJson(song: Song): JsonObject = buildJsonObject {
        put("video_id", song.videoId); put("title", song.title); put("artist", song.artist)
        song.albumName?.let { put("album", it) }
        song.durationText?.let { put("duration", it) }
        // No localUri, localPath, artwork URL, cookies or stream resolver output.
    }
    private fun songsJson(songs: List<Song>): JsonArray = JsonArray(songs.map(::songJson))
    private fun ok(): JsonObject = buildJsonObject { put("ok", true); put("status", "command_sent") }
    private fun error(code: String, message: String): JsonObject = buildJsonObject {
        putJsonObject("error") { put("code", code); put("message", message) }
    }
}
