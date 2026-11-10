/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils.soundcloud

private val TokenRegex =
    Regex(
        pattern = """(?i)(oauth[_-]?token|access[_-]?token|soundcloud[_-]?oauth[_-]?token)["'\s:=]+["']?([A-Za-z0-9._~+/=-]{16,})""",
    )

private val CookieTokenNames =
    setOf(
        "oauth_token",
        "oauth-token",
        "soundcloud_oauth_token",
        "soundcloud-oauth-token",
        "access_token",
        "access-token",
    )

fun normalizeSoundCloudAuthInput(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null

    val prefixed = trimmed
        .removePrefix("OAuth ")
        .removePrefix("oauth ")
        .removePrefix("Bearer ")
        .removePrefix("bearer ")
        .trim()
    if (prefixed.isPlausibleRawToken()) return prefixed

    trimmed
        .split(';', '\n', '\r')
        .asSequence()
        .map { it.trim() }
        .mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val name = part.substring(0, separator).trim().lowercase()
            val value = part.substring(separator + 1).trim().trim('"', '\'')
            value.takeIf { name in CookieTokenNames && it.isPlausibleRawToken() }
        }
        .firstOrNull()
        ?.let { return it }

    TokenRegex
        .findAll(trimmed)
        .map { it.groupValues.getOrNull(2).orEmpty().trim().trim('"', '\'') }
        .firstOrNull { it.isPlausibleRawToken() }
        ?.let { return it }

    return null
}

fun mergeSoundCloudAuthInputs(inputs: Iterable<String>): String? =
    inputs.firstNotNullOfOrNull(::normalizeSoundCloudAuthInput)

fun isSoundCloudAuthConfigured(value: String): Boolean =
    normalizeSoundCloudAuthInput(value) != null

fun hasSoundCloudAuthToken(value: String): Boolean =
    isSoundCloudAuthConfigured(value)

private fun String.isPlausibleRawToken(): Boolean {
    if (length < 16) return false
    if (any(Char::isWhitespace)) return false
    if (contains(';')) return false
    if (contains('{') || contains('}')) return false
    return count { it == '=' } <= 2
}
