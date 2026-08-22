package com.music.bitchord.auth

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.DebugLog as Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted-at-rest storage for the YouTube Music session cookie.
 * That cookie is the *only* credential this app holds — the Google password
 * is typed into accounts.google.com inside the WebView and never reaches us.
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
        get() = cookie?.contains("SAPISID") == true

    fun signOut() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_COOKIE = "cookie"
    }
}
