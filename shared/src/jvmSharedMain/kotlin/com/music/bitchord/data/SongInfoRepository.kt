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

data class SongCredit(val role: String, val names: List<String>) {
    val text: String get() = names.joinToString(", ")
}

data class SongInfo(
    val album: String? = null,
    val releaseYear: String? = null,
    val releaseDate: String? = null,
    val views: String? = null,
    val likes: String? = null,
    val comments: String? = null,
    val credits: List<SongCredit> = emptyList(),
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
                    // Without a consent answer an EU request is redirected to
                    // consent.youtube.com, whose page has no player response.
                    // SOCS=CAI is "reject all": it answers the question and
                    // opts into nothing.
                    header("Cookie", "SOCS=CAI")
                    parameter("v", song.videoId)
                    parameter("hl", Innertube.currentLanguage)
                }
                check(response.status.value == 200)
                SongInfoParser.parse(response.bodyAsText(), song.videoId)
            }
        }
        val credits = async { attempt { fetchCredits(song.videoId) }.getOrDefault(emptyList()) }
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
        val stat = statistics.await().getOrNull()
        val ytmCredits = credits.await()
        (stat ?: SongInfo(failed = true)).copy(
            album = album,
            releaseYear = year,
            credits = ytmCredits.ifEmpty { stat?.credits.orEmpty() },
        ).let { it.copy(failed = it.failed || release.isFailure) }
    }

    private suspend fun fetchCredits(videoId: String): List<SongCredit> {
        // Direct credits browse: reliable for catalog tracks on YouTube Music.
        val direct = runCatching { parseCreditsPage(Innertube.browse("MPTC$videoId")) }.getOrDefault(emptyList())
        if (direct.isNotEmpty()) return direct
        val next = Innertube.next(videoId)
        val endpoint = findCreditsEndpoint(next, videoId) ?: return emptyList()
        val browseId = (endpoint["browseId"] as? JsonPrimitive)?.contentOrNull ?: return emptyList()
        val params = (endpoint["params"] as? JsonPrimitive)?.contentOrNull
        val page = Innertube.browse(browseId, params)
        return parseCreditsPage(page)
    }

    /**
     * Finds the credits browse endpoint in a `next` response. Prefers an endpoint
     * whose page type marks it as track credits, falling back to any menu
     * navigation whose text suggests credits.
     */
    internal fun findCreditsEndpoint(root: JsonElement, videoId: String): JsonObject? {
        fun JsonObject.pageType(): String =
            this["browseEndpointContextSupportedConfigs"]
                ?.let { it as? JsonObject }?.get("browseEndpointContextMusicConfig")
                ?.let { it as? JsonObject }?.get("pageType")
                ?.let { it as? JsonPrimitive }?.contentOrNull.orEmpty()

        fun isCreditsBrowse(browse: JsonObject): Boolean {
            val id = (browse["browseId"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            return "CREDIT" in browse.pageType() || id.startsWith("MPTC")
        }

        val rows = objectsNamed(root, "playlistPanelVideoRenderer")
            .filter { (it["videoId"] as? JsonPrimitive)?.contentOrNull == videoId }
            .toList()
            .ifEmpty { objectsNamed(root, "playlistPanelVideoRenderer").toList() }
        val menuScopes = (rows + listOfNotNull(root as? JsonObject))
        for (scope in menuScopes) {
            val items = objectsNamed(scope, "menuNavigationItemRenderer").toList()
            val endpoint = items.firstNotNullOfOrNull { nav ->
                val browse = (nav["navigationEndpoint"] as? JsonObject)?.get("browseEndpoint") as? JsonObject
                    ?: return@firstNotNullOfOrNull null
                if (isCreditsBrowse(browse)) browse else null
            } ?: items.firstNotNullOfOrNull { nav ->
                val browse = (nav["navigationEndpoint"] as? JsonObject)?.get("browseEndpoint") as? JsonObject
                    ?: return@firstNotNullOfOrNull null
                if (nav.runsText().contains("credit", ignoreCase = true)) browse else null
            }
            if (endpoint != null) return endpoint
        }
        return objectsNamed(root, "browseEndpoint").firstOrNull(::isCreditsBrowse)
    }

    internal fun parseCreditsPage(root: JsonElement): List<SongCredit> {
        val credits = mutableListOf<SongCredit>()
        // dismissableDialogContentSectionRenderer: the song credits browse page (MPTC*)
        for (section in objectsNamed(root, "dismissableDialogContentSectionRenderer")) {
            val role = section["title"]?.runsText()?.trim().orEmpty()
            val names = splitNames(listOf(section["subtitle"]?.runsText().orEmpty()))
            if (role.isNotBlank() && names.isNotEmpty()) credits += SongCredit(role, names)
        }
        if (credits.isNotEmpty()) return credits.distinctBy { it.role.lowercase() to it.text.lowercase() }
        // musicResponsiveListItemRenderer rows: flex column 0 = role, 1+ = names
        for (item in objectsNamed(root, "musicResponsiveListItemRenderer")) {
            val flex = ((item["flexColumns"] as? JsonArray) ?: JsonArray(emptyList())).toList()
            val role = flex.getOrNull(0)?.rendererText()?.trim().orEmpty()
            val names = if (flex.size > 1) {
                flex.drop(1).flatMap { splitNames(listOf(it.rendererText())) }
            } else {
                splitNames(listOf(item["subtitle"]?.runsText().orEmpty()))
            }
            if (role.isNotBlank() && names.isNotEmpty()) credits += SongCredit(role, names)
        }
        if (credits.isNotEmpty()) return credits.distinctBy { it.role.lowercase() to it.text.lowercase() }
        // Fallback: shelf-based layouts
        for (shelf in objectsNamed(root, "musicShelfRenderer") + objectsNamed(root, "musicCarouselShelfRenderer")) {
            val header = shelf["header"] as? JsonObject
            val role = header?.runsText()?.trim().orEmpty()
            val contents = ((shelf["contents"] as? JsonArray) ?: JsonArray(emptyList())).toList()
            val names = contents.flatMap { c ->
                splitNames(listOf((c as? JsonObject)?.runsText().orEmpty()))
            }
            if (role.isNotBlank() && names.isNotEmpty()) credits += SongCredit(role, names)
        }
        return credits.distinctBy { it.role.lowercase() to it.text.lowercase() }
    }

    private fun JsonElement.rendererText(): String =
        objectsNamed(this, "text").firstNotNullOfOrNull { it.runsText().takeIf(String::isNotBlank) }
            ?: runsText()

    private fun JsonElement.runsText(): String = when (this) {
        is JsonPrimitive -> contentOrNull.orEmpty()
        is JsonArray -> joinToString("") { it.runsText() }
        is JsonObject -> (this["simpleText"] as? JsonPrimitive)?.contentOrNull
            ?: (this["runs"] as? JsonArray)?.joinToString("") { run ->
                ((run as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull.orEmpty()
            }
            ?: this["text"]?.runsText()
            ?: ""
    }

    private fun splitNames(strings: List<String>): List<String> {
        val joined = strings.joinToString("")
        if (joined.isBlank()) return emptyList()
        return joined.split(Regex("\\r?\\n+|\\s*[•·]\\s*|\\s*,\\s+(?=[\\p{Lu}\"'])"))
            .map { it.trim().trimEnd(',', '•', '·') }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(joined.trim()) }
    }

    private fun objectsNamed(root: JsonElement, name: String): Sequence<JsonObject> = sequence {
        when (root) {
            is JsonObject -> for ((key, value) in root) {
                if (key == name && value is JsonObject) yield(value)
                yieldAll(objectsNamed(value, name))
            }
            is JsonArray -> for (value in root) yieldAll(objectsNamed(value, name))
            else -> Unit
        }
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
            credits = parseMusicSectionCredits(data),
            failed = data == null,
        )
    }

    private fun parseMusicSectionCredits(data: JsonObject?): List<SongCredit> {
        if (data == null) return emptyList()
        val sections = data.find("videoDescriptionMusicSectionRenderer")
        if (sections.isEmpty()) return emptyList()
        val credits = mutableListOf<SongCredit>()
        for (section in sections) {
            val rows = section.find("infoRowRenderer") + section.find("metadataRowRenderer") + section.find("premiumMetadataRowRenderer")
            for (row in rows) {
                val role = row["title"]?.text() ?: continue
                val values = listOfNotNull(
                    row["defaultMetadata"]?.text(),
                    row["expandedMetadata"]?.text(),
                    row["contents"]?.text(),
                    row["content"]?.text(),
                ).joinToString("").replace('\n', ' ').trim()
                val names = values.split(Regex("\\s*[•·]\\s*|\\s*,\\s*(?=[A-Z])"))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .ifEmpty { listOf(values).filter { it.isNotBlank() } }
                if (role.isNotBlank() && names.isNotEmpty()) credits += SongCredit(role, names)
            }
            if (credits.isEmpty()) {
                for (lockup in section.find("carouselLockupRenderer")) {
                    for (row in lockup.find("infoRowRenderer")) {
                        val role = row["title"]?.text() ?: continue
                        val values = listOfNotNull(row["defaultMetadata"]?.text(), row["expandedMetadata"]?.text())
                            .joinToString("").trim()
                        if (role.isNotBlank() && values.isNotBlank()) credits += SongCredit(role, listOf(values))
                    }
                }
            }
        }
        return credits.distinctBy { it.role.lowercase() to it.text.lowercase() }
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
