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
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Runtime store for Faraday/Zemer-style player configs.
 *
 * The store deliberately separates the normal six-hour refresh from the two
 * failure-triggered refreshes used by Zemer: an unknown player hash and a CDN
 * rejection. Both failure paths are single-flight and rate-limited so a bad
 * player rotation cannot create a request storm.
 */
class RemotePlayerConfigStore(
    private val httpClient: HttpClient,
    internal val repository: PlayerConfigRepository,
    private val logger: InnerTubeLogger = InnerTubeLogger.NONE,
) {
    private companion object {
        private const val TAG = "RemotePlayerConfigStore"
        private const val REFRESH_TTL_MS = 6 * 60 * 60 * 1000L
        private const val FAILURE_RETRY_MS = 60 * 1000L
        private const val FAILURE_REFRESH_COOLDOWN_MS = 5 * 60 * 1000L
        private const val REQUEST_TIMEOUT_MS = 5_000L
        private const val MAX_LEGACY_TIMESTAMP_CACHE_ENTRIES = 16
        private const val MAX_CONFIG_RESPONSE_BYTES = 4 * 1024 * 1024
        private const val LEGACY_FARADAY_CONFIG_URL =
            "https://github.com/MetrolistGroup/faraday/releases/download/{playerTag}/{playerHash}.json"
        private const val OKHTTP_USER_AGENT = "okhttp/5.4.0"
    }

    private val mutex = Mutex()
    private val refreshMutex = Mutex()
    private val legacyTimestampCache = LinkedHashMap<String, CachedLegacyTimestamp>()

    @Volatile
    private var configs: Map<String, RemotePlayerConfigParser.HardcodedPlayerConfig> = emptyMap()

    @Volatile
    private var configSourceUrl: String? = null

    /** Advances whenever a newly fetched or cached table changes the active config set. */
    @Volatile
    internal var configEpoch: Long = 0L
        private set

    @Volatile
    private var lastRefreshAttemptSourceUrl: String? = null

    @Volatile
    private var lastRefreshAttemptAtMs: Long = 0L

    @Volatile
    private var lastUnknownHashRefreshSourceUrl: String? = null

    @Volatile
    private var lastUnknownHashRefreshAtMs: Long = 0L

    @Volatile
    private var lastStreamRejectionRefreshSourceUrl: String? = null

    @Volatile
    private var lastStreamRejectionRefreshAtMs: Long = 0L

    private data class CachedLegacyTimestamp(
        val value: Int?,
        val fetchedAtMs: Long,
    )

    internal suspend fun refreshIfStale() {
        if (!repository.enabled) return
        val now = Clock.System.now().toEpochMilliseconds()
        val sourceUrl = configuredUrl() ?: return
        if (hasFreshCache(sourceUrl, now)) {
            mutex.withLock { ensureLoadedFromCache(sourceUrl) }
            return
        }
        if (hasRecentRefreshAttempt(sourceUrl, now)) {
            mutex.withLock { ensureLoadedFromCache(sourceUrl) }
            return
        }
        refresh(sourceUrl, force = false)
    }

    /**
     * Force a table refresh when the current player hash is not known locally.
     * Returns true only when the active table changed.
     */
    internal suspend fun forceRefresh(missingHash: String? = null): Boolean {
        if (!repository.enabled) return false
        val sourceUrl = configuredUrl() ?: return false
        if (missingHash != null && isKnownHash(missingHash, sourceUrl)) return false
        val reservation = reserveUnknownHashRefresh(sourceUrl) ?: return false
        return try {
            refresh(sourceUrl, force = true)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { releaseUnknownHashRefresh(sourceUrl, reservation) }
            throw e
        }
    }

    /**
     * Force a conditional refresh after a deciphered URL is rejected by the CDN.
     * This is intentionally separate from unknown-hash cooldown: a stale or wrong
     * solver can produce a bad signature without throwing an extraction exception.
     */
    internal suspend fun refreshAfterStreamRejection(): Boolean {
        if (!repository.enabled) return false
        val sourceUrl = configuredUrl() ?: return false
        val reservation = reserveStreamRejectionRefresh(sourceUrl) ?: return false
        return try {
            refresh(sourceUrl, force = true)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { releaseStreamRejectionRefresh(sourceUrl, reservation) }
            throw e
        }
    }

    private suspend fun reserveUnknownHashRefresh(sourceUrl: String): Long? =
        mutex.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            if (withinCooldown(lastUnknownHashRefreshSourceUrl, lastUnknownHashRefreshAtMs, sourceUrl, now)) {
                return@withLock null
            }
            lastUnknownHashRefreshSourceUrl = sourceUrl
            lastUnknownHashRefreshAtMs = now
            now
        }

    private suspend fun reserveStreamRejectionRefresh(sourceUrl: String): Long? =
        mutex.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            if (withinCooldown(lastStreamRejectionRefreshSourceUrl, lastStreamRejectionRefreshAtMs, sourceUrl, now)) {
                return@withLock null
            }
            lastStreamRejectionRefreshSourceUrl = sourceUrl
            lastStreamRejectionRefreshAtMs = now
            now
        }

    private suspend fun releaseUnknownHashRefresh(
        sourceUrl: String,
        reservedAt: Long,
    ) {
        mutex.withLock {
            if (lastUnknownHashRefreshSourceUrl == sourceUrl && lastUnknownHashRefreshAtMs == reservedAt) {
                lastUnknownHashRefreshSourceUrl = null
                lastUnknownHashRefreshAtMs = 0L
            }
        }
    }

    private suspend fun releaseStreamRejectionRefresh(
        sourceUrl: String,
        reservedAt: Long,
    ) {
        mutex.withLock {
            if (lastStreamRejectionRefreshSourceUrl == sourceUrl && lastStreamRejectionRefreshAtMs == reservedAt) {
                lastStreamRejectionRefreshSourceUrl = null
                lastStreamRejectionRefreshAtMs = 0L
            }
        }
    }

    private suspend fun refresh(
        sourceUrl: String,
        force: Boolean,
    ): Boolean =
        refreshMutex.withLock refreshLock@{
            if (configuredUrl() != sourceUrl) return@refreshLock false
            val now = Clock.System.now().toEpochMilliseconds()
            if (!force && hasFreshCache(sourceUrl, now)) {
                mutex.withLock { ensureLoadedFromCache(sourceUrl) }
                return@refreshLock false
            }
            if (!force && hasRecentRefreshAttempt(sourceUrl, now)) {
                mutex.withLock { ensureLoadedFromCache(sourceUrl) }
                return@refreshLock false
            }

            val cachedEtag =
                repository.cachedEtag
                    .takeIf { repository.cachedSourceUrl == sourceUrl }
                    ?.takeIf { it.isNotBlank() }
            val response =
                try {
                    val validatedSourceUrl = validatedSourceUrlOrNull(sourceUrl) ?: return@refreshLock false
                    httpClient.getTextWithoutRedirects(validatedSourceUrl, MAX_CONFIG_RESPONSE_BYTES) {
                        header(HttpHeaders.UserAgent, OKHTTP_USER_AGENT)
                        header(HttpHeaders.Accept, "application/json")
                        cachedEtag?.let { header(HttpHeaders.IfNoneMatch, it) }
                        timeout {
                            requestTimeoutMillis = REQUEST_TIMEOUT_MS
                            connectTimeoutMillis = REQUEST_TIMEOUT_MS
                            socketTimeoutMillis = REQUEST_TIMEOUT_MS
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.w(TAG, "Remote player config refresh failed (${e.logType()})")
                    null
                }

            lastRefreshAttemptSourceUrl = sourceUrl
            lastRefreshAttemptAtMs = Clock.System.now().toEpochMilliseconds()

            mutex.withLock stateLock@{
                if (configuredUrl() != sourceUrl) return@stateLock false
                if (response == null) {
                    ensureLoadedFromCache(sourceUrl)
                    return@stateLock false
                }

                val status = response.status.value
                val responseEtag = response.headers[HttpHeaders.ETag]
                if (status == 304) {
                    val fetchedAt = Clock.System.now().toEpochMilliseconds()
                    repository.cachedAtMs = fetchedAt
                    if (!responseEtag.isNullOrBlank()) repository.cachedEtag = responseEtag
                    ensureLoadedFromCache(sourceUrl)
                    logger.d(TAG, "Remote player configs unchanged (304)")
                    return@stateLock false
                }
                if (status !in 200..299) {
                    logger.w(TAG, "Remote player configs HTTP $status")
                    ensureLoadedFromCache(sourceUrl)
                    return@stateLock false
                }

                val body = response.body ?: return@stateLock false
                when (val result = RemotePlayerConfigParser.parse(body)) {
                    is RemotePlayerConfigParser.ParseResult.Success -> {
                        val changed = applyConfigs(sourceUrl, result.configs)
                        repository.cachedJson = body
                        repository.cachedAtMs = Clock.System.now().toEpochMilliseconds()
                        repository.cachedSourceUrl = sourceUrl
                        responseEtag?.let { repository.cachedEtag = it }
                        logger.d(
                            TAG,
                            "Remote player configs applied (${result.configs.size} entries) changed=$changed epoch=$configEpoch",
                        )
                        return@stateLock changed
                    }

                    is RemotePlayerConfigParser.ParseResult.Failure -> {
                        logger.w(TAG, "Remote player configs rejected: ${result.reason}")
                        ensureLoadedFromCache(sourceUrl)
                        return@stateLock false
                    }
                }
            }
        }

    suspend fun getSignatureTimestamp(playerUrl: String): Int? =
        getConfig(playerUrl)?.signatureTimestamp ?: getLegacySignatureTimestamp(playerUrl)

    internal suspend fun getConfig(playerUrl: String): RemotePlayerConfigParser.HardcodedPlayerConfig? {
        if (!repository.enabled) return null
        val hash = RemotePlayerConfigParser.extractPlayerHash(playerUrl) ?: return null
        val sourceUrl = configuredUrl() ?: return null
        refreshIfStale()
        return configs.takeIf { configSourceUrl == sourceUrl }?.get(hash)
    }

    private suspend fun isKnownHash(
        hash: String,
        sourceUrl: String,
    ): Boolean {
        mutex.withLock { ensureLoadedFromCache(sourceUrl) }
        return configs.takeIf { configSourceUrl == sourceUrl }?.containsKey(hash) == true
    }

    private fun applyConfigs(
        sourceUrl: String,
        newConfigs: Map<String, RemotePlayerConfigParser.HardcodedPlayerConfig>,
    ): Boolean {
        val changed = configSourceUrl != sourceUrl || configs != newConfigs
        configs = newConfigs
        configSourceUrl = sourceUrl
        if (changed) configEpoch += 1L
        return changed
    }

    private fun hasFreshCache(
        sourceUrl: String,
        now: Long,
    ): Boolean =
        repository.cachedJson.isNotBlank() &&
            repository.cachedSourceUrl == sourceUrl &&
            now - repository.cachedAtMs < REFRESH_TTL_MS

    private fun hasRecentRefreshAttempt(
        sourceUrl: String,
        now: Long,
    ): Boolean =
        lastRefreshAttemptSourceUrl == sourceUrl &&
            now - lastRefreshAttemptAtMs in 0 until FAILURE_RETRY_MS

    private fun withinCooldown(
        previousSourceUrl: String?,
        previousAtMs: Long,
        sourceUrl: String,
        now: Long,
    ): Boolean =
        previousSourceUrl == sourceUrl &&
            (now <= previousAtMs || now - previousAtMs < FAILURE_REFRESH_COOLDOWN_MS)

    private fun ensureLoadedFromCache(sourceUrl: String) {
        if (configSourceUrl == sourceUrl && configs.isNotEmpty()) return
        if (repository.cachedSourceUrl != sourceUrl) return
        val cached = repository.cachedJson
        if (cached.isBlank()) return
        when (val result = RemotePlayerConfigParser.parse(cached)) {
            is RemotePlayerConfigParser.ParseResult.Success -> {
                applyConfigs(sourceUrl, result.configs)
            }

            is RemotePlayerConfigParser.ParseResult.Failure -> {
                logger.w(TAG, "Cached remote player configs rejected: ${result.reason}")
            }
        }
    }

    private suspend fun getLegacySignatureTimestamp(playerUrl: String): Int? {
        if (!repository.enabled) return null
        val playerHash = RemotePlayerConfigParser.extractPlayerHash(playerUrl) ?: return null
        val url = legacyConfigUrl(playerHash) ?: return null
        val now = Clock.System.now().toEpochMilliseconds()
        mutex.withLock {
            legacyTimestampCache
                .remove(url)
                ?.takeIf { now - it.fetchedAtMs < REFRESH_TTL_MS }
                ?.let { cached ->
                    legacyTimestampCache[url] = cached
                    return cached.value
                }
        }

        val value =
            try {
                val response =
                    httpClient.getTextWithoutRedirects(Url(url), MAX_CONFIG_RESPONSE_BYTES) {
                        header(HttpHeaders.UserAgent, OKHTTP_USER_AGENT)
                        header(HttpHeaders.Accept, "application/json")
                        timeout {
                            requestTimeoutMillis = REQUEST_TIMEOUT_MS
                            connectTimeoutMillis = REQUEST_TIMEOUT_MS
                            socketTimeoutMillis = REQUEST_TIMEOUT_MS
                        }
                    }
                response.body?.let { body -> RemotePlayerConfigParser.parseLegacySignatureTimestamp(body, playerHash) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.d(TAG, "Legacy player config timestamp unavailable hash=$playerHash type=${e.logType()}")
                null
            }

        mutex.withLock {
            legacyTimestampCache[url] = CachedLegacyTimestamp(value, now)
            while (legacyTimestampCache.size > MAX_LEGACY_TIMESTAMP_CACHE_ENTRIES) {
                legacyTimestampCache.remove(legacyTimestampCache.keys.firstOrNull() ?: break)
            }
        }
        return value
    }

    private fun legacyConfigUrl(playerHash: String): String? {
        val configured = repository.sourceUrl.trim()
        if (
            configured.isBlank() ||
            configured == LEGACY_FARADAY_CONFIG_URL ||
            configured.substringBefore('?').endsWith("/player_configs.json")
        ) {
            return null
        }
        return configured
            .replace("{playerHash}", playerHash)
            .replace("{playerTag}", "player-$playerHash")
            .takeIf { validatedSourceUrlOrNull(it) != null }
    }

    private fun configuredUrl(): String? =
        repository.sourceUrl
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { configured ->
                when {
                    configured == LEGACY_FARADAY_CONFIG_URL -> repository.defaultSourceUrl
                    configured.substringBefore('?').endsWith("/player_configs.json") -> configured
                    else -> null
                }
            }?.takeIf { validatedSourceUrlOrNull(it) != null }

    internal fun validatedSourceUrlOrNull(value: String): Url? =
        runCatching { Url(value) }
            .getOrNull()
            ?.takeIf { url ->
                url.protocol.name == "https" &&
                    when (url.host) {
                        // Our own published table comes first; the MetrolistGroup paths stay so that
                        // pointing [FaradayCipherEngine.PLAYER_CONFIG_URL] back at upstream is a
                        // one-line change if our registry ever stops being published.
                        "raw.githubusercontent.com" ->
                            url.encodedPath.startsWith("/maxrave-dev/simpmusic-files/") ||
                                url.encodedPath.startsWith("/MetrolistGroup/faraday/")
                        "cdn.jsdelivr.net" -> url.encodedPath.startsWith("/gh/MetrolistGroup/faraday@")
                        "github.com" -> url.encodedPath.startsWith("/MetrolistGroup/faraday/releases/download/")
                        else -> false
                    }
            }

    private fun Throwable.logType(): String = this::class.simpleName ?: "Exception"
}
