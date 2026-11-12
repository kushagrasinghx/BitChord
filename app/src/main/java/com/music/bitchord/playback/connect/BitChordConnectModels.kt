package com.music.bitchord.playback.connect

import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import org.json.JSONArray
import org.json.JSONObject

enum class ConnectDeviceType {
    TV,
    PHONE,
    SPEAKER,
}

data class ConnectDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 8765,
    val deviceType: ConnectDeviceType = ConnectDeviceType.TV,
    val model: String = "",
    val version: String = "1.8.1",
    val isCurrent: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("host", host)
        put("port", port)
        put("deviceType", deviceType.name)
        put("model", model)
        put("version", version)
        put("isCurrent", isCurrent)
    }

    companion object {
        fun fromJson(json: JSONObject): ConnectDevice {
            val typeStr = json.optString("deviceType", "TV")
            val type = runCatching { ConnectDeviceType.valueOf(typeStr) }.getOrDefault(ConnectDeviceType.TV)
            return ConnectDevice(
                id = json.optString("id", ""),
                name = json.optString("name", "BitChord Device"),
                host = json.optString("host", "127.0.0.1"),
                port = json.optInt("port", 8765),
                deviceType = type,
                model = json.optString("model", ""),
                version = json.optString("version", "1.8.1"),
                isCurrent = json.optBoolean("isCurrent", false),
            )
        }
    }
}

data class ConnectPlaybackSnapshot(
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val playbackState: Int = 1, // Player.STATE_IDLE = 1, STATE_READY = 3
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1f,
    val repeatMode: Int = 0,
    val shuffle: Boolean = false,
    val song: Song? = null,
    val queue: List<Song> = emptyList(),
    val queueIndex: Int = 0,
    val sourceQuality: String? = null,
    val isHiRes: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("isPlaying", isPlaying)
        put("playWhenReady", playWhenReady)
        put("playbackState", playbackState)
        put("positionMs", positionMs)
        put("durationMs", durationMs)
        put("volume", volume.toDouble())
        put("repeatMode", repeatMode)
        put("shuffle", shuffle)
        if (song != null) put("song", song.toJson())
        val queueArray = JSONArray()
        queue.take(50).forEach { queueArray.put(it.toJson()) }
        put("queue", queueArray)
        put("queueIndex", queueIndex)
        putOpt("sourceQuality", sourceQuality)
        put("isHiRes", isHiRes)
        put("timestamp", timestamp)
    }

    companion object {
        fun fromJson(json: JSONObject): ConnectPlaybackSnapshot {
            val songObj = json.optJSONObject("song")
            val parsedSong = songObj?.toSong()
            val queueList = mutableListOf<Song>()
            val qArr = json.optJSONArray("queue")
            if (qArr != null) {
                for (i in 0 until qArr.length()) {
                    val sObj = qArr.optJSONObject(i) ?: continue
                    queueList += sObj.toSong()
                }
            }
            return ConnectPlaybackSnapshot(
                isPlaying = json.optBoolean("isPlaying", false),
                playWhenReady = json.optBoolean("playWhenReady", false),
                playbackState = json.optInt("playbackState", 1),
                positionMs = json.optLong("positionMs", 0L),
                durationMs = json.optLong("durationMs", 0L),
                volume = json.optDouble("volume", 1.0).toFloat(),
                repeatMode = json.optInt("repeatMode", 0),
                shuffle = json.optBoolean("shuffle", false),
                song = parsedSong,
                queue = queueList,
                queueIndex = json.optInt("queueIndex", 0),
                sourceQuality = json.optString("sourceQuality").takeIf { it.isNotBlank() },
                isHiRes = json.optBoolean("isHiRes", false),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            )
        }
    }
}

data class ConnectCommand(
    val action: String,
    val positionMs: Long? = null,
    val volume: Float? = null,
    val song: Song? = null,
    val songs: List<Song>? = null,
    val startIndex: Int? = null,
    val repeatMode: Int? = null,
    val clientName: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("action", action)
        if (positionMs != null) put("positionMs", positionMs)
        if (volume != null) put("volume", volume.toDouble())
        if (song != null) put("song", song.toJson())
        if (songs != null) {
            val arr = JSONArray()
            songs.forEach { arr.put(it.toJson()) }
            put("songs", arr)
        }
        if (startIndex != null) put("startIndex", startIndex)
        if (repeatMode != null) put("repeatMode", repeatMode)
        putOpt("clientName", clientName)
    }

    companion object {
        const val ACTION_PLAY = "PLAY"
        const val ACTION_PAUSE = "PAUSE"
        const val ACTION_RESUME = "RESUME"
        const val ACTION_TOGGLE_PLAY_PAUSE = "TOGGLE_PLAY_PAUSE"
        const val ACTION_SEEK = "SEEK"
        const val ACTION_SKIP_NEXT = "SKIP_NEXT"
        const val ACTION_SKIP_PREVIOUS = "SKIP_PREVIOUS"
        const val ACTION_SET_VOLUME = "SET_VOLUME"
        const val ACTION_PLAY_SONGS = "PLAY_SONGS"
        const val ACTION_SET_REPEAT = "SET_REPEAT"
        const val ACTION_TOGGLE_SHUFFLE = "TOGGLE_SHUFFLE"
        const val ACTION_UPGRADE_QUALITY = "UPGRADE_QUALITY"
        const val ACTION_REVERT_ORIGINAL = "REVERT_ORIGINAL"

        fun fromJson(json: JSONObject): ConnectCommand {
            val songObj = json.optJSONObject("song")
            val songsArr = json.optJSONArray("songs")
            val songsList = songsArr?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.toSong() }
            }
            return ConnectCommand(
                action = json.optString("action", ""),
                positionMs = if (json.has("positionMs")) json.optLong("positionMs") else null,
                volume = if (json.has("volume")) json.optDouble("volume").toFloat() else null,
                song = songObj?.toSong(),
                songs = songsList,
                startIndex = if (json.has("startIndex")) json.optInt("startIndex") else null,
                repeatMode = if (json.has("repeatMode")) json.optInt("repeatMode") else null,
                clientName = json.optString("clientName").takeIf { it.isNotBlank() },
            )
        }
    }
}

fun Song.toJson(): JSONObject = JSONObject().apply {
    put("videoId", videoId)
    put("title", title)
    put("artist", artist)
    putOpt("thumbnailUrl", thumbnailUrl)
    putOpt("durationText", durationText)
    putOpt("artistId", artistId)
    putOpt("albumId", albumId)
    putOpt("albumName", albumName)
    put("isVideo", isVideo)
    put("isVideoOrigin", isVideoOrigin)
    putOpt("setVideoId", setVideoId)
    putOpt("radioName", radioName)
    putOpt("localUri", localUri)
    putOpt("downloadFormat", downloadFormat)
    putOpt("localPath", localPath)
    putOpt("sourceQuality", sourceQuality)
    putOpt("playbackSource", playbackSource)
    putOpt("playbackSourceType", playbackSourceType?.name)
    putOpt("playbackSourceId", playbackSourceId)
    if (isExplicit != null) put("isExplicit", isExplicit)
    put("queueTier", queueTier.name)
    putOpt("queueEntryId", queueEntryId)
    val artistsArray = JSONArray()
    artists.forEach { ref ->
        artistsArray.put(JSONObject().apply {
            put("name", ref.name)
            putOpt("browseId", ref.browseId)
        })
    }
    put("artists", artistsArray)
}

fun JSONObject.toSong(): Song {
    val artistsList = mutableListOf<ArtistRef>()
    val arr = optJSONArray("artists")
    if (arr != null) {
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            artistsList += ArtistRef(
                name = obj.optString("name", ""),
                browseId = obj.optString("browseId").takeIf { it.isNotBlank() },
            )
        }
    }
    val tierStr = optString("queueTier", "CONTEXT")
    val tier = runCatching { QueueTier.valueOf(tierStr) }.getOrDefault(QueueTier.CONTEXT)
    val pTypeStr = optString("playbackSourceType").takeIf { it.isNotBlank() }
    val pType = pTypeStr?.let { runCatching { PlaybackSourceType.valueOf(it) }.getOrNull() }

    return Song(
        videoId = optString("videoId", ""),
        title = optString("title", ""),
        artist = optString("artist", ""),
        thumbnailUrl = optString("thumbnailUrl").takeIf { it.isNotBlank() },
        durationText = optString("durationText").takeIf { it.isNotBlank() },
        artistId = optString("artistId").takeIf { it.isNotBlank() },
        artists = artistsList,
        albumId = optString("albumId").takeIf { it.isNotBlank() },
        albumName = optString("albumName").takeIf { it.isNotBlank() },
        isVideo = optBoolean("isVideo", false),
        isVideoOrigin = optBoolean("isVideoOrigin", false),
        setVideoId = optString("setVideoId").takeIf { it.isNotBlank() },
        radioName = optString("radioName").takeIf { it.isNotBlank() },
        localUri = optString("localUri").takeIf { it.isNotBlank() },
        downloadFormat = optString("downloadFormat").takeIf { it.isNotBlank() },
        localPath = optString("localPath").takeIf { it.isNotBlank() },
        sourceQuality = optString("sourceQuality").takeIf { it.isNotBlank() },
        playbackSource = optString("playbackSource").takeIf { it.isNotBlank() },
        playbackSourceType = pType,
        playbackSourceId = optString("playbackSourceId").takeIf { it.isNotBlank() },
        isExplicit = if (has("isExplicit")) optBoolean("isExplicit") else null,
        queueTier = tier,
        queueEntryId = optString("queueEntryId").takeIf { it.isNotBlank() },
    )
}
