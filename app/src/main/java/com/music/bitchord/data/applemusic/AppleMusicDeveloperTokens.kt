package com.music.bitchord.data.applemusic

import java.io.IOException
import java.net.URI
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Authenticator
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val REFRESH_SKEW_SECONDS = 60L
private const val MAX_RESPONSE_BYTES = 64 * 1024L
private const val MAX_TOKEN_CHARS = 16 * 1024

internal class AppleMusicTokenException(message: String) : IOException(message)

/** No Android storage or network implementation: both the fetch and epoch-seconds clock are injected. */
internal class AppleMusicDeveloperTokens(
    private val backendUrl: String,
    private val fetch: suspend (HttpUrl) -> String,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val mutex = Mutex()
    private val cacheLock = Any()
    private var generation = 0L
    private var cached: DeveloperToken? = null

    suspend fun developerToken(): String {
        currentCoroutineContext().ensureActive()
        val requestedGeneration = synchronized(cacheLock) { generation }
        return mutex.withLock {
            currentCoroutineContext().ensureActive()
            val hit = synchronized(cacheLock) {
                checkGeneration(requestedGeneration)
                val valid = cached?.takeIf { nowSeconds() < it.expiresAt - REFRESH_SKEW_SECONDS }
                cached = valid
                valid?.token
            }
            if (hit != null) return@withLock hit

            val url = configuredTokenUrl(backendUrl)
            val response = try {
                fetch(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AppleMusicTokenException) {
                currentCoroutineContext().ensureActive()
                throw e
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                // Neither arbitrary transport failures nor JSON parser causes may carry credentials out.
                throw AppleMusicTokenException("Unable to fetch Apple Music developer token")
            }
            currentCoroutineContext().ensureActive()
            val fresh = parseDeveloperToken(response, nowSeconds())
            currentCoroutineContext().ensureActive()
            synchronized(cacheLock) {
                checkGeneration(requestedGeneration)
                cached = fresh
                fresh.token
            }
        }
    }

    /** Synchronous invalidation also covers requests already queued on the mutex. */
    fun clear() = synchronized(cacheLock) {
        generation++
        cached = null
    }

    private fun checkGeneration(requested: Long) {
        if (requested != generation) {
            throw CancellationException("Apple Music developer token request was cleared")
        }
    }
}

private class DeveloperToken(val token: String, val expiresAt: Long)

private fun configuredTokenUrl(raw: String): HttpUrl {
    if (raw.isBlank()) {
        throw AppleMusicTokenException("Apple Music token backend is not configured")
    }
    val invalid = "Apple Music token backend must be an HTTPS /api/applemusic/token URL without credentials, query, or fragment"
    val uri = try {
        URI(raw)
    } catch (_: Exception) {
        throw AppleMusicTokenException(invalid)
    }
    val url = raw.toHttpUrlOrNull() ?: throw AppleMusicTokenException(invalid)
    if (!url.isHttps || raw != raw.trim() || '\\' in raw ||
        uri.rawAuthority == null || '@' in uri.rawAuthority ||
        url.username.isNotEmpty() || url.password.isNotEmpty() ||
        uri.rawQuery != null || uri.rawFragment != null ||
        uri.rawPath != url.encodedPath || !url.encodedPath.endsWith("/api/applemusic/token")
    ) {
        throw AppleMusicTokenException(invalid)
    }
    return url
}

private fun parseDeveloperToken(response: String, now: Long): DeveloperToken {
    val invalid = "Invalid Apple Music developer token response"
    try {
        if (response.length > MAX_RESPONSE_BYTES) throw AppleMusicTokenException(invalid)
        val json = Json.parseToJsonElement(response) as? JsonObject
            ?: throw AppleMusicTokenException(invalid)
        val tokenValue = json["token"] as? JsonPrimitive
        val token = tokenValue?.takeIf { it.isString }?.content
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_TOKEN_CHARS }
            ?: throw AppleMusicTokenException(invalid)
        val expiresAt = epochSeconds(json["expiresAt"] as? JsonPrimitive)
            ?: throw AppleMusicTokenException(invalid)
        val parts = token.split('.')
        if (parts.size != 3 || parts.any { part ->
                part.isEmpty() || part.any { it !in 'a'..'z' && it !in 'A'..'Z' && it !in '0'..'9' && it != '-' && it != '_' }
            }
        ) throw AppleMusicTokenException(invalid)

        val decoder = Base64.getUrlDecoder()
        Json.parseToJsonElement(decoder.decode(parts[0]).decodeToString(throwOnInvalidSequence = true)) as? JsonObject
            ?: throw AppleMusicTokenException(invalid)
        val claims = Json.parseToJsonElement(decoder.decode(parts[1]).decodeToString(throwOnInvalidSequence = true)) as? JsonObject
            ?: throw AppleMusicTokenException(invalid)
        // Check compact-JWT structure, not signature authenticity. Apple verifies the signature;
        // the trusted HTTPS backend is the sole source of developer credentials here.
        decoder.decode(parts[2])
        val expiry = epochSeconds(claims["exp"] as? JsonPrimitive)
        if (expiry != expiresAt) throw AppleMusicTokenException(invalid)
        if (now >= expiresAt - REFRESH_SKEW_SECONDS) {
            throw AppleMusicTokenException("Apple Music developer token is expired or expires too soon")
        }
        return DeveloperToken(token, expiresAt)
    } catch (e: AppleMusicTokenException) {
        throw e
    } catch (_: Exception) {
        // Serialization/Base64 exceptions can quote the offending input. Never retain their causes.
        throw AppleMusicTokenException(invalid)
    }
}

private fun epochSeconds(value: JsonPrimitive?): Long? =
    value?.takeUnless { it.isString }?.longOrNull?.takeIf { it > 0 }

/** Share connection resources, but never inherit another integration's credentials or disk cache. */
internal fun appleMusicTokenClient(base: OkHttpClient): OkHttpClient = base.newBuilder()
    .apply {
        interceptors().clear()
        networkInterceptors().clear()
    }
    .authenticator(Authenticator.NONE)
    .proxyAuthenticator(Authenticator.NONE)
    .cookieJar(CookieJar.NO_COOKIES)
    .cache(null)
    .followRedirects(false)
    .followSslRedirects(false)
    .retryOnConnectionFailure(false)
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .callTimeout(15, TimeUnit.SECONDS)
    .build()

/** Read/close the body on OkHttp's worker, so cancellation and call timeout cover the whole exchange. */
internal class AppleMusicTokenBackend(private val client: OkHttpClient) {
    suspend fun fetch(url: HttpUrl): String = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .cacheControl(CacheControl.Builder().noStore().build())
            .build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(AppleMusicTokenException("Unable to fetch Apple Music developer token"))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!continuation.isActive) {
                    response.close()
                    return
                }
                val result = try {
                    response.use {
                        if (it.code != 200) {
                            throw AppleMusicTokenException("Apple Music token backend returned HTTP ${it.code}")
                        }
                        val body = it.body ?: throw AppleMusicTokenException("Empty Apple Music token response")
                        val source = body.source()
                        if (body.contentLength() > MAX_RESPONSE_BYTES || source.request(MAX_RESPONSE_BYTES + 1)) {
                            throw AppleMusicTokenException("Apple Music token response is too large")
                        }
                        source.readUtf8()
                    }
                } catch (e: AppleMusicTokenException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                    return
                } catch (_: Exception) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(AppleMusicTokenException("Unable to read Apple Music token response"))
                    }
                    return
                }
                if (continuation.isActive) continuation.resume(result)
            }
        })
    }
}
