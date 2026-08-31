package com.music.bitchord.auth

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.DebugLog as Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted-at-rest storage for credentials.
 *
 * Two live here: the YouTube Music session cookie, and — if the user turns on
 * the Discord integration — that account's own bearer token. Neither is a
 * password: the Google one is typed into accounts.google.com inside a WebView,
 * and the Discord one is read out of a completed login session. But both grant
 * full access to their account, so they don't go in the plain prefs the
 * scrobbler tokens use.
 *
 * Keystore init fails on a handful of OEM builds, so it degrades to plain
 * prefs rather than crashing on launch.
 */
class AuthStore(context: Context) {

    private val prefs: SharedPreferences = runCatching {
        EncryptedSharedPreferences.create(
            context,
            "bitchord_auth",
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        Log.w("BitChord", "EncryptedSharedPreferences unavailable, falling back: ${it.message}")
        context.getSharedPreferences("bitchord_auth_plain", Context.MODE_PRIVATE)
    }

    var cookie: String?
        get() = prefs.getString(KEY_COOKIE, null)
        set(value) = prefs.edit().putString(KEY_COOKIE, value).apply()

    val isSignedIn: Boolean
        get() = cookie?.let { hasApiSid(it) } == true

    /** The Discord account's bearer token. See DiscordRPC for why a user token. */
    var discordToken: String?
        get() = prefs.getString(KEY_DISCORD_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_DISCORD_TOKEN, value).apply()

    /**
     * Signs out of YouTube Music only — the Discord login is a separate account.
     */
    fun signOut() = prefs.edit().remove(KEY_COOKIE).apply()

    companion object {
        /**
         * Whether a cookie header carries a secret Innertube requests can be
         * signed with.
         *
         * Matched on the cookie *name*, which reads as pedantry and is not. The
         * test used to be `cookie.contains("SAPISID")`, and `__Secure-3PAPISID`
         * contains "SAPISID" — so a jar holding only the `__Secure-` forms, which
         * is what a partitioned-cookie login produces, passed a check for a
         * cookie it did not have. The app then declared itself signed in and made
         * every request unsigned, which Google answers as a stranger. Library
         * reads degraded quietly and history was never written at all.
         */
        fun hasApiSid(cookieHeader: String): Boolean =
            cookieHeader.split(';').any { entry ->
                val name = entry.substringBefore('=').trim()
                val value = entry.substringAfter('=', "").trim()
                name in API_SID_NAMES && value.isNotEmpty()
            }

        private val API_SID_NAMES =
            setOf("SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID")

        private const val KEY_COOKIE = "cookie"
        private const val KEY_DISCORD_TOKEN = "discord_token"
    }
}
