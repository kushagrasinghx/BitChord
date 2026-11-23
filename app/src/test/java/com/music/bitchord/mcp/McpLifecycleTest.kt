package com.music.bitchord.mcp

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class McpLifecycleTest {
    @Test fun failureSurvivesQueuedStopAndCanBeRetried(): Unit = runBlocking {
        try {
            repeat(100) { attempt ->
                McpServer.stop()
                val expected = "Unable to start server: promotion rejected $attempt"
                McpServer.failStart("promotion rejected $attempt")
                withTimeout(5000) { McpServer.status.first { it == expected } }
                assertEquals(expected, McpServer.status.value)
            }
            McpServer.stop()
            withTimeout(5000) { McpServer.status.first { it == "Stopped" } }
        } finally { McpServer.stop() }
    }
}
