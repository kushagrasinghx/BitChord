package com.music.bitchord.data

import android.util.Log
import timber.log.Timber

/**
 * Routes Timber — what the ported ArchiveTune player stack logs through — into
 * [CrashLog]'s ring buffer, in every build. The counterpart of ArchiveTune's
 * GlobalLogTree. Never throws: a log call must not become the crash.
 */
class CrashLogTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        try {
            val level =
                when (priority) {
                    Log.VERBOSE, Log.DEBUG -> 'D'
                    Log.INFO -> 'I'
                    Log.WARN -> 'W'
                    else -> 'E'
                }
            CrashLog.record(level, tag ?: "ArchiveTune", message, t)
        } catch (_: Throwable) {
        }
    }
}
