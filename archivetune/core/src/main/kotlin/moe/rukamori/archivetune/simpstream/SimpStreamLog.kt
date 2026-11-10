/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.simpstream

/**
 * Minimal logger for the simpstream package (the SimpMusic port).
 *
 * :core is a pure Kotlin/JVM module, so it cannot use Timber — Timber ships
 * only an Android AAR and Gradle's variant matching rejects it for a JVM
 * consumer ("No matching variant ... library elements 'aar' and the consumer
 * needed ... class files"). The rest of core logs with println (see
 * InnerTube.kt); this object follows that convention by default but exposes a
 * pluggable [sink] so :app can forward everything into Timber (it installs the
 * bridge in YTPlayerUtils' initializer).
 */
object SimpStreamLog {
    const val DEBUG = 0
    const val INFO = 1
    const val WARN = 2
    const val ERROR = 3

    fun interface Sink {
        fun log(
            level: Int,
            tag: String,
            message: String,
            error: Throwable?,
        )
    }

    @Volatile
    var sink: Sink =
        Sink { level, tag, message, error ->
            val prefix =
                when (level) {
                    DEBUG -> "D"
                    INFO -> "I"
                    WARN -> "W"
                    else -> "E"
                }
            println("$prefix/$tag: $message" + (error?.let { " (${it::class.simpleName}: ${it.message})" } ?: ""))
        }

    fun d(
        tag: String,
        message: String,
    ) = sink.log(DEBUG, tag, message, null)

    fun i(
        tag: String,
        message: String,
    ) = sink.log(INFO, tag, message, null)

    fun w(
        tag: String,
        message: String,
        error: Throwable? = null,
    ) = sink.log(WARN, tag, message, error)

    fun e(
        tag: String,
        message: String,
        error: Throwable? = null,
    ) = sink.log(ERROR, tag, message, error)
}
