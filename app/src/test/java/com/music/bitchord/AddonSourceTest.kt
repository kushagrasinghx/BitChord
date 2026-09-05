package com.music.bitchord

import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.sources.AddonSource
import com.music.bitchord.data.sources.DeviceCodecs
import com.music.bitchord.data.sources.SourceConfig
import com.music.bitchord.data.sources.SourceHealth
import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.data.sources.StreamRequest
import com.music.bitchord.data.sources.addon.AddonClient
import com.music.bitchord.playback.StreamContainer
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The addon protocol, end to end, against a real HTTP server.
 *
 * Everything here is about the seam between somebody else's server and this
 * app: what BitChord puts on the wire, and what it does with what comes back.
 * That is not a seam a hand-rolled fake can test honestly — a fake client
 * would encode the same assumptions the code under test makes, and the bugs
 * worth catching here all live in the gap between an assumption and an actual
 * response. So the server is real, and the only thing mocked is what it says.
 */
class AddonSourceTest {

    private lateinit var server: MockWebServer

    /** What the server answers, per path, set by each test before it runs. */
    private val routes = mutableMapOf<String, MockResponse>()

    /** Every request the server saw, in order, for assertions about the wire. */
    private val seen = mutableListOf<RecordedRequest>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(seen) { seen += request }
                val path = request.requestUrl?.encodedPath.orEmpty()
                return routes[path] ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
        // Both are read on every stream answer. Pinned so a test asserting on
        // format never depends on what the machine running it can decode.
        DeviceCodecs.forced = true
        AppSettings.dolbyAtmos.value = true
    }

    @After
    fun tearDown() {
        server.shutdown()
        DeviceCodecs.forced = null
    }

    // ── URL handling ──────────────────────────────────────────────────────

    /**
     * Both forms of the address people actually paste have to land on the same
     * base, because both are offered to them: an addon's page prints its root
     * and the browser's address bar prints the manifest it was just shown.
     */
    @Test
    fun `normalizeBase accepts every form of the address a user might paste`() {
        val expected = "https://addon.example.com"
        assertEquals(expected, AddonClient.normalizeBase("https://addon.example.com"))
        assertEquals(expected, AddonClient.normalizeBase("https://addon.example.com/"))
        assertEquals(expected, AddonClient.normalizeBase("  https://addon.example.com//  "))
        assertEquals(expected, AddonClient.normalizeBase("https://addon.example.com/manifest.json"))
        assertEquals(expected, AddonClient.normalizeBase("https://addon.example.com/MANIFEST.JSON"))
    }

    /**
     * A token lives *in the path*, and every later call has to carry it — so
     * normalisation may take the manifest suffix off and nothing else.
     */
    @Test
    fun `normalizeBase keeps a token-bearing path intact`() {
        assertEquals(
            "https://addon.example.com/abc123",
            AddonClient.normalizeBase("https://addon.example.com/abc123/manifest.json"),
        )
    }

    /**
     * The path is the credential, so the path is what a log line must not
     * carry. This is the opposite of how a module index is redacted, and the
     * reason is exactly this test.
     */
    @Test
    fun `redact hides the token in the path and keeps the host`() {
        assertEquals(
            "https://addon.example.com/***",
            AddonClient.redact("https://addon.example.com/secret-token/stream/42"),
        )
        assertEquals("***", AddonClient.redact("not a url"))
    }

    // ── Manifest ──────────────────────────────────────────────────────────

    @Test
    fun `a well-formed manifest reports healthy with its name and version`() = runBlocking {
        route("/manifest.json", manifest())
        val health = source().health()
        assertTrue("expected Ok, got $health", health is SourceHealth.Ok)
        assertEquals("Test Addon v2.1.0", (health as SourceHealth.Ok).detail)
    }

    /**
     * An addon that cannot be *searched* has nothing to offer — every lookup
     * this app makes starts by asking for a track by name — and saying so while
     * the user is still looking at the URL they pasted is the entire value of
     * the Test button. A [SourceHealth.Rejected] rather than
     * [SourceHealth.Unreachable] because retrying will never change the answer.
     */
    @Test
    fun `an addon that cannot be searched is rejected, not merely unreachable`() = runBlocking {
        route("/manifest.json", json("""{"id":"x","name":"X","resources":["stream"]}"""))
        // No search endpoint either, so the behavioural fallback cannot save it.
        val health = source().health()
        assertTrue("expected Rejected, got $health", health is SourceHealth.Rejected)
        assertTrue((health as SourceHealth.Rejected).reason.contains("needs search"))
    }

    /**
     * Stream is not required. A search row may carry its own `streamURL`, so an
     * addon declaring only search can still play — refusing it would turn away
     * something that works.
     */
    @Test
    fun `an addon that declares search but not stream is accepted`() = runBlocking {
        route("/manifest.json", json("""{"id":"x","name":"X","resources":["search"]}"""))
        assertTrue(source().health() is SourceHealth.Ok)
    }

    /**
     * The manifest is a description; the endpoints are the addon. One that
     * never published a manifest is healthy the moment it answers a search, and
     * refusing it over a missing document refuses it for the wrong reason.
     */
    @Test
    fun `an addon with no manifest is healthy when its search endpoint answers`() = runBlocking {
        route("/search", json("""{"tracks":[{"id":"t1","title":"S","artist":"A"}]}"""))

        val health = source().health()

        assertTrue("expected Ok, got $health", health is SourceHealth.Ok)
        assertEquals("No manifest · search works", (health as SourceHealth.Ok).detail)
    }

    @Test
    fun `a URL that answers with something other than a manifest is rejected`() = runBlocking {
        route("/manifest.json", json("""{"hello":"world"}"""))
        assertTrue(source().health() is SourceHealth.Rejected)
    }

    /**
     * A missing manifest is the one 404 that is not a miss: everywhere else it
     * means "I don't hold that track", and here it means "there is no addon
     * here at all".
     */
    @Test
    fun `no manifest at the URL is rejected rather than reported as a miss`() = runBlocking {
        val health = source().health()
        assertTrue("expected Rejected, got $health", health is SourceHealth.Rejected)
    }

    /** A server that is simply down is worth trying again, and says so differently. */
    @Test
    fun `a server error reads as unreachable`() = runBlocking {
        route("/manifest.json", MockResponse().setResponseCode(503))
        assertTrue(source().health() is SourceHealth.Unreachable)
    }

    // ── Search ────────────────────────────────────────────────────────────

    @Test
    fun `search maps rows to songs tagged with this source`() = runBlocking {
        route("/manifest.json", manifest())
        route(
            "/search",
            json(
                """
                {"tracks":[
                  {"id":"t1","title":"Song One","artist":"Artist","album":"Album",
                   "duration":245,"artworkURL":"https://cdn/x.jpg","format":"flac"},
                  {"id":"t2","title":"Song Two","artist":"Artist","duration":61,"format":"mp3"}
                ]}
                """.trimIndent(),
            ),
        )

        val config = config()
        val songs = AddonSource(config).search("song", limit = 10)

        assertEquals(2, songs.size)
        assertEquals("Song One", songs[0].title)
        assertEquals("Album", songs[0].albumName)
        assertEquals("https://cdn/x.jpg", songs[0].thumbnailUrl)
        assertEquals("4:05", songs[0].durationText)
        // Read off the row's own claim, so the resolver can rank before asking.
        assertEquals("LOSSLESS", songs[0].sourceQuality)
        assertEquals("1:01", songs[1].durationText)
        assertEquals("HIGH", songs[1].sourceQuality)
        // Packed so that playing one comes back to this source and this id.
        assertTrue(songs[0].videoId.startsWith("src:${config.id}::"))
        assertTrue(songs[0].videoId.endsWith("::t1"))
    }

    /** A row with no id can never be streamed, so it is not offered as a result. */
    @Test
    fun `search drops rows with no id or no title`() = runBlocking {
        route("/manifest.json", manifest())
        route(
            "/search",
            json("""{"tracks":[{"id":"","title":"No id"},{"id":"t","title":""},{"id":"ok","title":"Fine"}]}"""),
        )
        val songs = AddonSource(config()).search("x", limit = 10)
        assertEquals(1, songs.size)
        assertEquals("Fine", songs[0].title)
    }

    @Test
    fun `search honours the caller's limit`() = runBlocking {
        route("/manifest.json", manifest())
        val rows = (1..20).joinToString(",") { """{"id":"t$it","title":"Song $it","artist":"A"}""" }
        route("/search", json("""{"tracks":[$rows]}"""))
        assertEquals(3, AddonSource(config()).search("x", limit = 3).size)
    }

    @Test
    fun `an unreachable addon returns no results rather than throwing`() = runBlocking {
        route("/manifest.json", manifest())
        assertTrue(AddonSource(config()).search("x", limit = 5).isEmpty())
    }

    // ── Settings passthrough ──────────────────────────────────────────────

    /**
     * A declared default has to reach the addon, because an addon is entitled
     * to assume the host is sending one — and `quality` has to override it,
     * because that one is not a preference, it is what this request is for.
     */
    @Test
    fun `declared setting defaults travel with the request and quality overrides them`() = runBlocking {
        route(
            "/manifest.json",
            json(
                """
                {"id":"a","name":"A","resources":["search","stream","settings"],
                 "settings":[
                   {"key":"quality","type":"select","default":"normal",
                    "options":[{"value":"lossless"},{"value":"high"},{"value":"normal"}]},
                   {"key":"region","type":"text","default":"US"},
                   {"key":"preferOpus","type":"toggle","default":true}
                 ]}
                """.trimIndent(),
            ),
        )
        route("/search", json("""{"tracks":[]}"""))

        AddonSource(config()).search("hello", limit = 5)

        val url = requestFor("/search")
        assertEquals("hello", url.queryParameter("q"))
        assertEquals("US", url.queryParameter("region"))
        assertEquals("true", url.queryParameter("preferOpus"))
        // Not "normal" — the declared default lost to what was actually asked
        // for, and not "LOSSLESS" either, because the addon enumerated the
        // words it understands and that is not one of them.
        assertEquals("lossless", url.queryParameter("quality"))
    }

    /**
     * With nothing enumerated there is nothing to match against, so the tier
     * goes as-is — which is what the reference host sends unconditionally.
     */
    @Test
    fun `an addon that enumerates no options is sent the tier verbatim`() = runBlocking {
        route("/manifest.json", manifest())
        route("/stream/t1", json("""{"url":"https://cdn/a.flac"}"""))

        AddonSource(config()).stream("t1", StreamRequest.Lossless)

        assertEquals("LOSSLESS", requestFor("/stream/t1").queryParameter("quality"))
    }

    @Test
    fun `a capped request asks for the addon's small tier`() = runBlocking {
        route("/manifest.json", manifest())
        route("/stream/t1", json("""{"url":"https://cdn/a.mp3"}"""))

        AddonSource(config()).stream("t1", StreamRequest.Capped(maxKbps = 64))

        assertEquals("LOW", requestFor("/stream/t1").queryParameter("quality"))
    }

    /** An id is a path segment, not string concatenation, or it can rewrite the request. */
    @Test
    fun `a track id containing a slash stays one path segment`() = runBlocking {
        route("/manifest.json", manifest())
        route("/stream/a%2Fb%3Fc", json("""{"url":"https://cdn/a.flac"}"""))

        val stream = AddonSource(config()).stream("a/b?c", StreamRequest.Lossless)

        assertNotNull("the id was not encoded into a single segment", stream)
    }

    // ── Stream ────────────────────────────────────────────────────────────

    @Test
    fun `the routing fields become the format the player is told about`() = runBlocking {
        route("/manifest.json", manifest())
        route(
            "/stream/t1",
            json(
                """
                {"url":"https://cdn.example.com/a.flac","format":"flac","quality":"lossless",
                 "codec":"flac","container":"flac","manifest":"none","encrypted":false,
                 "sampleRate":96000,"bitDepth":24}
                """.trimIndent(),
            ),
        )

        val stream = AddonSource(config()).stream("t1", StreamRequest.Lossless)!!

        assertEquals("https://cdn.example.com/a.flac", stream.url)
        assertEquals("flac", stream.format.codec)
        assertEquals(96000, stream.format.sampleRateHz)
        assertEquals(24, stream.format.bitDepth)
        assertEquals(true, stream.format.isLossless)
    }

    /**
     * The case the whole `manifest` field exists for: an HLS playlist served
     * from a path with no extension, reporting `format: "flac"`. Sniffed, that
     * is a plain FLAC file and the player fails on it. Declared, it isn't.
     */
    @Test
    fun `an extensionless HLS playlist is declared to the player rather than sniffed`() = runBlocking {
        val url = "https://cdn.example.com/dash/t1"
        route("/manifest.json", manifest())
        route("/stream/t1", json("""{"url":"$url","format":"flac","codec":"flac","manifest":"hls"}"""))

        // Nothing in the URL says HLS, which is the point.
        assertNull(StreamContainer.manifestMimeOf("https://cdn.example.com/dash/never-declared"))

        AddonSource(config()).stream("t1", StreamRequest.Lossless)

        assertEquals("application/x-mpegURL", StreamContainer.manifestMimeOf(url))
    }

    @Test
    fun `a declared dash transport is passed on too`() = runBlocking {
        val url = "https://cdn.example.com/play/t2"
        route("/manifest.json", manifest())
        route("/stream/t2", json("""{"url":"$url","manifest":"dash"}"""))

        AddonSource(config()).stream("t2", StreamRequest.Lossless)

        assertEquals("application/dash+xml", StreamContainer.manifestMimeOf(url))
    }

    /**
     * This app never sends `?drm=`, so an encrypted answer is an addon ignoring
     * the protocol. Refusing lets another source be heard; playing it fails
     * later and less clearly.
     */
    @Test
    fun `an encrypted rendition is refused`() = runBlocking {
        route("/manifest.json", manifest())
        route(
            "/stream/t1",
            json("""{"url":"https://cdn/a.mpd","encrypted":"widevine","manifest":"dash"}"""),
        )
        assertNull(AddonSource(config()).stream("t1", StreamRequest.Lossless))
    }

    @Test
    fun `encrypted false is not treated as encrypted`() = runBlocking {
        route("/manifest.json", manifest())
        route("/stream/t1", json("""{"url":"https://cdn/a.flac","encrypted":false}"""))
        assertNotNull(AddonSource(config()).stream("t1", StreamRequest.Lossless))
    }

    /** A URL the player's own parser would refuse costs nothing to refuse here. */
    @Test
    fun `a malformed stream URL is refused before the player sees it`() = runBlocking {
        route("/manifest.json", manifest())
        route("/stream/t1", json("""{"url":"/relative/path.flac"}"""))
        assertNull(AddonSource(config()).stream("t1", StreamRequest.Lossless))
    }

    /** A 404 on stream is the addon saying it doesn't hold the track. A miss. */
    @Test
    fun `a stream 404 is a miss, not a crash`() = runBlocking {
        route("/manifest.json", manifest())
        assertNull(AddonSource(config()).stream("nope", StreamRequest.Lossless))
    }

    /**
     * An addon serving permanent direct links may treat `/stream` as optional.
     * The row's own URL is the last thing to try rather than the first, because
     * it carries none of the routing metadata the stream call does.
     */
    @Test
    fun `the search row's own URL is used when the stream endpoint has nothing`() = runBlocking {
        route("/manifest.json", manifest())
        route(
            "/search",
            json(
                """{"tracks":[{"id":"t1","title":"S","artist":"A","duration":180,
                   "streamURL":"https://cdn.example.com/direct.mp3","format":"mp3"}]}""",
            ),
        )

        val source = AddonSource(config())
        source.search("s", limit = 5)
        val stream = source.stream("t1", StreamRequest.Best)!!

        assertEquals("https://cdn.example.com/direct.mp3", stream.url)
        assertEquals("mp3", stream.format.codec)
        // Carried through so an upgrade can check it against what actually decodes.
        assertEquals(180, stream.durationSec)
    }

    @Test
    fun `with no row remembered and no stream answer there is nothing to play`() = runBlocking {
        route("/manifest.json", manifest())
        assertNull(AddonSource(config()).stream("unknown", StreamRequest.Best))
    }

    /**
     * The duration the catalogue claimed travels with the stream, because a
     * substitute cut into a track already playing is checked against it.
     */
    @Test
    fun `a searched row's duration reaches the stream it produces`() = runBlocking {
        route("/manifest.json", manifest())
        route("/search", json("""{"tracks":[{"id":"t1","title":"S","artist":"A","duration":242}]}"""))
        route("/stream/t1", json("""{"url":"https://cdn/a.flac","codec":"flac"}"""))

        val source = AddonSource(config())
        source.search("s", limit = 5)

        assertEquals(242, source.stream("t1", StreamRequest.Lossless)!!.durationSec)
    }

    // ── Rate limiting ─────────────────────────────────────────────────────

    /**
     * A 429 must not read as "this addon does not have the track" — that is a
     * different and wrong conclusion, and it drops a working source out of the
     * walk the moment it gets busy.
     */
    @Test
    fun `a rate-limited request is waited out and retried`() = runBlocking {
        route("/manifest.json", manifest())
        val answers = ArrayDeque(
            listOf(
                MockResponse().setResponseCode(429).setHeader("Retry-After", "0"),
                json("""{"url":"https://cdn/a.flac","codec":"flac"}"""),
            ),
        )
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(seen) { seen += request }
                val path = request.requestUrl?.encodedPath.orEmpty()
                if (path == "/stream/t1") return answers.removeFirst()
                return routes[path] ?: MockResponse().setResponseCode(404)
            }
        }

        val stream = AddonSource(config()).stream("t1", StreamRequest.Lossless)

        assertNotNull("a 429 should be retried, not reported as a miss", stream)
        assertEquals(0, answers.size)
    }

    // ── Fallback quality inference ────────────────────────────────────────

    /**
     * An addon that states nothing still has to produce a usable format, or
     * every ranking decision above this is made on nulls.
     */
    @Test
    fun `a bare answer still yields what can be inferred from the URL`() = runBlocking {
        route("/manifest.json", manifest())
        route("/stream/t1", json("""{"url":"https://cdn.example.com/track.128.mp3"}"""))

        val format = AddonSource(config()).stream("t1", StreamRequest.Best)!!.format

        assertEquals("mp3", format.codec)
        assertEquals(false, format.isLossless)
    }

    @Test
    fun `a bitrate in bits per second is read as kbps`() = runBlocking {
        route("/manifest.json", manifest())
        route("/stream/t1", json("""{"url":"https://cdn/a.mp3","codec":"mp3","bitrate":320000}"""))
        assertEquals(320, AddonSource(config()).stream("t1", StreamRequest.Best)!!.format.kbps)
    }

    @Test
    fun `a bitrate already in kbps is left alone`() = runBlocking {
        route("/manifest.json", manifest())
        route("/stream/t1", json("""{"url":"https://cdn/a.flac","codec":"flac","bitrate":1411}"""))
        assertEquals(1411, AddonSource(config()).stream("t1", StreamRequest.Lossless)!!.format.kbps)
    }

    /**
     * An immersive rendition is routinely the only one an addon will give for a
     * track, so a device that cannot decode it has to refuse here — there is no
     * lower-ranked sibling for the resolver to fall to.
     */
    @Test
    fun `an Atmos rendition is refused on a device that cannot decode it`() = runBlocking {
        DeviceCodecs.forced = false
        route("/manifest.json", manifest())
        route("/stream/t1", json("""{"url":"https://cdn/a.mp4","codec":"eac3_joc","quality":"Dolby Atmos"}"""))

        assertNull(AddonSource(config()).stream("t1", StreamRequest.Lossless))
    }

    @Test
    fun `an Atmos rendition plays on a device that can decode it`() = runBlocking {
        route("/manifest.json", manifest())
        route("/stream/t1", json("""{"url":"https://cdn/a.mp4","codec":"eac3-joc","quality":"Dolby Atmos"}"""))

        val stream = AddonSource(config()).stream("t1", StreamRequest.Lossless)
        assertNotNull(stream)
        assertTrue(stream!!.format.isDolbyAtmos)
    }

    // ── Against what a real addon actually sends ──────────────────────────
    //
    // Every payload below is captured verbatim from a live multi-backend addon
    // in September 2026, trimmed only in length. They are here because the
    // mocked tests above all pass against a wrong implementation: they assert
    // that this app reads the spec correctly, and an addon in the field does
    // not send exactly what the spec describes.

    /**
     * The one that matters. A Hi-Res FLAC over DASH, and the *only* statement
     * that it is a manifest at all is the word `dash` in `format` — no
     * `manifest` field, no `codec`, no `container`, and a URL with no `.mpd`
     * for anything downstream to sniff.
     *
     * Read wrong, this is a DASH document handed to a progressive extractor.
     */
    @Test
    fun `a real Tidal Hi-Res answer routes to DASH on the strength of its format field`() = runBlocking {
        val url = "https://im-fa.manifest.tidal.com/1/manifests/Egk0MTc1OTQ2NzEYAigBMAJY3onUY2CHaGoIUExBWUJBQ0s"
        route("/manifest.json", manifest())
        route(
            "/stream/t1",
            json(
                """
                {"url":"$url","format":"dash","quality":"Tidal · Hi-Res FLAC (DASH)",
                 "streamQuality":"[Tidal] HI_RES_LOSSLESS","provider":"Tidal","expiresAt":1788633325}
                """.trimIndent(),
            ),
        )

        val stream = AddonSource(config()).stream("t1", StreamRequest.Lossless)!!

        assertEquals(url, stream.url)
        assertEquals("application/dash+xml", StreamContainer.manifestMimeOf(url))
        // Nothing named the codec either; "Hi-Res FLAC" in free text is the
        // whole of the evidence that this is worth playing over YouTube.
        assertEquals("flac", stream.format.codec)
        assertEquals(true, stream.format.isLossless)
    }

    /**
     * The same addon's JioSaavn backend answers with `format: "mp4"` — a
     * container, in the same field the Tidal answer used for a transport. It
     * must not be mistaken for one, or a plain audio file is opened as a
     * manifest and nothing plays.
     */
    @Test
    fun `a container in the format field is not mistaken for a transport`() = runBlocking {
        val url = "https://aac.saavncdn.com/601/b81082b74fa06e4596b5b111b0115d1a_320.mp4"
        route("/manifest.json", manifest())
        route("/stream/t1", json("""{"url":"$url","format":"mp4","quality":"320kbps","provider":"JioSaavn"}"""))

        val stream = AddonSource(config()).stream("t1", StreamRequest.Best)!!

        assertNull("mp4 is a container, not a manifest", StreamContainer.manifestMimeOf(url))
        assertEquals(320, stream.format.kbps)
        assertEquals("mp4", stream.format.codec)
        // Not null. An unknown codec is the answer that would let this stay in
        // contention against a FLAC, on the grounds nobody could rule it out.
        assertEquals(false, stream.format.isLossless)
    }

    /**
     * Two of that addon's backends answer a track they cannot serve with
     * `HTTP 200` and an `error` string rather than a status code. It is a miss
     * either way — but the second kind is a credential the user can go and set,
     * and silently falling through to YouTube is how nobody ever finds out.
     */
    @Test
    fun `a 200 carrying an error and no url is a miss`() = runBlocking {
        route("/manifest.json", manifest())
        route("/stream/t1", json("""{"error":"Deezer ARL required — set it in the setup page"}"""))
        assertNull(AddonSource(config()).stream("t1", StreamRequest.Lossless))
    }

    /** That addon answers an unknown id with a 502, not the 404 the spec implies. */
    @Test
    fun `a gateway error on an unknown id is a miss rather than a crash`() = runBlocking {
        route("/manifest.json", manifest())
        route("/stream/nope", MockResponse().setResponseCode(502))
        assertNull(AddonSource(config()).stream("nope", StreamRequest.Lossless))
    }

    /** Real rows: a colon inside the id, and artwork under two different keys. */
    @Test
    fun `real search rows survive their provider-prefixed ids`() = runBlocking {
        route("/manifest.json", manifest())
        route(
            "/search",
            json(
                """
                {"tracks":[{"id":"tidal:417594671","sourceId":"417594671","title":"Daylight",
                  "artist":"David Kushner","album":"Daylight","albumId":"417594670",
                  "albumArtworkURL":"https://resources.tidal.com/images/x/1080x1080.jpg",
                  "trackNumber":1,"duration":212,"format":"flac","isrc":"QZXDB2300005",
                  "audioQuality":"LOSSLESS","provider":"Tidal","audioModes":["STEREO"]}],
                 "albums":[],"artists":[],"playlists":[]}
                """.trimIndent(),
            ),
        )

        val songs = AddonSource(config()).search("daylight", limit = 5)

        assertEquals(1, songs.size)
        assertEquals("LOSSLESS", songs[0].sourceQuality)
        assertEquals("3:32", songs[0].durationText)
        // The provider prefix has to survive the round trip through the media
        // id, or the stream call asks for a track the addon has never heard of.
        assertTrue(songs[0].videoId.endsWith("::tidal:417594671"))
        // Only albumArtworkURL was sent, and it still has to reach the row.
        assertEquals("https://resources.tidal.com/images/x/1080x1080.jpg", songs[0].thumbnailUrl)
    }

    /** A free-text label is where a Hi-Res addon states its resolution, if anywhere. */
    @Test
    fun `bit depth and sample rate are read out of a free-text quality label`() = runBlocking {
        route("/manifest.json", manifest())
        route(
            "/stream/t1",
            json("""{"url":"https://cdn/a","format":"dash","quality":"FLAC 24-bit / 96 kHz"}"""),
        )

        val format = AddonSource(config()).stream("t1", StreamRequest.Lossless)!!.format

        assertEquals(24, format.bitDepth)
        assertEquals(96000, format.sampleRateHz)
    }

    // ── The sniffing that was already there ──────────────────────────────

    @Test
    fun `a URL nobody declared is still judged on its extension`() {
        assertEquals(
            "application/x-mpegURL",
            StreamContainer.manifestMimeOf("https://cdn.example.com/audio/playlist.m3u8"),
        )
        assertEquals(
            "application/dash+xml",
            StreamContainer.manifestMimeOf("https://cdn.example.com/audio/manifest.mpd?token=abc"),
        )
        assertNull(StreamContainer.manifestMimeOf("https://cdn.example.com/audio/track.flac"))
        assertNull(StreamContainer.manifestMimeOf("https://cdn.example.com/audio/track"))
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun route(path: String, response: MockResponse) {
        routes[path] = response
    }

    private fun json(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun manifest() = json(
        """{"id":"com.test.addon","name":"Test Addon","version":"2.1.0","resources":["search","stream"]}""",
    )

    private fun config() = SourceConfig(
        kind = SourceKind.ADDON,
        baseUrl = server.url("/").toString().trimEnd('/'),
    )

    private fun source() = AddonSource(config())

    /** The URL of the first request the server saw for [path]. */
    private fun requestFor(path: String) = synchronized(seen) {
        seen.first { it.requestUrl?.encodedPath == path }.requestUrl!!
    }

    @Test
    fun `an addon URL is stored normalised so two spellings are one source`() {
        assertEquals(
            AddonClient.normalizeBase("https://a.example.com/manifest.json"),
            AddonClient.normalizeBase("https://a.example.com/"),
        )
        assertFalse(AddonClient.normalizeBase("https://a.example.com/").endsWith("/"))
    }
}
