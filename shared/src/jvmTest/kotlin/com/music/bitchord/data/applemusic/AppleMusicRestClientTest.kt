package com.music.bitchord.data.applemusic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppleMusicRestClientTest {
    private val clients = mutableListOf<OkHttpClient>()

    @AfterTest
    fun closeClients() {
        clients.forEach { client ->
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private class TestAuth(
        var developer: String? = "developer-secret",
        var user: String? = "user-secret",
    ) : AppleMusicLibrary.Auth {
        var developerCalls = 0
        var userCalls = 0
        override suspend fun developerToken(): String? {
            developerCalls++
            return developer
        }
        override suspend fun userToken(): String? {
            userCalls++
            return user
        }
    }

    private data class Reply(val body: String, val status: Int = 200)

    /** Application interceptors short-circuit all networking and retain the real API request URL. */
    private inner class FakeApi(
        replies: List<Reply> = listOf(Reply("""{"data":[]}""")),
        var provider: AppleMusicLibrary.Auth? = TestAuth(),
        maxPages: Int = 1_000,
    ) {
        val requests = mutableListOf<Request>()
        private val responses = ArrayDeque(replies)
        val rest = AppleMusicRestClient(
            OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                requests += request
                val reply = responses.removeFirst()
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(reply.status)
                    .message("Test response")
                    .body(reply.body.toResponseBody("application/json".toMediaType()))
                    .build()
            }.build().also { clients += it },
            { provider },
            maxPages,
        )
    }

    private val songsUrl = appleMusicUrl("me", "library", "songs")
    private val catalogUrl = appleMusicUrl("catalog", "us", "songs")

    @Test
    fun catalogRequiresOnlyDeveloperAuthAndNeverCallsOrForwardsUserAuth() = runBlocking {
        val auth = TestAuth(user = null)
        val api = FakeApi(provider = auth)
        assertTrue(api.rest.pages(catalogUrl, ::parseTrackPage).isEmpty())
        val request = api.requests.single()
        assertEquals("Bearer developer-secret", request.header("Authorization"))
        assertEquals("application/json", request.header("Accept"))
        assertNull(request.header("Music-User-Token"))
        assertEquals(1, auth.developerCalls)
        assertEquals(0, auth.userCalls)
    }

    @Test
    fun evenWhenAvailableUserTokenIsNotSentToCatalogEndpoints() = runBlocking {
        val auth = TestAuth()
        val api = FakeApi(provider = auth)
        api.rest.get(catalogUrl)
        assertNull(api.requests.single().header("Music-User-Token"))
        assertEquals(0, auth.userCalls)
    }

    @Test
    fun userEndpointsSendBothHeaders() = runBlocking {
        val api = FakeApi()
        api.rest.get(appleMusicUrl("me", "storefront"))
        val request = api.requests.single()
        assertEquals("Bearer developer-secret", request.header("Authorization"))
        assertEquals("user-secret", request.header("Music-User-Token"))
    }

    @Test
    fun absentOrBlankTokensFailBeforeNetworking() = runBlocking {
        for (provider in listOf(null, TestAuth(developer = null), TestAuth(developer = "  "))) {
            val api = FakeApi(provider = provider)
            assertFailsWith<IllegalStateException> { api.rest.get(catalogUrl) }
            assertTrue(api.requests.isEmpty())
        }
        for (provider in listOf(TestAuth(user = null), TestAuth(user = ""), TestAuth(developer = null))) {
            val api = FakeApi(provider = provider)
            assertFailsWith<IllegalStateException> { api.rest.get(songsUrl) }
            assertTrue(api.requests.isEmpty())
        }
    }

    @Test
    fun invalidHeaderValuesAndAuthProviderFailuresDoNotLeakTokens() = runBlocking {
        val api = FakeApi(provider = TestAuth(developer = "developer-secret\ninjected-header"))
        val invalid = assertFailsWith<IllegalStateException> { api.rest.get(catalogUrl) }
        assertSafeError(invalid)
        assertTrue(api.requests.isEmpty())

        val provider = object : AppleMusicLibrary.Auth {
            override suspend fun developerToken(): String? = error("developer-secret user-secret")
            override suspend fun userToken(): String? = error("Not needed")
        }
        val failing = FakeApi(provider = provider)
        assertSafeError(assertFailsWith<IllegalStateException> { failing.rest.get(catalogUrl) })
    }

    @Test
    fun authCancellationIsNotConvertedIntoAnAuthenticationError() = runBlocking {
        val provider = object : AppleMusicLibrary.Auth {
            override suspend fun developerToken(): String? = throw CancellationException("cancelled")
            override suspend fun userToken(): String? = null
        }
        val api = FakeApi(provider = provider)
        assertFailsWith<CancellationException> { api.rest.get(catalogUrl) }
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun followsRelativeAndAbsoluteNextLinksAndPublishesCumulativeSnapshots() = runBlocking {
        val api = FakeApi(listOf(
            Reply("""{"data":[{"id":"i.1","type":"library-songs"}],"next":"?offset=1"}"""),
            Reply("""{"data":[{"id":"i.2","type":"library-songs"}],"next":"https://api.music.apple.com/v1/me/library/songs?offset=2"}"""),
            Reply("""{"data":[{"id":"i.3","type":"library-songs"}]}"""),
        ))
        val snapshots = mutableListOf<List<AppleMusicTrack>>()
        val result = api.rest.pages(songsUrl, ::parseTrackPage) { snapshots += it }
        assertEquals(listOf("i.1", "i.2", "i.3"), result.map { it.id })
        assertEquals(listOf(listOf("i.1"), listOf("i.1", "i.2"), listOf("i.1", "i.2", "i.3")),
            snapshots.map { page -> page.map { it.id } })
        assertEquals(listOf(null, "1", "2"), api.requests.map { it.url.queryParameter("offset") })
        assertTrue(api.requests.all { it.header("Music-User-Token") == "user-secret" })
    }

    @Test
    fun developerTokenIsRefreshedBetweenPagesWithoutChangingTheUserSession() = runBlocking {
        val auth = TestAuth()
        val api = FakeApi(
            listOf(
                Reply("""{"data":[{"id":"i.1","type":"library-songs"}],"next":"?offset=1"}"""),
                Reply("""{"data":[{"id":"i.2","type":"library-songs"}]}"""),
            ),
            provider = auth,
        )
        val tracks = api.rest.pages(songsUrl, ::parseTrackPage) {
            auth.developer = "refreshed-developer-secret"
        }
        assertEquals(listOf("i.1", "i.2"), tracks.map { it.id })
        assertEquals(listOf("Bearer developer-secret", "Bearer refreshed-developer-secret"),
            api.requests.map { it.header("Authorization") })
        assertTrue(api.requests.all { it.header("Music-User-Token") == "user-secret" })
        assertEquals(2, auth.developerCalls)
    }

    @Test
    fun changingUserSessionsAbortsBeforeFetchingAnotherPage() = runBlocking {
        for (replacement in listOf("another-user-secret", null)) {
            val auth = TestAuth()
            val api = FakeApi(
                listOf(Reply("""{"data":[],"next":"?offset=1"}""")),
                provider = auth,
            )
            val error = assertFailsWith<IllegalStateException> {
                api.rest.pages(songsUrl, ::parseTrackPage) { auth.user = replacement }
            }
            assertSafeError(error)
            assertFalse(error.stackTraceToString().contains("another-user-secret"))
            assertEquals(1, api.requests.size)
        }
    }

    @Test
    fun replacingTheAuthProviderAbortsBeforeFetchingAnotherPage() = runBlocking {
        val api = FakeApi(listOf(Reply("""{"data":[],"next":"?offset=1"}""")))
        val error = assertFailsWith<IllegalStateException> {
            api.rest.pages(songsUrl, ::parseTrackPage) { api.provider = TestAuth() }
        }
        assertEquals("Apple Music authentication changed during pagination", error.message)
        assertEquals(1, api.requests.size)
    }

    @Test
    fun filteredOrEmptyPagesDoNotTerminatePaginationWhileThereIsANextLink() = runBlocking {
        val api = FakeApi(listOf(
            Reply("""{"results":{"songs":{"data":[{"id":"v","type":"music-videos"}],"next":"/v1/catalog/us/search?offset=25"}}}"""),
            Reply("""{"results":{"songs":{"data":[],"next":"/v1/catalog/us/search?offset=50"}}}"""),
            Reply("""{"results":{"songs":{"data":[{"id":"s","type":"songs"}]}}}"""),
        ))
        val tracks = api.rest.pages(appleMusicUrl("catalog", "us", "search"), ::parseSearchSongsPage)
        assertEquals(listOf("s"), tracks.map { it.id })
        assertEquals(3, api.requests.size)
    }

    @Test
    fun hostileNextLinksAreRejectedBeforeAnyCredentialsCanBeForwarded() = runBlocking {
        val hostile = listOf(
            "https://evil.example/v1/me/library/songs",
            "//evil.example/v1/me/library/songs",
            "https://api.music.apple.com.evil.example/v1/me/library/songs",
            "http://api.music.apple.com/v1/me/library/songs",
            "https://api.music.apple.com:444/v1/me/library/songs",
            "https://user:password@api.music.apple.com/v1/me/library/songs",
            "https://api.music.apple.com/not-v1/songs",
            "/v1/../outside",
            "/v1/me/library/songs#fragment",
        )
        for (next in hostile) {
            val api = FakeApi(listOf(Reply("""{"data":[],"next":"$next"}""")))
            val failure = assertFailsWith<IllegalArgumentException> {
                api.rest.pages(songsUrl, ::parseTrackPage)
            }
            assertSafeError(failure)
            assertEquals(1, api.requests.size, next)
        }
    }

    @Test
    fun unsafeInitialUrlsAreRejectedBeforeAuthOrNetwork() = runBlocking {
        val auth = TestAuth()
        val api = FakeApi(provider = auth)
        assertFailsWith<IllegalArgumentException> {
            api.rest.get("https://evil.example/v1/me/library/songs".toHttpUrl())
        }
        assertTrue(api.requests.isEmpty())
        assertEquals(0, auth.developerCalls)
    }

    @Test
    fun nextLinksCannotChangeBetweenCatalogAndUserAuthenticationScopes() = runBlocking {
        for ((initial, next) in listOf(
            catalogUrl to "/v1/me/library/songs",
            songsUrl to "/v1/catalog/us/songs",
        )) {
            val api = FakeApi(listOf(Reply("""{"data":[],"next":"$next"}""")))
            assertFailsWith<IllegalStateException> { api.rest.pages(initial, ::parseTrackPage) }
            assertEquals(1, api.requests.size)
        }
    }

    @Test
    fun repeatedUrlsAreRejectedInsteadOfLooping() = runBlocking {
        val api = FakeApi(listOf(
            Reply("""{"data":[],"next":"?offset=1"}"""),
            Reply("""{"data":[],"next":"/v1/me/library/songs"}"""),
        ))
        val failure = assertFailsWith<IllegalStateException> { api.rest.pages(songsUrl, ::parseTrackPage) }
        assertTrue(failure.message.orEmpty().contains("loop"))
        assertEquals(2, api.requests.size)
    }

    @Test
    fun continuallyChangingNextUrlsStillHitThePageBound() = runBlocking {
        val api = FakeApi(
            listOf(Reply("""{"data":[],"next":"?offset=1"}""")),
            maxPages = 1,
        )
        val failure = assertFailsWith<IllegalStateException> { api.rest.pages(songsUrl, ::parseTrackPage) }
        assertTrue(failure.message.orEmpty().contains("page limit"))
        assertEquals(1, api.requests.size)
    }

    @Test
    fun httpAndApiErrorsNeverExposeResponseTextOrTokens() = runBlocking {
        for (status in listOf(301, 302, 307, 308, 401, 403, 429, 500)) {
            val api = FakeApi(listOf(Reply("developer-secret user-secret", status)))
            val error = assertFailsWith<IllegalStateException> { api.rest.get(songsUrl) }
            assertEquals("Apple Music request failed (HTTP $status)", error.message)
            assertSafeError(error)
        }
        val api = FakeApi(listOf(Reply("""{"errors":[{"detail":"developer-secret user-secret"}]}""")))
        assertSafeError(assertFailsWith<IllegalStateException> { api.rest.get(songsUrl) })
    }

    @Test
    fun malformedJsonErrorsNeverIncludePayloadSnippets() = runBlocking {
        val api = FakeApi(listOf(Reply("""{"data":developer-secret user-secret""")))
        val error = assertFailsWith<IllegalStateException> { api.rest.get(songsUrl) }
        assertEquals("Apple Music response was invalid", error.message)
        assertSafeError(error)
    }

    @Test
    fun transportErrorsAreSanitized() = runBlocking {
        val rest = AppleMusicRestClient(
            OkHttpClient.Builder().addInterceptor {
                throw IOException("developer-secret user-secret")
            }.build().also { clients += it },
            { TestAuth() },
        )
        assertSafeError(assertFailsWith<IllegalStateException> { rest.get(songsUrl) })
    }

    @Test
    fun redirectsAreActuallyDisabledInTheOkHttpTransport() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/steal")))
            server.enqueue(MockResponse().setBody("""{"data":[]}"""))
            // Only the test interceptor rewrites the validated API request to loopback.
            // Unlike a synthetic interceptor response, this exercises OkHttp's redirect machinery.
            val client = OkHttpClient.Builder().followRedirects(true).addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url("/v1/me/library/songs")).build())
            }.build().also { clients += it }
            val rest = AppleMusicRestClient(client, { TestAuth() })
            val error = assertFailsWith<IllegalStateException> { rest.get(songsUrl) }
            assertEquals("Apple Music request failed (HTTP 302)", error.message)
            val initial = server.takeRequest(1, TimeUnit.SECONDS)
            assertEquals("Bearer developer-secret", initial?.getHeader("Authorization"))
            assertEquals("user-secret", initial?.getHeader("Music-User-Token"))
            assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS), "redirect must not receive credentials")
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun pathAndQueryValuesAreEncodedRatherThanInterpolated() {
        val url = appleMusicUrl("catalog", "us", "playlists", "pl.id?extra=1/segment").newBuilder()
            .addQueryParameter("term", "A & B + café")
            .addQueryParameter("filter[isrc]", "USAAA2400001")
            .build()
        assertEquals("api.music.apple.com", url.host)
        assertEquals(listOf("v1", "catalog", "us", "playlists", "pl.id?extra=1/segment"), url.pathSegments)
        assertEquals("A & B + café", url.queryParameter("term"))
        assertEquals("USAAA2400001", url.queryParameter("filter[isrc]"))
        assertNull(url.queryParameter("extra"))
        assertFailsWith<IllegalArgumentException> { appleMusicUrl("catalog", "..", "songs") }
    }

    private fun assertSafeError(error: Throwable) {
        val text = error.stackTraceToString()
        assertFalse(text.contains("developer-secret"))
        assertFalse(text.contains("user-secret"))
        // Coroutine stack-trace recovery can attach the original sanitized exception.
        generateSequence(error.cause) { it.cause }.forEach { cause ->
            assertEquals(error::class, cause::class)
            assertEquals(error.message, cause.message)
        }
    }
}
