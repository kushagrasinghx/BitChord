package com.music.bitchord.playback.connect

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL

class BitChordConnectDiscovery(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "BitChordDiscovery"
        private const val SERVICE_TYPE = "_bitchord-connect._tcp."
    }

    private val _discoveredDevices = MutableStateFlow<List<ConnectDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<ConnectDevice>> = _discoveredDevices.asStateFlow()

    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var isDiscovering = false
    private var scanJob: Job? = null

    fun startDiscovery() {
        if (isDiscovering) return
        isDiscovering = true

        startNsdDiscovery()
        scanSubnet()
    }

    fun stopDiscovery() {
        isDiscovering = false
        scanJob?.cancel()
        scanJob = null
        stopNsdDiscovery()
    }

    fun refresh() {
        scanSubnet()
    }

    private fun startNsdDiscovery() {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsdManager = nsd

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "NSD Discovery started for $regType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
                resolveService(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                _discoveredDevices.update { current ->
                    current.filterNot { it.name == serviceInfo.serviceName }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "NSD Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Start discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Stop discovery failed: $errorCode")
            }
        }

        runCatching {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }.onFailure { Log.w(TAG, "Failed to start NSD discovery: ${it.message}") }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val nsd = nsdManager ?: return
        runCatching {
            nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.d(TAG, "Resolve failed: $errorCode")
                }

                override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                    val host = resolvedInfo.host?.hostAddress ?: return
                    val port = resolvedInfo.port

                    // Ignore self
                    if (isLocalAddress(host) || host == "127.0.0.1") return

                    scope.launch(Dispatchers.IO) {
                        probeDevice(host, port)
                    }
                }
            })
        }
    }

    private fun stopNsdDiscovery() {
        discoveryListener?.let { listener ->
            runCatching { nsdManager?.stopServiceDiscovery(listener) }
        }
        discoveryListener = null
        nsdManager = null
    }

    /**
     * Fast subnet sweep to find BitChord Connect devices even if mDNS multicast is blocked by the router.
     */
    fun scanSubnet() {
        scanJob?.cancel()
        scanJob = scope.launch(Dispatchers.IO) {
            val localIps = getLocalIpAddresses()
            if (localIps.isEmpty()) return@launch

            for (localIp in localIps) {
                val subnetPrefix = localIp.substringBeforeLast(".") + "."

                // Parallel sweep on port 8765
                val deferreds = (1..254).map { hostNum ->
                    val targetIp = "$subnetPrefix$hostNum"
                    if (targetIp == localIp) return@map null

                    async(Dispatchers.IO) {
                        if (isPortOpen(targetIp, BitChordConnectServer.CONNECT_PORT, timeoutMs = 250)) {
                            probeDevice(targetIp, BitChordConnectServer.CONNECT_PORT)
                        }
                    }
                }.filterNotNull()

                deferreds.awaitAll()
            }
        }
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun probeDevice(ip: String, port: Int) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("http://$ip:$port/connect/info")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 800
                    readTimeout = 800
                    requestMethod = "GET"
                }

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val devJson = json.optJSONObject("device")
                    if (devJson != null) {
                        val device = ConnectDevice.fromJson(devJson).copy(host = ip, port = port)
                        Log.i(TAG, "Discovered BitChord Connect device: ${device.name} at ${device.host}:${device.port}")
                        addOrUpdateDevice(device)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Probe device $ip:$port failed: ${e.message}")
            }
        }
    }

    private fun addOrUpdateDevice(device: ConnectDevice) {
        if (isLocalAddress(device.host)) return

        _discoveredDevices.update { current ->
            val filtered = current.filterNot { it.id == device.id || it.host == device.host }
            (filtered + device).sortedBy { it.name }
        }
    }

    private fun isLocalAddress(host: String): Boolean {
        if (host == "127.0.0.1" || host == "localhost") return true
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return false
            for (intf in interfaces) {
                val addrs = intf.inetAddresses ?: continue
                for (addr in addrs) {
                    if (addr.hostAddress == host) return true
                }
            }
            false
        }.getOrDefault(false)
    }

    private fun getLocalIpAddresses(): List<String> {
        val list = mutableListOf<String>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return emptyList()
            val sorted = interfaces.sortedByDescending { intf ->
                val name = intf.name.lowercase()
                when {
                    name.startsWith("wlan") -> 3
                    name.startsWith("eth") -> 2
                    name.startsWith("en") -> 2
                    name.startsWith("rmnet") || name.startsWith("dummy") -> 0
                    else -> 1
                }
            }
            for (intf in sorted) {
                if (intf.isLoopback || !intf.isUp) continue
                val name = intf.name.lowercase()
                if (name.startsWith("rmnet") || name.startsWith("dummy")) continue
                val addrs = intf.inetAddresses ?: continue
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        list.add(addr.hostAddress)
                    }
                }
            }
        }
        return list
    }

    fun getLocalIpAddress(): String? = getLocalIpAddresses().firstOrNull()
}
