package com.music.bitchord.data.innertube

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.music.bitchord.BuildConfig
import com.music.bitchord.data.MonotonicClock
import com.music.bitchord.data.TrackLog
import android.webkit.CookieManager
import com.music.bitchord.data.innertube.potoken.PoTokenGenerator
import com.music.bitchord.data.innertube.potoken.PoTokenWebView
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.sources.SourceResolver

/**
 * The phone's answers to what the shared stream resolution asks of its
 * platform: the clock, logcat, the exported log's header, the quality
 * ceiling, and InnerTubeX's config store and BotGuard minter.
 */
object AndroidStreamHooks {

    /** Before anything logs or resolves. */
    fun installEarly() {
        MonotonicClock.nowMs = SystemClock::elapsedRealtime
        TrackLog.echo = { level, tag, message, error ->
            if (BuildConfig.DEBUG) {
                when (level) {
                    'D' -> Log.d(tag, message, error)
                    'I' -> Log.i(tag, message, error)
                    'W' -> Log.w(tag, message, error)
                    else -> Log.e(tag, message, error)
                }
            }
        }
        TrackLog.sourcesLine = {
            "sources: substitution=${SourceResolver.canSubstituteForYouTube()} " +
                "request=${SourceResolver.requestForNow()}"
        }
        TrackLog.headerLines = {
            listOf(
                "build: ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})",
                "device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}",
            )
        }
        StreamResolver.maxKbps = { AppSettings.effectiveAudioQuality.maxKbps }
        InnerTubeXResolver.debugLogging = BuildConfig.DEBUG
    }

    /** What `InnerTubeXResolver.init(context)` did when it lived in the app. */
    fun initInnerTubeX(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("innertubex_player_config", Context.MODE_PRIVATE)
        // BotGuard PoTokens minted in a hidden WebView; what WEB_REMIX needs for age-restricted tracks.
        val webViewSupported by lazy { runCatching { CookieManager.getInstance() }.isSuccess }
        val poTokens = PoTokenGenerator(
            createMinter = { PoTokenWebView.getNewPoTokenGenerator(app) },
            available = { webViewSupported },
        )
        InnerTubeXResolver.init(
            filesDir = app.filesDir,
            store = object : InnerTubeXResolver.ConfigStore {
                override fun getString(key: String): String? = prefs.getString(key, null)
                override fun getLong(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0L) else null
                override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
                override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
            },
            poTokenProvider = poTokens.asTokenProvider(),
        )
    }
}
