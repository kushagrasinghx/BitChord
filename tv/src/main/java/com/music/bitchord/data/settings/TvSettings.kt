package com.music.bitchord.data.settings

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The Android TV app's own preferences. They live in the same preferences file
 * as [AppSettings] — `bitchord_settings` — under `tv_`-prefixed keys, so the
 * phone's settings class carries nothing of TV. Loaded by
 * [com.music.bitchord.data.TvStartup] before anything reads them.
 */
object TvSettings {
    private lateinit var prefs: SharedPreferences

    val tvRefreshRate = MutableStateFlow("AUTO")
    val tvNickname = MutableStateFlow("Listener")
    val tvTheme = MutableStateFlow("dynamic_artwork")
    val tvFontFamily = MutableStateFlow("sf_pro")
    val spatialAudioEnabled = MutableStateFlow(false)
    val soundCheckEnabled = MutableStateFlow(false)
    val addPlaylistSongsToLibrary = MutableStateFlow(true)
    val addFavoriteSongsToLibrary = MutableStateFlow(true)
    val tvSetupVersionCompleted = MutableStateFlow(0)
    val tvLiquidGlassEnabled = MutableStateFlow(true)
    val tvBlurIntensity = MutableStateFlow(0.85f)
    val tvLyricsCanvasEnabled = MutableStateFlow(true)
    val tvVideoAuto1080p = MutableStateFlow(true)
    val tvNavLayout = MutableStateFlow("left_rail")

    /** UI density/scale multiplier for the TV layout: 0.8 (Small) → 1.0 (Normal) → 1.2 (Large) → 1.4 (XL). */
    val tvUiScale = MutableStateFlow(1.0f)

    fun init(preferences: SharedPreferences) {
        prefs = preferences
        TvPinThrottle.init(preferences)
        tvRefreshRate.value = prefs.getString(KEY_TV_REFRESH_RATE, "AUTO") ?: "AUTO"
        tvNickname.value = prefs.getString(KEY_TV_NICKNAME, "Listener") ?: "Listener"
        tvTheme.value = prefs.getString(KEY_TV_THEME, "dynamic_artwork") ?: "dynamic_artwork"
        tvFontFamily.value = prefs.getString(KEY_TV_FONT, "sf_pro") ?: "sf_pro"
        spatialAudioEnabled.value = prefs.getBoolean(KEY_TV_SPATIAL_AUDIO, false)
        soundCheckEnabled.value = prefs.getBoolean(KEY_SOUND_CHECK, false)
        addPlaylistSongsToLibrary.value = prefs.getBoolean(KEY_ADD_PLAYLIST_SONGS_TO_LIBRARY, true)
        addFavoriteSongsToLibrary.value = prefs.getBoolean(KEY_ADD_FAVORITE_SONGS_TO_LIBRARY, true)
        tvSetupVersionCompleted.value = prefs.getInt(KEY_TV_SETUP_VERSION, 0)
        tvUiScale.value = prefs.getFloat(KEY_TV_UI_SCALE, 1.0f).coerceIn(0.8f, 1.4f)
        tvLiquidGlassEnabled.value = prefs.getBoolean(KEY_TV_LIQUID_GLASS, true)
        tvBlurIntensity.value = prefs.getFloat(KEY_TV_BLUR_INTENSITY, 0.85f).coerceIn(0.0f, 1.0f)
        tvLyricsCanvasEnabled.value = prefs.getBoolean(KEY_TV_LYRICS_CANVAS, true)
        tvVideoAuto1080p.value = prefs.getBoolean(KEY_TV_VIDEO_AUTO_1080P, true)
        tvNavLayout.value = prefs.getString(KEY_TV_NAV_LAYOUT, "left_rail") ?: "left_rail"
    }

    fun init(context: Context) {
        init(context.getSharedPreferences("bitchord_settings", Context.MODE_PRIVATE))
    }

    private fun save(edit: SharedPreferences.Editor.() -> Unit) {
        if (this::prefs.isInitialized) prefs.edit().apply(edit).apply()
    }

    fun setTvFontFamily(fontId: String) {
        tvFontFamily.value = fontId
        save { putString(KEY_TV_FONT, fontId) }
    }

    fun setTvUiScale(scale: Float) {
        val clamped = scale.coerceIn(0.8f, 1.4f)
        tvUiScale.value = clamped
        save { putFloat(KEY_TV_UI_SCALE, clamped) }
    }

    fun setTvLiquidGlassEnabled(enabled: Boolean) {
        tvLiquidGlassEnabled.value = enabled
        save { putBoolean(KEY_TV_LIQUID_GLASS, enabled) }
    }

    fun setTvBlurIntensity(intensity: Float) {
        val clamped = intensity.coerceIn(0.0f, 1.0f)
        tvBlurIntensity.value = clamped
        save { putFloat(KEY_TV_BLUR_INTENSITY, clamped) }
    }

    fun setTvLyricsCanvasEnabled(enabled: Boolean) {
        tvLyricsCanvasEnabled.value = enabled
        save { putBoolean(KEY_TV_LYRICS_CANVAS, enabled) }
    }

    fun setTvVideoAuto1080p(enabled: Boolean) {
        tvVideoAuto1080p.value = enabled
        save { putBoolean(KEY_TV_VIDEO_AUTO_1080P, enabled) }
    }

    fun setSpatialAudioEnabled(enabled: Boolean) {
        spatialAudioEnabled.value = enabled
        save { putBoolean(KEY_TV_SPATIAL_AUDIO, enabled) }
    }

    fun setSoundCheckEnabled(enabled: Boolean) {
        soundCheckEnabled.value = enabled
        save { putBoolean(KEY_SOUND_CHECK, enabled) }
    }

    fun setAddPlaylistSongsToLibrary(enabled: Boolean) {
        addPlaylistSongsToLibrary.value = enabled
        save { putBoolean(KEY_ADD_PLAYLIST_SONGS_TO_LIBRARY, enabled) }
    }

    fun setAddFavoriteSongsToLibrary(enabled: Boolean) {
        addFavoriteSongsToLibrary.value = enabled
        save { putBoolean(KEY_ADD_FAVORITE_SONGS_TO_LIBRARY, enabled) }
    }

    fun setTvRefreshRate(value: String) {
        tvRefreshRate.value = value
        save { putString(KEY_TV_REFRESH_RATE, value) }
    }

    fun setTvPersonalization(nickname: String, themeId: String, version: Int = 1) {
        tvNickname.value = nickname
        tvTheme.value = themeId
        tvSetupVersionCompleted.value = version
        save {
            putString(KEY_TV_NICKNAME, nickname)
            putString(KEY_TV_THEME, themeId)
            putInt(KEY_TV_SETUP_VERSION, version)
        }
    }

    fun setTvNickname(nickname: String) {
        tvNickname.value = nickname
        save { putString(KEY_TV_NICKNAME, nickname) }
    }

    fun setTvTheme(themeId: String) {
        tvTheme.value = themeId
        save { putString(KEY_TV_THEME, themeId) }
    }

    fun setTvNavLayout(layout: String) {
        tvNavLayout.value = layout
        save { putString(KEY_TV_NAV_LAYOUT, layout) }
    }

    /**
     * Stores a PBKDF2-HMAC-SHA256 salted hash of the given PIN per profile, or clears it if null/blank.
     */
    fun setProfilePin(profileId: String, pin: String?) {
        save {
            if (pin.isNullOrBlank()) {
                remove("$KEY_PROFILE_PIN_PREFIX$profileId")
            } else {
                val salt = ByteArray(PIN_SALT_BYTES).apply {
                    SecureRandom().nextBytes(this)
                }
                val hash = hashPin(pin, salt, PIN_PBKDF2_ITERATIONS)
                val stored = "$PIN_HASH_PREFIX:$PIN_PBKDF2_ITERATIONS:${salt.toHex()}:${hash.toHex()}"
                putString("$KEY_PROFILE_PIN_PREFIX$profileId", stored)
            }
        }
        TvPinThrottle.reset(profileId)
    }

    /**
     * Verifies the entered PIN against the profile's stored salted hash in constant time.
     * Also migrates legacy plaintext PINs if found upon successful match.
     */
    fun verifyProfilePin(profileId: String, pin: String): Boolean {
        if (!this::prefs.isInitialized) return false
        val stored = prefs.getString("$KEY_PROFILE_PIN_PREFIX$profileId", null) ?: return false
        if (stored.isBlank() || pin.isBlank()) return false

        if (stored.startsWith("$PIN_HASH_PREFIX:")) {
            val parts = stored.split(':')
            if (parts.size != 4) return false
            val iterations = parts[1].toIntOrNull() ?: return false
            val salt = parts[2].hexToByteArray() ?: return false
            val expectedHash = parts[3].hexToByteArray() ?: return false
            val computedHash = try {
                hashPin(pin, salt, iterations)
            } catch (_: Exception) {
                return false
            }
            val matches = MessageDigest.isEqual(computedHash, expectedHash)
            // Re-hash at the current cost when the stored hash predates a bump in iterations.
            if (matches && iterations < PIN_PBKDF2_ITERATIONS) setProfilePin(profileId, pin)
            return matches
        }

        // Backward compatibility: compare legacy plaintext PIN in constant time and upgrade on match.
        val storedBytes = stored.toByteArray(Charsets.UTF_8)
        val pinBytes = pin.toByteArray(Charsets.UTF_8)
        val matches = MessageDigest.isEqual(storedBytes, pinBytes)
        if (matches) {
            setProfilePin(profileId, pin)
        }
        return matches
    }

    fun isProfileLocked(profileId: String): Boolean {
        if (!this::prefs.isInitialized) return false
        val stored = prefs.getString("$KEY_PROFILE_PIN_PREFIX$profileId", null)
        return !stored.isNullOrBlank()
    }

    fun wipeAllPins() {
        if (!this::prefs.isInitialized) return
        save {
            prefs.all.keys
                .filter { it.startsWith(KEY_PROFILE_PIN_PREFIX) }
                .forEach { key -> remove(key) }
        }
        TvPinThrottle.resetAll()
    }

    fun getPinThrottleDelayRemainingMs(profileId: String): Long =
        TvPinThrottle.getDelayRemainingMs(profileId)

    fun recordFailedPinAttempt(profileId: String): Long =
        TvPinThrottle.recordFailure(profileId)

    fun recordSuccessfulPinAttempt(profileId: String) {
        TvPinThrottle.recordSuccess(profileId)
    }

    private fun hashPin(pin: String, salt: ByteArray, iterations: Int = PIN_PBKDF2_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, PIN_HASH_BYTES * 8)
        return try {
            val factory = SecretKeyFactory.getInstance(PIN_ALGORITHM)
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.toHex(): String {
        val hexChars = "0123456789abcdef"
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val i = b.toInt() and 0xFF
            sb.append(hexChars[i ushr 4])
            sb.append(hexChars[i and 0x0F])
        }
        return sb.toString()
    }

    private fun String.hexToByteArray(): ByteArray? {
        if (length % 2 != 0) return null
        val bytes = ByteArray(length / 2)
        for (i in bytes.indices) {
            val hi = Character.digit(this[i * 2], 16)
            val lo = Character.digit(this[i * 2 + 1], 16)
            if (hi == -1 || lo == -1) return null
            bytes[i] = ((hi shl 4) or lo).toByte()
        }
        return bytes
    }

    private const val KEY_PROFILE_PIN_PREFIX = "tv_profile_pin_"
    private const val PIN_HASH_PREFIX = "pbkdf2_sha256"
    private const val PIN_SALT_BYTES = 16
    private const val PIN_HASH_BYTES = 32
    private const val PIN_PBKDF2_ITERATIONS = 120_000
    private const val PIN_ALGORITHM = "PBKDF2WithHmacSHA256"

    private const val KEY_TV_REFRESH_RATE = "tv_refresh_rate"
    private const val KEY_TV_NICKNAME = "tv_nickname"
    private const val KEY_TV_THEME = "tv_theme"
    private const val KEY_TV_FONT = "tv_font_family"
    private const val KEY_TV_SPATIAL_AUDIO = "tv_spatial_audio"
    private const val KEY_SOUND_CHECK = "tv_sound_check"
    private const val KEY_ADD_PLAYLIST_SONGS_TO_LIBRARY = "tv_add_playlist_songs_to_library"
    private const val KEY_ADD_FAVORITE_SONGS_TO_LIBRARY = "tv_add_favorite_songs_to_library"
    private const val KEY_TV_SETUP_VERSION = "tv_setup_version"
    private const val KEY_TV_UI_SCALE = "tv_ui_scale"
    private const val KEY_TV_LIQUID_GLASS = "tv_liquid_glass"
    private const val KEY_TV_BLUR_INTENSITY = "tv_blur_intensity"
    private const val KEY_TV_LYRICS_CANVAS = "tv_lyrics_canvas"
    private const val KEY_TV_VIDEO_AUTO_1080P = "tv_video_auto_1080p"
    private const val KEY_TV_NAV_LAYOUT = "tv_nav_layout"
}
