package com.music.bitchord.data.opensubsonic

import com.music.bitchord.data.Http
import com.music.bitchord.data.sources.addon.AddonException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

object OpenSubsonicService {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun ping(baseUrl: String, username: String? = null, password: String? = null): Boolean = withContext(Dispatchers.IO) {
        val url = buildUrl(baseUrl, "ping.view", username, password, emptyMap())
        val request = Request.Builder().url(url).build()
        runCatching {
            Http.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val body = response.body?.string() ?: return@use false
                val root = json.parseToJsonElement(body).jsonObject
                val subResp = root["subsonic-response"]?.jsonObject ?: return@use false
                subResp["status"]?.jsonPrimitive?.contentOrNull == "ok"
            }
        }.getOrDefault(false)
    }

    suspend fun search(baseUrl: String, username: String?, password: String?, query: String, limit: Int): List<OpenSubsonicSong> = withContext(Dispatchers.IO) {
        val url = buildUrl(baseUrl, "search3.view", username, password, mapOf("query" to query, "songCount" to limit.toString()))
        val request = Request.Builder().url(url).build()
        val responseBody = Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw AddonException("HTTP ${response.code}")
            response.body?.string() ?: throw AddonException("Empty response")
        }
        val root = json.parseToJsonElement(responseBody).jsonObject
        val subResp = root["subsonic-response"]?.jsonObject ?: throw AddonException("Invalid OpenSubsonic response")
        if (subResp["status"]?.jsonPrimitive?.contentOrNull != "ok") {
            throw AddonException(subResp["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull ?: "Search failed")
        }
        val searchResult3 = subResp["searchResult3"]?.jsonObject ?: return@withContext emptyList()
        val songArray = searchResult3["song"]?.jsonArray ?: return@withContext emptyList()

        songArray.mapNotNull { element ->
            val songObj = element.jsonObject
            val id = songObj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val title = songObj["title"]?.jsonPrimitive?.contentOrNull ?: "Unknown Title"
            val artist = songObj["artist"]?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
            val album = songObj["album"]?.jsonPrimitive?.contentOrNull
            val coverArt = songObj["coverArt"]?.jsonPrimitive?.contentOrNull
            val duration = songObj["duration"]?.jsonPrimitive?.intOrNull ?: 0
            val suffix = songObj["suffix"]?.jsonPrimitive?.contentOrNull?.lowercase()
            val isLossless = (suffix in listOf("flac", "alac", "wav"))
            
            OpenSubsonicSong(
                id = id,
                title = title,
                artist = artist,
                album = album,
                coverArtId = coverArt,
                durationSeconds = duration,
                isLossless = isLossless,
                suffix = suffix
            )
        }
    }

    suspend fun getRandomSongs(baseUrl: String, username: String?, password: String?, size: Int): List<OpenSubsonicSong> = withContext(Dispatchers.IO) {
        val url = buildUrl(baseUrl, "getRandomSongs.view", username, password, mapOf("size" to size.toString()))
        val request = Request.Builder().url(url).build()
        val responseBody = Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            response.body?.string() ?: return@withContext emptyList()
        }
        val root = json.parseToJsonElement(responseBody).jsonObject
        val subResp = root["subsonic-response"]?.jsonObject ?: return@withContext emptyList()
        if (subResp["status"]?.jsonPrimitive?.contentOrNull != "ok") return@withContext emptyList()
        
        val randomSongs = subResp["randomSongs"]?.jsonObject ?: return@withContext emptyList()
        val songArray = randomSongs["song"]?.jsonArray ?: return@withContext emptyList()

        songArray.mapNotNull { element ->
            val songObj = element.jsonObject
            val id = songObj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val title = songObj["title"]?.jsonPrimitive?.contentOrNull ?: "Unknown Title"
            val artist = songObj["artist"]?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
            val album = songObj["album"]?.jsonPrimitive?.contentOrNull
            val coverArt = songObj["coverArt"]?.jsonPrimitive?.contentOrNull
            val duration = songObj["duration"]?.jsonPrimitive?.intOrNull ?: 0
            val suffix = songObj["suffix"]?.jsonPrimitive?.contentOrNull?.lowercase()
            val isLossless = (suffix in listOf("flac", "alac", "wav"))
            
            OpenSubsonicSong(
                id = id,
                title = title,
                artist = artist,
                album = album,
                coverArtId = coverArt,
                durationSeconds = duration,
                isLossless = isLossless,
                suffix = suffix
            )
        }
    }

    fun getStreamUrl(baseUrl: String, username: String?, password: String?, trackId: String): String {
        return buildUrl(baseUrl, "stream.view", username, password, mapOf("id" to trackId))
    }
    
    fun getCoverArtUrl(baseUrl: String, username: String?, password: String?, coverArtId: String): String {
        return buildUrl(baseUrl, "getCoverArt.view", username, password, mapOf("id" to coverArtId))
    }

    private fun buildUrl(rawBase: String, endpoint: String, username: String?, password: String?, extraParams: Map<String, String>): String {
        val parsed = rawBase.toHttpUrlOrNull() ?: throw AddonException("Invalid URL")
        val builder = parsed.newBuilder()
            .addPathSegment("rest")
            .addPathSegment(endpoint)
            .addQueryParameter("v", "1.15.0")
            .addQueryParameter("c", "BitChord")
            .addQueryParameter("f", "json")
            
        if (!username.isNullOrBlank()) {
            builder.addQueryParameter("u", username.trim())
        }
        if (!password.isNullOrBlank()) {
            builder.addQueryParameter("p", password)
        }
            
        extraParams.forEach { (key, value) ->
            builder.addQueryParameter(key, value)
        }
        return builder.build().toString()
    }
    suspend fun getAlbumList2(baseUrl: String, username: String?, password: String?, type: String, size: Int): List<OpenSubsonicAlbum> = withContext(Dispatchers.IO) {
        val url = buildUrl(baseUrl, "getAlbumList2.view", username, password, mapOf("type" to type, "size" to size.toString()))
        val request = Request.Builder().url(url).build()
        val responseBody = Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            response.body?.string() ?: return@withContext emptyList()
        }
        val root = json.parseToJsonElement(responseBody).jsonObject
        val subResp = root["subsonic-response"]?.jsonObject ?: return@withContext emptyList()
        val albumList2 = subResp["albumList2"]?.jsonObject ?: return@withContext emptyList()
        val albumArray = albumList2["album"]?.jsonArray ?: return@withContext emptyList()

        albumArray.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown Album"
            val artist = obj["artist"]?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
            val coverArt = obj["coverArt"]?.jsonPrimitive?.contentOrNull
            OpenSubsonicAlbum(id, name, artist, coverArt)
        }
    }

    suspend fun getAlbum(baseUrl: String, username: String?, password: String?, albumId: String): List<OpenSubsonicSong> = withContext(Dispatchers.IO) {
        val url = buildUrl(baseUrl, "getAlbum.view", username, password, mapOf("id" to albumId))
        val request = Request.Builder().url(url).build()
        val responseBody = Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            response.body?.string() ?: return@withContext emptyList()
        }
        val root = json.parseToJsonElement(responseBody).jsonObject
        val subResp = root["subsonic-response"]?.jsonObject ?: return@withContext emptyList()
        val album = subResp["album"]?.jsonObject ?: return@withContext emptyList()
        val songArray = album["song"]?.jsonArray ?: return@withContext emptyList()

        songArray.mapNotNull { element ->
            val songObj = element.jsonObject
            val id = songObj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val title = songObj["title"]?.jsonPrimitive?.contentOrNull ?: "Unknown Title"
            val artist = songObj["artist"]?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
            val albumName = songObj["album"]?.jsonPrimitive?.contentOrNull
            val coverArt = songObj["coverArt"]?.jsonPrimitive?.contentOrNull
            val duration = songObj["duration"]?.jsonPrimitive?.intOrNull ?: 0
            val suffix = songObj["suffix"]?.jsonPrimitive?.contentOrNull?.lowercase()
            val isLossless = (suffix in listOf("flac", "alac", "wav"))
            
            OpenSubsonicSong(
                id = id,
                title = title,
                artist = artist,
                album = albumName,
                coverArtId = coverArt,
                durationSeconds = duration,
                isLossless = isLossless,
                suffix = suffix
            )
        }
    }

    suspend fun getPlaylists(baseUrl: String, username: String?, password: String?): List<OpenSubsonicPlaylist> = withContext(Dispatchers.IO) {
        val url = buildUrl(baseUrl, "getPlaylists.view", username, password, emptyMap())
        val request = Request.Builder().url(url).build()
        val responseBody = Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            response.body?.string() ?: return@withContext emptyList()
        }
        val root = json.parseToJsonElement(responseBody).jsonObject
        val subResp = root["subsonic-response"]?.jsonObject ?: return@withContext emptyList()
        val playlists = subResp["playlists"]?.jsonObject ?: return@withContext emptyList()
        val playlistArray = playlists["playlist"]?.jsonArray ?: return@withContext emptyList()

        playlistArray.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown Playlist"
            val comment = obj["comment"]?.jsonPrimitive?.contentOrNull ?: ""
            val coverArt = obj["coverArt"]?.jsonPrimitive?.contentOrNull
            OpenSubsonicPlaylist(id, name, comment, coverArt)
        }
    }

    suspend fun getPlaylist(baseUrl: String, username: String?, password: String?, playlistId: String): List<OpenSubsonicSong> = withContext(Dispatchers.IO) {
        val url = buildUrl(baseUrl, "getPlaylist.view", username, password, mapOf("id" to playlistId))
        val request = Request.Builder().url(url).build()
        val responseBody = Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            response.body?.string() ?: return@withContext emptyList()
        }
        val root = json.parseToJsonElement(responseBody).jsonObject
        val subResp = root["subsonic-response"]?.jsonObject ?: return@withContext emptyList()
        val playlist = subResp["playlist"]?.jsonObject ?: return@withContext emptyList()
        val songArray = playlist["entry"]?.jsonArray ?: return@withContext emptyList()

        songArray.mapNotNull { element ->
            val songObj = element.jsonObject
            val id = songObj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val title = songObj["title"]?.jsonPrimitive?.contentOrNull ?: "Unknown Title"
            val artist = songObj["artist"]?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
            val albumName = songObj["album"]?.jsonPrimitive?.contentOrNull
            val coverArt = songObj["coverArt"]?.jsonPrimitive?.contentOrNull
            val duration = songObj["duration"]?.jsonPrimitive?.intOrNull ?: 0
            val suffix = songObj["suffix"]?.jsonPrimitive?.contentOrNull?.lowercase()
            val isLossless = (suffix in listOf("flac", "alac", "wav"))
            
            OpenSubsonicSong(
                id = id,
                title = title,
                artist = artist,
                album = albumName,
                coverArtId = coverArt,
                durationSeconds = duration,
                isLossless = isLossless,
                suffix = suffix
            )
        }
    }
}

data class OpenSubsonicSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val coverArtId: String?,
    val durationSeconds: Int,
    val isLossless: Boolean,
    val suffix: String?
)

data class OpenSubsonicAlbum(
    val id: String,
    val name: String,
    val artist: String,
    val coverArtId: String?
)

data class OpenSubsonicPlaylist(
    val id: String,
    val name: String,
    val comment: String,
    val coverArtId: String?
)
