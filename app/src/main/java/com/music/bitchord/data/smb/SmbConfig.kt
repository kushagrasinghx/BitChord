package com.music.bitchord.data.smb

import com.music.bitchord.data.model.Song
import java.util.Locale

/**
 * SMB file-share music library configuration and mapping — the same shape as
 * [WebDavConfig][com.music.bitchord.data.webdav.WebDavConfig], for a
 * protocol that has no URLs of its own.
 *
 * Tracks are addressed as `smb://host/share/path/to/file`. Credentials never
 * ride in the URI (it is logged, cached and keyed on); readers authenticate
 * from [SmbAuth] at open time instead.
 */
object SmbConfig {

    const val BROWSE_ID = "local:smb"
    const val SCHEME = "smb"
    const val DEFAULT_PORT = 445

    val audioExtensions = setOf(
        "mp3", "m4a", "flac", "ogg", "opus", "aac", "wav", "wma", "aif", "aiff", "webm",
    )

    val imageExtensions = setOf("jpg", "jpeg", "png", "webp")

    fun normalizeHost(raw: String): String = splitHostPort(raw).first

    /**
     * `host`, `host:port` or `smb://host:port/…` — the port defaults to 445.
     * Bracketed IPv6 (`[::1]:445`) keeps its colons.
     */
    fun splitHostPort(raw: String): Pair<String, Int> {
        val authority = raw.trim().removePrefix("smb://").removePrefix("\\\\")
            .substringBefore('/').substringBefore('\\')
        if (authority.startsWith("[")) {
            val host = authority.substringAfter('[').substringBefore(']').lowercase(Locale.ROOT)
            val port = authority.substringAfter("]:", "").toIntOrNull()
            return host to validPort(port)
        }
        val port = authority.substringAfterLast(':', "").takeIf { ':' in authority }?.toIntOrNull()
        val host = authority.substringBeforeLast(':').lowercase(Locale.ROOT).trim()
        return host to validPort(port)
    }

    private fun validPort(port: Int?): Int =
        if (port != null && port in 1..65535) port else DEFAULT_PORT

    fun isConfigured(host: String, share: String): Boolean =
        normalizeHost(host).isNotBlank() && share.trim().isNotBlank()

    fun isAudioFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in audioExtensions
    }

    fun isImageFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in imageExtensions
    }

    /**
     * Stable track id. Prefixed so it never collides with a YouTube id, a
     * MediaStore URI, a module key or a WebDAV id.
     */
    fun idFor(host: String, share: String, path: String): String =
        "smb:${normalizeHost(host)}/${share.trim().trim('/')}/${path.trim('/')}".trimEnd('/')

    fun isSmbId(videoId: String): Boolean = videoId.startsWith("smb:")

    private fun libraryPath(path: String, basePath: String): String {
        val trimmed = path.trim('/')
        val base = basePath.trim('/')
        return if (base.isNotEmpty() && trimmed.startsWith("$base/")) trimmed.substring(base.length + 1) else trimmed
    }

    fun isSmbUrl(url: String): Boolean = url.startsWith("smb://")

    /**
     * `smb://host/share/a/b.mp3` back to the share-relative path `a/b.mp3`.
     * Pure string work so it stays unit-testable off the framework stub. The
     * share segment must name [share]: a URL baked under a previous share
     * fails loudly rather than opening a same-named stranger.
     */
    fun relativePath(streamUrl: String, share: String): String? {
        // [host, share, ...path]. These URIs never carry userinfo — see the
        // note on SmbAuth — so there is nothing to strip.
        val segments = streamUrl.removePrefix("smb://").split('/').filter { it.isNotBlank() }
        if (segments.size < 3) return null
        if (!segments[1].equals(share.trim().trim('/'), ignoreCase = true)) return null
        val path = segments.drop(2).joinToString("/").substringBefore('?').substringBefore('#')
        return path.takeIf { it.isNotBlank() }
    }

    /** [basePath] is the configured folder: credits read from the library below it. */
    fun songFor(host: String, share: String, path: String, basePath: String = ""): Song {
        val streamUrl = "smb://${normalizeHost(host)}/${share.trim().trim('/')}/${path.trim('/')}"
        return com.music.bitchord.data.remote.RemoteSong.build(
            videoId = idFor(host, share, path),
            streamUrl = streamUrl,
            credit = com.music.bitchord.data.remote.RemoteSong.credit(libraryPath(path, basePath)),
            source = "SMB",
            browseId = BROWSE_ID,
        )
    }
}

/**
 * Live connection parameters for [SmbConnection] and company.
 *
 * Held outside SharedPreferences so the network layer never touches storage:
 * [AppSettings][com.music.bitchord.data.settings.AppSettings] publishes here
 * on init and on every edit, which also drops the pooled connection — a
 * server or credential change must never keep reading on the old session.
 */
object SmbAuth {
    @Volatile var host: String = ""
    @Volatile var port: Int = SmbConfig.DEFAULT_PORT
    @Volatile var share: String = ""
    @Volatile var basePath: String = ""
    @Volatile var username: String = ""
    @Volatile var password: String = ""

    fun update(host: String, share: String, basePath: String, username: String, password: String) {
        val (parsedHost, parsedPort) = SmbConfig.splitHostPort(host)
        this.host = parsedHost
        this.port = parsedPort
        this.share = share.trim().trim('/')
        this.basePath = basePath.trim().trim('/').replace('\\', '/')
        this.username = username.trim()
        this.password = password
        SmbConnection.invalidate()
    }

    fun isConfigured(): Boolean = SmbConfig.isConfigured(host, share)
}
