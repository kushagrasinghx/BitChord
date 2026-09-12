package com.music.bitchord.desktop

import java.net.http.HttpClient
import java.time.Duration

/** One connection pool for YouTube player, probe, JS and media requests. */
internal object DesktopYouTubeHttpClient {
    val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
}
