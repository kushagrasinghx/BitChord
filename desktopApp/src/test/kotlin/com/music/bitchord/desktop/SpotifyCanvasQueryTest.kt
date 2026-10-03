package com.music.bitchord.desktop

import com.music.bitchord.data.canvas.SpotifyCanvasQuery
import com.music.bitchord.data.canvas.SpotifyCanvasQuery.Answer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Pathfinder `canvas` query the Spotify source now asks first (shared by the phone and the
 * desktop): the request it sends, every shape of answer it has to read, and how it keeps the
 * query's hash current.
 */
class SpotifyCanvasQueryTest {

    private val uri = "spotify:track:4cOdK2wGLETKBW3PvgPWqT"
    private val hashA = "a".repeat(64)
    private val hashB = "b".repeat(64)

    @Test
    fun `the request names the canvas operation, the track and the hash`() {
        val body = Json.parseToJsonElement(SpotifyCanvasQuery.requestBody(uri, hashA)).jsonObject
        assertEquals("canvas", body["operationName"]!!.jsonPrimitive.content)
        // `trackUri`, not the older `uri` variable: the query ignores the wrong name and answers
        // with no canvas, which looks exactly like a track that has none.
        assertEquals(uri, body["variables"]!!.jsonObject["trackUri"]!!.jsonPrimitive.content)
        val persisted = body["extensions"]!!.jsonObject["persistedQuery"]!!.jsonObject
        assertEquals(1, persisted["version"]!!.jsonPrimitive.int)
        assertEquals(hashA, persisted["sha256Hash"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a canvas is read from data trackUnion canvas url`() {
        val answer = SpotifyCanvasQuery.parse(
            """{"data":{"trackUnion":{"__typename":"Track","canvas":{"type":"VIDEO_LOOPING","uri":"spotify:canvas:x","url":"https://canvaz.scdn.co/upload/artist/x/video/y.cnvs.mp4"}}}}""",
        )
        assertEquals(Answer.Found("https://canvaz.scdn.co/upload/artist/x/video/y.cnvs.mp4", "VIDEO_LOOPING"), answer)
    }

    @Test
    fun `a track without a canvas is a final answer, not a failure`() {
        val nullCanvas = assertIs<Answer.NoCanvas>(
            SpotifyCanvasQuery.parse("""{"data":{"trackUnion":{"__typename":"Track","canvas":null}}}"""),
        )
        assertEquals("canvas null", nullCanvas.detail)

        val missingCanvas = assertIs<Answer.NoCanvas>(
            SpotifyCanvasQuery.parse("""{"data":{"trackUnion":{"__typename":"Track"}}}"""),
        )
        assertEquals("canvas null", missingCanvas.detail)

        val missingTrack = assertIs<Answer.NoCanvas>(
            SpotifyCanvasQuery.parse("""{"data":{"trackUnion":null}}"""),
        )
        assertEquals("canvas null", missingTrack.detail)
    }

    @Test
    fun `an image canvas or a link that is not https is not played`() {
        val image = assertIs<Answer.NoCanvas>(
            SpotifyCanvasQuery.parse("""{"data":{"trackUnion":{"canvas":{"type":"IMAGE","url":"https://canvaz.scdn.co/upload/x.jpg"}}}}"""),
        )
        assertTrue(image.detail.contains("type=IMAGE"))

        val insecure = assertIs<Answer.NoCanvas>(
            SpotifyCanvasQuery.parse("""{"data":{"trackUnion":{"canvas":{"url":"http://canvaz.scdn.co/x.cnvs.mp4"}}}}"""),
        )
        assertTrue(insecure.detail.contains("url=http://"))

        val fileIdVideo = assertIs<Answer.NoCanvas>(
            SpotifyCanvasQuery.parse("""{"data":{"trackUnion":{"canvas":{"type":"VIDEO_LOOPING","fileId":"abc123"}}}}"""),
        )
        assertTrue(fileIdVideo.detail.contains("fileId=abc123"))
    }

    @Test
    fun `a stale hash is a failure that says the hash should be looked up again`() {
        val answer = SpotifyCanvasQuery.parse("""{"errors":[{"message":"PersistedQueryNotFound"}]}""")
        assertIs<Answer.Failed>(answer)
        assertTrue(answer.staleHash)
    }

    @Test
    fun `other failures fall back without blaming the hash`() {
        val errored = SpotifyCanvasQuery.parse("""{"errors":[{"message":"Something else"}],"data":null}""")
        assertIs<Answer.Failed>(errored)
        assertEquals(false, errored.staleHash)
        assertIs<Answer.Failed>(SpotifyCanvasQuery.parse("<html>rate limited</html>"))
        assertIs<Answer.Failed>(SpotifyCanvasQuery.parse(""))
    }

    @Test
    fun `the hash is found where the web player declares its operations`() {
        val bundle = """var a=new o.l("searchTracks","query","$hashB",null),c=new o.l("canvas","query","$hashA",null);"""
        assertEquals(hashA, SpotifyCanvasQuery.findQueryHash(bundle))
        assertEquals(hashB, SpotifyCanvasQuery.findQueryHash(bundle, "searchTracks"))
        // A longer operation name that merely starts with "canvas" is someone else's query.
        assertNull(SpotifyCanvasQuery.findQueryHash("""new o.l("canvasMeta","query","$hashA",null)"""))
        assertNull(SpotifyCanvasQuery.findQueryHash("no declarations here"))
    }

    @Test
    fun `only the web player's own bundles are fetched from the page`() {
        val html = """
            <script src="https://open.spotifycdn.com/cdn/build/web-player/vendor~web-player.1a2b3c.js"></script>
            <script src="https://open.spotifycdn.com/cdn/build/web-player/web-player.4d5e6f.js"></script>
            <script src="https://open.spotifycdn.com/cdn/build/web-player/web-player.4d5e6f.js"></script>
            <script src="https://www.googletagmanager.com/gtm.js"></script>
        """
        assertEquals(
            listOf(
                "https://open.spotifycdn.com/cdn/build/web-player/vendor~web-player.1a2b3c.js",
                "https://open.spotifycdn.com/cdn/build/web-player/web-player.4d5e6f.js",
            ),
            SpotifyCanvasQuery.webPlayerScripts(html),
        )
    }

    @Test
    fun `the live hash is read off the player's scripts and remembered`() {
        val page = """<script src="https://open.spotifycdn.com/cdn/build/web-player/web-player.1.js"></script>"""
        val fetched = mutableListOf<String>()
        val hashes = SpotifyCanvasQuery.QueryHashes(fetch = { url ->
            fetched += url
            when {
                url == "https://open.spotify.com/" -> page
                url.endsWith("web-player.1.js") -> """x=new o.l("canvas","query","$hashB",null)"""
                else -> null
            }
        })
        assertEquals(hashB, hashes.canvasHash())
        assertEquals(hashB, hashes.canvasHash())
        assertEquals(2, fetched.size, "the second call is served from memory")
    }

    @Test
    fun `without a readable player the known hash is used, and not looked up on every track`() {
        var now = 0L
        var calls = 0
        val hashes = SpotifyCanvasQuery.QueryHashes(fetch = { calls++; null }, now = { now })
        now = 1_000
        assertEquals(SpotifyCanvasQuery.KNOWN_CANVAS_HASH, hashes.canvasHash())
        assertEquals(SpotifyCanvasQuery.KNOWN_CANVAS_HASH, hashes.canvasHash())
        assertEquals(1, calls, "a failed lookup backs off")
        now += 31L * 60 * 1000
        hashes.canvasHash()
        assertEquals(2, calls, "and tries again after the back-off")
        hashes.canvasHash(forceRefresh = true)
        assertEquals(3, calls, "a stale-hash answer forces a fresh look")
    }

    @Test
    fun `a failed refresh keeps the hash it already found`() {
        var reachable = true
        val hashes = SpotifyCanvasQuery.QueryHashes(fetch = { url ->
            if (!reachable) null
            else if (url == "https://open.spotify.com/") """https://open.spotifycdn.com/cdn/build/web-player/web-player.1.js"""
            else """new o.l("canvas","query","$hashB",null)"""
        })
        assertEquals(hashB, hashes.canvasHash())
        reachable = false
        assertEquals(hashB, hashes.canvasHash(forceRefresh = true))
    }
}
