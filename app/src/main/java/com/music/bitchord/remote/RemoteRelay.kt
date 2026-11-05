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
 * The phone's half of the relay mode: a small pool of WebSocket
 * channels to the relay service, each bridging one browser request to
 * the phone's own remote server.
 *
 * Why WebSocket, and why through the tunnel hostname: the relay's
 * first version took raw TCP channels on a public VPS port, which
 * worked but left the port exposed and the traffic plaintext. The
 * Cloudflare Tunnel in front of the VPS carries HTTP and WebSocket
 * only, so the channels moved to `wss://remote.gaspol.men/channel`:
 * to every network on the way — the phone's VPN above all — a channel
 * is ordinary HTTPS, and once the tunnel path is proven the VPS needs
 * no open port at all. Cloudflare reaps idle WebSockets, so a parked
 * channel pings every [PING_INTERVAL_MS]; the relay answers.
 *
 * The relay is a dumb pipe: binary messages are the spliced byte
 * stream, and the local server still enforces the PIN, its lockout
 * and its body cap on every request that arrives through a channel.
 * A channel carries one request and is then redialled — the local
 * server closes after every response anyway. The pool is only held
 * while the remote server runs; [RemoteControl] starts and stops
 * this client alongside it.
 */
object RemoteRelay {

    private const val RELAY_HOST = "remote.gaspol.men"
    private const val RELAY_PORT = 443
    private const val CHANNEL_PATH = "/channel"
    private const val CHANNELS = 4
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val LOCAL_TIMEOUT_MS = 5_000
    private const val RETRY_DELAY_MS = 1_000L
    private const val PING_INTERVAL_MS = 30_000
    private const val FRAME_CHUNK = 32 * 1024

    @Volatile private var running = false
    private var token: String = ""
    private val openSockets = ConcurrentHashMap.newKeySet<Socket>()

    @Synchronized
    fun start(relayToken: String) {
        if (running) return
        token = relayToken
        running = true
        repeat(CHANNELS) { i ->
            Thread({ channelLoop() }, "remote-relay-$i").apply { isDaemon = true }.start()
        }
        Log.i(REMOTE_LOG_TAG, "relay client started ($CHANNELS channels to wss://$RELAY_HOST$CHANNEL_PATH)")
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        // Closing the sockets is what unblocks the channel threads
        // parked in reads; they notice `running` is false and exit.
        openSockets.forEach { runCatching { it.close() } }
        openSockets.clear()
        Log.i(REMOTE_LOG_TAG, "relay client stopped")
    }

    private fun channelLoop() {
        while (running) {
            var ws: WsConnection? = null
            var local: Socket? = null
            try {
                ws = WsConnection(token)
                openSockets.add(ws.socket)
                ws.handshake()

                // Parked until the relay hands this channel a request.
                // The local server is only touched once bytes arrive —
                // a parked channel holds no local connection.
                val first = ws.readData() ?: throw IOException("channel closed while parked")

                local = Socket().apply {
                    connect(InetSocketAddress("127.0.0.1", RemoteControl.PORT), LOCAL_TIMEOUT_MS)
                    tcpNoDelay = true
                }
                openSockets.add(local)
                val localOut = local.getOutputStream()
                localOut.write(first)
                localOut.flush()

                val pumpThread = Thread({ pumpLocalToWs(local, ws) }, "remote-relay-pump")
                    .apply { isDaemon = true }
                pumpThread.start()
                // Relay -> local, in this thread: data messages are
                // stream bytes, written through until the channel ends.
                while (true) {
                    val data = ws.readData() ?: break
                    localOut.write(data)
                    localOut.flush()
                }
                pumpThread.join(2_000)
            } catch (e: Exception) {
                if (running) {
                    Log.w(REMOTE_LOG_TAG, "relay channel dropped: ${e.message}")
                    try {
                        Thread.sleep(RETRY_DELAY_MS)
                    } catch (ignored: InterruptedException) {
                    }
                }
            } finally {
                ws?.let { openSockets.remove(it.socket); it.close() }
                local?.let { openSockets.remove(it); runCatching { it.close() } }
            }
        }
    }

    /** Local server -> channel, until the local side closes. */
    private fun pumpLocalToWs(local: Socket, ws: WsConnection) {
        try {
            val input = local.getInputStream()
            val buf = ByteArray(FRAME_CHUNK)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                ws.sendBinary(buf, n)
            }
        } catch (ignored: Exception) {
        } finally {
            runCatching { ws.sendClose() }
        }
    }

    /**
     * A minimal WebSocket client over TLS, just enough for the relay:
     * the upgrade handshake with the relay token in a header, masked
     * binary frames out, plain frames in, ping/pong handled inside the
     * read loop. The socket read timeout doubles as the keepalive
     * timer — a read that times out sends a ping and resumes, so a
     * parked channel speaks every [PING_INTERVAL_MS] and Cloudflare
     * never sees it idle.
     */
    private class WsConnection(private val token: String) {

        val socket: SSLSocket = run {
            // An unconnected SSLSocket, then connect(): the layered
            // createSocket(Socket, ...) overload is not visible
            // through getDefault()'s declared SocketFactory type.
            val tls = SSLSocketFactory.getDefault().createSocket() as SSLSocket
            tls.connect(InetSocketAddress(RELAY_HOST, RELAY_PORT), CONNECT_TIMEOUT_MS)
            tls.soTimeout = PING_INTERVAL_MS
            tls.startHandshake()
            verifyPeer(tls)
            tls
        }

        private val input: InputStream = socket.getInputStream()
        private val output: OutputStream = socket.getOutputStream()
        private val sendLock = Any()
        private val random = SecureRandom()

        fun handshake() {
            val key = ByteArray(16).also { random.nextBytes(it) }
            val request = buildString {
                append("GET $CHANNEL_PATH HTTP/1.1\r\n")
                append("Host: $RELAY_HOST\r\n")
                append("Upgrade: websocket\r\n")
                append("Connection: Upgrade\r\n")
                append("Sec-WebSocket-Key: ${Base64.encodeToString(key, Base64.NO_WRAP)}\r\n")
                append("Sec-WebSocket-Version: 13\r\n")
                append("X-Relay-Token: $token\r\n")
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
         * The next data payload (binary, with continuation frames
         * assembled), or null when the channel has closed. Control
         * frames are handled on the way: pings are answered, pongs
         * ignored.
         */
        fun readData(): ByteArray? {
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
                        acc.write(frame.payload)
                        if (frame.fin) return acc.toByteArray()
                    }
                    else -> Unit
                }
            }
        }

        fun sendBinary(data: ByteArray, length: Int) = sendFrame(0x2, data, length)

        fun sendClose() {
            sendFrame(0x8, ByteArray(0), 0)
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
                    // The keepalive: a parked channel must not look
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
            if (length < 0 || length > 1024 * 1024) throw IOException("frame too large")
            val maskKey = if (masked) {
                ByteArray(4) { readByte()?.toByte() ?: return null }
            } else {
                null
            }
            val payload = ByteArray(length.toInt())
            var off = 0
            while (off < payload.size) {
                val b = readByte() ?: return null
                payload[off] = if (maskKey != null) {
                    (b xor (maskKey[off % 4].toInt() and 0xFF)).toByte()
                } else {
                    b.toByte()
                }
                off++
            }
            return Frame(fin, opcode, payload)
        }

        private fun readByte(): Int? {
            val b = input.read()
            return if (b < 0) null else b
        }

        /** Client frames are always masked (RFC 6455 §5.3). */
        private fun sendFrame(opcode: Int, data: ByteArray, length: Int) {
            val mask = ByteArray(4).also { random.nextBytes(it) }
            val head = ByteArrayOutputStream()
            head.write(0x80 or opcode)
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
         */
        private fun verifyPeer(tls: SSLSocket) {
            val cert = tls.session.peerCertificates.firstOrNull() as? X509Certificate
                ?: throw IOException("relay presented no certificate")
            val names = runCatching { cert.subjectAlternativeNames?.toList() }.getOrNull().orEmpty()
            val dnsNames = names.filter { it.size >= 2 && it[0] == 7 }.map { it[1].toString() }
            val cn = Regex("CN=([^,]+)").find(cert.subjectX500Principal.name)?.groupValues?.get(1)
            if (RELAY_HOST !in dnsNames && cn != RELAY_HOST) {
                throw IOException("relay certificate is not for $RELAY_HOST")
            }
        }
    }
}
