package com.music.bitchord.data.lyrics

import com.music.bitchord.data.Http
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Resolves the *untranslated* YouTube title/artist for a track before lyrics
 * providers are asked.
 *
 * YouTube Music localises track titles (the same translated-title problem the
 * YouTube-No-Translation browser extension tackles — credit for the layered
 * idea: https://github.com/YouG-o/YouTube-No-Translation). Lyrics catalogues
 * index the original wording, so searching with a translated title misses.
 * This resolver is an independent implementation of that idea, shaped for a
 * lyrics lookup rather than a web page:
 *
 * 1. oEmbed (`youtube.com/oembed`, no key) — answers with the original
 *    `title` / `author_name` in the common case;
 * 2. an unauthenticated Innertube `player` call with `hl=en` — the
 *    `videoDetails` block carries the untranslated title/author;
 * 3. the caller-supplied title/artist unchanged.
 *
 * Results are cached in memory per videoId. Every layer is time-boxed so a
 * slow network can never hold up the lyric lookup behind it.
 */
object OriginalTitleResolver {

    data class Resolved(
        val title: String,
        val artist: String,
        /** True when either field came back different from what was given. */
        val fromOriginal: Boolean,
    )

    private const val TOTAL_TIMEOUT_MS = 6_000L
    private const val WEB_REMIX_VERSION = "1.20250101.01.00"
    private const val WEB_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

    private val client by lazy {
        Http.client.newBuilder()
            .callTimeout(8, TimeUnit.SECONDS)
            .connectTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    private val memory = object {
        private val entries = object : LinkedHashMap<String, Resolved>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Resolved>) = size > 64
        }

        @Synchronized fun get(key: String): Resolved? = entries[key]

        @Synchronized fun put(key: String, value: Resolved) {
            entries[key] = value
        }
    }

    suspend fun resolve(videoId: String, fallbackTitle: String, fallbackArtist: String): Resolved =
        withContext(Dispatchers.IO) {
            memory.get(videoId)?.let { return@withContext it }
            val resolved = withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
                fromOEmbed(videoId) ?: fromPlayer(videoId)
            }?.takeIf { it.first.isNotBlank() }
            val out = if (resolved == null) {
                Resolved(fallbackTitle, fallbackArtist, fromOriginal = false)
            } else {
                val (title, artist) = resolved
                Resolved(
                    title = title.ifBlank { fallbackTitle },
                    artist = artist.ifBlank { fallbackArtist },
                    fromOriginal = title.isNotBlank() && title.trim() != fallbackTitle.trim() ||
                        artist.isNotBlank() && artist.trim() != fallbackArtist.trim(),
                )
            }
            memory.put(videoId, out)
            out
        }

    private fun fromOEmbed(videoId: String): Pair<String, String>? = runCatching {
        val url = "https://www.youtube.com/oembed?url=" +
            java.net.URLEncoder.encode("https://www.youtube.com/watch?v=$videoId", "UTF-8") +
            "&format=json"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", WEB_USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val root = lyricsJson.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            val title = root["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val author = root["author_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (title.isBlank() && author.isBlank()) null else title to author
        }
    }.getOrNull()

    private fun fromPlayer(videoId: String): Pair<String, String>? = runCatching {
        val payload = """
            {"context":{"client":{"clientName":"WEB_REMIX","clientVersion":"$WEB_REMIX_VERSION","hl":"en","gl":"US"}},
             "videoId":"$videoId","racyCheckOk":true,"contentCheckOk":true}
        """.trimIndent()
        val request = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/player?prettyPrint=false")
            .header("User-Agent", WEB_USER_AGENT)
            .header("Accept", "application/json")
            .header("X-Origin", "https://music.youtube.com")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val details = runCatching {
                lyricsJson.parseToJsonElement(response.body?.string().orEmpty())
                    .jsonObject["videoDetails"]?.jsonObject
            }.getOrNull() ?: return null
            val title = details["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val author = details["author"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (title.isBlank() && author.isBlank()) null else title to author
        }
    }.getOrNull()
}
