package com.music.bitchord

import com.music.bitchord.data.sources.addon.AddonClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * [AddonClient.manifestForRequest]: a request never waits for a manifest once
 * one has been read, and an expired one is refreshed beside it.
 */
class AddonManifestCacheTest {

    private lateinit var server: MockWebServer
    private val manifestReads = AtomicInteger()

    @Volatile
    private var manifestName = "First"

    private var clockMs = 1_000_000L

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl?.encodedPath != "/manifest.json") return MockResponse().setResponseCode(404)
                manifestReads.incrementAndGet()
                return MockResponse().setBody(
                    """{"id":"test.addon","name":"$manifestName","version":"1.0.0","resources":["search","stream"]}""",
                )
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client() = AddonClient(server.url("/").toString(), now = { clockMs })

    @Test
    fun `a request reads the manifest once and then from memory`() = runBlocking {
        val client = client()
        assertEquals("First", client.manifestForRequest().getOrThrow().name)
        assertEquals("First", client.manifestForRequest().getOrThrow().name)
        assertEquals(1, manifestReads.get())
    }

    @Test
    fun `an expired manifest answers at once and is refreshed for the next request`() = runBlocking {
        val client = client()
        client.manifestForRequest().getOrThrow()
        manifestName = "Second"
        clockMs += AddonClient.MANIFEST_TTL_MS + 1

        // The last copy, without waiting for the refresh it starts.
        assertEquals("First", client.manifestForRequest().getOrThrow().name)
        val deadline = System.currentTimeMillis() + 5_000
        while (manifestReads.get() < 2 && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(2, manifestReads.get())
        // Give the refresh a moment to store what it read.
        val stored = System.currentTimeMillis() + 5_000
        while (client.manifestForRequest().getOrThrow().name != "Second" && System.currentTimeMillis() < stored) {
            Thread.sleep(20)
        }
        assertEquals("Second", client.manifestForRequest().getOrThrow().name)
        assertEquals(2, manifestReads.get())
    }

    @Test
    fun `the sources screen's manifest read is still a fresh one once the copy expires`() = runBlocking {
        val client = client()
        client.manifest().getOrThrow()
        manifestName = "Second"
        clockMs += AddonClient.MANIFEST_TTL_MS + 1
        assertEquals("Second", client.manifest().getOrThrow().name)
        assertEquals(2, manifestReads.get())
    }
}
