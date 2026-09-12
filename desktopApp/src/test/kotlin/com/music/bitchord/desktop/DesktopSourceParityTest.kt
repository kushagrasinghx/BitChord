package com.music.bitchord.desktop

import com.music.bitchord.data.model.SearchResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.atomic.AtomicReference

class DesktopSourceParityTest {
    @Test
    fun `module track ids retain the configured source`() {
        assertEquals(
            DesktopModuleSource.ModuleTrackRef("custom-id", "tidal", "track-42"),
            DesktopModuleSource.parseTrack("module:custom-id::tidal::track-42"),
        )
    }

    @Test
    fun `legacy module track ids remain readable`() {
        assertEquals(
            DesktopModuleSource.ModuleTrackRef(null, "tidal", "track-42"),
            DesktopModuleSource.parseTrack("module:tidal::track-42"),
        )
    }

    @Test
    fun `malformed module track ids are rejected`() {
        assertNull(DesktopModuleSource.parseTrack("module:"))
        assertNull(DesktopModuleSource.parseTrack("youtube-video-id"))
    }

    @Test
    fun `source kinds follow android priority`() {
        assertEquals(
            listOf(
                DesktopSourceKind.ADDON,
                DesktopSourceKind.CUSTOM_MODULE,
                DesktopSourceKind.MODULE,
                DesktopSourceKind.JIOSAAVN,
                DesktopSourceKind.YOUTUBE,
            ),
            DesktopSourceKind.entries,
        )
        // The walk is ranked rather than declared.
        assertEquals(
            listOf(0, 0, 1, 2, 3),
            DesktopSourceKind.entries.map { it.rank },
        )
    }

    @Test
    fun `module search and stream use the configured entry and requested tier`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        try {
            val port = server.address.port
            val streamUrl = "http://127.0.0.1:$port/audio.mp4"
            server.json("/index.json", """
                {"category:music":[{"id":"fake","name":"Fake Module","download":"module.js"}]}
            """.trimIndent())
            server.json("/catalog.json", """
                {"tracks":[{"id":"track","title":"Demo","artist":"Artist"}],"total":1}
            """.trimIndent())
            server.text("/module.js", """
                module.exports = {
                  searchTracks: function() {
                    return fetch("catalog.json").then(function(response) { return response.json(); });
                  },
                  getTrackStreamUrl: function(id, quality) {
                    return {streamUrl: "http://127.0.0.1:$port/audio-" + quality + ".mp4", track: {mimeType: "audio/mp4", audioQuality: quality}};
                  }
                };
            """.trimIndent())
            server.text("/audio.mp4", "not-a-real-audio-file")

            val config = DesktopSourceConfig(
                id = "custom-test",
                kind = DesktopSourceKind.CUSTOM_MODULE,
                label = "Test source",
                baseUrl = "http://127.0.0.1:$port/index.json",
            )
            val result = DesktopModuleSource.search(config, "demo", 5)
                .getOrThrow()
                .single() as SearchResult.Track
            assertEquals("module:custom-test::fake::track", result.song.videoId)

            val stream = assertNotNull(DesktopModuleSource.stream(config, result.song, "HIGH").getOrThrow())
            assertEquals("$streamUrl".replace("audio.mp4", "audio-HIGH.mp4"), stream.url)
            assertEquals("mp4", stream.format.codec)
            assertEquals("custom-test", stream.sourceId)
        } finally {
            DesktopModuleSource.close()
            server.stop(0)
        }
    }

    private fun HttpServer.text(path: String, body: String) {
        createContext(path) { exchange ->
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private fun HttpServer.json(path: String, body: String) = text(path, body)
}
