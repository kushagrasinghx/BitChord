/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lastfm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object CatalogueCoverProvider {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun resolveCoverUrl(
        title: String,
        artist: String?,
    ): String? {
        if (title.isBlank()) return null
        val cleanedTitle = cleanSearchTitle(title)
        val cleanedArtist = artist?.takeIf(String::isNotBlank)?.let(::cleanSearchArtist)
        return withContext(Dispatchers.IO) {
            iTunesCoverUrl(cleanedTitle, cleanedArtist)
                ?: deezerCoverUrl(cleanedTitle, cleanedArtist)
                ?: lastFmTrackInfoCoverUrl(cleanedTitle, cleanedArtist)
                ?: coverArtArchiveCoverUrl(cleanedTitle, cleanedArtist)
                ?: spotifyOEmbedCoverUrl(cleanedTitle, cleanedArtist)
        }
    }

    suspend fun iTunesCoverUrl(
        title: String,
        artist: String?,
    ): String? =
        withContext(Dispatchers.IO) {
            if (title.isBlank()) return@withContext null
            val term = listOfNotNull(artist?.takeIf(String::isNotBlank), title).joinToString(" ")
            val url =
                "https://itunes.apple.com/search".toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("term", term)
                    .addQueryParameter("entity", "song")
                    .addQueryParameter("limit", "3")
                    .build()
            val response =
                runCatching {
                    client.newCall(Request.Builder().url(url).get().build()).execute()
                }.getOrNull() ?: return@withContext null
            response.use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body =
                    runCatching { resp.body?.string() }.getOrNull()
                        ?: return@withContext null
                val parsed = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return@withContext null
                val results: JsonArray = parsed["results"]?.jsonArray ?: return@withContext null

                val first = results
                    .firstOrNull { entry ->
                        val trackName = entry.jsonObject["trackName"]?.jsonPrimitive?.contentOrNull.orEmpty().lowercase()
                        trackName.contains(title.lowercase()) || title.lowercase().contains(trackName)
                    }?.jsonObject
                    ?: results.firstOrNull()?.jsonObject
                    ?: return@withContext null
                val art = first["artworkUrl100"]?.jsonPrimitive?.contentOrNull
                    ?: first["artworkUrl60"]?.jsonPrimitive?.contentOrNull
                    ?: return@withContext null

                art.replace("100x100bb", "600x600bb")
                    .replace("60x60bb", "600x600bb")
                    .replace("30x30bb", "600x600bb")
                    .ifBlank { null }
            }
        }

    suspend fun deezerCoverUrl(
        title: String,
        artist: String?,
    ): String? =
        withContext(Dispatchers.IO) {
            if (title.isBlank()) return@withContext null

            val q =
                if (!artist.isNullOrBlank()) {
                    "artist:\"${artist.replace("\"", "")}\" track:\"${title.replace("\"", "")}\""
                } else {
                    title
                }
            val url =
                "https://api.deezer.com/search".toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("q", q)
                    .addQueryParameter("limit", "3")
                    .build()
            val response =
                runCatching {
                    client.newCall(Request.Builder().url(url).get().build()).execute()
                }.getOrNull() ?: return@withContext null
            response.use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body =
                    runCatching { resp.body?.string() }.getOrNull()
                        ?: return@withContext null
                val parsed = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return@withContext null
                val data: JsonArray = parsed["data"]?.jsonArray ?: return@withContext null
                val first = data.firstOrNull()?.jsonObject ?: return@withContext null
                val album = first["album"]?.jsonObject ?: return@withContext null
                album["cover_big"]?.jsonPrimitive?.contentOrNull
                    ?: album["cover_medium"]?.jsonPrimitive?.contentOrNull
                    ?: album["cover_xl"]?.jsonPrimitive?.contentOrNull
            }
        }

    suspend fun lastFmTrackInfoCoverUrl(
        title: String,
        artist: String?,
    ): String? =
        withContext(Dispatchers.IO) {
            if (title.isBlank() || artist.isNullOrBlank()) return@withContext null
            val config = LastFM.currentConfig()
            if (config.apiKey.isBlank() || config.apiKey == LastFM.FALLBACK_COMPAT_API_KEY) return@withContext null
            val url =
                config.endpoint.toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("method", "track.getInfo")
                    .addQueryParameter("api_key", config.apiKey)
                    .addQueryParameter("artist", artist)
                    .addQueryParameter("track", title)
                    .addQueryParameter("format", "json")
                    .build()
            val response =
                runCatching {
                    client.newCall(Request.Builder().url(url).get().build()).execute()
                }.getOrNull() ?: return@withContext null
            response.use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body =
                    runCatching { resp.body?.string() }.getOrNull()
                        ?: return@withContext null
                val parsed = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return@withContext null
                val track = parsed["track"]?.jsonObject ?: return@withContext null
                val album = track["album"]?.jsonObject ?: return@withContext null
                val images: JsonArray = album["image"]?.jsonArray ?: return@withContext null

                val sizeOrder = listOf("extralarge", "large", "medium", "mega", "small")
                for (size in sizeOrder) {
                    val match =
                        images.firstOrNull { entry ->
                            entry.jsonObject["size"]?.jsonPrimitive?.contentOrNull == size &&
                                !entry.jsonObject["#text"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()
                        }?.jsonObject
                    val text = match?.get("#text")?.jsonPrimitive?.contentOrNull
                    if (!text.isNullOrBlank()) return@use text
                }
                null
            }
        }

    suspend fun coverArtArchiveCoverUrl(
        title: String,
        artist: String?,
    ): String? =
        withContext(Dispatchers.IO) {
            if (title.isBlank()) return@withContext null
            val query = buildString {
                if (!artist.isNullOrBlank()) {
                    append("artist:\"")
                    append(artist.replace("\"", ""))
                    append("\" AND ")
                }
                append("recording:\"")
                append(title.replace("\"", ""))
                append("\"")
            }
            val searchUrl =
                "https://musicbrainz.org/ws/2/recording/".toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("query", query)
                    .addQueryParameter("limit", "1")
                    .addQueryParameter("inc", "releases")
                    .addQueryParameter("fmt", "json")
                    .build()
            val searchResponse =
                runCatching {
                    client.newCall(
                        Request.Builder()
                            .url(searchUrl)
                            .header("User-Agent", "ArchiveTune/1.0 (https://github.com/rukamori/ArchiveTune)")
                            .get()
                            .build(),
                    ).execute()
                }.getOrNull() ?: return@withContext null
            val mbid: String? =
                searchResponse.use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = runCatching { resp.body?.string() }.getOrNull() ?: return@use null
                    val parsed = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return@use null
                    val recordings = parsed["recordings"]?.jsonArray ?: return@use null
                    val first = recordings.firstOrNull()?.jsonObject ?: return@use null
                    val releases = first["releases"]?.jsonArray ?: return@use null
                    val release = releases.firstOrNull()?.jsonObject ?: return@use null
                    release["id"]?.jsonPrimitive?.contentOrNull
                }
            if (mbid.isNullOrBlank()) return@withContext null

            val coverUrl = "https://coverartarchive.org/release/$mbid/front"
            val headResponse =
                runCatching {
                    client.newCall(
                        Request.Builder()
                            .url(coverUrl)
                            .head()
                            .build(),
                    ).execute()
                }.getOrNull() ?: return@withContext null
            headResponse.use { resp ->
                if (resp.code != 200 && resp.code != 307 && resp.code != 302) return@withContext null

                val finalUrl = resp.request.url.toString()
                if (finalUrl != coverUrl && finalUrl.startsWith("http")) finalUrl else null
            }
        }

    suspend fun spotifyOEmbedCoverUrl(
        title: String,
        artist: String?,
    ): String? =
        withContext(Dispatchers.IO) {
            if (title.isBlank()) return@withContext null
            val term = listOfNotNull(artist?.takeIf(String::isNotBlank), title).joinToString(" ")

            val searchUrl =
                "https://open.spotify.com/search/${
                    java.net.URLEncoder.encode(term, "UTF-8").replace("+", "%20")
                }/tracks"
            val response =
                runCatching {
                    client.newCall(
                        Request.Builder()
                            .url(searchUrl)
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                            .get()
                            .build(),
                    ).execute()
                }.getOrNull() ?: return@withContext null
            response.use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = runCatching { resp.body?.string() }.getOrNull() ?: return@withContext null

                val ogImageMatch = Regex(
                    "<meta[^>]+property=\"og:image\"[^>]+content=\"([^\"]+)\"",
                    RegexOption.IGNORE_CASE,
                ).find(body)
                ogImageMatch?.groupValues?.getOrNull(1)?.takeIf(String::isNotBlank)
            }
        }

    private fun cleanSearchTitle(raw: String): String {
        val stripped =
            raw
                .replace(Regex("^\\s*\\d{1,3}\\s*[.\\-]\\s*"), "")
                .replace(Regex("\\s*\\[[^]]*]"), "")
                .replace(
                    Regex(
                        "\\s*\\((?:feat\\.?|ft\\.?|featuring|with)\\b[^)]*\\)",
                        RegexOption.IGNORE_CASE,
                    ),
                    "",
                ).replace(
                    Regex(
                        "\\s*\\((?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix|full|hd|hq|4k|60fps|30fps)[^)]*\\)",
                        RegexOption.IGNORE_CASE,
                    ),
                    "",
                ).replace(
                    Regex(
                        "\\s*-\\s*(?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix|full|hd|hq|4k|60fps|30fps)\\b.*$",
                        RegexOption.IGNORE_CASE,
                    ),
                    "",
                ).replace(Regex("\\s*\\|\\s*[^|]*$"), "")
                .replace(Regex("\\s+/\\s*.*$"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
                .trim('-')
                .replace(Regex("\\s+"), " ")
                .trim()
        return stripped.ifBlank { raw.trim() }
    }

    private fun cleanSearchArtist(raw: String): String {
        val first =
            raw
                .split(
                    Regex(
                        "(?:\\s*,\\s*|\\s*&\\s*|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)",
                        RegexOption.IGNORE_CASE,
                    ),
                    limit = 2,
                ).firstOrNull()
                .orEmpty()
        return first.replace(Regex("\\s+"), " ").trim()
    }

    private val songwriterCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    private val genreCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    private const val MetadataCacheMaxEntries = 64
    private val mbUserAgent = "ArchiveTune/1.0 (https://github.com/rukamori/ArchiveTune)"

    suspend fun resolveSongwriters(
        title: String,
        artist: String?,
    ): List<String>? {
        if (title.isBlank()) return null
        val cleanedTitle = cleanSearchTitle(title)
        val cleanedArtist = artist?.takeIf(String::isNotBlank)?.let(::cleanSearchArtist)
        val cacheKey = "${cleanedTitle}|${cleanedArtist.orEmpty()}"
        songwriterCache[cacheKey]?.let { return it }
        return withContext(Dispatchers.IO) {
            val mbid = searchMusicBrainzRecordingMbid(cleanedTitle, cleanedArtist) ?: return@withContext null
            val writers = fetchRecordingWriters(mbid)
            if (writers.isEmpty()) {
                null
            } else {
                val distinct = LinkedHashSet<String>().apply { writers.forEach { add(it) } }.toList().take(5)
                songwriterCache[cacheKey] = distinct
                trimMetadataCache(songwriterCache)
                distinct
            }
        }
    }

    suspend fun resolveGenres(
        title: String,
        artist: String?,
    ): List<String>? {
        if (title.isBlank()) return null
        val cleanedTitle = cleanSearchTitle(title)
        val cleanedArtist = artist?.takeIf(String::isNotBlank)?.let(::cleanSearchArtist)
        val cacheKey = "${cleanedTitle}|${cleanedArtist.orEmpty()}"
        genreCache[cacheKey]?.let { return it }
        return withContext(Dispatchers.IO) {
            val fromItunes = iTunesPrimaryGenre(cleanedTitle, cleanedArtist)
            val fromMb = mbRecordingTags(cleanedTitle, cleanedArtist)
            val combined =
                LinkedHashSet<String>()
                    .apply {
                        fromItunes?.let { add(it) }
                        fromMb?.forEach { add(it) }
                    }.toList()
                    .filter { it.isNotBlank() }
                    .take(3)
            if (combined.isEmpty()) {
                null
            } else {
                genreCache[cacheKey] = combined
                trimMetadataCache(genreCache)
                combined
            }
        }
    }

    private fun searchMusicBrainzRecordingMbid(
        title: String,
        artist: String?,
    ): String? {
        if (title.isBlank()) return null
        val query = buildString {
            if (!artist.isNullOrBlank()) {
                append("artist:\"")
                append(artist.replace("\"", ""))
                append("\" AND ")
            }
            append("recording:\"")
            append(title.replace("\"", ""))
            append("\"")
        }
        val searchUrl =
            "https://musicbrainz.org/ws/2/recording/".toHttpUrl()
                .newBuilder()
                .addQueryParameter("query", query)
                .addQueryParameter("limit", "1")
                .addQueryParameter("fmt", "json")
                .build()
        val response =
            runCatching {
                client.newCall(
                    Request.Builder()
                        .url(searchUrl)
                        .header("User-Agent", mbUserAgent)
                        .get()
                        .build(),
                ).execute()
            }.getOrNull() ?: return null
        return response.use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = runCatching { resp.body?.string() }.getOrNull() ?: return@use null
            val parsed = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return@use null
            val recordings = parsed["recordings"]?.jsonArray ?: return@use null
            recordings.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
        }
    }

    private fun fetchRecordingWriters(mbid: String): List<String> {
        val lookupUrl =
            "https://musicbrainz.org/ws/2/recording/$mbid".toHttpUrl()
                .newBuilder()
                .addQueryParameter("inc", "artist-rels+work-rels")
                .addQueryParameter("fmt", "json")
                .build()
        val response =
            runCatching {
                client.newCall(
                    Request.Builder()
                        .url(lookupUrl)
                        .header("User-Agent", mbUserAgent)
                        .get()
                        .build(),
                ).execute()
            }.getOrNull() ?: return emptyList()
        val body = response.use { resp ->
            if (!resp.isSuccessful) return emptyList()
            runCatching { resp.body?.string() }.getOrNull() ?: return emptyList()
        }
        val parsed = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        val relations = parsed["relations"]?.jsonArray ?: return emptyList()
        val writers = mutableListOf<String>()
        for (relationEntry in relations) {
            val relation = relationEntry.jsonObject

            val type = relation["type"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: continue
            if (type != "performance of") continue
            val work = relation["work"]?.jsonObject ?: continue
            val workRelations = work["relations"]?.jsonArray ?: continue
            for (workRelationEntry in workRelations) {
                val workRelation = workRelationEntry.jsonObject
                val workType = workRelation["type"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: continue
                if (workType != "composer" && workType != "lyricist" && workType != "writer") continue
                val writerName = workRelation["artist"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                if (!writerName.isNullOrBlank()) writers.add(writerName)
            }
        }
        return writers
    }

    private suspend fun iTunesPrimaryGenre(
        title: String,
        artist: String?,
    ): String? =
        withContext(Dispatchers.IO) {
            if (title.isBlank()) return@withContext null
            val term = listOfNotNull(artist?.takeIf(String::isNotBlank), title).joinToString(" ")
            val url =
                "https://itunes.apple.com/search".toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("term", term)
                    .addQueryParameter("entity", "song")
                    .addQueryParameter("limit", "3")
                    .build()
            val response =
                runCatching {
                    client.newCall(Request.Builder().url(url).get().build()).execute()
                }.getOrNull() ?: return@withContext null
            response.use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = runCatching { resp.body?.string() }.getOrNull() ?: return@withContext null
                val parsed = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return@withContext null
                val results = parsed["results"]?.jsonArray ?: return@withContext null

                val first =
                    results
                        .firstOrNull { entry ->
                            val trackName = entry.jsonObject["trackName"]?.jsonPrimitive?.contentOrNull.orEmpty().lowercase()
                            trackName.contains(title.lowercase()) || title.lowercase().contains(trackName)
                        }?.jsonObject
                        ?: results.firstOrNull()?.jsonObject
                        ?: return@withContext null
                first["primaryGenreName"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            }
        }

    private suspend fun mbRecordingTags(
        title: String,
        artist: String?,
    ): List<String>? =
        withContext(Dispatchers.IO) {
            val mbid = searchMusicBrainzRecordingMbid(title, artist) ?: return@withContext null
            val lookupUrl =
                "https://musicbrainz.org/ws/2/recording/$mbid".toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("inc", "tags+genres")
                    .addQueryParameter("fmt", "json")
                    .build()
            val response =
                runCatching {
                    client.newCall(
                        Request.Builder()
                            .url(lookupUrl)
                            .header("User-Agent", mbUserAgent)
                            .get()
                            .build(),
                    ).execute()
                }.getOrNull() ?: return@withContext null
            response.use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = runCatching { resp.body?.string() }.getOrNull() ?: return@withContext null
                val parsed = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return@withContext null
                val result = mutableListOf<String>()

                parsed["tags"]?.jsonArray?.forEach { tagEntry ->
                    val name = tagEntry.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                    if (!name.isNullOrBlank()) result.add(name)
                }

                parsed["genres"]?.jsonArray?.forEach { genreEntry ->
                    val name = genreEntry.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                    if (!name.isNullOrBlank()) result.add(name)
                }
                result.takeIf { it.isNotEmpty() }
            }
        }

    private fun trimMetadataCache(cache: java.util.concurrent.ConcurrentHashMap<String, List<String>>) {
        if (cache.size <= MetadataCacheMaxEntries) return
        cache.keys.firstOrNull()?.let { cache.remove(it) }
    }
}
