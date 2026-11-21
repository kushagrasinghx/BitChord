package com.music.bitchord.data.lyrics

import com.music.bitchord.data.innertube.Innertube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** Lyrics exposed by YouTube Music's Lyrics tab for the exact playing video. */
object YouTubeMusicLyrics {
    suspend fun lyrics(videoId: String): List<LyricLine>? = withContext(Dispatchers.IO) {
        if (!YOUTUBE_ID.matches(videoId)) return@withContext null
        val next = runCatching { Innertube.next(videoId) }.getOrNull() ?: return@withContext null
        val endpoint = next.objectsNamed("tabRenderer")
            .firstOrNull { it.youtubeStrings().any { text -> text.equals("Lyrics", ignoreCase = true) } }
            ?.objectsNamed("browseEndpoint")?.firstOrNull()
            ?: next.objectsNamed("tabRenderer").drop(1).firstNotNullOfOrNull {
                it.objectsNamed("browseEndpoint").firstOrNull()
            }
            ?: return@withContext null
        val browseId = (endpoint["browseId"] as? JsonPrimitive)?.contentOrNull
            ?: return@withContext null
        val params = (endpoint["params"] as? JsonPrimitive)?.contentOrNull
        // The web client is answered with the plain description shelf even
        // when the track has timed lyrics; only the mobile client gets the
        // timedLyricsModel. Ask as the phone first, then fall back to the
        // shelf it returns (if any) or the web client's, which always has it.
        val mobile = runCatching { Innertube.browseMobile(browseId, params) }.getOrNull()
        mobile?.let(::timedLyrics)?.let { return@withContext it }
        mobile?.let(::plainLyrics)?.let { return@withContext it }
        val web = runCatching { Innertube.browse(browseId, params) }.getOrNull()
        web?.let(::plainLyrics)
    }
}

/** YouTube Music's timed lyrics, where the track has them. */
internal fun timedLyrics(page: JsonObject): List<LyricLine>? {
    val data = findArray(page, "timedLyricsData") ?: return null
    return data.mapNotNull { element ->
        val line = element as? JsonObject ?: return@mapNotNull null
        val text = (line["lyricLine"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (text.isEmpty()) return@mapNotNull null
        val cue = line["cueRange"] as? JsonObject
        val start = (cue?.get("startTimeMilliseconds") as? JsonPrimitive)
            ?.contentOrNull?.toLongOrNull() ?: return@mapNotNull null
        val end = (cue?.get("endTimeMilliseconds") as? JsonPrimitive)
            ?.contentOrNull?.toLongOrNull()
        LyricLine(start, text, sungUntilMs = end)
    }.sortedBy { it.timeMs }.takeIf { it.isNotEmpty() }
}

/** The untimed description shelf the web client gets; the old behaviour. */
internal fun plainLyrics(page: JsonObject): List<LyricLine>? {
    val shelf = page.objectsNamed("musicDescriptionShelfRenderer").firstOrNull()
        ?: return null
    val text = shelf["description"]?.youtubeStrings()?.joinToString("").orEmpty().trim()
    return text.lineSequence().map(String::trim).filter(String::isNotEmpty)
        .map { LyricLine(0L, it) }.toList().takeIf { it.isNotEmpty() }
}

/** First array named [name] anywhere in [element], depth-first. */
private fun findArray(element: JsonElement, name: String): JsonArray? = when (element) {
    is JsonObject -> (element[name] as? JsonArray)
        ?: element.values.firstNotNullOfOrNull { findArray(it, name) }
    is JsonArray -> element.firstNotNullOfOrNull { findArray(it, name) }
    else -> null
}

/** Timed YouTube transcript/captions for the exact playing video. */
object YouTubeTranscriptLyrics {
    suspend fun lyrics(videoId: String): List<LyricLine>? = withContext(Dispatchers.IO) {
        if (!YOUTUBE_ID.matches(videoId)) return@withContext null
        val response = runCatching { Innertube.transcript(videoId) }.getOrNull()
            ?: return@withContext null
        response.objectsNamed("transcriptCueRenderer").mapNotNull { cue ->
            val start = (cue["startOffsetMs"] as? JsonPrimitive)?.longOrNull
                ?: return@mapNotNull null
            val text = cue["cue"]?.youtubeStrings()?.joinToString("").orEmpty()
                .trim(' ', '\n', '♪')
            text.takeIf { it.isNotEmpty() }?.let { LyricLine(start, it) }
        }.sortedBy { it.timeMs }.toList().takeIf { it.isNotEmpty() }
    }
}

private val YOUTUBE_ID = Regex("""[A-Za-z0-9_-]{11}""")

private fun JsonElement.objectsNamed(name: String): Sequence<JsonObject> = sequence {
    when (this@objectsNamed) {
        is JsonObject -> for ((key, value) in this@objectsNamed) {
            if (key == name && value is JsonObject) yield(value)
            yieldAll(value.objectsNamed(name))
        }
        is JsonArray -> for (value in this@objectsNamed) yieldAll(value.objectsNamed(name))
        else -> Unit
    }
}

fun JsonElement.youtubeStrings(): List<String> = when (this) {
    is JsonPrimitive -> contentOrNull?.let(::listOf).orEmpty()
    is JsonArray -> flatMap { it.youtubeStrings() }
    is JsonObject -> {
        val direct = (this["text"] as? JsonPrimitive)?.contentOrNull
            ?: (this["simpleText"] as? JsonPrimitive)?.contentOrNull
        direct?.let(::listOf) ?: values.flatMap { it.youtubeStrings() }
    }
}
