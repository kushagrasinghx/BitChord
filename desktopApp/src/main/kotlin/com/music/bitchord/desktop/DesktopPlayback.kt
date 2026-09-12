package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

data class DesktopPlaybackState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val volume: Float = 1.0f,
    val isLoading: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
    val streamFormat: DesktopStreamFormat? = null,
    /** Which configured source is serving this — null before anything opened. */
    val streamSourceId: String? = null,
    /** Whether a source is still looking for a better copy of this track while it plays. */
    val searchingBetter: Boolean = false,
    /** True while two tracks are actually being mixed, which the scrubber shows. */
    val mixing: Boolean = false,
    /** Where the next transition will sit, as fractions of this track — drawn on the scrubber. */
    val transitionWindow: com.music.bitchord.data.settings.TransitionWindow? = null,
    /** How far Automix has got on this track and the next — see the player's line. */
    val smartAnalysis: com.music.bitchord.data.settings.SmartAnalysis =
        com.music.bitchord.data.settings.SmartAnalysis(),
)

internal data class DesktopStream(
    val url: String,
    val format: DesktopStreamFormat = DesktopStreamFormat(),
    val headers: Map<String, String> = emptyMap(),
    /** The configured source that produced this stream, for media-failure fallback. */
    val sourceId: String? = null,
    /** Whether the source says this is the immersive mix rather than a stereo one. */
    val isDolbyAtmos: Boolean = false,
    /**
     * Whether this server will only hand the file over a window at a time — see
     * [DesktopRangeStream].
     */
    val windowedReads: Boolean = false,
)

data class DesktopStreamFormat(
    val codec: String? = null,
    val kbps: Int? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    /** How many channels the decoder is being fed; null before anything measured one. */
    val channels: Int? = null,
) {
    /** The codec's short name, whether it arrived bare or inside a MIME type. */
    private val codecName: String?
        get() = codec?.substringAfterLast('/')?.substringBefore(';')?.trim()?.lowercase()

    val isLossless: Boolean
        get() = codecName != null && (
            codecName in LOSSLESS_CODECS ||
                // FFmpeg names every uncompressed flavour `pcm_<layout>`, and Media3 names the same
                // bytes `audio/raw`.
                codecName.orEmpty().startsWith("pcm_") ||
                codecName == "raw"
            )

    /** Dolby Atmos decoded as E-AC-3 JOC; deliberately distinct from lossless. */
    val isDolbyAtmos: Boolean
        get() = codecName == "eac3" || codecName == "eac3-joc" || codecName == "ec-3"

    /**
     * Better than CD — the line Tidal, Qobuz and Apple Music all draw it at: past 16-bit or past
     * 48kHz, not merely lossless.
     */
    val isHiRes: Boolean
        get() = isLossless && ((bitDepth ?: 0) > 16 || (sampleRateHz ?: 0) > 48_000)

    /** Lossy, but the top of what lossy gets — a 320kbps AAC rather than YouTube's 160kbps Opus. */
    val isHiQuality: Boolean
        get() = !isLossless && (kbps ?: 0) >= HI_QUALITY_KBPS

    /** The rate to state on screen. */
    private val statedKbps: Int?
        get() = when {
            !isLossless -> kbps
            bitDepth != null && sampleRateHz != null && channels != null ->
                bitDepth * sampleRateHz * channels / 1_000
            else -> null
        }

    /** The codec under its usual name rather than its container's or FFmpeg's. */
    private val codecLabel: String?
        get() = when (val name = codecName) {
            null -> null
            "opus" -> "Opus"
            "aac", "mp4a-latm", "mp4a", "mp4", "m4a" -> "AAC"
            "vorbis" -> "Vorbis"
            "mp3", "mpeg", "mpeg-4" -> "MP3"
            "flac" -> "FLAC"
            "alac" -> "ALAC"
            "eac3", "ec-3", "eac3-joc" -> "E-AC-3"
            "ac3" -> "AC-3"
            else -> if (name.startsWith("pcm_")) "PCM" else name.uppercase()
        }

    /** "FLAC · 24-bit · 96.0 kHz · 4608 kbps · Stereo" — whichever of those is actually known. */
    val summary: String
        get() = listOfNotNull(
            codecLabel,
            bitDepth?.let { "$it-bit" },
            sampleRateHz?.let { "${it / 1000f} kHz".replace(".0 ", " ") },
            statedKbps?.let { "$it kbps" },
            when (channels) {
                null -> null
                1 -> "Mono"
                2 -> "Stereo"
                else -> "$channels channels"
            },
        ).joinToString(" · ").ifBlank { "Unknown format" }

    private companion object {
        val LOSSLESS_CODECS = setOf("flac", "alac", "wav", "aiff", "ape", "wv", "wavpack", "dsf", "dff", "tta")

        /** The bitrate a lossy stream has to reach to be worth calling out. */
        const val HI_QUALITY_KBPS = 256
    }
}

/** Resolves a playable AAC stream without bringing Android's Media3 into JVM. */
internal object DesktopStreamClient {
    private data class Client(
        val name: String,
        val version: String,
        val id: String,
        val userAgent: String,
        val osName: String? = null,
        val osVersion: String? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val androidSdkVersion: Int? = null,
        val origin: String? = null,
        val needsSignatureTimestamp: Boolean = false,
        /**
         * Whether this identity is only worth asking with a signed-in session behind it, and should
         * carry one.
         */
        val authenticated: Boolean = false,
    ) {
        /**
         * Browser-shaped clients are served from music.youtube.com; app clients from YouTube
         * proper.
         */
        val apiBase: String
            get() = (origin ?: "https://www.youtube.com") + "/youtubei/v1/player"
    }

    private fun Client.mediaHeaders(): Map<String, String> = buildMap {
        put("User-Agent", userAgent)
        origin?.let {
            put("Origin", it)
            put("Referer", "$it/")
        }
    }

    private val clients = listOf(
        // Keep the same starting order as Android's StreamResolver.
        Client(
            name = "ANDROID_MUSIC",
            version = "8.39.42",
            id = "21",
            userAgent = "com.google.android.apps.youtube.music/8.39.42 " +
                "(Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002) gzip",
            osName = "Android",
            osVersion = "15",
            deviceMake = "Google",
            deviceModel = "Pixel 9 Pro",
            androidSdkVersion = 35,
        ),
        // Cobalt's TV identity is the next fallback on IPs that YouTube challenges as a bot.
        Client(
            name = "TVHTML5",
            version = "7.20260707.07.00",
            id = "7",
            userAgent = "Mozilla/5.0(SMART-TV; Linux; Tizen 4.0.0.2) " +
                "AppleWebkit/605.1.15 (KHTML, like Gecko) SamsungBrowser/9.2 " +
                "TV Safari/605.1.15",
            origin = "https://www.youtube.com",
        ),
        // This older VR identity currently returns ordinary HTTPS audio URLs without a PO token or
        // signature-cipher step.
        Client(
            name = "ANDROID_VR",
            version = "1.65.10",
            id = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 " +
                "(Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            osName = "Android",
            osVersion = "12L",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = 32,
        ),
        Client(
            name = "ANDROID_VR_LEGACY",
            version = "1.43.32",
            id = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.43.32 " +
                "(Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; " +
                "Cronet/107.0.5284.2)",
            osName = "Android",
            osVersion = "12",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = 32,
        ),
        Client(
            name = "IOS",
            version = "21.26.4",
            id = "5",
            userAgent = "com.google.ios.youtube/21.26.4 " +
                "(iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            osName = "iPhone",
            osVersion = "18.3.2.22D82",
            deviceMake = "Apple",
            deviceModel = "iPhone16,2",
        ),
        Client(
            name = "IOS_RECENT",
            version = "21.29.1",
            id = "5",
            userAgent = "com.google.ios.youtube/21.29.1 " +
                "(iPhone16,2; U; CPU iOS 18_5 like Mac OS X;)",
            osName = "iPhone",
            osVersion = "18.5.22F70",
            deviceMake = "Apple",
            deviceModel = "iPhone16,2",
        ),
        Client(
            name = "ANDROID",
            version = "21.26.364",
            id = "3",
            userAgent = "com.google.android.youtube/21.26.364 " +
                "(Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002; " +
                "Cronet/132.0.6834.79) gzip",
            osName = "Android",
            osVersion = "15",
            deviceMake = "Google",
            deviceModel = "Pixel 9 Pro",
            androidSdkVersion = 35,
            needsSignatureTimestamp = true,
        ),
        // Last, and only with a session to send.
        Client(
            name = "WEB_REMIX",
            version = "1.20260707.12.00",
            id = "67",
            userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/132.0.0.0 Safari/537.36",
            origin = "https://music.youtube.com",
            needsSignatureTimestamp = true,
            authenticated = true,
        ),
    )

    private val client = DesktopYouTubeHttpClient.client

    suspend fun resolve(song: Song, maxKbps: Int = Int.MAX_VALUE): Result<DesktopStream> = runCatching {
        var lastFailure: Throwable? = null
        var refreshedVisitor = false
        var signatureTimestampLoaded = false
        var signatureTimestamp: Int? = null
        for (identity in clients) {
            if (identity.needsSignatureTimestamp && !signatureTimestampLoaded) {
                signatureTimestampLoaded = true
                signatureTimestamp = DesktopYouTubeUnlocker.signatureTimestamp(song.videoId)
            }
            if (identity.authenticated && !DesktopYouTubeAuth.isSignedIn) continue
            if (identity.needsSignatureTimestamp && signatureTimestamp == null) continue
            for (attempt in 0..1) {
                if (attempt == 1) {
                    if (refreshedVisitor) break
                    DesktopYouTubeSession.ensureVisitorData(refresh = true)
                }
                try {
                    val visitorData = DesktopYouTubeSession.ensureVisitorData()
                    val requestBody = buildJsonObject {
                        putJsonObject("context") {
                            putJsonObject("client") {
                                put("clientName", identity.name)
                                put("clientVersion", identity.version)
                                identity.osName?.let { put("osName", it) }
                                identity.osVersion?.let { put("osVersion", it) }
                                identity.deviceMake?.let { put("deviceMake", it) }
                                identity.deviceModel?.let { put("deviceModel", it) }
                                identity.androidSdkVersion?.let { put("androidSdkVersion", it) }
                                put("hl", "en")
                                put("gl", "US")
                                visitorData?.let { put("visitorData", it) }
                            }
                        }
                        put("videoId", song.videoId)
                        put("contentCheckOk", true)
                        put("racyCheckOk", true)
                        if (identity.needsSignatureTimestamp) {
                            putJsonObject("playbackContext") {
                                putJsonObject("contentPlaybackContext") {
                                    put("signatureTimestamp", signatureTimestamp!!)
                                }
                            }
                        }
                    }.toString()
                    val response = client.send(
                        HttpRequest.newBuilder(URI.create("${identity.apiBase}?prettyPrint=false"))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .header("User-Agent", identity.userAgent)
                            .header("X-YouTube-Client-Name", identity.id)
                            .header("X-YouTube-Client-Version", identity.version)
                            .apply {
                                visitorData?.let { header("X-Goog-Visitor-Id", it) }
                                identity.origin?.let { header("Origin", it) }
                                identity.origin?.let { header("Referer", "$it/") }
                                // Signed and addressed to one account, and only on the identity
                                // that is meant to carry a session — see [Client.authenticated].
                                if (identity.authenticated) {
                                    DesktopYouTubeAuth
                                        .headers(identity.origin ?: DesktopYouTubeAuth.YOUTUBE_ORIGIN)
                                        .forEach { (name, value) -> header(name, value) }
                                }
                            }
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                            .build(),
                        HttpResponse.BodyHandlers.ofString(),
                    )
                    check(response.statusCode() in 200..299) {
                        "YouTube player returned HTTP ${response.statusCode()}"
                    }
                    val responseRoot = Json.parseToJsonElement(response.body()).jsonObject
                    DesktopYouTubeSession.capture(responseRoot)
                    val audioFormats = collectAudioFormats(responseRoot)
                    val eligible = rankByQuality(audioFormats, maxKbps)
                    for (format in eligible) {
                        val url = DesktopYouTubeUnlocker.urlFor(
                            videoId = song.videoId,
                            directUrl = format["url"]?.jsonPrimitive?.contentOrNull,
                            cipher = format["signatureCipher"]?.jsonPrimitive?.contentOrNull
                                ?: format["cipher"]?.jsonPrimitive?.contentOrNull,
                        ) ?: continue
                        val patchedUrl = patchClientVersion(url, identity.version)
                        val headers = identity.mediaHeaders()
                        val probeFailure = probe(patchedUrl, headers)
                        if (probeFailure == null) {
                            DesktopTrackLog.log("  youtube: ${identity.name} answered for '${song.title}'")
                            return Result.success(
                                DesktopStream(
                                    url = patchedUrl,
                                    format = DesktopStreamFormat(
                                        codec = format["mimeType"]?.jsonPrimitive?.contentOrNull
                                            ?.substringAfterLast('/')
                                            ?.substringBefore(';')
                                            ?.lowercase(),
                                        kbps = format["bitrate"]?.jsonPrimitive?.intOrNull
                                            ?.let { if (it > 2_000) it / 1_000 else it },
                                    ),
                                    headers = headers,
                                    sourceId = "youtube",
                                    // Google's media servers answer 403 to a request for a whole
                                    // file and 206 to one for a megabyte of it, and FFmpeg's own
                                    // HTTP client asks for the whole thing.
                                    windowedReads = true,
                                ),
                            )
                        }
                        DesktopTrackLog.log("  youtube: ${identity.name} minted a URL that would not serve — $probeFailure")
                        lastFailure = IllegalStateException("${identity.name} media probe failed: $probeFailure")
                    }
                    val responseFailure = responseRoot.failure(identity.name)
                    DesktopTrackLog.log("  youtube: ${identity.name} declined — ${responseFailure.message}")
                    if (lastFailure == null) lastFailure = responseFailure
                    if (!responseFailure.message.orEmpty().looksLikeBotCheck()) break
                    if (attempt == 0 && !refreshedVisitor) refreshedVisitor = true
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Throwable) {
                    DesktopTrackLog.log("  youtube: ${identity.name} failed — ${failure.message ?: failure::class.simpleName}")
                    lastFailure = failure
                    if (attempt == 0 && failure.message.orEmpty().looksLikeBotCheck()) {
                        refreshedVisitor = true
                    } else {
                        break
                    }
                }
            }
        }
        // Every identity refused.
        extracted(song, maxKbps)?.let { return@runCatching it }

        // Every identity refused and the watch page had nothing either, which is as far as this
        // goes.
        error("YouTube has no playable stream for this track")
    }

    /** Probes a bounded request before handing the stream to JavaFX. */
    /** Whether this URL will actually serve the track, or null when it will. */
    /** The best rendition the watch page will give up, probed like any other. */
    private suspend fun extracted(song: Song, maxKbps: Int): DesktopStream? {
        val found = withContext(Dispatchers.IO) { DesktopYouTubeUnlocker.extractAudio(song.videoId) }
        if (found.isEmpty()) return null
        DesktopTrackLog.log("  youtube: the watch page offered ${found.size} rendition(s)")
        val ordered: List<DesktopYouTubeUnlocker.ExtractedAudio> = found.sortedWith(
            compareByDescending<DesktopYouTubeUnlocker.ExtractedAudio> { (it.kbps ?: 0) <= maxKbps }
                .thenByDescending { it.kbps ?: 0 },
        )
        for (candidate in ordered) {
            val headers = mapOf("User-Agent" to WATCH_PAGE_USER_AGENT)
            val failure = probe(candidate.url, headers)
            if (failure != null) {
                DesktopTrackLog.log("  youtube: a scraped rendition would not serve — $failure")
                continue
            }
            DesktopTrackLog.log(
                "  youtube: playing '${song.title}' from the watch page at " +
                    (candidate.kbps?.let { "$it kbps" } ?: "an unstated rate"),
            )
            return DesktopStream(
                url = candidate.url,
                format = DesktopStreamFormat(codec = candidate.mimeType, kbps = candidate.kbps),
                headers = headers,
                sourceId = "youtube",
                windowedReads = true,
            )
        }
        return null
    }

    /** What the scrape presented itself as, so the media request agrees with it. */
    private const val WATCH_PAGE_USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"

    private fun probe(url: String, headers: Map<String, String>): String? {
        val head = fetch(url, headers, 0, PROBE_BYTES - 1L)
        val failure = head.failure
        if (failure != null) return failure
        val total = head.total ?: return null
        if (total <= PROBE_BYTES) return null
        val tail = fetch(url, headers, (total - PROBE_TAIL_BYTES).coerceAtLeast(0), total - 1)
        return tail.failure?.let { "the end of this stream is $it" }
    }

    private class Probe(val failure: String?, val total: Long?)

    private fun fetch(url: String, headers: Map<String, String>, from: Long, to: Long): Probe {
        val builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Range", "bytes=$from-$to")
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
        headers.forEach { (name, value) -> runCatching { builder.header(name, value) } }
        return runCatching {
            val response = client.send(
                builder.GET().build(),
                HttpResponse.BodyHandlers.ofInputStream(),
            )
            val input = response.body()
            try {
                if (response.statusCode() !in 200..299) {
                    Probe("upstream HTTP ${response.statusCode()}", null)
                } else {
                    val buffer = ByteArray(PROBE_READ_BYTES)
                    if (input.read(buffer) <= 0) {
                        Probe("upstream returned no media bytes", null)
                    } else {
                        val stated = response.headers().firstValue("content-range").orElse(null)
                        Probe(null, stated?.substringAfterLast('/')?.trim()?.toLongOrNull())
                    }
                }
            } finally {
                input.close()
            }
        }.getOrElse { failure ->
            Probe(failure.message?.takeIf(String::isNotBlank) ?: failure::class.simpleName.orEmpty(), null)
        }
    }

    private const val PROBE_BYTES = 64 * 1024

    /** How much of the far end is asked for; see [probe]. */
    private const val PROBE_TAIL_BYTES = 64 * 1024
    private const val PROBE_READ_BYTES = 16 * 1024

    suspend fun resolveUrl(song: Song): Result<String> = resolve(song).map(DesktopStream::url)

    private fun collectAudioFormats(root: JsonElement): List<JsonObject> = buildList {
        fun walk(node: JsonElement) {
            when (node) {
                is JsonObject -> {
                    val mime = node["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (mime.startsWith("audio/") &&
                        (node["url"] != null || node["signatureCipher"] != null || node["cipher"] != null)
                    ) add(node)
                    node.values.forEach(::walk)
                }
                is JsonArray -> node.forEach(::walk)
                else -> Unit
            }
        }
        walk(root)
    }

    private fun patchClientVersion(url: String, clientVersion: String): String =
        if ("cver=" in url) url.replace(Regex("cver=[^&]+"), "cver=$clientVersion") else url

    /** The formats in the order they are worth attempting, as Android ranks them. */
    internal fun rankByQuality(candidates: List<JsonObject>, maxKbps: Int): List<JsonObject> {
        val unciphered = compareByDescending<JsonObject> { it["url"] != null }
        val (withinBudget, overBudget) = candidates.partition { it.kbps() <= maxKbps }
        return withinBudget.sortedWith(compareByDescending<JsonObject> { it.kbps() }.then(unciphered)) +
            overBudget.sortedWith(compareBy<JsonObject> { it.kbps() }.then(unciphered))
    }

    private fun JsonObject.kbps(): Int = (this["bitrate"]?.jsonPrimitive?.intOrNull ?: 0) / 1000

    private val JsonPrimitive.intOrNull: Int?
        get() = contentOrNull?.toIntOrNull()

    private fun JsonObject.failure(clientName: String): IllegalStateException {
        val playability = this["playabilityStatus"]?.jsonObject
        val status = playability?.get("status")?.jsonPrimitive?.contentOrNull
        val reason = playability?.get("reason")?.jsonPrimitive?.contentOrNull
        val serverAbr = this["streamingData"]?.jsonObject
            ?.get("serverAbrStreamingUrl")?.jsonPrimitive?.contentOrNull
        val detail = when {
            status != null && status != "OK" ->
                "$status${reason?.let { ": $it" }.orEmpty()}"
            serverAbr != null -> "SABR-only streaming (no direct audio URL)"
            else -> "no direct audio URL"
        }
        return IllegalStateException("$clientName returned $detail")
    }

    private fun String.looksLikeBotCheck(): Boolean =
        contains("bot", ignoreCase = true) ||
            contains("unusual traffic", ignoreCase = true) ||
            contains("sign in", ignoreCase = true) ||
            contains("login_required", ignoreCase = true)
}

