package com.music.bitchord.data.applemusic

import java.io.IOException
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TOKEN_URL = "https://backend.example/api/applemusic/token"
private const val NOW = 1_700_000_000L

@OptIn(ExperimentalCoroutinesApi::class)
class AppleMusicDeveloperTokensTest {
    @Test
    fun `cache hits are network free and refresh begins exactly sixty seconds before expiry`() = runTest {
        var now = NOW
        var calls = 0
        val provider = AppleMusicDeveloperTokens(TOKEN_URL, {
            calls++
            tokenResponse(now + 3600, "request-$calls")
        }, { now })

        val first = provider.developerToken()
        assertEquals(first, provider.developerToken())
        now = NOW + 3600 - 61
        assertEquals(first, provider.developerToken())
        assertEquals(1, calls)
        now++
        val refreshed = provider.developerToken()
        assertFalse(first == refreshed)
        assertEquals(refreshed, provider.developerToken())
        assertEquals(2, calls)
    }

    @Test
    fun `concurrent cold and refresh requests each share a single fetch`() = runTest {
        var now = NOW
        var calls = 0
        var release = CompletableDeferred<Unit>()
        val provider = AppleMusicDeveloperTokens(TOKEN_URL, {
            calls++
            release.await()
            tokenResponse(now + 3600, "request-$calls")
        }, { now })

        repeat(2) {
            val requests = List(20) { async { provider.developerToken() } }
            runCurrent()
            assertEquals(it + 1, calls)
            release.complete(Unit)
            assertEquals(1, requests.awaitAll().distinct().size)
            now += 3600 - 60
            release = CompletableDeferred()
        }
        assertEquals(2, calls)
    }

    @Test
    fun `clear evicts a cached token`() = runTest {
        var calls = 0
        val provider = AppleMusicDeveloperTokens(TOKEN_URL, {
            tokenResponse(NOW + 3600, "request-${++calls}")
        }, { NOW })
        val first = provider.developerToken()
        provider.clear()
        assertFalse(first == provider.developerToken())
        assertEquals(2, calls)
    }

    @Test
    fun `clear invalidates both in-flight and already queued responses`() = runTest {
        var calls = 0
        val release = CompletableDeferred<Unit>()
        val provider = AppleMusicDeveloperTokens(TOKEN_URL, {
            calls++
            release.await()
            tokenResponse(NOW + 3600, "request-$calls")
        }, { NOW })
        val first = async { provider.developerToken() }
        val queued = async { provider.developerToken() }
        runCurrent()
        assertEquals(1, calls)
        provider.clear()
        release.complete(Unit)
        expectFailure<CancellationException> { first.await() }
        expectFailure<CancellationException> { queued.await() }
        assertEquals(1, calls)
        assertEquals(jwt(NOW + 3600, "request-2"), provider.developerToken())
        assertEquals(2, calls)
    }

    @Test
    fun `caller cancellation releases the mutex and never caches its response`() = runTest {
        var calls = 0
        val release = CompletableDeferred<Unit>()
        val provider = AppleMusicDeveloperTokens(TOKEN_URL, {
            val request = ++calls
            if (request == 1) release.await()
            tokenResponse(NOW + 3600, "request-$request")
        }, { NOW })
        val first = async { provider.developerToken() }
        val queued = async { provider.developerToken() }
        runCurrent()
        first.cancelAndJoin()
        assertTrue(first.isCancelled)
        assertEquals(jwt(NOW + 3600, "request-2"), queued.await())
        release.complete(Unit)
        assertEquals(queued.await(), provider.developerToken())
        assertEquals(2, calls)
    }

    @Test
    fun `a cancelled waiter does not cancel the shared fetch`() = runTest {
        var calls = 0
        val release = CompletableDeferred<Unit>()
        val provider = AppleMusicDeveloperTokens(TOKEN_URL, {
            calls++
            release.await()
            tokenResponse(NOW + 3600)
        }, { NOW })
        val first = async { provider.developerToken() }
        val queued = async { provider.developerToken() }
        runCurrent()
        queued.cancelAndJoin()
        release.complete(Unit)
        assertEquals(jwt(NOW + 3600), first.await())
        assertEquals(first.await(), provider.developerToken())
        assertEquals(1, calls)
    }

    @Test
    fun `fetch cancellation propagates unchanged`() = runTest {
        val cancellation = CancellationException("cancelled by caller")
        val provider = AppleMusicDeveloperTokens(TOKEN_URL, { throw cancellation }, { NOW })
        assertSame(cancellation, expectFailure<CancellationException> { provider.developerToken() })
    }

    @Test
    fun `failed refresh never falls back to a near expiry token`() = runTest {
        var now = NOW
        var calls = 0
        val provider = AppleMusicDeveloperTokens(TOKEN_URL, {
            calls++
            if (calls == 2) throw IOException("private transport details")
            tokenResponse(now + 3600, "request-$calls")
        }, { now })
        provider.developerToken()
        now += 3600 - 60
        expectFailure<AppleMusicTokenException> { provider.developerToken() }
        assertEquals(jwt(now + 3600, "request-3"), provider.developerToken())
        assertEquals(3, calls)
    }

    @Test
    fun `unconfigured and unsafe backend URLs never fetch or leak configuration`() = runTest {
        val invalid = listOf(
            "", " ", "embedded-local-token",
            "http://backend.example/api/applemusic/token",
            "https://user:private-password@backend.example/api/applemusic/token",
            "https://@backend.example/api/applemusic/token",
            "$TOKEN_URL?token=private-query", "$TOKEN_URL?",
            "$TOKEN_URL#private-fragment", "$TOKEN_URL#",
            "https://backend.example/wrong", "$TOKEN_URL/",
            "https://backend.example/not-api/applemusic/token",
            "https://backend.example/prefix/../api/applemusic/token",
            "https://backend.example\\api/applemusic/token",
            " $TOKEN_URL", "$TOKEN_URL ",
        )
        for (url in invalid) {
            var calls = 0
            val provider = AppleMusicDeveloperTokens(url, { calls++; tokenResponse(NOW + 3600) }, { NOW })
            val error = expectFailure<AppleMusicTokenException> { provider.developerToken() }
            assertEquals(0, calls)
            assertNull(error.cause)
            assertFalse(error.stackTraceToString().contains("private-"))
        }
    }

    @Test
    fun `HTTPS deployment prefixes and custom ports are allowed without adding authentication`() = runTest {
        val configured = "https://backend.example:8443/deployment/v1/api/applemusic/token"
        var fetched: HttpUrl? = null
        val provider = AppleMusicDeveloperTokens(configured, {
            fetched = it
            tokenResponse(NOW + 3600)
        }, { NOW })
        assertNull(fetched)
        assertEquals(jwt(NOW + 3600), provider.developerToken())
        assertEquals(configured, fetched.toString())
    }

    @Test
    fun `transport exception messages and causes are not exposed`() = runTest {
        val secret = jwt(NOW + 3600)
        val provider = AppleMusicDeveloperTokens(TOKEN_URL, { throw IOException(secret) }, { NOW })
        val error = expectFailure<AppleMusicTokenException> { provider.developerToken() }
        assertNull(error.cause)
        assertFalse(error.stackTraceToString().contains(secret))
    }

    @Test
    fun `malformed response fields and JWT structure are rejected without leaking input`() = runTest {
        val valid = jwt(NOW + 3600)
        val exp = NOW + 3600
        val malformedTokens = listOf(
            "not-a-jwt", "a.b", ".${encode("{\"exp\":$exp}")}.c2ln",
            "${encode("not-json")}.${encode("{\"exp\":$exp}")}.c2ln",
            "${encode("[]")}.${encode("{\"exp\":$exp}")}.c2ln",
            "${encode("{}")}.${encode("not-json")}.c2ln",
            "${encode("{}")}.${encode("[]")}.c2ln",
            "${encode("{}")}.${encode("{}")}.c2ln",
            "${encode("{}")}.${encode("{\"exp\":\"$exp\"}")}.c2ln",
            "${encode("{}")}.${encode("{\"exp\":$exp.5}")}.c2ln",
            "${encode("{}")}.${encode("{\"exp\":true}")}.c2ln",
            "${encode("{}")}.${encode("{\"exp\":$exp}")}.a",
            "$valid=", "$valid.extra", "$valid ",
        )
        val malformed = listOf(
            "", "{private-input", "[]", "null", "{}",
            """{"token":123,"expiresAt":$exp}""",
            """{"token":"","expiresAt":$exp}""",
            """{"token":"$valid"}""",
            """{"token":"$valid","expiresAt":"$exp"}""",
            """{"token":"$valid","expiresAt":$exp.5}""",
            """{"token":"$valid","expiresAt":true}""",
            """{"token":"$valid","expiresAt":null}""",
            """{"token":"$valid","expiresAt":0}""",
            """{"token":"$valid","expiresAt":-1}""",
            """{"token":"$valid","expiresAt":9223372036854775808}""",
            """{"token":"$valid","expiresAt":${exp + 1}}""",
        ) + malformedTokens.map { """{"token":"$it","expiresAt":$exp}""" }
        for (response in malformed) {
            val provider = AppleMusicDeveloperTokens(TOKEN_URL, { response }, { NOW })
            val error = expectFailure<AppleMusicTokenException> { provider.developerToken() }
            assertNull(error.cause)
            assertFalse(error.stackTraceToString().contains(valid))
            assertFalse(error.stackTraceToString().contains("private-input"))
        }
    }

    @Test
    fun `expired and near expiry JWTs are rejected even when backend expiry agrees`() = runTest {
        for (expiry in listOf(NOW - 1, NOW, NOW + 1, NOW + 59, NOW + 60)) {
            val provider = AppleMusicDeveloperTokens(TOKEN_URL, { tokenResponse(expiry) }, { NOW })
            expectFailure<AppleMusicTokenException> { provider.developerToken() }
        }
        val usable = AppleMusicDeveloperTokens(TOKEN_URL, { tokenResponse(NOW + 61) }, { NOW })
        assertEquals(jwt(NOW + 61), usable.developerToken())
    }

    @Test
    fun `expiry is checked after a slow fetch and malformed results are not cached`() = runTest {
        var now = NOW
        var calls = 0
        val provider = AppleMusicDeveloperTokens(TOKEN_URL, {
            calls++
            if (calls == 1) {
                now += 3600
                tokenResponse(NOW + 3600)
            } else if (calls == 2) {
                "not-json"
            } else {
                tokenResponse(now + 3600)
            }
        }, { now })
        expectFailure<AppleMusicTokenException> { provider.developerToken() }
        expectFailure<AppleMusicTokenException> { provider.developerToken() }
        assertEquals(jwt(now + 3600), provider.developerToken())
        assertEquals(3, calls)
    }
}

class AppleMusicTokenBackendTest {
    private val clients = mutableListOf<OkHttpClient>()

    @After
    fun closeClients() {
        clients.forEach {
            it.dispatcher.executorService.shutdownNow()
            it.connectionPool.evictAll()
        }
    }

    @Test
    fun `dedicated client strips inherited credentials and disables redirects and disk caching`() = runBlocking {
        val cookies = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
            override fun loadForRequest(url: HttpUrl) = listOf(Cookie.Builder().name("session").value("private-cookie").domain(url.host).build())
        }
        val auth = Authenticator { _, response -> response.request.newBuilder().header("Authorization", "private-auth").build() }
        val base = OkHttpClient.Builder()
            .authenticator(auth)
            .proxyAuthenticator(auth)
            .cookieJar(cookies)
            .addInterceptor { error("Inherited credential interceptor must not run") }
            .addNetworkInterceptor { error("Inherited logging interceptor must not run") }
            .build()
        val secured = appleMusicTokenClient(base)
        assertTrue(secured.interceptors.isEmpty())
        assertTrue(secured.networkInterceptors.isEmpty())
        assertSame(Authenticator.NONE, secured.authenticator)
        assertSame(Authenticator.NONE, secured.proxyAuthenticator)
        assertSame(CookieJar.NO_COOKIES, secured.cookieJar)
        assertNull(secured.cache)
        assertFalse(secured.followRedirects)
        assertFalse(secured.followSslRedirects)
        assertFalse(secured.retryOnConnectionFailure)
        assertEquals(15_000, secured.callTimeoutMillis)
        assertTrue(secured.connectTimeoutMillis in 1..15_000)
        assertTrue(secured.readTimeoutMillis in 1..15_000)

        var seen: Request? = null
        val body = TrackedBody(tokenResponse(NOW + 3600))
        val client = secured.newBuilder().addInterceptor { chain ->
            seen = chain.request()
            response(chain, 200, body)
        }.build().also { clients += it }
        val provider = AppleMusicDeveloperTokens(TOKEN_URL, AppleMusicTokenBackend(client)::fetch, { NOW })
        assertEquals(jwt(NOW + 3600), provider.developerToken())
        val request = checkNotNull(seen)
        assertEquals("GET", request.method)
        assertEquals(TOKEN_URL, request.url.toString())
        assertNull(request.body)
        assertNull(request.url.query)
        assertNull(request.header("Authorization"))
        assertNull(request.header("Proxy-Authorization"))
        assertNull(request.header("Music-User-Token"))
        assertNull(request.header("Cookie"))
        assertEquals(setOf("Accept", "Cache-Control"), request.headers.names())
        assertEquals("application/json", request.header("Accept"))
        assertTrue(request.cacheControl.noStore)
        assertTrue(body.closed.get())
    }

    @Test
    fun `HTTP errors and redirects are rejected without exposing response bodies or locations`() = runBlocking {
        for (status in listOf(204, 301, 302, 303, 307, 308, 400, 401, 403, 429, 500, 503)) {
            var calls = 0
            val secret = jwt(NOW + 3600)
            val body = TrackedBody(secret)
            val client = client { chain ->
                calls++
                response(chain, status, body).newBuilder().header("Location", "https://other.example/?token=$secret").build()
            }
            val provider = AppleMusicDeveloperTokens(TOKEN_URL, AppleMusicTokenBackend(client)::fetch, { NOW })
            val error = expectFailure<AppleMusicTokenException> { provider.developerToken() }
            assertTrue(error.message.orEmpty().contains(status.toString()))
            assertFalse(error.stackTraceToString().contains(secret))
            assertRedactedCauseChain(error)
            assertEquals(1, calls)
            assertTrue(body.closed.get())
        }
    }

    @Test
    fun `IO failures are redacted and retried only on a later request`() = runBlocking {
        var calls = 0
        val secret = jwt(NOW + 3600)
        val client = client { chain ->
            if (++calls == 1) throw IOException(secret)
            response(chain, 200, tokenResponse(NOW + 3600).toResponseBody())
        }
        val provider = AppleMusicDeveloperTokens(TOKEN_URL, AppleMusicTokenBackend(client)::fetch, { NOW })
        val error = expectFailure<AppleMusicTokenException> { provider.developerToken() }
        assertRedactedCauseChain(error)
        assertFalse(error.stackTraceToString().contains(secret))
        assertEquals(1, calls)
        assertEquals(secret, provider.developerToken())
        assertEquals(2, calls)
    }

    @Test
    fun `oversized known and unknown length bodies and malformed JSON are closed and rejected`() = runBlocking {
        for ((text, length) in listOf(
            "x".repeat(65_537) to 65_537L,
            "x".repeat(65_537) to -1L,
            "{private-json" to -1L,
            "" to 0L,
        )) {
            val body = TrackedBody(text, length)
            val client = client { response(it, 200, body) }
            val provider = AppleMusicDeveloperTokens(TOKEN_URL, AppleMusicTokenBackend(client)::fetch, { NOW })
            val error = expectFailure<AppleMusicTokenException> { provider.developerToken() }
            assertTrue(body.closed.get())
            assertRedactedCauseChain(error)
            assertFalse(error.stackTraceToString().contains("private-json"))
        }
    }

    @Test
    fun `body read failures are closed and redacted`() = runBlocking {
        val body = TrackedBody("", source = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long = throw IOException("private-read-failure")
            override fun timeout() = Timeout.NONE
            override fun close() = Unit
        })
        val client = client { response(it, 200, body) }
        val error = expectFailure<AppleMusicTokenException> {
            AppleMusicTokenBackend(client).fetch(TOKEN_URL.toHttpUrl())
        }
        assertTrue(body.closed.get())
        assertRedactedCauseChain(error)
        assertFalse(error.stackTraceToString().contains("private-read-failure"))
    }

    @Test(timeout = 10_000)
    fun `cancellation cancels an enqueued call and closes a late response without reading it`() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val call = AtomicReference<Call>()
        val body = TrackedBody("", source = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long = throw AssertionError("Cancelled response must not be read")
            override fun timeout() = Timeout.NONE
            override fun close() { closed.countDown() }
        })
        val client = client { chain ->
            call.set(chain.call())
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            response(chain, 200, body)
        }
        val request = async(start = CoroutineStart.UNDISPATCHED) { AppleMusicTokenBackend(client).fetch(TOKEN_URL.toHttpUrl()) }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            request.cancelAndJoin()
            assertTrue(request.isCancelled)
            assertTrue(call.get().isCanceled())
        } finally {
            release.countDown()
        }
        assertTrue(closed.await(5, TimeUnit.SECONDS))
        assertTrue(body.closed.get())
    }

    @Test(timeout = 10_000)
    fun `cancellation while reading a body cancels the call and closes the body`() = runBlocking {
        val reading = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val call = AtomicReference<Call>()
        val body = TrackedBody("", source = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                reading.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                throw IOException("private-cancelled-body")
            }
            override fun timeout() = Timeout.NONE
            override fun close() { closed.countDown() }
        })
        val client = client { chain ->
            call.set(chain.call())
            response(chain, 200, body)
        }
        val request = async(start = CoroutineStart.UNDISPATCHED) { AppleMusicTokenBackend(client).fetch(TOKEN_URL.toHttpUrl()) }
        try {
            assertTrue(reading.await(5, TimeUnit.SECONDS))
            request.cancelAndJoin()
            assertTrue(request.isCancelled)
            assertTrue(call.get().isCanceled())
        } finally {
            release.countDown()
        }
        assertTrue(closed.await(5, TimeUnit.SECONDS))
        assertTrue(body.closed.get())
    }

    private fun assertRedactedCauseChain(error: AppleMusicTokenException) {
        // Coroutine stack-trace recovery may attach the original redacted exception.
        generateSequence(error.cause) { it.cause }.forEach { cause ->
            assertEquals(AppleMusicTokenException::class.java, cause.javaClass)
            assertEquals(error.message, cause.message)
        }
    }

    private fun client(intercept: (Interceptor.Chain) -> Response): OkHttpClient =
        appleMusicTokenClient(OkHttpClient()).newBuilder().addInterceptor { intercept(it) }.build().also { clients += it }

    private fun response(chain: Interceptor.Chain, status: Int, body: ResponseBody): Response = Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(status)
        .message("test response")
        .body(body)
        .build()

    private class TrackedBody(
        text: String,
        private val length: Long = -1L,
        source: Source = Buffer().writeUtf8(text),
    ) : ResponseBody() {
        val closed = AtomicBoolean(false)
        private val buffered = object : ForwardingSource(source) {
            override fun close() {
                closed.set(true)
                super.close()
            }
        }.buffer()
        override fun contentType() = "application/json".toMediaType()
        override fun contentLength() = length
        override fun source(): BufferedSource = buffered
    }
}

private fun encode(value: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

// Deliberately not a real signature: these tests check parsing/expiry, not JWT signature verification.
private fun jwt(expiry: Long, marker: String = "test"): String =
    "${encode("""{"alg":"ES256","kid":"test-key"}""")}.${encode("""{"exp":$expiry,"iss":"$marker"}""")}.${encode("test-signature")}"

private fun tokenResponse(expiry: Long, marker: String = "test"): String =
    """{"token":"${jwt(expiry, marker)}","expiresAt":$expiry}"""

private suspend inline fun <reified T : Throwable> expectFailure(block: suspend () -> Unit): T {
    try {
        block()
    } catch (error: Throwable) {
        if (error is T) return error
        throw AssertionError("Expected ${T::class.java.simpleName}, got ${error.javaClass.simpleName}", error)
    }
    throw AssertionError("Expected ${T::class.java.simpleName}")
}
