package com.music.bitchord.data.webdav

/**
 * In-memory credentials for [com.music.bitchord.data.Http]'s interceptor.
 *
 * Held outside SharedPreferences so the network layer never touches prefs:
 * [com.music.bitchord.data.settings.AppSettings] publishes here on init and
 * on every WebDAV edit.
 */
object WebDavAuth {
    class Snapshot(private val host: String?, val authHeader: String?) {
        fun headerFor(requestHost: String): String? = authHeader?.takeIf {
            it.isNotBlank() && requestHost.equals(host, ignoreCase = true)
        }
    }

    @Volatile var current = Snapshot(null, null)
        private set
    val authHeader: String? get() = current.authHeader

    /** [host] and [header] as WebDavConfig derives them from the saved server. */
    fun update(host: String?, header: String?) {
        current = Snapshot(host, header)
    }

    fun shouldAuthorize(requestHost: String): Boolean = current.headerFor(requestHost) != null
}
