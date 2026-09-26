package com.music.bitchord.playback.smart

/** Loads the analyser's native library, however the platform keeps it. */
object NativeAnalysisLibrary {

    private var loader: (() -> Unit)? = null

    /** Must be called before anything touches the analyser. */
    fun install(loader: () -> Unit) {
        this.loader = loader
    }

    val available: Boolean by lazy {
        runCatching {
            val install = loader
            if (install != null) install() else System.loadLibrary(LIBRARY_NAME)
        }.onFailure { AnalysisLog.warn("Analysis library unavailable; Automix will not run", it) }
            .isSuccess
    }

    /** The name both builds give it, so neither spells it out separately. */
    const val LIBRARY_NAME = "bitchord_analysis"
}
