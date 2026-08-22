package com.music.bitchord.data

import android.util.Log
import com.music.bitchord.BuildConfig

/**
 * `android.util.Log`, minus the release build.
 *
 * For call sites outside the playback/resolve path that [TrackLog] covers —
 * feeds, artwork, library scans, scrobbling — where there's no Copy Log
 * reader depending on the output, so there's nothing to preserve in prod.
 * Import as `import com.music.bitchord.data.DebugLog as Log` to drop in
 * without touching call sites.
 */
object DebugLog {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.i(tag, message)
    }

    fun w(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.w(tag, message)
    }

    fun w(tag: String, message: String, error: Throwable) {
        if (BuildConfig.DEBUG) Log.w(tag, message, error)
    }

    fun e(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.e(tag, message)
    }

    fun e(tag: String, message: String, error: Throwable) {
        if (BuildConfig.DEBUG) Log.e(tag, message, error)
    }
}
