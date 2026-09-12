package com.music.bitchord.desktop

import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@Serializable
private data class DesktopSaavnSearchResponse(val results: List<DesktopSaavnSong> = emptyList())

@Serializable
private data class DesktopSaavnSong(
    val id: String = "",
    val title: String = "",
    val image: String = "",
    @SerialName("explicit_content") val explicitContent: String = "",
    @SerialName("more_info") val moreInfo: DesktopSaavnMoreInfo = DesktopSaavnMoreInfo(),
) {
    val isExplicit: Boolean
        get() = explicitContent == "1" || explicitContent.equals("true", ignoreCase = true)
}

@Serializable
private data class DesktopSaavnMoreInfo(
    val album: String = "",
    @SerialName("encrypted_media_url") val encryptedMediaUrl: String = "",
    val duration: String = "",
    @SerialName("320kbps") val has320: String = "",
    val artistMap: DesktopSaavnArtistMap = DesktopSaavnArtistMap(),
) {
    val supports320: Boolean get() = has320.equals("true", ignoreCase = true)
}

@Serializable
private data class DesktopSaavnArtistMap(
    @SerialName("primary_artists") val primaryArtists: List<DesktopSaavnArtist> = emptyList(),
)

@Serializable
private data class DesktopSaavnArtist(val name: String = "")

/** JioSaavn catalogue and CDN adapter for desktop source-aware playback. */
internal object DesktopJioSaavn {
    private const val BASE_URL = "https://www.jiosaavn.com/api.php"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
    private const val TRACK_PREFIX = "jiosaavn:"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
    }

    suspend fun search(query: String, limit: Int = 25): Result<List<SearchResult>> = runCatching {
        val response = client.get(BASE_URL) {
            parameter("__call", "search.getResults")
            parameter("_format", "json")
            parameter("_marker", "0")
            parameter("api_version", "4")
            parameter("ctx", "android")
            parameter("q", query.trim())
            parameter("p", "1")
            parameter("n", limit)
            header("Accept", "application/json")
            header("Accept-Language", "en-IN,en;q=0.9")
            header("User-Agent", USER_AGENT)
            header("Cookie", "explicit_content=1")
        }
        check(response.status == HttpStatusCode.OK) {
            "JioSaavn returned HTTP ${response.status.value}"
        }
        val payload = json.decodeFromString<DesktopSaavnSearchResponse>(response.bodyAsText())
        prioritizeExplicit(payload.results).take(limit).map { it.toSong() }
            .map(SearchResult::Track)
    }

    suspend fun stream(trackKey: String): Result<DesktopStream?> = runCatching {
        val trackId = trackKey.removePrefix(TRACK_PREFIX)
        if (trackId.isBlank()) return@runCatching null
        val response = client.get(BASE_URL) {
            parameter("__call", "song.getDetails")
            parameter("_format", "json")
            parameter("_marker", "0")
            parameter("api_version", "4")
            parameter("ctx", "android")
            parameter("pids", trackId)
            header("Accept", "application/json")
            header("User-Agent", USER_AGENT)
            header("Cookie", "explicit_content=1")
        }
        check(response.status == HttpStatusCode.OK) {
            "JioSaavn returned HTTP ${response.status.value}"
        }
        val root = json.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: return@runCatching null
        val song = (root["songs"] as? JsonArray)?.firstOrNull()
            ?: root.values.firstOrNull { it is JsonObject }
            ?: return@runCatching null
        val raw = json.decodeFromJsonElement(DesktopSaavnSong.serializer(), song)
        val url = decryptUrl(raw.moreInfo.encryptedMediaUrl)
            .takeIf(String::isNotBlank)
            ?: return@runCatching null
        val rewritten = bestUrl(url, raw.moreInfo.supports320)
        DesktopStream(
            url = rewritten.first,
            // JioSaavn's encrypted URL points to an AAC track inside an MP4 container, and the
            // container is what the label should name.
            format = DesktopStreamFormat(codec = "mp4", kbps = rewritten.second),
            sourceId = "jiosaavn",
        )
    }

    /** Finds the same catalogue recording for a YouTube-origin song. */
    suspend fun match(song: Song): Song? = matches(song).firstOrNull()

    /** Every copy this catalogue holds of [song], most confident first. */
    suspend fun matches(song: Song): List<Song> {
        if (song.title.isBlank() || song.isVideo) return emptyList()
        for (query in DesktopTrackMatcher.queries(song)) {
            val candidates = search(query, 25).getOrDefault(emptyList())
                .mapNotNull { (it as? SearchResult.Track)?.song }
            DesktopTrackMatcher.ranked(candidates, song).ifEmpty { null }?.let { return it }
        }
        return emptyList()
    }

    private fun DesktopSaavnSong.toSong() = Song(
        videoId = TRACK_PREFIX + id,
        title = title,
        artist = moreInfo.artistMap.primaryArtists.joinToString(", ") { it.name }
            .ifBlank { "Unknown Artist" },
        albumName = moreInfo.album.ifBlank { null },
        thumbnailUrl = image
            .replace(Regex("150x150|50x50"), "500x500")
            .replace(Regex("^http://"), "https://"),
        durationText = moreInfo.duration.toIntOrNull()?.let { seconds ->
            "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
        },
        sourceQuality = "HIGH",
        isExplicit = isExplicit,
    )

    private fun prioritizeExplicit(songs: List<DesktopSaavnSong>): List<DesktopSaavnSong> =
        songs.sortedByDescending(DesktopSaavnSong::isExplicit)

    private fun decryptUrl(encrypted: String): String = runCatching {
        if (encrypted.isBlank()) return ""
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec("38346591".toByteArray(Charsets.UTF_8), "DES"),
        )
        cipher.doFinal(Base64.getDecoder().decode(encrypted)).toString(Charsets.UTF_8).trim()
    }.getOrDefault("")

    private fun bestUrl(url: String, supports320: Boolean): Pair<String, Int?> {
        val suffix = Regex("_(48|96|160|320)\\.(mp4|aac|mp3)$").find(url)
            ?: return url to if (supports320) 320 else null
        val offered = suffix.groupValues[1].toIntOrNull()
        return if (supports320) {
            url.replaceRange(suffix.range, "_320.${suffix.groupValues[2]}") to 320
        } else {
            url to offered
        }
    }

}

internal object DesktopMusicSources {
    suspend fun search(query: String, filter: SearchFilter): Result<List<SearchResult>> =
        DesktopSourceRegistry.search(query, filter)

    suspend fun resolve(
        song: Song,
        qualityOverride: String? = null,
        excludedSourceId: String? = null,
    ): Result<DesktopStream> = DesktopSourceRegistry.resolve(song, qualityOverride, excludedSourceId)

    /** As [resolve], but for playback: races the sources and reports what is still running. */
    suspend fun resolveLive(
        song: Song,
        qualityOverride: String? = null,
        excludedSourceId: String? = null,
    ): Result<DesktopLiveResolution> =
        DesktopSourceRegistry.resolveLive(song, qualityOverride, excludedSourceId)

    /** A second look, asked from scratch once the first one settled for nothing better. */
    suspend fun upgradeFor(song: Song, playing: DesktopStream?): DesktopStream? =
        DesktopSourceRegistry.upgradeFor(song, playing)

    /** The quality rung in force. */
    fun ceiling(qualityOverride: String? = null): DesktopAudioQuality =
        DesktopSourceRegistry.ceiling(qualityOverride)

    fun worthSwapping(candidate: DesktopStreamFormat, playing: DesktopStreamFormat?): Boolean =
        DesktopSourceRegistry.worthSwapping(candidate, playing)

    fun sourceNameFor(stream: DesktopStream): String = DesktopSourceRegistry.sourceNameFor(stream)

    fun hasYouTubeOriginal(song: Song): Boolean = DesktopSourceRegistry.hasYouTubeOriginal(song)
}
