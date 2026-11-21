package com.music.bitchord.mcp

import android.content.Context
import com.music.bitchord.data.Http
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.spotify.SpotifyLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Request
import java.security.MessageDigest

/** Read-only Spotify source. Callers must authorize an ID BEFORE calling read.
 * SpotifyLibrary uses global auth: endpoint checks detect account changes but cannot
 * eliminate an A->B->A race inside that library. Never mutate its global auth here.
 */
object McpSpotifyPlaylists {
    private val scopePattern = Regex("[a-f0-9]{64}")
    private val rawPattern = Regex("[A-Za-z0-9_-]{1,200}")

    internal fun scope(userId: String): String {
        require(userId.isNotBlank()) { "Missing Spotify identity" }
        return MessageDigest.getInstance("SHA-256").digest(userId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    internal fun encode(accountScope: String, rawId: String): String {
        require(scopePattern.matches(accountScope) && rawPattern.matches(rawId)) { "Invalid Spotify playlist ID" }
        return "spotify:$accountScope:$rawId"
    }

    internal fun decode(id: String, currentScope: String): String {
        val parts = id.split(':')
        require(parts.size == 3 && parts[0] == "spotify" && scopePattern.matches(parts[1]) &&
            rawPattern.matches(parts[2])) { "Invalid Spotify playlist ID" }
        if (parts[1] != currentScope) throw SecurityException("Spotify account changed")
        return parts[2]
    }

    private suspend fun currentScope(): String? = withContext(Dispatchers.IO) {
        if (AppSettings.spotifySpdcToken.value.isBlank()) return@withContext null
        val tokens = SpotifyLibrary.auth?.tokens() ?: return@withContext null
        val request = Request.Builder().url("https://api.spotify.com/v1/me")
            .header("Authorization", "Bearer ${tokens.bearer}").build()
        Http.client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Spotify identity request failed (${response.code})" }
            val body = response.body?.string() ?: error("Spotify identity response empty")
            val id = Json.parseToJsonElement(body).jsonObject["id"]?.jsonPrimitive?.contentOrNull
            check(!id.isNullOrBlank()) { "Spotify identity missing" }
            scope(id)
        }
    }

    private suspend fun assertScope(expected: String) {
        currentCoroutineContext().ensureActive()
        if (currentScope() != expected) throw SecurityException("Spotify account changed")
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun load(context: Context): List<McpPlaylistEntry> {
        val accountScope = currentScope() ?: return emptyList()
        val rows = SpotifyLibrary.playlists()
        assertScope(accountScope)
        val entries = rows.map { McpPlaylistEntry(encode(accountScope, it.id), it.name, "spotify", true) }
            .distinctBy { it.id }
        assertScope(accountScope)
        return entries
    }

    suspend fun read(id: String, offset: Int, limit: Int): JsonObject {
        require(offset >= 0 && limit in 1..500) { "Invalid Spotify pagination" }
        // Structural validation before accessing auth or making a request.
        val requestedScope = id.split(':').getOrNull(1).orEmpty()
        decode(id, requestedScope)
        val accountScope = currentScope() ?: throw SecurityException("Spotify is not connected")
        val rawId = decode(id, accountScope)
        val tracks = SpotifyLibrary.tracks(rawId)
        assertScope(accountScope)
        val result = buildJsonObject {
            put("id", id)
            put("source", "spotify")
            put("readOnly", true)
            put("offset", offset)
            put("limit", limit)
            put("total", tracks.size)
            put("hasMore", offset.toLong() + limit < tracks.size)
            putJsonArray("tracks") {
                tracks.drop(offset).take(limit).forEach { track ->
                    add(buildJsonObject {
                        put("id", track.id)
                        put("spotifyId", track.id)
                        put("source", "spotify")
                        put("title", track.title)
                        put("artist", track.artist)
                        put("album", track.album)
                        put("durationMs", track.durationMs)
                    })
                }
            }
        }
        assertScope(accountScope)
        return result
    }
}
