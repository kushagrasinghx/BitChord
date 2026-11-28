package com.music.bitchord.data.proxy

import com.music.bitchord.data.Http
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Routing is the part that cannot be checked by reading a settings screen: a
 * request either went through the proxy the user chose or it did not.
 */
class ProxyRouterTest {

    private val global = ProxyConfig(
        enabled = true,
        protocol = ProxyProtocol.HTTP,
        host = "192.0.2.10",
        port = 8080,
    )

    private val custom = ProxyConfig(
        enabled = true,
        protocol = ProxyProtocol.SOCKS5,
        host = "198.51.100.7",
        port = 1080,
    )

    @BeforeTest
    fun resetRouter() {
        ProxyRouter.setTemporaryAuth(null)
        ProxyRouter.setGlobal(ProxyConfig())
        ProxyRouter.setSourceRoutes(emptyList())
    }

    @AfterTest
    fun resetRouterAgain() {
        ProxyRouter.setTemporaryAuth(null)
        ProxyRouter.setGlobal(ProxyConfig())
        ProxyRouter.setSourceRoutes(emptyList())
        Http.evictConnections()
    }

    @Test
    fun `no proxy configured means a direct connection`() {
        assertDirect(ProxyRouter.proxyFor(URI("https://music.example/album")))
    }

    @Test
    fun `the global proxy applies to every host`() {
        ProxyRouter.setGlobal(global)
        assertProxy(ProxyRouter.proxyFor(URI("https://music.example/album")), global)
        assertProxy(ProxyRouter.proxyFor(URI("http://another.example/")), global)
    }

    @Test
    fun `a disabled proxy is a direct connection even when it has an endpoint`() {
        ProxyRouter.setGlobal(global.copy(enabled = false))
        assertDirect(ProxyRouter.proxyFor(URI("https://music.example/album")))
    }

    @Test
    fun `an enabled but invalid proxy fails closed`() {
        ProxyRouter.setGlobal(global.copy(host = "bad host"))
        assertFailsWith<IllegalStateException> {
            ProxyRouter.proxyFor(URI("https://music.example/album"))
        }
    }

    @Test
    fun `a source can inherit, bypass, or replace the global proxy`() {
        ProxyRouter.setGlobal(global)
        ProxyRouter.setSourceRoutes(
            listOf(
                SourceRoute("inherit", "inherit.example", ProxyMode.INHERIT, ProxyConfig()),
                SourceRoute("direct", "direct.example", ProxyMode.DIRECT, ProxyConfig()),
                SourceRoute("custom", "custom.example", ProxyMode.CUSTOM, custom),
            ),
        )

        assertProxy(ProxyRouter.proxyFor(URI("https://inherit.example/")), global)
        assertDirect(ProxyRouter.proxyFor(URI("https://direct.example/")))
        assertProxy(ProxyRouter.proxyFor(URI("https://custom.example/")), custom)
        // Anything not claimed by a source still follows the app setting.
        assertProxy(ProxyRouter.proxyFor(URI("https://unrelated.example/")), global)
    }

    @Test
    fun `a stream host learned from a source keeps that source's route`() {
        ProxyRouter.setSourceRoutes(
            listOf(SourceRoute("tidal", "tidal.example", ProxyMode.CUSTOM, custom)),
        )

        ProxyRouter.rememberStreamHost("tidal", "https://cdn.tidal.example/audio/1.flac")

        assertProxy(ProxyRouter.proxyFor(URI("https://cdn.tidal.example/audio/1.flac")), custom)
        assertProxy(ProxyRouter.proxyFor(URI("https://tidal.example/")), custom)
        assertDirect(ProxyRouter.proxyFor(URI("https://youtube.example/")))
    }

    @Test
    fun `an unknown source cannot add a stream route`() {
        ProxyRouter.rememberStreamHost("missing", "https://cdn.example/audio")
        assertDirect(ProxyRouter.proxyFor(URI("https://cdn.example/audio")))
    }

    @Test
    fun `a scoped source routes a host it has not registered`() {
        ProxyRouter.setGlobal(global)
        ProxyRouter.setSourceRoutes(
            listOf(
                SourceRoute("tidal", "tidal.example", ProxyMode.CUSTOM, custom),
                SourceRoute("qobuz", "qobuz.example", ProxyMode.DIRECT, ProxyConfig()),
            ),
        )

        // A redirect inside a module fetch names a host the registry has never
        // seen; the call's own source still has to decide the route.
        ProxyRouter.withSource("tidal") {
            assertProxy(ProxyRouter.proxyFor(URI("https://redirect.example/")), custom)
        }
        ProxyRouter.withSource("qobuz") {
            assertDirect(ProxyRouter.proxyFor(URI("https://redirect.example/")))
        }
        // The scope ends with the call, so the same host follows the global
        // setting again rather than keeping whichever source ran last.
        assertProxy(ProxyRouter.proxyFor(URI("https://redirect.example/")), global)
    }

    @Test
    fun `host matching ignores case and IPv6 brackets`() {
        assertEquals("cdn.example.com", ProxyRouter.hostOf("https://CDN.Example.COM/a"))
        assertEquals("2001:db8::1", ProxyRouter.hostOf("https://[2001:db8::1]/a"))
        assertNull(ProxyRouter.hostOf("not a url"))
    }

    @Test
    fun `replacing the same routes reports no change`() {
        val routes = listOf(SourceRoute("a", "a.example", ProxyMode.DIRECT, ProxyConfig()))
        assertTrue(ProxyRouter.setSourceRoutes(routes))
        assertFalse(ProxyRouter.setSourceRoutes(routes))
    }

    @Test
    fun `an unrelated publish keeps a learned stream host`() {
        val routes = listOf(SourceRoute("tidal", "tidal.example", ProxyMode.CUSTOM, custom))
        ProxyRouter.setSourceRoutes(routes)
        ProxyRouter.rememberStreamHost("tidal", "https://cdn.tidal.example/audio/1.flac")

        // A manifest refresh republishes the same base routes. It must not
        // erase the CDN route the source already taught the router.
        assertFalse(ProxyRouter.setSourceRoutes(routes))
        assertProxy(ProxyRouter.proxyFor(URI("https://cdn.tidal.example/audio/1.flac")), custom)
    }

    @Test
    fun `changing a route forgets the old stream hosts`() {
        val before = listOf(SourceRoute("tidal", "tidal.example", ProxyMode.CUSTOM, custom))
        ProxyRouter.setSourceRoutes(before)
        ProxyRouter.rememberStreamHost("tidal", "https://cdn.tidal.example/audio/1.flac")

        val after = listOf(SourceRoute("tidal", "tidal.example", ProxyMode.DIRECT, ProxyConfig()))
        assertTrue(ProxyRouter.setSourceRoutes(after))
        assertDirect(ProxyRouter.proxyFor(URI("https://cdn.tidal.example/audio/1.flac")))
    }

    @Test
    fun `an invalid custom route fails closed instead of falling back`() {
        ProxyRouter.setGlobal(global)
        ProxyRouter.setSourceRoutes(
            listOf(
                SourceRoute(
                    "broken",
                    "broken.example",
                    ProxyMode.CUSTOM,
                    ProxyConfig(enabled = true, host = "", port = 0),
                ),
            ),
        )
        assertFailsWith<IllegalStateException> {
            ProxyRouter.proxyFor(URI("https://broken.example/"))
        }
    }

    @Test
    fun `http proxy credentials answer a 407 once`() {
        val server = MockWebServer()
        var calls = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                calls++
                return if (request.getHeader("Proxy-Authorization") == null) {
                    MockResponse()
                        .setResponseCode(407)
                        .setHeader("Proxy-Authenticate", "Basic realm=\"test\"")
                } else {
                    MockResponse().setResponseCode(200).setBody("ok")
                }
            }
        }
        server.start()
        try {
            ProxyRouter.setGlobal(
                ProxyConfig(
                    enabled = true,
                    host = server.url("/").host,
                    port = server.url("/").port,
                    username = "alice",
                    password = "hunter2",
                ),
            )
            Http.evictConnections()

            Http.client.newCall(Request.Builder().url("http://target.invalid/test").build())
                .execute()
                .use { response ->
                    assertEquals(200, response.code)
                }

            assertEquals(2, calls)
            val first = server.takeRequest()
            val second = server.takeRequest()
            assertNull(first.getHeader("Proxy-Authorization"))
            assertEquals(
                Credentials.basic("alice", "hunter2"),
                second.getHeader("Proxy-Authorization"),
            )
        } finally {
            server.shutdown()
            ProxyRouter.setGlobal(ProxyConfig())
            Http.evictConnections()
        }
    }

    @Test
    fun `a source's credentials win at a proxy the app also uses`() {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.getHeader("Proxy-Authorization") == Credentials.basic("source", "source-pass")) {
                    MockResponse().setResponseCode(200)
                } else {
                    MockResponse()
                        .setResponseCode(407)
                        .setHeader("Proxy-Authenticate", "Basic realm=\"test\"")
                }
        }
        server.start()
        try {
            val endpoint = ProxyConfig(
                enabled = true,
                host = server.url("/").host,
                port = server.url("/").port,
            )
            ProxyRouter.setGlobal(
                endpoint.copy(username = "global", password = "global-pass"),
            )
            ProxyRouter.setSourceRoutes(
                listOf(
                    SourceRoute(
                        "source",
                        "source.example",
                        ProxyMode.CUSTOM,
                        endpoint.copy(username = "source", password = "source-pass"),
                    ),
                ),
            )
            Http.evictConnections()

            Http.client.newCall(Request.Builder().url("http://source.example/test").build())
                .execute()
                .use { response ->
                    assertEquals(200, response.code)
                }
        } finally {
            server.shutdown()
            ProxyRouter.setGlobal(ProxyConfig())
            ProxyRouter.setSourceRoutes(emptyList())
            Http.evictConnections()
        }
    }

    @Test
    fun `a custom route with a disabled endpoint fails closed`() {
        ProxyRouter.setSourceRoutes(
            listOf(
                SourceRoute(
                    "disabled",
                    "disabled.example",
                    ProxyMode.CUSTOM,
                    ProxyConfig(enabled = false, host = "127.0.0.1", port = 8080),
                ),
            ),
        )
        assertFailsWith<IllegalStateException> {
            ProxyRouter.proxyFor(URI("https://disabled.example/"))
        }
    }

    @Test
    fun `the tester can use a proxy without changing the app's global route`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(204))
        server.start()
        try {
            val result = runBlocking {
                ProxyTester.test(
                    ProxyConfig(
                        enabled = true,
                        host = server.url("/").host,
                        port = server.url("/").port,
                    ),
                    targetUrl = "http://target.invalid/",
                )
            }
            assertTrue(result.isSuccess, result.exceptionOrNull()?.message)
            assertDirect(ProxyRouter.proxyFor(URI("https://music.example/")))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `the tester reports a rejected proxy login`() {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse()
                    .setResponseCode(407)
                    .setHeader("Proxy-Authenticate", "Basic realm=\"test\"")
        }
        server.start()
        try {
            val result = runBlocking {
                ProxyTester.test(
                    ProxyConfig(
                        enabled = true,
                        host = server.url("/").host,
                        port = server.url("/").port,
                        username = "alice",
                        password = "wrong",
                    ),
                    targetUrl = "http://target.invalid/",
                )
            }
            assertTrue(result.isFailure)
            assertTrue(
                result.exceptionOrNull()?.message.orEmpty().contains("authentication", ignoreCase = true),
                result.exceptionOrNull()?.message,
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `the tester connects directly when there is no proxy`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(204))
        server.start()
        try {
            val result = runBlocking { ProxyTester.test(null, server.url("/").toString()) }
            assertTrue(result.isSuccess, result.exceptionOrNull()?.message)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `the tester treats a disabled endpoint as direct`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(204))
        server.start()
        try {
            val result = runBlocking {
                ProxyTester.test(
                    ProxyConfig(enabled = false, host = "bad host", port = 70_000),
                    targetUrl = server.url("/").toString(),
                )
            }
            assertTrue(result.isSuccess, result.exceptionOrNull()?.message)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `the tester rejects an invalid endpoint before opening a socket`() {
        val result = runBlocking {
            ProxyTester.test(ProxyConfig(enabled = true, host = "bad host", port = 8080))
        }
        assertTrue(result.isFailure)
        assertEquals("HOST_INVALID", result.exceptionOrNull()?.message)
    }

    @Test
    fun `the tester reports an unavailable proxy`() {
        val port = ServerSocket(0).use { it.localPort }
        val result = runBlocking {
            ProxyTester.test(
                ProxyConfig(enabled = true, host = "127.0.0.1", port = port),
                targetUrl = "http://music.example/",
            )
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun `socks5 carries a request without authentication`() {
        val target = MockWebServer()
        target.enqueue(MockResponse().setResponseCode(204))
        target.start()
        TestSocks5Proxy().use { socks ->
            try {
                val result = runBlocking {
                    ProxyTester.test(
                        ProxyConfig(
                            enabled = true,
                            protocol = ProxyProtocol.SOCKS5,
                            host = "127.0.0.1",
                            port = socks.port,
                        ),
                        targetUrl = target.url("/").toString(),
                    )
                }
                assertTrue(result.isSuccess, result.exceptionOrNull()?.message)
            } finally {
                target.shutdown()
            }
        }
    }

    @Test
    fun `socks5 username and password reach the proxy`() {
        val target = MockWebServer()
        target.enqueue(MockResponse().setResponseCode(204))
        target.start()
        TestSocks5Proxy(username = "alice", password = "hunter2").use { socks ->
            try {
                val result = runBlocking {
                    ProxyTester.test(
                        ProxyConfig(
                            enabled = true,
                            protocol = ProxyProtocol.SOCKS5,
                            host = "127.0.0.1",
                            port = socks.port,
                            username = "alice",
                            password = "hunter2",
                        ),
                        targetUrl = target.url("/").toString(),
                    )
                }
                assertTrue(result.isSuccess, result.exceptionOrNull()?.message)
                assertTrue(socks.sawCredentials.await(5, TimeUnit.SECONDS))
                assertEquals("alice" to "hunter2", socks.credentials)
            } finally {
                target.shutdown()
            }
        }
    }

    private fun assertDirect(proxy: Proxy) {
        assertEquals(Proxy.Type.DIRECT, proxy.type())
    }

    private fun assertProxy(proxy: Proxy, expected: ProxyConfig) {
        assertEquals(expected.toJavaProxy().type(), proxy.type())
        val address = proxy.address() as InetSocketAddress
        assertEquals(expected.host, address.hostString)
        assertEquals(expected.port, address.port)
    }
}

/**
 * A SOCKS5 endpoint small enough to test the client rather than a proxy
 * product. It performs the greeting, optional username/password exchange,
 * CONNECT, and then copies bytes both ways.
 */
private class TestSocks5Proxy(
    private val username: String? = null,
    private val password: String? = null,
) : AutoCloseable {

    private val server = ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"))
    private val acceptor = thread(isDaemon = true, name = "test-socks-acceptor") { accept() }

    val port: Int get() = server.localPort
    val sawCredentials = CountDownLatch(1)

    @Volatile
    var credentials: Pair<String, String>? = null

    private fun accept() {
        while (!server.isClosed) {
            val socket = runCatching { server.accept() }.getOrNull() ?: return
            thread(isDaemon = true, name = "test-socks-connection") { handle(socket) }
        }
    }

    private fun handle(socket: Socket) {
        try {
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())

            if (input.readUnsignedByte() != 5) return
            val methodCount = input.readUnsignedByte()
            val methods = ByteArray(methodCount).also { input.readFully(it) }

            if (username != null) {
                if (methods.none { it.toInt() == 2 }) {
                    output.write(byteArrayOf(5, 0xFF.toByte()))
                    output.flush()
                    return
                }
                output.write(byteArrayOf(5, 2))
                output.flush()

                val authVersion = input.readUnsignedByte()
                val user = input.readSocksString()
                val pass = input.readSocksString()
                credentials = user to pass
                sawCredentials.countDown()
                if (authVersion != 1 || user != username || pass != password) {
                    output.write(byteArrayOf(1, 1))
                    output.flush()
                    return
                }
                output.write(byteArrayOf(1, 0))
                output.flush()
            } else {
                output.write(byteArrayOf(5, 0))
                output.flush()
            }

            if (input.readUnsignedByte() != 5) return
            if (input.readUnsignedByte() != 1) return // CONNECT only
            input.readUnsignedByte() // reserved

            val host = when (input.readUnsignedByte()) {
                1 -> InetAddress.getByAddress(ByteArray(4).also(input::readFully)).hostAddress
                3 -> String(ByteArray(input.readUnsignedByte()).also(input::readFully), Charsets.UTF_8)
                4 -> InetAddress.getByAddress(ByteArray(16).also(input::readFully)).hostAddress
                else -> return
            }
            val targetPort = input.readUnsignedShort()

            val target = Socket()
            target.connect(InetSocketAddress(host, targetPort), 2_000)
            output.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
            output.flush()

            val upstream = thread(isDaemon = true) {
                runCatching { socket.getInputStream().copyTo(target.getOutputStream()) }
                runCatching { target.shutdownOutput() }
            }
            val downstream = thread(isDaemon = true) {
                runCatching { target.getInputStream().copyTo(socket.getOutputStream()) }
                runCatching { socket.shutdownOutput() }
            }
            upstream.join(5_000)
            downstream.join(5_000)
            runCatching { target.close() }
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun DataInputStream.readSocksString(): String {
        val length = readUnsignedByte()
        return String(ByteArray(length).also { readFully(it) }, Charsets.UTF_8)
    }

    override fun close() {
        runCatching { server.close() }
        acceptor.join(1_000)
    }
}
