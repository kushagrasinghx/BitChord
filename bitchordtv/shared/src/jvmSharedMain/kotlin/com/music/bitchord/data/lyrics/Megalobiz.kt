package com.music.bitchord.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Line-synced community LRC scraped from Megalobiz. */
object Megalobiz {
    private const val BASE = "https://www.megalobiz.com"

    suspend fun lyrics(title: String, artist: String): List<LyricLine>? = withContext(Dispatchers.IO) {
        val search = "$BASE/searchall".toHttpUrl().newBuilder()
            .addQueryParameter("qry", "$artist $title".trim()).build()
        val results = lyricsGet(search.toString()) ?: return@withContext null
        val path = LRC_LINK.find(results)?.groupValues?.get(1) ?: return@withContext null
        val page = lyricsGet(BASE + path.replace("&amp;", "&")) ?: return@withContext null
        val raw = LRC_BODY.find(page)?.groupValues?.get(1) ?: return@withContext null
        val lrc = raw.replace(Regex("""(?i)<br\s*/?>"""), "\n")
            .replace(Regex("""<[^>]+>"""), "")
            .let(EnhancedLrc::decodeEntities)
        LrcLib.parseLrc(lrc).takeIf { lines -> lines.any { it.text.isNotBlank() } }
    }

    private val LRC_LINK = Regex("""href=[\"'](/lrc/maker/download/[^\"']+)[\"']""", RegexOption.IGNORE_CASE)
    private val LRC_BODY = Regex(
        """id=[\"']lrc_[^\"']*_details[\"'][^>]*>(.*?)</span>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
}
