package com.music.bitchord.data.applemusic

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Validates candidate credentials without installing a temporary global user session.
 * The supplied client must be credential/cache/interceptor-free (appleMusicTokenClient).
 * No redirect can forward the Music-User-Token, including to the developer-token backend.
 */
internal class AppleMusicAuthStorefrontClient(client: OkHttpClient) {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    suspend fun resolve(developerToken: String, userToken: String): String {
        currentCoroutineContext().ensureActive()
        if (!validAuthToken(developerToken) || !validAuthToken(userToken)) {
            throw AppleMusicAuthStorefrontException(AppleMusicAuthProblem.AUTHORIZATION_DENIED)
        }
        val request = Request.Builder()
            .url("https://api.music.apple.com/v1/me/storefront")
            .header("Authorization", "Bearer $developerToken")
            .header("Music-User-Token", userToken)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-store")
            .build()
        val body = awaitBody(client.newCall(request))
        currentCoroutineContext().ensureActive()
        return try {
            val root = Json.parseToJsonElement(body) as? JsonObject
            val errors = root?.get("errors") as? JsonArray
            val data = root?.get("data") as? JsonArray
            val row = data?.singleOrNull() as? JsonObject
            val type = row?.get("type") as? JsonPrimitive
            val id = row?.get("id") as? JsonPrimitive
            if (!errors.isNullOrEmpty() || type?.isString != true || type.content != "storefronts" ||
                id?.isString != true || !id.content.matches(Regex("[a-z]{2}"))
            ) throw AppleMusicAuthStorefrontException(AppleMusicAuthProblem.STOREFRONT)
            id.content
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // JSON exceptions can quote their input, so do not retain a parser cause.
            throw AppleMusicAuthStorefrontException(AppleMusicAuthProblem.STOREFRONT)
        }
    }

    private suspend fun awaitBody(call: Call): String = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        if (!continuation.isActive) return@suspendCancellableCoroutine
        try {
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(
                        AppleMusicAuthStorefrontException(AppleMusicAuthProblem.STOREFRONT),
                    )
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = try {
                        response.use {
                            if (!continuation.isActive) return
                            val problem = when (it.code) {
                                200 -> null
                                401 -> AppleMusicAuthProblem.BACKEND
                                403 -> AppleMusicAuthProblem.AUTHORIZATION_DENIED
                                else -> AppleMusicAuthProblem.STOREFRONT
                            }
                            if (problem != null) throw AppleMusicAuthStorefrontException(problem)
                            val body = it.body
                            if (body.contentLength() > MAX_BODY || body.source().request(MAX_BODY + 1)) {
                                throw AppleMusicAuthStorefrontException(AppleMusicAuthProblem.STOREFRONT)
                            }
                            body.source().readUtf8()
                        }
                    } catch (error: AppleMusicAuthStorefrontException) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                        return
                    } catch (_: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(
                            AppleMusicAuthStorefrontException(AppleMusicAuthProblem.STOREFRONT),
                        )
                        return
                    }
                    if (continuation.isActive) continuation.resume(body)
                }
            })
        } catch (_: Exception) {
            if (continuation.isActive) continuation.resumeWithException(
                AppleMusicAuthStorefrontException(AppleMusicAuthProblem.STOREFRONT),
            )
        }
    }

    private companion object {
        const val MAX_BODY = 64 * 1024L
    }
}

internal class AppleMusicAuthStorefrontException(problem: AppleMusicAuthProblem) : AppleMusicAuthException(problem)
