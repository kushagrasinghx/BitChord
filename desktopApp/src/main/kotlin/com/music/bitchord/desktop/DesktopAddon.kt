package com.music.bitchord.desktop

import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

// ── Protocol models ────────────────────────────────────────────────────── The addon protocol is
// three GET endpoints returning JSON.

@Serializable
internal data class DesktopAddonManifest(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("version") val version: String = "",
    @SerialName("resources") val resources: List<String> = emptyList(),
    /**
     * The settings schema the addon declares, read for its *defaults* rather than to build a form.
     */
    @SerialName("settings") val settings: List<DesktopAddonSetting> = emptyList(),
) {
    fun declares(resource: String): Boolean = resources.any { it.equals(resource, ignoreCase = true) }

    /** Whether this addon is worth asking anything. */
    val isPlayable: Boolean get() = resources.isEmpty() || declares("search")

    /** What to show when the user did not name the addon themselves. */
    val displayName: String get() = name.ifBlank { id }
}

@Serializable
internal data class DesktopAddonSetting(
    @SerialName("key") val key: String = "",
    @SerialName("default") val default: JsonElement? = null,
    @SerialName("options") val options: List<DesktopAddonSettingOption> = emptyList(),
) {
    val defaultValue: String? get() = default?.asQueryValue()
}

@Serializable
internal data class DesktopAddonSettingOption(
    @SerialName("value") val value: JsonElement? = null,
) {
    val stringValue: String? get() = value?.asQueryValue()
}

@Serializable
internal data class DesktopAddonSearchResponse(
    @SerialName("tracks") val tracks: List<DesktopAddonTrack> = emptyList(),
)

@Serializable
internal data class DesktopAddonTrack(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("artist") val artist: String = "",
    @SerialName("album") val album: String = "",
    /** Seconds, read as a double so an addon sending `240.0` is not a parse failure. */
    @SerialName("duration") val duration: Double? = null,
    @SerialName("artworkURL") val artworkURL: String? = null,
    @SerialName("albumArtworkURL") val albumArtworkURL: String? = null,
    @SerialName("format") val format: String = "",
    /** Not in the spec, but several addons send it and it says the same thing. */
    @SerialName("audioQuality") val audioQuality: String = "",
    /** A stream URL for the row itself; used only as a fallback — see [DesktopAddonSource]. */
    @SerialName("streamURL") val streamURL: String? = null,
) {
    val durationSec: Int? get() = duration?.takeIf { it > 0 }?.toInt()

    val artwork: String? get() = artworkURL?.ifBlank { null } ?: albumArtworkURL?.ifBlank { null }
}

@Serializable
internal data class DesktopAddonStream(
    @SerialName("url") val url: String = "",
    @SerialName("format") val format: String = "",
    @SerialName("quality") val quality: String = "",
    /** Non-standard spellings of [quality] that addons in the wild send instead. */
    @SerialName("streamQuality") val streamQuality: String = "",
    @SerialName("audioQuality") val audioQuality: String = "",
    @SerialName("codec") val codec: String? = null,
    @SerialName("fileCodec") val fileCodec: String? = null,
    @SerialName("container") val container: String? = null,
    @SerialName("containerFormat") val containerFormat: String? = null,
    @SerialName("mimeType") val mimeType: String? = null,
    /** `false`, or the name of a DRM scheme. */
    @SerialName("encrypted") val encrypted: JsonElement? = null,
    @SerialName("sampleRate") val sampleRate: Double? = null,
    @SerialName("bitDepth") val bitDepth: Double? = null,
    @SerialName("bitrate") val bitrate: Double? = null,
    @SerialName("audioMode") val audioMode: String? = null,
    @SerialName("audioModes") val audioModes: List<String> = emptyList(),
    /** Why there is no [url], when the addon bothered to say. */
    @SerialName("error") val error: String? = null,
) {
    val statedCodec: String?
        get() = (codec?.ifBlank { null } ?: fileCodec?.ifBlank { null })?.lowercase()

    val statedContainer: String?
        get() = (container?.ifBlank { null } ?: containerFormat?.ifBlank { null })?.lowercase()

    /** Every free-text quality field at once, for the label readers below. */
    val qualityText: String get() = "$quality $streamQuality $audioQuality $format"

    /** Whether this rendition is behind a DRM scheme. */
    val isEncrypted: Boolean
        get() {
            val element = encrypted as? JsonPrimitive ?: return false
            element.booleanOrNull?.let { return it }
            return element.content.isNotBlank() &&
                !element.content.equals("false", ignoreCase = true) &&
                !element.content.equals("none", ignoreCase = true)
        }

    /**
     * Sample rate from the field when the addon filled it in, and from its own quality label when
     * it did not.
     */
    val sampleRateHz: Int?
        get() = sampleRate?.takeIf { it > 0 }?.let {
            // Some publishers state kHz where the field is specified in Hz.
            if (it < 1000) (it * 1000).toInt() else it.toInt()
        } ?: KHZ_LABEL.find(qualityText)?.groupValues?.get(1)?.toDoubleOrNull()
            ?.takeIf { it > 0 }?.let { (it * 1000).toInt() }

    /** As [sampleRateHz], for bit depth: the number in `24-bit` when there is no field. */
    val bits: Int?
        get() = bitDepth?.takeIf { it > 0 }?.toInt()
            ?: BIT_DEPTH_LABEL.find(qualityText)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 8..32 }

    /** Bitrate in kbps, whichever unit the addon used. */
    val kbps: Int?
        get() = bitrate?.takeIf { it > 0 }?.let { if (it > 3_000) (it / 1000).toInt() else it.toInt() }
            ?: KBPS_LABEL.find(qualityText)?.groupValues?.get(1)?.toIntOrNull()

    /** Whether the addon says this is the immersive mix rather than a stereo one. */
    val isDolbyAtmos: Boolean
        get() = ATMOS.containsMatchIn(
            "$qualityText ${audioMode.orEmpty()} ${audioModes.joinToString(" ")} ${statedCodec.orEmpty()}",
        )

    companion object {
        private val KBPS_LABEL = Regex("""(\d{2,4})\s*kbps""", RegexOption.IGNORE_CASE)
        private val KHZ_LABEL = Regex("""([\d.]+)\s*kHz""", RegexOption.IGNORE_CASE)
        private val BIT_DEPTH_LABEL = Regex("""(\d{1,2})\s*-?\s*bit""", RegexOption.IGNORE_CASE)
        private val ATMOS = Regex("""atmos|joc|eac3|e-ac-3""", RegexOption.IGNORE_CASE)
    }
}

private fun JsonElement.asQueryValue(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.content.takeIf { it.isNotBlank() }
}

// ── Failures ─────────────────────────────────────────────────────────────

internal open class DesktopAddonException(message: String) : Exception(message)

/** The addon does not hold this recording — a miss, not a fault. */
internal class DesktopAddonNotFound : DesktopAddonException("Not found")

/** A transient outage rather than a configuration error; the row says so. */
internal class DesktopAddonUnavailable(message: String) : DesktopAddonException(message)

// ── Client ───────────────────────────────────────────────────────────────

/** One addon server, and every question this app knows how to ask it. */
internal class DesktopAddonClient(rawBaseUrl: String) {

    /** Where this addon lives, with `/manifest.json` and trailing slashes off. */
    val baseUrl: String = normalizeBase(rawBaseUrl)

    private val calls = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val manifests = DesktopSharedCalls<DesktopAddonManifest>(MANIFEST_TTL_MS, calls)
    private val searches = DesktopSharedCalls<DesktopAddonSearchResponse>(SEARCH_TTL_MS, calls)
    private val streams = DesktopSharedCalls<DesktopAddonStream>(STREAM_TTL_MS, calls)

    /** When this addon may be spoken to again after it said it was being asked too often. */
    @Volatile
    private var quietUntilMs = 0L

    /** What the addon says it is, or a failure explaining why it cannot be used. */
    suspend fun manifest(): Result<DesktopAddonManifest> = manifests.get(baseUrl) {
        fetch<DesktopAddonManifest>(manifestUrl(baseUrl)).mapCatching { manifest ->
            if (manifest.id.isBlank()) {
                throw DesktopAddonException("That URL answered, but not with an addon manifest")
            }
            if (!manifest.isPlayable) {
                val declared = manifest.resources.joinToString(", ")
                throw DesktopAddonException("This addon declares $declared — BitChord needs search")
            }
            manifest
        }.recoverCatching { failure ->
            // A manifest 404 is not "no such track", it is "no addon here".
            if (failure is DesktopAddonNotFound) throw DesktopAddonException("No manifest at that URL")
            throw failure
        }
    }

    /** Whether the search endpoint answers, asked without reference to a manifest. */
    suspend fun probeSearch(): Result<Int> =
        fetch<DesktopAddonSearchResponse>(endpoint(listOf("search"), mapOf("q" to PROBE_QUERY)))
            .map { it.tracks.size }

    /** Tracks the addon holds for [query]. */
    suspend fun search(query: String, tier: String): Result<List<DesktopAddonTrack>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())
        val params = settingsFor(tier)
        // The parameters are in the key because they are in the request: the same query at a
        // different quality is a different question.
        return searches.get(keyOf(trimmed, params.toString())) {
            fetch<DesktopAddonSearchResponse>(endpoint(listOf("search"), params + ("q" to trimmed)))
        }.map { it.tracks }
    }

    /** A playable URL for one of this addon's own track ids. */
    suspend fun stream(trackId: String, tier: String): Result<DesktopAddonStream> {
        val params = settingsFor(tier)
        return streams.get(keyOf(trackId, params.toString())) {
            fetch<DesktopAddonStream>(endpoint(listOf("stream", trackId), params))
        }
    }

    /** Everything held about this addon, dropped — reached when it is edited or removed. */
    fun clear() {
        manifests.clear()
        searches.clear()
        streams.clear()
        quietUntilMs = 0L
    }

    /** The query parameters that travel with every request. */
    private suspend fun settingsFor(tier: String): Map<String, String> {
        val declared = manifest().getOrNull()?.settings.orEmpty()
        val params = LinkedHashMap<String, String>()
        declared.forEach { setting ->
            val key = setting.key.takeIf { it.isNotBlank() } ?: return@forEach
            setting.defaultValue?.let { params[key] = it }
        }
        if (tier.isNotBlank()) {
            val options = declared.firstOrNull { it.key == QUALITY_KEY }
                ?.options.orEmpty()
                .mapNotNull { it.stringValue }
            params[QUALITY_KEY] = matchTier(tier, options) ?: tier
        }
        return params
    }

    /** The option that best answers a request for [tier]. */
    private fun matchTier(tier: String, options: List<String>): String? {
        if (options.isEmpty()) return null
        options.firstOrNull { it.equals(tier, ignoreCase = true) }?.let { return it }
        val wanted = when (tier.uppercase()) {
            TIER_LOSSLESS -> LOSSLESS_WORDS
            TIER_LOW -> LOW_WORDS
            else -> HIGH_WORDS
        }
        return options.firstOrNull { option -> wanted.any { it in option.lowercase() } } ?: options.first()
    }

    /** `{base}/{segments…}?{params}`, with every part properly encoded. */
    private fun endpoint(segments: List<String>, params: Map<String, String>): String {
        val parsed = runCatching { Url(baseUrl) }.getOrNull()
            ?: throw DesktopAddonException("That is not a usable address")
        return URLBuilder(parsed).apply {
            appendPathSegments(segments)
            params.forEach { (key, value) -> parameters.append(key, value) }
        }.buildString()
    }

    private suspend inline fun <reified T> fetch(url: String): Result<T> =
        runCatching { json.decodeFromString<T>(body(url)) }

    /** One GET, with the addon's own back-pressure honoured. */
    private suspend fun body(url: String): String = withContext(Dispatchers.IO) {
        var attempt = 0
        while (true) {
            (quietUntilMs - System.currentTimeMillis()).takeIf { it > 0 }?.let { delay(it) }

            val response = http.get(url) {
                header("Accept", "application/json")
                header("User-Agent", USER_AGENT)
            }
            val code = response.status.value
            val wait = when {
                code in 200..299 -> return@withContext response.bodyAsText().takeIf { it.isNotBlank() }
                    ?: throw DesktopAddonException("Empty response")
                code == 404 -> throw DesktopAddonNotFound()
                code >= 500 -> throw DesktopAddonUnavailable("HTTP $code")
                code != 429 -> throw DesktopAddonException("HTTP $code")
                attempt >= MAX_RETRIES -> throw DesktopAddonUnavailable("This addon is rate limiting BitChord")
                else -> retryAfterMs(response.headers["Retry-After"], attempt)
            }
            quietUntilMs = System.currentTimeMillis() + wait
            attempt++
        }
        @Suppress("UNREACHABLE_CODE")
        throw DesktopAddonException("unreachable")
    }

    /** How long to wait after a 429, from the header when it sent one. */
    private fun retryAfterMs(header: String?, attempt: Int): Long {
        val stated = header?.trim()?.toDoubleOrNull()?.times(1000)?.toLong()
        return (stated ?: (BACKOFF_BASE_MS shl attempt)).coerceIn(BACKOFF_BASE_MS, BACKOFF_CAP_MS)
    }

    /** Parts joined length-prefixed, so no byte a part may contain can act as a delimiter. */
    private fun keyOf(vararg parts: String) = parts.joinToString("|") { "${it.length}:$it" }

    companion object {
        /** The tiers [DesktopAddonSource] asks in. */
        const val TIER_LOSSLESS = "LOSSLESS"
        const val TIER_HIGH = "HIGH"
        const val TIER_LOW = "LOW"

        /**
         * A backstop against a server that dribbles, not a budget: how long anyone actually waits
         * is decided by [DesktopSourceRegistry]'s own caps. A cold multi-backend addon needs well
         * over ten seconds to mint a lossless URL, and cutting it off there is what left a Hi-Res
         * copy unfound while a 320 kbps one played.
         */
        const val CALL_TIMEOUT_MS = 45_000L
        const val MANIFEST_TTL_MS = 10 * 60 * 1000L
        const val SEARCH_TTL_MS = 10 * 60 * 1000L
        const val STREAM_TTL_MS = 5 * 60 * 1000L

        private const val QUALITY_KEY = "quality"
        private const val MANIFEST_SUFFIX = "/manifest.json"
        private const val MAX_RETRIES = 2
        private const val BACKOFF_BASE_MS = 500L
        private const val BACKOFF_CAP_MS = 8_000L
        private const val USER_AGENT = "BitChord"
        private const val PROBE_QUERY = "music"

        private val LOSSLESS_WORDS = listOf("lossless", "flac", "hifi", "hi-res", "hires", "max", "best")
        private val HIGH_WORDS = listOf("high", "320", "normal", "standard")
        private val LOW_WORDS = listOf("low", "96", "128", "min")

        internal val json = Json {
            isLenient = true
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }

        /** A ceiling on the whole call: the spec asks addons to answer in seconds, not minutes. */
        private val http = HttpClient(CIO) {
            install(HttpTimeout) { requestTimeoutMillis = CALL_TIMEOUT_MS }
        }

        /** A base URL from whatever the user pasted. */
        fun normalizeBase(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            val lastSegment = trimmed.substringAfterLast('/', "")
            return if (lastSegment.endsWith(".json", ignoreCase = true) && trimmed.contains("://")) {
                trimmed.dropLast(lastSegment.length).trimEnd('/')
            } else {
                trimmed
            }
        }

        fun manifestUrl(base: String): String = "$base$MANIFEST_SUFFIX"

        /** A URL with its host hidden, for anything user-visible or logged. */
        fun redact(url: String): String = runCatching {
            val parsed = Url(url)
            "${parsed.protocol.name}://***${parsed.encodedPath}"
        }.getOrDefault("***")
    }
}

// ── Source ───────────────────────────────────────────────────────────────

/** One addon's track, addressed the way the rest of the desktop app addresses tracks. */
internal data class DesktopAddonTrackRef(val sourceId: String, val trackId: String)

/**
 * The translation between what an addon says and what the rest of the app understands, and
 * deliberately the only place that knows both vocabularies.
 */
internal object DesktopAddonSource {
    private const val TRACK_PREFIX = "addon:"
    private const val SEPARATOR = ""
    private const val MAX_ROWS = 400

    private val clients = ConcurrentHashMap<String, DesktopAddonClient>()

    /** Search rows kept so a `/stream` miss can fall back to the row's own URL. */
    private val rows = ConcurrentHashMap<String, DesktopAddonTrack>()

    fun trackKey(sourceId: String, trackId: String): String =
        TRACK_PREFIX + sourceId + SEPARATOR + trackId

    fun parseTrack(videoId: String): DesktopAddonTrackRef? {
        if (!videoId.startsWith(TRACK_PREFIX)) return null
        val encoded = videoId.removePrefix(TRACK_PREFIX)
        val at = encoded.indexOf(SEPARATOR)
        if (at < 1) return null
        return DesktopAddonTrackRef(encoded.substring(0, at), encoded.substring(at + SEPARATOR.length))
    }

    private fun client(config: DesktopSourceConfig): DesktopAddonClient {
        val base = DesktopAddonClient.normalizeBase(config.baseUrl)
        val existing = clients[config.id]
        if (existing != null && existing.baseUrl == base) return existing
        existing?.clear()
        return DesktopAddonClient(config.baseUrl).also { clients[config.id] = it }
    }

    /** Drops everything held for a source that was edited or removed. */
    fun forget(sourceId: String) {
        clients.remove(sourceId)?.clear()
    }

    /** Whether this addon can be used, and what to say about it. */
    suspend fun health(config: DesktopSourceConfig): Result<String> {
        if (config.baseUrl.isBlank()) return Result.failure(DesktopAddonException("An addon URL is required"))
        val addon = client(config)
        return addon.manifest().fold(
            onSuccess = { manifest ->
                Result.success(
                    listOfNotNull(
                        manifest.displayName.takeIf { it.isNotBlank() },
                        manifest.version.takeIf { it.isNotBlank() }?.let { "v$it" },
                    ).joinToString(" ").ifBlank { "Addon ready" },
                )
            },
            onFailure = { failure ->
                if (addon.probeSearch().isSuccess) {
                    Result.success("No manifest · search works")
                } else {
                    Result.failure(failure)
                }
            },
        )
    }

    /** The addon's own name, for a source row the user never named themselves. */
    suspend fun manifestName(config: DesktopSourceConfig): String? =
        client(config).manifest().getOrNull()?.displayName?.takeIf { it.isNotBlank() }

    suspend fun search(
        config: DesktopSourceConfig,
        query: String,
        limit: Int,
    ): Result<List<SearchResult>> = runCatching {
        if (query.isBlank()) return@runCatching emptyList()
        val tracks = client(config)
            .search(query, DesktopAddonClient.TIER_LOSSLESS)
            .getOrElse { return@runCatching emptyList() }
        tracks.asSequence()
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .take(limit)
            .map { track ->
                remember(config.id, track)
                SearchResult.Track(
                    Song(
                        videoId = trackKey(config.id, track.id),
                        title = track.title,
                        artist = track.artist,
                        albumName = track.album.ifBlank { null },
                        thumbnailUrl = track.artwork,
                        durationText = track.durationSec?.let {
                            "${it / 60}:${"%02d".format(Locale.ROOT, it % 60)}"
                        },
                        sourceQuality = DesktopModuleSource.qualityTier("${track.audioQuality} ${track.format}"),
                    ),
                )
            }
            .toList()
    }

    /** The addon's own copy of a track that arrived from somewhere else. */
    suspend fun match(config: DesktopSourceConfig, song: Song): Song? =
        matches(config, song).firstOrNull()

    /** Every copy this addon holds of [song], most confident first. */
    suspend fun matches(config: DesktopSourceConfig, song: Song): List<Song> {
        if (song.title.isBlank() || song.isVideo) return emptyList()
        for (query in DesktopTrackMatcher.queries(song)) {
            val candidates = search(config, query, 25).getOrDefault(emptyList())
                .mapNotNull { (it as? SearchResult.Track)?.song }
            DesktopTrackMatcher.ranked(candidates, song).ifEmpty { null }?.let { return it }
        }
        return emptyList()
    }

    suspend fun stream(
        config: DesktopSourceConfig,
        song: Song,
        quality: String?,
    ): Result<DesktopStream?> = runCatching {
        val reference = parseTrack(song.videoId)
            ?: return@runCatching null
        val tier = tierFor(quality)
        val outcome = client(config).stream(reference.trackId, tier)
        val answer = outcome.getOrNull()
        if (answer == null) {
            // A 404 is the addon saying it does not hold this one, which is a miss, not a fault.
            val failure = outcome.exceptionOrNull()
            if (failure !is DesktopAddonNotFound) {
                DesktopTrackLog.log(
                    "${config.displayName}: stream failed for ${reference.trackId} — " +
                        "${failure?.javaClass?.simpleName}: ${redact(failure?.message)}",
                )
            }
            return@runCatching fromRow(config, reference.trackId, tier)
        }
        val url = answer.url.ifBlank { null }
        if (url == null) {
            // Some addons name a credential the listener has to go and set on their own setup page.
            DesktopTrackLog.log(
                "${config.displayName}: no stream for ${reference.trackId}" +
                    (answer.error?.ifBlank { null }?.let { " — $it" } ?: ""),
            )
            return@runCatching fromRow(config, reference.trackId, tier)
        }
        openable(config, url, answer, tier)
    }

    /** The stream the search row carried, when the `/stream` call had nothing. */
    private fun fromRow(config: DesktopSourceConfig, trackId: String, tier: String): DesktopStream? {
        val row = rows[rowKey(config.id, trackId)] ?: return null
        val url = row.streamURL?.ifBlank { null } ?: return null
        return openable(config, url, DesktopAddonStream(url = url, format = row.format), tier)
    }

    private fun openable(
        config: DesktopSourceConfig,
        url: String,
        answer: DesktopAddonStream,
        tier: String,
    ): DesktopStream? {
        // Never sent a `?drm=`, so an encrypted answer is an addon ignoring the protocol.
        if (answer.isEncrypted) {
            DesktopTrackLog.log(
                "${config.displayName}: that rendition came back encrypted, which BitChord never asked for",
            )
            return null
        }
        return DesktopStream(
            url = url,
            format = formatOf(answer, url, tier),
            sourceId = config.id,
            isDolbyAtmos = answer.isDolbyAtmos,
        )
    }

    /**
     * What the player is being handed, from whichever field the addon filled in.
     *
     * The free-text quality label is load-bearing: a Tidal Hi-Res row states neither codec nor
     * container and carries its only claim in words like `Hi-Res FLAC (DASH)`.
     */
    private fun formatOf(answer: DesktopAddonStream, url: String, tier: String): DesktopStreamFormat {
        val codec = answer.statedCodec?.takeIf { it in AUDIO_CODECS }
            ?: answer.statedContainer?.takeIf { it in SELF_DESCRIBING_CONTAINERS }
            ?: answer.mimeType?.substringAfterLast('/')?.substringBefore(';')?.trim()
                ?.lowercase(Locale.ROOT)?.takeIf { it in AUDIO_CODECS }
            ?: if (answer.isDolbyAtmos) {
                "eac3-joc"
            } else if (DesktopModuleSource.qualityTier(answer.qualityText) == DesktopModuleSource.LOSSLESS) {
                "flac"
            } else {
                url.substringBefore('?').substringAfterLast('.')
                    .lowercase(Locale.ROOT).takeIf { it in AUDIO_CODECS }
            }
        return DesktopStreamFormat(
            codec = codec,
            // The tier's published meaning is the last resort, and only on the lossy rungs.
            kbps = answer.kbps ?: when (tier) {
                DesktopAddonClient.TIER_HIGH -> 320
                DesktopAddonClient.TIER_LOW -> 128
                else -> null
            },
            sampleRateHz = answer.sampleRateHz,
            bitDepth = answer.bits,
        )
    }

    /** The tier to ask an addon for, from the app's own quality rung. */
    private fun tierFor(quality: String?): String = when (quality?.uppercase()) {
        "LOW" -> DesktopAddonClient.TIER_LOW
        "MEDIUM", "STANDARD", "HIGH" -> DesktopAddonClient.TIER_HIGH
        else -> DesktopAddonClient.TIER_LOSSLESS
    }

    private fun remember(sourceId: String, track: DesktopAddonTrack) {
        if (rows.size > MAX_ROWS) rows.clear()
        rows[rowKey(sourceId, track.id)] = track
    }

    private fun rowKey(sourceId: String, trackId: String) = "$sourceId$trackId"

    /** An addon's base URL embeds the account's signed token, so no message may carry one. */
    private fun redact(message: String?): String =
        message?.replace(Regex("""https?://\S+"""), "the addon") ?: "no reason given"

    /** Containers that name their own codec. */
    private val SELF_DESCRIBING_CONTAINERS = setOf("flac", "wav", "mp3", "aiff")

    /** What may be believed as a codec, from a field or from a URL's extension. */
    private val AUDIO_CODECS = setOf(
        "flac", "alac", "wav", "aiff", "mp3", "aac", "he-aac", "m4a", "mp4",
        "ogg", "opus", "vorbis", "webm", "eac3-joc", "ec3-joc",
    )
}

// ── URL identification ───────────────────────────────────────────────────

/** What is on the end of a URL somebody pasted into the sources editor. */
internal sealed interface DesktopDetectedFormat {
    /** An addon server. */
    data class Addon(val manifest: DesktopAddonManifest, val baseUrl: String) : DesktopDetectedFormat

    /** A module index: JS plugins listed under `category:*` keys. */
    data class ModuleIndex(val moduleCount: Int, val url: String) : DesktopDetectedFormat

    data class Unsupported(val reason: String) : DesktopDetectedFormat
}

/** Identifies what a pasted URL actually points at. */
internal object DesktopSourceFormats {

    suspend fun identify(rawUrl: String): Result<DesktopDetectedFormat> = withContext(Dispatchers.IO) {
        val url = rawUrl.trim().trimEnd('/')
        if (url.isBlank() || runCatching { Url(url) }.getOrNull()?.host.isNullOrBlank()) {
            return@withContext Result.success(
                DesktopDetectedFormat.Unsupported("That is not a web address BitChord can open"),
            )
        }
        val base = DesktopAddonClient.normalizeBase(url)
        val isDocument = url.substringBefore('?').endsWith(".json", ignoreCase = true)
        val attempts = if (isDocument) {
            listOf(url, DesktopAddonClient.manifestUrl(base))
        } else {
            listOf(DesktopAddonClient.manifestUrl(url), url)
        }

        var lastFailure: Throwable? = null
        var firstUnsupported: DesktopDetectedFormat.Unsupported? = null
        for (attempt in attempts) {
            val body = fetch(attempt).getOrElse { failure ->
                lastFailure = failure
                continue
            }
            when (val found = detect(body, attempt)) {
                is DesktopDetectedFormat.Unsupported -> firstUnsupported = firstUnsupported ?: found
                else -> return@withContext Result.success(found)
            }
        }

        // Nothing readable was published.
        if (DesktopAddonClient(base).probeSearch().isSuccess) {
            return@withContext Result.success(
                DesktopDetectedFormat.Addon(manifest = synthesised(base), baseUrl = base),
            )
        }
        // Report what was actually found over a transport error from a URL the user never typed.
        firstUnsupported?.let { return@withContext Result.success(it) }
        Result.failure(lastFailure ?: DesktopAddonUnavailable("Nothing answered at that address"))
    }

    internal fun detect(body: String, url: String = ""): DesktopDetectedFormat {
        val root = runCatching { DesktopAddonClient.json.parseToJsonElement(body) }.getOrNull()
            ?: return DesktopDetectedFormat.Unsupported("That address did not return JSON")
        val obj = root as? JsonObject
            ?: return DesktopDetectedFormat.Unsupported(
                "That JSON is a list, and every format BitChord reads is an object",
            )

        // A module index first: its marker is unmistakable and belongs to no other format.
        if (obj.keys.any { it.startsWith("category:") }) {
            val modules = runCatching { DesktopModuleSource.parseIndex(body) }.getOrDefault(emptyList())
            return if (modules.isEmpty()) {
                DesktopDetectedFormat.Unsupported("That module index listed no modules")
            } else {
                DesktopDetectedFormat.ModuleIndex(modules.size, url)
            }
        }

        val manifest = runCatching {
            DesktopAddonClient.json.decodeFromString<DesktopAddonManifest>(body)
        }.getOrNull()
        return when {
            manifest == null -> DesktopDetectedFormat.Unsupported("BitChord does not recognise that JSON")
            manifest.id.isBlank() ->
                DesktopDetectedFormat.Unsupported("That JSON has no addon id, so it is not a manifest")
            !manifest.isPlayable -> DesktopDetectedFormat.Unsupported(
                "This addon declares ${manifest.resources.joinToString(", ")} — BitChord needs search",
            )
            else -> DesktopDetectedFormat.Addon(manifest, DesktopAddonClient.normalizeBase(url))
        }
    }

    /** Stand-in for an addon that works but published nothing describing itself. */
    private fun synthesised(base: String) = DesktopAddonManifest(
        id = base,
        name = runCatching { Url(base).host }.getOrNull().orEmpty().ifBlank { "Addon" },
        resources = listOf("search"),
    )

    private suspend fun fetch(url: String): Result<String> = runCatching {
        val response = http.get(url) { header("Accept", "application/json") }
        val code = response.status.value
        when {
            code in 200..299 -> response.bodyAsText()
            code == 404 -> throw DesktopAddonNotFound()
            code >= 500 -> throw DesktopAddonUnavailable("HTTP $code")
            else -> throw DesktopAddonException("HTTP $code")
        }
    }

    private val http = HttpClient(CIO)
}
