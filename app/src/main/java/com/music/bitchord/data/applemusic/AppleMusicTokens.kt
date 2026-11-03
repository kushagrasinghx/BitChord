package com.music.bitchord.data.applemusic

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.BuildConfig
import com.music.bitchord.auth.EncryptedPrefs
import com.music.bitchord.data.Http
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User credentials are persisted separately from the memory-only backend developer token. */
object AppleMusicTokens {
    private val users = AppleMusicUserTokenStore()
    private val developers by lazy {
        val backend = AppleMusicTokenBackend(appleMusicTokenClient(Http.client))
        AppleMusicDeveloperTokens(BuildConfig.APPLE_MUSIC_TOKEN_URL, backend::fetch)
    }

    val connected: StateFlow<Boolean> = users.connected

    /** Opens local storage only; an unconfigured backend does not prevent application startup. */
    fun init(context: Context) {
        users.init {
            EncryptedPrefs.open(context, "bitchord_apple_music", "bitchord_apple_music_plain")
        }
    }

    fun userToken(): String? = users.userToken()

    fun storefront(): String? = users.storefront()

    fun playbackStorefront(): String? = users.playbackStorefront()

    /** Throws a redacted IOException on configuration, transport, or token validation failure. */
    suspend fun developerToken(): String = developers.developerToken()

    fun saveUserToken(token: String, storefront: String?, playbackStorefront: String? = null) {
        users.saveUserToken(token, storefront, playbackStorefront)
    }

    /** Does not reset settings or matcher state; the caller orchestrates those separately. */
    fun clear() {
        developers.clear()
        users.clear()
    }
}

/** Kept separate from the singleton so storage/state transitions can be tested without a Keystore. */
internal class AppleMusicUserTokenStore {
    private val lock = Any()
    private var prefs: SharedPreferences? = null
    private var token: String? = null
    private var country: String? = null
    private var playbackCountry: String? = null
    private var clearBeforeInit = false
    private val mutableConnected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = mutableConnected.asStateFlow()

    fun init(open: () -> SharedPreferences) = synchronized(lock) {
        if (prefs != null) return@synchronized
        val opened = open()
        // A reset before application initialization must not restore the old session later.
        if (clearBeforeInit) opened.edit().clear().apply()
        val storedToken = opened.getString("user_token", null)?.takeUnless { it.isBlank() }
        val storedCountry = opened.getString("storefront", null)?.trim()?.takeUnless { it.isEmpty() }
        prefs = opened
        token = storedToken
        country = storedCountry.takeIf { storedToken != null }
        playbackCountry = opened.getString("playback_storefront", null)?.takeIf { storedToken != null && it.isNotBlank() }
        mutableConnected.value = storedToken != null
    }

    fun userToken(): String? = synchronized(lock) { token }

    fun storefront(): String? = synchronized(lock) { country }

    fun playbackStorefront(): String? = synchronized(lock) { playbackCountry }

    fun saveUserToken(token: String, storefront: String?, playbackStorefront: String? = null) = synchronized(lock) {
        require(token.isNotBlank()) { "Apple Music user token must not be blank" }
        val opened = checkNotNull(prefs) { "Apple Music tokens have not been initialized" }
        val country = storefront?.trim()?.takeUnless { it.isEmpty() }
        val playbackCountry = playbackStorefront?.takeIf { it.isNotBlank() }
        opened.edit().putString("user_token", token).putString("storefront", country)
            .putString("playback_storefront", playbackCountry).apply()
        this.token = token
        this.country = country
        this.playbackCountry = playbackCountry
        mutableConnected.value = true
    }

    fun clear() = synchronized(lock) {
        if (prefs == null) clearBeforeInit = true
        prefs?.edit()?.clear()?.apply()
        token = null
        country = null
        playbackCountry = null
        mutableConnected.value = false
    }
}
