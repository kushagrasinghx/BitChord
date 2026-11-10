/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.canvas

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object AppleMusicProvider {

    private const val LOG_TAG = "AppleMusicCanvas"
    private const val LOG_LEVEL_DEBUG = 3
    private const val LOG_LEVEL_WARN = 5
    private const val LOG_LEVEL_ERROR = 6

    var logger: ((level: Int, tag: String, message: String) -> Unit)? = null

    @Volatile
    var devTokenProvider: (() -> String?)? = null

    @Volatile
    var mediaUserTokenProvider: (() -> String?)? = null

    private object Log {
        fun d(msg: String) {
            val logger = AppleMusicProvider.logger
            if (logger != null) {
                logger(AppleMusicProvider.LOG_LEVEL_DEBUG, AppleMusicProvider.LOG_TAG, msg)
            } else {
                println("${AppleMusicProvider.LOG_TAG}: D: $msg")
            }
        }

        fun w(msg: String) {
            val logger = AppleMusicProvider.logger
            if (logger != null) {
                logger(AppleMusicProvider.LOG_LEVEL_WARN, AppleMusicProvider.LOG_TAG, msg)
            } else {
                println("${AppleMusicProvider.LOG_TAG}: W: $msg")
            }
        }

        fun e(
            t: Throwable,
            msg: String,
        ) {
            val logger = AppleMusicProvider.logger
            if (logger != null) {
                logger(AppleMusicProvider.LOG_LEVEL_ERROR, AppleMusicProvider.LOG_TAG, "$msg: ${t.message}")
            } else {
                println("${AppleMusicProvider.LOG_TAG}: E: $msg")
                t.printStackTrace()
            }
        }
    }

    private val fallbackAppleMusicToken: String =
        "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsImtpZCI6IldlYlBsYXlLaWQifQ" +
            ".eyJpc3MiOiJBTVBXZWJQbGF5IiwiaWF0IjoxNzg2NjMyOTI0LCJleHAiOjE3OTI2" +
            "ODA5MjQsInJvb3RfaHR0cHNfb3JpZ2luIjpbImFwcGxlLmNvbSJdfQ" +
            ".hBgj61sZf-y7bmuvT-joXAUAcf7TVJ51732xnH5vFkLHOmsQHxVqGMYUuI4h8c0-RX3fRY3moylhLW8fewFJyw"

    @Volatile
    private var appleMusicToken: String = fallbackAppleMusicToken

    @Volatile
    private var appleMusicTokenExpAtSec: Long = decodeJwtExpSec(fallbackAppleMusicToken)

    @Volatile
    private var appleMusicTokenLastRefreshAtMs: Long = 0L

    private val tokenRefreshMutex = Mutex()

    private const val APPLE_MUSIC_WEB_HOME = "https://music.apple.com/"
    private const val AMP_BASE_URL = "https://amp-api.music.apple.com"
    private const val CACHE_TTL_MS = 1000L * 60 * 60 * 24
    private const val US_STOREFRONT = "us"

    // Misses get a much shorter TTL than hits: a canvas that did not exist is
    // unlikely to appear mid-session, while retrying the same album over the
    // network on every queue prefetch measurably drains battery and churns
    // allocations (the same ids were observed re-fetching 5+ times a minute).
    private const val NEGATIVE_CACHE_TTL_MS = 1000L * 60 * 15
    private const val APPLE_MUSIC_WEB_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36"

    @Volatile private var cachedStorefront: String? = null
    @Volatile private var cachedStorefrontAtMs: Long = 0L
    private val storefrontMutex = Mutex()
    private const val STOREFRONT_TTL_MS = 1000L * 60 * 60 * 24

    private val tokenClient by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 8_000
                socketTimeoutMillis = 10_000
            }
            defaultRequest {
                header("User-Agent", APPLE_MUSIC_WEB_UA)
                header("Accept", "text/html,application/xhtml+xml,application/javascript,*/*;q=0.8")
                header("Accept-Language", "en-US,en;q=0.9")
            }
            expectSuccess = false
        }
    }

    /**
     * The best available developer JWT: the user-pasted token when present,
     * otherwise the auto-scraped web-player token (refreshed when stale).
     * Lets the audio-provider search path work without a manually pasted
     * developer token — the popup search used to see null and silently return
     * no results.
     */
    suspend fun currentDevToken(): String? = ensureTokenFresh()?.trim()?.takeIf { it.isNotBlank() }

    /** Non-suspend access to the last scraped web token, when it is still unexpired. */
    fun cachedScrapedDevToken(): String? {
        val token = appleMusicToken.trim()
        if (token.isBlank()) return null
        val expSec = appleMusicTokenExpAtSec
        if (expSec != 0L && expSec <= System.currentTimeMillis() / 1000L) return null
        return token
    }

    suspend fun refreshToken(): String? =
        tokenRefreshMutex.withLock {
            appleMusicTokenLastRefreshAtMs = System.currentTimeMillis()
            val fresh = scrapeTokenFromWeb()
            if (fresh != null) {
                appleMusicToken = fresh
                appleMusicTokenExpAtSec = decodeJwtExpSec(fresh)
                Log.d("Apple Music token refreshed from web player (exp=${appleMusicTokenExpAtSec}s)")
            } else {
                Log.w("Apple Music token refresh failed — falling back to hardcoded token")
            }
            fresh
        }

    suspend fun ensureTokenFresh(): String {
        devTokenProvider?.invoke()?.trim()?.takeIf { it.isNotBlank() }?.let { userDevToken ->
            val expSec = decodeJwtExpSec(userDevToken)
            val nowSec = System.currentTimeMillis() / 1000L
            if (expSec == 0L || expSec > nowSec) {
                return userDevToken
            }
            Log.w("Apple Music user dev token expired (exp=${expSec}s) — falling back to the refreshed web token")
        }
        val nowSec = System.currentTimeMillis() / 1000L
        // exp == 0 means "unparseable payload", NOT "expired": treat unknown
        // exp as serve-and-back-off (re-scrape at most once per 10 minutes)
        // instead of hammering the web player on every request.
        val expUnknown = appleMusicTokenExpAtSec == 0L
        val isExpired = !expUnknown && appleMusicTokenExpAtSec <= nowSec
        val needsRefresh =
            expUnknown || appleMusicTokenExpAtSec - nowSec < 60L * 60L * 24L
        if (!needsRefresh) return appleMusicToken

        val sinceLast = System.currentTimeMillis() - appleMusicTokenLastRefreshAtMs
        if (expUnknown) {
            if (sinceLast in 1..600_000L) return appleMusicToken
            return refreshToken() ?: appleMusicToken
        }
        if (!isExpired && sinceLast in 1..60_000L) return appleMusicToken

        if (!isExpired) return appleMusicToken

        return refreshToken() ?: appleMusicToken
    }

    private suspend fun resolveStorefront(): String {
        val media = mediaUserTokenProvider?.invoke()?.trim()?.takeIf { it.isNotBlank() } ?: return "us"
        val now = System.currentTimeMillis()
        cachedStorefront?.let { if (now - cachedStorefrontAtMs < STOREFRONT_TTL_MS) return it }
        return storefrontMutex.withLock {
            cachedStorefront?.let { if (System.currentTimeMillis() - cachedStorefrontAtMs < STOREFRONT_TTL_MS) return it }
            val fetched = runCatching { fetchStorefrontFromApi() }.getOrNull()
            if (fetched != null) {
                cachedStorefront = fetched
                cachedStorefrontAtMs = System.currentTimeMillis()
                Log.d("Apple Music storefront resolved to $fetched from Media-User-Token")
                fetched
            } else {
                cachedStorefront ?: "us"
            }
        }
    }

    private suspend fun fetchStorefrontFromApi(): String? {
        val token = ensureTokenFresh()
        val media = mediaUserTokenProvider?.invoke()?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val resp = client.get("$AMP_BASE_URL/v1/me/storefront") {
            header("Authorization", "Bearer $token")
            header("Media-User-Token", media)
            header("Origin", "https://music.apple.com")
            header("Referer", "https://music.apple.com/")
            header("User-Agent", APPLE_MUSIC_WEB_UA)
        }
        if (!resp.status.isSuccess()) {
            Log.w("Apple Music storefront fetch failed: ${resp.status}")
            return null
        }
        val root = resp.body<JsonObject>()
        return root["data"]?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
    }

    fun clearStorefrontCache() {
        cachedStorefront = null
        cachedStorefrontAtMs = 0L
    }

    private suspend fun scrapeTokenFromWeb(): String? =
        try {
            val homeResponse = tokenClient.get(APPLE_MUSIC_WEB_HOME)

            if (!homeResponse.status.isSuccess()) {
                Log.w("Apple Music home fetch failed: ${homeResponse.status}")
                return null
            }
            val html = homeResponse.bodyAsText()

            pickAmpToken(html)?.let { return it }

            val bundleUrls = jsBundleUrls(html)
            if (bundleUrls.isEmpty()) {
                Log.w("Apple Music token: no JS bundle URL found in home HTML")
                return null
            }

            for (jsBundleUrl in bundleUrls) {
                val jsResponse = tokenClient.get(jsBundleUrl)
                if (!jsResponse.status.isSuccess()) {
                    Log.w("Apple Music token: JS bundle fetch failed: ${jsResponse.status} ($jsBundleUrl)")
                    continue
                }
                pickAmpToken(jsResponse.bodyAsText())?.let { return it }
                Log.w("Apple Music token: no usable JWT found in JS bundle $jsBundleUrl")
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(e, "Apple Music token scrape error")
            null
        }

    private fun pickAmpToken(text: String): String? {
        val nowSec = System.currentTimeMillis() / 1000L
        val candidates = directJwtRegex.findAll(text).map { it.value }.distinct().toList()
        if (candidates.isEmpty()) return null

        fun unexpired(jwt: String): Boolean {
            val exp = decodeJwtExpSec(jwt)
            return exp == 0L || exp > nowSec
        }

        return candidates.firstOrNull { jwt ->
            unexpired(jwt) && decodeJwtIssuer(jwt) == AMP_WEB_PLAY_ISSUER
        } ?: candidates.firstOrNull { unexpired(it) }
    }

    private fun jsBundleUrls(html: String): List<String> {
        val indexBundles =
            jsBundleRegex
                .findAll(html)
                .map { it.groupValues[1] }
                .toList()
        val anyBundles =
            anyJsAssetRegex
                .findAll(html)
                .map { it.groupValues[1] }
                .toList()
        return (indexBundles + anyBundles)
            .distinct()
            .map(::absoluteAppleMusicUrl)
            .take(4)
    }

    private fun absoluteAppleMusicUrl(rawUrl: String): String =
        when {
            rawUrl.startsWith("http") -> rawUrl
            rawUrl.startsWith("//") -> "https:$rawUrl"
            rawUrl.startsWith("/") -> "https://music.apple.com$rawUrl"
            else -> "https://music.apple.com/$rawUrl"
        }

    private val directJwtRegex: Regex =
        Regex("""eyJ[A-Za-z0-9_-]{8,}\.eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}""")

    private val jsBundleRegex: Regex =
        Regex("""(?:src|href)=["']([^"']*index[~\-][A-Za-z0-9._~-]+\.js)["']""")

    private val anyJsAssetRegex: Regex =
        Regex("""(?:src|data-src)=["']([^"']*/assets/[^"']+\.js)["']""")

    private const val AMP_WEB_PLAY_ISSUER = "AMPWebPlay"

    private fun decodeJwtExpSec(jwt: String): Long {
        val payload = decodeJwtPayload(jwt) ?: return 0L
        // NOTE: this used to be a 4-quote raw string whose extra trailing
        // quote became part of the PATTERN (demanding a quote AFTER the
        // digits), which a JWT payload never has ("," follows the number) -
        // so every token parsed to exp=0, was treated as permanently
        // expired, and the provider re-scraped music.apple.com (multi-MB
        // HTML + JS bundle) on every single canvas request. Plain escaped
        // string, no raw-string quote ambiguity.
        val expMatch = Regex("\"exp\"\\s*:\\s*(\\d+)").find(payload) ?: return 0L
        return expMatch.groupValues[1].toLongOrNull() ?: 0L
    }

    private fun decodeJwtIssuer(jwt: String): String? {
        val payload = decodeJwtPayload(jwt) ?: return null
        return Regex("\"iss\"\\s*:\\s*\"([^\"]+)\"").find(payload)?.groupValues?.get(1)
    }

    private fun decodeJwtPayload(jwt: String): String? {
        val parts = jwt.split(".")
        if (parts.size != 3) return null
        return runCatching {
            val normalized = parts[1].replace('-', '+').replace('_', '/')
            val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
            String(java.util.Base64.getDecoder().decode(padded))
        }.getOrNull()
    }


    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
                register(ContentType.Text.JavaScript, KotlinxSerializationConverter(json))
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 25_000
                socketTimeoutMillis = 25_000
            }
            install(ContentEncoding) {
                gzip()
                deflate()
            }
            install(HttpCache)
            expectSuccess = false
        }
    }


    private data class CacheEntry(
        val value: CanvasArtwork?,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private fun cacheKey(
        prefix: String,
        vararg parts: String,
    ): String = "$prefix|" + parts.joinToString("|") { it.trim().lowercase(Locale.ROOT) }


    suspend fun getByAlbumArtist(
        album: String,
        artist: String,
        storefront: String = "us",
    ): CanvasArtwork? {
        val key = cacheKey("sa", album, artist, storefront)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.value }
        var result = searchAndFetchMotion(album, artist, album, storefront, "albums")
        if (result == null && !storefront.equals(US_STOREFRONT, ignoreCase = true)) {
            result = searchAndFetchMotion(album, artist, album, US_STOREFRONT, "albums", storefrontLocked = true)
        }
        cache[key] =
            CacheEntry(
                result,
                System.currentTimeMillis() + if (result != null) CACHE_TTL_MS else NEGATIVE_CACHE_TTL_MS,
            )
        return result
    }

    suspend fun getBySongArtist(
        song: String,
        artist: String,
        album: String? = null,
        storefront: String = "us",
        forceRefresh: Boolean = false,
    ): CanvasArtwork? {
        val key = cacheKey("song", song, artist, album ?: "", storefront)
        if (forceRefresh) {
            cache.remove(key)
        } else {
            cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.value }
        }
        var result = searchAndFetchMotion(song, artist, album, storefront, "songs", forceRefresh)
        if (result == null && !storefront.equals(US_STOREFRONT, ignoreCase = true)) {
            result = searchAndFetchMotion(song, artist, album, US_STOREFRONT, "songs", forceRefresh, storefrontLocked = true)
        }
        cache[key] =
            CacheEntry(
                result,
                System.currentTimeMillis() + if (result != null) CACHE_TTL_MS else NEGATIVE_CACHE_TTL_MS,
            )
        return result
    }

    suspend fun getByAlbumId(
        albumId: String,
        storefront: String = "us",
    ): CanvasArtwork? {
        val key = cacheKey("id", albumId, storefront)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.value }
        val result = fetchMotionArtwork(albumId, storefront, null)
        cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        return result
    }

    /**
     * The artist's OWN motion artwork — the looping video that only ever appears on
     * the artist page (Apple Music `editorialVideo.motionArtistFullscreen16x9` /
     * `motionArtistSquare1x1`). Distinct from any album canvas of the same artist.
     */
    suspend fun getByArtistName(
        artist: String,
        storefront: String = "us",
    ): CanvasArtwork? {
        if (artist.isBlank()) return null
        val key = cacheKey("artist", artist, storefront)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.value }
        val result = searchArtistMotion(artist, storefront)
        cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        return result
    }

    private suspend fun searchArtistMotion(
        artist: String,
        storefront: String,
    ): CanvasArtwork? =
        runCatching {
            val effectiveStorefront = if (storefront == "us") resolveStorefront() else storefront
            val searchUrl = "$AMP_BASE_URL/v1/catalog/$effectiveStorefront/search"
            var token = ensureTokenFresh()
            var response =
                client.get(searchUrl) {
                    header("Authorization", "Bearer $token")
                    mediaUserTokenProvider?.invoke()?.trim()?.takeIf { it.isNotBlank() }?.let { mt -> header("Media-User-Token", mt) }
                    header("Origin", "https://music.apple.com")
                    header("Referer", "https://music.apple.com/")
                    header("User-Agent", APPLE_MUSIC_WEB_UA)
                    parameter("term", artist)
                    parameter("types", "artists")
                    parameter("limit", "5")
                    parameter("extend", "editorialVideo")
                }
            if (response.status == HttpStatusCode.Unauthorized) {
                Log.w("artist search returned 401 — force-refreshing token and retrying once")
                token = refreshToken() ?: token
                response =
                    client.get(searchUrl) {
                        header("Authorization", "Bearer $token")
                        mediaUserTokenProvider?.invoke()?.trim()?.takeIf { it.isNotBlank() }?.let { mt -> header("Media-User-Token", mt) }
                        header("Origin", "https://music.apple.com")
                        header("Referer", "https://music.apple.com/")
                        header("User-Agent", APPLE_MUSIC_WEB_UA)
                        parameter("term", artist)
                        parameter("types", "artists")
                        parameter("limit", "5")
                        parameter("extend", "editorialVideo")
                    }
            }
            if (response.status != HttpStatusCode.OK) {
                Log.w("artist search failed with status ${response.status}")
                return@runCatching null
            }

            val data =
                response.body<JsonObject>()["results"]
                    ?.jsonObject
                    ?.get("artists")
                    ?.jsonObject
                    ?.get("data")
                    ?.jsonArray
                    ?: return@runCatching null

            for (item in data) {
                val obj = item.jsonObject
                val attributes = obj["attributes"]?.jsonObject ?: continue
                val name = attributes["name"]?.jsonPrimitive?.contentOrNull ?: continue
                if (!name.equals(artist, ignoreCase = true) && normalized(name) != normalized(artist)) continue

                val ev = attributes["editorialVideo"]?.jsonObject ?: continue
                val urls = extractArtistMotionUrls(ev) ?: continue
                Log.d("found artist motion artwork for $name (id ${obj["id"]})")
                return@runCatching CanvasArtwork(
                    name = name,
                    artist = name,
                    albumId = obj["id"]?.jsonPrimitive?.contentOrNull,
                    albumName = name,
                    animated = urls.fullscreen,
                    animatedVertical = urls.square,
                    videoUrl = urls.square ?: urls.wide,
                    videoUrlVertical = urls.square,
                    provider = CanvasArtwork.PROVIDER_APPLE_MUSIC,
                )
            }
            Log.d("no artist motion artwork for $artist")
            null
        }.onFailure {
            if (it is CancellationException) throw it
            Log.e(it, "error in searchArtistMotion for $artist")
        }.getOrNull()

    private fun extractArtistMotionUrls(ev: JsonObject): ArtistMotionUrls? {
        fun JsonObject.videoUrl(): String? =
            this["video"]?.jsonPrimitive?.contentOrNull
                ?: this["videoUrl"]?.jsonPrimitive?.contentOrNull
                ?: this["hlsUrl"]?.jsonPrimitive?.contentOrNull

        val fullscreen =
            ev["motionArtistFullscreen16x9"]?.jsonObject?.videoUrl()
                ?: ev["motionArtistWide16x9"]?.jsonObject?.videoUrl()
        val square = ev["motionArtistSquare1x1"]?.jsonObject?.videoUrl()
        val wide = ev["motionArtistWide16x9"]?.jsonObject?.videoUrl()
        if (fullscreen.isNullOrBlank() && square.isNullOrBlank() && wide.isNullOrBlank()) {
            Log.d("artist editorialVideo present but no motion video: ${ev.keys}")
            return null
        }
        return ArtistMotionUrls(fullscreen = fullscreen, square = square, wide = wide)
    }

    private fun normalized(value: String): String = value.trim().lowercase(Locale.ROOT)

    suspend fun diagnose(
        song: String,
        artist: String,
    ): CanvasSourceDiagnosis {
        val token =
            try {
                ensureTokenFresh()
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                return CanvasSourceDiagnosis.Skipped("Token error: ${throwable.message}")
            } ?: return CanvasSourceDiagnosis.Skipped("Couldn't obtain an Apple Music API token — music.apple.com may be unreachable or blocked.")
        return try {
            val effectiveStorefront = resolveStorefront()
            val query = if (song.contains(artist, ignoreCase = true)) song else "$artist $song"
            val response =
                client.get("$AMP_BASE_URL/v1/catalog/$effectiveStorefront/search") {
                    header("Authorization", "Bearer $token")
                    mediaUserTokenProvider?.invoke()?.trim()?.takeIf { it.isNotBlank() }?.let { mt -> header("Media-User-Token", mt) }
                    header("Origin", "https://music.apple.com")
                    header("Referer", "https://music.apple.com/")
                    header("User-Agent", APPLE_MUSIC_WEB_UA)
                    parameter("term", query)
                    parameter("types", "songs")
                    parameter("limit", "10")
                    parameter("extend", "editorialVideo")
                    parameter("include", "albums")
                }
            when {
                response.status == HttpStatusCode.OK -> {
                    val root = response.body<JsonObject>()
                    val data =
                        root["results"]
                            ?.jsonObject
                            ?.get("songs")
                            ?.jsonObject
                            ?.get("data")
                            ?.jsonArray
                            .orEmpty()
                    val hasCanvas =
                        data.any { item ->
                            val ev = item.jsonObject["attributes"]?.jsonObject?.get("editorialVideo")?.jsonObject
                            if (ev == null) {
                                false
                            } else {
                                val urls = extractEditorialVideoUrls(ev)
                                !urls.animated.isNullOrBlank() || !urls.animatedVertical.isNullOrBlank()
                            }
                        }
                    if (hasCanvas) {
                        CanvasSourceDiagnosis.Ok(canvasFound = true, "Answered — canvas motion artwork found for the probe track.")
                    } else {
                        CanvasSourceDiagnosis.Ok(canvasFound = false, "Working — API answered, the probe track has no canvas motion artwork.")
                    }
                }
                response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden ->
                    CanvasSourceDiagnosis.Rejected(
                        httpStatus = response.status.value,
                        detail = "API rejected the token (HTTP ${response.status.value}).",
                    )
                else ->
                    CanvasSourceDiagnosis.Rejected(
                        httpStatus = response.status.value,
                        detail = "Answered HTTP ${response.status.value}.",
                    )
            }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            CanvasSourceDiagnosis.Unreachable("Unreachable: ${throwable.message ?: throwable::class.simpleName}")
        }
    }


    private suspend fun searchAndFetchMotion(
        term: String,
        artist: String,
        album: String?,
        storefront: String,
        type: String,
        forceRefresh: Boolean = false,
        storefrontLocked: Boolean = false,
    ): CanvasArtwork? {
        return runCatching {
            val effectiveStorefront =
                if (storefrontLocked) {
                    storefront
                } else if (storefront == "us") {
                    resolveStorefront()
                } else {
                    storefront
                }
            Log.d("searching for $type: $term (album: $album) in $effectiveStorefront (requested $storefront)")
            var query = if (term.contains(artist, ignoreCase = true)) term else "$artist $term"
            if (!album.isNullOrBlank() && !query.contains(album, ignoreCase = true)) query = "$query $album"

            val searchUrl = "$AMP_BASE_URL/v1/catalog/$effectiveStorefront/search"
            var token = ensureTokenFresh()
            var response =
                client.get(searchUrl) {
                    header("Authorization", "Bearer $token")
                    mediaUserTokenProvider?.invoke()?.trim()?.takeIf { it.isNotBlank() }?.let { mt -> header("Media-User-Token", mt) }
                    header("Origin", "https://music.apple.com")
                    header("Referer", "https://music.apple.com/")
                    header("User-Agent", APPLE_MUSIC_WEB_UA)
                    parameter("term", query)
                    parameter("types", type)
                    parameter("limit", "10")
                    parameter("extend", "editorialVideo")
                    parameter("include", "albums")
                    if (forceRefresh) header("Cache-Control", "no-cache")
                }
            if (response.status == HttpStatusCode.Unauthorized) {
                Log.w("AMP search returned 401 — force-refreshing token and retrying once")
                token = refreshToken() ?: token
                response =
                    client.get(searchUrl) {
                        header("Authorization", "Bearer $token")
                        mediaUserTokenProvider?.invoke()?.trim()?.takeIf { it.isNotBlank() }?.let { mt -> header("Media-User-Token", mt) }
                        header("Origin", "https://music.apple.com")
                        header("Referer", "https://music.apple.com/")
                        header("User-Agent", APPLE_MUSIC_WEB_UA)
                        parameter("term", query)
                        parameter("types", type)
                        parameter("limit", "10")
                        parameter("extend", "editorialVideo")
                        parameter("include", "albums")
                        if (forceRefresh) header("Cache-Control", "no-cache")
                    }
            }
            if (response.status != HttpStatusCode.OK) {
                Log.w("search failed with status ${response.status}")
                return@runCatching null
            }

            val root = response.body<JsonObject>()
            val results =
                root["results"]
                    ?.jsonObject
                    ?.get(type)
                    ?.jsonObject
                    ?.get("data")
                    ?.jsonArray
                    ?: return@runCatching null

            val scoredResults =
                results
                    .mapNotNull { scoreAndFilterItem(it.jsonObject, term, artist, album) }
                    .sortedByDescending { it.first }

            Log.d("Found ${scoredResults.size} scored results for term '$term'")

            for ((score, obj) in scoredResults) {
                if (score < 12) {
                    Log.d("skipping result with low score: $score")
                    continue
                }

                val attributes = obj["attributes"]?.jsonObject ?: continue
                val resultName = attributes["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val resultArtistName = attributes["artistName"]?.jsonPrimitive?.contentOrNull ?: ""
                val itemType = obj["type"]?.jsonPrimitive?.contentOrNull

                val targetAlbumId = resolveAlbumId(obj, attributes, itemType, resultName)
                if (targetAlbumId == null || targetAlbumId.startsWith("pl.")) {
                    Log.d("skipping null or playlist albumId ($targetAlbumId) for $resultName ($resultArtistName)")
                    continue
                }

                Log.d("trying resolve for $targetAlbumId (from $itemType)")

                val ev = attributes["editorialVideo"]?.jsonObject
                if (ev != null) {
                    val videoUrls = extractEditorialVideoUrls(ev)
                    if (!videoUrls.animated.isNullOrBlank() || !videoUrls.animatedVertical.isNullOrBlank()) {
                        val name = attributes["name"]?.jsonPrimitive?.contentOrNull
                        val collName = attributes["collectionName"]?.jsonPrimitive?.contentOrNull
                        val resolvedAlbumName = if (itemType == "songs") collName else name
                        Log.d("Found direct editorialVideo for $name (ID: $targetAlbumId)")
                        return@runCatching CanvasArtwork(
                            name = name,
                            artist = resultArtistName,
                            albumId = targetAlbumId,
                            albumName = resolvedAlbumName,
                            animated = videoUrls.animated,
                            animatedVertical = videoUrls.animatedVertical,
                            provider = CanvasArtwork.PROVIDER_APPLE_MUSIC,
                        )
                    }
                }

                val fetched =
                    fetchMotionArtwork(
                        albumId = targetAlbumId,
                        storefront = storefront,
                        fallbackArtist = resultArtistName,
                        titleOverride = if (itemType == "songs") attributes["name"]?.jsonPrimitive?.contentOrNull else null,
                        artistOverride = if (itemType == "songs") resultArtistName else null,
                    )
                if (fetched != null) return@runCatching fetched
            }
            Log.d("no canvas found in resolution/lookup for $term after ${scoredResults.size} results")
            null
        }.onFailure {
            if (it is CancellationException) throw it
            Log.e(it, "error in searchAndFetchMotion for $term")
        }.getOrNull()
    }

    private suspend fun fetchMotionArtwork(
        albumId: String,
        storefront: String,
        fallbackArtist: String?,
        titleOverride: String? = null,
        artistOverride: String? = null,
    ): CanvasArtwork? {
        if (albumId.startsWith("pl.")) {
            Log.d("fetchMotionArtwork: ignoring playlist id $albumId")
            return null
        }
        return runCatching {
            val effectiveStorefront = if (storefront == "us") resolveStorefront() else storefront
            Log.d("fetching album $albumId in $effectiveStorefront")
            val albumUrl = "$AMP_BASE_URL/v1/catalog/$effectiveStorefront/albums/$albumId"
            var token = ensureTokenFresh()
            var response =
                client.get(albumUrl) {
                    header("Authorization", "Bearer $token")
                    mediaUserTokenProvider?.invoke()?.trim()?.takeIf { it.isNotBlank() }?.let { mt -> header("Media-User-Token", mt) }
                    header("Origin", "https://music.apple.com")
                    header("Referer", "https://music.apple.com/")
                    header("User-Agent", APPLE_MUSIC_WEB_UA)
                    parameter("extend", "editorialVideo")
                    parameter("include", "tracks")
                }
            if (response.status == HttpStatusCode.Unauthorized) {
                Log.w("album fetch returned 401 — force-refreshing token and retrying once")
                token = refreshToken() ?: token
                response =
                    client.get(albumUrl) {
                        header("Authorization", "Bearer $token")
                        mediaUserTokenProvider?.invoke()?.trim()?.takeIf { it.isNotBlank() }?.let { mt -> header("Media-User-Token", mt) }
                        header("Origin", "https://music.apple.com")
                        header("Referer", "https://music.apple.com/")
                        header("User-Agent", APPLE_MUSIC_WEB_UA)
                        parameter("extend", "editorialVideo")
                        parameter("include", "tracks")
                    }
            }
            if (response.status != HttpStatusCode.OK) {
                Log.w("album fetch failed for $albumId: ${response.status}")
                return@runCatching null
            }

            val root = response.body<JsonObject>()
            val data = root["data"]?.jsonArray
            if (data.isNullOrEmpty()) return@runCatching null

            val albumObj = data.firstOrNull()?.jsonObject ?: return@runCatching null
            val attributes = albumObj["attributes"]?.jsonObject
            val albumName = attributes?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
            val artistName = attributes?.get("artistName")?.jsonPrimitive?.contentOrNull ?: fallbackArtist

            val nameLower = albumName.lowercase(Locale.ROOT)
            val isBlacklisted =
                nameLower.contains("playlist") || nameLower.contains("set list") ||
                    nameLower.contains("essentials") || nameLower.contains("dj mix") ||
                    nameLower.contains("mixed") || nameLower.contains("apple music") ||
                    nameLower.contains("today's hits") || nameLower.contains("session")
            if (isBlacklisted) {
                Log.d("fetchMotionArtwork: ignoring blacklisted album '$albumName' ($albumId)")
                return@runCatching null
            }

            val finalTitle = titleOverride ?: albumName
            val finalArtist = artistOverride ?: artistName

            val ev = attributes?.get("editorialVideo")?.jsonObject
            if (ev != null) {
                val videoUrls = extractEditorialVideoUrls(ev)
                if (!videoUrls.animated.isNullOrBlank() || !videoUrls.animatedVertical.isNullOrBlank()) {
                    Log.d("found editorialVideo for $finalTitle (album: $albumName, id: $albumId)")
                    return@runCatching CanvasArtwork(
                        name = finalTitle,
                        artist = finalArtist,
                        albumId = albumId,
                        albumName = albumName,
                        animated = videoUrls.animated,
                        animatedVertical = videoUrls.animatedVertical,
                        provider = CanvasArtwork.PROVIDER_APPLE_MUSIC,
                    )
                }
            }

            Log.d("no editorialVideo for $albumId")
            null
        }.onFailure {
            if (it is CancellationException) throw it
            Log.e(it, "error in fetchMotionArtwork for $albumId")
        }.getOrNull()
    }


    private fun scoreAndFilterItem(
        obj: JsonObject,
        term: String,
        artist: String,
        album: String?,
    ): Pair<Int, JsonObject>? {
        val attributes = obj["attributes"]?.jsonObject ?: return null
        val resultArtistName = attributes["artistName"]?.jsonPrimitive?.contentOrNull ?: ""
        val resultName = attributes["name"]?.jsonPrimitive?.contentOrNull ?: ""
        val resultCollectionName = attributes["collectionName"]?.jsonPrimitive?.contentOrNull ?: ""

        val nameLower = resultName.lowercase(Locale.ROOT)
        val collectionLower = resultCollectionName.lowercase(Locale.ROOT)
        val isBlacklisted =
            nameLower.contains("playlist") || nameLower.contains("set list") ||
                collectionLower.contains("playlist") || collectionLower.contains("set list") ||
                nameLower.contains("essentials") || collectionLower.contains("essentials") ||
                collectionLower.contains("dj mix") || collectionLower.contains("mixed") ||
                collectionLower.contains("apple music") || collectionLower.contains("today's hits") ||
                nameLower.contains("session") || collectionLower.contains("session")
        if (isBlacklisted) {
            Log.d("  - Skipping blacklisted result: '$resultName' (Album: '$resultCollectionName')")
            return null
        }

        val artistMatch = resultArtistName.equals(artist, ignoreCase = true)
        val artistFuzzy =
            resultArtistName.contains(artist, ignoreCase = true) ||
                artist.contains(resultArtistName, ignoreCase = true)
        if (!artistFuzzy) return null

        var score = if (artistMatch) 10 else 5

        val nameMatch = resultName.equals(term, ignoreCase = true)
        val nameFuzzy = resultName.contains(term, ignoreCase = true) || term.contains(resultName, ignoreCase = true)
        score +=
            when {
                nameMatch -> 15
                nameFuzzy -> 7
                else -> -10
            }

        val editionWords = listOf("deluxe", "expanded", "remastered", "remix", "version", "edit", "mix", "bonus")
        for (word in editionWords) {
            val inTerm = term.contains(word, ignoreCase = true)
            val inResult = resultName.contains(word, ignoreCase = true)
            score +=
                when {
                    inTerm && inResult -> 5
                    inTerm != inResult && inResult -> -3
                    else -> 0
                }
        }

        if (!album.isNullOrBlank() && resultCollectionName.isNotBlank()) {
            val albumMatch = resultCollectionName.equals(album, ignoreCase = true)
            val albumFuzzy =
                resultCollectionName.contains(album, ignoreCase = true) ||
                    album.contains(resultCollectionName, ignoreCase = true)
            score +=
                when {
                    albumMatch -> 20
                    albumFuzzy -> 10
                    else -> 0
                }
        }

        Log.d("  - Result: '$resultName' by '$resultArtistName' (Album: '$resultCollectionName', ID: ${obj["id"]}) -> Score: $score")
        return score to obj
    }

    private fun resolveAlbumId(
        obj: JsonObject,
        attributes: JsonObject,
        itemType: String?,
        resultName: String,
    ): String? {
        if (itemType == "albums") return obj["id"]?.jsonPrimitive?.contentOrNull
        if (itemType != "songs") return null

        val relationships = obj["relationships"]?.jsonObject
        var albumId =
            relationships
                ?.get("albums")
                ?.jsonObject
                ?.get("data")
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("id")
                ?.jsonPrimitive
                ?.contentOrNull
                ?: attributes["collectionId"]?.jsonPrimitive?.contentOrNull

        if (albumId == null) {
            val url = attributes["url"]?.jsonPrimitive?.contentOrNull
            if (url != null) {
                val albumPart = url.substringAfter("/album/", "").substringBefore("?")
                val id = albumPart.substringAfterLast("/", "")
                if (id.isNotBlank() && id.all { it.isDigit() }) albumId = id
            }
        }

        if (albumId == null) Log.d("relationships keys for $resultName: ${relationships?.keys}")
        return albumId
    }

    private data class EditorialVideoUrls(
        val animated: String?,
        val animatedVertical: String?,
    )

    private data class ArtistMotionUrls(
        val fullscreen: String?,
        val square: String?,
        val wide: String?,
    )

    private fun extractEditorialVideoUrls(ev: JsonObject): EditorialVideoUrls {
        fun JsonObject.videoUrl(): String? =
            this["video"]?.jsonPrimitive?.contentOrNull
                ?: this["videoUrl"]?.jsonPrimitive?.contentOrNull
                ?: this["hlsUrl"]?.jsonPrimitive?.contentOrNull
                ?: this["url"]?.jsonPrimitive?.contentOrNull

        val raw = ev["motionDetailRaw"]?.jsonObject?.videoUrl()
        val square = ev["motionDetailSquare"]?.jsonObject?.videoUrl()
        val tall = ev["motionDetailTall"]?.jsonObject?.videoUrl()
        val static = ev["motionDetailStatic"]?.jsonObject?.videoUrl()
        val animated = raw ?: square ?: static ?: tall

        if (animated.isNullOrBlank() && tall.isNullOrBlank()) {
            Log.d("editorialVideo found but no video link in assets: ${ev.keys}")
        }

        return EditorialVideoUrls(
            animated = animated,
            animatedVertical = tall,
        )
    }
}
