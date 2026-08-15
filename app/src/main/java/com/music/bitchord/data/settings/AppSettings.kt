package com.music.bitchord.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Stream bitrate ceiling. HIGH means "whatever the best available format is".
 *
 * [hourly] is what the ceiling costs in data over an hour of listening, which
 * is the only part of this a user actually cares about on a metered plan.
 */
enum class AudioQuality(
    val maxKbps: Int,
    val label: String,
    val detail: String,
    val hourly: String,
) {
    LOW(64, "Low", "~64 kbps · smallest download", "29 MB/hr"),
    MEDIUM(128, "Medium", "~128 kbps · balanced", "58 MB/hr"),
    HIGH(Int.MAX_VALUE, "High", "Best available · ~171 kbps Opus", "77 MB/hr"),
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark")
}

/**
 * App settings, backed by SharedPreferences and exposed as flows.
 *
 * PlaybackService runs in the same process as the UI, so it observes these
 * same flows and applies changes to the live ExoPlayer instance immediately —
 * no restart, no rebinding.
 */
object AppSettings {

    private lateinit var prefs: SharedPreferences

    /**
     * Quality ceilings, one per kind of connection — the point of the split is
     * that Wi-Fi can stay on High while mobile data is capped. Both default to
     * High; the mobile plan is the user's to budget, not ours to assume.
     */
    val audioQualityWifi = MutableStateFlow(AudioQuality.HIGH)
    val audioQualityCellular = MutableStateFlow(AudioQuality.HIGH)

    /** Whether the active network charges for data. `null` while offline. */
    val meteredConnection = MutableStateFlow<Boolean?>(null)

    val crossfadeSeconds = MutableStateFlow(0)
    val skipSilence = MutableStateFlow(false)

    /**
     * Widens stereo output via [com.music.bitchord.playback.SpatialAudioProcessor],
     * a stereo widening + cross-feed effect running inside ExoPlayer's own
     * pipeline. Not true object-based spatial audio — YouTube only ever hands
     * us a stereo stream, so there's no Atmos-style source to render.
     *
     * The user's wish, not the final answer: it only takes effect on a device
     * with Dolby Atmos switched on, and [com.music.bitchord.playback.DolbyAtmos]
     * clears it back to false the moment that stops being true.
     */
    val spatialAudio = MutableStateFlow(false)
    val playbackSpeed = MutableStateFlow(1.0f)
    val themeMode = MutableStateFlow(ThemeMode.SYSTEM)

    /** Keep playing similar music once the queue runs out. */
    val autoplay = MutableStateFlow(true)

    /** Put the playing track's codec, bitrate and sample rate on the player. */
    val showNerdStats = MutableStateFlow(false)

    /** Freezes the main player's mesh gradient instead of letting it drift/crossfade. */
    val reduceAnimation = MutableStateFlow(false)

    /** Drops haze blur (status bar, mini player, bottom fade, lyrics focus) for a solid-fill look. */
    val reduceDynamicBlur = MutableStateFlow(false)

    /** Disk budget for cached audio. [AudioCache][com.music.bitchord.playback.AudioCache] evicts past it. */
    val audioCacheLimitBytes = MutableStateFlow(DEFAULT_CACHE_LIMIT_BYTES)

    /** Published by PlaybackService so the UI can open the system equalizer. */
    val audioSessionId = MutableStateFlow(0)

    /** The ceiling that applies to a stream started right now. */
    val effectiveAudioQuality: AudioQuality
        get() = if (meteredConnection.value == true) {
            audioQualityCellular.value
        } else {
            audioQualityWifi.value
        }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("bitchord_settings", Context.MODE_PRIVATE)
        migrateSingleQuality()
        audioQualityWifi.value = readQuality(KEY_QUALITY_WIFI)
        audioQualityCellular.value = readQuality(KEY_QUALITY_CELLULAR)
        crossfadeSeconds.value = prefs.getInt(KEY_CROSSFADE, 0)
        skipSilence.value = prefs.getBoolean(KEY_SKIP_SILENCE, false)
        spatialAudio.value = prefs.getBoolean(KEY_SPATIAL_AUDIO, false)
        playbackSpeed.value = prefs.getFloat(KEY_SPEED, 1.0f)
        themeMode.value = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "SYSTEM")
        }.getOrDefault(ThemeMode.SYSTEM)
        autoplay.value = prefs.getBoolean(KEY_AUTOPLAY, true)
        showNerdStats.value = prefs.getBoolean(KEY_NERD_STATS, false)
        reduceAnimation.value = prefs.getBoolean(KEY_REDUCE_ANIMATION, false)
        reduceDynamicBlur.value = prefs.getBoolean(KEY_REDUCE_BLUR, false)
        audioCacheLimitBytes.value = prefs.getLong(KEY_CACHE_LIMIT, DEFAULT_CACHE_LIMIT_BYTES)
            .coerceIn(DEFAULT_CACHE_LIMIT_BYTES, MAX_CACHE_LIMIT_BYTES)
        watchConnection(context)
    }

    /**
     * A ceiling saved when there was only one applies to both connections.
     * Someone who picked Low to protect a data plan would not thank us for
     * quietly putting Wi-Fi *and* mobile back on High.
     */
    private fun migrateSingleQuality() {
        val legacy = prefs.getString(KEY_QUALITY_LEGACY, null) ?: return
        prefs.edit()
            .putString(KEY_QUALITY_WIFI, legacy)
            .putString(KEY_QUALITY_CELLULAR, legacy)
            .remove(KEY_QUALITY_LEGACY)
            .apply()
    }

    private fun readQuality(key: String): AudioQuality {
        val stored = prefs.getString(key, null) ?: return AudioQuality.HIGH
        return runCatching { AudioQuality.valueOf(stored) }.getOrDefault(AudioQuality.HIGH)
    }

    /**
     * Track the active network so [effectiveAudioQuality] can answer without
     * touching ConnectivityManager. Stream resolution happens off the main
     * thread mid-playback; a callback keeps that lookup off the hot path and
     * lets the settings page show which ceiling is currently in force.
     */
    private fun watchConnection(context: Context) {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        val refresh = {
            meteredConnection.value = runCatching {
                if (manager.activeNetwork == null) null else manager.isActiveNetworkMetered
            }.getOrNull()
        }
        refresh()
        runCatching {
            manager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) = refresh()
                    override fun onLost(network: Network) = refresh()
                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) = refresh()
                },
            )
        }
    }

    fun setAutoplay(value: Boolean) {
        autoplay.value = value
        prefs.edit().putBoolean(KEY_AUTOPLAY, value).apply()
    }

    fun setAudioQualityWifi(value: AudioQuality) {
        audioQualityWifi.value = value
        prefs.edit().putString(KEY_QUALITY_WIFI, value.name).apply()
    }

    fun setAudioQualityCellular(value: AudioQuality) {
        audioQualityCellular.value = value
        prefs.edit().putString(KEY_QUALITY_CELLULAR, value.name).apply()
    }

    fun setCrossfadeSeconds(value: Int) {
        crossfadeSeconds.value = value
        prefs.edit().putInt(KEY_CROSSFADE, value).apply()
    }

    fun setSkipSilence(value: Boolean) {
        skipSilence.value = value
        prefs.edit().putBoolean(KEY_SKIP_SILENCE, value).apply()
    }

    fun setSpatialAudio(value: Boolean) {
        spatialAudio.value = value
        prefs.edit().putBoolean(KEY_SPATIAL_AUDIO, value).apply()
    }

    fun setPlaybackSpeed(value: Float) {
        playbackSpeed.value = value
        prefs.edit().putFloat(KEY_SPEED, value).apply()
    }

    fun setShowNerdStats(value: Boolean) {
        showNerdStats.value = value
        prefs.edit().putBoolean(KEY_NERD_STATS, value).apply()
    }

    fun setThemeMode(value: ThemeMode) {
        themeMode.value = value
        prefs.edit().putString(KEY_THEME, value.name).apply()
    }

    fun setReduceAnimation(value: Boolean) {
        reduceAnimation.value = value
        prefs.edit().putBoolean(KEY_REDUCE_ANIMATION, value).apply()
    }

    fun setReduceDynamicBlur(value: Boolean) {
        reduceDynamicBlur.value = value
        prefs.edit().putBoolean(KEY_REDUCE_BLUR, value).apply()
    }

    /** Clamped to [DEFAULT_CACHE_LIMIT_BYTES]..[MAX_CACHE_LIMIT_BYTES] — the floor is the default, not zero. */
    fun setAudioCacheLimitBytes(value: Long) {
        val clamped = value.coerceIn(DEFAULT_CACHE_LIMIT_BYTES, MAX_CACHE_LIMIT_BYTES)
        audioCacheLimitBytes.value = clamped
        prefs.edit().putLong(KEY_CACHE_LIMIT, clamped).apply()
    }

    const val DEFAULT_CACHE_LIMIT_BYTES = 512L * 1024 * 1024
    const val MAX_CACHE_LIMIT_BYTES = 10L * 1024 * 1024 * 1024

    private const val KEY_QUALITY_LEGACY = "audio_quality"
    private const val KEY_QUALITY_WIFI = "audio_quality_wifi"
    private const val KEY_QUALITY_CELLULAR = "audio_quality_cellular"
    private const val KEY_CROSSFADE = "crossfade_seconds"
    private const val KEY_SKIP_SILENCE = "skip_silence"
    private const val KEY_SPATIAL_AUDIO = "spatial_audio"
    private const val KEY_SPEED = "playback_speed"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_AUTOPLAY = "autoplay"
    private const val KEY_NERD_STATS = "show_nerd_stats"
    private const val KEY_CACHE_LIMIT = "audio_cache_limit_bytes"
    private const val KEY_REDUCE_ANIMATION = "reduce_animation"
    private const val KEY_REDUCE_BLUR = "reduce_dynamic_blur"
}
