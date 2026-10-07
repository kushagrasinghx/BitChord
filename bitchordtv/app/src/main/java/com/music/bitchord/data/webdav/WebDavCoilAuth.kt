package com.music.bitchord.data.webdav

import coil3.intercept.Interceptor
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageResult

/**
 * Attaches the WebDAV credential to cover loads aimed at the configured
 * server.
 *
 * Coil fetches with its own transport, which never passes through
 * [Http.client][com.music.bitchord.data.Http]'s interceptor — so without this
 * every in-app cover (rows, sleeve, palette, mesh, widget) goes out without
 * `Authorization`, comes back 401, and reads as "this track has no artwork".
 * ExoPlayer's surfaces never had the problem; they share the app's client.
 *
 * Runs on every image request in the app, so it decides on strings alone and
 * touches nothing else: non-WebDAV hosts and requests that already carry a
 * credential pass through unchanged.
 */
class WebDavCoilAuth : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val header = authHeaderFor(request.data, request.httpHeaders) ?: return chain.proceed()
        val headers = NetworkHeaders.Builder(request.httpHeaders)
            .set("Authorization", header)
            .build()
        return chain.withRequest(request.newBuilder().httpHeaders(headers).build()).proceed()
    }

    companion object {
        /**
         * The credential for [data]'s URL, or null when this request wants
         * nothing added. Pure so it stays unit-testable without a request.
         */
        fun authHeaderFor(data: Any?, headers: NetworkHeaders = NetworkHeaders.EMPTY): String? {
            if (headers["Authorization"] != null) return null
            val url = (data as? String)?.takeIf { it.startsWith("http") } ?: return null
            val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return null
            if (!WebDavAuth.shouldAuthorize(host)) return null
            return WebDavAuth.authHeader
        }
    }
}
