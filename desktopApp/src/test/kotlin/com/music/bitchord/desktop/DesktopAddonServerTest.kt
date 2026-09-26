package com.music.bitchord.desktop

import com.music.bitchord.data.model.SearchResult
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The addon protocol against a real server: manifest, search and stream, plus the settings
 * passthrough that decides which tier an addon is actually asked for.
 */
class DesktopAddonServerTest {

    @Test
    fun `an addon supplies searchable tracks and a playable stream`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        try {
            val port = server.address.port
            val base = "http://127.0.0.1:$port"
            val searchQuery = AtomicReference<String>()
            val streamQuery = AtomicReference<String>()

            server.json(
                "/manifest.json",
                """
                {"id":"demo","name":"Demo Addon","version":"2.1","resources":["search","stream"],
                 "settings":[{"key":"quality","default":"high",
                   "options":[{"value":"lossless"},{"value":"high"},{"value":"low"}]},
                  {"key":"region","default":"in"}]}
                """.trimIndent(),
            )
            server.jsonCapturing("/search", searchQuery) {
                """
                {"tracks":[{"id":"t/1","title":"Demo Track","artist":"Demo Artist","album":"Demo Album",
                  "duration":245.0,"artworkURL":"$base/art.jpg","format":"flac",
                  "audioQuality":"LOSSLESS"}]}
                """.trimIndent()
            }
            server.jsonCapturing("/stream/t/1", streamQuery) {
                """
                {"url":"$base/audio.flac","format":"flac","quality":"FLAC 24-bit 96 kHz",
                 "codec":"flac","bitrate":2304000,"encrypted":false}
                """.trimIndent()
            }

            val config = DesktopSourceConfig("addon-1", DesktopSourceKind.ADDON, baseUrl = base)

            // Search — the row becomes an ordinary Song addressed to this source.
            val rows = DesktopAddonSource.search(config, "demo", 10).getOrThrow()
            val song = (rows.single() as SearchResult.Track).song
            assertEquals("Demo Track", song.title)
            assertEquals("Demo Artist", song.artist)
            assertEquals("Demo Album", song.albumName)
            assertEquals("4:05", song.durationText)
            assertEquals("LOSSLESS", song.sourceQuality)
            assertEquals("addon-1", DesktopAddonSource.parseTrack(song.videoId)?.sourceId)

            // The declared defaults travel with the request, and `quality` is overridden with the
            // tier actually being asked for.
            assertTrue(searchQuery.get().contains("region=in"), searchQuery.get())
            assertTrue(searchQuery.get().contains("quality=lossless"), searchQuery.get())
            assertTrue(searchQuery.get().contains("q=demo"), searchQuery.get())

            // Stream — metadata is read off the answer, including a bitrate stated in bits per
            // second.
            val stream = DesktopAddonSource.stream(config, song, "LOSSLESS").getOrThrow()
            assertNotNull(stream)
            assertEquals("$base/audio.flac", stream.url)
            assertEquals("flac", stream.format.codec)
            assertEquals(2304, stream.format.kbps)
            assertEquals(96_000, stream.format.sampleRateHz)
            assertEquals(24, stream.format.bitDepth)
            assertEquals("addon-1", stream.sourceId)
            assertTrue(stream.format.isLossless)

            // A LOW request is negotiated down to the addon's own spelling.
            DesktopAddonSource.stream(config, song, "LOW")
            assertTrue(streamQuery.get().contains("quality=low"), streamQuery.get())

            // And the same URL identifies as an addon before it is stored.
            val detected = DesktopSourceFormats.identify(base).getOrThrow()
            assertTrue(detected is DesktopDetectedFormat.Addon)
            assertEquals("Demo Addon", detected.manifest.displayName)
        } finally {
            DesktopAddonSource.forget("addon-1")
            server.stop(0)
        }
    }

    @Test
    fun `a stream miss falls back to the url the search row carried`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        try {
            val port = server.address.port
            val base = "http://127.0.0.1:$port"
            server.json("/manifest.json", """{"id":"rowonly","name":"Row Only","resources":["search"]}""")
            server.json(
                "/search",
                """{"tracks":[{"id":"r1","title":"Row Track","artist":"A","format":"mp3",
                   "streamURL":"$base/row.mp3"}]}""",
            )
            // No /stream context at all: the call 404s, which is the protocol's way of saying this
            // addon does not hold that recording.

            val config = DesktopSourceConfig("addon-row", DesktopSourceKind.ADDON, baseUrl = base)
            val song = (DesktopAddonSource.search(config, "row", 10).getOrThrow().single() as SearchResult.Track).song

            val stream = DesktopAddonSource.stream(config, song, "HIGH").getOrThrow()

            assertNotNull(stream)
            assertEquals("$base/row.mp3", stream.url)
        } finally {
            DesktopAddonSource.forget("addon-row")
            server.stop(0)
        }
    }

    @Test
    fun `an encrypted answer is declined rather than handed to the player`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        try {
            val port = server.address.port
            val base = "http://127.0.0.1:$port"
            server.json("/manifest.json", """{"id":"drm","resources":["search","stream"]}""")
            server.json("/search", """{"tracks":[{"id":"d1","title":"Locked","artist":"A"}]}""")
            server.json("/stream/d1", """{"url":"$base/a.mp4","encrypted":"widevine"}""")

            val config = DesktopSourceConfig("addon-drm", DesktopSourceKind.ADDON, baseUrl = base)
            val song = (DesktopAddonSource.search(config, "locked", 10).getOrThrow().single() as SearchResult.Track).song

            // This app never sends `?drm=`, so an encrypted answer is an addon ignoring the
            // protocol.
            assertNull(DesktopAddonSource.stream(config, song, "HIGH").getOrThrow())
        } finally {
            DesktopAddonSource.forget("addon-drm")
            server.stop(0)
        }
    }

    @Test
    fun `an addon with no manifest is still usable when search answers`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        try {
            val port = server.address.port
            val base = "http://127.0.0.1:$port"
            // No /manifest.json context — the endpoints are the addon, the manifest only describes
            // them.
            server.json("/search", """{"tracks":[]}""")

            val config = DesktopSourceConfig("addon-bare", DesktopSourceKind.ADDON, baseUrl = base)

            assertEquals("No manifest · search works", DesktopAddonSource.health(config).getOrThrow())

            val detected = DesktopSourceFormats.identify(base).getOrThrow()
            assertTrue(detected is DesktopDetectedFormat.Addon)
            assertEquals(base, detected.baseUrl)
        } finally {
            DesktopAddonSource.forget("addon-bare")
            server.stop(0)
        }
    }

    @Test
    fun `an unreachable addon reports why rather than claiming a miss`() = runBlocking {
        val config = DesktopSourceConfig(
            "addon-dead",
            DesktopSourceKind.ADDON,
            // Port 1 is reserved and nothing answers on it.
            baseUrl = "http://127.0.0.1:1",
        )

        val health = DesktopAddonSource.health(config)

        assertTrue(health.isFailure)
        DesktopAddonSource.forget("addon-dead")
    }

    @Test
    fun `a search row keeps whichever artwork field the addon filled in`() {
        assertEquals("a.jpg", DesktopAddonTrack(artworkURL = "a.jpg", albumArtworkURL = "b.jpg").artwork)
        assertEquals("b.jpg", DesktopAddonTrack(albumArtworkURL = "b.jpg").artwork)
        assertNull(DesktopAddonTrack().artwork)
    }

    private fun HttpServer.json(path: String, body: String) {
        createContext(path) { exchange ->
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private fun HttpServer.jsonCapturing(
        path: String,
        query: AtomicReference<String>,
        body: () -> String,
    ) {
        createContext(path) { exchange ->
            query.set(exchange.requestURI.query.orEmpty())
            val bytes = body().toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }
}
