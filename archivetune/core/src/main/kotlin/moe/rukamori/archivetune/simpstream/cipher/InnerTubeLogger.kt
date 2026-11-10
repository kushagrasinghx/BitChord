/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Ported from SimpMusic (https://github.com/maxrave-dev/SimpMusic),
 * core/service/kotlinYtmusicScraper cipher package — GPL-3.0, © maxrave-dev.
 * Logic kept byte-for-byte; only the package name and imports changed.
 */

package moe.rukamori.archivetune.simpstream.cipher

enum class InnerTubeLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

data class InnerTubeLogEvent(
    val level: InnerTubeLogLevel,
    val tag: String,
    val message: String,
    val mediaId: String? = null,
    val details: Map<String, String> = emptyMap(),
)

fun interface InnerTubeLogger {
    fun log(event: InnerTubeLogEvent)

    companion object {
        val NONE = InnerTubeLogger {}
    }
}

internal fun InnerTubeLogger.d(
    tag: String,
    message: String,
    mediaId: String? = null,
    details: Map<String, String> = emptyMap(),
) = log(InnerTubeLogEvent(InnerTubeLogLevel.DEBUG, tag, message, mediaId, details))

internal fun InnerTubeLogger.i(
    tag: String,
    message: String,
    mediaId: String? = null,
    details: Map<String, String> = emptyMap(),
) = log(InnerTubeLogEvent(InnerTubeLogLevel.INFO, tag, message, mediaId, details))

internal fun InnerTubeLogger.w(
    tag: String,
    message: String,
    mediaId: String? = null,
    details: Map<String, String> = emptyMap(),
) = log(InnerTubeLogEvent(InnerTubeLogLevel.WARN, tag, message, mediaId, details))

internal fun InnerTubeLogger.e(
    tag: String,
    message: String,
    mediaId: String? = null,
    details: Map<String, String> = emptyMap(),
) = log(InnerTubeLogEvent(InnerTubeLogLevel.ERROR, tag, message, mediaId, details))
