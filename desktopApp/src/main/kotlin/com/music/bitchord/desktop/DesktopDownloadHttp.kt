package com.music.bitchord.desktop

import com.music.bitchord.data.Http
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * How downloads fetch their bytes.
 *
 * Not `HttpURLConnection`: the JVM's default [java.net.CookieHandler] is [DesktopWebCookies], which
 * holds the Google, YouTube and Spotify session cookies. `HttpURLConnection` sends whatever that
 * handler matches for each URL, so a download from an addon-supplied address on a Google domain
 * would have been sent the account's session. The shared OkHttp client has no cookie jar, so it
 * sends none.
 */
internal object DesktopDownloadHttp {
    val client: OkHttpClient = Http.client.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** A GET for [url] with the download's user agent and the stream's own headers, from [rangeFrom] on. */
    fun request(url: String, userAgent: String, headers: Map<String, String>, rangeFrom: Long? = null): Request =
        Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            // Raw bytes, so the length and Range offsets are the file's own.
            .header("Accept-Encoding", "identity")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .apply { rangeFrom?.let { header("Range", "bytes=$it-") } }
            .build()
}
