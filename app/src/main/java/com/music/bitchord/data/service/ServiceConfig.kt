package com.music.bitchord.data.service

import android.content.Context
import android.net.Uri
import com.music.bitchord.data.innertube.PlayerClient
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The service file: every network identity this app needs to talk to the
 * streaming service, supplied by the listener instead of shipped in the repo.
 *
 * The repository contains no service endpoints, client versions, user agents
 * or link hosts — only the placeholders below. A downloaded JSON file fills
 * them in at runtime; every feature works exactly as before once imported,
 * and nothing service-backed works until it is. See README.md ("Service
 * file") for the documented format. No sample values live in this repo, its
 * docs, or its tests outside of non-routable fixtures.
 */
@Serializable
data class ServiceFile(
    val app: String = APP_TAG,
    val configVersion: Int = SCHEMA_VERSION,
    val endpoints: Endpoints,
    val login: Login,
    val webClient: WebClient,
    val playerClients: List<ServiceClient>,
    val clientOrder: List<String>,
    val streamHostSuffix: String,
    val links: Links,
    val swDataUrl: String,
    /** Third-party catalogue source config. Null = that source stays unavailable. */
    val catalogue: CatalogueBlock? = null,
) {
    companion object {
        const val APP_TAG = "bitchord-service"

        /**
         * Bump when the shape below stops being readable by an older build. A
         * file from a *newer* schema is refused rather than partially read;
         * one from an older schema is read as-is, since every field that can
         * gain a default has one.
         */
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class Endpoints(
    /** API base, e.g. the music host's inner-tube root. */
    val musicBase: String,
    /** API base the app-shaped clients are served from. */
    val tubeBase: String,
    /** Origin the browser-shaped client runs on. */
    val musicOrigin: String,
    /** Origin the app-shaped clients are dressed with. */
    val tubeOrigin: String,
)

@Serializable
data class Login(
    /** Origin the in-app browser opens for sign-in and channel switching. */
    val origin: String,
    /** Full URL loaded for a fresh sign-in. */
    val loginUrl: String,
    /** Bare hosts whose cookies belong to the login session. */
    val cookieOrigins: List<String>,
)

@Serializable
data class WebClient(
    val name: String,
    val version: String,
    val id: String,
    val ua: String,
    val stats: StatsP,
)

@Serializable
data class StatsP(
    val cplayer: String,
    val cbr: String,
    val cbrver: String,
    val cos: String,
    val cosver: String,
)

/**
 * One client identity for the player endpoint, as authored in the service
 * file. Converts to [PlayerClient] via [ServiceConfig.orderedClients].
 */
@Serializable
data class ServiceClient(
    val name: String,
    val version: String,
    val id: String,
    val ua: String,
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: String? = null,
    /** The host this client runs on, for browser-shaped clients only. */
    val origin: String? = null,
    /** Which API base serves this client: "music" or "tube". */
    val apiBase: String = "music",
    /** Ciphered formats can't be unlocked without a signature timestamp. */
    val needsSignatureTimestamp: Boolean = false,
)

@Serializable
data class Links(
    /** Hosts that address the catalogue, first one primary. */
    val hosts: List<String>,
    /** Short-link host carrying a bare id in its path. */
    val shortHost: String,
    /** Origin share links and outbound buttons are composed against. */
    val shareOrigin: String,
)

/**
 * The third-party lossy catalogue source: where its API lives, which calls
 * to make, and the stream-key that unlocks its CDN URLs. All user-supplied —
 * none of these values exist anywhere in the repo.
 */
@Serializable
data class CatalogueBlock(
    /** API root the search/details calls are issued against. */
    val apiBase: String,
    /** Search call name, e.g. the catalogue's own search method. */
    val searchCall: String,
    /** Details call name, e.g. the catalogue's own details method. */
    val detailsCall: String,
    /** Stream-key the CDN URLs are deciphered with. */
    val urlKey: String,
    /** User agent the catalogue API is asked with. */
    val userAgent: String,
    /** Optional egress hint some catalogues need to see. */
    val forwardedFor: String? = null,
    /** Optional session cookie the catalogue API expects. */
    val cookie: String? = null,
)

/**
 * Thrown by every service-backed path while no service file is installed.
 * The message is user-facing: existing error surfaces (search errors,
 * library prompts, snackbars) render it as-is.
 */
class ServiceFileRequired :
    IllegalStateException("Service file required — import one in Settings to enable this.")

object ServiceConfig {

    // ---- Anti-phishing guard -------------------------------------------------
    //
    // The one deliberate exception to "no corporation mentions in code". The
    // session cookie and its API signature are sent ONLY to a host at or
    // under one of these suffixes, no matter what an imported file claims.
    // A malicious file can therefore redirect anonymous streams, but never
    // harvest the login session. Documented in NOTICE as a safety valve.

    private val AUTH_GUARD_SUFFIXES = listOf("google.com", "youtube.com", "googlevideo.com")

    /** Whether [host] may receive the session cookie and API signature. */
    fun canSendAuth(host: String): Boolean {
        val h = host.lowercase().trim().removeSuffix(".")
        return AUTH_GUARD_SUFFIXES.any { h == it || h.endsWith(".$it") }
    }

    // ---- Store ---------------------------------------------------------------

    private val json = Json { ignoreUnknownKeys = true }

    private var prefs: android.content.SharedPreferences? = null

    /** The installed file, or null while none is imported. */
    private val _state = MutableStateFlow<Active?>(null)
    val state: StateFlow<Active?> = _state.asStateFlow()

    val isReady: Boolean get() = _state.value != null

    data class Active(
        val file: ServiceFile,
        /** Short SHA-256 of the imported text, shown in Settings. */
        val fingerprint: String,
        val importedAt: Long,
    )

    data class ImportSummary(
        val clients: Int,
        val hosts: Int,
        val fingerprint: String,
    )

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs?.getString(KEY_JSON, null) ?: return
        val at = prefs?.getLong(KEY_IMPORTED_AT, 0L) ?: 0L
        validate(raw).onSuccess { file ->
            _state.value = Active(file, fingerprintOf(raw), at)
        }.onFailure {
            // A file this build can no longer read is dropped rather than
            // half-honoured; the Settings screen reports it as missing.
            prefs?.edit()?.remove(KEY_JSON)?.remove(KEY_IMPORTED_AT)?.apply()
        }
    }

    /** For JVM unit tests only: installs [file] without touching storage. */
    fun installForTests(file: ServiceFile) {
        _state.value = Active(file, "test", 0L)
    }

    /** For JVM unit tests only. */
    fun clearForTests() {
        _state.value = null
    }

    fun importText(text: String): Result<ImportSummary> = runCatching {
        require(text.length <= MAX_FILE_CHARS) { "That file is too large to be a service file" }
        val file = validate(text).getOrThrow()
        val now = System.currentTimeMillis()
        prefs?.edit()
            ?.putString(KEY_JSON, text)
            ?.putLong(KEY_IMPORTED_AT, now)
            ?.apply()
        _state.value = Active(file, fingerprintOf(text), now)
        ImportSummary(
            clients = file.playerClients.size,
            hosts = file.links.hosts.size + 1,
            fingerprint = fingerprintOf(text),
        )
    }

    suspend fun importUri(context: Context, source: Uri): Result<ImportSummary> {
        val text = runCatching {
            context.contentResolver.openInputStream(source)
                ?.use { it.readBytes().decodeToString() }
                ?: error("Couldn't open that file")
        }.getOrElse { return Result.failure(it) }
        return importText(text)
    }

    /**
     * Pre-checks a service file link before anything is fetched. Pure and
     * unit-tested: https only, no credentials, public host, sane length.
     */
    fun checkImportUrl(url: String): Result<Unit> = runCatching {
        val u = url.trim()
        require(u.length in 12..MAX_URL_CHARS) { "That doesn't look like a file link" }
        require(u.startsWith("https://")) { "Service file links must be https" }
        val authority = u.substringAfter("://").substringBefore("/")
        require(authority.isNotEmpty() && "@" !in authority) {
            "Service file links must not carry credentials"
        }
        val host = authority.substringBefore(":")
        require(isBareHost(host)) { "That link doesn't name a valid host" }
        require(!isPrivateHost(host)) { "Service file links must point at a public host" }
    }

    /**
     * Downloads [url] and imports it as a service file.
     *
     * Explicitly user-invoked only: nothing in the app fetches a link on its
     * own, no link is baked in, and an imported file is never re-fetched. The
     * download is size-capped and the text goes through the same validation
     * as a picked file.
     */
    suspend fun importUrl(url: String): Result<ImportSummary> {
        checkImportUrl(url).getOrElse { return Result.failure(it) }
        val text = runCatching {
            kotlinx.coroutines.withTimeout(30_000L) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val request = okhttp3.Request.Builder().url(url.trim()).get().build()
                    com.music.bitchord.data.Http.client.newCall(request).execute().use { response ->
                        require(response.isSuccessful) { "That link answered ${response.code}" }
                        val body = response.body ?: error("That link sent nothing back")
                        val sink = okio.Buffer()
                        var remaining = MAX_FILE_CHARS + 1L
                        val source = body.source()
                        while (!source.exhausted()) {
                            val read = source.read(sink, minOf(8_192L, remaining))
                            if (read == -1L) break
                            remaining -= read
                            require(remaining > 0) { "That file is too large to be a service file" }
                        }
                        sink.readUtf8()
                    }
                }
            }
        }.getOrElse { return Result.failure(it) }
        return importText(text)
    }

    /**
     * One cheap anonymous request against the file's own reachability URL.
     * Reports the HTTP code; anything thrown (DNS, TLS, timeout) reads as
     * unreachable rather than as a verdict on the file's contents.
     */
    suspend fun probe(): Result<Int> = runCatching {
        val active = requireReady()
        kotlinx.coroutines.withTimeout(15_000L) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val request = okhttp3.Request.Builder()
                    .url(active.file.swDataUrl)
                    .header("User-Agent", active.file.webClient.ua)
                    .get()
                    .build()
                com.music.bitchord.data.Http.client.newCall(request).execute().use { it.code }
            }
        }
    }

    fun clear() {
        prefs?.edit()?.remove(KEY_JSON)?.remove(KEY_IMPORTED_AT)?.apply()
        _state.value = null
    }

    fun requireReady(): Active = _state.value ?: throw ServiceFileRequired()

    // ---- Pure validation (unit-tested, no Android past this line's imports) ---

    fun validate(text: String): Result<ServiceFile> = runCatching {
        val file = runCatching { json.decodeFromString(ServiceFile.serializer(), text) }
            .getOrElse { error("That doesn't look like a service file") }
        require(file.app == ServiceFile.APP_TAG) { "That file isn't a BitChord service file" }
        require(file.configVersion <= ServiceFile.SCHEMA_VERSION) {
            "That service file was written for a newer version of BitChord"
        }
        val problems = collectProblems(file)
        require(problems.isEmpty()) { problems.first() }
        file
    }

    internal fun collectProblems(file: ServiceFile): List<String> = buildList {
        urlProblem("endpoints.musicBase", file.endpoints.musicBase)?.let(::add)
        urlProblem("endpoints.tubeBase", file.endpoints.tubeBase)?.let(::add)
        urlProblem("endpoints.musicOrigin", file.endpoints.musicOrigin)?.let(::add)
        urlProblem("endpoints.tubeOrigin", file.endpoints.tubeOrigin)?.let(::add)
        urlProblem("login.origin", file.login.origin)?.let(::add)
        urlProblem("login.loginUrl", file.login.loginUrl)?.let(::add)
        urlProblem("swDataUrl", file.swDataUrl)?.let(::add)
        urlProblem("links.shareOrigin", file.links.shareOrigin)?.let(::add)
        if (file.login.cookieOrigins.isEmpty() || file.login.cookieOrigins.size > MAX_HOSTS) {
            add("login.cookieOrigins must hold 1–$MAX_HOSTS hosts")
        } else file.login.cookieOrigins.forEach { h ->
            if (!isBareHost(h)) add("login.cookieOrigins holds hosts, not URLs ($h)")
            else if (isPrivateHost(h)) add("login.cookieOrigins must not name a private host ($h)")
        }
        if (file.webClient.name.isBlank() || file.webClient.version.isBlank() ||
            file.webClient.id.isBlank() || file.webClient.ua.isBlank()
        ) {
            add("webClient needs a name, version, id and user agent")
        }
        if (file.webClient.ua.length > MAX_URL_CHARS) add("webClient.ua is too long")
        listOf(
            "stats.cplayer" to file.webClient.stats.cplayer,
            "stats.cbr" to file.webClient.stats.cbr,
            "stats.cbrver" to file.webClient.stats.cbrver,
            "stats.cos" to file.webClient.stats.cos,
            "stats.cosver" to file.webClient.stats.cosver,
        ).forEach { (label, value) ->
            if (value.isBlank() || value.length > MAX_SHORT) add("$label must be a short non-blank token")
        }
        if (file.playerClients.isEmpty() || file.playerClients.size > MAX_CLIENTS) {
            add("playerClients must hold 1–$MAX_CLIENTS entries")
        } else file.playerClients.forEach { c ->
            if (c.name.isBlank() || c.version.isBlank() || c.id.isBlank() || c.ua.isBlank()) {
                add("every player client needs a name, version, id and user agent")
            } else {
                if (c.ua.length > MAX_URL_CHARS) add("player client ${c.name} has too long a user agent")
                if (c.apiBase != "music" && c.apiBase != "tube") {
                    add("player client ${c.name}: apiBase must be \"music\" or \"tube\"")
                }
                c.origin?.let { o ->
                    urlProblem("player client ${c.name} origin", o)?.let(::add)
                }
            }
        }
        val names = file.playerClients.map { it.name.uppercase() }.toSet()
        if (file.clientOrder.isEmpty() || file.clientOrder.size > MAX_CLIENTS) {
            add("clientOrder must hold 1–$MAX_CLIENTS names")
        } else file.clientOrder.forEach { n ->
            if (n.uppercase() !in names) add("clientOrder names an unknown client ($n)")
        }
        if (!isBareHostSuffix(file.streamHostSuffix)) add("streamHostSuffix must be a bare public domain suffix")
        if (file.links.hosts.isEmpty() || file.links.hosts.size > MAX_HOSTS) {
            add("links.hosts must hold 1–$MAX_HOSTS hosts")
        } else file.links.hosts.forEach { h ->
            if (!isBareHost(h)) add("links.hosts holds hosts, not URLs ($h)")
            else if (isPrivateHost(h)) add("links.hosts must not name a private host ($h)")
        }
        if (!isBareHost(file.links.shortHost)) add("links.shortHost must be a bare host")
        else if (isPrivateHost(file.links.shortHost)) add("links.shortHost must not be private")
        file.catalogue?.let { catalogue ->
            urlProblem("catalogue.apiBase", catalogue.apiBase)?.let(::add)
            if (!CALL_TOKEN.matches(catalogue.searchCall)) add("catalogue.searchCall must be a plain call name")
            if (!CALL_TOKEN.matches(catalogue.detailsCall)) add("catalogue.detailsCall must be a plain call name")
            if (catalogue.urlKey.length != URL_KEY_CHARS || catalogue.urlKey.any { it.isWhitespace() }) {
                add("catalogue.urlKey must be the $URL_KEY_CHARS-character stream key")
            }
            if (catalogue.userAgent.isBlank() || catalogue.userAgent.length > MAX_URL_CHARS ||
                catalogue.userAgent.any { it == '\r' || it == '\n' }
            ) {
                add("catalogue.userAgent must be a single-line agent string")
            }
            catalogue.forwardedFor?.let { ip ->
                if (!isPublicIpv4(ip)) add("catalogue.forwardedFor must be a public IPv4 address")
            }
            catalogue.cookie?.let { cookie ->
                if (cookie.isBlank() || cookie.length > MAX_SHORT ||
                    cookie.any { it == '\r' || it == '\n' }
                ) {
                    add("catalogue.cookie must be a single-line cookie string")
                }
            }
        }
    }

    private fun urlProblem(label: String, value: String): String? {
        if (value.isBlank() || value.length > MAX_URL_CHARS) return "$label must be a URL under $MAX_URL_CHARS chars"
        // Shape enforced manually (rather than a platform URI type) so this
        // stays pure-JVM testable: scheme, no credentials, valid public host.
        if (!value.startsWith("https://")) return "$label must be an https URL"
        val authority = value.substringAfter("://").substringBefore("/")
        if (authority.isEmpty() || "@" in authority) return "$label must not carry credentials"
        val host = authority.substringBefore(":")
        if (!isBareHost(host)) return "$label must name a valid host"
        if (isPrivateHost(host)) return "$label must not point at a private host"
        return null
    }

    internal fun isBareHost(host: String): Boolean {
        val h = host.lowercase().trim().removeSuffix(".")
        if (h.isEmpty() || h.length > 253 || "@" in h || "/" in h || " " in h) return false
        if (":" in h) return isPublicIpv6(h) // literal IP
        if (!HOST_CHARS.matches(h)) return false
        if (h.startsWith(".") || h.startsWith("-") || ".." in h) return false
        // Single-label names only resolve locally; a service file has no
        // business pointing at them.
        if ("." !in h && !isPublicIpv4(h)) return false
        return true
    }

    internal fun isBareHostSuffix(suffix: String): Boolean {
        val s = suffix.lowercase().trim().removePrefix(".").removeSuffix(".")
        return "." in s && isBareHost(s) && !isPrivateHost(s)
    }

    /**
     * Whether [host] resolves somewhere other than this device or LAN.
     * Test-only names under RFC 2606 (.invalid/.example/.test) pass: they
     * are safe precisely because they resolve nowhere.
     */
    internal fun isPrivateHost(host: String): Boolean {
        val h = host.lowercase().trim().removeSuffix(".")
        if (h == "localhost" || h.endsWith(".local") || h.endsWith(".localhost")) return true
        if (isPublicIpv4(h).not() && IPV4.matches(h)) return true // malformed IPv4
        if (IPV4.matches(h)) {
            val o = h.split(".").map { it.toInt() }
            val (a, b) = o
            return a == 10 || a == 127 || a == 0 ||
                (a == 172 && b in 16..31) ||
                (a == 192 && b == 168) ||
                (a == 169 && b == 254) ||
                a >= 224
        }
        if (":" in h) return !isPublicIpv6(h)
        return false
    }

    private fun isPublicIpv4(h: String): Boolean {
        if (!IPV4.matches(h)) return false
        val o = h.split(".").mapNotNull { it.toIntOrNull() }
        if (o.size != 4 || o.any { it !in 0..255 }) return false
        val (a, b) = o
        return !(a == 10 || a == 127 || a == 0 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254) ||
            a >= 224)
    }

    private fun isPublicIpv6(h: String): Boolean {
        val v = h.lowercase().removePrefix("[").removeSuffix("]")
        if (v == "::1") return false
        return !(v.startsWith("fe80") || v.startsWith("fc") || v.startsWith("fd") || v.startsWith("ff"))
    }

    internal fun fingerprintOf(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)

    // ---- Typed accessors (all throw [ServiceFileRequired] while empty) --------

    fun musicBase(): String = requireReady().file.endpoints.musicBase
    fun tubeBase(): String = requireReady().file.endpoints.tubeBase
    fun musicOrigin(): String = requireReady().file.endpoints.musicOrigin
    fun tubeOrigin(): String = requireReady().file.endpoints.tubeOrigin
    fun webName(): String = requireReady().file.webClient.name
    fun webVersion(): String = requireReady().file.webClient.version
    fun webId(): String = requireReady().file.webClient.id
    fun webUa(): String = requireReady().file.webClient.ua
    fun webStats(): StatsP = requireReady().file.webClient.stats
    fun swDataUrl(): String = requireReady().file.swDataUrl
    fun loginOrigin(): String = requireReady().file.login.origin
    fun loginUrl(): String = requireReady().file.login.loginUrl
    fun loginCookieHosts(): List<String> = requireReady().file.login.cookieOrigins
    fun catalogue(): CatalogueBlock =
        requireReady().file.catalogue ?: throw ServiceFileRequired()
    fun shareOrigin(): String = requireReady().file.links.shareOrigin
    fun linkHosts(): Set<String> =
        requireReady().file.links.let { (it.hosts + it.shortHost).map { h -> hostOf(h) }.toSet() }
    fun shortHost(): String = hostOf(requireReady().file.links.shortHost)

    fun watchUrl(videoId: String): String = "${shareOrigin()}/watch?v=$videoId"
    fun playlistUrl(listId: String): String =
        "${shareOrigin()}/playlist?list=${listId.removePrefix("VL")}"
    fun browseUrl(browseId: String): String = "${shareOrigin()}/browse/$browseId"

    /** Whether [url] names the configured stream host (or its subdomains). */
    fun isStreamHost(url: String): Boolean {
        val suffix = requireReady().file.streamHostSuffix.lowercase().trim()
            .removePrefix(".").removeSuffix(".")
        val host = runCatching {
            url.substringAfter("://").substringBefore("/").substringBefore(":").lowercase()
        }.getOrNull()?.removeSuffix(".") ?: return false
        return host == suffix || host.endsWith(".$suffix")
    }

    /**
     * The browser-shaped web client as a player identity. Only meaningful
     * where a caller dresses a fetch as it; the player walk itself lives in
     * the extraction library's catalog now.
     */
    fun webPlayerClient(): PlayerClient {
        val file = requireReady().file
        return PlayerClient(
            clientName = file.webClient.name,
            clientVersion = file.webClient.version,
            userAgent = file.webClient.ua,
            origin = file.endpoints.musicOrigin,
        )
    }

    /**
     * Player clients in file order, cheapest and most reliable first as the
     * file's author ranked them. Used to dress media fetches for URLs the
     * extraction library did not mint (chiefly the extractor failsafe's).
     */
    fun orderedClients(): List<PlayerClient> {
        val file = requireReady().file
        val byName = file.playerClients.associateBy { it.name.uppercase() }
        val ordered = file.clientOrder.mapNotNull { byName[it.uppercase()] } +
            file.playerClients.filter { it.name.uppercase() !in file.clientOrder.map { o -> o.uppercase() }.toSet() }
        return ordered.map { it.toPlayerClient() }
    }

    /**
     * The client a stream URL says minted it, so the media fetch can be
     * dressed as that client. Falls back to the first ordered client for a
     * URL that names no known client.
     */
    fun clientForUrl(url: String): PlayerClient {
        val ordered = orderedClients()
        val name = runCatching {
            url.substringAfter("?").split("&")
                .firstOrNull { it.startsWith("c=") }
                ?.removePrefix("c=")?.uppercase()
        }.getOrNull()
        if (name.isNullOrBlank()) return ordered.first()
        return ordered.firstOrNull {
            it.clientName.uppercase() == name || name.startsWith(it.clientName.uppercase())
        } ?: ordered.first()
    }

    private fun ServiceClient.toPlayerClient(): PlayerClient = PlayerClient(
        clientName = name,
        clientVersion = version,
        userAgent = ua,
        origin = origin,
    )

    private fun hostOf(value: String): String =
        value.lowercase().trim().removeSuffix(".").substringAfter("://").substringBefore("/")

    private const val PREFS = "service_config"
    private const val KEY_JSON = "service_file_json"
    private const val KEY_IMPORTED_AT = "service_file_imported_at"

    private const val MAX_FILE_CHARS = 65_536
    private const val MAX_URL_CHARS = 2_048
    private const val MAX_SHORT = 128
    private const val MAX_CLIENTS = 16
    private const val MAX_HOSTS = 32
    /** Stream-key length the catalogue decipher expects. */
    private const val URL_KEY_CHARS = 8

    private val HOST_CHARS = Regex("^[a-z0-9.-]+$")
    private val CALL_TOKEN = Regex("^[A-Za-z0-9._-]{1,64}$")
    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
}
