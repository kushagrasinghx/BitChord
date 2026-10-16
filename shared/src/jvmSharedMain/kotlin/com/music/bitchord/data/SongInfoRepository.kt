package com.music.bitchord.data

import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.Song
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import java.time.LocalDate

data class SongInfo(
    val album: String? = null,
    val releaseYear: String? = null,
    val releaseDate: String? = null,
    val views: String? = null,
    val likes: String? = null,
    val comments: String? = null,
    val failed: Boolean = false,
)

/**
 * YouTube-style short form of a raw count: 950 → "950", 1234 → "1.2K",
 * 100000 → "100K", 2345678 → "2.3M". Truncated rather than rounded, as YouTube
 * does, so 999999 never shows as "1000K". Text that isn't a plain number is
 * returned unchanged.
 */
fun compactCount(raw: String): String {
    val n = raw.toLongOrNull()?.takeIf { it >= 0 } ?: return raw
    val (unit, suffix) = when {
        n >= 1_000_000_000 -> 1_000_000_000L to "B"
        n >= 1_000_000 -> 1_000_000L to "M"
        n >= 1_000 -> 1_000L to "K"
        else -> return n.toString()
    }
    val whole = n / unit
    if (whole >= 10) return "$whole$suffix"
    val tenth = n % unit * 10 / unit
    val separator = java.text.DecimalFormatSymbols.getInstance().decimalSeparator
    return if (tenth == 0L) "$whole$suffix" else "$whole$separator$tenth$suffix"
}

/** Loaded only when requested. Counts belong to this exact upload, never a search match. */
object SongInfoRepository {
    private val client = HttpClient(OkHttp) {
        engine { preconfigured = Http.client }
        install(HttpTimeout) { requestTimeoutMillis = 15_000 }
    }

    suspend fun load(song: Song): SongInfo = withContext(Dispatchers.IO) {
        if (song.localUri != null || !Regex("[A-Za-z0-9_-]{11}").matches(song.videoId)) {
            return@withContext SongInfo(album = song.albumName)
        }
        val statistics = async {
            attempt {
                val response = client.get("https://www.youtube.com/watch") {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                    parameter("v", song.videoId)
                    parameter("hl", Innertube.currentLanguage)
                }
                check(response.status.value == 200)
                SongInfoParser.parse(response.bodyAsText(), song.videoId)
            }
        }
        var album = song.albumName
        var year: String? = null
        val release = attempt {
            val linked = if (song.albumId == null) {
                YtMusicRepository.trackLinks(song.videoId).getOrThrow()
            } else song
            album = album ?: linked.albumName
            linked.albumId?.let { id ->
                val header = InnertubeParser.parseBrowseHeader(Innertube.browse(id))
                album = album ?: header?.title
                year = header?.subtitle?.split('•')?.map { it.trim() }
                    ?.firstOrNull { it.matches(Regex("(?:19|20)[0-9]{2}")) }
            }
        }
        (statistics.await().getOrNull() ?: SongInfo(failed = true)).copy(
            album = album, releaseYear = year,
        ).let { it.copy(failed = it.failed || release.isFailure) }
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal object SongInfoParser {
    fun parse(html: String, videoId: String): SongInfo {
        val scripts = Jsoup.parse(html).select("script").map { it.data() }
        val player = scripts.firstNotNullOfOrNull { extract(it, "ytInitialPlayerResponse") }
            ?: error("Missing player metadata")
        val details = player.obj("videoDetails")
        check(details.str("videoId") == videoId) { "Metadata belongs to another track" }
        val data = scripts.firstNotNullOfOrNull { extract(it, "ytInitialData") }
        // Only the current video's primary renderer: recommendations also carry counts.
        val primary = data?.find("videoPrimaryInfoRenderer")?.firstOrNull()
        val oldLike = primary?.find("toggleButtonRenderer")?.firstOrNull {
            it.obj("defaultIcon").str("iconType") == "LIKE"
        }?.get("defaultText")?.text()
        val newLike = primary?.find("likeButtonViewModel")?.firstOrNull()
            ?.find("buttonViewModel")?.firstOrNull()?.str("title")
        val comments = data?.find("commentsHeaderRenderer")?.firstNotNullOfOrNull {
            it["countText"]?.text()?.withCount()
        }
            ?: data?.find("commentsEntryPointHeaderRenderer")?.firstOrNull()
                ?.get("commentCount")?.text()?.withCount()
            ?: data?.find("engagementPanelSectionListRenderer")?.firstOrNull {
                it.str("targetId") == "engagement-panel-comments-section" ||
                    it.str("panelIdentifier") == "engagement-panel-comments-section"
            }?.find("engagementPanelTitleHeaderRenderer")?.firstOrNull()
                ?.get("contextualInfo")?.text()?.withCount()
        return SongInfo(
            releaseDate = releaseDate(details.str("shortDescription")),
            views = details.str("viewCount")?.takeIf { it.toLongOrNull()?.let { n -> n >= 0 } == true },
            likes = (newLike ?: oldLike)?.withCount(),
            comments = comments,
            failed = data == null,
        )
    }

    /** Catalogue uploads explicitly state this in their description. Upload/publish dates
     * describe the video and must never substitute for a recording's release date. */
    private fun releaseDate(description: String?): String? {
        val date = description?.lineSequence()?.firstNotNullOfOrNull { line ->
            Regex("Released on:\\s*(\\d{4}-\\d{2}-\\d{2})", RegexOption.IGNORE_CASE)
                .matchEntire(line.trim())?.groupValues?.get(1)
        } ?: return null
        return runCatching { LocalDate.parse(date).toString() }.getOrNull()
    }

    /** Balanced JSON extraction tolerates braces inside titles and trailing script statements. */
    private fun extract(script: String, name: String): JsonObject? {
        val assignment = Regex("(?:var\\s+)?${name}\\s*=\\s*").find(script) ?: return null
        val start = assignment.range.last + 1
        if (script.getOrNull(start) != '{') return null
        var depth = 0
        var quoted = false
        var escaped = false
        for (i in start until script.length) {
            val c = script[i]
            if (quoted) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> if (--depth == 0) return runCatching {
                    Json.parseToJsonElement(script.substring(start, i + 1)) as? JsonObject
                }.getOrNull()
            }
        }
        return null
    }

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun String.withCount(): String? = takeIf { it.any(Char::isDigit) }
    private fun JsonElement.text(): String? = (this as? JsonObject)?.let {
        it.str("simpleText") ?: (it["runs"] as? JsonArray)?.joinToString("") { run ->
            (run as? JsonObject)?.str("text").orEmpty()
        }
    }?.takeIf { it.isNotBlank() }
    private fun JsonElement.find(key: String): List<JsonObject> = when (this) {
        is JsonObject -> listOfNotNull(this[key] as? JsonObject) + values.flatMap { it.find(key) }
        is JsonArray -> flatMap { it.find(key) }
        else -> emptyList()
    }
}
