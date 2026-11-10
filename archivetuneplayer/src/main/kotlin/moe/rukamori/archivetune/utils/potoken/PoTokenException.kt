/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils.potoken

class PoTokenException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class BrokenWebViewException(
    message: String,
) : Exception(message)

fun classifyJsError(error: String): Exception =
    if (error.contains("SyntaxError")) {
        BrokenWebViewException(error)
    } else {
        PoTokenException(error)
    }
