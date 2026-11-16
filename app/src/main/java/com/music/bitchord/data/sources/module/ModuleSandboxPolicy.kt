package com.music.bitchord.data.sources.module

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Network and resource policy for untrusted module JavaScript.
 *
 * A module is remote code. Its `fetch` bridge must not become an SSRF pivot
 * into the phone's LAN or cloud metadata endpoints, and a remote module must
 * not be able to use plain HTTP. A module served from a local/LAN index keeps
 * local-network access, because that is the only way a self-hosted module can
 * reach its own backend.
 */
internal object ModuleSandboxPolicy {

    const val MAX_RESPONSE_BYTES = 8L * 1024 * 1024
    const val MAX_MODULE_BYTES = 2L * 1024 * 1024
    const val MAX_REDIRECTS = 3
    const val MEMORY_LIMIT_BYTES = 64L * 1024 * 1024
    const val EVALUATION_TIMEOUT_MS = 60_000L

    fun isLocalOrPrivateHost(host: String): Boolean {
        val h = host.lowercase().removePrefix("[").removeSuffix("]")
        if (h == "localhost" || h.endsWith(".localhost")) return true
        if (h == "::1" || h == "0:0:0:0:0:0:0:1" || h == "0.0.0.0") return true
        if (h.startsWith("127.") || h.startsWith("10.") || h.startsWith("192.168.")) return true
        if (h.startsWith("169.254.") || h == "169.254.169.254") return true
        if (h.startsWith("172.")) {
            val second = h.split('.').getOrNull(1)?.toIntOrNull()
            if (second != null && second in 16..31) return true
        }
        // IPv6 unique-local and link-local ranges.
        if (h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe80")) return true
        return false
    }

    fun isLinkLocal(host: String): Boolean {
        val h = host.lowercase().removePrefix("[").removeSuffix("]")
        return h.startsWith("169.254.") || h == "169.254.169.254" || h.startsWith("fe80")
    }

    /**
     * Whether a module may fetch [targetUrl].
     *
     * Public HTTPS destinations are always allowed. A private/loopback target
     * is allowed only when the module itself was served from the same host or
     * from another local/private host. Link-local metadata is never allowed.
     */
    fun allowsDestination(targetUrl: String, fetchBase: String): Boolean {
        val target = targetUrl.toHttpUrlOrNull() ?: return false
        if (target.scheme != "http" && target.scheme != "https") return false
        if (target.host.isBlank()) return false
        if (isLinkLocal(target.host)) return false
        if (target.scheme == "http" && !isLocalOrPrivateHost(target.host)) return false
        if (!isLocalOrPrivateHost(target.host)) return true
        val base = fetchBase.toHttpUrlOrNull() ?: return false
        return target.host == base.host || isLocalOrPrivateHost(base.host)
    }

    /** Cross-host redirects keep only the generic User-Agent. */
    fun headersForRedirect(
        fromUrl: String,
        toUrl: String,
        headers: Map<String, String>,
    ): Map<String, String> {
        val fromHost = fromUrl.toHttpUrlOrNull()?.host
        val toHost = toUrl.toHttpUrlOrNull()?.host
        if (fromHost != null && toHost != null && fromHost.equals(toHost, ignoreCase = true)) {
            return headers
        }
        return headers.filterKeys { it.equals("User-Agent", ignoreCase = true) }
    }

    /** Module code and indexes must be HTTPS unless self-hosted locally. */
    fun allowsModuleDownload(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        return parsed.scheme == "https" || isLocalOrPrivateHost(parsed.host)
    }
}
