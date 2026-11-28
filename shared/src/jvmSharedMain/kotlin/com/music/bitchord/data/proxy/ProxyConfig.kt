package com.music.bitchord.data.proxy

import kotlinx.serialization.Serializable
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * Which wire protocol the proxy speaks.
 *
 * [HTTPS] means an HTTP proxy reached over TLS. OkHttp has no transport for
 * that, so it is carried as a distinct choice for the UI and documentation to
 * explain, but [toJavaProxy] sends it as a plain HTTP proxy. The proxy editor
 * says so next to the selector.
 */
@Serializable
enum class ProxyProtocol {
    HTTP,
    HTTPS,
    SOCKS5,
}

/**
 * How one source answers the "which proxy?" question.
 *
 * [INHERIT] follows the app-wide setting, [DIRECT] bypasses it, and [CUSTOM]
 * names a proxy of its own. The distinction matters: DIRECT is an explicit
 * instruction not to use a proxy, not an absence of configuration.
 */
@Serializable
enum class ProxyMode {
    INHERIT,
    DIRECT,
    CUSTOM,
}

enum class ProxyValidationError {
    HOST_REQUIRED,
    HOST_INVALID,
    PORT_REQUIRED,
    PORT_INVALID,
}

/**
 * A proxy endpoint, with credentials when it needs them.
 *
 * Deliberately plain data: the app stores the password in its encrypted
 * credential store, and this type is never written to a log. [toString] is
 * redacted so an accidental interpolation cannot leak one.
 */
@Serializable
data class ProxyConfig(
    val enabled: Boolean = false,
    val protocol: ProxyProtocol = ProxyProtocol.HTTP,
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = "",
) {

    fun normalized(): ProxyConfig = copy(
        host = normalizeProxyHost(host),
        username = username.trim(),
    )

    /**
     * What is wrong with this endpoint, or null when it is usable.
     *
     * [requireConfigured] is false for a global proxy that is switched off: a
     * disabled proxy is allowed to keep its old address so turning it back on
     * does not mean retyping it.
     */
    fun validationError(requireConfigured: Boolean = enabled): ProxyValidationError? {
        if (!requireConfigured) return null
        if (host.isBlank()) return ProxyValidationError.HOST_REQUIRED
        if (!isValidProxyHost(host)) return ProxyValidationError.HOST_INVALID
        if (port == 0) return ProxyValidationError.PORT_REQUIRED
        if (port !in 1..65535) return ProxyValidationError.PORT_INVALID
        return null
    }

    val isConfigured: Boolean
        get() = validationError(requireConfigured = true) == null

    val isUsable: Boolean
        get() = enabled && isConfigured

    /**
     * The endpoint as java.net understands it. Resolution is left to connect
     * time, so a proxy hostname that only resolves through a VPN still works.
     */
    fun toJavaProxy(): Proxy {
        val type = when (protocol) {
            ProxyProtocol.HTTP, ProxyProtocol.HTTPS -> Proxy.Type.HTTP
            ProxyProtocol.SOCKS5 -> Proxy.Type.SOCKS
        }
        return Proxy(type, InetSocketAddress.createUnresolved(host, port))
    }

    override fun toString(): String =
        "ProxyConfig(enabled=$enabled, protocol=$protocol, host=$host, port=$port, " +
            "username=${if (username.isEmpty()) "" else "***"}, " +
            "password=${if (password.isEmpty()) "" else "***"})"
}

/**
 * The per-source override stored alongside a [SourceConfig].
 *
 * Kept separate from [ProxyConfig] because INHERIT and DIRECT are routing
 * decisions, not endpoints. The endpoint fields are still retained while
 * INHERIT/DIRECT is selected so switching back to CUSTOM does not lose them.
 */
@Serializable
data class SourceProxy(
    val mode: ProxyMode = ProxyMode.INHERIT,
    val protocol: ProxyProtocol = ProxyProtocol.HTTP,
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = "",
) {

    fun normalized(): SourceProxy = copy(
        host = normalizeProxyHost(host),
        username = username.trim(),
    )

    /** This source's own endpoint, or null when it is not CUSTOM. */
    fun customConfig(): ProxyConfig? =
        if (mode == ProxyMode.CUSTOM) {
            ProxyConfig(
                enabled = true,
                protocol = protocol,
                host = host,
                port = port,
                username = username,
                password = password,
            ).normalized()
        } else {
            null
        }

    fun validationError(): ProxyValidationError? =
        customConfig()?.validationError(requireConfigured = true)

    override fun toString(): String =
        "SourceProxy(mode=$mode, protocol=$protocol, host=$host, port=$port, " +
            "username=${if (username.isEmpty()) "" else "***"}, " +
            "password=${if (password.isEmpty()) "" else "***"})"
}

fun normalizeProxyHost(raw: String): String {
    val trimmed = raw.trim()
    return when {
        trimmed.startsWith("[") && trimmed.endsWith("]") -> trimmed.substring(1, trimmed.length - 1)
        else -> trimmed
    }
}

/**
 * Whether [raw] is a hostname or IP literal a proxy can be reached at.
 *
 * Hostnames are checked label by label rather than by resolving them: saving a
 * proxy should not block on DNS, and a name that does not resolve yet may
 * become valid once a VPN is up. IPv6 literals are parsed by the JDK so
 * `::1`, `[::1]` and the compressed forms are all accepted.
 */
fun isValidProxyHost(raw: String): Boolean {
    val host = normalizeProxyHost(raw).trimEnd('.')
    if (host.isEmpty() || host.length > 253) return false

    if (host.contains(':')) {
        return runCatching { InetAddress.getByName(host) is Inet6Address }.getOrDefault(false)
    }

    val labels = host.split('.')
    if (labels.any { label ->
            label.isEmpty() ||
                label.length > 63 ||
                !label.first().isAsciiLetterOrDigit() ||
                !label.last().isAsciiLetterOrDigit() ||
                label.any { !it.isAsciiLetterOrDigit() && it != '-' }
        }
    ) {
        return false
    }

    // A name made only of digits is an IPv4 address, and each octet has to fit
    // in a byte. Without this, 999.999.999.999 would pass as a hostname.
    if (labels.all { label -> label.all(Char::isDigit) }) {
        return labels.size == 4 && labels.all { label ->
            val octet = label.toIntOrNull()
            octet != null && octet in 0..255
        }
    }
    return true
}

private fun Char.isAsciiLetterOrDigit(): Boolean =
    this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
