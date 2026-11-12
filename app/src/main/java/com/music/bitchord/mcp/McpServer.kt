package com.music.bitchord.mcp

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*

/** Opt-in, process-lifetime server. Android may kill it; intentionally no foreground service. */
object McpServer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var server: HttpServer? = null
    private val mutableStatus = MutableStateFlow("Stopped")
    val status: StateFlow<String> = mutableStatus.asStateFlow()

    fun start(context: Context) {
        val app = context.applicationContext
        scope.launch {
            synchronized(lock) {
                McpSettings.init(app)
                McpSettings.onChanged = { start(app) }
                server?.stop()
                server = null
                if (!McpSettings.enabled) { mutableStatus.value = "Disabled"; return@synchronized }
                if (McpSettings.port !in 1024..65535 || McpSettings.token.length < 32) {
                    mutableStatus.value = "Invalid server settings"; return@synchronized
                }
                try {
                    val instance = HttpServer(app, McpSettings.port, McpSettings.lanEnabled, McpSettings.token)
                    server = instance
                    instance.start(10_000, true)
                    mutableStatus.value = "Listening on ${if (McpSettings.lanEnabled) "LAN" else "localhost"}:${McpSettings.port}/mcp"
                } catch (_: Exception) {
                    server?.stop(); server = null
                    mutableStatus.value = "Unable to start server"
                }
            }
        }
    }

    fun stop() {
        scope.launch { synchronized(lock) {
            server?.stop(); server = null; mutableStatus.value = "Stopped"
        } }
    }

    private class HttpServer(val context: Context, port: Int, val lan: Boolean, val token: String) :
        NanoHTTPD(if (lan) "0.0.0.0" else "127.0.0.1", port) {
        private val calls = SupervisorJob()
        private val runner = BoundedRunner()
        init { setAsyncRunner(runner) }
        override fun stop() {
            calls.cancel()
            super.stop()
            runner.closeAll()
        }
        private fun reply(code: Response.Status, text: String = ""): Response =
            newFixedLengthResponse(code, "application/json", text).apply {
                addHeader("Cache-Control", "no-store")
                addHeader("X-Content-Type-Options", "nosniff")
                // One bounded request per connection; no pipelined bytes survive.
                addHeader("Connection", "close")
            }

        override fun serve(session: IHTTPSession): Response {
            val h = session.headers
            if (h.containsKey("origin")) return reply(Response.Status.FORBIDDEN)
            if (!validHost(h["host"])) return reply(Response.Status.FORBIDDEN)
            val auth = h["authorization"].orEmpty()
            // Hash first to compare fixed-size byte arrays, including wrong-length input.
            fun digest(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
            if (!MessageDigest.isEqual(digest(auth), digest("Bearer $token")))
                return reply(Response.Status.UNAUTHORIZED).apply { addHeader("WWW-Authenticate", "Bearer") }
            if (session.uri != "/mcp") return reply(Response.Status.NOT_FOUND)
            if (session.method != Method.POST) return reply(Response.Status.METHOD_NOT_ALLOWED).apply {
                addHeader("Allow", "POST")
            }
            if (h["mcp-protocol-version"]?.let { it !in McpProtocol.versions } == true)
                return reply(Response.Status.BAD_REQUEST, "{\"error\":\"Unsupported MCP-Protocol-Version\"}")
            if (h["content-type"]?.substringBefore(';')?.trim()?.lowercase() != "application/json")
                return reply(Response.Status.UNSUPPORTED_MEDIA_TYPE)
            val accepts = h["accept"].orEmpty().split(',').map {
                it.substringBefore(';').trim().lowercase()
            }
            if ("application/json" !in accepts || "text/event-stream" !in accepts)
                return reply(Response.Status.NOT_ACCEPTABLE)
            // NanoHTTPD does not decode chunked request bodies. Require an exact length,
            // never parseBody (which can spill attacker data into temporary files).
            if (h.containsKey("transfer-encoding")) return reply(Response.Status.BAD_REQUEST)
            val length = h["content-length"]?.toLongOrNull() ?: return reply(Response.Status.LENGTH_REQUIRED)
            if (length !in 1..65536) return reply(Response.Status.PAYLOAD_TOO_LARGE)
            return try {
                val bytes = ByteArray(length.toInt())
                var offset = 0
                while (offset < bytes.size) {
                    val count = session.inputStream.read(bytes, offset, bytes.size - offset)
                    if (count <= 0) return reply(Response.Status.BAD_REQUEST)
                    offset += count
                }
                val result = runBlocking(calls) {
                    withTimeout(15_000) {
                        McpProtocol.dispatch(bytes.toString(Charsets.UTF_8), { McpTools.listTools(context) }) { name, args ->
                            try {
                                withTimeout(12_000) { McpTools.call(context, name, args) }
                            } catch (e: TimeoutCancellationException) {
                                toolError("Tool execution timed out")
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { toolError("Tool execution failed") }
                        }
                    }
                }
                if (result == null) reply(Response.Status.ACCEPTED)
                else reply(Response.Status.OK, result.toString())
            } catch (_: CancellationException) {
                reply(Response.Status.SERVICE_UNAVAILABLE)
            } catch (_: Exception) {
                reply(Response.Status.BAD_REQUEST)
            }
        }

        private fun validHost(value: String?): Boolean {
            if (value == null || value.any { it.isWhitespace() } || value.contains('@')) return false
            val match = Regex("^(localhost|[0-9.]+|\\[[0-9a-fA-F:]+])(?::([0-9]{1,5}))?$").matchEntire(value) ?: return false
            if (match.groupValues[2].isNotEmpty() && match.groupValues[2].toIntOrNull() != listeningPort) return false
            val host = match.groupValues[1]
            if (host == "localhost") return true
            // Only literal IP input reaches InetAddress: never resolve attacker DNS.
            if (!host.startsWith("[")) {
                val parts = host.split('.')
                if (parts.size != 4 || parts.any { it.isEmpty() || it.length > 3 || it.toIntOrNull() !in 0..255 }) return false
            }
            return try {
                val address = InetAddress.getByName(host.removePrefix("[").removeSuffix("]"))
                !address.isAnyLocalAddress && !address.isMulticastAddress && (lan || address.isLoopbackAddress)
            } catch (_: Exception) { false }
        }
    }

    private fun toolError(message: String): JsonObject = buildJsonObject {
        put("isError", true)
        putJsonArray("content") { add(buildJsonObject { put("type", "text"); put("text", message) }) }
    }

    /** Caps sockets, worker threads and queued work, including slow unauthenticated peers. */
    private class BoundedRunner : NanoHTTPD.AsyncRunner {
        private val clients = ConcurrentHashMap.newKeySet<NanoHTTPD.ClientHandler>()
        private val executor = ThreadPoolExecutor(4, 4, 10, TimeUnit.SECONDS, ArrayBlockingQueue(4),
            { task -> Thread(task, "mcp-http").apply { isDaemon = true } })
        override fun exec(handler: NanoHTTPD.ClientHandler) {
            clients.add(handler)
            try { executor.execute { try { handler.run() } finally { clients.remove(handler) } } }
            catch (_: java.util.concurrent.RejectedExecutionException) { clients.remove(handler); handler.close() }
        }
        override fun closed(handler: NanoHTTPD.ClientHandler) { clients.remove(handler) }
        override fun closeAll() {
            clients.forEach { it.close() }; clients.clear(); executor.shutdownNow()
        }
    }
}
