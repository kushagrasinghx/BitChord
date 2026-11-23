package com.music.bitchord.mcp

import android.content.Context
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*

/** Account-only writes; never falls back to local storage. Create defaults to PRIVATE.
 * Grants only the fresh scoped ID, after a successful scoped create response. Timeout/network errors
 * may follow a server-side commit: callers must refresh, never retry blindly.
 */
object McpAccountPlaylists {
    internal data class Input(val title: String?, val privacy: PlaylistPrivacy, val ids: List<String>, val entry: String?, val before: String?)
    internal fun validate(name: String, args: JsonObject): Input {
        require(name in setOf("playlist_create", "playlist_rename", "playlist_delete", "playlist_batch_add", "playlist_batch_remove", "playlist_reorder"))
        fun text(key: String, max: Int): String {
            val p = args[key] as? JsonPrimitive
            require(p != null && p.isString)
            return p.content.also { require(it.isNotBlank() && it.length <= max && it.none { c -> c.isISOControl() }) }
        }
        val title = if (name == "playlist_create" || name == "playlist_rename") text("title", 200).trim() else null
        val privacy = if (args.containsKey("privacy")) {
            require(name == "playlist_create")
            PlaylistPrivacy.valueOf(text("privacy", 10).uppercase())
        } else PlaylistPrivacy.PRIVATE
        if (name != "playlist_create") McpPlaylistId.parseYouTube(text("playlist_id", 280))
        val ids = if (name == "playlist_batch_add" || name == "playlist_batch_remove") {
            val key = if (name == "playlist_batch_add") "video_ids" else "entry_ids"
            val array = args[key] as? JsonArray ?: throw IllegalArgumentException()
            require(array.size in 1..50)
            array.map { element ->
                val p = element as? JsonPrimitive
                require(p != null && p.isString)
                p.content.also { require(if (key == "video_ids") Regex("[A-Za-z0-9_-]{11}").matches(it) else Regex("[A-Za-z0-9_=-]{1,200}").matches(it)) }
            }.also { require(it.distinct().size == it.size) }
        } else emptyList()
        val entry = if (name == "playlist_reorder") text("entry_id", 200) else null
        val before = if (name == "playlist_reorder" && args["before_entry_id"] != null && args["before_entry_id"] != JsonNull) text("before_entry_id", 200) else null
        require(entry == null || entry != before)
        return Input(title, privacy, ids, entry, before)
    }

    suspend fun call(context: Context, name: String, args: JsonObject): JsonObject {
        fun error(code: String) = buildJsonObject { putJsonObject("error") { put("code", code); put("message", "Account playlist operation failed; refresh before retrying") } }
        return try {
            val input = validate(name, args)
            val permission = when (name) { "playlist_create" -> "playlist_create"; "playlist_delete" -> "playlist_delete"; else -> "playlist_write" }
            val scope = McpPlaylistCatalog.currentScope(context) ?: throw SecurityException()
            val id = if (name == "playlist_create") null else args.getValue("playlist_id").jsonPrimitive.content
            fun guard(listId: String? = id) {
                McpPlaylistCatalog.assertScope(context, scope)
                if (!McpSettings.allowed(permission)) throw SecurityException()
                if (listId != null) {
                    McpPlaylistCatalog.remoteBrowseId(context, listId)
                    if (!McpSettings.playlistAllowed(listId)) throw SecurityException()
                }
            }
            suspend fun <T> request(block: suspend () -> Result<T>): T {
                currentCoroutineContext().ensureActive(); guard()
                val result = block()
                currentCoroutineContext().ensureActive(); guard()
                return result.getOrThrow()
            }
            guard()
            withTimeoutOrNull(12_000) {
                if (name == "playlist_create") {
                    val raw = request { YtMusicRepository.createPlaylist(input.title!!, input.privacy) }
                    val browse = if (raw.startsWith("VL")) raw else "VL$raw"
                    val fresh = "youtube:$scope:$browse"
                    McpPlaylistId.parseYouTube(fresh)
                    // The successful authenticated create response establishes ownership.
                    // A separate browse immediately after creation can lag and report
                    // unknown/false, despite the playlist already having been committed.
                    guard()
                    McpSettings.grantPlaylist(fresh)
                    guard(fresh)
                    return@withTimeoutOrNull buildJsonObject { put("created_id", fresh); put("privacy", input.privacy.apiValue); put("requires_user_approval", false) }
                }
                val browse = McpPlaylistCatalog.remoteBrowseId(context, id!!)
                if (browse == YtMusicRepository.LIKED_MUSIC || request { YtMusicRepository.playlistOwned(browse) } != true) throw SecurityException()
                suspend fun rows(): List<Song> {
                    var page = request { YtMusicRepository.browseSongs(browse) }
                    val result = page.songs.toMutableList()
                    val seen = hashSetOf<String>()
                    while (page.continuation != null) {
                        val token = page.continuation!!
                        require(seen.add(token) && seen.size <= 50)
                        page = request { YtMusicRepository.moreSongs(token) }
                        result.addAll(page.songs)
                    }
                    return result
                }
                when (name) {
                    "playlist_rename" -> request { YtMusicRepository.renamePlaylist(browse, input.title!!) }
                    "playlist_delete" -> request { YtMusicRepository.deletePlaylist(browse) }
                    "playlist_batch_add" -> {
                        val returned = request { YtMusicRepository.addToPlaylist(browse, input.ids) }
                        val applied = input.ids.filter { !returned[it].isNullOrBlank() }
                        val missing = input.ids.filterNot { it in applied }
                        guard()
                        return@withTimeoutOrNull buildJsonObject {
                            put("ok", missing.isEmpty())
                            put("status", if (missing.isEmpty()) "complete" else "partial_outcome_unknown")
                            put("atomic", false)
                            put("requested", JsonArray(input.ids.map { JsonPrimitive(it) }))
                            putJsonObject("applied") { applied.forEach { put(it, returned.getValue(it)) } }
                            put("missing", JsonArray(missing.map { JsonPrimitive(it) }))
                            put("confirmed", missing.isEmpty())
                            put("readback_required", missing.isNotEmpty())
                        }
                    }
                    "playlist_batch_remove" -> {
                        val entries = rows().associateBy { it.setVideoId }
                        val pairs = input.ids.map { entry -> entry to (entries[entry]?.videoId ?: throw IllegalArgumentException()) }
                        request { YtMusicRepository.removeFromPlaylist(browse, pairs) }
                        guard()
                        // Unit acknowledges the request, not each entry's removal.
                        return@withTimeoutOrNull buildJsonObject {
                            put("ok", true); put("status", "command_sent")
                            put("atomic", false); put("confirmed", false)
                            put("readback_required", true)
                            put("requested", JsonArray(input.ids.map { JsonPrimitive(it) }))
                        }
                    }
                    "playlist_reorder" -> {
                        // Repository chunks moves at 50. Cap the whole list to guarantee one mutation request.
                        val current = rows().map { it.setVideoId ?: throw IllegalArgumentException() }
                        require(current.size <= 50 && current.distinct().size == current.size && input.entry in current)
                        require(input.before == null || input.before in current)
                        val target = current.toMutableList().apply {
                            remove(input.entry!!)
                            add(if (input.before == null) size else indexOf(input.before), input.entry)
                        }
                        request { YtMusicRepository.reorderPlaylist(browse, current, target) }
                    }
                }
                guard()
                buildJsonObject { put("ok", true); put("status", "command_sent"); put("atomic", false) }
            } ?: error("timeout_outcome_unknown")
        } catch (e: CancellationException) { throw e }
          catch (_: SecurityException) { error("permission_denied") }
          catch (_: IllegalArgumentException) { error("invalid_arguments") }
          catch (_: Exception) { error("remote_failure_outcome_unknown") }
    }
}
