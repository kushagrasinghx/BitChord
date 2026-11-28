package com.music.bitchord.data.proxy

import com.music.bitchord.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import java.net.Authenticator as JavaAuthenticator

/**
 * One source's host-to-proxy decision, handed to [ProxyRouter] by the registry.
 *
 * [host] is the addon's own host. Stream hosts are added later, when a source
 * resolves one, so a CDN the addon names is routed the same way as the addon.
 */
data class SourceRoute(
    val sourceId: String,
    val host: String,
    val mode: ProxyMode,
    val config: ProxyConfig,
)

/**
 * The app's routing table for proxies.
 *
 * OkHttp asks a [ProxySelector] for every connection. Keeping one selector
 * here, installed on the shared client, means the addon request and the audio
 * fetch that follows it make the same decision without either caller having to
 * know a proxy exists. A host with a per-source route uses that route; every
 * other host uses the global setting.
 *
 * The selector fails closed. If a proxy is enabled but its saved endpoint is
 * invalid, selecting it throws instead of quietly connecting directly: a user
 * who configured a proxy has asked not to be routed around it.
 */
object ProxyRouter {

    private data class Route(val mode: ProxyMode, val config: ProxyConfig)

    @Volatile
    private var global: ProxyConfig = ProxyConfig()

    private val lock = Any()
    private val bySource = HashMap<String, Route>()

    /** Routes derived from each source's own host, before stream hosts were learned. */
    private val baseByHost = HashMap<String, Route>()

    /** [baseByHost] plus the CDN hosts each source has resolved. */
    private val byHost = HashMap<String, Route>()

    /** Only for [ProxyTester], which tests an endpoint before it is saved. */
    @Volatile
    private var temporaryAuth: ProxyConfig? = null

    /**
     * The source a blocking call is currently being made for.
     *
     * Host registration covers the hosts a source has already named, but a
     * redirect is followed inside OkHttp and names a host nobody has seen yet.
     * A module fetch runs synchronously on one thread, so scoping that thread
     * lets the selector apply the source's route to every hop — including a
     * redirect to a CDN — instead of falling back to the global setting.
     */
    private val scopedSource = ThreadLocal<String?>()

    /** Runs [block] with every otherwise-unrouted host attributed to [sourceId]. */
    fun <T> withSource(sourceId: String, block: () -> T): T {
        val previous = scopedSource.get()
        scopedSource.set(sourceId)
        return try {
            block()
        } finally {
            if (previous == null) scopedSource.remove() else scopedSource.set(previous)
        }
    }

    val selector: ProxySelector = object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> =
            listOf(if (uri == null) Proxy.NO_PROXY else proxyFor(uri))

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
    }

    /**
     * The authenticator installed on the shared client. It answers only for a
     * proxy this router selected, so a 407 from an unrelated proxy is not met
     * with credentials meant for another one.
     */
    val proxyAuthenticator: Authenticator = Authenticator { route, response ->
        if (response.request.header("Proxy-Authorization") != null) return@Authenticator null
        val proxy = route?.proxy ?: return@Authenticator null
        val config = configFor(proxy, response.request.url.host) ?: return@Authenticator null
        if (config.username.isEmpty()) return@Authenticator null
        response.request.newBuilder()
            .header("Proxy-Authorization", Credentials.basic(config.username, config.password))
            .build()
    }

    /** Replaces the app-wide endpoint. Returns true when anything changed. */
    fun setGlobal(config: ProxyConfig): Boolean {
        val normalized = config.normalized()
        if (global == normalized) return false
        global = normalized
        installSocksAuthenticatorIfNeeded()
        return true
    }

    /**
     * Replaces every source's own host route. Called by SourceRegistry whenever
     * the source list changes; stream hosts learned since the last call are
     * deliberately dropped, because they belonged to the old configuration.
     */
    fun setSourceRoutes(routes: List<SourceRoute>): Boolean {
        val usable = routes.filter { it.host.isNotBlank() }
        val nextBySource = usable.associate { route ->
            route.sourceId to Route(route.mode, route.config.normalized())
        }
        val nextByHost = usable.associate { route ->
            normalizeProxyHost(route.host).lowercase() to
                Route(route.mode, route.config.normalized())
        }

        synchronized(lock) {
            // Compare only the routes the registry owns. A learned stream host
            // is not part of this list; treating it as a difference would drop
            // it on every unrelated publish (a manifest refresh, a policy
            // toggle) and send the next playback fetch through the global route
            // instead of the source's own.
            if (bySource == nextBySource && baseByHost == nextByHost) return false
            bySource.clear()
            bySource.putAll(nextBySource)
            baseByHost.clear()
            baseByHost.putAll(nextByHost)
            byHost.clear()
            byHost.putAll(nextByHost)
        }
        installSocksAuthenticatorIfNeeded()
        return true
    }

    /**
     * Routes [url]'s host the way [sourceId] is configured.
     *
     * Called when a source hands back a stream URL. The URL is often on a CDN
     * rather than the addon's own host, and the fetch that follows must follow
     * the same proxy decision as the request that produced it.
     */
    fun rememberStreamHost(sourceId: String, url: String) {
        val host = hostOf(url) ?: return
        synchronized(lock) {
            val route = bySource[sourceId] ?: return
            byHost[host] = route
        }
    }

    /** The host part of [url], lowercased, or null when it has none. */
    fun hostOf(url: String): String? {
        val parsed = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val host = parsed.host ?: return null
        if (host.isBlank()) return null
        return normalizeProxyHost(host).lowercase()
    }

    /** The proxy to use for [uri]. Public for tests and for [ProxyTester]. */
    fun proxyFor(uri: URI): Proxy {
        if (uri.scheme != "http" && uri.scheme != "https") return Proxy.NO_PROXY
        val host = uri.host?.let { normalizeProxyHost(it).lowercase() } ?: return Proxy.NO_PROXY
        val route = synchronized(lock) { byHost[host] }
            ?: scopedSource.get()?.let { sourceId -> synchronized(lock) { bySource[sourceId] } }
            ?: return global.proxyOrThrow()
        return when (route.mode) {
            ProxyMode.DIRECT -> Proxy.NO_PROXY
            ProxyMode.CUSTOM -> {
                // A custom route is an explicit request for a proxy. If its
                // endpoint is disabled or invalid, throw rather than falling
                // back to a direct connection.
                if (!route.config.enabled) {
                    throw IllegalStateException("Custom proxy is disabled")
                }
                route.config.proxyOrThrow()
            }
            ProxyMode.INHERIT -> global.proxyOrThrow()
        }
    }

    /**
     * SOCKS5 authentication is not part of the OkHttp authenticator contract:
     * the JDK performs it inside the socket. One authenticator is installed on
     * first use and answers only for a configured SOCKS endpoint.
     */
    private val socksAuthenticator = object : JavaAuthenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication? {
            // The JDK asks for SOCKS credentials through this callback rather
            // than OkHttp's authenticator. Match the endpoint, not the
            // requestor type: some JDK versions label a SOCKS handshake as a
            // server request, and the host/port check below is the real guard.
            val config = socksConfigFor(requestingHost, requestingPort) ?: return null
            if (config.username.isEmpty()) return null
            return PasswordAuthentication(config.username, config.password.toCharArray())
        }
    }

    @Volatile
    private var socksAuthenticatorInstalled = false

    private fun installSocksAuthenticatorIfNeeded() {
        if (socksAuthenticatorInstalled) return
        if (!hasSocksAuth()) return
        JavaAuthenticator.setDefault(socksAuthenticator)
        socksAuthenticatorInstalled = true
    }

    private fun hasSocksAuth(): Boolean {
        val direct = listOfNotNull(global, temporaryAuth).any { it.needsSocksAuth() }
        if (direct) return true
        synchronized(lock) {
            return bySource.values.any { it.mode == ProxyMode.CUSTOM && it.config.needsSocksAuth() }
        }
    }

    private fun ProxyConfig.needsSocksAuth(): Boolean =
        enabled && protocol == ProxyProtocol.SOCKS5 && username.isNotEmpty()

    private fun socksConfigFor(host: String?, port: Int): ProxyConfig? {
        val candidates = allAuthConfigs().filter {
            it.enabled &&
                it.protocol == ProxyProtocol.SOCKS5 &&
                it.port == port &&
                it.username.isNotEmpty()
        }
        if (candidates.isEmpty()) return null
        // Do not guess when the JDK does not name the endpoint. Sending one
        // proxy's credentials to another because it happened to be the only
        // SOCKS configuration is worse than letting the handshake fail.
        val wanted = host?.let { normalizeProxyHost(it).lowercase() } ?: return null
        return candidates.firstOrNull {
            normalizeProxyHost(it.host).lowercase() == wanted
        }
    }

    private fun configFor(proxy: Proxy, requestHost: String? = null): ProxyConfig? {
        val address = proxy.address() as? InetSocketAddress ?: return null
        val host = normalizeProxyHost(address.hostString).lowercase()
        val port = address.port
        temporaryAuth?.let { candidate ->
            if (candidate.matches(host, port)) return candidate
        }
        // Two sources may use the same proxy endpoint with different logins.
        // The request's own host says which of them this 407 belongs to.
        if (requestHost != null) {
            val route = synchronized(lock) {
                byHost[normalizeProxyHost(requestHost).lowercase()]
            }
            if (route?.mode == ProxyMode.CUSTOM && route.config.matches(host, port)) {
                return route.config
            }
        }
        // A redirect may have taken a scoped module fetch to a host nobody
        // registered. The thread's source still says whose credentials belong
        // at the proxy it is already using.
        val scoped = scopedSource.get()?.let { sourceId ->
            synchronized(lock) { bySource[sourceId] }
        }
        if (scoped?.mode == ProxyMode.CUSTOM && scoped.config.matches(host, port)) {
            return scoped.config
        }
        if (global.matches(host, port)) return global
        synchronized(lock) {
            return bySource.values
                .asSequence()
                .map { it.config }
                .firstOrNull { it.matches(host, port) }
        }
    }

    private fun ProxyConfig.matches(host: String, port: Int): Boolean =
        enabled && this.port == port && normalizeProxyHost(this.host).lowercase() == host

    private fun allAuthConfigs(): List<ProxyConfig> {
        val routes = synchronized(lock) { bySource.values.map { it.config } }
        return listOfNotNull(global, temporaryAuth) + routes
    }

    private fun ProxyConfig.proxyOrThrow(): Proxy {
        if (!enabled) return Proxy.NO_PROXY
        val error = validationError(requireConfigured = true)
        if (error != null) {
            throw IllegalStateException("Proxy is enabled but invalid: $error")
        }
        return toJavaProxy()
    }

    internal fun setTemporaryAuth(config: ProxyConfig?) {
        temporaryAuth = config?.normalized()
        installSocksAuthenticatorIfNeeded()
    }
}

/**
 * Tests a proxy by making one request through it.
 *
 * A response other than 407 counts as success: the point is that the proxy was
 * reached and spoke the protocol, not that the particular test URL is allowed.
 * 407 is separated because OkHttp turns a failed proxy challenge into an
 * authentication failure, and reporting "connected" there would be misleading.
 */
object ProxyTester {

    const val DEFAULT_TEST_URL = "https://www.gstatic.com/generate_204"

    suspend fun test(config: ProxyConfig?, targetUrl: String = DEFAULT_TEST_URL): Result<Unit> =
        withContext(Dispatchers.IO) {
            val normalized = config?.normalized()
            val active = normalized?.takeIf { it.enabled }
            if (active != null) {
                val error = active.validationError(requireConfigured = true)
                if (error != null) {
                    return@withContext Result.failure(IllegalArgumentException(error.name))
                }
            }

            val client = Http.client.newBuilder()
                // A test is a one-shot connection: giving it its own pool keeps
                // it from reusing a socket the app opened through another route.
                .connectionPool(ConnectionPool())
                .proxy(active?.toJavaProxy() ?: Proxy.NO_PROXY)
                .proxyAuthenticator { _, response ->
                    if (active == null || active.username.isEmpty()) {
                        null
                    } else if (response.request.header("Proxy-Authorization") != null) {
                        null
                    } else {
                        response.request.newBuilder()
                            .header(
                                "Proxy-Authorization",
                                Credentials.basic(active.username, active.password),
                            )
                            .build()
                    }
                }
                .callTimeout(20, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()

            ProxyRouter.setTemporaryAuth(active)
            try {
                client.newCall(Request.Builder().url(targetUrl).head().build())
                    .execute()
                    .use { response ->
                        if (response.code == 407) {
                            Result.failure(IOException("Proxy authentication failed"))
                        } else {
                            Result.success(Unit)
                        }
                    }
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                ProxyRouter.setTemporaryAuth(null)
                client.connectionPool.evictAll()
            }
        }
}
