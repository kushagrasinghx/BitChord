package com.music.bitchord

import com.music.bitchord.data.sources.DeviceCodecs
import com.music.bitchord.data.sources.addon.AddonAuth
import com.music.bitchord.data.sources.addon.AddonClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * The app's half of addon auth, without a device. Real attestation needs
 * secure hardware, so [AddonAuth.testHeaders] stands in for it. What is tested
 * is the wire: who receives credentials, when, and how refusals are handled.
 * The signature encoding is also checked, since addons verify it in other
 * languages.
 */
class AddonAuthTest {

    private lateinit var server: MockWebServer
    private val seen = mutableListOf<RecordedRequest>()
    private var manifest = """{"id":"t","name":"T","resources":["search","stream"]}"""

    /** How /search answers: given the token it got (or null), the response. */
    private var search: (String?) -> MockResponse = { ok() }

    /** Tokens handed out by the stand-in, in order. */
    private val issued = mutableListOf<String>()

    private fun ok() = MockResponse().setBody("""{"tracks":[{"id":"1","title":"Song","artist":"Artist"}]}""")

    private fun challenge(code: String) = MockResponse().setResponseCode(401)
        .setHeader("WWW-Authenticate", """BitChord realm="bitchord-addons", error="$code", error_description="x"""")
        .setBody("""{"error":"bitchord_auth","code":"$code"}""")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(seen) { seen += request }
                return when (request.requestUrl?.encodedPath) {
                    "/manifest.json" -> MockResponse().setBody(manifest)
                    "/search" -> search(request.getHeader(AddonAuth.TOKEN_HEADER))
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        DeviceCodecs.forced = true
        AddonAuth.testHeaders = { _, rejected ->
            val token = if (rejected == null && issued.isNotEmpty()) issued.last() else "token-${issued.size + 1}"
            if (issued.lastOrNull() != token) issued += token
            mapOf(AddonAuth.TOKEN_HEADER to token, AddonAuth.PROOF_HEADER to "proof-for-$token")
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
        DeviceCodecs.forced = null
        AddonAuth.testHeaders = null
    }

    private fun client() = AddonClient(server.url("/").toString())

    private fun searches() = synchronized(seen) { seen.filter { it.requestUrl?.encodedPath == "/search" } }

    @Test
    fun `an addon that never asks never sees a credential`() = runBlocking {
        assertTrue(client().search("song", AddonClient.TIER_LOSSLESS).isSuccess)
        synchronized(seen) { seen.forEach { assertNull(it.getHeader(AddonAuth.TOKEN_HEADER)) } }
        assertTrue(issued.isEmpty())
    }

    @Test
    fun `a 401 BitChord challenge is answered with credentials, once`() = runBlocking {
        search = { token -> if (token == null) challenge("missing") else ok() }
        val result = client().search("song", AddonClient.TIER_LOSSLESS)
        assertTrue(result.exceptionOrNull()?.message, result.isSuccess)
        val calls = searches()
        assertEquals(2, calls.size)
        assertNull(calls[0].getHeader(AddonAuth.TOKEN_HEADER))
        assertEquals("token-1", calls[1].getHeader(AddonAuth.TOKEN_HEADER))
        assertEquals("proof-for-token-1", calls[1].getHeader(AddonAuth.PROOF_HEADER))
    }

    @Test
    fun `a manifest declaring bitchordAuth gets credentials from the first request`() = runBlocking {
        manifest = """{"id":"t","name":"T","resources":["search","stream"],"bitchordAuth":true}"""
        search = { token -> if (token == null) challenge("missing") else ok() }
        assertTrue(client().search("song", AddonClient.TIER_LOSSLESS).isSuccess)
        assertEquals(1, searches().size)
        // The manifest itself stays public: it is how the app learns to authenticate at all.
        assertNull(seen.first { it.requestUrl?.encodedPath == "/manifest.json" }.getHeader(AddonAuth.TOKEN_HEADER))
    }

    @Test
    fun `an expired token is replaced once and the request retried`() = runBlocking {
        manifest = """{"id":"t","name":"T","resources":["search","stream"],"bitchordAuth":1}"""
        search = { token -> if (token == "token-1") challenge("expired") else ok() }
        assertTrue(client().search("song", AddonClient.TIER_LOSSLESS).isSuccess)
        assertEquals(listOf("token-1", "token-2"), searches().map { it.getHeader(AddonAuth.TOKEN_HEADER) })
    }

    @Test
    fun `a final refusal says why instead of looping`() = runBlocking {
        search = { challenge("invalid_proof") }
        val failure = client().search("song", AddonClient.TIER_LOSSLESS).exceptionOrNull()
        assertEquals("This addon didn't accept BitChord's verification (invalid_proof)", failure?.message)
        assertEquals(2, searches().size)
    }

    @Test
    fun `a 401 from some other auth scheme is not treated as ours`() = runBlocking {
        search = { MockResponse().setResponseCode(401).setHeader("WWW-Authenticate", "Bearer") }
        assertEquals("HTTP 401", client().search("song", AddonClient.TIER_LOSSLESS).exceptionOrNull()?.message)
        assertTrue(issued.isEmpty())
    }

    @Test
    fun `DER signatures become the 64-byte r-s form WebCrypto and JOSE verify`() {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        // Enough rounds to hit r and s with leading zero bytes, the case naive conversions get wrong.
        repeat(300) { i ->
            val message = "bitchord-addon-proof/v1\naddon.example.com\n$i\nhash".toByteArray()
            val der = Signature.getInstance("SHA256withECDSA").run { initSign(pair.private); update(message); sign() }
            val raw = AddonAuth.derToRaw(der)
            assertEquals(64, raw.size)
            val verified = Signature.getInstance("SHA256withECDSAinP1363Format").run {
                initVerify(pair.public); update(message); verify(raw)
            }
            assertTrue("round $i", verified)
        }
    }

    @Test
    fun `the signed message matches the protocol exactly`() {
        assertEquals(
            "bitchord-addon-proof/v1\naddon.example.com\n1762300000000\nabc",
            AddonAuth.proofMessage("addon.example.com", 1762300000000, "abc"),
        )
        assertEquals("addon.example.com", AddonAuth.normalizeHost(" Addon.Example.COM. "))
    }

    /** Must match normalizeTesterCode in the auth service (src/server.js), or a typed code never matches its hash. */
    @Test
    fun `tester codes ignore case, spaces and dashes like the auth service does`() {
        val code = "K7Q2M-9XW4T-ZD3HP-RN8CV-JB6YF"
        assertEquals("K7Q2M9XW4TZD3HPRN8CVJB6YF", AddonAuth.normalizeTesterCode(code))
        assertEquals("K7Q2M9XW4TZD3HPRN8CVJB6YF", AddonAuth.normalizeTesterCode(" k7q2m 9xw4t zd3hp rn8cv jb6yf "))
        assertEquals("", AddonAuth.normalizeTesterCode(null))
        assertEquals("", AddonAuth.normalizeTesterCode(" - - "))
    }
}
