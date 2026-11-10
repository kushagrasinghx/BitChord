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

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.isSuccess

internal data class RestrictedTextResponse(
    val status: HttpStatusCode,
    val headers: Headers,
    val body: String?,
)

/** Uses the caller's engine without inheriting its redirect policy or taking engine ownership. */
internal suspend fun HttpClient.getTextWithoutRedirects(
    url: Url,
    maxBytes: Int,
    configure: HttpRequestBuilder.() -> Unit = {},
): RestrictedTextResponse {
    val directClient =
        HttpClient(engine) {
            expectSuccess = false
            followRedirects = false
            install(HttpTimeout)
        }
    return try {
        val response = directClient.get(url, configure)
        if (response.status.isSuccess()) {
            RestrictedTextResponse(response.status, response.headers, response.bodyAsTextLimited(maxBytes))
        } else {
            response.bodyAsChannel().cancel(null)
            RestrictedTextResponse(response.status, response.headers, null)
        }
    } finally {
        directClient.close()
    }
}
