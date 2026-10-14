package com.music.bitchord.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

/**
 * High-accuracy artwork discovery for local albums and songs that lack cover art.
 * Employs strict candidate verification (matching artist + title/album like AppleArtistArtRepository),
 * preventing mismatched covers from attaching to songs. Never touches songs with existing or attached art.
 */
object OnlineArtworkRepository {

    private const val DISK_FILE = "online_song_art_cache.json"
    private val memoryCache = LinkedHashMap<String, String>(1000, 0.75f, true)
    private val diskLock = Any()
    private var diskLoaded = false

    private val TRACK_NUMBER_REGEX = Regex("""^\d{1,3}[\s.\-_]+""")
    private val FILE_EXT_REGEX = Regex("""\.(mp3|m4a|flac|wav|ogg|opus|aac)$""", RegexOption.IGNORE_CASE)
    private val FEATURING_REGEX = Regex("""(?i)\s*(?:\(|\[)?\s*(?:feat\.?|ft\.?|featuring|with)\b[^\)\]]*(?:\)|\])?""")
    private val VIDEO_DESCRIPTOR_REGEX = Regex("""(?i)\s*(?:\(|\[)\s*(?:official\s+(?:video|audio|music\s+video|visualizer|lyric\s+video)|lyrics?|audio|remastered(?:\s+\d{4})?|\d+kbps|1080p|720p|hd|hq|4k)\s*(?:\)|\])""")
    private val EDITION_REGEX = Regex("""(?i)\s*(?:\(|\[)\s*(?:deluxe|bonus\s+track|expanded|collector's|anniversary|special|remastered)\s+(?:edition|version)?\s*(?:\)|\])""")
    private val DISC_SUFFIX_REGEX = Regex("""(?i)(?:\s*[-–—:]\s*(?:cd|disc|disk)\s*[0-9a-z]+\b.*$|\s*[\(\[\{]\s*(?:cd|disc|disk)\s*[0-9a-z]+\s*[\)\]\}])""")

    private val GENERIC_ALBUM_NAMES = setOf(
        "singles", "single", "downloads", "download", "music", "audio", "unknown",
        "unknown album", "<unknown>", "album", "tracks", "track", "sounds",
        "sound", "recordings", "bitchord", "youtube", "ytmusic", "sdcard",
    )

    fun sanitizeTitle(raw: String): String {
        return raw
            .replace(FILE_EXT_REGEX, "")
            .replace(TRACK_NUMBER_REGEX, "")
            .replace(FEATURING_REGEX, "")
            .replace(VIDEO_DESCRIPTOR_REGEX, "")
            .trim()
    }

    fun sanitizeArtist(raw: String): String {
        val lower = raw.trim().lowercase(Locale.ROOT)
        if (lower == "unknown artist" || lower == "<unknown>" || lower == "various artists") {
            return ""
        }
        val clean = raw.replace(FEATURING_REGEX, "").trim()
        val parts = clean.split(Regex("""(?i)\s+(?:feat\.?|ft\.?|featuring|&|/)\s+|,"""))
        return parts.firstOrNull()?.trim() ?: clean
    }

    fun sanitizeAlbum(raw: String): String {
        val trimmed = raw.trim()
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower in GENERIC_ALBUM_NAMES) return ""
        val clean = trimmed
            .replace(DISC_SUFFIX_REGEX, "")
            .replace(EDITION_REGEX, "")
            .replace(VIDEO_DESCRIPTOR_REGEX, "")
            .trim()
        val cleanLower = clean.lowercase(Locale.ROOT)
        if (cleanLower in GENERIC_ALBUM_NAMES) return ""
        if (cleanLower.matches(Regex("""^(?:cd|disc|disk)\s*[0-9a-z]*$"""))) return ""
        return clean
    }

    private val embeddedArtCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    @Volatile
    private var isDirty = false
    @Volatile
    private var lastSaveTime = 0L

    fun clearEmbeddedArtCache() {
        embeddedArtCache.clear()
    }

    fun clearCache(context: Context) {
        synchronized(memoryCache) {
            memoryCache.clear()
        }
        embeddedArtCache.clear()
        diskLoaded = false
        val file = File(context.filesDir, DISK_FILE)
        if (file.exists()) {
            runCatching { file.delete() }
        }
        val tmp = File(context.filesDir, "$DISK_FILE.tmp")
        if (tmp.exists()) {
            runCatching { tmp.delete() }
        }
    }

    private fun splitArtists(raw: String): List<String> {
        return raw.replace(FEATURING_REGEX, "")
            .split(Regex("""(?i)\s+(?:feat\.?|ft\.?|featuring|&|/|x)\s+|,"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..s2.length) {
                val temp = dp[j]
                dp[j] = if (s1[i - 1] == s2[j - 1]) prev else minOf(prev, dp[j], dp[j - 1]) + 1
                prev = temp
            }
        }
        return dp[s2.length]
    }

    private fun similarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        val maxLen = maxOf(s1.length, s2.length)
        val dist = levenshteinDistance(s1, s2)
        return 1.0 - (dist.toDouble() / maxLen.toDouble())
    }

    private fun String.normalized(): String =
        Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .filter { it.isLetterOrDigit() }

    private fun matchesArtist(target: String, candidate: String): Boolean {
        val tNorm = target.normalized()
        val cNorm = candidate.normalized()
        if (tNorm.isEmpty() || cNorm.isEmpty()) return false
        if (tNorm == cNorm) return true
        if (similarity(tNorm, cNorm) >= 0.85) return true

        val targetArtists = splitArtists(target).map { it.normalized() }.filter { it.length >= 3 }
        val candidateArtists = splitArtists(candidate).map { it.normalized() }.filter { it.length >= 3 }

        for (tArt in targetArtists) {
            for (cArt in candidateArtists) {
                if (tArt == cArt) return true
                if (tArt.length >= 4 && cArt.length >= 4 && similarity(tArt, cArt) >= 0.85) return true
            }
        }
        return false
    }

    private fun matchesTitle(target: String, candidate: String): Boolean {
        val tClean = sanitizeTitle(target)
        val cClean = sanitizeTitle(candidate)
        val t = tClean.normalized()
        val c = cClean.normalized()
        if (t.isEmpty() || c.isEmpty()) return false
        if (t == c) return true
        if (similarity(t, c) >= 0.85) return true

        val minLen = minOf(t.length, c.length)
        val maxLen = maxOf(t.length, c.length)
        if (minLen >= 6 && (t.startsWith(c) || c.startsWith(t)) && (minLen.toDouble() / maxLen.toDouble() >= 0.8)) {
            return true
        }
        return false
    }

    private fun normalizeKey(artist: String, titleOrAlbum: String): String {
        val a = artist.normalized()
        val t = titleOrAlbum.normalized()
        return "$a:$t"
    }

    /**
     * Checks if a song already has valid embedded art, folder art, or remote thumbnail.
     * True means artwork already exists and MUST NOT be overwritten or touched.
     */
    fun hasAttachedArtwork(context: Context, song: Song): Boolean {
        val thumb = song.thumbnailUrl
        if (!thumb.isNullOrBlank()) {
            if (thumb.startsWith("http://") || thumb.startsWith("https://")) return true
            if (thumb.startsWith("file://")) {
                val path = Uri.parse(thumb).path
                if (path != null && File(path).exists() && File(path).length() > 0) return true
            }
        }
        val path = song.localPath
        if (!path.isNullOrBlank()) {
            val parent = File(path).parentFile
            if (parent != null && parent.exists()) {
                val isDiscDir = parent.name.matches(Regex("""(?i)^(?:cd|disc|disk)\s*[0-9a-z]*$"""))
                val searchDirs = if (isDiscDir && parent.parentFile != null) {
                    listOf(parent, parent.parentFile)
                } else {
                    listOf(parent)
                }
                for (dir in searchDirs) {
                    if (dir.exists()) {
                        val hasCover = File(dir, "cover.jpg").exists() ||
                            File(dir, "folder.jpg").exists() ||
                            File(dir, "Cover.jpg").exists() ||
                            File(dir, "Folder.jpg").exists() ||
                            File(dir, "cover.png").exists() ||
                            File(dir, "folder.png").exists()
                        if (hasCover) return true
                    }
                }
            }
        }
        val rawUri = song.localUri ?: song.localPath ?: return false
        val cacheKey = song.localPath ?: song.localUri ?: song.videoId
        embeddedArtCache[cacheKey]?.let { return it }

        val retriever = MediaMetadataRetriever()
        val hasEmbedded = try {
            if (rawUri.startsWith("/")) {
                retriever.setDataSource(rawUri)
            } else {
                retriever.setDataSource(context, Uri.parse(rawUri))
            }
            retriever.embeddedPicture != null
        } catch (_: Throwable) {
            false
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
        embeddedArtCache[cacheKey] = hasEmbedded
        return hasEmbedded
    }

    private fun loadDiskCache(context: Context) {
        if (diskLoaded) return
        val file = File(context.filesDir, DISK_FILE)
        if (!file.exists()) {
            diskLoaded = true
            return
        }
        runCatching {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            synchronized(memoryCache) {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = json.optString(k)
                    if (v.isNotBlank()) memoryCache[k] = v
                }
            }
            diskLoaded = true
        }
    }

    private fun markCacheUpdated(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastSaveTime > 5000L) {
            lastSaveTime = now
            saveDiskCache(context)
        } else {
            isDirty = true
        }
    }

    fun flushDiskCache(context: Context) {
        if (isDirty) {
            isDirty = false
            lastSaveTime = System.currentTimeMillis()
            saveDiskCache(context)
        }
    }

    private fun saveDiskCache(context: Context) {
        val file = File(context.filesDir, DISK_FILE)
        val tmp = File(context.filesDir, "$DISK_FILE.tmp")
        synchronized(diskLock) {
            runCatching {
                val json = JSONObject()
                synchronized(memoryCache) {
                    for ((k, v) in memoryCache) {
                        json.put(k, v)
                    }
                }
                tmp.writeText(json.toString(), Charsets.UTF_8)
                if (tmp.exists()) {
                    if (file.exists()) file.delete()
                    tmp.renameTo(file)
                }
            }
        }
    }

    suspend fun resolveAlbumArt(
        context: Context,
        artist: String,
        albumName: String,
        forceRefresh: Boolean = false,
    ): String? = withContext(Dispatchers.IO) {
        loadDiskCache(context)
        val cleanArtist = sanitizeArtist(artist)
        val cleanAlbum = sanitizeAlbum(albumName)
        if (cleanAlbum.isBlank()) return@withContext null

        val key = normalizeKey(cleanArtist, cleanAlbum)
        if (!forceRefresh) {
            synchronized(memoryCache) { memoryCache[key] }?.let { return@withContext it }
        }

        // 1. iTunes Album Search with strict candidate validation
        var art = fetchItunesAlbumArt(cleanArtist, cleanAlbum)

        // 2. Deezer Album Search with strict candidate validation
        if (art == null) {
            art = fetchDeezerAlbumArt(cleanArtist, cleanAlbum)
        }

        // 3. YouTube Music Album Search with strict candidate validation
        if (art == null) {
            art = fetchYtMusicAlbumArt(cleanArtist, cleanAlbum)
        }

        if (art != null) {
            synchronized(memoryCache) { memoryCache[key] = art }
            markCacheUpdated(context)
        }
        art
    }

    suspend fun resolveSongArt(
        context: Context,
        artist: String,
        title: String,
        albumName: String? = null,
        forceRefresh: Boolean = false,
    ): String? = withContext(Dispatchers.IO) {
        loadDiskCache(context)
        val cleanArtist = sanitizeArtist(artist)
        val cleanTitle = sanitizeTitle(title)
        val cleanAlbum = albumName?.let { sanitizeAlbum(it) }?.takeIf { it.isNotBlank() }

        // If a verified album name is known, prioritize resolving the full album artwork
        if (cleanAlbum != null) {
            val albumArt = resolveAlbumArt(context, cleanArtist, cleanAlbum, forceRefresh = forceRefresh)
            if (albumArt != null) return@withContext albumArt
        }

        val key = normalizeKey(cleanArtist, cleanTitle)
        if (key.isBlank()) return@withContext null

        if (!forceRefresh) {
            synchronized(memoryCache) { memoryCache[key] }?.let { return@withContext it }
        }

        // 1. iTunes Song Search with strict candidate validation
        var art = fetchItunesSongArt(cleanArtist, cleanTitle)

        // 2. Deezer Track Search with strict candidate validation
        if (art == null) {
            art = fetchDeezerTrackArt(cleanArtist, cleanTitle)
        }

        // 3. YouTube Music Song Search with strict candidate validation
        if (art == null) {
            art = fetchYtMusicSongArt(cleanArtist, cleanTitle)
        }

        if (art != null) {
            synchronized(memoryCache) { memoryCache[key] = art }
            markCacheUpdated(context)
        }
        art
    }

    private fun fetchItunesAlbumArt(artist: String, album: String): String? = runCatching {
        val query = if (artist.isNotBlank()) "$artist $album" else album
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://itunes.apple.com/search?term=$encoded&entity=album&limit=10"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        Http.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching null
            val body = resp.body?.string() ?: return@runCatching null
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return@runCatching null

            for (i in 0 until results.length()) {
                val r = results.getJSONObject(i)
                val candidateArtist = r.optString("artistName")
                val candidateAlbum = r.optString("collectionName")

                val artistMatches = if (artist.isNotBlank()) {
                    matchesArtist(artist, candidateArtist)
                } else {
                    candidateArtist.isNotBlank() && album.length >= 6 && album.normalized() == candidateAlbum.normalized()
                }
                val albumMatches = matchesTitle(album, candidateAlbum)

                if (artistMatches && albumMatches) {
                    val raw = r.optString("artworkUrl100").takeIf { it.isNotBlank() } ?: continue
                    return@runCatching raw
                        .replace("100x100bb.jpg", "1000x1000bb.jpg")
                        .replace("100x100bb.png", "1000x1000bb.png")
                        .replace("100x100", "1000x1000")
                }
            }
            null
        }
    }.getOrNull()

    private fun fetchItunesSongArt(artist: String, title: String): String? = runCatching {
        val query = if (artist.isNotBlank()) "$artist $title" else title
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://itunes.apple.com/search?term=$encoded&entity=song&limit=10"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        Http.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching null
            val body = resp.body?.string() ?: return@runCatching null
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return@runCatching null

            for (i in 0 until results.length()) {
                val r = results.getJSONObject(i)
                val candidateArtist = r.optString("artistName")
                val candidateTitle = r.optString("trackName")

                val artistMatches = if (artist.isNotBlank()) {
                    matchesArtist(artist, candidateArtist)
                } else {
                    candidateArtist.isNotBlank() && title.length >= 6 && title.normalized() == candidateTitle.normalized()
                }
                val titleMatches = matchesTitle(title, candidateTitle)

                if (artistMatches && titleMatches) {
                    val raw = r.optString("artworkUrl100").takeIf { it.isNotBlank() } ?: continue
                    return@runCatching raw
                        .replace("100x100bb.jpg", "1000x1000bb.jpg")
                        .replace("100x100bb.png", "1000x1000bb.png")
                        .replace("100x100", "1000x1000")
                }
            }
            null
        }
    }.getOrNull()

    private fun fetchDeezerAlbumArt(artist: String, album: String): String? = runCatching {
        val q = if (artist.isNotBlank()) "artist:\"$artist\" album:\"$album\"" else album
        val encoded = URLEncoder.encode(q, "UTF-8")
        val url = "https://api.deezer.com/search/album?q=$encoded&limit=5"
        val req = Request.Builder().url(url).build()
        Http.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching null
            val body = resp.body?.string() ?: return@runCatching null
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return@runCatching null

            for (i in 0 until data.length()) {
                val r = data.getJSONObject(i)
                val candidateArtist = r.optJSONObject("artist")?.optString("name").orEmpty()
                val candidateTitle = r.optString("title")

                val artistMatches = if (artist.isNotBlank()) {
                    matchesArtist(artist, candidateArtist)
                } else {
                    candidateArtist.isNotBlank() && album.length >= 6 && album.normalized() == candidateTitle.normalized()
                }
                val albumMatches = matchesTitle(album, candidateTitle)

                if (artistMatches && albumMatches) {
                    val art = r.optString("cover_xl").takeIf { it.isNotBlank() }
                        ?: r.optString("cover_big").takeIf { it.isNotBlank() }
                    if (art != null) return@runCatching art
                }
            }
            null
        }
    }.getOrNull()

    private fun fetchDeezerTrackArt(artist: String, title: String): String? = runCatching {
        val q = if (artist.isNotBlank()) "artist:\"$artist\" track:\"$title\"" else title
        val encoded = URLEncoder.encode(q, "UTF-8")
        val url = "https://api.deezer.com/search/track?q=$encoded&limit=5"
        val req = Request.Builder().url(url).build()
        Http.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching null
            val body = resp.body?.string() ?: return@runCatching null
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return@runCatching null

            for (i in 0 until data.length()) {
                val r = data.getJSONObject(i)
                val candidateArtist = r.optJSONObject("artist")?.optString("name").orEmpty()
                val candidateTitle = r.optString("title")

                val artistMatches = if (artist.isNotBlank()) {
                    matchesArtist(artist, candidateArtist)
                } else {
                    candidateArtist.isNotBlank() && title.length >= 6 && title.normalized() == candidateTitle.normalized()
                }
                val titleMatches = matchesTitle(title, candidateTitle)

                if (artistMatches && titleMatches) {
                    val albumObj = r.optJSONObject("album")
                    val art = albumObj?.optString("cover_xl")?.takeIf { it.isNotBlank() }
                        ?: albumObj?.optString("cover_big")?.takeIf { it.isNotBlank() }
                    if (art != null) return@runCatching art
                }
            }
            null
        }
    }.getOrNull()

    private suspend fun fetchYtMusicAlbumArt(artist: String, album: String): String? = runCatching {
        val query = if (artist.isNotBlank()) "$artist $album" else album
        val results = YtMusicRepository.search(query, SearchFilter.ALBUMS).getOrNull()
            ?.filterIsInstance<SearchResult.Browse>() ?: return@runCatching null

        for (item in results) {
            val candidateTitle = item.item.title
            val candidateArtist = item.item.subtitle
            val titleMatches = matchesTitle(album, candidateTitle)
            val artistMatches = if (artist.isNotBlank()) {
                matchesArtist(artist, candidateArtist)
            } else {
                candidateArtist.isNotBlank() && album.length >= 6 && album.normalized() == candidateTitle.normalized()
            }
            if (titleMatches && artistMatches) {
                return@runCatching item.item.thumbnailUrl
            }
        }
        null
    }.getOrNull()

    private suspend fun fetchYtMusicSongArt(artist: String, title: String): String? = runCatching {
        val query = if (artist.isNotBlank()) "$artist $title" else title
        val results = YtMusicRepository.search(query, SearchFilter.SONGS).getOrNull()
            ?.filterIsInstance<SearchResult.Track>() ?: return@runCatching null

        for (item in results) {
            val candidateTitle = item.song.title
            val candidateArtist = item.song.artist
            val titleMatches = matchesTitle(title, candidateTitle)
            val artistMatches = if (artist.isNotBlank()) {
                matchesArtist(artist, candidateArtist)
            } else {
                candidateArtist.isNotBlank() && title.length >= 6 && title.normalized() == candidateTitle.normalized()
            }
            if (titleMatches && artistMatches) {
                return@runCatching item.song.thumbnailUrl
            }
        }
        null
    }.getOrNull()

    /**
     * Saves discovered artwork as `cover.jpg` directly into the album's physical folder.
     * Guarded by user preference and directory safety checks to prevent gallery pollution.
     * Note: Never writes `.nomedia` to user music folders as that suppresses MediaStore indexing of songs.
     */
    fun saveCoverToAlbumFolder(
        context: Context,
        song: Song,
        artworkUrl: String,
        overwrite: Boolean = false,
    ): Boolean {
        if (!AppSettings.saveAlbumCoverToFolder.value) return false

        val path = song.localPath ?: return false
        val songFile = File(path)
        val rawFolder = songFile.parentFile ?: return false
        if (!rawFolder.exists() || !rawFolder.isDirectory) return false
        val isDiscDir = rawFolder.name.matches(Regex("""(?i)^(?:cd|disc|disk)\s*[0-9a-z]*$"""))
        val folder = if (isDiscDir && rawFolder.parentFile != null && rawFolder.parentFile.exists()) rawFolder.parentFile else rawFolder

        // Critical safety: never pollute top-level user directories with album covers
        val fPath = folder.absolutePath.replace('\\', '/').trimEnd('/').lowercase(Locale.ROOT)
        if (fPath == "/storage/emulated/0" ||
            fPath == "/storage/emulated/0/download" ||
            fPath == "/storage/emulated/0/music" ||
            fPath == "/sdcard" ||
            fPath.endsWith("/dcim") ||
            fPath.endsWith("/pictures") ||
            fPath.endsWith("/movies")
        ) {
            return false
        }

        val coverFile = File(folder, "cover.jpg")
        if (!overwrite && coverFile.exists() && coverFile.length() > 0) return true

        return runCatching {
            val req = Request.Builder().url(artworkUrl).build()
            Http.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching false
                val bytes = resp.body?.bytes() ?: return@runCatching false
                coverFile.writeBytes(bytes)
                true
            }
        }.getOrDefault(false)
    }

    fun saveCoverToAlbumFolder(
        context: Context,
        songs: List<Song>,
        artworkUrl: String,
        overwrite: Boolean = false,
    ): Boolean {
        val firstWithPath = songs.firstOrNull { !it.localPath.isNullOrBlank() } ?: return false
        return saveCoverToAlbumFolder(context, firstWithPath, artworkUrl, overwrite = overwrite)
    }
}
