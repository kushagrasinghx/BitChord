package com.music.bitchord.playback.connect

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
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
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class BitChordConnectClient(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onStateUpdated: (ConnectPlaybackSnapshot) -> Unit,
) {
    companion object {
        private const val TAG = "BitChordConnectClient"
    }

    private val _remoteState = MutableStateFlow<ConnectPlaybackSnapshot?>(null)
    val remoteState: StateFlow<ConnectPlaybackSnapshot?> = _remoteState.asStateFlow()

    private var activeDevice: ConnectDevice? = null
    private var sseJob: Job? = null
    private var isConnected = false

    fun connect(device: ConnectDevice) {
        if (activeDevice?.id == device.id && isConnected) return
        disconnect()

        activeDevice = device
        isConnected = true
        startSseStream(device)
    }

    fun disconnect() {
        isConnected = false
        activeDevice = null
        sseJob?.cancel()
        sseJob = null
        _remoteState.value = null
    }

    private fun startSseStream(device: ConnectDevice) {
        sseJob?.cancel()
        sseJob = scope.launch(Dispatchers.IO) {
            // Fetch initial snapshot immediately so UI updates without waiting for first SSE event
            try {
                val infoUrl = URL("http://${device.host}:${device.port}/connect/info")
                val infoConn = (infoUrl.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 2000
                    readTimeout = 2000
                    requestMethod = "GET"
                }
                if (infoConn.responseCode == 200) {
                    val body = infoConn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val playbackObj = json.optJSONObject("playback")
                    if (playbackObj != null) {
                        val snapshot = ConnectPlaybackSnapshot.fromJson(playbackObj)
                        _remoteState.value = snapshot
                        withContext(Dispatchers.Main) {
                            onStateUpdated(snapshot)
                        }
                    }
                }
                infoConn.disconnect()
            } catch (e: Exception) {
                Log.d(TAG, "Initial connect/info fetch note: ${e.message}")
            }

            while (isActive && isConnected) {
                var connection: HttpURLConnection? = null
                try {
                    val url = URL("http://${device.host}:${device.port}/connect/events")
                    connection = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 3000
                        readTimeout = 0 // Infinite read timeout for SSE
                        requestMethod = "GET"
                        setRequestProperty("Accept", "text/event-stream")
                    }

                    if (connection.responseCode == 200) {
                        val reader = BufferedReader(InputStreamReader(connection.inputStream))
                        while (isActive && isConnected) {
                            val line = reader.readLine() ?: break
                            if (line.startsWith("data:")) {
                                val jsonStr = line.substring(5).trim()
                                if (jsonStr.isNotEmpty()) {
                                    val json = JSONObject(jsonStr)
                                    val snapshot = ConnectPlaybackSnapshot.fromJson(json)
                                    _remoteState.value = snapshot
                                    withContext(Dispatchers.Main) {
                                        onStateUpdated(snapshot)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "SSE stream reconnecting: ${e.message}")
                } finally {
                    runCatching { connection?.disconnect() }
                }

                if (isActive && isConnected) {
                    delay(1500)
                }
            }
        }
    }

    fun sendCommand(command: ConnectCommand) {
        val device = activeDevice ?: return
        val clientName = getClientDeviceName()
        val commandWithClient = if (command.clientName == null) command.copy(clientName = clientName) else command

        scope.launch(Dispatchers.IO) {
            try {
                val url = URL("http://${device.host}:${device.port}/connect/command")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 1500
                    readTimeout = 1500
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }

                val body = commandWithClient.toJson().toString()
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(body)
                    writer.flush()
                }

                val code = connection.responseCode
                if (code != 200) {
                    Log.w(TAG, "Command failed with code: $code")
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send command ${command.action}: ${e.message}")
            }
        }
    }

    private fun getClientDeviceName(): String {
        val customName = runCatching {
            Settings.Global.getString(context.contentResolver, "device_name")
        }.getOrNull()?.takeIf { it.isNotBlank() }

        val btName = runCatching {
            Settings.Secure.getString(context.contentResolver, "bluetooth_name")
        }.getOrNull()?.takeIf { it.isNotBlank() }

        return customName ?: btName ?: "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
    }
}
