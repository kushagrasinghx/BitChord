package com.music.bitchord.data.applemusic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

private val APPLE_MUSIC_API = "https://api.music.apple.com/v1/".toHttpUrl()

/** Encode caller-provided ids/search values instead of interpolating them into URLs. */
internal fun appleMusicUrl(vararg segments: String): HttpUrl = APPLE_MUSIC_API.newBuilder().apply {
    segments.forEach { segment ->
        require(segment.isNotBlank() && segment != "." && segment != "..") {
            "Apple Music path component was empty or invalid"
        }
        addPathSegment(segment)
    }
}.build()

internal data class AppleMusicPage<T>(val items: List<T>, val next: String? = null)

/** Kept internal so offline tests can supply an OkHttp interceptor without a mutable global client. */
internal class AppleMusicRestClient(
    client: OkHttpClient,
    private val auth: () -> AppleMusicLibrary.Auth?,
    private val maxPages: Int = 1_000,
) {
    // Music-User-Token is not a standard credential header: OkHttp's cross-host redirect
    // stripping cannot be relied upon to remove it. Do not follow redirects at all.
    private val client = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .authenticator(Authenticator.NONE)
        .proxyAuthenticator(Authenticator.NONE)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    init {
        require(maxPages > 0) { "Apple Music page limit must be positive" }
    }

    suspend fun get(url: HttpUrl): JsonObject = withContext(Dispatchers.IO) {
        validateAppleMusicUrl(url)
        request(url, headers(url.isUserRequest()))
    }

    suspend fun <T> pages(
        url: HttpUrl,
        parse: (JsonObject) -> AppleMusicPage<T>,
        onPage: (List<T>) -> Unit = {},
    ): List<T> = withContext(Dispatchers.IO) {
        validateAppleMusicUrl(url)
        val userRequest = url.isUserRequest()
        val provider = auth() ?: throw IllegalStateException("Apple Music authentication is not configured")
        val initialHeaders = headers(userRequest, provider)
        val visited = mutableSetOf<HttpUrl>()
        val collected = mutableListOf<T>()
        var current = url
        while (true) {
            currentCoroutineContext().ensureActive()
            check(visited.add(current)) { "Apple Music pagination loop detected" }
            check(visited.size <= maxPages) { "Apple Music pagination page limit exceeded" }
            check(auth() === provider) { "Apple Music authentication changed during pagination" }
            // The developer-token provider owns expiry/refresh. Consult it for every page,
            // but never merge library pages from two different user sessions.
            val pageHeaders = if (visited.size == 1) initialHeaders else headers(userRequest, provider)
            check(auth() === provider && pageHeaders["Music-User-Token"] == initialHeaders["Music-User-Token"]) {
                "Apple Music authentication changed during pagination"
            }
            val page = parse(request(current, pageHeaders))
            collected += page.items
            // A snapshot, not the mutable accumulator retained by subsequent pages.
            onPage(collected.toList())
            val next = page.next ?: break
            current = resolveAppleMusicNext(current, next)
            check(current.isUserRequest() == userRequest) {
                "Apple Music pagination changed authentication scope"
            }
        }
        collected
    }

    private suspend fun headers(
        userRequest: Boolean,
        provider: AppleMusicLibrary.Auth = auth()
            ?: throw IllegalStateException("Apple Music authentication is not configured"),
    ): Map<String, String> {
        try {
            val developer = provider.developerToken().validToken()
                ?: throw IllegalStateException("Apple Music developer token is required")
            val user = if (userRequest) {
                provider.userToken().validToken()
                    ?: throw IllegalStateException("Apple Music user token is required")
            } else {
                null
            }
            return buildMap {
                put("Authorization", "Bearer $developer")
                put("Accept", "application/json")
                user?.let { put("Music-User-Token", it) }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Neither an auth provider exception nor an invalid header value may expose a token.
            throw IllegalStateException(
                if (userRequest) "Apple Music developer and user tokens are required"
                else "Apple Music developer token is required",
            )
        }
    }

    private suspend fun request(url: HttpUrl, headers: Map<String, String>): JsonObject {
        currentCoroutineContext().ensureActive()
        validateAppleMusicUrl(url)
        val request = Request.Builder().url(url)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        val text = client.newCall(request).awaitAppleMusicBody()
        currentCoroutineContext().ensureActive()
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
                ?: throw IllegalStateException("Apple Music response was invalid")
        } catch (_: IllegalArgumentException) {
            throw IllegalStateException("Apple Music response was invalid")
        }
        check((root["errors"] as? JsonArray).isNullOrEmpty()) { "Apple Music API returned an error" }
        return root
    }
}

/** Cancellation covers both waiting for headers and consuming a slow response body. */
internal suspend fun Call.awaitAppleMusicBody(): String = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    if (!continuation.isActive) return@suspendCancellableCoroutine
    try {
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Transport failures can contain URLs or credentials. Do not retain the cause.
                continuation.resumeWith(Result.failure(IllegalStateException("Apple Music request failed")))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use {
                        // A callback may race cancellation. Close its body even when nobody awaits it.
                        if (!continuation.isActive) return
                        if (!it.isSuccessful) {
                            Result.failure(IllegalStateException("Apple Music request failed (HTTP ${it.code})"))
                        } else {
                            // Read on OkHttp's callback thread while the cancellation hook is still
                            // installed, not on the coroutine after resuming with an open response.
                            Result.success(it.body.string())
                        }
                    }
                } catch (_: Exception) {
                    Result.failure(IllegalStateException("Apple Music request failed"))
                }
                continuation.resumeWith(result)
            }
        })
    } catch (_: Exception) {
        continuation.resumeWith(Result.failure(IllegalStateException("Apple Music request failed")))
    }
}

private fun String?.validToken(): String? =
    this?.takeIf { it.isNotBlank() && it.all { char -> char.code in 33..126 } }

private fun HttpUrl.isUserRequest(): Boolean = encodedPath.startsWith("/v1/me/")

internal fun validateAppleMusicUrl(url: HttpUrl) {
    require(
        url.scheme == "https" && url.host == "api.music.apple.com" && url.port == 443 &&
            url.username.isEmpty() && url.password.isEmpty() && url.fragment == null &&
            url.encodedPath.startsWith("/v1/"),
    ) { "Apple Music pagination URL was not allowed" }
}

internal fun resolveAppleMusicNext(current: HttpUrl, next: String): HttpUrl {
    validateAppleMusicUrl(current)
    val resolved = current.resolve(next)
        ?: throw IllegalStateException("Apple Music pagination URL was invalid")
    validateAppleMusicUrl(resolved)
    return resolved
}
