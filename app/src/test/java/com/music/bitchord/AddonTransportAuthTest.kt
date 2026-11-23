package com.music.bitchord

import com.music.bitchord.data.Http
import com.music.bitchord.data.sources.SourceStream
import com.music.bitchord.data.sources.StreamLengthProbe
import com.music.bitchord.data.sources.addon.AddonClient
import com.music.bitchord.data.sources.module.ModuleManager
import com.music.bitchord.data.webdav.WebDavAuth
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonTransportAuthTest {

    @Test
    fun addonRequestsDoNotInheritWebDavCredentials() {
        val server = MockWebServer().apply { start() }
        WebDavAuth.update(server.url("/").host, "Basic dXNlcjpwYXNz")
        try {
            server.enqueue(MockResponse().setBody("{}"))
            AddonClient.client
                .newCall(Request.Builder().url(server.url("/manifest.json")).build())
                .execute()
                .use { assertEquals(200, it.code) }
            assertNull(
                "an addon endpoint must not receive the saved WebDAV Authorization header",
                server.takeRequest().getHeader("Authorization"),
            )

            server.enqueue(MockResponse().setBody("{}"))
            Http.client
                .newCall(Request.Builder().url(server.url("/webdav")).build())
                .execute()
                .use { assertEquals(200, it.code) }
            assertEquals(
                "the WebDAV client must still authenticate its own host",
                "Basic dXNlcjpwYXNz",
                server.takeRequest().getHeader("Authorization"),
            )
        } finally {
            WebDavAuth.update(null, null)
            server.shutdown()
        }
    }

    @Test
    fun moduleIndexRequestsDoNotInheritWebDavCredentials() = runBlocking {
        val server = MockWebServer().apply { start() }
        WebDavAuth.update(server.url("/").host, "Basic dXNlcjpwYXNz")
        try {
            server.enqueue(MockResponse().setBody("""{"category:music":[]}"""))
            val result = ModuleManager().fetchIndex(server.url("/index.json").toString())
            assertTrue(result.isSuccess)
            assertNull(
                "a module index must not receive the saved WebDAV Authorization header",
                server.takeRequest().getHeader("Authorization"),
            )
        } finally {
            WebDavAuth.update(null, null)
            server.shutdown()
        }
    }

    @Test
    fun addonStreamProbeDoesNotInheritWebDavCredentials() = runBlocking {
        val server = MockWebServer().apply { start() }
        WebDavAuth.update(server.url("/").host, "Basic dXNlcjpwYXNz")
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes 0-0/12345"),
            )
            val bytes = StreamLengthProbe.bytesOf(SourceStream(server.url("/stream").toString()))
            assertEquals(12345L, bytes)
            assertNull(
                "an addon stream probe must not receive the saved WebDAV Authorization header",
                server.takeRequest().getHeader("Authorization"),
            )
        } finally {
            WebDavAuth.update(null, null)
            server.shutdown()
        }
    }
}
