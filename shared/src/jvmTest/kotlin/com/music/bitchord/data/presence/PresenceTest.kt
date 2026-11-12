package com.music.bitchord.data.presence

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PresenceTest {
    private lateinit var server: MockWebServer
    private val id = "3f2b8c1e-9a4d-4e6f-8b7a-1c2d3e4f5a6b"

    @BeforeTest
    fun start() {
        server = MockWebServer()
        repeat(10) { server.enqueue(MockResponse().setBody("""{"intervalSec":300}""")) }
        server.start()
    }

    @AfterTest
    fun stop() = server.shutdown()

    private fun nextPing(timeoutSec: Long = 5): JsonObject? {
        val request = server.takeRequest(timeoutSec, TimeUnit.SECONDS) ?: return null
        assertEquals("POST", request.method)
        assertEquals("/api/presence", request.path)
        assertNull(request.getHeader("Origin"), "the server refuses pings that carry an Origin")
        return Json.parseToJsonElement(request.body.readUtf8()).jsonObject
    }

    private fun JsonObject.isOpen() = getValue("open").jsonPrimitive.boolean

    @Test
    fun opensThenClosesAfterTheDebounce() {
        val presence = Presence(PresencePlatform.Pc, id, server.url("/api/presence").toString())
        presence.setOpen(true)
        val open = nextPing()!!
        assertEquals(id, open.getValue("id").jsonPrimitive.content)
        assertEquals("pc", open.getValue("platform").jsonPrimitive.content)
        assertEquals(true, open.isOpen())

        presence.setOpen(false)
        // Held back for the debounce, then sent.
        assertNull(nextPing(timeoutSec = 1), "close should wait out the debounce")
        assertEquals(false, nextPing()!!.isOpen())
    }

    @Test
    fun aQuickReopenIsNotAClose() {
        val presence = Presence(PresencePlatform.Android, id, server.url("/api/presence").toString())
        presence.setOpen(true)
        assertEquals("android", nextPing()!!.getValue("platform").jsonPrimitive.content)

        presence.setOpen(false)
        Thread.sleep(300)
        presence.setOpen(true)
        // The reopen pings straight away; the close it replaced never goes out.
        assertEquals(true, nextPing()!!.isOpen())
        assertNull(nextPing(timeoutSec = 3))
    }

    @Test
    fun neverOpenedSendsNothing() {
        val presence = Presence(PresencePlatform.Pc, id, server.url("/api/presence").toString())
        presence.setOpen(false)
        presence.closeBlocking()
        assertNull(nextPing(timeoutSec = 3))
    }

    @Test
    fun closeBlockingSendsOnTheCallingThread() {
        val presence = Presence(PresencePlatform.Pc, id, server.url("/api/presence").toString())
        presence.setOpen(true)
        nextPing()!!
        presence.closeBlocking()
        // Already sent by the time closeBlocking returns: no waiting.
        assertEquals(false, nextPing(timeoutSec = 0)!!.isOpen())
    }
}
