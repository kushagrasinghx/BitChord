package com.music.bitchord.mcp

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.SecureRandom

/** Private, deny-by-default policy. Creating a playlist never grants access to it. */
object McpSettings {
    private lateinit var prefs: SharedPreferences
    val permissions = listOf("now_playing", "history", "queue", "playback", "seek", "search", "playlist_read", "playlist_create", "playlist_write")
    @Volatile var onChanged: (() -> Unit)? = null
    val port: Int = 8765

    @Synchronized fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("mcp_settings", Context.MODE_PRIVATE)
        if (prefs.getString("token", null).isNullOrBlank()) rotateToken()
    }

    var enabled: Boolean
        get() = ::prefs.isInitialized && prefs.getBoolean("enabled", false)
        set(value) { if (enabled != value) { prefs.edit().putBoolean("enabled", value).apply(); onChanged?.invoke() } }
    var lanEnabled: Boolean
        get() = ::prefs.isInitialized && prefs.getBoolean("lan", false)
        set(value) { if (lanEnabled != value) { prefs.edit().putBoolean("lan", value).apply(); onChanged?.invoke() } }
    var token: String
        get() = if (::prefs.isInitialized) prefs.getString("token", "").orEmpty() else ""
        set(value) {
            require(value.length >= 43) { "Token must be at least 43 characters" }
            prefs.edit().putString("token", value).apply()
            onChanged?.invoke()
        }
    fun rotateToken() {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        token = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
    fun allowed(permission: String): Boolean =
        ::prefs.isInitialized && permission in permissions && prefs.getBoolean("permission_$permission", false)
    fun setAllowed(permission: String, allowed: Boolean) {
        require(permission in permissions)
        prefs.edit().putBoolean("permission_$permission", allowed).apply()
    }
    private fun canonicalId(id: String) = id.removePrefix("local:playlist:")
    fun playlistAllowed(id: String): Boolean = ::prefs.isInitialized &&
        canonicalId(id) in prefs.getStringSet("playlists", emptySet()).orEmpty()
    /** Call only after explicit user selection, never from a create tool. */
    fun grantPlaylist(id: String) = setPlaylistAllowed(id, true)
    fun setPlaylistAllowed(id: String, allowed: Boolean) {
        val key = canonicalId(id)
        require(key.isNotBlank())
        val ids = prefs.getStringSet("playlists", emptySet()).orEmpty().toMutableSet()
        if (allowed) ids.add(key) else ids.remove(key)
        prefs.edit().putStringSet("playlists", ids).apply()
    }
}
