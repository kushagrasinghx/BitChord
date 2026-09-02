package com.music.bitchord.data.lyrics

import com.music.bitchord.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Web scraper for Genius.com lyrics.
 *
 * Used strictly as a last resort fallback when none of the time-synced providers
 * (LRCLIB, Musixmatch, BetterLyrics, KuGou, etc.) have lyrics for a track.
 *
 * Genius does not offer timestamps, but carries an immense catalogue. The scraper:
 *  1. Searches Genius's open multi-search endpoint (no API key required).
 *  2. Matches the best candidate song and grabs its webpage URL.
 *  3. Fetches the page HTML and extracts lyrics from `data-lyrics-container` tags via Jsoup.
 *  4. Cleans out Genius metadata (headers, translation links, "You might also like", "Embed").
 *  5. Retains section headers (e.g. "[Verse 1]", "[Chorus]") for beautiful formatted display.
 */
object Genius {

    private const val BROWSER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val httpClient by lazy {
        Http.client.newBuilder()
            .callTimeout(8, TimeUnit.SECONDS)
            .connectTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    suspend fun lyrics(title: String, artist: String): List<LyricLine>? = withContext(Dispatchers.IO) {
        LyricsLog.i("Genius", "Fallback scraper triggered for: \"$title\" by \"$artist\"")
        val cleanTitle = cleanQuery(title)
        val cleanArtist = cleanQuery(artist)

        val songUrl = searchSongUrl(cleanTitle, cleanArtist)
        if (songUrl == null) {
            LyricsLog.w("Genius", "No matching song found on Genius")
            return@withContext null
        }

        LyricsLog.i("Genius", "Found song page: $songUrl")
        val html = fetchHtml(songUrl)
        if (html.isNullOrBlank()) {
            LyricsLog.e("Genius", "Failed to fetch HTML from song page")
            return@withContext null
        }

        val lines = parseHtml(html)
        if (lines.isNullOrEmpty()) {
            LyricsLog.w("Genius", "HTML parsed but no lyric lines could be extracted")
            return@withContext null
        }

        val sectionCount = lines.count { isSectionHeader(it.text) }
        val sungCount = lines.count { !it.isGap && !isSectionHeader(it.text) }
        LyricsLog.s("Genius", "Successfully scraped $sungCount lines and $sectionCount sections")
        lines
    }

    /**
     * Searches Genius for the track and returns the song's page URL.
     */
    internal fun searchSongUrl(cleanTitle: String, cleanArtist: String): String? {
        val query = "$cleanArtist $cleanTitle".trim()
        val url = "https://genius.com/api/search/multi?q=${URLEncoder.encode(query, "UTF-8")}"
        LyricsLog.i("Genius", "Querying Genius search API: $query")

        val responseBody = httpGet(url) ?: run {
            LyricsLog.w("Genius", "Search API request failed")
            return null
        }

        return runCatching {
            val root = json.parseToJsonElement(responseBody).jsonObject
            val responseObj = root["response"]?.jsonObject ?: return null
            val sections = responseObj["sections"]?.jsonArray ?: return null

            val songSection = sections.firstOrNull {
                (it as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "song"
            }?.jsonObject ?: return null

            val hits = songSection["hits"]?.jsonArray ?: return null
            val candidates = hits.mapNotNull { (it as? JsonObject)?.get("result")?.jsonObject }

            val best = bestMatch(candidates, cleanTitle, cleanArtist)
            best?.get("url")?.jsonPrimitive?.contentOrNull
        }.onFailure {
            LyricsLog.e("Genius", "Failed to parse search response: ${it.message}")
        }.getOrNull()
    }

    private fun bestMatch(
        candidates: List<JsonObject>,
        targetTitle: String,
        targetArtist: String,
    ): JsonObject? {
        if (candidates.isEmpty()) return null
        val normTitle = targetTitle.lowercase(Locale.ROOT)
        val normArtist = targetArtist.lowercase(Locale.ROOT)

        return candidates.maxByOrNull { item ->
            val title = item["title"]?.jsonPrimitive?.contentOrNull?.lowercase(Locale.ROOT) ?: ""
            val artist = item["artist_names"]?.jsonPrimitive?.contentOrNull?.lowercase(Locale.ROOT) ?: ""
            var score = 0

            if (title == normTitle) score += 50
            else if (title.contains(normTitle) || normTitle.contains(title)) score += 25

            if (artist.contains(normArtist) || normArtist.contains(artist)) score += 40

            // Penalize translations / instrumentals / reviews unless specifically requested
            val path = item["path"]?.jsonPrimitive?.contentOrNull ?: ""
            if (path.contains("translation", ignoreCase = true) && !normTitle.contains("translation")) score -= 30
            if (path.contains("türkçe", ignoreCase = true) || path.contains("polskie-tlumaczenie", ignoreCase = true)) score -= 40
            if (path.contains("tracklist", ignoreCase = true) || path.contains("album-art", ignoreCase = true)) score -= 50

            score
        }
    }

    /**
     * Parses the Genius lyrics HTML page using Jsoup, extracting text while preserving
     * stanzas and section headers.
     */
    internal fun parseHtml(html: String): List<LyricLine>? {
        val doc = Jsoup.parse(html)

        // Modern Genius uses data-lyrics-container="true", older pages use div.lyrics
        var containers = doc.select("div[data-lyrics-container=true]")
        if (containers.isEmpty()) {
            containers = doc.select("div.lyrics")
        }
        if (containers.isEmpty()) return null

        val fullTextBuilder = StringBuilder()

        for (container in containers) {
            // Remove headers, contributors, translation links, buttons, ads
            container.select(
                "[data-exclude-from-selection=true], " +
                    ".LyricsHeader__Container, " +
                    ".SongBioPreview__Container, " +
                    ".InreadAd__Container, " +
                    "button, " +
                    "script, " +
                    "style",
            ).remove()

            // Turn <br> into explicit newlines before extracting text
            container.select("br").forEach { it.replaceWith(TextNode("\n")) }
            container.select("p").forEach { it.prepend("\n") }

            val text = container.wholeText()
            if (text.isNotBlank()) {
                fullTextBuilder.append(text).append("\n")
            }
        }

        val rawLyrics = fullTextBuilder.toString()
        if (rawLyrics.isBlank()) return null

        val cleaned = stripArtifacts(rawLyrics)
        return textToLyricLines(cleaned)
    }

    /**
     * Cleans common web scraper artifacts found on Genius:
     * - "You might also like" insertions
     * - Trailing "Embed" or "[0-9]+Embed"
     * - Non-breaking or zero-width unicode spaces
     */
    internal fun stripArtifacts(raw: String): String {
        return raw
            // Replace non-breaking / special unicode spaces with normal space
            .replace('\u00A0', ' ')
            .replace('\u200B', ' ')
            .replace('\uFEFF', ' ')
            // Remove "You might also like" mid-text recommendations
            .replace(YOU_MIGHT_ALSO_LIKE, "")
            .trim()
            // Remove trailing "123Embed" or "Embed" at the very end
            .replace(TRAILING_EMBED, "")
            .trim()
    }

    /**
     * Converts clean multi-line lyrics text into structured [LyricLine]s.
     */
    internal fun textToLyricLines(text: String): List<LyricLine> {
        val lines = text.lines()
        val result = mutableListOf<LyricLine>()
        var lastWasGap = false

        for (rawLine in lines) {
            val line = rawLine.trim().replace(TRAILING_EMBED, "").trim()
            if (line.isEmpty()) {
                if (!lastWasGap && result.isNotEmpty()) {
                    result.add(LyricLine(timeMs = 0L, text = ""))
                    lastWasGap = true
                }
            } else {
                result.add(LyricLine(timeMs = 0L, text = line))
                lastWasGap = false
            }
        }

        // Drop leading and trailing gaps
        while (result.isNotEmpty() && result.first().isGap) result.removeAt(0)
        while (result.isNotEmpty() && result.last().isGap) result.removeAt(result.lastIndex)

        return result
    }

    /**
     * Checks if a line is a section header like "[Verse 1]", "[Chorus]", "[Bridge]".
     */
    fun isSectionHeader(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length in 3..60
    }

    private fun cleanQuery(text: String): String = text
        .replace(NOISE, " ")
        .substringBefore(" | ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { text }

    private fun httpGet(url: String): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/json,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }.getOrNull()

    private fun fetchHtml(url: String): String? = httpGet(url)

    private val NOISE = Regex(
        """\((?:from|feat\.?|official|lyrical|video|audio|remix|music video|visualizer)[^)]*\)|\[[^]]*]|""" +
            """\b(?:official (?:video|audio|music video)|lyrical|full song|4k video)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val YOU_MIGHT_ALSO_LIKE = Regex("""\d*You might also like""", RegexOption.IGNORE_CASE)
    private val TRAILING_EMBED = Regex("""\d*Embed\s*$""", RegexOption.IGNORE_CASE)
}
