package com.music.bitchord.data.webdav

import com.music.bitchord.data.model.Song
import java.util.Locale

/**
 * WebDAV music library configuration and mapping.
 *
 * A WebDAV server is treated as a remote folder of audio files: tracks are
 * listed with PROPFIND, streamed over plain HTTPS with Basic auth, and shown
 * in the same Songs / Artists / Albums view as on-device files.
 */
object WebDavConfig {

    const val BROWSE_ID = "local:webdav"

    val audioExtensions = setOf(
        "mp3", "m4a", "flac", "ogg", "opus", "aac", "wav", "wma", "aif", "aiff", "webm",
    )

    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    fun isConfigured(url: String): Boolean {
        val normalized = normalizeUrl(url)
        if (!normalized.startsWith("http://", ignoreCase = true) &&
            !normalized.startsWith("https://", ignoreCase = true)
        ) {
            return false
        }
        // "not a url" normalizes to "https://not a url" — only a parseable
        // host counts as configured.
        return hostOf(normalized) != null
    }

    fun basicAuthHeader(username: String, password: String): String? {
        if (username.isBlank() && password.isBlank()) return null
        val credentials = "$username:$password"
        val encoded = java.util.Base64.getEncoder()
            .encodeToString(credentials.toByteArray(Charsets.UTF_8))
        return "Basic $encoded"
    }

    fun hostOf(url: String): String? = runCatching {
        java.net.URI(normalizeUrl(url)).host?.lowercase(Locale.ROOT)
    }.getOrNull()?.takeIf { it.isNotBlank() }

    fun isAudioFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in audioExtensions
    }

    /**
     * Stable track id for a remote file. Prefixed so it never collides with a
     * YouTube id, a MediaStore content URI, or a module source key.
     */
    fun idFor(fileUrl: String): String = "webdav:$fileUrl"

    fun isWebDavId(videoId: String): Boolean = videoId.startsWith("webdav:")

    fun fileUrlOf(videoId: String): String? =
        videoId.removePrefix("webdav:").takeIf { isWebDavId(videoId) && it.startsWith("http") }

    /**
     * A row for a remote file. Title/artist come from the filename —
     * "Artist - Title.ext" splits, anything else is a title by an unknown
     * artist — and the parent folder names the album when there is one.
     */
    fun songFor(fileUrl: String, albumName: String? = null): Song {
        val decoded = runCatching {
            java.net.URLDecoder.decode(fileUrl.substringAfterLast('/'), "UTF-8")
        }.getOrDefault(fileUrl.substringAfterLast('/'))
        return com.music.bitchord.data.remote.RemoteSong.build(
            videoId = idFor(fileUrl),
            streamUrl = fileUrl,
            fileName = decoded,
            albumName = albumName,
            source = "WebDAV",
            browseId = BROWSE_ID,
        )
    }
}

/**
 * In-memory credentials for [com.music.bitchord.data.Http]'s interceptor.
 *
 * Held outside SharedPreferences so the network layer never touches prefs:
 * [com.music.bitchord.data.settings.AppSettings] publishes here on init and
 * on every WebDAV edit.
 */
object WebDavAuth {
    @Volatile var host: String? = null
    @Volatile var authHeader: String? = null

    fun update(url: String, username: String, password: String) {
        host = WebDavConfig.hostOf(url)
        authHeader = WebDavConfig.basicAuthHeader(username, password)
    }

    fun shouldAuthorize(requestHost: String): Boolean {
        val expected = host ?: return false
        val header = authHeader ?: return false
        if (header.isBlank()) return false
        return requestHost.equals(expected, ignoreCase = true)
    }
}
