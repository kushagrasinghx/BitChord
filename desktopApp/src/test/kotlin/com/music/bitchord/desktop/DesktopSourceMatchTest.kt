package com.music.bitchord.desktop

import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Choosing between the copies one catalogue holds of the same recording: which are tried, in what
 * order, and what happens when the first one cannot be served.
 */
class DesktopSourceMatchTest {

    private fun song(id: String, title: String, artist: String, duration: String, quality: String? = null) =
        Song(
            videoId = id,
            title = title,
            artist = artist,
            thumbnailUrl = "",
            durationText = duration,
            sourceQuality = quality,
        )

    private val target = song("yt1", "Shayar", "Prabhakar Raj", "4:08")

    @Test
    fun `every copy of the recording is ranked, not just the best one`() {
        val candidates = listOf(
            song("a", "Shayar", "Prabhakar Raj", "4:08"),
            song("b", "Shayar", "Prabhakar Raj, LarkZ", "4:07"),
            song("c", "Something Else", "Prabhakar Raj", "4:08"),
        )
        val ranked = DesktopTrackMatcher.ranked(candidates, target)
        assertEquals(listOf("a", "b"), ranked.map { it.videoId })
    }

    @Test
    fun `a lossless copy is opened before a lossy one when lossless is wanted`() {
        val candidates = listOf(
            song("ytm", "Shayar", "Prabhakar Raj", "4:08", "HIGH"),
            song("tidal", "Shayar", "Prabhakar Raj, LarkZ", "4:07", "LOSSLESS"),
            song("js", "Shayar", "Prabhakar Raj, LarkZ", "4:06", "HIGH"),
        )
        val ordered = DesktopSourceRegistry.preferred(candidates, target, wantsLossless = true)
        assertEquals("tidal", ordered.first().videoId)
    }

    @Test
    fun `without a lossless request the matcher's own confidence order stands`() {
        val candidates = listOf(
            song("ytm", "Shayar", "Prabhakar Raj", "4:08", "HIGH"),
            song("tidal", "Shayar", "Prabhakar Raj", "4:08", "LOSSLESS"),
        )
        val ordered = DesktopSourceRegistry.preferred(candidates, target, wantsLossless = false)
        assertEquals("ytm", ordered.first().videoId)
    }

    @Test
    fun `rows that disagree on runtime drop out while any row agrees`() {
        val candidates = listOf(
            song("long", "Shayar", "Prabhakar Raj", "4:30"),
            song("same", "Shayar", "Prabhakar Raj", "4:08"),
        )
        val ordered = DesktopSourceRegistry.preferred(candidates, target, wantsLossless = false)
        assertEquals(listOf("same"), ordered.map { it.videoId })
    }

    @Test
    fun `a free-text Hi-Res label is believed when the addon states no codec`() = runBlocking {
        withAddon(
            search = """
                {"tracks":[{"id":"tidal:1","title":"Shayar","artist":"Prabhakar Raj",
                  "duration":247.0,"audioQuality":"HI_RES_LOSSLESS","format":"dash"}]}
            """.trimIndent(),
            streams = mapOf(
                // What a live Tidal Hi-Res row answers: a manifest URL and words, nothing else.
                "/stream/tidal:1" to (
                    200 to """{"url":"https://im-fa.manifest.tidal.com/1/manifests/abc",
                        "format":"dash","quality":"Tidal · Hi-Res FLAC (DASH)",
                        "streamQuality":"[Tidal] HI_RES_LOSSLESS"}"""
                    ),
            ),
        ) { config ->
            val matched = DesktopAddonSource.matches(config, target).single()
            val stream = DesktopAddonSource.stream(config, matched, "LOSSLESS").getOrThrow()
            assertNotNull(stream)
            assertTrue(stream.format.isLossless, "a Hi-Res FLAC row must not read as lossy")
        }
    }

    @Test
    fun `a copy that will not stream does not write off the whole source`() = runBlocking {
        withAddon(
            search = """
                {"tracks":[{"id":"ytm:1","title":"Shayar","artist":"Prabhakar Raj",
                  "duration":248.0,"audioQuality":"HIGH"},
                 {"id":"tidal:1","title":"Shayar","artist":"Prabhakar Raj",
                  "duration":247.0,"audioQuality":"LOSSLESS"}]}
            """.trimIndent(),
            streams = mapOf(
                "/stream/ytm:1" to (502 to """{"error":"upstream failed"}"""),
                "/stream/tidal:1" to (
                    200 to """{"url":"https://example.invalid/a.flac","codec":"flac",
                        "quality":"FLAC 24-bit 96 kHz","encrypted":"none"}"""
                    ),
            ),
        ) { config ->
            val ordered = DesktopSourceRegistry.preferred(
                DesktopAddonSource.matches(config, target),
                target,
                wantsLossless = true,
            )
            // The lossless row leads, and the broken one no longer stands in front of it.
            assertEquals("tidal:1", DesktopAddonSource.parseTrack(ordered.first().videoId)?.trackId)
            val stream = DesktopAddonSource.stream(config, ordered.first(), "LOSSLESS").getOrThrow()
            assertNotNull(stream)
            assertTrue(stream.format.isLossless)
        }
    }

    @Test
    fun `an addon saying encrypted none is not saying DRM`() = runBlocking {
        withAddon(
            search = """
                {"tracks":[{"id":"t1","title":"Shayar","artist":"Prabhakar Raj",
                  "duration":248.0,"audioQuality":"LOSSLESS"}]}
            """.trimIndent(),
            streams = mapOf(
                "/stream/t1" to (
                    200 to """{"url":"https://example.invalid/a.flac","codec":"flac","encrypted":"none"}"""
                    ),
            ),
        ) { config ->
            val matched = DesktopAddonSource.matches(config, target).single()
            assertNotNull(DesktopAddonSource.stream(config, matched, "LOSSLESS").getOrThrow())
        }
    }

    private suspend fun withAddon(
        search: String,
        streams: Map<String, Pair<Int, String>>,
        block: suspend (DesktopSourceConfig) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            server.respond("/manifest.json", 200, """{"id":"demo","name":"Demo","resources":["search","stream"]}""")
            server.respond("/search", 200, search)
            streams.forEach { (path, answer) -> server.respond(path, answer.first, answer.second) }
            block(DesktopSourceConfig("addon-match", DesktopSourceKind.ADDON, baseUrl = base))
        } finally {
            server.stop(0)
        }
    }

    private fun HttpServer.respond(path: String, code: Int, body: String) {
        createContext(path) { exchange ->
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }
}
