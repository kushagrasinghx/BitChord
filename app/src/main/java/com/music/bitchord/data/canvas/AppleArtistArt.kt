package com.music.bitchord.data.canvas

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.AppleCatalogue
import com.music.bitchord.data.Http
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.UiState
import androidx.compose.ui.graphics.Color
import com.music.bitchord.ui.theme.ArtworkKeyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/**
 * What Apple Music's artist page shows at the top: a hero photograph, usually
 * the artist's title logo drawn over it as a separate image, and for some
 * artists a looping video that stands in for the photograph.
 *
 * [logoUrl] is a transparent PNG, null for the many artists without one;
 * [logoAspect] is its width over its height, so the caller can size it without
 * waiting for the image to decode. [videoUrl] is an HLS playlist.
 *
 * [background] and [accent] are Apple's own colours for the photograph (as
 * ARGB), so the page can be tinted without decoding the image to find them.
 */
data class AppleArtistArt(
    val heroUrl: String,
    val logoUrl: String?,
    val logoAspect: Float,
    val videoUrl: String?,
    val background: Int,
    val accent: Int,
    /** The colour Apple fills the artist's Play button with; absent for some artists. */
    val keyColor: Int?,
)

/** Apple's colours for this artist, in the form the palette takes. */
fun AppleArtistArt.keyColors() = ArtworkKeyColors(Color(background), Color(accent))

/**
 * Finds [AppleArtistArt] from nothing but an artist's name.
 *
 * Two public, unauthenticated requests: the iTunes search API turns the name
 * into an Apple Music artist id, and the artist's own music.apple.com page
 * carries its header art in the JSON it embeds for hydration. That JSON is not
 * an API Apple publishes, so every step here treats a surprise as "no art" —
 * the YouTube header is always underneath.
 *
 * A name is not an identity: Apple has a "Krsna" who is not YouTube's KR$NA,
 * and a search will happily return either. So a same-named Apple artist is only
 * taken when their catalogue shares a song or release title with the YouTube
 * page's own ([appleCatalogue]). Answers are cached per YouTube browse id,
 * since two channels with one name can deserve different answers. Most artists
 * have no logo; those, and every miss, are cached so a revisit costs nothing.
 */
object AppleArtistArtRepository {

    private const val TAG = "AppleArtistArt"
    private const val CACHE_SIZE = 64
    private const val HERO_WIDTH = 1200
    private const val LOGO_WIDTH = 1000
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    private class Entry(val art: AppleArtistArt?)

    private val cache = object : LinkedHashMap<String, Entry>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) =
            size > CACHE_SIZE
    }

    private val _updates = MutableStateFlow(0)

    /**
     * Ticks whenever a lookup settles. The page that asked for the art is not the
     * only thing coloured by it — the bars above and below the page are drawn
     * from outside it — so anything that reads [cached] re-reads on this.
     */
    val updates: StateFlow<Int> = _updates

    /**
     * What is already known for the YouTube artist [browseId], without a
     * request — null for a page never looked up *and* for one known to have
     * nothing, which is the same thing to a caller that is only deciding what
     * to draw first.
     */
    fun cached(browseId: String): AppleArtistArt? =
        synchronized(cache) { cache[browseId] }?.art

    /**
     * The header art for the YouTube artist [browseId], billed as [artistName],
     * or null when Apple has none — or none that provably is this artist.
     * [catalogue] gives the page's own song and release titles, and is only
     * asked on a cache miss since it may cost a page fetch; with none, nothing
     * can be proved and nothing is asked. Never throws.
     */
    suspend fun artFor(
        browseId: String,
        artistName: String,
        catalogue: suspend () -> Collection<String>,
    ): AppleArtistArt? {
        val key = artistName.normalized()
        if (key.isEmpty()) return null
        synchronized(cache) { cache[browseId] }?.let { return it.art }
        val titles = AppleCatalogue.keys(runCatching { catalogue() }.getOrDefault(emptyList()))
        if (titles.isEmpty()) return null

        // Null here is a network hiccup, not an answer — only a settled result
        // (including a genuine "no logo") is worth remembering.
        val entry = withContext(Dispatchers.IO) {
            runCatching { lookup(artistName, key, titles) }
                .onFailure { Log.d(TAG, "lookup failed for $artistName: $it") }
                .getOrNull()
        } ?: return null
        synchronized(cache) { cache[browseId] = entry }
        _updates.value++
        return entry.art
    }

    private fun lookup(artistName: String, key: String, titles: Set<String>): Entry? {
        val search = get(
            "https://itunes.apple.com/search?term=${URLEncoder.encode(artistName, "UTF-8")}" +
                "&entity=musicArtist&limit=5",
        ) ?: return null
        val results = JSONObject(search).optJSONArray("results") ?: JSONArray()
        var page: String? = null
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            if (r.optString("artistName").normalized() != key) continue
            val id = r.optLong("artistId").takeIf { it > 0 } ?: continue
            // Every same-named candidate is checked, not just the first: the one
            // Apple ranks highest is often the wrong one of two.
            if (!AppleCatalogue.sharesTitles(id, titles)) {
                Log.d(TAG, "$artistName: Apple ${r.optString("artistName")} ($id) shares no titles, skipped")
                continue
            }
            // The link carries the artist's slug; the bare id answers with
            // a redirect to it, one more round trip for the same page.
            page = r.optString("artistLinkUrl").substringBefore('?').takeIf { it.isNotBlank() }
                ?: "https://music.apple.com/us/artist/$id"
            break
        }
        if (page == null) return Entry(null)

        val html = get(page) ?: return null
        return Entry(parse(html))
    }

    private fun parse(html: String): AppleArtistArt? {
        val marker = html.indexOf("id=\"serialized-server-data\"")
        if (marker < 0) return null
        val start = html.indexOf('>', marker) + 1
        val end = html.indexOf("</script>", start)
        if (start <= 0 || end < 0) return null
        val header = findHeader(JSONObject(html.substring(start, end).let { "{\"d\":$it}" }).get("d"))
            ?: return null

        // Absent for most artists: the key is there and null.
        val logo = header.optJSONObject("artistLogo")?.optJSONObject("dictionary")
        val hero = (header.optJSONObject("artwork") ?: header.optJSONObject("colorBackdropArtwork"))
            ?.optJSONObject("dictionary") ?: return null

        val logoW = logo?.optInt("width") ?: 0
        val logoH = logo?.optInt("height") ?: 0
        val heroW = hero.optInt("width").takeIf { it > 0 } ?: return null
        val heroH = hero.optInt("height").takeIf { it > 0 } ?: return null

        val heroUrl = hero.optString("url").fill(
            HERO_WIDTH, (HERO_WIDTH.toLong() * heroH / heroW).toInt(), "jpg",
        ) ?: return null
        val logoUrl = if (logo != null && logoW > 0 && logoH > 0) {
            logo.optString("url").fill(LOGO_WIDTH, (LOGO_WIDTH.toLong() * logoH / logoW).toInt(), "png")
        } else {
            null
        }
        val clip = header.optJSONObject("videoArtwork")?.optJSONObject("dictionary")
            ?.optJSONObject("motionArtistSquare1x1")
        val video = clip?.optString("video")?.takeIf { it.startsWith("http") }
        // The clip is a different picture from the still, with its own colours
        // — the page has to match whichever one is on screen.
        val colours = clip?.optJSONObject("previewFrame")?.takeIf { video != null } ?: hero
        val background = colours.optString("bgColor").toArgb()
            ?: hero.optString("bgColor").toArgb() ?: return null
        val accent = colours.optString("textColor1").toArgb() ?: background
        return AppleArtistArt(
            heroUrl = heroUrl,
            logoUrl = logoUrl,
            logoAspect = if (logoUrl != null) logoW.toFloat() / logoH else 1f,
            videoUrl = video,
            background = background,
            accent = accent,
            keyColor = header.optString("keyColor").toArgb(),
        )
    }

    /** The first object anywhere in [node] that has an `artistLogo` key. */
    private fun findHeader(node: Any?): JSONObject? {
        when (node) {
            is JSONObject -> {
                if (node.has("artistLogo")) return node
                val keys = node.keys()
                while (keys.hasNext()) findHeader(node.opt(keys.next()))?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findHeader(node.opt(i))?.let { return it }
        }
        return null
    }

    /** Apple's colours are six hex digits with no prefix. */
    private fun String.toArgb(): Int? =
        takeIf { it.length == 6 }?.toIntOrNull(16)?.let { 0xFF000000.toInt() or it }

    private fun String.fill(w: Int, h: Int, format: String): String? {
        if (isBlank() || !contains("{w}")) return null
        return replace("{w}", w.toString())
            .replace("{h}", h.toString())
            .replace("{c}", "bb")
            .replace("{f}", format)
    }

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun String.normalized(): String = AppleCatalogue.matchKey(this)
}

/**
 * The titles an artist page vouches for, as the evidence
 * [AppleArtistArtRepository.artFor] checks Apple's artist against.
 */
fun DetailPage.appleCatalogue(): List<String> =
    AppleCatalogue.titlesOf((songs as? UiState.Success)?.data.orEmpty(), sections)
