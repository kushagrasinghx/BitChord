package com.music.bitchord.mcp

import android.content.Context
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.download.DownloadState
import com.music.bitchord.download.DownloadTarget
import com.music.bitchord.download.Downloads
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Dispatcher must route these names here; this helper also checks fresh permissions. */
object McpDownloadTools {
    val names = setOf("downloads_list", "download_enqueue", "download_status", "download_cancel", "download_delete",
        "playlist_download", "playlist_download_status", "playlist_download_cancel", "playlist_download_delete")

    internal fun validate(name: String, args: JsonObject) {
        require(name in names)
        if (name == "downloads_list") {
            validateToolArguments(args, mapOf("limit" to "integer"), emptyList())
            require((args["limit"]?.jsonPrimitive?.int ?: 20) in 1..50)
            return
        }
        val key = if (name.startsWith("playlist_")) "playlist_id" else "video_id"
        validateToolArguments(args, mapOf(key to "string"), listOf(key))
        if (key == "playlist_id") require(!args.getValue(key).jsonPrimitive.content.startsWith("local:playlist:"))
    }

    /** Exact whitelist only: arbitrary UI exception strings must never cross MCP. */
    internal fun safeFailure(reason: String): Pair<String, String> = when (reason) {
        "Downloads are set to Wi-Fi only" -> "wifi_required" to "Connect to Wi-Fi or change download settings."
        "Downloads can't start right now" -> "start_unavailable" to "Downloads cannot start right now."
        else -> "download_failed" to "Download failed. Check connection, sign-in and storage access."
    }

    internal fun exclusive(ids: List<String>, others: Set<String>, saved: Map<String, String>): List<String> {
        val files = others.mapNotNullTo(HashSet()) { saved[it] }
        return ids.filter { it !in others && (saved[it] == null || saved[it] !in files) }
    }

    private fun guard(context: Context, permission: String, playlist: String?) {
        if (!McpSettings.allowed(permission)) throw SecurityException()
        if (playlist != null) {
            if (!McpSettings.allowed("playlist_read") || !McpSettings.playlistAllowed(playlist)) throw SecurityException()
            if (McpPlaylistCatalog.isRemote(playlist)) McpPlaylistCatalog.remoteBrowseId(context, playlist)
        }
    }

    suspend fun call(context: Context, name: String, args: JsonObject): JsonObject {
        try {
            validate(name, args)
            val playlist = if (name.startsWith("playlist_")) args.getValue("playlist_id").jsonPrimitive.content else null
            val permission = when {
                name.endsWith("_delete") -> "downloads_delete"
                name == "downloads_list" || name.endsWith("_status") -> "downloads_read"
                else -> "downloads_write"
            }
            guard(context, permission, playlist)
            if (name == "downloads_list") {
                val limit = args["limit"]?.jsonPrimitive?.int ?: 20
                val ids = (Downloads.active.value.keys + Downloads.saved.value.keys).take(limit)
                val rows = ids.map { status(it) }
                guard(context, permission, null)
                return buildJsonObject { put("downloads", JsonArray(rows)) }
            }
            val id = playlist ?: args.getValue("video_id").jsonPrimitive.content
            if (name == "playlist_download") {
                val local = LocalPlaylistStore.playlists.value.firstOrNull { it.id == id }
                val songs: List<Song> = if (local != null) local.songs else {
                    if (!McpPlaylistCatalog.isRemote(id)) return error("unsupported", "Playlist source is not supported.")
                    guard(context, permission, playlist)
                    val (rows, more) = McpPlaylistCatalog.read(context, id, 0, 201)
                    if (more || rows.size > 200) return error("playlist_too_large", "At most 200 tracks can be scheduled.")
                    rows
                }
                if (songs.size > 200) return error("playlist_too_large", "At most 200 tracks can be scheduled.")
                if (songs.any { !Regex("[A-Za-z0-9_-]{11}").matches(it.videoId) })
                    return error("unsupported", "Playlist contains unsupported track identifiers.")
                withContext(Dispatchers.Main.immediate) {
                    currentCoroutineContext().ensureActive()
                    guard(context, permission, playlist)
                    Downloads.rememberCollection(DownloadTarget(id, local?.title ?: "Playlist", playlist = true), songs)
                    Downloads.markRequested(id, songs.map { it.videoId })
                    songs.distinctBy { it.videoId }.forEach {
                        guard(context, permission, playlist)
                        if (Downloads.verifiedSavedUri(it.videoId) == null) Downloads.enqueue(context, it, id)
                    }
                }
            } else if (name == "download_enqueue") {
                val song = YtMusicRepository.trackLinks(id).getOrThrow()
                require(song.videoId == id)
                withContext(Dispatchers.Main.immediate) {
                    currentCoroutineContext().ensureActive()
                    guard(context, permission, null)
                    if (Downloads.verifiedSavedUri(id) == null) Downloads.enqueue(context, song)
                }
            } else if (name.endsWith("_cancel") || name.endsWith("_delete")) {
                // Operate only on the existing download record; never mutate an account playlist.
                val records = Downloads.collections.value
                val ids = if (playlist == null) listOf(id) else records[id]?.videoIds.orEmpty()
                val others = records.values.filter { it.id != playlist }.flatMapTo(HashSet()) { it.videoIds }
                val safe = exclusive(ids, others, Downloads.saved.value)
                if (playlist == null && safe.size != ids.size)
                    return error("shared_download", "Track belongs to a saved collection; manage that collection instead.")
                for (track in safe) {
                    currentCoroutineContext().ensureActive()
                    guard(context, permission, playlist)
                    Downloads.cancel(track)
                    if (name.endsWith("_delete")) {
                        // Unsaved queued tracks have no file to delete.
                        if (track in Downloads.saved.value && !Downloads.delete(context, track))
                            return error("delete_failed", "A downloaded file could not be deleted.")
                    }
                }
                if (playlist != null && name.endsWith("_delete")) {
                    currentCoroutineContext().ensureActive()
                    guard(context, permission, playlist)
                    Downloads.forgetCollection(playlist)
                }
            }
            currentCoroutineContext().ensureActive()
            guard(context, permission, playlist)
            return if (playlist == null) status(id) else buildJsonObject {
                put("playlist_id", id)
                put("accepted", true)
                put("tracks", JsonArray(Downloads.collections.value[id]?.videoIds.orEmpty().map(::status)))
            }
        } catch (e: CancellationException) { throw e }
          catch (_: SecurityException) { return error("access_denied", "Permission or account scope is unavailable.") }
          catch (_: IllegalArgumentException) { return error("invalid_arguments", "Invalid download arguments.") }
          catch (_: Exception) { return error("download_failed", "Download operation failed. Check connection, sign-in and storage access.") }
    }

    private fun status(id: String): JsonObject = buildJsonObject {
        put("video_id", id)
        val active = Downloads.active.value[id]
        put("status", when {
            Downloads.verifiedSavedUri(id) != null -> "completed"
            active is DownloadState.Queued -> "queued"
            active is DownloadState.Running -> "running"
            active is DownloadState.Failed -> "failed"
            else -> "not_downloaded"
        })
        if (active is DownloadState.Running && active.fraction.isFinite()) put("progress", active.fraction.coerceIn(0f, 1f))
        if (active is DownloadState.Failed) {
            val (code, message) = safeFailure(active.reason)
            put("error", buildJsonObject { put("code", code); put("message", message) })
        }
    }

    private fun error(code: String, message: String) = buildJsonObject {
        put("error", buildJsonObject { put("code", code); put("message", message) })
    }
}
