package com.music.bitchord.playback.smart

/** Where the analyser's warnings go, without it having to know the platform. */
object AnalysisLog {

    private var sink: ((String, Throwable?) -> Unit)? = null

    fun install(sink: (String, Throwable?) -> Unit) {
        this.sink = sink
    }

    fun warn(message: String, error: Throwable? = null) {
        sink?.invoke(message, error)
    }
}
