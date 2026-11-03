package com.music.bitchord.remote

import android.content.Context
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

internal const val REMOTE_LOG_TAG = "RemoteControl"

/**
 * A tiny web server in the app that turns any browser on the same Wi-Fi
 * into a BitChord remote: a player page with the current track, the queue,
 * transport controls and YouTube Music search, all driving this phone's
 * player through [RemoteBridge].
 *
 * Why not Listen Together's protocol? That syncs BitChord-to-BitChord
 * through a hosted relay. This is the opposite shape — the phone is the
 * server, a browser is the client, nothing leaves the LAN — so it also
 * works for the person next to you who does not have the app.
 *
 * Access is a PIN shown next to the switch in Settings: the page trades
 * it once for a session token. A LAN is a small place, but "anyone on the
 * coffee-shop Wi-Fi can pause your music" is not a feature.
 */
object RemoteControl {

    const val PORT = 8899

    data class State(
        val running: Boolean = false,
        val url: String? = null,
        val pin: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var server: RemoteHttpServer? = null

    /** Called once from the application, after settings are loaded. */
    fun init(app: Context) {
        if (AppSettings.remoteControlEnabled.value) start(app)
    }

    @Synchronized
    fun start(app: Context) {
        if (server != null) return
        val pin = "%04d".format(SecureRandom().nextInt(10_000))
        val http = RemoteHttpServer(app.applicationContext, pin)
        try {
            http.start()
        } catch (e: Exception) {
            Log.e(REMOTE_LOG_TAG, "remote server failed to bind", e)
            return
        }
        server = http
        RemoteBridge.connect(app.applicationContext)
        _state.value = State(running = true, url = lanUrl(), pin = pin)
        Log.i(REMOTE_LOG_TAG, "remote control listening at ${_state.value.url}")
    }

    @Synchronized
    fun stop() {
        server?.stop()
        server = null
        RemoteBridge.disconnect()
        _state.value = State()
    }

    fun lanUrl(): String? = lanIp()?.let { "http://$it:$PORT" }

    /** First site-local IPv4 (the Wi-Fi address, in practice). */
    fun lanIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

}

private class RemoteHttpServer(
    private val app: Context,
    private val pin: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var running = false
    private var socket: ServerSocket? = null
    private var token: String? = null

    private val pageBytes: ByteArray by lazy {
        app.assets.open("remote_player.html").use { it.readBytes() }
    }

    fun start() {
        socket = ServerSocket(RemoteControl.PORT)
        running = true
        Thread({
            while (running) {
                try {
                    val client = socket?.accept() ?: break
                    Thread({ handle(client) }, "remote-http").apply { isDaemon = true }.start()
                } catch (e: Exception) {
                    if (running) Log.w(REMOTE_LOG_TAG, "accept failed", e)
                }
            }
        }, "remote-http-accept").apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
        try {
            socket?.close()
        } catch (ignored: Exception) {
        }
        socket = null
        token = null
    }

    private fun handle(client: Socket) {
        try {
            client.soTimeout = 15_000
            val input = BufferedInputStream(client.getInputStream())
            val output = client.getOutputStream()
            val head = readHead(input) ?: run {
                client.close()
                return
            }
            val lines = head.split("\r\n")
            val parts = lines.firstOrNull()?.split(" ") ?: emptyList()
            val method = parts.getOrNull(0) ?: ""
            val rawPath = parts.getOrNull(1) ?: "/"
            val path = rawPath.substringBefore('?')
            val headers = parseHeaders(lines)
            val length = headers["content-length"]?.toIntOrNull() ?: 0
            val body = if (length > 0) readBody(input, length) else ""

            when {
                method == "GET" && (path == "/" || path == "/index.html") ->
                    respond(output, 200, "text/html; charset=utf-8", pageBytes)

                method == "POST" && path == "/api/unlock" -> {
                    val given = runCatching { JSONObject(body).optString("pin") }.getOrNull()
                    if (given == pin) {
                        val fresh = ByteArray(16).also { SecureRandom().nextBytes(it) }
                            .joinToString("") { "%02x".format(it) }
                        token = fresh
                        respondJson(output, 200, JSONObject().put("token", fresh))
                    } else {
                        respondJson(output, 401, JSONObject().put("error", "wrong PIN"))
                    }
                }

                path.startsWith("/api/") && !authorized(headers, rawPath) ->
                    respondJson(output, 401, JSONObject().put("error", "locked"))

                method == "GET" && path == "/api/state" -> {
                    val snapshot = RemoteBridge.snapshot(app)
                    if (snapshot == null) {
                        respondJson(output, 503, JSONObject().put("error", "player not connected yet"))
                    } else {
                        respond(output, 200, "application/json", snapshot.toString().toByteArray(StandardCharsets.UTF_8))
                    }
                }

                method == "POST" && path == "/api/command" -> {
                    val ok = runCatching { RemoteBridge.command(app, JSONObject(body)) }.getOrDefault(false)
                    respondJson(output, if (ok) 200 else 400, JSONObject().put("ok", ok))
                }

                method == "GET" && path == "/api/search" -> {
                    val query = queryParam(rawPath, "q") ?: ""
                    searchAndRespond(output, client, query)
                    return // the search coroutine writes the response and closes the socket
                }

                else -> respondJson(output, 404, JSONObject().put("error", "not found"))
            }
        } catch (e: Exception) {
            Log.w(REMOTE_LOG_TAG, "request failed", e)
        } finally {
            try {
                client.close()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun searchAndRespond(output: OutputStream, client: Socket, query: String) {
        // Runs the suspend search on the IO scope and keeps this worker
        // thread parked until the response is written.
        val latch = java.util.concurrent.CountDownLatch(1)
        scope.launch {
            try {
                val songs = RemoteBridge.search(query)
                respond(output, 200, "application/json",
                    RemoteBridge.searchJson(songs).toString().toByteArray(StandardCharsets.UTF_8))
            } catch (e: Exception) {
                runCatching {
                    respondJson(output, 500, JSONObject().put("error", "search failed"))
                }
            } finally {
                latch.countDown()
                try {
                    client.close()
                } catch (ignored: Exception) {
                }
            }
        }
        latch.await(20, java.util.concurrent.TimeUnit.SECONDS)
    }

    private fun authorized(headers: Map<String, String>, rawPath: String): Boolean {
        val current = token ?: return false
        if (headers["x-remote-token"] == current) return true
        return queryParam(rawPath, "t") == current
    }

    private fun respondJson(output: OutputStream, status: Int, json: JSONObject) {
        respond(output, status, "application/json", json.toString().toByteArray(StandardCharsets.UTF_8))
    }

    private fun respond(output: OutputStream, status: Int, mime: String, body: ByteArray) {
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            500 -> "Internal Server Error"
            503 -> "Service Unavailable"
            else -> "OK"
        }
        val head = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: $mime\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        synchronized(output) {
            output.write(head.toByteArray(StandardCharsets.UTF_8))
            output.write(body)
            output.flush()
        }
    }

    private fun readHead(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        var window = 0
        var b: Int
        while (input.read().also { b = it } >= 0) {
            buf.write(b)
            window = (window shl 8) or b
            if (window == 0x0D0A0D0A) break
            if (buf.size() > 32 * 1024) break
        }
        if (buf.size() == 0) return null
        return buf.toString(StandardCharsets.UTF_8.name())
    }

    private fun readBody(input: InputStream, length: Int): String {
        val buf = ByteArray(length)
        var off = 0
        while (off < length) {
            val n = input.read(buf, off, length - off)
            if (n < 0) break
            off += n
        }
        return String(buf, 0, off, StandardCharsets.UTF_8)
    }

    private fun parseHeaders(lines: List<String>): Map<String, String> {
        val out = HashMap<String, String>()
        for (line in lines.drop(1)) {
            val idx = line.indexOf(':')
            if (idx > 0) {
                out[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }
        }
        return out
    }

    private fun queryParam(rawPath: String, name: String): String? {
        val query = rawPath.substringAfter('?', "")
        if (query.isEmpty()) return null
        for (pair in query.split('&')) {
            val kv = pair.split('=', limit = 2)
            if (kv.size == 2 && kv[0] == name) {
                return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name())
            }
        }
        return null
    }
}
