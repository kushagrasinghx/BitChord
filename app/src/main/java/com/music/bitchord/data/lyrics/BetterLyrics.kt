package com.music.bitchord.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Word-timed lyrics from BetterLyrics — the backend behind the YouTube Music
 * browser extension of the same name.
 *
 * One key-less call keyed on title, artist and duration, answering with Apple
 * Music's own TTML. That combination is why it leads the chain: no track-id
 * lookup, no token to scrape, no login, and the timing is per-syllable.
 *
 * Note this is the extension's original host. The project's newer Cloudflare
 * API puts the same endpoint behind a Turnstile challenge, which a native
 * client has no way to answer.
 */
object BetterLyrics {

    private const val BASE = "https://lyrics-api.boidu.dev/getLyrics"

    suspend fun lyrics(
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
    ): List<LyricLine>? = withContext(Dispatchers.IO) {
        val url = BASE.toHttpUrl().newBuilder()
            .addQueryParameter("s", title)
            .addQueryParameter("a", artist)
            .apply {
                val seconds = durationMs / 1000
                if (seconds > 0) addQueryParameter("d", seconds.toString())
                if (!album.isNullOrBlank()) addQueryParameter("al", album)
            }
            .build()

        val body = lyricsGet(url.toString()) ?: return@withContext null
        val ttml = runCatching {
            (lyricsJson.parseToJsonElement(body) as? JsonObject)
                ?.get("ttml")?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: return@withContext null

        TtmlLyrics.parse(ttml).takeIf { it.isNotEmpty() }
    }
}
