package com.music.bitchord.data.listentogether

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.music.bitchord.BitChordApplication
import com.music.bitchord.BuildConfig
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.settings.AppSettings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed interface ServerUrlError {
    data object Whitespace : ServerUrlError
    data object InvalidScheme : ServerUrlError
    data object InvalidHost : ServerUrlError
    data object InvalidPort : ServerUrlError
    data object InvalidPath : ServerUrlError
    data object HasPath : ServerUrlError
    data object HasQuery : ServerUrlError
    data object HasFragment : ServerUrlError
    data object Malformed : ServerUrlError
}

sealed interface ServerUrlValidationResult {
    data class Valid(val normalizedUrl: String) : ServerUrlValidationResult
    data class Invalid(val error: ServerUrlError) : ServerUrlValidationResult
}

sealed interface ServerConnectionState {
    data object Checking : ServerConnectionState
    data class DefaultOnline(val latencyMs: Long) : ServerConnectionState
    data class CustomOnline(val latencyMs: Long) : ServerConnectionState
    data class CustomFallback(val latencyMs: Long) : ServerConnectionState
    data object Offline : ServerConnectionState
}

data class ProbeResult(val isOnline: Boolean, val latencyMs: Long = 0L)

val ServerConnectionState.latencyMs: Long?
    get() = when (this) {
        is ServerConnectionState.DefaultOnline -> latencyMs
        is ServerConnectionState.CustomOnline -> latencyMs
        is ServerConnectionState.CustomFallback -> latencyMs
        ServerConnectionState.Checking, ServerConnectionState.Offline -> null
    }

val ServerConnectionState.isFallback: Boolean
    get() = this is ServerConnectionState.CustomFallback

val ServerConnectionState.health: ListenTogether.Health
    get() = when (this) {
        ServerConnectionState.Checking -> ListenTogether.Health.CHECKING
        is ServerConnectionState.DefaultOnline,
        is ServerConnectionState.CustomOnline,
        is ServerConnectionState.CustomFallback -> ListenTogether.Health.ONLINE
        ServerConnectionState.Offline -> ListenTogether.Health.OFFLINE
    }

/**
 * Listen together: one party, shared by up to five signed-in devices.
 *
 * This object is the whole client half of the feature — membership, the socket,
 * the clock, and the controls any member may send. It does **not** touch the
 * player. What it publishes instead is [partyPositionMs]: where this device
 * ought to be, right now, on its own clock.
 * [PartySync][com.music.bitchord.playback.PartySync] is what binds that to
 * Media3, and it lives in the playback service rather than here, because
 * everything above that line is testable and everything below it is not.
 *
 * ## A party lasts as long as the app is open
 *
 * Backgrounding, the screen going off, a tunnel, a handover — none of those end
 * a party; that is most of what listening together looks like and the whole
 * reconnect loop below exists for it. Closing the app does end it. The slot is
 * given back at the next launch (see [init]) rather than kept warm, because a
 * party nobody is in is a party of five that only holds four.
 *
 * ## How it stays in time
 *
 * Nothing here acts on "play now" messages, and correctness never depends on
 * when a frame arrived. The server holds a position and the server time that
 * position was true at; [ServerClock] measures this device's offset from that
 * clock; and the playhead is arithmetic from the two. A frame delayed 300 ms
 * carries an anchor 300 ms older and still lands in exactly the right place —
 * which is what makes a party survive one member being on bad mobile data.
 *
 * Three things back that up:
 *
 *  - **Sequence numbers.** Every state the server sends carries a [seq]
 *    [PartyPlayback.seq] that only climbs. Anything not newer than what has
 *    already been applied is dropped unread, so two people pressing pause at
 *    the same moment settle instead of fighting.
 *  - **A heartbeat from the server.** Every few seconds the party is re-told
 *    where it is, unprompted. Lost frames, a phone back from doze, an offset
 *    that has wandered — none of those announce themselves, so nothing waits
 *    to be asked.
 *  - **A reconnect loop that assumes the socket will die.** On a phone it will:
 *    tunnels, handovers, screen-off. The membership outlives the socket (the
 *    server holds the slot through a grace period), so reconnecting is
 *    invisible to everyone else, and the clock is re-sampled on arrival rather
 *    than trusted across the gap.
 *
 * ## Identity
 *
 * Creating or joining requires a signed-in account — that is what puts real
 * names and faces in the member list on every device — and [identity] is where
 * that is enforced on this side. The server cannot verify the claim; what it
 * can do is refuse anyone without the token it minted, which is why the token
 * is stored and never shown.
 */
object ListenTogether {

    enum class Connection { OFFLINE, CONNECTING, LIVE }

    data class State(
        val code: String? = null,
        val you: PartyMember? = null,
        val members: List<PartyMember> = emptyList(),
        val maxMembers: Int = 5,
        /** @see controlsLocked */
        val hostOnlyControl: Boolean = false,
        val playback: PartyPlayback = PartyPlayback(),
        /**
         * Held separately from [playback] because it arrives separately: the
         * state frame carries only a sequence number for it, and this is
         * replaced when the server says the list has actually changed.
         */
        val queue: PartyQueue = PartyQueue(),
        val connection: Connection = Connection.OFFLINE,
        /** False until the first round trip; the playhead is a guess until then. */
        val clockSynced: Boolean = false,
        val roundTripMs: Long = 0,
        /** The last thing that went wrong, for the screen to show. */
        val error: String? = null,
    ) {
        val inParty: Boolean get() = code != null
        val isFull: Boolean get() = members.size >= maxMembers

        /**
         * Whether this device may not drive the music.
         *
         * True only for a listener in a party whose host has taken control of
         * it — the host is never locked out of their own party, and a device
         * that is not in one is not in this feature's business at all. The
         * server enforces the same rule, so this is what the app shows rather
         * than what makes it true; see `backend/party.MayControl`.
         *
         * Host is reassigned when a host leaves, so this can go false under a
         * listener mid-party. Everything reading it has to follow.
         */
        val controlsLocked: Boolean
            get() = inParty && hostOnlyControl && you?.isHost != true
    }

    /** A refusal from the server, carrying the machine-readable half. */
    class PartyException(val code: String, message: String, val statusCode: Int? = null) : Exception(message)

    sealed interface SwitchPartyResult {
        data class Success(val partyCode: String) : SwitchPartyResult
        data class TargetFailedRecovered(val partyCode: String, val targetError: String) : SwitchPartyResult
        data class TargetFailedNoParty(val targetError: String) : SwitchPartyResult
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val http = HttpClient(OkHttp) {
        engine {
            config {
                // Not the shared [Http.client]: that one is tuned for streaming
                // media, and its read timeout would take down a socket that is
                // merely quiet. A party can sit paused for ten minutes and the
                // connection is not in trouble — the server's own heartbeat and
                // the ping below are what say whether it is.
                readTimeout(0, TimeUnit.MILLISECONDS)
                connectTimeout(15, TimeUnit.SECONDS)
                pingInterval(20, TimeUnit.SECONDS)
                retryOnConnectionFailure(true)
            }
        }
        install(ContentNegotiation) { json(json) }
        // Installed with no defaults so it changes nothing on its own — the
        // socket in particular must stay open indefinitely. It exists so the
        // health check can set a bound of its own; every other request is
        // left to OkHttp's connect timeout above.
        install(HttpTimeout)
        install(WebSockets)
        // Off, so a 409 "party full" can be read out of the body and shown as
        // itself rather than arriving as a transport exception.
        expectSuccess = false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = ServerClock()
    private val switchMutex = Mutex()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _activity = MutableStateFlow<List<PartyActivity>>(emptyList())
    /** Last 100 actions for this app session only. */
    val activity: StateFlow<List<PartyActivity>> = _activity.asStateFlow()

    /**
     * A server the user has pointed this install at instead of the built-in one.
     *
     * Blank means "the one this build ships with", and that address is
     * deliberately never published — not through this flow, not on screen, and
     * not in a log. See [redact]. So this is the only server address the app
     * will ever show back, because it is the only one the user typed.
     */
    fun parseAndNormalizeServerUrl(raw: String): ServerUrlValidationResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ServerUrlValidationResult.Valid("")
        if (trimmed.any { it.isWhitespace() }) return ServerUrlValidationResult.Invalid(ServerUrlError.Whitespace)

        val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"

        val uri = try {
            URI(candidate)
        } catch (_: Exception) {
            val authority = candidate.substringAfter("://").substringBefore("/").substringBefore("?").substringBefore("#")
            if (authority.isEmpty()) {
                return ServerUrlValidationResult.Invalid(ServerUrlError.InvalidHost)
            }
            val portStr = authority.substringAfterLast(':', "")
            if (portStr.isNotEmpty() && portStr.all { it.isDigit() }) {
                val portNum = portStr.toLongOrNull()
                if (portNum != null && portNum !in 1..65535) {
                    return ServerUrlValidationResult.Invalid(ServerUrlError.InvalidPort)
                }
            }
            return ServerUrlValidationResult.Invalid(ServerUrlError.InvalidHost)
        }

        val scheme = uri.scheme?.lowercase() ?: return ServerUrlValidationResult.Invalid(ServerUrlError.InvalidScheme)
        if (scheme != "http" && scheme != "https") {
            return ServerUrlValidationResult.Invalid(ServerUrlError.InvalidScheme)
        }

        if (!uri.rawQuery.isNullOrEmpty() || candidate.contains("?")) {
            return ServerUrlValidationResult.Invalid(ServerUrlError.HasQuery)
        }

        if (!uri.rawFragment.isNullOrEmpty() || candidate.contains("#")) {
            return ServerUrlValidationResult.Invalid(ServerUrlError.HasFragment)
        }

        val pathSegments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }
        for (segment in pathSegments) {
            if (segment == "." || segment == "..") {
                return ServerUrlValidationResult.Invalid(ServerUrlError.InvalidPath)
            }
        }
        val canonicalPath = if (pathSegments.isEmpty()) "" else "/" + pathSegments.joinToString("/")

        val port = uri.port
        if (port != -1 && port !in 1..65535) {
            return ServerUrlValidationResult.Invalid(ServerUrlError.InvalidPort)
        }
        val authority = candidate.substringAfter("://").substringBefore("/").substringBefore("?").substringBefore("#")
        val portStr = authority.substringAfterLast(':', "")
        if (portStr.isNotEmpty() && portStr.all { it.isDigit() } && authority.contains(":")) {
            val portNum = portStr.toLongOrNull()
            if (portNum != null && portNum !in 1..65535) {
                return ServerUrlValidationResult.Invalid(ServerUrlError.InvalidPort)
            }
        }

        val rawHost = uri.host ?: return ServerUrlValidationResult.Invalid(ServerUrlError.InvalidHost)
        if (rawHost.isEmpty()) return ServerUrlValidationResult.Invalid(ServerUrlError.InvalidHost)

        val canonicalHost: String = when {
            rawHost.startsWith("[") && rawHost.endsWith("]") -> {
                val unbracketed = rawHost.substring(1, rawHost.length - 1)
                if (!unbracketed.contains(":")) return ServerUrlValidationResult.Invalid(ServerUrlError.InvalidHost)
                "[${unbracketed.lowercase()}]"
            }
            rawHost.contains(":") -> {
                "[${rawHost.lowercase()}]"
            }
            rawHost.equals("localhost", ignoreCase = true) -> {
                "localhost"
            }
            else -> {
                if (rawHost.length > 253) return ServerUrlValidationResult.Invalid(ServerUrlError.InvalidHost)
                if (rawHost.startsWith(".") || rawHost.endsWith(".")) {
                    return ServerUrlValidationResult.Invalid(ServerUrlError.InvalidHost)
                }
                val labels = rawHost.split('.')
                if (labels.size < 2) return ServerUrlValidationResult.Invalid(ServerUrlError.InvalidHost)
                val labelRegex = Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?$")
                for (label in labels) {
                    if (label.isEmpty() || label.length > 63 || !label.matches(labelRegex)) {
                        return ServerUrlValidationResult.Invalid(ServerUrlError.InvalidHost)
                    }
                }
                rawHost.lowercase()
            }
        }

        val portSuffix = if (port != -1) ":$port" else ""
        return ServerUrlValidationResult.Valid("${scheme.lowercase()}://$canonicalHost$portSuffix$canonicalPath")
    }

    private val _customServer = MutableStateFlow("")
    val customServerUrl: StateFlow<String> = _customServer.asStateFlow()

    /**
     * The default party server this build ships pointed at, from `LISTEN_TOGETHER_SERVER`
     * in `local.properties` or build environment.
     */
    val defaultServer: String = when (val res = parseAndNormalizeServerUrl(BuildConfig.LISTEN_TOGETHER_SERVER)) {
        is ServerUrlValidationResult.Valid -> res.normalizedUrl
        is ServerUrlValidationResult.Invalid -> error("Invalid BuildConfig.LISTEN_TOGETHER_SERVER: ${BuildConfig.LISTEN_TOGETHER_SERVER}")
    }

    /**
     * The dynamic server currently determined to be healthy and available for IDLE operations.
     * Probes custom server first if configured; falls back to [defaultServer] if custom is down.
     */
    private val _effectiveIdleServer = MutableStateFlow(defaultServer)
    fun effectiveIdleServerBase(): String = _effectiveIdleServer.value

    /**
     * The server a party switch should target when the invite itself does not
     * name one.
     *
     * [customServer] is `""` when nobody has configured one — not the
     * default server's address — so a switch with no explicit target (a
     * typed code, entered while already live in a party) has to fall back to
     * whichever server idle joins already resolve to. Handed the raw empty
     * string instead, [switchPartyWithRecovery] treats it as a malformed
     * target and refuses the switch outright.
     */
    fun resolveSwitchTarget(
        inviteServer: String?,
        customServer: String,
        idleServer: String = effectiveIdleServerBase(),
    ): String = inviteServer ?: customServer.ifBlank { idleServer }

    /**
     * The actual server hosting the active party session. Non-null while [State.inParty] is true.
     * Immutable during the party session and never mutated by idle health checks.
     */
    @Volatile
    private var activePartyServerBase: String? = null
    fun activePartyServerBase(): String? = activePartyServerBase

    /** Whether idle operations are currently falling back to the default server. */
    val isUsingDefaultFallback: Boolean
        get() {
            val configured = _customServer.value
            return configured.isNotBlank() &&
                effectiveIdleServerBase() == defaultServer &&
                configured != defaultServer
        }

    /** Whether a party can be reached at all — a built-in or a custom address. */
    val hasServer: Boolean get() = effectiveIdleServerBase().isNotBlank()

    enum class Health { UNKNOWN, CHECKING, ONLINE, OFFLINE }

    data class ServerStatus(
        val health: Health = Health.UNKNOWN,
        val latencyMs: Long = 0,
        val isFallback: Boolean = false,
    )

    private val _serverConnectionState = MutableStateFlow<ServerConnectionState>(ServerConnectionState.Checking)
    val serverConnectionState: StateFlow<ServerConnectionState> = _serverConnectionState.asStateFlow()

    private val _serverStatus = MutableStateFlow(ServerStatus())
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    fun isEligibleForFallback(error: Throwable): Boolean = when (error) {
        is java.net.UnknownHostException,
        is java.net.ConnectException,
        is java.net.NoRouteToHostException,
        is java.net.PortUnreachableException,
        is java.net.SocketTimeoutException,
        is io.ktor.client.plugins.HttpRequestTimeoutException,
        is io.ktor.client.network.sockets.SocketTimeoutException,
        is io.ktor.client.network.sockets.ConnectTimeoutException -> true
        is PartyException -> (error.statusCode ?: 0) in 500..599
        else -> {
            val cause = error.cause
            if (cause != null && cause !== error && isEligibleForFallback(cause)) true
            else false
        }
    }

    const val CUSTOM_SERVER_TIMEOUT_MS = 6_000L
    const val DEFAULT_SERVER_TIMEOUT_MS = 30_000L

    private var isScreenActive: Boolean = false
    private var healthMonitorJob: Job? = null
    private val resolutionMutex = Mutex()
    private var activeResolutionJob: Job? = null
    private var resolutionGeneration = 0L

    fun setScreenActive(active: Boolean) {
        isScreenActive = active
        if (active) {
            refreshServerHealth(showChecking = false)
        }
    }

    private fun startHealthMonitor() {
        healthMonitorJob?.cancel()
        healthMonitorJob = scope.launch {
            while (isActive) {
                val delayMs = if (isScreenActive) 10_000L else 30_000L
                delay(delayMs)
                refreshServerHealth(showChecking = false)
            }
        }
    }

    suspend fun resolveServerConnection(
        customServer: String,
        defaultServer: String,
        probeCustom: suspend () -> ProbeResult,
        probeDefault: suspend () -> ProbeResult,
    ): Pair<String, ServerConnectionState> {
        val normCustom = when (val res = parseAndNormalizeServerUrl(customServer)) {
            is ServerUrlValidationResult.Valid -> res.normalizedUrl
            is ServerUrlValidationResult.Invalid -> ""
        }
        val hasCustom = normCustom.isNotBlank() && normCustom != defaultServer

        return if (hasCustom) {
            val customProbe = probeCustom()
            if (customProbe.isOnline) {
                normCustom to ServerConnectionState.CustomOnline(customProbe.latencyMs)
            } else {
                val defaultProbe = probeDefault()
                if (defaultProbe.isOnline) {
                    // Invariant: CustomFallback latency is strictly the default server's latency
                    defaultServer to ServerConnectionState.CustomFallback(defaultProbe.latencyMs)
                } else {
                    defaultServer to ServerConnectionState.Offline
                }
            }
        } else {
            val defaultProbe = probeDefault()
            if (defaultProbe.isOnline) {
                defaultServer to ServerConnectionState.DefaultOnline(defaultProbe.latencyMs)
            } else {
                defaultServer to ServerConnectionState.Offline
            }
        }
    }

    private suspend fun resolveServerConnectionSerialized(
        showChecking: Boolean = false,
        targetGeneration: Long? = null,
    ): ServerConnectionState {
        if (showChecking) {
            resolutionMutex.withLock {
                if (targetGeneration == null || targetGeneration == resolutionGeneration) {
                    _serverConnectionState.value = ServerConnectionState.Checking
                    _serverStatus.value = ServerStatus(Health.CHECKING)
                }
            }
        }

        val custom = _customServer.value
        val (effectiveServer, state) = resolveServerConnection(
            customServer = custom,
            defaultServer = defaultServer,
            probeCustom = { probeHealthWithLatency(custom, CUSTOM_SERVER_TIMEOUT_MS) },
            probeDefault = { probeHealthWithLatency(defaultServer, DEFAULT_SERVER_TIMEOUT_MS) },
        )

        resolutionMutex.withLock {
            if (targetGeneration == null || targetGeneration == resolutionGeneration) {
                _effectiveIdleServer.value = effectiveServer
                _serverConnectionState.value = state
                _serverStatus.value = ServerStatus(
                    health = state.health,
                    latencyMs = state.latencyMs ?: 0L,
                    isFallback = state.isFallback,
                )
            }
        }
        return state
    }

    fun refreshServerHealth(showChecking: Boolean = false) {
        scope.launch {
            val (job, _) = resolutionMutex.withLock {
                activeResolutionJob?.cancel()
                val nextGen = ++resolutionGeneration
                val newJob = scope.async(start = CoroutineStart.LAZY) {
                    resolveServerConnectionSerialized(showChecking = showChecking, targetGeneration = nextGen)
                }
                activeResolutionJob = newJob
                Pair(newJob, nextGen)
            }
            job.start()
        }
    }

    suspend fun probeHealthWithLatency(serverUrl: String, timeoutMs: Long): ProbeResult {
        val raw = resolveHttpBase(serverUrl)
        if (raw.isBlank()) return ProbeResult(isOnline = false, latencyMs = 0L)
        val start = ServerClock.localNowMs()
        val isOnline = runCatching {
            val response = http.get("$raw/healthz") {
                timeout { requestTimeoutMillis = timeoutMs }
            }
            if (!response.status.isSuccess()) return@runCatching false
            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            body.contains("\"ok\":true")
        }.getOrElse {
            Log.w(TAG, "health check failed for ${redact(raw)}: ${redact(it.message)}")
            false
        }
        val elapsed = ServerClock.localNowMs() - start
        return ProbeResult(isOnline = isOnline, latencyMs = if (isOnline) elapsed.coerceAtLeast(0L) else 0L)
    }

    private suspend fun probeHealth(baseUrl: String): Boolean =
        probeHealthWithLatency(baseUrl, HEALTH_TIMEOUT_MS).isOnline

    data class HealthResolution(
        val resolvedServer: String,
        val health: Health,
        val isFallback: Boolean,
    )

    suspend fun computeHealthResolution(
        customServer: String,
        probeCustom: suspend () -> Boolean,
        probeDefault: suspend () -> Boolean,
    ): HealthResolution {
        val custom = normalizeServerBase(customServer)
        val hasCustom = custom.isNotBlank() && custom != defaultServer
        return if (hasCustom) {
            if (probeCustom()) {
                HealthResolution(resolvedServer = custom, health = Health.ONLINE, isFallback = false)
            } else if (probeDefault()) {
                HealthResolution(resolvedServer = defaultServer, health = Health.ONLINE, isFallback = true)
            } else {
                HealthResolution(resolvedServer = defaultServer, health = Health.OFFLINE, isFallback = false)
            }
        } else {
            if (probeDefault()) {
                HealthResolution(resolvedServer = defaultServer, health = Health.ONLINE, isFallback = false)
            } else {
                HealthResolution(resolvedServer = defaultServer, health = Health.OFFLINE, isFallback = false)
            }
        }
    }

    private lateinit var prefs: SharedPreferences
    private var token: String? = null

    @Volatile
    private var session: DefaultClientWebSocketSession? = null
    private var socketJob: Job? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val rawSaved = prefs.getString(KEY_SERVER, null)?.trim().orEmpty()
        val normalizedSaved = when (val res = parseAndNormalizeServerUrl(rawSaved)) {
            is ServerUrlValidationResult.Valid -> res.normalizedUrl
            is ServerUrlValidationResult.Invalid -> ""
        }
        _customServer.value = normalizedSaved
        _effectiveIdleServer.value = if (normalizedSaved.isNotBlank()) {
            normalizedSaved
        } else {
            defaultServer
        }

        val code = prefs.getString(KEY_CODE, null)
        val saved = prefs.getString(KEY_TOKEN, null)
        prefs.edit().remove(KEY_CODE).remove(KEY_TOKEN).apply()
        if (!code.isNullOrBlank() && !saved.isNullOrBlank()) {
            releaseStaleSlot(code, saved)
        }

        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager != null) {
            runCatching {
                manager.registerDefaultNetworkCallback(
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            refreshServerHealth(showChecking = false)
                        }
                        override fun onLost(network: Network) {
                            _serverConnectionState.value = ServerConnectionState.Offline
                            _serverStatus.value = ServerStatus(Health.OFFLINE)
                        }
                        override fun onCapabilitiesChanged(
                            network: Network,
                            capabilities: NetworkCapabilities,
                        ) {
                            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                                refreshServerHealth(showChecking = false)
                            }
                        }
                    }
                )
            }
        }

        startHealthMonitor()
        refreshServerHealth(showChecking = true)
    }

    /**
     * Hands a previous process's slot back, so the others see them leave now
     * rather than when the server's disconnect grace sweeps it.
     */
    private fun releaseStaleSlotOnServer(serverBase: String, code: String, held: String) {
        if (serverBase.isBlank() || code.isBlank() || held.isBlank()) return
        scope.launch {
            runCatching {
                http.post("$serverBase/api/parties/$code/leave") {
                    header("Authorization", "Bearer $held")
                }
            }.onFailure { failure ->
                Log.i(TAG, "stale party slot left to the server's grace: ${redact(failure.message)}")
            }
        }
    }

    private fun releaseStaleSlot(code: String, held: String) {
        val server = activePartyServerBase ?: resolveHttpBase(effectiveIdleServerBase())
        releaseStaleSlotOnServer(server, code, held)
    }

    /** Points this install at another server, or back at the default one if blank. */
    suspend fun setCustomServerUrl(normalizedUrl: String): ServerConnectionState {
        require(parseAndNormalizeServerUrl(normalizedUrl) == ServerUrlValidationResult.Valid(normalizedUrl)) {
            "setCustomServerUrl accepts only canonical URLs produced by parseAndNormalizeServerUrl"
        }

        prefs.edit().putString(KEY_SERVER, normalizedUrl).apply()
        _customServer.value = normalizedUrl

        val (job, _) = resolutionMutex.withLock {
            activeResolutionJob?.cancel()
            val nextGen = ++resolutionGeneration
            val newJob = scope.async(start = CoroutineStart.LAZY) {
                resolveServerConnectionSerialized(showChecking = true, targetGeneration = nextGen)
            }
            activeResolutionJob = newJob
            Pair(newJob, nextGen)
        }
        job.start()
        return job.await()
    }

    /** Whether this device has an account it can jam as. */
    fun canJoin(): Boolean = identity() != null

    fun nickname(): String = prefs.getString(KEY_NICKNAME, null).orEmpty()

    fun setNickname(value: String) {
        prefs.edit().putString(KEY_NICKNAME, value.trim().take(80)).apply()
    }

    /**
     * Opens the socket for a membership that was restored from storage.
     */
    fun ensureConnected() {
        if (_state.value.code == null || token == null) return
        if (socketJob?.isActive == true) return
        connect()
    }

    // ------------------------------------------------------------ joining --

    suspend fun createParty(nickname: String = nickname(), maxMembers: Int = 5): Result<String> = switchMutex.withLock {
        val primary = effectiveIdleServerBase()
        val primaryNormalized = resolveHttpBase(primary)
        val attempt = runCatching {
            doCreateOnServer(primaryNormalized, nickname, maxMembers)
        }

        if (attempt.isSuccess) {
            return@withLock Result.success(attempt.getOrThrow())
        }

        val failure = attempt.exceptionOrNull() ?: return@withLock Result.failure(IllegalStateException("Unknown create failure"))

        if (normalizeServerBase(primary) != defaultServer && isEligibleForFallback(failure)) {
            Log.w(TAG, "createParty failed on custom server, falling back to default: ${redact(failure.message)}")
            val fallbackNormalized = resolveHttpBase(defaultServer)
            val fallbackAttempt = runCatching {
                doCreateOnServer(fallbackNormalized, nickname, maxMembers)
            }
            if (fallbackAttempt.isSuccess) {
                return@withLock Result.success(fallbackAttempt.getOrThrow())
            }
            val fallbackFailure = fallbackAttempt.exceptionOrNull() ?: failure
            _state.update { it.copy(error = fallbackFailure.displayMessage()) }
            return@withLock Result.failure(fallbackFailure)
        }

        _state.update { it.copy(error = failure.displayMessage()) }
        Result.failure(failure)
    }

    private suspend fun doCreateOnServer(serverBase: String, nickname: String, maxMembers: Int): String {
        val who = identity(nickname) ?: throw PartyException("not_signed_in", "Sign in to listen together.")
        if (serverBase.isBlank()) throw PartyException("no_server", "Set the party server address first.")
        val membership = post(
            "$serverBase/api/parties",
            JoinRequest(
                who.userId, who.deviceId, who.name, who.avatar, maxMembers,
                autoplayEnabled = AppSettings.autoplay.value,
            ),
        )

        withContext(NonCancellable) {
            activePartyServerBase = serverBase
            token = membership.token
            prefs.edit()
                .putString(KEY_CODE, membership.code)
                .putString(KEY_TOKEN, membership.token)
                .apply()
            clock.reset()
            _state.value = State(
                code = membership.code,
                you = membership.you,
                members = membership.party.members,
                maxMembers = membership.party.maxMembers,
                playback = membership.party.playback,
                queue = membership.party.queue,
                connection = Connection.CONNECTING,
            )
        }
        connect()
        return membership.code
    }

    /**
     * Joins a party from the currently active server.
     *
     * Architectural contract:
     * - Precondition: IDLE only.
     * - Used by manual party-code entry.
     * - Performs a direct Idle -> Live transition.
     *
     * Do not merge this with [switchPartyWithRecovery]. Deep-link invites
     * require target-first transactional semantics and recovery guarantees
     * that are intentionally not part of this API.
     */
    suspend fun joinParty(code: String, nickname: String = nickname()): Result<String> = switchMutex.withLock {
        val targetServer = resolveHttpBase(effectiveIdleServerBase())
        runCatching {
            doJoinOnServer(targetServer, code, nickname)
        }.onFailure { failure ->
            Log.w(TAG, "could not enter a party: ${redact(failure.message)}")
            _state.update { it.copy(error = failure.displayMessage()) }
        }
    }

    private suspend fun doJoinOnServer(serverBase: String, code: String, nickname: String): String {
        val who = identity(nickname) ?: throw PartyException("not_signed_in", "Sign in to listen together.")
        if (serverBase.isBlank()) throw PartyException("no_server", "Set the party server address first.")
        val cleaned = code.filter { it.isLetterOrDigit() }.uppercase()
        if (cleaned.length != CODE_LENGTH) {
            throw PartyException("bad_code", "A party code is six letters or digits.")
        }
        refuseIfRecentlyKicked(cleaned)
        val membership = post(
            "$serverBase/api/parties/$cleaned/join",
            JoinRequest(who.userId, who.deviceId, who.name, who.avatar),
        )

        withContext(NonCancellable) {
            activePartyServerBase = serverBase
            token = membership.token
            prefs.edit()
                .putString(KEY_CODE, membership.code)
                .putString(KEY_TOKEN, membership.token)
                .apply()
            clock.reset()
            _state.value = State(
                code = membership.code,
                you = membership.you,
                members = membership.party.members,
                maxMembers = membership.party.maxMembers,
                playback = membership.party.playback,
                queue = membership.party.queue,
                connection = Connection.CONNECTING,
            )
        }
        connect()
        return membership.code
    }

    /**
     * Transactionally transitions to an invite target.
     *
     * Architectural contract:
     *
     * Before successful target `/join`, this method must not mutate the current
     * party membership, current token, or persisted server preference.
     *
     * Phase 1 — Target join (cancellable):
     * - Target /join is attempted before mutating the current party or
     *   persisted server preference.
     * - If the target fails, the current party remains untouched.
     *
     * Phase 2 — Local commit (NonCancellable):
     * - Begins only after the target join succeeds.
     * - Commits the target server/token and local CONNECTING state.
     * - Dispatches old-party cleanup asynchronously.
     * - Target WebSocket establishment occurs outside the commit boundary.
     *
     * This API is intentionally separate from [joinParty]. It is the
     * deep-link transition state machine and may begin from either IDLE
     * or LIVE. It strictly contacts [targetCustomServer] and NEVER queries
     * or triggers idle fallback.
     */
    suspend fun switchPartyWithRecovery(
        targetCustomServer: String,
        targetCode: String,
        nickname: String = nickname(),
    ): SwitchPartyResult = switchMutex.withLock {
        withContext(Dispatchers.IO) {
            val who = identity(nickname)
                ?: return@withContext if (_state.value.inParty) {
                    SwitchPartyResult.TargetFailedRecovered(_state.value.code.orEmpty(), "Sign in to listen together.")
                } else {
                    SwitchPartyResult.TargetFailedNoParty("Sign in to listen together.")
                }

            val normTarget = when (val res = parseAndNormalizeServerUrl(targetCustomServer)) {
                is ServerUrlValidationResult.Valid -> res.normalizedUrl
                is ServerUrlValidationResult.Invalid -> return@withContext if (_state.value.inParty) {
                    SwitchPartyResult.TargetFailedRecovered(_state.value.code.orEmpty(), "Target server address is invalid.")
                } else {
                    SwitchPartyResult.TargetFailedNoParty("Target server address is invalid.")
                }
            }

            val targetBase = resolveHttpBase(normTarget)
            if (targetBase.isBlank()) {
                return@withContext if (_state.value.inParty) {
                    SwitchPartyResult.TargetFailedRecovered(_state.value.code.orEmpty(), "Target server address is invalid or missing.")
                } else {
                    SwitchPartyResult.TargetFailedNoParty("Target server address is invalid or missing.")
                }
            }

            val cleanedTargetCode = targetCode.filter { it.isLetterOrDigit() }.uppercase()
            if (cleanedTargetCode.length != CODE_LENGTH) {
                return@withContext if (_state.value.inParty) {
                    SwitchPartyResult.TargetFailedRecovered(_state.value.code.orEmpty(), "A party code is six letters or digits.")
                } else {
                    SwitchPartyResult.TargetFailedNoParty("A party code is six letters or digits.")
                }
            }

            if (recentKicks().containsKey(cleanedTargetCode)) {
                val refusal = "Couldn’t let you in — you’ve recently been kicked out of this party."
                return@withContext if (_state.value.inParty) {
                    SwitchPartyResult.TargetFailedRecovered(_state.value.code.orEmpty(), refusal)
                } else {
                    SwitchPartyResult.TargetFailedNoParty(refusal)
                }
            }

            val oldServerBase = activePartyServerBase ?: resolveHttpBase(_customServer.value)
            val oldCode = _state.value.code
            val oldToken = token

            // Step 1: Join Target First while old party stays connected & playing
            val targetJoinResult = runCatching {
                post(
                    "$targetBase/api/parties/$cleanedTargetCode/join",
                    JoinRequest(who.userId, who.deviceId, who.name, who.avatar),
                )
            }

            val membership = targetJoinResult.getOrElse { failure ->
                Log.w(TAG, "failed to join target party: ${redact(failure.message)}")
                val errorMsg = failure.displayMessage()
                return@withContext if (oldCode != null) {
                    SwitchPartyResult.TargetFailedRecovered(oldCode, errorMsg)
                } else {
                    SwitchPartyResult.TargetFailedNoParty(errorMsg)
                }
            }

            // Step 2: Target join succeeded! Commit switch & leave old party inside NonCancellable
            withContext(NonCancellable) {
                // Asynchronously release old party slot on old server
                if (!oldCode.isNullOrBlank() && !oldToken.isNullOrBlank() &&
                    (oldServerBase != targetBase || !oldCode.equals(membership.code, ignoreCase = true))
                ) {
                    releaseStaleSlotOnServer(oldServerBase, oldCode, oldToken)
                }

                // Stop old socket
                socketJob?.cancel()
                socketJob = null
                session = null

                // Commit active party server authority
                activePartyServerBase = targetBase

                // Commit new server URL setting
                setCustomServerUrl(normTarget)

                // Commit new party session
                token = membership.token
                prefs.edit()
                    .putString(KEY_CODE, membership.code)
                    .putString(KEY_TOKEN, membership.token)
                    .apply()
                clock.reset()
                _state.value = State(
                    code = membership.code,
                    you = membership.you,
                    members = membership.party.members,
                    maxMembers = membership.party.maxMembers,
                    playback = membership.party.playback,
                    queue = membership.party.queue,
                    connection = Connection.CONNECTING,
                )
            }

            // Outside NonCancellable: establish socket connection
            connect()

            SwitchPartyResult.Success(membership.code)
        }
    }

    /** Give up this device's slot. The party carries on without it. */
    suspend fun leaveParty() = switchMutex.withLock {
        withContext(Dispatchers.IO) {
            val code = _state.value.code
            val held = token
            val currentServer = activePartyServerBase ?: resolveHttpBase(effectiveIdleServerBase())
            socketJob?.cancel()
            socketJob = null
            session = null
            clock.reset()
            token = null
            activePartyServerBase = null
            prefs.edit().remove(KEY_CODE).remove(KEY_TOKEN).apply()
            _state.value = State()
            if (code != null && held != null) {
                releaseStaleSlotOnServer(currentServer, code, held)
            }
        }
    }

    // ------------------------------------------------------------ controls --
    //
    // Any member may send any of these, unless the host has taken control of
    // the party — see [State.controlsLocked] and [setHostOnlyControl]. The
    // server enforces that independently of anything the app does, so a control
    // sent anyway comes back refused rather than quietly obeyed.

    fun play(positionMs: Long? = null) = control("play") { positionMs?.let { put("positionMs", it) } }

    fun pause(positionMs: Long? = null) = control("pause") { positionMs?.let { put("positionMs", it) } }

    fun seek(positionMs: Long) = control("seek") { put("positionMs", positionMs) }

    fun next() = control("next") {}

    fun previous() = control("previous") {}

    fun setTrack(track: PartyTrack, positionMs: Long = 0, isPlaying: Boolean = true) =
        control("setTrack") {
            put("track", json.encodeToJsonElement(PartyTrack.serializer(), track))
            put("positionMs", positionMs)
            put("isPlaying", isPlaying)
        }

    fun setQueue(queue: List<PartyTrack>, index: Int) = control("setQueue") {
        put("queue", json.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(PartyTrack.serializer()), queue))
        put("queueIndex", index)
    }

    fun queueAdd(tracks: List<PartyTrack>, playNext: Boolean = false) = control("queueAdd") {
        put("tracks", json.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(PartyTrack.serializer()), tracks))
        put("playNext", playNext)
    }

    fun queueRemove(videoId: String) = control("queueRemove") {
        put("videoId", videoId)
    }

    fun queueClear() = control("queueClear") {}

    fun queueMove(fromIndex: Int, toIndex: Int, videoId: String? = null) = control("queueMove") {
        put("fromIndex", fromIndex)
        put("toIndex", toIndex)
        if (videoId != null) put("videoId", videoId)
    }

    fun setMaxMembers(value: Int) = control("setMaxMembers") { put("maxMembers", value) }

    fun kick(memberId: String) = control("kick") { put("memberId", memberId) }

    fun setAutoplay(enabled: Boolean) = control("setAutoplay") { put("enabled", enabled) }

    /** Host only, and refused by the server from anybody else. @see State.controlsLocked */
    fun setHostOnlyControl(enabled: Boolean) =
        control("setHostOnlyControl") { put("enabled", enabled) }

    private fun control(action: String, body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) {
        val frame = buildJsonObject {
            put("type", "control")
            put("action", action)
            body()
        }
        send(frame)
    }

    private fun send(frame: JsonObject) {
        val live = session ?: return
        scope.launch {
            runCatching { live.send(Frame.Text(frame.toString())) }
                .onFailure { Log.w(TAG, "control not sent: ${redact(it.message)}") }
        }
    }

    /**
     * A resume this device has accepted but is not allowed to perform yet.
     *
     * In a party a local `play()` is withheld and performed later, on the
     * instant the server schedules for everyone — see `PartySync.shouldDeferPlay`.
     * Nothing about the player moves during that wait, so a play button reading
     * `isPlaying` sat on "play" and then snapped to "pause" once the echo landed,
     * which reads as a glitch rather than as the deliberate wait it is.
     *
     * Written only by `PartySync`, which owns the wait; held here because the
     * player screen is the only thing that needs to see it and the playback
     * service is not something the UI can reach.
     */
    private val _awaitingStart = MutableStateFlow(false)
    val awaitingStart: StateFlow<Boolean> = _awaitingStart.asStateFlow()

    /** @see awaitingStart */
    fun setAwaitingStart(value: Boolean) {
        _awaitingStart.value = value
    }

    // --------------------------------------------------------- the playhead --

    /**
     * Where this device should be in the current track, right now.
     *
     * The one number the player layer will need, and the reason the rest of
     * this class exists. Null when there is no party, or nothing playing in it.
     *
     * Before the first pong lands there is no offset to convert with, and this
     * falls back to the server's own reading at the moment it sent the frame —
     * which is right to within the age of that frame, and wrong by exactly the
     * amount the clock sync exists to remove. So it is a starting point, not a
     * thing to seek to: wait for [State.clockSynced] before acting on it.
     */
    fun partyPositionMs(): Long? {
        val playback = _state.value.playback
        playback.track ?: return null
        if (!playback.isPlaying) return playback.positionMs
        val serverNow = clock.serverNowMs() ?: return playback.effectivePositionMs
        val elapsed = (serverNow - playback.anchorMs).coerceAtLeast(0)
        val position = playback.positionMs + elapsed
        val duration = playback.track.durationMs
        return if (duration != null) minOf(position, duration) else position
    }

    /**
     * How far in the future the party's next resume is scheduled, or 0 if it is
     * already under way. The player layer waits this out rather than starting
     * early and seeking.
     */
    fun msUntilStart(): Long {
        val playback = _state.value.playback
        if (!playback.isPlaying) return 0
        val serverNow = clock.serverNowMs() ?: return 0
        return (playback.anchorMs - serverNow).coerceAtLeast(0)
    }

    // ------------------------------------------------------------- socket --

    private fun connect() {
        socketJob?.cancel()
        socketJob = scope.launch { runSocketLoop() }
    }

    /**
     * Connect, pump frames until the connection goes away for any reason, back
     * off, go round again.
     *
     * The loop is the design, not error handling bolted onto one. A socket held
     * by a backgrounded music app will be dropped repeatedly over a listening
     * session — the radio sleeping, a Wi-Fi handover, a captive portal, the
     * server's free instance spinning down — and every one of those has to heal
     * without anyone reopening the app.
     */
    private suspend fun runSocketLoop() {
        var backoffMs = 1_000L
        while (currentScopeActive()) {
            val code = _state.value.code ?: return
            val held = token ?: return
            try {
                _state.update { it.copy(connection = Connection.CONNECTING) }
                http.webSocket(
                    urlString = "${wsBase()}/ws/parties/$code",
                    request = { header("Authorization", "Bearer $held") },
                ) {
                    session = this
                    backoffMs = 1_000L
                    _state.update { it.copy(connection = Connection.LIVE, error = null) }
                    launch { pingLoop() }
                    launch { reportLoop() }
                    for (frame in incoming) {
                        if (frame is Frame.Text) onFrame(frame.readText())
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "party socket dropped: ${redact(failure.message)}")
            } finally {
                session = null
            }
            if (!currentScopeActive()) return
            _state.update { it.copy(connection = Connection.CONNECTING) }
            // The clock is not carried across a gap. Whatever the offset was
            // before the socket died, the only honest thing to say afterwards is
            // that it has not been measured since.
            clock.reset()
            _state.update { it.copy(clockSynced = false) }
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(20_000L)
        }
    }

    private suspend fun currentScopeActive(): Boolean =
        kotlinx.coroutines.currentCoroutineContext().isActive

    /**
     * A burst on arrival, then a slow trickle.
     *
     * The burst is what makes the first seconds of a party accurate: the best of
     * several quick round trips is a far better offset than the first one, and
     * the first one is what the playhead would otherwise be built on. After
     * that the offset only has to keep up with clock drift, which on a phone is
     * milliseconds per minute.
     */
    private suspend fun DefaultClientWebSocketSession.pingLoop() {
        repeat(4) {
            ping()
            delay(300)
        }
        while (true) {
            delay(PING_INTERVAL_MS)
            ping()
        }
    }

    private suspend fun DefaultClientWebSocketSession.ping() {
        val sentAt = ServerClock.localNowMs()
        val frame = buildJsonObject {
            put("type", "ping")
            put("clientMs", sentAt)
        }
        runCatching { send(Frame.Text(frame.toString())) }
    }

    /**
     * Tells the server where this device actually is.
     *
     * Nothing is decided by it — the server's state is the truth, and a party
     * averaged towards its slowest member would be a party that drags. It keeps
     * the membership alive and makes real drift visible in the server log,
     * which is the only place the two halves of this feature can be compared.
     */
    private suspend fun DefaultClientWebSocketSession.reportLoop() {
        while (true) {
            delay(REPORT_INTERVAL_MS)
            val position = partyPositionMs() ?: continue
            val frame = buildJsonObject {
                put("type", "report")
                put("positionMs", position)
                put("isPlaying", _state.value.playback.isPlaying)
            }
            runCatching { send(Frame.Text(frame.toString())) }
        }
    }

    private fun onFrame(text: String) {
        val received = ServerClock.localNowMs()
        val frame = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        when (frame["type"]?.jsonPrimitive?.content) {
            "welcome" -> {
                val party = frame["party"]?.let {
                    runCatching { json.decodeFromJsonElement(PartySnapshot.serializer(), it) }.getOrNull()
                } ?: return
                val you = frame["you"]?.let {
                    runCatching { json.decodeFromJsonElement(PartyMember.serializer(), it) }.getOrNull()
                }
                _state.update { it.copy(
                    code = party.code,
                    you = you ?: it.you,
                    members = party.members,
                    maxMembers = party.maxMembers,
                    hostOnlyControl = party.hostOnlyControl,
                    playback = party.playback,
                    // A snapshot is the one message that carries the queue
                    // unconditionally — a device that has just arrived has no
                    // other way to learn it.
                    queue = party.queue,
                    connection = Connection.LIVE,
                    error = null,
                ) }
            }

            "pong" -> {
                val sentAt = frame["clientMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: return
                val serverMs = frame["serverMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: return
                clock.record(sentAt, serverMs, received)
                _state.update { it.copy(
                    clockSynced = clock.synced,
                    roundTripMs = clock.roundTripMs,
                ) }
            }

            "state" -> {
                val playback = frame["playback"]?.let {
                    runCatching { json.decodeFromJsonElement(PartyPlayback.serializer(), it) }.getOrNull()
                } ?: return
                // Older than what is already applied, so it says nothing. This
                // is the guard that keeps two simultaneous controllers from
                // sending each other backwards.
                if (playback.seq < _state.value.playback.seq) return
                _state.update { it.copy(playback = playback) }
                // The queue does not ride along with the state — only its
                // sequence number does. Disagreeing with the copy held here
                // means a queue frame was missed, which the heartbeat therefore
                // heals within a few seconds rather than leaving the running
                // order wrong until somebody happens to change it.
                if (playback.queueSeq != _state.value.queue.seq) {
                    send(buildJsonObject { put("type", "syncQueue") })
                }
            }

            "queue" -> {
                val queue = frame["queue"]?.let {
                    runCatching { json.decodeFromJsonElement(PartyQueue.serializer(), it) }.getOrNull()
                } ?: return
                if (queue.seq < _state.value.queue.seq) return
                _state.update { it.copy(queue = queue) }
            }

            "members" -> {
                val members = frame["members"]?.let {
                    runCatching {
                        json.decodeFromJsonElement(
                            kotlinx.serialization.builtins.ListSerializer(PartyMember.serializer()),
                            it,
                        )
                    }.getOrNull()
                } ?: return
                val maxMembers = frame["maxMembers"]?.jsonPrimitive?.content?.toIntOrNull() ?: _state.value.maxMembers
                // Absent on a server that predates the setting, which is not
                // the same as "off": keeping the value already held means an
                // upgrade mid-party does not silently unlock the party.
                val hostOnly = frame["hostOnlyControl"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                    ?: _state.value.hostOnlyControl
                _state.update { current ->
                    current.copy(
                        members = members,
                        maxMembers = maxMembers,
                        hostOnlyControl = hostOnly,
                        // This frame is the only place a promotion is ever
                        // announced — the server hands the role to an arbitrary
                        // survivor when a host leaves and says so nowhere else.
                        // Left unread, [State.you] keeps saying "not the host"
                        // for the rest of the party, which hides the host's own
                        // controls from them and, with [hostOnlyControl] on,
                        // locks them out of a party they now own.
                        you = current.you
                            ?.let { mine -> members.firstOrNull { it.memberId == mine.memberId } }
                            ?: current.you,
                    )
                }
            }

            "activity" -> {
                val action = frame["action"]?.jsonPrimitive?.content ?: return
                val by = frame["by"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return
                val atMs = frame["atMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: received
                val detail = frame["detail"]?.jsonPrimitive?.content.orEmpty()
                recordActivity(PartyActivity(action, by, atMs, detail))
            }

            "error" -> {
                val reason = frame["error"]?.jsonPrimitive?.content
                val message = frame["message"]?.jsonPrimitive?.content
                Log.w(TAG, "party server refused a frame: $reason ${redact(message)}")
                _state.update { it.copy(error = message) }
                // These two are terminal, and the reconnect loop cannot learn
                // that on its own — it would keep dialling a party that no
                // longer knows this device for as long as the app is open. The
                // ordinary way to reach here is the server having restarted,
                // which on a free instance is every idle spin-down.
                if (reason == "bad_token" || reason == "no_such_party") {
                    scope.launch { leaveParty() }
                }
            }

            "bye" -> {
                // The server has let this membership go — the slot was swept, or
                // the party ended. Reconnecting would be answered with 4401
                // forever, so the loop is stopped rather than left spinning.
                //
                // A removal is the one kind worth remembering: the code goes on
                // this device's own shut-out list before the state carrying it
                // is torn down. See [recentKicks].
                if (frame["reason"]?.jsonPrimitive?.content == "kicked") {
                    _state.value.code?.let(::recordKick)
                }
                scope.launch { leaveParty() }
            }
        }
    }

    // ------------------------------------------------------------- plumbing --

    private data class Identity(
        val userId: String,
        val deviceId: String,
        val name: String,
        val avatar: String?,
    )

    /**
     * Who this device is jamming as, or null if it cannot.
     *
     * [AuthStore.isSignedIn][com.music.bitchord.auth.AuthStore.isSignedIn]
     * rather than "is there a cookie": a jar with no signable secret in it is a
     * session the app is already treating as signed out everywhere else, and a
     * party is not the place to start disagreeing with that.
     *
     * [userId] is a hash of the account and profile ids, not either of them. It
     * only has to be stable and comparable — the server never needs to know
     * which Google account it stands for, and a party server that accumulated
     * real account identifiers would be holding something it has no use for.
     */
    private fun identity(nickname: String = nickname()): Identity? {
        val store = BitChordApplication.authStore
        if (!store.isSignedIn) return null
        val account = store.activeSession ?: return null
        val profile = account.profiles.firstOrNull { it.profileId == account.activeProfileId }
            ?: account.profiles.firstOrNull()
        val name = nickname.trim().takeIf { it.isNotBlank() }
            ?: profile?.name?.takeIf { it.isNotBlank() }
            ?: account.name.takeIf { it.isNotBlank() }
            ?: account.email.substringBefore('@').takeIf { it.isNotBlank() }
            ?: return null
        return Identity(
            userId = sha256("${account.accountId}:${profile?.profileId.orEmpty()}").take(32),
            deviceId = deviceId(),
            name = name,
            avatar = profile?.avatar?.takeIf { it.startsWith("http") },
        )
    }

    // ------------------------------------------------------- recent kicks --

    /**
     * Parties this device was removed from, and when it may ask again.
     *
     * Held here rather than on the server, which forgets a party minutes after
     * it empties and would have to keep a list of who is not welcome where for
     * far longer than it keeps the party itself. A host who removes somebody
     * gets a door that stays shut for a day without the server carrying a
     * grudge; somebody determined to get back in can clear the app's data, and
     * that is an acceptable trade for a guard rail rather than a ban.
     *
     * Stored as code → the epoch millisecond it lapses, pruned on every read.
     */
    private fun recentKicks(): Map<String, Long> {
        val raw = prefs.getString(KEY_KICKED, null) ?: return emptyMap()
        val stored = runCatching {
            json.decodeFromString(MapSerializer(String.serializer(), Long.serializer()), raw)
        }.getOrNull() ?: return emptyMap()
        val now = System.currentTimeMillis()
        val live = stored.filterValues { it > now }
        if (live.size != stored.size) writeKicks(live)
        return live
    }

    private fun writeKicks(entries: Map<String, Long>) {
        if (entries.isEmpty()) {
            prefs.edit().remove(KEY_KICKED).apply()
            return
        }
        val encoded = json.encodeToString(
            MapSerializer(String.serializer(), Long.serializer()),
            entries,
        )
        prefs.edit().putString(KEY_KICKED, encoded).apply()
    }

    /** @see recentKicks */
    private fun recordKick(code: String) {
        val normalised = code.filter { it.isLetterOrDigit() }.uppercase()
        if (normalised.isEmpty()) return
        writeKicks(recentKicks() + (normalised to System.currentTimeMillis() + KICK_BLOCK_MS))
    }

    /**
     * Refuses a party this device was recently removed from.
     *
     * The refusal says it was removed and not for how long: a countdown is an
     * invitation to wait it out, and the listener's business is with the host
     * rather than with a timer.
     */
    private fun refuseIfRecentlyKicked(code: String) {
        if (recentKicks().containsKey(code)) {
            throw PartyException(
                "recently_kicked",
                "Couldn’t let you in — you’ve recently been kicked out of this party.",
            )
        }
    }

    /**
     * The picture the rest of the party will see against this device's name.
     *
     * Read off [identity] rather than the account directly, so the settings
     * screen shows what will actually be sent rather than a second guess at it
     * — including answering null in the cases a party cannot be joined at all.
     */
    fun myAvatarUrl(): String? = identity()?.avatar

    /**
     * This install's identity to the party, independent of who is signed in.
     *
     * The server counts devices, not people, and uses this to tell a rejoin
     * from a sixth device — so it has to survive a sign-out, an account switch
     * and a process death, and must not survive an uninstall.
     */
    private fun deviceId(): String {
        prefs.getString(KEY_DEVICE, null)?.let { return it }
        return UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE, it).apply()
        }
    }

    private fun recordActivity(entry: PartyActivity) {
        val next = (listOf(entry) + _activity.value).take(100)
        _activity.value = next
    }

    /**
     * Who is in a party, before committing a slot to it.
     *
     * Unauthenticated on both sides — see the server's `handlePreviewParty` for
     * why that is safe — so this works from the code somebody read out as
     * readily as from a tapped link. A party this device was recently removed
     * from is refused here too, so the refusal arrives while the code is still
     * on screen rather than after a confirmation the listener cannot act on.
     */
    suspend fun previewParty(code: String, server: String? = null): Result<PartyPreview> {
        val cleaned = code.filter { it.isLetterOrDigit() }.uppercase()
        if (cleaned.length != CODE_LENGTH) {
            return Result.failure(PartyException("bad_code", "A party code is six letters or digits."))
        }
        return runCatching {
            refuseIfRecentlyKicked(cleaned)
            val base = resolveHttpBase(server ?: effectiveIdleServerBase())
            if (base.isBlank()) throw PartyException("no_server", "Set the party server address first.")
            val response = http.get("$base/api/parties/$cleaned/preview")
            if (!response.status.isSuccess()) {
                val problem = response.toPartyException()
                // A server that predates this endpoint is not the same answer
                // as a code that does not exist, and the two arrive as the same
                // status. What tells them apart is the body: the handler's own
                // 404 is JSON with a code in it, while a route the router has
                // never heard of is answered by the router, in plain text, so
                // nothing parses and the code falls back to the status.
                //
                // Worth the paragraph because the invite in somebody's hand may
                // point at any server at all — an older deploy, or a copy they
                // run themselves — and a party that cannot be *looked at* is a
                // party that cannot be joined. Without a face to show, the
                // confirmation is still a confirmation.
                if (problem.statusCode == 404 && problem.code == "http_404") {
                    return@runCatching PartyPreview(code = cleaned)
                }
                throw problem
            }
            response.body<PartyPreview>()
        }.onFailure {
            Log.w(TAG, "could not look up a party: ${redact(it.message)}")
        }
    }

    private suspend fun post(url: String, body: JoinRequest): PartyMembership {
        val response = http.post(url) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (!response.status.isSuccess()) throw response.toPartyException()
        return response.body()
    }

    private suspend fun HttpResponse.toPartyException(): PartyException {
        val body = runCatching { bodyAsText() }.getOrDefault("")
        val parsed = runCatching { json.decodeFromString(ApiError.serializer(), body) }.getOrNull()
        return PartyException(
            code = parsed?.code.orEmpty().ifBlank { "http_${status.value}" },
            message = parsed?.message?.takeIf { it.isNotBlank() }
                // 422 is the server rejecting an identity, which here can only
                // mean the account layer handed over something blank.
                ?: if (status.value == 422) "This account can't be used to jam."
                else "The party server said ${status.value}.",
            statusCode = status.value,
        )
    }

    fun normalizeServerBase(value: String): String =
        value.trim().trimEnd('/')

    /**
     * Resolves the given custom server (or default server if blank) to a fully qualified HTTP base URL.
     */
    private fun resolveHttpBase(server: String): String {
        val raw = normalizeServerBase(server).ifBlank { defaultServer }
        if (raw.isBlank()) return ""
        return if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
    }

    /**
     * A message with every server address taken out of it.
     *
     * Failures from the HTTP and WebSocket layers name the host they were
     * talking to — that is what they are for — and those messages end up in two
     * places that must not carry it: logcat, and the error line on the Listen
     * Together screen. So nothing from below this class reaches either without
     * passing through here.
     */
    private fun redact(text: String?): String {
        var out = text.orEmpty()
        if (out.isEmpty()) return out
        listOfNotNull(defaultServer, _customServer.value, activePartyServerBase)
            .filter { it.isNotBlank() }
            .flatMap { listOf(it, it.substringAfter("://")) }
            .sortedByDescending(String::length)
            .forEach { out = out.replace(it, SERVER_PLACEHOLDER, ignoreCase = true) }
        return out.replace(ABSOLUTE_URL, SERVER_PLACEHOLDER)
    }

    /**
     * What the listener is told when the transport fails.
     */
    private fun Throwable.displayMessage(): String = when (this) {
        is PartyException -> message ?: UNREACHABLE
        else -> UNREACHABLE
    }

    private fun wsBase(): String {
        val base = activePartyServerBase ?: effectiveIdleServerBase()
        return resolveHttpBase(base)
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    const val CODE_LENGTH = 6

    private const val TAG = "ListenTogether"
    private const val PREFS = "bitchord_listen_together"
    private const val KEY_SERVER = "server_url"

    /** What a redacted address reads as. Not a hostname, so it cannot be resolved back. */
    private const val SERVER_PLACEHOLDER = "<party server>"

    private const val UNREACHABLE = "Couldn’t reach the party server."

    private val ABSOLUTE_URL = Regex("""(?:https?|wss?)://[^\s,;)\]}'\"]+""", RegexOption.IGNORE_CASE)
    private const val KEY_CODE = "party_code"
    private const val KEY_TOKEN = "party_token"
    private const val KEY_DEVICE = "device_id"
    private const val KEY_NICKNAME = "party_nickname"
    private const val KEY_KICKED = "party_kicked_until"

    /** How long a removal keeps this device out of that party. */
    private const val KICK_BLOCK_MS = 24L * 60 * 60 * 1000

    private const val PING_INTERVAL_MS = 15_000L
    private const val REPORT_INTERVAL_MS = 10_000L
    private const val HEALTH_TIMEOUT_MS = 45_000L
}
