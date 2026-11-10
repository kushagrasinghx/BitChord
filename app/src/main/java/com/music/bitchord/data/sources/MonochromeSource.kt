package com.music.bitchord.data.sources

import com.music.bitchord.data.Http
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.net.URLEncoder

private const val TAG = "BitChord"

class MonochromeSource(
    override val config: SourceConfig,
) : MusicSource, SourceRegistry.ConfigBacked {

    override val configId: String get() = config.id
    override val kind: SourceKind get() = config.kind
    override val displayName: String get() = config.label.ifBlank { "Qobuz [Monochrome] (Hi-Res FLAC)" }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun health(): SourceHealth = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://tracks.monochrome.st")
                .header("Accept", "*/*")
                .build()
            Http.client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) SourceHealth.Ok()
                else SourceHealth.Unreachable("HTTP ${resp.code}")
            }
        } catch (e: Exception) {
            SourceHealth.Unreachable(e.message ?: "Connection failed")
        }
    }

    override suspend fun search(
        query: String,
        limit: Int,
        waitForAll: Boolean,
        request: StreamRequest?,
    ): List<Song> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val url = "https://tracks.monochrome.st/search/tracks?q=$encoded&limit=$limit"
        TrackLog.d(TAG, "▶ Monochrome search() url=$url")
        try {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            Http.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    TrackLog.w(TAG, "  ✗ Monochrome search HTTP ${resp.code}")
                    return@withContext emptyList()
                }
                val body = resp.body?.string().orEmpty()
                val parsed = json.decodeFromString<MonochromeSearchResponse>(body)
                TrackLog.d(TAG, "  ✓ Monochrome returned ${parsed.tracks.size} tracks for \"$trimmed\"")
                parsed.tracks.take(limit).map { raw ->
                    val artistName = raw.artistNames.joinToString(", ").ifBlank { "Unknown Artist" }
                    val dur = raw.duration
                    val durationSec = if (dur > 10000) (dur / 1000).toInt() else dur.toInt()
                    val m = durationSec / 60
                    val s = durationSec % 60
                    Song(
                        videoId = SourceRegistry.trackKey(config.id, raw.id),
                        title = raw.title,
                        artist = artistName,
                        albumName = null,
                        thumbnailUrl = raw.artwork,
                        durationText = String.format(java.util.Locale.US, "%d:%02d", m, s),
                        sourceQuality = "LOSSLESS",
                        isExplicit = raw.explicit,
                    )
                }
            }
        } catch (e: Exception) {
            TrackLog.w(TAG, "  ✗ Monochrome search failed: ${e.message}")
            emptyList()
        }
    }

    override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? {
        TrackLog.d(TAG, "▶ Monochrome stream() trackId=$trackId")
        val streamUrl = "https://tracks.monochrome.st/track/$trackId"
        return SourceStream(
            url = streamUrl,
            format = StreamFormat(
                codec = "flac",
                bitDepth = 16,
                sampleRateHz = 44100,
            ),
        )
    }

    @Serializable
    private data class MonochromeSearchResponse(
        @SerialName("tracks") val tracks: List<MonochromeTrack> = emptyList(),
    )

    @Serializable
    private data class MonochromeTrack(
        @SerialName("id") val id: String = "",
        @SerialName("title") val title: String = "",
        @SerialName("artistNames") val artistNames: List<String> = emptyList(),
        @SerialName("duration") val duration: Double = 0.0,
        @SerialName("artwork") val artwork: String? = null,
        @SerialName("explicit") val explicit: Boolean = false,
    )
}
