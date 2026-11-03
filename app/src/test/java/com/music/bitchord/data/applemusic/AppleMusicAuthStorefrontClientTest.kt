package com.music.bitchord.data.applemusic

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

import org.junit.Test

class AppleMusicAuthStorefrontClientTest {
    @Test
    fun `candidate credentials are sent only to the fixed official storefront endpoint`() = runBlocking {
        var captured: Request? = null
        val client = client { chain ->
            captured = chain.request()
            response(chain.request(), 200, storefront)
        }
        assertEquals("gb", client.resolve("developer-secret", "user-secret"))
        assertEquals("https://api.music.apple.com/v1/me/storefront", captured?.url.toString())
        assertEquals("Bearer developer-secret", captured?.header("Authorization"))
        assertEquals("user-secret", captured?.header("Music-User-Token"))
        assertEquals("no-store", captured?.header("Cache-Control"))
    }

    @Test
    fun `HTTP denial and backend failures remain distinct without retaining response content`() = runBlocking {
        for ((code, problem) in mapOf(
            401 to AppleMusicAuthProblem.BACKEND,
            403 to AppleMusicAuthProblem.AUTHORIZATION_DENIED,
            429 to AppleMusicAuthProblem.STOREFRONT,
            500 to AppleMusicAuthProblem.STOREFRONT,
        )) {
            val client = client { chain -> response(chain.request(), code, "secret-in-error-body") }
            val error = failure { client.resolve("developer-secret", "user-secret") }
            assertEquals(problem, error.problem)
            assertFalse(error.stackTraceToString().contains("secret"))
        }
    }

    @Test
    fun `malformed ambiguous and oversized storefront responses never authorize a guessed locale`() = runBlocking {
        val invalid = listOf(
            "secret-parser-input",
            "{}",
            "{\"data\":[]}",
            "{\"data\":[{\"id\":\"gb\",\"type\":\"songs\"}]}",
            "{\"data\":[{\"id\":\"GB\",\"type\":\"storefronts\"}]}",
            "{\"data\":[{\"id\":\"gb\",\"type\":\"storefronts\"},{\"id\":\"us\",\"type\":\"storefronts\"}]}",
            "{\"errors\":[{\"detail\":\"secret\"}],\"data\":[{\"id\":\"gb\",\"type\":\"storefronts\"}]}",
            "x".repeat(65537),
        )
        for (body in invalid) {
            val client = client { chain -> response(chain.request(), 200, body) }
            val error = failure { client.resolve("developer-secret", "user-secret") }
            assertEquals(AppleMusicAuthProblem.STOREFRONT, error.problem)
            assertFalse(error.stackTraceToString().contains("secret"))
        }
    }

    @Test
    fun `invalid candidate tokens do not send any request`() = runBlocking {
        var requests = 0
        val client = client { chain -> requests++; response(chain.request(), 200, storefront) }
        failure { client.resolve("developer\nsecret", "user-secret") }
        failure { client.resolve("developer-secret", "") }
        assertEquals(0, requests)
    }

    @Test
    fun `transport errors cannot expose their credential bearing cause`() = runBlocking {
        val client = client { throw IOException("developer-secret user-secret") }
        val error = failure { client.resolve("developer-secret", "user-secret") }
        assertEquals(AppleMusicAuthProblem.STOREFRONT, error.problem)
        assertFalse(error.stackTraceToString().contains("secret"))
        assertEquals(null, error.cause)
    }

    @Test
    fun `redirect responses are rejected rather than interpreted as an authorized storefront`() = runBlocking {
        for (location in listOf("https://backend.example/api/applemusic/token", "https://api.music.apple.com/v1/catalog/gb/songs")) {
            val calls = AtomicInteger()

            val base = OkHttpClient.Builder().addInterceptor { chain ->
                calls.incrementAndGet()
                response(chain.request(), 302, "").newBuilder().header("Location", location).build()
            }.build()
            val error = failure { AppleMusicAuthStorefrontClient(base).resolve("developer-secret", "user-secret") }
            assertEquals(AppleMusicAuthProblem.STOREFRONT, error.problem)
            assertEquals(1, calls.get())
        }
    }

    private fun client(interceptor: Interceptor): AppleMusicAuthStorefrontClient = AppleMusicAuthStorefrontClient(
        OkHttpClient.Builder().addInterceptor(interceptor).build(),
    )

    private fun response(request: Request, code: Int, body: String): Response = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
        .body(body.toResponseBody("application/json".toMediaType())).build()

    private suspend fun failure(block: suspend () -> Unit): AppleMusicAuthException = withTimeout(5_000) {
        try {
            block()
            throw AssertionError("Expected Apple Music storefront validation failure")
        } catch (error: AppleMusicAuthException) {
            error
        }
    }

    private val storefront = "{\"data\":[{\"id\":\"gb\",\"type\":\"storefronts\"}]}"
}
