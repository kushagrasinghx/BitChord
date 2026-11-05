package com.music.bitchord.remote

import com.music.bitchord.data.DebugLog as Log
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * The phone's half of the relay mode: a small pool of outbound
 * connections to the relay service, each bridging one browser request
 * to the phone's own remote server.
 *
 * Why the phone dials out: the remote server is reachable on the LAN,
 * and a full-tunnel VPN on the phone breaks exactly that path — other
 * devices can no longer reach the phone. Outbound internet through
 * the VPN works fine, though, so the phone holds pooled connections
 * to the relay (a tiny service on the user's VPS), and browsers talk
 * to the relay's public address instead of the phone's:
 *
 *   browser ──▶ relay ──pooled channel──▶ this client ──▶ 127.0.0.1:8899
 *
 * The relay is a dumb pipe: the local server still enforces the PIN,
 * its lockout and its body cap on every request that arrives through
 * a channel. A channel carries one request and is then redialled —
 * the local server closes after every response anyway. The pool is
 * only held while the remote server runs; [RemoteControl] starts and
 * stops this client alongside it.
 *
 * The relay's address is a constant because the relay is a personal
 * service, deployed once for this fork; moving it is a one-line edit.
 */
object RemoteRelay {

    private const val RELAY_HOST = "103.103.23.1"
    private const val RELAY_PORT = 8890
    private const val CHANNELS = 4
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val LOCAL_TIMEOUT_MS = 5_000
    private const val RETRY_DELAY_MS = 1_000L

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
        Log.i(REMOTE_LOG_TAG, "relay client started ($CHANNELS channels to $RELAY_HOST:$RELAY_PORT)")
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
            var relay: Socket? = null
            var local: Socket? = null
            try {
                relay = Socket().apply {
                    connect(InetSocketAddress(RELAY_HOST, RELAY_PORT), CONNECT_TIMEOUT_MS)
                    tcpNoDelay = true
                }
                openSockets.add(relay)
                val relayOut = relay.getOutputStream()
                relayOut.write("RELAY/1 $token\n".toByteArray(Charsets.US_ASCII))
                relayOut.flush()

                // Parked until the relay hands this channel a request.
                // The local server is only touched once bytes arrive —
                // a parked channel holds no local connection.
                val relayIn = relay.getInputStream()
                val first = ByteArray(64 * 1024)
                val n = relayIn.read(first)
                if (n < 0) throw IOException("relay closed the channel")

                local = Socket().apply {
                    connect(InetSocketAddress("127.0.0.1", RemoteControl.PORT), LOCAL_TIMEOUT_MS)
                    tcpNoDelay = true
                }
                openSockets.add(local)
                val localOut = local.getOutputStream()
                localOut.write(first, 0, n)
                localOut.flush()

                val pumpThread = Thread({ pump(local, relay) }, "remote-relay-pump")
                    .apply { isDaemon = true }
                pumpThread.start()
                pump(relay, local)
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
                relay?.let { openSockets.remove(it); runCatching { it.close() } }
                local?.let { openSockets.remove(it); runCatching { it.close() } }
            }
        }
    }

    /** Copies one socket into the other until EOF, then half-closes. */
    private fun pump(from: Socket, to: Socket) {
        try {
            val input = from.getInputStream()
            val output = to.getOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                output.write(buf, 0, n)
                output.flush()
            }
        } catch (ignored: Exception) {
        } finally {
            runCatching { to.shutdownOutput() }
        }
    }
}
