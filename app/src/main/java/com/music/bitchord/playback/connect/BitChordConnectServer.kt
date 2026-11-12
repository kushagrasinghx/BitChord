package com.music.bitchord.playback.connect

import android.content.Context
import android.media.AudioManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Player
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.toSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt

/**
 * Announcement banner state for TV / remote notification when a phone connects or controls playback.
 */
object BitChordConnectBanner {
    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private var autoDismissJob: Job? = null

    fun show(scope: CoroutineScope, clientName: String, action: String = "connected") {
        autoDismissJob?.cancel()
        _bannerMessage.value = "Connected to $clientName"
        autoDismissJob = scope.launch {
            delay(3500)
            _bannerMessage.value = null
        }
    }

    fun clear() {
        _bannerMessage.value = null
    }
}

/**
 * Embedded HTTP REST & Server-Sent Events (SSE) server for BitChord Connect.
 * Runs on the TV (and any target device), listening on port 8765.
 */
class BitChordConnectServer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val getPlayer: () -> Player?,
    private val onPlaySongsRequested: (songs: List<Song>, startIndex: Int, positionMs: Long) -> Unit,
) {
    companion object {
        private const val TAG = "BitChordConnectServer"
        const val CONNECT_PORT = 8765
        const val SERVICE_TYPE = "_bitchord-connect._tcp."
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private val sseClients = CopyOnWriteArrayList<PrintWriter>()
    private var eventBroadcastJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            triggerImmediateBroadcast()
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            triggerImmediateBroadcast()
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            triggerImmediateBroadcast()
        }
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            triggerImmediateBroadcast()
        }
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            triggerImmediateBroadcast()
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            triggerImmediateBroadcast()
        }
    }

    private fun triggerImmediateBroadcast() {
        if (!isRunning || sseClients.isEmpty()) return
        scope.launch(Dispatchers.Main) {
            val snapshot = getPlaybackSnapshot()
            broadcastPlaybackState(snapshot)
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        acquireMulticastLock()
        startHttpServer()
        registerMdnsService()
        startEventBroadcastTicker()
        scope.launch(Dispatchers.Main) {
            getPlayer()?.addListener(playerListener)
        }
    }

    fun stop() {
        isRunning = false
        scope.launch(Dispatchers.Main) {
            getPlayer()?.removeListener(playerListener)
        }
        eventBroadcastJob?.cancel()
        eventBroadcastJob = null

        sseClients.forEach { runCatching { it.close() } }
        sseClients.clear()

        runCatching { serverSocket?.close() }
        serverSocket = null

        unregisterMdnsService()
        releaseMulticastLock()
    }

    private fun acquireMulticastLock() {
        runCatching {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("bitchord_connect_multicast")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        }.onFailure { Log.w(TAG, "Failed to acquire multicast lock: ${it.message}") }
    }

    private fun releaseMulticastLock() {
        runCatching {
            multicastLock?.takeIf { it.isHeld }?.release()
            multicastLock = null
        }
    }

    private fun registerMdnsService() {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsdManager = nsd

        val serviceName = getDeviceFriendlyName()
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = SERVICE_TYPE
            this.port = CONNECT_PORT
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(registeredInfo: NsdServiceInfo) {
                Log.i(TAG, "mDNS Service registered: ${registeredInfo.serviceName} on port $CONNECT_PORT")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "mDNS registration failed: $errorCode")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.i(TAG, "mDNS Service unregistered")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "mDNS unregistration failed: $errorCode")
            }
        }

        runCatching {
            nsd.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        }.onFailure { Log.w(TAG, "Failed to register NSD service: ${it.message}") }
    }

    private fun unregisterMdnsService() {
        registrationListener?.let { listener ->
            runCatching { nsdManager?.unregisterService(listener) }
        }
        registrationListener = null
        nsdManager = null
    }

    private fun startHttpServer() {
        scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(CONNECT_PORT).apply {
                    reuseAddress = true
                }
                Log.i(TAG, "BitChord Connect Server listening on port $CONNECT_PORT")

                while (isActive && isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch(Dispatchers.IO) {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) Log.w(TAG, "Server socket error: ${e.message}")
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        withContext(Dispatchers.IO) {
            try {
                socket.soTimeout = 0 // Don't timeout SSE streams
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val outputStream = socket.getOutputStream()

                val requestLine = reader.readLine() ?: return@withContext
                val parts = requestLine.split(" ")
                if (parts.size < 2) return@withContext

                val method = parts[0]
                val path = parts[1]

                // Read headers
                var contentLength = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }

                when {
                    method == "GET" && path.startsWith("/connect/info") -> {
                        handleGetInfo(outputStream)
                        socket.close()
                    }
                    method == "GET" && path.startsWith("/connect/events") -> {
                        handleSseEvents(socket, outputStream)
                    }
                    method == "POST" && path.startsWith("/connect/command") -> {
                        val body = if (contentLength > 0) {
                            val chars = CharArray(contentLength)
                            var read = 0
                            while (read < contentLength) {
                                val count = reader.read(chars, read, contentLength - read)
                                if (count < 0) break
                                read += count
                            }
                            String(chars, 0, read)
                        } else ""
                        handlePostCommand(body, outputStream)
                        socket.close()
                    }
                    method == "OPTIONS" -> {
                        handleCors(outputStream)
                        socket.close()
                    }
                    else -> {
                        writeHttpResponse(outputStream, 404, "Not Found", "application/json", "{\"error\":\"not_found\"}")
                        socket.close()
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Client socket handled/closed: ${e.message}")
            }
        }
    }

    private suspend fun handleGetInfo(output: OutputStream) {
        val snapshot = withContext(Dispatchers.Main) { getPlaybackSnapshot() }
        val device = ConnectDevice(
            id = "bitchord_${Build.MODEL}_$CONNECT_PORT",
            name = getDeviceFriendlyName(),
            host = getLocalIpAddress() ?: "127.0.0.1",
            port = CONNECT_PORT,
            deviceType = if (isTv()) ConnectDeviceType.TV else ConnectDeviceType.PHONE,
            model = Build.MODEL,
            version = "1.8.1",
            isCurrent = true,
        )

        val json = JSONObject().apply {
            put("device", device.toJson())
            put("playback", snapshot.toJson())
        }

        writeHttpResponse(output, 200, "OK", "application/json", json.toString())
    }

    private fun handleSseEvents(socket: Socket, output: OutputStream) {
        val writer = PrintWriter(output, true)
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: text/event-stream\r\n")
        writer.print("Cache-Control: no-cache\r\n")
        writer.print("Connection: keep-alive\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n\r\n")
        writer.flush()

        sseClients.add(writer)

        // Send initial state immediately
        scope.launch(Dispatchers.Main) {
            val snapshot = getPlaybackSnapshot()
            sendSseEvent(writer, snapshot)
        }

        // Keep connection open; if writer encounters error, remove it
        socket.keepAlive = true
    }

    private fun sendSseEvent(writer: PrintWriter, snapshot: ConnectPlaybackSnapshot) {
        try {
            val data = snapshot.toJson().toString()
            writer.print("data: $data\n\n")
            writer.flush()
        } catch (e: Exception) {
            sseClients.remove(writer)
        }
    }

    private fun broadcastPlaybackState(snapshot: ConnectPlaybackSnapshot) {
        val deadClients = mutableListOf<PrintWriter>()
        for (client in sseClients) {
            try {
                val data = snapshot.toJson().toString()
                client.print("data: $data\n\n")
                client.flush()
                if (client.checkError()) {
                    deadClients.add(client)
                }
            } catch (e: Exception) {
                deadClients.add(client)
            }
        }
        if (deadClients.isNotEmpty()) {
            sseClients.removeAll(deadClients)
        }
    }

    private fun startEventBroadcastTicker() {
        eventBroadcastJob = scope.launch(Dispatchers.Default) {
            while (isActive && isRunning) {
                delay(500)
                if (sseClients.isNotEmpty()) {
                    val snapshot = withContext(Dispatchers.Main) { getPlaybackSnapshot() }
                    broadcastPlaybackState(snapshot)
                }
            }
        }
    }

    private suspend fun handlePostCommand(body: String, output: OutputStream) {
        if (body.isBlank()) {
            writeHttpResponse(output, 400, "Bad Request", "application/json", "{\"error\":\"empty_body\"}")
            return
        }

        val json = runCatching { JSONObject(body) }.getOrNull()
        if (json == null) {
            writeHttpResponse(output, 400, "Bad Request", "application/json", "{\"error\":\"invalid_json\"}")
            return
        }

        val command = ConnectCommand.fromJson(json)

        withContext(Dispatchers.Main) {
            executeCommand(command)
            triggerImmediateBroadcast()
        }

        writeHttpResponse(output, 200, "OK", "application/json", "{\"status\":\"ok\"}")
    }

    private fun executeCommand(command: ConnectCommand) {
        val player = getPlayer() ?: return

        // Notify UI on TV of client connection
        command.clientName?.let { clientName ->
            BitChordConnectBanner.show(scope, clientName)
        }

        when (command.action) {
            ConnectCommand.ACTION_PLAY, ConnectCommand.ACTION_RESUME -> player.play()
            ConnectCommand.ACTION_PAUSE -> player.pause()
            ConnectCommand.ACTION_TOGGLE_PLAY_PAUSE -> {
                if (player.isPlaying) player.pause() else player.play()
            }
            ConnectCommand.ACTION_SEEK -> {
                command.positionMs?.let { player.seekTo(it) }
            }
            ConnectCommand.ACTION_SKIP_NEXT -> player.seekToNextMediaItem()
            ConnectCommand.ACTION_SKIP_PREVIOUS -> player.seekToPreviousMediaItem()
            ConnectCommand.ACTION_SET_VOLUME -> {
                command.volume?.let { fraction ->
                    player.volume = fraction.coerceIn(0f, 1f)
                    setSystemStreamVolume(fraction)
                }
            }
            ConnectCommand.ACTION_PLAY_SONGS -> {
                val songs = command.songs ?: command.song?.let { listOf(it) } ?: emptyList()
                if (songs.isNotEmpty()) {
                    onPlaySongsRequested(
                        songs,
                        command.startIndex ?: 0,
                        command.positionMs ?: 0L,
                    )
                }
            }
            ConnectCommand.ACTION_SET_REPEAT -> {
                command.repeatMode?.let { player.repeatMode = it }
            }
            ConnectCommand.ACTION_TOGGLE_SHUFFLE -> {
                player.shuffleModeEnabled = !player.shuffleModeEnabled
            }
        }
    }

    private fun setSystemStreamVolume(fraction: Float) {
        runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val target = (fraction * max).roundToInt().coerceIn(0, max)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
    }

    private fun getPlaybackSnapshot(): ConnectPlaybackSnapshot {
        val player = getPlayer() ?: return ConnectPlaybackSnapshot()
        val currentItem = player.currentMediaItem
        val currentSong = currentItem?.toSong()

        val count = player.mediaItemCount
        val curIndex = player.currentMediaItemIndex
        val timeline = player.currentTimeline
        val shuffle = player.shuffleModeEnabled
        val queue = mutableListOf<Song>()
        var queueIndex = 0

        if (count > 0 && curIndex in 0 until count) {
            if (shuffle && !timeline.isEmpty) {
                // Collect history (previous in shuffle order) and upcoming (next in shuffle order)
                val orderedIndices = mutableListOf<Int>()
                var prevIdx = curIndex
                val prevList = mutableListOf<Int>()
                while (prevList.size < 15) {
                    prevIdx = timeline.getPreviousWindowIndex(prevIdx, player.repeatMode, true)
                    if (prevIdx == C.INDEX_UNSET || prevIdx in prevList || prevIdx == curIndex) break
                    prevList.add(0, prevIdx)
                }
                orderedIndices.addAll(prevList)
                queueIndex = orderedIndices.size
                orderedIndices.add(curIndex)

                var nextIdx = curIndex
                while (orderedIndices.size < 50) {
                    nextIdx = timeline.getNextWindowIndex(nextIdx, player.repeatMode, true)
                    if (nextIdx == C.INDEX_UNSET || nextIdx in orderedIndices) break
                    orderedIndices.add(nextIdx)
                }

                for (idx in orderedIndices) {
                    if (idx in 0 until count) {
                        queue += player.getMediaItemAt(idx).toSong()
                    }
                }
            } else {
                // Standard linear order: window centered around curIndex
                val start = (curIndex - 15).coerceAtLeast(0)
                val end = (start + 50).coerceAtMost(count)
                for (i in start until end) {
                    queue += player.getMediaItemAt(i).toSong()
                }
                queueIndex = (curIndex - start).coerceIn(0, (queue.size - 1).coerceAtLeast(0))
            }
        } else if (currentSong != null) {
            queue.add(currentSong)
            queueIndex = 0
        }

        val duration = if (player.duration != C.TIME_UNSET) player.duration else 0L
        val position = player.currentPosition.coerceAtLeast(0L)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
        val curVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 15
        val volumeFraction = (curVol.toFloat() / maxVol).coerceIn(0f, 1f)

        return ConnectPlaybackSnapshot(
            isPlaying = player.isPlaying,
            playWhenReady = player.playWhenReady,
            playbackState = player.playbackState,
            positionMs = position,
            durationMs = duration,
            volume = volumeFraction,
            repeatMode = player.repeatMode,
            shuffle = player.shuffleModeEnabled,
            song = currentSong,
            queue = queue,
            queueIndex = queueIndex,
            sourceQuality = currentSong?.sourceQuality ?: "Lossless FLAC",
            isHiRes = currentSong?.sourceHiRes == true || (currentSong?.sourceQuality?.contains("24-bit", true) == true),
            timestamp = System.currentTimeMillis(),
        )
    }

    private fun handleCors(output: OutputStream) {
        val response = "HTTP/1.1 204 No Content\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
            "Access-Control-Allow-Headers: Content-Type\r\n\r\n"
        output.write(response.toByteArray(Charsets.UTF_8))
        output.flush()
    }

    private fun writeHttpResponse(
        output: OutputStream,
        code: Int,
        statusText: String,
        contentType: String,
        body: String,
    ) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val response = "HTTP/1.1 $code $statusText\r\n" +
            "Content-Type: $contentType; charset=utf-8\r\n" +
            "Content-Length: ${bodyBytes.size}\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Connection: close\r\n\r\n"
        output.write(response.toByteArray(Charsets.UTF_8))
        output.write(bodyBytes)
        output.flush()
    }

    private fun getDeviceFriendlyName(): String {
        val customName = runCatching {
            Settings.Global.getString(context.contentResolver, "device_name")
        }.getOrNull()?.takeIf { it.isNotBlank() }

        val btName = runCatching {
            Settings.Secure.getString(context.contentResolver, "bluetooth_name")
        }.getOrNull()?.takeIf { it.isNotBlank() }

        if (customName != null) return customName
        if (btName != null) return btName

        return when {
            Build.MODEL.contains("AFT", ignoreCase = true) -> "Fire TV (${Build.MODEL})"
            Build.MODEL.contains("Smart_TV", ignoreCase = true) -> "Google TV (${Build.MANUFACTURER})"
            isTv() -> "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} TV"
            else -> "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        }
    }

    private fun isTv(): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        return uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature("android.software.leanback") ||
            Build.MODEL.contains("AFT", ignoreCase = true) ||
            Build.MODEL.contains("Smart_TV", ignoreCase = true)
    }

    private fun getLocalIpAddress(): String? {
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null
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
                        return addr.hostAddress
                    }
                }
            }
        }
        return null
    }
}
