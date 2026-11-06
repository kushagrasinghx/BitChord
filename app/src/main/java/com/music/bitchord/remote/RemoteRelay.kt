package com.music.bitchord.remote

import android.util.Base64
import com.music.bitchord.data.DebugLog as Log
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * The phone's half of the relay mode: two persistent WebSocket
 * channels to the relay service, multiplexing browser requests to
 * the phone's own remote server.
 *
 * Why WebSocket, and why through the tunnel hostname: the relay's
 * first version took raw TCP channels on a public VPS port, which
 * worked but left the port exposed and the traffic plaintext. The
 * Cloudflare Tunnel in front of the VPS carries HTTP and WebSocket
 * only, so the channels moved to `wss://<relay address>/channel`:
 * to every network on the way — the phone's VPN above all — a channel
 * is ordinary HTTPS, and once the tunnel path is proven the VPS needs
 * no open port at all. (An address written with a `ws://` scheme
 * instead dials a relay directly in plaintext — the same protocol
 * minus TLS, for owners who would rather have no tunnel in the path.)
 * Cloudflare reaps idle WebSockets, so a connected
 * channel pings every [PING_INTERVAL_MS]; the relay answers.
 *
 * Why multiplexed: the second version burned one channel per browser
 * request, and the remote page polls state every second — so the
 * phone owed Cloudflare a fresh TLS + WebSocket handshake every
 * second, and any slow redial (a VPN route change, a network switch)
 * drained the channel pool faster than it refilled. The page hung,
 * then 502'd: "spotty." Now a channel is persistent and every binary
 * message on it is one leg of some request — a five-byte header
 * (request id, type) plus payload; see [muxEncode]. Requests share
 * the channel, a redial is a rare event instead of a per-poll one,
 * and two channels are kept so one dying still leaves the other.
 *
 * The address is a setting, not a constant: the relay is a personal
 * service and its tunnel hostname is whatever its owner configured
 * in their DNS — the client cannot know it at compile time.
 *
 * The relay is a dumb pipe: it forwards request bytes and response
 * chunks without reading them, and the local server still enforces
 * the PIN, its lockout and its body cap on every request that
 * arrives through a channel. The pool is only held while the remote
 * server runs; [RemoteControl] starts and stops this client
 * alongside it.
 */
object RemoteRelay {

    /** Used only if the address setting somehow parses to nothing. */
    private const val FALLBACK_HOST = "remote.gaspol.men"
    private const val DEFAULT_PORT = 443
    private const val DIRECT_DEFAULT_PORT = 80
    private const val CHANNEL_PATH = "/channel"
    private const val CHANNELS = 2
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val LOCAL_TIMEOUT_MS = 5_000
    private const val RETRY_DELAY_MS = 1_000L
    private const val PING_INTERVAL_MS = 30_000
    private const val DATA_CHUNK = 16 * 1024

    // Mux message types — byte 4 of every message on a channel. The
    // relay speaks the same protocol from its side.
    const val MUX_REQUEST = 1 // relay -> phone: payload is the whole HTTP request
    const val MUX_DATA = 2    // phone -> relay: payload is a response chunk
    const val MUX_END = 3     // phone -> relay: the response is complete
    const val MUX_CANCEL = 4  // relay -> phone: the browser went away

    @Volatile private var running = false
    private var token: String = ""
    @Volatile private var relayHost: String = FALLBACK_HOST
    @Volatile private var relayPort: Int = DEFAULT_PORT
    @Volatile private var relayTls: Boolean = true
    private val openSockets = ConcurrentHashMap.newKeySet<Socket>()

    /** A parsed relay address: where to dial, and whether over TLS. */
    data class Address(val host: String, val port: Int, val useTls: Boolean)

    /**
     * Parse the address setting, forgivingly. The scheme chooses the
     * transport: `wss://`/`https://` (or no scheme) is TLS — the
     * tunnel hostname's shape — while `ws://`/`http://` is a
     * plaintext WebSocket to a relay reached directly, whose port
     * speaks plain HTTP for the tunnel's local side. Anything past
     * the first `/` is dropped, a `:port` suffix is honoured, and an
     * input whose host part parses empty falls back to the full
     * defaults (a stray `ws://` alone must not pair the default host
     * with a plaintext choice it never made) — a pasted browser URL
     * should just work either way.
     */
    fun parseAddress(raw: String): Address {
        var text = raw.trim()
        var useTls = true
        val schemeEnd = text.indexOf("://")
        if (schemeEnd >= 0) {
            val scheme = text.substring(0, schemeEnd).lowercase()
            useTls = scheme != "ws" && scheme != "http"
            text = text.substring(schemeEnd + 3)
        }
        text = text.substringBefore('/').trim()
        var host = text
        var port: Int? = null
        val colon = text.lastIndexOf(':')
        if (colon > 0) {
            port = text.substring(colon + 1).toIntOrNull()
            if (port != null) host = text.substring(0, colon)
        }
        host = host.lowercase()
        if (host.isEmpty()) return Address(FALLBACK_HOST, DEFAULT_PORT, true)
        return Address(host, port ?: if (useTls) DEFAULT_PORT else DIRECT_DEFAULT_PORT, useTls)
    }

    /** One decoded mux message: whose it is, what it is, its bytes. */
    class MuxMessage(val id: Int, val type: Int, val payload: ByteArray)

    /** Frame a mux message: 4-byte big-endian id, 1-byte type, payload. */
    fun muxEncode(id: Int, type: Int, payload: ByteArray): ByteArray {
        val out = ByteArray(5 + payload.size)
        out[0] = (id ushr 24).toByte()
        out[1] = (id ushr 16).toByte()
        out[2] = (id ushr 8).toByte()
        out[3] = id.toByte()
        out[4] = type.toByte()
        payload.copyInto(out, destinationOffset = 5)
        return out
    }

    /** The inverse of [muxEncode]; null when too short to be one. */
    fun muxDecode(message: ByteArray): MuxMessage? {
        if (message.size < 5) return null
        val id = ((message[0].toInt() and 0xFF) shl 24) or
            ((message[1].toInt() and 0xFF) shl 16) or
            ((message[2].toInt() and 0xFF) shl 8) or
            (message[3].toInt() and 0xFF)
        return MuxMessage(id, message[4].toInt() and 0xFF, message.copyOfRange(5, message.size))
    }

    @Synchronized
    fun start(relayToken: String, address: String) {
        if (running) return
        token = relayToken
        val parsed = parseAddress(address)
        relayHost = parsed.host
        relayPort = parsed.port
        relayTls = parsed.useTls
        running = true
        repeat(CHANNELS) { i ->
            Thread({ connectionLoop() }, "remote-relay-$i").apply { isDaemon = true }.start()
        }
        val scheme = if (relayTls) "wss" else "ws"
        Log.i(REMOTE_LOG_TAG, "relay client started ($CHANNELS mux channels to $scheme://$relayHost:$relayPort$CHANNEL_PATH)")
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        // Closing the sockets is what unblocks the connection threads
        // parked in reads and the exchanges parked in local reads;
        // they notice `running` is false and exit.
        openSockets.forEach { runCatching { it.close() } }
        openSockets.clear()
        Log.i(REMOTE_LOG_TAG, "relay client stopped")
    }

    private fun connectionLoop() {
        while (running) {
            var conn: MuxConnection? = null
            try {
                conn = MuxConnection(token)
                openSockets.add(conn.ws.socket)
                // stop() flips `running` before sweeping openSockets;
                // a socket added after that sweep would escape it, so
                // re-check here and self-close instead of leaking a
                // channel that outlives the stop.
                if (!running) {
                    conn.ws.close()
                    break
                }
                conn.ws.handshake()
                conn.run()
            } catch (e: Exception) {
                if (running) {
                    Log.w(REMOTE_LOG_TAG, "relay channel dropped: ${e.message}")
                    try {
                        Thread.sleep(RETRY_DELAY_MS)
                    } catch (ignored: InterruptedException) {
                    }
                }
            } finally {
                conn?.let {
                    openSockets.remove(it.ws.socket)
                    it.closeAll()
                }
            }
        }
    }

    /**
     * One persistent channel and the requests it is carrying. The
     * reader loop runs on the connection's own thread; each REQUEST
     * gets an [Exchange] on a thread of its own, so a slow local
     * response never blocks the next request's arrival.
     */
    private class MuxConnection(private val token: String) {

        val ws = WsConnection(token)
        private val exchanges = ConcurrentHashMap<Int, Exchange>()

        /** Read mux messages until the channel dies; then fail every
         * exchange still riding it (their local sockets close, which
         * is also what the relay does with the browser side). */
        fun run() {
            try {
                while (true) {
                    val raw = ws.readMessage() ?: break
                    val msg = muxDecode(raw) ?: continue
                    when (msg.type) {
                        MUX_REQUEST -> {
                            val exchange = Exchange(msg.id, this)
                            // The relay never reuses a live id; if one
                            // ever arrives, close the orphan rather than
                            // leak its socket and cross its responses.
                            exchanges.put(msg.id, exchange)?.close()
                            Thread(
                                { exchange.serve(msg.payload) },
                                "remote-relay-req-${msg.id}",
                            ).apply { isDaemon = true }.start()
                        }
                        MUX_CANCEL -> exchanges.remove(msg.id)?.close()
                        else -> Unit // DATA/END travel the other way; ignore
                    }
                }
            } finally {
                exchanges.values.forEach { it.close() }
                exchanges.clear()
            }
        }

        fun sendData(id: Int, chunk: ByteArray) {
            ws.sendBinary(muxEncode(id, MUX_DATA, chunk))
        }

        fun sendEnd(id: Int) {
            exchanges.remove(id)
            runCatching { ws.sendBinary(muxEncode(id, MUX_END, ByteArray(0))) }
        }

        fun closeAll() {
            exchanges.values.forEach { it.close() }
            exchanges.clear()
            ws.close()
        }

        /**
         * One browser request being served: connect the local remote
         * server, write the request, stream the response back as
         * DATA chunks, then END. The server answers with a
         * Content-Length and closes, so completion is whichever of
         * "the stated body has arrived" and EOF happens first.
         */
        private class Exchange(
            private val id: Int,
            private val conn: MuxConnection,
        ) {
            @Volatile private var local: Socket? = null
            @Volatile private var closed = false

            fun serve(requestBytes: ByteArray) {
                try {
                    val socket = Socket().apply {
                        connect(InetSocketAddress("127.0.0.1", RemoteControl.PORT), LOCAL_TIMEOUT_MS)
                        tcpNoDelay = true
                    }
                    local = socket
                    openSockets.add(socket)
                    val out = socket.getOutputStream()
                    out.write(requestBytes)
                    out.flush()
                    val input = socket.getInputStream()
                    val buf = ByteArray(DATA_CHUNK)
                    val head = ByteArrayOutputStream()
                    var bodyExpected = -1L
                    var bodySeen = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        val chunk = buf.copyOf(n)
                        conn.sendData(id, chunk)
                        if (bodyExpected < 0) {
                            head.write(chunk)
                            val bytes = head.toByteArray()
                            val headEnd = indexOfHeadEnd(bytes)
                            if (headEnd >= 0) {
                                bodyExpected = contentLengthOf(bytes, headEnd) ?: -1L
                                bodySeen = (bytes.size - headEnd).toLong()
                            }
                        } else {
                            bodySeen += n
                        }
                        if (bodyExpected >= 0 && bodySeen >= bodyExpected) break
                    }
                } catch (e: Exception) {
                    // A failed local exchange still ends: the relay
                    // closes the browser side rather than hanging it.
                    if (!closed) Log.w(REMOTE_LOG_TAG, "relay request $id failed: ${e.message}")
                } finally {
                    local?.let {
                        openSockets.remove(it)
                        runCatching { it.close() }
                    }
                    if (!closed) conn.sendEnd(id)
                }
            }

            fun close() {
                closed = true
                runCatching { local?.close() }
            }

            private fun indexOfHeadEnd(bytes: ByteArray): Int {
                for (i in 0..bytes.size - 4) {
                    if (bytes[i] == 13.toByte() && bytes[i + 1] == 10.toByte() &&
                        bytes[i + 2] == 13.toByte() && bytes[i + 3] == 10.toByte()
                    ) {
                        return i + 4
                    }
                }
                return -1
            }

            private fun contentLengthOf(head: ByteArray, headEnd: Int): Long? {
                val text = head.copyOf(headEnd).toString(Charsets.US_ASCII)
                for (line in text.split("\r\n")) {
                    if (line.startsWith("content-length:", ignoreCase = true)) {
                        return line.substringAfter(':').trim().toLongOrNull()
                    }
                }
                return null
            }
        }
    }

    /**
     * A minimal WebSocket client over TLS, just enough for the relay:
     * the upgrade handshake with the relay token and the mux marker
     * in headers, masked binary frames out, plain frames in, ping/pong
     * handled inside the read loop. The socket read timeout doubles
     * as the keepalive timer — a read that times out sends a ping and
     * resumes, so a connected channel speaks every [PING_INTERVAL_MS]
     * and Cloudflare never sees it idle.
     */
    private class WsConnection(private val token: String) {

        val socket: Socket = run {
            if (!relayTls) {
                // Plaintext to a directly reached relay: the same
                // WebSocket protocol, minus TLS — the relay's port
                // speaks plain HTTP for the tunnel's local side.
                val plain = Socket()
                plain.connect(InetSocketAddress(relayHost, relayPort), CONNECT_TIMEOUT_MS)
                plain.soTimeout = PING_INTERVAL_MS
                plain
            } else {
                // An unconnected SSLSocket, then connect(): the layered
                // createSocket(Socket, ...) overload is not visible
                // through getDefault()'s declared SocketFactory type.
                val tls = SSLSocketFactory.getDefault().createSocket() as SSLSocket
                tls.connect(InetSocketAddress(relayHost, relayPort), CONNECT_TIMEOUT_MS)
                tls.soTimeout = PING_INTERVAL_MS
                tls.startHandshake()
                verifyPeer(tls)
                tls
            }
        }

        private val input: InputStream = socket.getInputStream()
        private val output: OutputStream = socket.getOutputStream()
        private val sendLock = Any()
        private val random = SecureRandom()

        fun handshake() {
            val key = ByteArray(16).also { random.nextBytes(it) }
            val request = buildString {
                append("GET $CHANNEL_PATH HTTP/1.1\r\n")
                val defaultPort = if (relayTls) DEFAULT_PORT else DIRECT_DEFAULT_PORT
                append("Host: ${if (relayPort == defaultPort) relayHost else "$relayHost:$relayPort"}\r\n")
                append("Upgrade: websocket\r\n")
                append("Connection: Upgrade\r\n")
                append("Sec-WebSocket-Key: ${Base64.encodeToString(key, Base64.NO_WRAP)}\r\n")
                append("Sec-WebSocket-Version: 13\r\n")
                append("X-Relay-Token: $token\r\n")
                // The mux marker: the relay files this channel as
                // multiplexed rather than a one-request legacy channel.
                append("X-Relay-Mux: 1\r\n")
                append("\r\n")
            }
            output.write(request.toByteArray(Charsets.US_ASCII))
            output.flush()
            // Read byte-by-byte so no frame bytes are swallowed: the
            // first frame starts immediately after the head.
            val head = ByteArrayOutputStream()
            val window = IntArray(4)
            while (true) {
                val b = input.read()
                if (b < 0) throw IOException("relay closed during handshake")
                head.write(b)
                window[0] = window[1]; window[1] = window[2]; window[2] = window[3]; window[3] = b
                if (window.contentEquals(intArrayOf(13, 10, 13, 10))) break
                if (head.size() > 16 * 1024) throw IOException("handshake head too large")
            }
            val statusLine = head.toString(Charsets.US_ASCII.name()).substringBefore("\r\n")
            if (!statusLine.contains(" 101")) {
                throw IOException("relay refused the channel: $statusLine")
            }
        }

        /**
         * The next complete message (continuation frames assembled),
         * or null when the channel has closed. Control frames are
         * handled on the way: pings are answered, pongs ignored.
         */
        fun readMessage(): ByteArray? {
            var assembled: ByteArrayOutputStream? = null
            while (true) {
                val frame = readFrame() ?: return null
                when (frame.opcode) {
                    0x9 -> sendFrame(0xA, frame.payload, frame.payload.size)
                    0xA -> Unit
                    0x8 -> return null
                    0x0, 0x1, 0x2 -> {
                        if (frame.fin && assembled == null) return frame.payload
                        val acc = assembled ?: ByteArrayOutputStream().also { assembled = it }
                        // Mirror the relay's reassembly cap: frames are
                        // capped individually, the message was not.
                        if (acc.size() + frame.payload.size > MESSAGE_MAX) return null
                        acc.write(frame.payload)
                        if (frame.fin) return acc.toByteArray()
                    }
                    else -> Unit
                }
            }
        }

        fun sendBinary(data: ByteArray) {
            // Hold sendLock for the WHOLE message (sendFrame's own
            // lock is the same monitor, hence reentrant): releasing it
            // between fragments would let another message — or a pong
            // from the reader thread — splice into this one's frame
            // sequence and corrupt the peer's reassembly.
            synchronized(sendLock) {
                if (data.size <= FRAME_LIMIT) {
                    sendFrame(0x2, data, data.size)
                    return
                }
                // Larger than one frame: fragment, FIN only on the last.
                var off = 0
                var first = true
                while (off < data.size) {
                    val n = minOf(FRAME_LIMIT, data.size - off)
                    val fin = off + n == data.size
                    sendFrame(if (first) 0x2 else 0x0, data.copyOfRange(off, off + n), n, fin)
                    first = false
                    off += n
                }
            }
        }

        fun close() {
            runCatching { socket.close() }
        }

        private class Frame(val fin: Boolean, val opcode: Int, val payload: ByteArray)

        private fun readFrame(): Frame? {
            while (true) {
                try {
                    return readFrameRaw()
                } catch (e: SocketTimeoutException) {
                    // The keepalive: a connected channel must not look
                    // idle to the tunnel, so ping and keep waiting.
                    runCatching { sendFrame(0x9, ByteArray(0), 0) }
                }
            }
        }

        private fun readFrameRaw(): Frame? {
            val b0 = readByte() ?: return null
            val b1 = readByte() ?: return null
            val fin = b0 and 0x80 != 0
            val opcode = b0 and 0x0F
            val masked = b1 and 0x80 != 0
            var length = (b1 and 0x7F).toLong()
            if (length == 126L) {
                val hi = readByte() ?: return null
                val lo = readByte() ?: return null
                length = ((hi shl 8) or lo).toLong()
            } else if (length == 127L) {
                length = 0
                repeat(8) {
                    val b = readByte() ?: return null
                    length = (length shl 8) or b.toLong()
                }
            }
            if (length < 0 || length > 4 * 1024 * 1024) throw IOException("frame too large")
            val maskKey = if (masked) {
                ByteArray(4) { readByte()?.toByte() ?: return null }
            } else {
                null
            }
            val payload = ByteArray(length.toInt())
            var off = 0
            while (off < payload.size) {
                val n = input.read(payload, off, payload.size - off)
                if (n < 0) return null
                off += n
            }
            if (maskKey != null) {
                for (i in payload.indices) {
                    payload[i] = (payload[i].toInt() xor (maskKey[i % 4].toInt() and 0xFF)).toByte()
                }
            }
            return Frame(fin, opcode, payload)
        }

        private fun readByte(): Int? {
            val b = input.read()
            return if (b < 0) null else b
        }

        /** Client frames are always masked (RFC 6455 §5.3). */
        private fun sendFrame(opcode: Int, data: ByteArray, length: Int, fin: Boolean = true) {
            val mask = ByteArray(4).also { random.nextBytes(it) }
            val head = ByteArrayOutputStream()
            head.write((if (fin) 0x80 else 0x00) or opcode)
            when {
                length < 126 -> head.write(0x80 or length)
                length <= 0xFFFF -> {
                    head.write(0x80 or 126)
                    head.write(length ushr 8)
                    head.write(length)
                }
                else -> {
                    head.write(0x80 or 127)
                    repeat(8) { i -> head.write((length.toLong() ushr (56 - 8 * i)).toInt()) }
                }
            }
            head.write(mask)
            val masked = ByteArray(length) { i -> (data[i].toInt() xor (mask[i % 4].toInt() and 0xFF)).toByte() }
            synchronized(sendLock) {
                output.write(head.toByteArray())
                output.write(masked)
                output.flush()
            }
        }

        /**
         * SSLSocket checks the certificate chain but not the name on
         * it; the relay token crosses this connection, so check the
         * peer really is the relay hostname before any traffic flows.
         * Names match exactly or by single-label wildcard — the
         * tunnel's certificate is issued for `*.gaspol.men`, not for
         * this hostname literally (an exact-only check rejected every
         * channel before its handshake ever left the phone).
         */
        private fun verifyPeer(tls: SSLSocket) {
            val cert = tls.session.peerCertificates.firstOrNull() as? X509Certificate
                ?: throw IOException("relay presented no certificate")
            val names = runCatching { cert.subjectAlternativeNames?.toList() }.getOrNull().orEmpty()
            val dnsNames = names.filter { it.size >= 2 && it[0] == 7 }.map { it[1].toString() }
            val cn = Regex("CN=([^,]+)").find(cert.subjectX500Principal.name)?.groupValues?.get(1)
            val candidates = dnsNames + listOfNotNull(cn)
            if (candidates.none { nameMatches(it, relayHost) }) {
                throw IOException("relay certificate is not for $relayHost")
            }
        }

        /** Exact match, or `*.example.com` covering one label. */
        private fun nameMatches(pattern: String, host: String): Boolean {
            if (pattern.equals(host, ignoreCase = true)) return true
            if (!pattern.startsWith("*.")) return false
            val suffix = pattern.substring(1) // ".example.com"
            if (!host.endsWith(suffix, ignoreCase = true)) return false
            val label = host.dropLast(suffix.length)
            return label.isNotEmpty() && '.' !in label
        }

        private companion object {
            /** One frame's payload cap; larger messages fragment. */
            const val FRAME_LIMIT = 64 * 1024

            /** Whole-message reassembly cap, mirroring the relay. */
            const val MESSAGE_MAX = 16 * 1024 * 1024
        }
    }
}
