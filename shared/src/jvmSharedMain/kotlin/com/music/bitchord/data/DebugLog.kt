package com.music.bitchord.data

/**
 * Debug logging for code both applications run.
 *
 * Silent until an application installs a [sink]: the phone routes it to
 * logcat on debug builds only, the desktop to its track log.
 */
object DebugLog {
    fun interface Sink {
        fun log(level: Char, tag: String, message: String, error: Throwable?)
    }

    @Volatile
    var sink: Sink? = null

    fun d(tag: String, message: String) = sink?.log('D', tag, message, null) ?: Unit

    fun i(tag: String, message: String) = sink?.log('I', tag, message, null) ?: Unit

    fun w(tag: String, message: String) = sink?.log('W', tag, message, null) ?: Unit

    fun w(tag: String, message: String, error: Throwable) = sink?.log('W', tag, message, error) ?: Unit

    fun e(tag: String, message: String) = sink?.log('E', tag, message, null) ?: Unit

    fun e(tag: String, message: String, error: Throwable) = sink?.log('E', tag, message, error) ?: Unit
}
