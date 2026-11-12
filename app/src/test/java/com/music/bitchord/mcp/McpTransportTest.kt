package com.music.bitchord.mcp

import android.content.ContextWrapper
import fi.iki.elonen.NanoHTTPD
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert.*
import org.junit.Test

class McpTransportTest {
    @Test fun bothCloseApisReallyClose() {
        for (explicit in listOf(false, true)) {
            val server = object : NanoHTTPD("127.0.0.1", 0) {
                override fun serve(session: IHTTPSession): Response =
                    newFixedLengthResponse("ok").apply {
                        if (explicit) closeConnection(true) else addHeader("Connection", "close")
                    }
            }
            server.start(1000, true)
            try {
                Socket("127.0.0.1", server.listeningPort).use { socket ->
                    socket.soTimeout = 1000
                    socket.getOutputStream().write("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
                    assertTrue(socket.getInputStream().readBytes().toString(Charsets.UTF_8).contains("ok"))
                }
            } finally { server.stop() }
        }
    }

    @Test fun unauthenticatedKeepAliveDoesNotExhaustRealServer() {
        val server = McpServer.HttpServer(ContextWrapper(null), 0, false, "x".repeat(43))
        server.start(1000, true)
        try {
            repeat(16) {
                Socket("127.0.0.1", server.listeningPort).use { socket ->
                    socket.soTimeout = 1500
                    socket.getOutputStream().write(("POST /mcp HTTP/1.1\r\nHost: localhost:${server.listeningPort}\r\n" +
                        "Connection: keep-alive\r\nContent-Length: 0\r\n\r\n").toByteArray())
                    val response = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
                    assertTrue(response, response.startsWith("HTTP/1.1 401"))
                }
            }
        } finally { server.stop() }
    }

    @Test fun nonCooperativeBackendCannotPinCallerForever() {
        val executor = McpRequestExecutor(100)
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        val before = System.nanoTime()
        try {
            try {
                executor.execute {
                    started.countDown()
                    while (release.count > 0) {
                        try { release.await(10, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { }
                    }
                }
                fail("Expected timeout")
            } catch (_: TimeoutException) { }
            assertEquals(0L, started.count)
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before) < 1500)
            assertEquals("ping", executor.execute { "ping" })
        } finally { release.countDown(); executor.close() }
    }
}
