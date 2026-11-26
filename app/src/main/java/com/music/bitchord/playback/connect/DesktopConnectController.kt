package com.music.bitchord.playback.connect

import android.content.Context
import android.content.SharedPreferences
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.music.bitchord.data.connect.ConnectApiError
import com.music.bitchord.data.connect.ConnectHostInfo
import com.music.bitchord.data.connect.ConnectPairLink
import com.music.bitchord.data.connect.ConnectPairRequest
import com.music.bitchord.data.connect.ConnectPairResponse
import com.music.bitchord.data.connect.ConnectProtocol
import com.music.bitchord.playback.cast.CastController
import com.music.bitchord.wear.WearBridge
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

@Serializable
data class SavedDesktopDevice(
    val deviceId: String,
    val name: String,
    val host: String,
    val port: Int = ConnectProtocol.DEFAULT_PORT,
    val token: String,
    val lastConnectedMs: Long = 0L,
)

/**
 * Discovery, pairing, and connection state for BitChord PC LAN Connect.
 *
 * Mirrors [CastController]'s role for desktop receivers on the same Wi-Fi (discovered via
 * Android's [NsdManager]) or reached manually by IP/Tailscale.
 */
object DesktopConnectController {
    private const val TAG = "BitChordConnect"
    private const val PREFS_NAME = "bitchord_desktop_connect"
    private const val KEY_CLIENT_ID = "client_id"
    private const val KEY_SAVED_DEVICES = "saved_devices_v1"

    data class Device(
        val id: String,
        val name: String,
        val host: String,
        val port: Int,
        val paired: Boolean,
        val online: Boolean,
        val connecting: Boolean,
        val connected: Boolean,
    )

    data class State(
        val devices: List<Device> = emptyList(),
        val connectedDeviceId: String? = null,
        val connectedName: String? = null,
        val connectingDeviceId: String? = null,
        val discovering: Boolean = false,
        val pairing: Boolean = false,
        val error: String? = null,
    ) {
        val connected: Boolean get() = connectedDeviceId != null
        val connecting: Boolean get() = connectingDeviceId != null
    }

    private data class DiscoveredDesktop(
        val id: String,
        val name: String,
        val host: String,
        val port: Int,
        val serviceName: String,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            engine {
                config {
                    connectTimeout(5, TimeUnit.SECONDS)
                    readTimeout(8, TimeUnit.SECONDS)
                    writeTimeout(8, TimeUnit.SECONDS)
                }
            }
        }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** The connected PC's volume, 0..1. */
    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val lock = Any()
    private val savedDevices = LinkedHashMap<String, SavedDesktopDevice>()
    private val discoveredDevices = LinkedHashMap<String, DiscoveredDesktop>()

    @Volatile private var appContext: Context? = null
    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var playback: DesktopConnectPlayback? = null
    @Volatile private var started = false

    fun ensureStarted(context: Context) {
        if (started) return
        synchronized(lock) {
            if (started) return
            val app = context.applicationContext
            appContext = app
            prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadSavedDevicesLocked()
            started = true
        }
        publishDevices()
    }

    fun attach(target: DesktopConnectPlayback) {
        playback = target
    }

    fun detach(target: DesktopConnectPlayback) {
        if (playback === target) playback = null
    }

    /**
     * Starts live mDNS discovery for `_bitchord-connect._tcp` while the picker dialog is open.
     * Returns a stop function to release the NSD listener and Wi-Fi multicast lock.
     */
    fun discover(): () -> Unit {
        val app = appContext ?: return {}
        val nsd = app.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return {}
        val wifi = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = runCatching {
            wifi?.createMulticastLock("bitchord-connect-mdns")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()

        val resolveQueue = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val resolverJob = scope.launch {
            for (info in resolveQueue) {
                val resolved = resolveServiceCompat(nsd, info) ?: continue
                onServiceResolved(resolved)
            }
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "mDNS start discovery failed: $errorCode")
                _state.update { it.copy(discovering = false) }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "mDNS stop discovery failed: $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String) {
                _state.update { it.copy(discovering = true) }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                _state.update { it.copy(discovering = false) }
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.contains(ConnectProtocol.MDNS_SERVICE_TYPE)) {
                    resolveQueue.trySend(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                synchronized(lock) {
                    val toRemove = discoveredDevices.entries
                        .filter { it.value.serviceName == serviceInfo.serviceName }
                        .map { it.key }
                    toRemove.forEach { discoveredDevices.remove(it) }
                }
                publishDevices()
            }
        }

        runCatching {
            nsd.discoverServices(ConnectProtocol.MDNS_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            Log.w(TAG, "Unable to start mDNS discovery", it)
            _state.update { s -> s.copy(discovering = false) }
        }

        publishDevices()
        return {
            runCatching { nsd.stopServiceDiscovery(listener) }
            resolveQueue.close()
            resolverJob.cancel()
            runCatching { if (multicastLock?.isHeld == true) multicastLock.release() }
            _state.update { it.copy(discovering = false) }
        }
    }

    /** Connects to an already-paired PC using its saved bearer token. */
    fun connect(deviceId: String) {
        val saved = synchronized(lock) {
            val base = savedDevices[deviceId] ?: return
            val live = discoveredDevices[deviceId]
            if (live != null && (live.host != base.host || live.port != base.port || live.name != base.name)) {
                val updated = base.copy(host = live.host, port = live.port, name = live.name)
                savedDevices[deviceId] = updated
                persistSavedDevicesLocked()
                updated
            } else {
                base
            }
        }
        val target = playback
        if (target == null) {
            _state.update { it.copy(error = "Playback service is not ready yet. Try again in a moment.") }
            return
        }
        // One remote speaker at a time.
        if (CastController.state.value.casting) CastController.disconnect(resumeHere = false)
        if (WearBridge.watchOutput.value.active) WearBridge.leaveWatchOutput()

        _state.update { it.copy(connectingDeviceId = saved.deviceId, error = null) }
        publishDevices()
        target.connectTo(saved)
    }

    /**
     * Pairs with a PC at [host]:[port] using its 6-digit [pin], saves its bearer token, and
     * immediately connects.
     */
    fun pairAndConnect(host: String, port: Int, pin: String) {
        val cleanHost = host.trim()
        val cleanPin = pin.filter(Char::isDigit)
        if (cleanHost.isEmpty()) {
            _state.update { it.copy(error = "Enter the PC IP address or pairing link.") }
            return
        }
        if (port !in 1..65535) {
            _state.update { it.copy(error = "Invalid port number.") }
            return
        }
        if (cleanPin.length < 4) {
            _state.update { it.copy(error = "Enter the 6-digit PIN shown on BitChord PC.") }
            return
        }
        _state.update { it.copy(pairing = true, error = null) }
        scope.launch {
            val result = performPair(cleanHost, port, cleanPin)
            result.onSuccess { saved ->
                synchronized(lock) {
                    savedDevices[saved.deviceId] = saved
                    persistSavedDevicesLocked()
                }
                _state.update { it.copy(pairing = false, error = null) }
                publishDevices()
                withContext(Dispatchers.Main) {
                    connect(saved.deviceId)
                }
            }.onFailure { err ->
                _state.update { it.copy(pairing = false, error = err.message ?: "Pairing failed") }
            }
        }
    }

    /** Parses a `bitchord-connect://pair?...` URI or `host[:port]` string and pairs with it. */
    fun pairFromManualInput(hostOrLink: String, portText: String, pinText: String) {
        val parsed = ConnectPairLink.parse(hostOrLink)
        if (parsed == null) {
            _state.update { it.copy(error = "Enter a valid IP address (e.g. 192.168.1.50) or pairing link.") }
            return
        }
        val port = if (hostOrLink.trim().startsWith("${ConnectProtocol.LINK_SCHEME}://", ignoreCase = true) ||
            hostOrLink.contains(':')
        ) {
            parsed.port
        } else {
            portText.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: parsed.port
        }
        val pin = pinText.filter(Char::isDigit).ifBlank { parsed.pin.orEmpty() }
        pairAndConnect(parsed.host, port, pin)
    }

    fun disconnect(resumeHere: Boolean) {
        playback?.disconnect(resumeHere)
        _state.update {
            it.copy(
                connectedDeviceId = null,
                connectedName = null,
                connectingDeviceId = null,
                error = null,
            )
        }
        publishDevices()
    }

    fun forgetDevice(deviceId: String) {
        if (_state.value.connectedDeviceId == deviceId || _state.value.connectingDeviceId == deviceId) {
            playback?.disconnect(resumeHere = false)
        }
        synchronized(lock) {
            savedDevices.remove(deviceId)
            persistSavedDevicesLocked()
        }
        _state.update {
            it.copy(
                connectedDeviceId = it.connectedDeviceId?.takeUnless { id -> id == deviceId },
                connectedName = if (it.connectedDeviceId == deviceId) null else it.connectedName,
                connectingDeviceId = it.connectingDeviceId?.takeUnless { id -> id == deviceId },
            )
        }
        publishDevices()
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun setVolume(level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        _volume.value = clamped
        playback?.setVolume(clamped)
    }

    internal fun onConnected(device: SavedDesktopDevice, liveName: String?) {
        val resolvedName = liveName?.takeIf { it.isNotBlank() } ?: device.name
        synchronized(lock) {
            savedDevices[device.deviceId] = device.copy(
                name = resolvedName,
                lastConnectedMs = System.currentTimeMillis(),
            )
            persistSavedDevicesLocked()
        }
        _state.update {
            it.copy(
                connectedDeviceId = device.deviceId,
                connectedName = resolvedName,
                connectingDeviceId = null,
                error = null,
            )
        }
        publishDevices()
    }

    internal fun onDisconnected(errorMessage: String? = null, unauthorizedDeviceId: String? = null) {
        if (unauthorizedDeviceId != null) {
            synchronized(lock) {
                savedDevices.remove(unauthorizedDeviceId)
                persistSavedDevicesLocked()
            }
        }
        _state.update {
            it.copy(
                connectedDeviceId = null,
                connectedName = null,
                connectingDeviceId = null,
                error = errorMessage,
            )
        }
        publishDevices()
    }

    internal fun onRemoteVolume(level: Float) {
        _volume.value = level.coerceIn(0f, 1f)
    }

    private suspend fun performPair(host: String, port: Int, pin: String): Result<SavedDesktopDevice> = runCatching {
        val clientId = getOrCreateClientId()
        val clientName = deviceDisplayName()
        val requestBody = ConnectPairRequest(
            pin = pin,
            clientId = clientId,
            clientName = clientName,
            platform = "android",
        )
        val response = httpClient.post("http://$host:$port/connect/pair") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(requestBody))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val apiErr = runCatching { json.decodeFromString<ConnectApiError>(text) }.getOrNull()
            throw IllegalStateException(
                apiErr?.message?.takeIf { it.isNotBlank() }
                    ?: "Pairing rejected (HTTP ${response.status.value})",
            )
        }
        val paired = json.decodeFromString<ConnectPairResponse>(text)
        SavedDesktopDevice(
            deviceId = paired.deviceId,
            name = paired.deviceName.ifBlank { host },
            host = host,
            port = port,
            token = paired.token,
            lastConnectedMs = System.currentTimeMillis(),
        )
    }

    private suspend fun onServiceResolved(info: NsdServiceInfo) {
        @Suppress("DEPRECATION")
        val host = info.host?.hostAddress ?: return
        val port = info.port.takeIf { it in 1..65535 } ?: return
        val attrs = runCatching { info.attributes }.getOrDefault(emptyMap())
        var id = attrs["id"]?.decodeToString()?.trim().orEmpty()
        var name = attrs["name"]?.decodeToString()?.trim().orEmpty()

        // Fallback to HTTP /connect/info if TXT records were stripped by the router.
        if (id.isEmpty() || name.isEmpty()) {
            val hostInfo = runCatching {
                val res = httpClient.get("http://$host:$port/connect/info")
                if (res.status.isSuccess()) json.decodeFromString<ConnectHostInfo>(res.bodyAsText()) else null
            }.getOrNull()
            if (hostInfo != null) {
                id = hostInfo.deviceId
                name = hostInfo.deviceName
            }
        }
        if (id.isEmpty()) id = "$host:$port"
        if (name.isEmpty()) name = info.serviceName

        synchronized(lock) {
            discoveredDevices[id] = DiscoveredDesktop(
                id = id,
                name = name,
                host = host,
                port = port,
                serviceName = info.serviceName,
            )
            // Keep saved host/port fresh when the PC gets a new DHCP IP on the same Wi-Fi.
            savedDevices[id]?.let { existing ->
                if (existing.host != host || existing.port != port || existing.name != name) {
                    savedDevices[id] = existing.copy(host = host, port = port, name = name)
                    persistSavedDevicesLocked()
                }
            }
        }
        publishDevices()
    }

    @Suppress("DEPRECATION")
    private suspend fun resolveServiceCompat(nsd: NsdManager, info: NsdServiceInfo): NsdServiceInfo? =
        withTimeoutOrNull(4_000L) {
            suspendCancellableCoroutine { cont ->
                try {
                    nsd.resolveService(
                        info,
                        object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                if (cont.isActive) cont.resume(null)
                            }

                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                if (cont.isActive) cont.resume(serviceInfo)
                            }
                        },
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "resolveService failed", e)
                    if (cont.isActive) cont.resume(null)
                }
            }
        }

    private fun publishDevices() {
        val currentState = _state.value
        val merged = synchronized(lock) {
            val byId = LinkedHashMap<String, Device>()
            for (d in discoveredDevices.values) {
                val saved = savedDevices[d.id]
                byId[d.id] = Device(
                    id = d.id,
                    name = d.name,
                    host = d.host,
                    port = d.port,
                    paired = saved != null,
                    online = true,
                    connecting = currentState.connectingDeviceId == d.id,
                    connected = currentState.connectedDeviceId == d.id,
                )
            }
            for (s in savedDevices.values) {
                if (s.deviceId !in byId) {
                    byId[s.deviceId] = Device(
                        id = s.deviceId,
                        name = s.name,
                        host = s.host,
                        port = s.port,
                        paired = true,
                        online = currentState.connectedDeviceId == s.deviceId,
                        connecting = currentState.connectingDeviceId == s.deviceId,
                        connected = currentState.connectedDeviceId == s.deviceId,
                    )
                }
            }
            byId.values.sortedWith(
                compareByDescending<Device> { it.connected }
                    .thenByDescending { it.online }
                    .thenByDescending { it.paired }
                    .thenBy { it.name.lowercase() },
            )
        }
        _state.update { it.copy(devices = merged) }
    }

    private fun getOrCreateClientId(): String {
        val store = prefs ?: return UUID.randomUUID().toString()
        val existing = store.getString(KEY_CLIENT_ID, null)?.trim()
        if (!existing.isNullOrEmpty()) return existing
        val created = UUID.randomUUID().toString()
        store.edit().putString(KEY_CLIENT_ID, created).apply()
        return created
    }

    private fun deviceDisplayName(): String {
        val model = Build.MODEL?.trim().orEmpty()
        val brand = Build.MANUFACTURER?.trim().orEmpty()
        return when {
            model.isBlank() -> "Android Phone"
            brand.isNotBlank() && !model.startsWith(brand, ignoreCase = true) ->
                "${brand.replaceFirstChar(Char::uppercase)} $model"
            else -> model
        }.take(60)
    }

    private fun loadSavedDevicesLocked() {
        val raw = prefs?.getString(KEY_SAVED_DEVICES, null)?.trim().orEmpty()
        if (raw.isEmpty()) return
        val list = runCatching { json.decodeFromString<List<SavedDesktopDevice>>(raw) }.getOrDefault(emptyList())
        savedDevices.clear()
        list.forEach { savedDevices[it.deviceId] = it }
    }

    private fun persistSavedDevicesLocked() {
        val store = prefs ?: return
        runCatching {
            store.edit().putString(KEY_SAVED_DEVICES, json.encodeToString(savedDevices.values.toList())).apply()
        }
    }
}
