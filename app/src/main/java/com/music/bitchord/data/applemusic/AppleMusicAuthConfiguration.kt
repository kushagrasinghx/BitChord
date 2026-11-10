package com.music.bitchord.data.applemusic

import java.net.URI
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Preflight matches the developer-token provider's backend-only URL requirements. */
internal fun validAppleMusicAuthBackend(raw: String): Boolean = try {
    val uri = URI(raw)
    val url = raw.toHttpUrlOrNull()
    url != null && url.isHttps && raw == raw.trim() && '\\' !in raw &&
        uri.rawAuthority != null && '@' !in uri.rawAuthority &&
        url.username.isEmpty() && url.password.isEmpty() &&
        uri.rawQuery == null && uri.rawFragment == null &&
        uri.rawPath == url.encodedPath && url.encodedPath.endsWith("/api/applemusic/token")
} catch (_: Exception) {
    false
}
