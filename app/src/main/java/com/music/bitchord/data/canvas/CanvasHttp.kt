package com.music.bitchord.data.canvas

import com.music.bitchord.data.Http
import okhttp3.Request

/**
 * A plain GET returning the body, or null for anything that isn't a 2xx or
 * that throws. Canvas is decoration: no provider failure is allowed to reach
 * the caller, and none of these hosts are ours to depend on.
 *
 * Shares [Http.client] with the rest of the app so these lookups reuse its
 * connection pool rather than standing up a second HTTP stack.
 */
internal fun canvasGet(url: String, headers: Map<String, String> = emptyMap()): String? {
    val request = Request.Builder().url(url).apply {
        headers.forEach { (name, value) -> header(name, value) }
    }.build()
    return runCatching {
        Http.client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }.getOrNull()
}

/**
 * Same as [canvasGet], but keeps the status code even on failure — for the
 * few callers where "it wasn't a 2xx" needs to say *which* code, rather than
 * collapsing every kind of failure into the same null.
 */
internal fun canvasGetWithStatus(url: String, headers: Map<String, String> = emptyMap()): Pair<Int, String?> {
    val request = Request.Builder().url(url).apply {
        headers.forEach { (name, value) -> header(name, value) }
    }.build()
    return runCatching {
        Http.client.newCall(request).execute().use { response ->
            response.code to if (response.isSuccessful) response.body?.string() else null
        }
    }.getOrDefault(-1 to null)
}

/** The browser UA these catalog endpoints expect; they 403 an unknown one. */
internal const val CANVAS_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/122.0.0.0 Safari/537.36"
