package com.music.bitchord.remote

import android.content.Context
import com.music.bitchord.R
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
 * The largest request body the server will read. Every legitimate body
 * is a small JSON object (a PIN, a name, one song); anything bigger is
 * a mistake or an attack, and it is refused before a single byte is
 * buffered — the claimed Content-Length is never trusted as an
 * allocation size.
 */
private const val MAX_BODY_BYTES = 64 * 1024

/** Wrong PINs from one address that trip the unlock lockout. */
private const val UNLOCK_FAILURE_LIMIT = 5

/** Wrong PINs across all addresses that trip the global lockout. */
private const val UNLOCK_GLOBAL_FAILURE_LIMIT = 30

/** How long failures count toward a lockout. */
private const val UNLOCK_WINDOW_MS = 10 * 60 * 1000L

/** How long one address stays locked out. */
private const val UNLOCK_LOCKOUT_MS = 10 * 60 * 1000L

/** How long everyone stays locked out after a global trip. */
private const val UNLOCK_GLOBAL_LOCKOUT_MS = 5 * 60 * 1000L

/** A beat of delay on every wrong PIN, so guessing is never free. */
private const val UNLOCK_FAILURE_DELAY_MS = 400L

/**
 * Which PIN a server start uses, before any generation happens: the
 * user's own PIN when one is set (it always wins); else, with the
 * persistent option on, the last generated PIN; else null, meaning
 * "mint a fresh one" (the caller saves it when persistent is on).
 * Pure, so the order is unit-testable.
 */
internal fun pickPin(custom: String, persistent: Boolean, saved: String): String? = when {
    custom.isNotBlank() -> custom
    persistent && saved.isNotBlank() -> saved
    else -> null
}

/**
 * Display names are cosmetic, not identities: trim, cap the length,
 * and drop control characters so a name can never break the page's
 * layout or its logs. Empty names join as "Guest".
 */
internal fun sanitizeRemoteName(raw: String?): String {
    val cleaned = raw.orEmpty().trim().filter { !it.isISOControl() }.take(24).trim()
    return cleaned.ifEmpty { "Guest" }
}

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
 * it once — together with a display name — for a session token. Anyone
 * with the PIN can join under their own name; each connection holds its
 * own token, and songs a connection queues are labelled with its name.
 * A LAN is a small place, but "anyone on the coffee-shop Wi-Fi can pause
 * your music" is not a feature.
 */
object RemoteControl {

    const val PORT = 8899

    data class State(
        val running: Boolean = false,
        val url: String? = null,
        val pin: String? = null,
        /** Why the last start failed, shown under the switch; null when fine. */
        val error: String? = null,
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
        val pin = resolvePin()
        val http = RemoteHttpServer(app.applicationContext, pin)
        try {
            http.start()
        } catch (e: Exception) {
            Log.e(REMOTE_LOG_TAG, "remote server failed to bind", e)
            // Surface it: a switch that silently does nothing leaves the
            // user with no recourse and no explanation.
            _state.value = State(error = app.getString(R.string.remote_control_start_failed))
            return
        }
        server = http
        RemoteBridge.connect(app.applicationContext)
        _state.value = State(running = true, url = lanUrl(), pin = pin)
        Log.i(REMOTE_LOG_TAG, "remote control listening at ${_state.value.url}")
    }

    /**
     * Which PIN this start uses: the user's own PIN when one is set;
     * else, with the persistent option on, the last generated PIN —
     * generating and remembering one when there is none yet; else a
     * fresh PIN for this start only. Settings changes therefore take
     * effect at the next start, never under a running server.
     */
    private fun resolvePin(): String {
        val chosen = pickPin(
            custom = AppSettings.remoteControlPin.value,
            persistent = AppSettings.remoteControlPinPersistent.value,
            saved = AppSettings.remoteControlSavedPin.value,
        )
        if (chosen != null) return chosen
        val generated = newPin()
        if (AppSettings.remoteControlPinPersistent.value) {
            AppSettings.setRemoteControlSavedPin(generated)
        }
        return generated
    }

    private fun newPin(): String = "%04d".format(SecureRandom().nextInt(10_000))

    @Synchronized
    fun stop() {
        server?.stop()
        server = null
        RemoteBridge.disconnect()
        _state.value = State()
    }

    fun lanUrl(): String? = lanIp()?.let { "http://$it:$PORT" }

    /**
     * The address browsers on the LAN should use. The Wi-Fi interface
     * wins: with a VPN connected, the tunnel interface also carries a
     * site-local address, and advertising that one leaves the remote
     * unreachable for everyone on the Wi-Fi — the server itself listens
     * on every interface either way. Falls back to the first site-local
     * IPv4 on any up interface when no Wi-Fi interface is found.
     */
    fun lanIp(): String? = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .toList()
        interfaces.sortedByDescending { it.name.startsWith("wlan") }
            .asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

}

private class RemoteHttpServer(
    private val app: Context,
    private val pin: String,
) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    @Volatile private var running = false
    private var socket: ServerSocket? = null

    /** Session token -> display name, one entry per joined browser. */
    private val sessions = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Wrong-PIN bookkeeping for /api/unlock. A 4-digit PIN is only a
     * lock against strangers if guessing is expensive, so failures are
     * counted per source address and globally: an address that trips
     * [UNLOCK_FAILURE_LIMIT] inside the window is refused — even with
     * the right PIN — until its lockout passes, and a flood from many
     * addresses locks everyone out briefly. The direct socket address
     * is the key: connections here are direct LAN traffic, no proxy,
     * so it cannot be spoofed the way a forwarded-for header can.
     */
    private val unlockLock = Any()
    private val unlockFailures = HashMap<String, ArrayDeque<Long>>()
    private val unlockLockouts = HashMap<String, Long>()
    private val globalUnlockFailures = ArrayDeque<Long>()
    private var globalUnlockLockoutUntil = 0L

    private fun unlockLockedOut(ip: String, now: Long): Boolean = synchronized(unlockLock) {
        (unlockLockouts[ip] ?: 0L) > now || globalUnlockLockoutUntil > now
    }

    private fun noteUnlockFailure(ip: String, now: Long) {
        synchronized(unlockLock) {
            val cutoff = now - UNLOCK_WINDOW_MS
            fun prune(stamps: ArrayDeque<Long>) {
                while (stamps.isNotEmpty() && stamps.first() < cutoff) stamps.removeFirst()
            }
            val mine = unlockFailures.getOrPut(ip) { ArrayDeque() }
            prune(mine)
            mine.addLast(now)
            if (mine.size >= UNLOCK_FAILURE_LIMIT) {
                unlockLockouts[ip] = now + UNLOCK_LOCKOUT_MS
            }
            prune(globalUnlockFailures)
            globalUnlockFailures.addLast(now)
            if (globalUnlockFailures.size >= UNLOCK_GLOBAL_FAILURE_LIMIT) {
                globalUnlockLockoutUntil = now + UNLOCK_GLOBAL_LOCKOUT_MS
            }
            // Keep the maps household-sized: drop addresses whose
            // window has drained and whose lockout has passed.
            val it = unlockFailures.entries.iterator()
            while (it.hasNext()) {
                val entry = it.next()
                prune(entry.value)
                if (entry.value.isEmpty() && (unlockLockouts[entry.key] ?: 0L) <= now) {
                    unlockLockouts.remove(entry.key)
                    it.remove()
                }
            }
        }
    }

    private fun noteUnlockSuccess(ip: String) {
        synchronized(unlockLock) {
            unlockFailures.remove(ip)
            unlockLockouts.remove(ip)
        }
    }

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
        sessions.clear()
        job.cancel()
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
            if (length > MAX_BODY_BYTES) {
                // Refused before auth and before any allocation: the
                // claimed length is the attack, not the body.
                respondJson(output, 400, JSONObject().put("error", "request too large"))
                return
            }
            val body = if (length > 0) readBody(input, length) else ""

            when {
                method == "GET" && (path == "/" || path == "/index.html") ->
                    respond(output, 200, "text/html; charset=utf-8", pageBytes)

                method == "POST" && path == "/api/unlock" -> {
                    val ip = client.inetAddress?.hostAddress ?: "unknown"
                    val now = System.currentTimeMillis()
                    val json = runCatching { JSONObject(body) }.getOrNull()
                    val given = json?.optString("pin")
                    when {
                        unlockLockedOut(ip, now) ->
                            respondJson(
                                output, 429,
                                JSONObject().put("error", "too many wrong PINs — wait a few minutes"),
                            )
                        given == pin -> {
                            noteUnlockSuccess(ip)
                            val name = sanitizeRemoteName(json?.optString("name"))
                            val fresh = ByteArray(16).also { SecureRandom().nextBytes(it) }
                                .joinToString("") { "%02x".format(it) }
                            sessions[fresh] = name
                            respondJson(output, 200, JSONObject().put("token", fresh).put("name", name))
                        }
                        else -> {
                            noteUnlockFailure(ip, now)
                            Thread.sleep(UNLOCK_FAILURE_DELAY_MS)
                            respondJson(output, 401, JSONObject().put("error", "wrong PIN"))
                        }
                    }
                }

                path.startsWith("/api/") && sessionUser(headers) == null ->
                    respondJson(output, 401, JSONObject().put("error", "locked"))

                method == "GET" && path == "/api/state" -> {
                    val snapshot = RemoteBridge.snapshot(app, sessionUser(headers))
                    if (snapshot == null) {
                        respondJson(output, 503, JSONObject().put("error", "player not connected yet"))
                    } else {
                        respond(output, 200, "application/json", snapshot.toString().toByteArray(StandardCharsets.UTF_8))
                    }
                }

                method == "POST" && path == "/api/command" -> {
                    val ok = runCatching {
                        RemoteBridge.command(app, JSONObject(body), sessionUser(headers))
                    }.getOrDefault(false)
                    respondJson(output, if (ok) 200 else 400, JSONObject().put("ok", ok))
                }

                method == "GET" && path == "/api/search" -> {
                    val query = queryParam(rawPath, "q") ?: ""
                    searchAndRespond(output, client, query)
                    return // the search coroutine writes the response and closes the socket
                }

                method == "GET" && path == "/api/browse" -> {
                    val id = queryParam(rawPath, "id") ?: ""
                    val kind = queryParam(rawPath, "kind") ?: ""
                    browseAndRespond(output, client, id, kind)
                    return // the browse coroutine writes the response and closes the socket
                }

                method == "GET" && path == "/api/lyrics" -> {
                    lyricsAndRespond(output, client)
                    return // the lyrics coroutine writes the response and closes the socket
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

    private fun searchAndRespond(output: OutputStream, client: Socket, query: String) =
        asyncJson(output, client) { RemoteBridge.searchAll(query) }

    private fun browseAndRespond(output: OutputStream, client: Socket, id: String, kind: String) =
        asyncJson(output, client) { RemoteBridge.browse(id, kind) }

    private fun lyricsAndRespond(output: OutputStream, client: Socket) =
        asyncJson(output, client) { RemoteBridge.lyrics() }

    /**
     * Runs a suspend JSON producer on the IO scope and keeps this worker
     * thread parked until the response is written.
     */
    private fun asyncJson(output: OutputStream, client: Socket, block: suspend () -> JSONObject) {
        val latch = java.util.concurrent.CountDownLatch(1)
        scope.launch {
            try {
                respond(output, 200, "application/json",
                    block().toString().toByteArray(StandardCharsets.UTF_8))
            } catch (e: Exception) {
                runCatching {
                    respondJson(output, 500, JSONObject().put("error", "request failed"))
                }
            } finally {
                latch.countDown()
                try {
                    client.close()
                } catch (ignored: Exception) {
                }
            }
        }
        latch.await(30, java.util.concurrent.TimeUnit.SECONDS)
    }

    /**
     * The display name behind the request's session token, if it has
     * one. The token travels in a header only — never in the URL, where
     * it would leak into browser history and logs.
     */
    private fun sessionUser(headers: Map<String, String>): String? {
        val presented = headers["x-remote-token"] ?: return null
        return sessions[presented]
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
            429 -> "Too Many Requests"
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
