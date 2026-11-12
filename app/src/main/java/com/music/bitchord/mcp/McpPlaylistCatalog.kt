package com.music.bitchord.mcp

import android.content.Context
import com.music.bitchord.BitChordApplication
import com.music.bitchord.data.OfflinePlaylists
import kotlinx.coroutines.CancellationException
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.LocalPlaylistStore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

data class McpPlaylistEntry(val id: String, val title: String, val source: String, val readOnly: Boolean)

/** IDs bind Google account AND YouTube profile. No credentials enter IDs or results. */
data class McpPlaylistCatalogResult(val entries: List<McpPlaylistEntry>, val warnings: List<String>)

object McpPlaylistCatalog {
    fun localEntries(): List<McpPlaylistEntry> =
        LocalPlaylistStore.playlists.value.map { McpPlaylistEntry(it.id, it.title, "local", false) } +
        OfflinePlaylists.playlists.value.map { McpPlaylistEntry(it.browseId, it.title, "offline", true) }

    private val _warnings = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
    val warnings: kotlinx.coroutines.flow.StateFlow<List<String>> = _warnings

    suspend fun loadResult(context: Context): McpPlaylistCatalogResult = coroutineScope {
        suspend fun source(name: String, block: suspend () -> List<McpPlaylistEntry>): Pair<List<McpPlaylistEntry>, String?> = try {
            val rows = withTimeoutOrNull(5_000) { block() }
            if (rows == null) emptyList<McpPlaylistEntry>() to "${name}_catalog_timeout" else rows to null
        } catch (e: CancellationException) { throw e }
          catch (_: Exception) { emptyList<McpPlaylistEntry>() to "${name}_catalog_unavailable" }
        val youtube = async { source("youtube") { loadYouTube(context) } }
        val spotify = async { source("spotify") { McpSpotifyPlaylists.load(context) } }
        val results = listOf(youtube.await(), spotify.await())
        val warnings = results.mapNotNull { it.second }
        _warnings.value = warnings
        McpPlaylistCatalogResult(localEntries() + results.flatMap { it.first }, warnings)
    }

    suspend fun load(context: Context): List<McpPlaylistEntry> = loadResult(context).entries

    fun currentScope(context: Context): String? {
        val store = BitChordApplication.authStore
        val session = store.activeSession ?: return null
        val profile = store.activeProfileId ?: session.activeProfileId ?: return null
        if (session.profiles.none { it.profileId == profile }) return null
        return McpPlaylistId.scope(session.accountId, profile)
    }

    fun assertScope(context: Context, scope: String) {
        if (currentScope(context) != scope) throw SecurityException("Account changed")
    }

    fun isRemote(id: String): Boolean = id.startsWith("youtube:")

    /** Validates scope before any remote request; never treats untrusted IDs as browse paths. */
    fun remoteBrowseId(context: Context, id: String): String {
        val (scope, browseId) = McpPlaylistId.parseYouTube(id)
        assertScope(context, scope)
        return browseId
    }

    /** Settings UI discovery, not an MCP authorization bypass. Errors propagate, never silently hide remote failure. */
    private suspend fun loadYouTube(context: Context): List<McpPlaylistEntry> {
        val scope = currentScope(context) ?: return emptyList()
        val rows = YtMusicRepository.userPlaylists().getOrThrow() // same paged saved/own playlist feed as library
        assertScope(context, scope)
        val remote = rows.map { McpPlaylistEntry("youtube:$scope:${it.browseId}", it.title, "youtube", true) }
        // Liked Music is excluded by userPlaylists' editable-list parser; add explicitly.
        val liked = McpPlaylistEntry("youtube:$scope:${YtMusicRepository.LIKED_MUSIC}", "Liked Music", "youtube", true)
        currentCoroutineContext().ensureActive()
        assertScope(context, scope)
        return (listOf(liked) + remote).distinctBy { it.id }
    }

    /** Page explicitly: allSongs may return a partial result after a continuation failure. */
    suspend fun read(context: Context, id: String, offset: Int, limit: Int): Pair<List<Song>, Boolean> {
        val browse = remoteBrowseId(context, id)
        val scope = id.split(':')[1]
        var page = YtMusicRepository.browseSongs(browse).getOrThrow()
        assertScope(context, scope)
        val songs = page.songs.toMutableList()
        val seen = HashSet<String>()
        var pages = 1
        while (songs.size.toLong() < offset.toLong() + limit && page.continuation != null) {
            val token = page.continuation!!
            check(seen.add(token) && pages++ < 100) { "Playlist pagination limit reached" }
            currentCoroutineContext().ensureActive()
            assertScope(context, scope)
            page = YtMusicRepository.moreSongs(token).getOrThrow()
            assertScope(context, scope)
            songs.addAll(page.songs)
        }
        currentCoroutineContext().ensureActive()
        assertScope(context, scope)
        return songs.drop(offset).take(limit) to (songs.size.toLong() > offset.toLong() + limit || page.continuation != null)
    }
}
