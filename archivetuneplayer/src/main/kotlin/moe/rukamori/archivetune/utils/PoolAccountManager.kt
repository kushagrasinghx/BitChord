/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.constants.PoolApiKeyKey
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object PoolAccountManager {
    private const val TAG = "PoolAccounts"

    private const val MIN_REFRESH_INTERVAL_MS = 24 * 60 * 60 * 1000L

    private const val MIN_PARTIAL_REFRESH_INTERVAL_MS = 5 * 60 * 60 * 1000L

    private val CACHE_TIDAL_KEY = stringPreferencesKey("poolTidalAccounts")
    private val CACHE_QOBUZ_KEY = stringPreferencesKey("poolQobuzAccounts")
    private val CACHE_DEEZER_KEY = stringPreferencesKey("poolDeezerAccounts")
    private val CACHE_APPLE_KEY = stringPreferencesKey("poolAppleMusicAccounts")

    @Volatile
    private var poolApiKey: String? = null

    @Volatile
    private var appContext: Context? = null

    @Volatile
    var lastFeedError: String? = null
        private set

    private val reportDedupe = ConcurrentHashMap<String, Long>()
    private const val REPORT_DEDUPE_WINDOW_MS = 10 * 60 * 1000L

    data class TidalPoolAccount(
        val id: Long?,
        val token: String,
        val refreshToken: String?,
        val countryCode: String?,
        val premium: Boolean,
    )

    data class QobuzPoolAccount(
        val id: Long?,
        val token: String,
        val appId: String,
        val appSecret: String,
        val premium: Boolean,
    )

    data class DeezerPoolAccount(
        val id: Long?,
        val arl: String,
        val premium: Boolean,

        val masterSecret: String? = null,
    )

    data class AppleMusicPoolAccount(
        val id: Long?,
        val mediaUserToken: String,
        val premium: Boolean,
    )

    @Volatile
    private var tidalCache: List<TidalPoolAccount> = emptyList()

    @Volatile
    private var qobuzCache: List<QobuzPoolAccount> = emptyList()

    @Volatile
    private var deezerCache: List<DeezerPoolAccount> = emptyList()

    @Volatile
    private var appleMusicCache: List<AppleMusicPoolAccount> = emptyList()

    @Volatile
    var lastRefreshAtMillis = 0L
        private set

    @Volatile
    private var lastLaunchRefreshAt = 0L

    @Volatile
    private var lastFeedFailureAt = 0L

    @Volatile
    private var loadedFromDisk = false

    private val refreshMutex = Mutex()

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaTypeOrNull()

    private val reportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val isEnabled: Boolean
        get() = BuildConfig.SOURCE_PROVIDER_URL.isNotBlank()

    fun isPoolEnabled(): Boolean = isEnabled

    private val poolBaseUrl: String?
        get() {
            val raw = BuildConfig.SOURCE_PROVIDER_URL.trim()
            if (raw.isEmpty()) return null
            return raw
                .replace(Regex("(?i)/api/(sources|accounts)/?$"), "")
                .trimEnd('/')
                .takeIf { it.isNotEmpty() }
        }

    private val accountsUrl: String? get() = poolBaseUrl?.let { "$it/api/accounts" }

    private val legacySourcesUrl: String? get() = poolBaseUrl?.let { "$it/api/sources" }

    private const val ACCOUNT_COOLDOWN_MS = 10 * 60 * 1000L

    private const val FEED_FAILURE_BACKOFF_MS = 5 * 60 * 1000L

    private val accountCooldownUntil = ConcurrentHashMap<String, Long>()

    private fun cooldownKey(service: String, id: Long?) = "$service:${id ?: 0L}"

    private fun isCoolingDown(service: String, id: Long?): Boolean {
        val key = cooldownKey(service, id)
        val until = accountCooldownUntil[key] ?: return false
        if (until > System.currentTimeMillis()) return true
        accountCooldownUntil.remove(key, until)
        return false
    }

    fun noteAccountFailure(service: String, id: Long?) {
        if (id == null || id <= 0L) return
        accountCooldownUntil[cooldownKey(service, id)] = System.currentTimeMillis() + ACCOUNT_COOLDOWN_MS
    }

    fun noteAccountSuccess(service: String, id: Long?) {
        if (id == null || id <= 0L) return
        accountCooldownUntil.remove(cooldownKey(service, id))
    }

    private fun <T> ordered(
        service: String,
        accounts: List<T>,
        idOf: (T) -> Long?,
        premiumOf: (T) -> Boolean,
    ): List<T> = accounts.sortedWith(compareBy({ isCoolingDown(service, idOf(it)) }, { !premiumOf(it) }))

    fun tidalAccounts(): List<TidalPoolAccount> =
        ordered("tidal", tidalCache, { it.id }, { it.premium })

    fun qobuzAccounts(): List<QobuzPoolAccount> =
        ordered("qobuz", qobuzCache, { it.id }, { it.premium })

    fun deezerAccounts(): List<DeezerPoolAccount> =
        ordered("deezer", deezerCache, { it.id }, { it.premium })

    fun appleMusicAccounts(): List<AppleMusicPoolAccount> = appleMusicCache.sortedByDescending { it.premium }

    fun hasAccounts(): Boolean =
        tidalCache.isNotEmpty() || qobuzCache.isNotEmpty() || deezerCache.isNotEmpty() ||
            appleMusicCache.isNotEmpty()

    private fun hasEveryService(): Boolean =
        tidalCache.isNotEmpty() && qobuzCache.isNotEmpty() && deezerCache.isNotEmpty() && appleMusicCache.isNotEmpty()

    private const val LAUNCH_REFRESH_THROTTLE_MS = 10L * 60L * 1000L

    private fun refreshIntervalMs(): Long =
        if (hasEveryService()) MIN_REFRESH_INTERVAL_MS else MIN_PARTIAL_REFRESH_INTERVAL_MS

    suspend fun loadCached(context: Context) {
        if (loadedFromDisk) return
        appContext = context.applicationContext
        withContext(Dispatchers.IO) {
            runCatching {
                val passthrough: (String) -> String? = { raw -> raw }
                cached(context, CACHE_TIDAL_KEY)?.takeIf { it.isNotBlank() }?.let {
                    tidalCache = parseTidal(JSONArray(it), passthrough)
                }
                cached(context, CACHE_QOBUZ_KEY)?.takeIf { it.isNotBlank() }?.let {
                    qobuzCache = parseQobuz(JSONArray(it), passthrough)
                }
                cached(context, CACHE_DEEZER_KEY)?.takeIf { it.isNotBlank() }?.let {
                    deezerCache = parseDeezer(JSONArray(it), passthrough)
                }
                cached(context, CACHE_APPLE_KEY)?.takeIf { it.isNotBlank() }?.let {
                    appleMusicCache = parseAppleMusic(JSONArray(it), passthrough)
                }
                loadedFromDisk = true
                Timber.tag(TAG).d(
                    "Loaded cached accounts: tidal=%d qobuz=%d deezer=%d apple=%d",
                    tidalCache.size,
                    qobuzCache.size,
                    deezerCache.size,
                    appleMusicCache.size,
                )
            }.onFailure { Timber.tag(TAG).w(it, "Failed to load cached pool accounts") }
        }
    }

    suspend fun refreshForLaunch(context: Context): Boolean =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (now - lastLaunchRefreshAt < LAUNCH_REFRESH_THROTTLE_MS) {
                return@withContext hasAccounts()
            }
            lastLaunchRefreshAt = now
            refresh(context, force = true)
        }

    suspend fun refresh(
        context: Context,
        force: Boolean = false,
    ): Boolean =
        withContext(Dispatchers.IO) {
            appContext = context.applicationContext
            if (!isEnabled) return@withContext false
            loadCached(context)

            val now = System.currentTimeMillis()
            if (!force && hasAccounts() && now - lastRefreshAtMillis < refreshIntervalMs()) {
                return@withContext true
            }
            if (!force && now - lastFeedFailureAt < FEED_FAILURE_BACKOFF_MS) {
                return@withContext hasAccounts()
            }

            refreshMutex.withLock {
                if (!force && hasAccounts() && System.currentTimeMillis() - lastRefreshAtMillis < refreshIntervalMs()) {
                    return@withLock true
                }
                if (!force && System.currentTimeMillis() - lastFeedFailureAt < FEED_FAILURE_BACKOFF_MS) {
                    return@withLock hasAccounts()
                }
                val url = accountsUrl ?: legacySourcesUrl
                if (url == null) {
                    lastFeedError = null
                    Timber.tag(TAG).d("No Source Pool URL configured; nothing to refresh")
                } else {
                    val userKey =
                        runCatching { context.dataStore.getAsync(PoolApiKeyKey) }
                            .getOrNull()
                            ?.trim()
                            .orEmpty()
                    val readKey = userKey.ifBlank { BuildConfig.SOURCE_PROVIDER_KEY }
                    poolApiKey = readKey.ifBlank { null }

                    var result = fetchAccounts(context, url, readKey)

                    if (!result.succeeded && result.code == 404 && url == accountsUrl && legacySourcesUrl != null) {
                        Timber.tag(TAG).d("/api/accounts unavailable; falling back to legacy /api/sources")
                        result = fetchAccounts(context, legacySourcesUrl!!, readKey)
                    }

                    lastFeedError =
                        when {
                            result.succeeded -> null

                            result.code == 404 -> {
                                Timber.tag(TAG).e(
                                    "No pool API at %s — both /api/accounts and /api/sources returned 404. " +
                                        "SOURCE_PROVIDER_URL points at a host that is not an ArchivePool deployment " +
                                        "(this is NOT a read-key problem; a bad key answers 401).",
                                    poolBaseUrl,
                                )
                                "No pool API at $poolBaseUrl (HTTP 404) — that URL is not an ArchivePool deployment."
                            }
                            result.code == 401 ->
                                "Pool rejected the API key (HTTP 401) — SOURCE_PROVIDER_KEY is missing, revoked, " +
                                    "or issued by a different deployment."
                            result.code == 0 -> "Could not reach $poolBaseUrl — network error."
                            else -> "Pool feed returned HTTP ${result.code}."
                        }

                    lastFeedFailureAt = if (result.succeeded) 0L else System.currentTimeMillis()
                }

                hasAccounts()
            }
        }

    private class FeedFetch(
        val json: JSONObject?,
        val code: Int,
    ) {
        val succeeded: Boolean get() = code == 200 && json != null
    }

    private suspend fun fetchAccounts(
        context: Context,
        url: String,
        readKey: String,
    ): FeedFetch {
        val builder =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", "ArchiveTune-Android")

                .header("X-Pool-Client", "v2")
        if (readKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $readKey")
        }
        return try {
            client.newCall(builder.get().build()).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 401) {
                        Timber.tag(TAG).w(
                            "Pool account feed rejected the presented key (HTTP 401) — " +
                                "it is revoked, deleted, or predates the pool's current database.",
                        )
                    } else {
                        Timber.tag(TAG).w("Pool account feed %s returned HTTP %d", url, response.code)
                    }
                    return@use FeedFetch(null, response.code)
                }
                val root = JSONObject(response.body?.string().orEmpty())
                val decryptor = decryptorFor(root, readKey)
                val tidal = parseTidal(accountsArray(root, "tidal"), decryptor)
                val qobuz = parseQobuz(accountsArray(root, "qobuz"), decryptor)
                val deezer = parseDeezer(accountsArray(root, "deezer"), decryptor)
                val apple = parseAppleMusic(accountsArray(root, "apple-music"), decryptor)

                val allEmpty = tidal.isEmpty() && qobuz.isEmpty() && deezer.isEmpty() && apple.isEmpty()
                if (allEmpty && hasAccounts()) {
                    Timber
                        .tag(TAG)
                        .w("Pool returned empty account lists — keeping existing cache to avoid mid-playback source disappearance")
                } else {
                    tidalCache = tidal
                    qobuzCache = qobuz
                    deezerCache = deezer
                    appleMusicCache = apple
                    lastRefreshAtMillis = System.currentTimeMillis()
                    persist(context, tidal, qobuz, deezer, apple)
                }
                Timber.tag(TAG).i(
                    "Pool accounts refreshed: tidal=%d qobuz=%d deezer=%d apple=%d",
                    tidal.size,
                    qobuz.size,
                    deezer.size,
                    apple.size,
                )
                FeedFetch(root, 200)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Pool account refresh failed")
            FeedFetch(null, 0)
        }
    }

    private suspend fun persist(
        context: Context,
        tidal: List<TidalPoolAccount>,
        qobuz: List<QobuzPoolAccount>,
        deezer: List<DeezerPoolAccount>,
        apple: List<AppleMusicPoolAccount>,
    ) {
        val tidalJson =
            JSONArray().apply {
                tidal.forEach {
                    put(
                        JSONObject()
                            .put("id", it.id)
                            .put("token", it.token)
                            .put("refreshToken", it.refreshToken)
                            .put("countryCode", it.countryCode)
                            .put("premium", it.premium),
                    )
                }
            }.toString()
        val qobuzJson =
            JSONArray().apply {
                qobuz.forEach {
                    put(
                        JSONObject()
                            .put("id", it.id)
                            .put("token", it.token)
                            .put("appId", it.appId)
                            .put("appSecret", it.appSecret)
                            .put("premium", it.premium),
                    )
                }
            }.toString()
        val deezerJson =
            JSONArray().apply {
                deezer.forEach {
                    put(
                        JSONObject()
                            .put("id", it.id)
                            .put("arl", it.arl)
                            .put("masterSecret", it.masterSecret)
                            .put("premium", it.premium),
                    )
                }
            }.toString()
        val appleJson =
            JSONArray().apply {
                apple.forEach {
                    put(
                        JSONObject()
                            .put("id", it.id)
                            .put("token", it.mediaUserToken)
                            .put("premium", it.premium),
                    )
                }
            }.toString()
        runCatching {
            context.dataStore.edit { prefs ->
                prefs[CACHE_TIDAL_KEY] = PoolCacheCrypto.encrypt(tidalJson)
                prefs[CACHE_QOBUZ_KEY] = PoolCacheCrypto.encrypt(qobuzJson)
                prefs[CACHE_DEEZER_KEY] = PoolCacheCrypto.encrypt(deezerJson)
                prefs[CACHE_APPLE_KEY] = PoolCacheCrypto.encrypt(appleJson)
            }
        }.onFailure { Timber.tag(TAG).w(it, "Failed to persist pool accounts") }
    }

    private suspend fun cached(context: Context, key: androidx.datastore.preferences.core.Preferences.Key<String>): String? {
        val raw = context.dataStore.getAsync(key)?.takeIf { it.isNotBlank() } ?: return null
        return PoolCacheCrypto.decrypt(raw) ?: raw
    }

    fun report(
        service: String,
        kind: String,
        id: Long?,
        reportType: String,
    ) {
        if (id == null) return
        val base = poolBaseUrl ?: return

        val dedupeKey = "$service:$id:$reportType"
        val now = System.currentTimeMillis()
        val lastReported = reportDedupe[dedupeKey]
        if (lastReported != null && now - lastReported < REPORT_DEDUPE_WINDOW_MS) {
            return
        }
        reportDedupe[dedupeKey] = now

        reportDedupe.entries.removeIf { (_, ts) -> now - ts > REPORT_DEDUPE_WINDOW_MS }

        reportScope.launch {
            runCatching {
                val body =
                    JSONObject()
                        .put("service", service)
                        .put("kind", kind)
                        .put("id", id)
                        .put("report", reportType)
                        .toString()
                val builder =
                    Request
                        .Builder()
                        .url("$base/api/report")
                        .header("User-Agent", "ArchiveTune-Android")
                        .header("X-Pool-Client", "v2")
                        .post(body.toRequestBody(JSON_MEDIA))
                val readKey = poolApiKey?.takeIf { it.isNotBlank() } ?: BuildConfig.SOURCE_PROVIDER_KEY
                if (readKey.isNotBlank()) {
                    builder.header("Authorization", "Bearer $readKey")
                }
                client.newCall(builder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.tag(TAG).d("Pool report %s/%s/%s returned HTTP %d", service, reportType, id, response.code)
                        return@use
                    }
                    val responseBody = response.body?.string().orEmpty()
                    if (responseBody.isNotBlank()) {
                        val root = JSONObject(responseBody)
                        if (root.optBoolean("ok", false) && kind == "account" && readKey.isNotBlank()) {
                            mergeReplacement(root, service, id, readKey)
                        }
                    }
                }
            }.onFailure { Timber.tag(TAG).d("Pool report failed: %s", it.message ?: it.javaClass.simpleName) }
        }
    }

    private fun accountsArray(root: JSONObject, service: String): JSONArray? =
        root.optJSONObject(service)?.optJSONArray("accounts") ?: root.optJSONArray(service)

    private fun field(
        obj: JSONObject,
        key: String,
        decryptor: (String) -> String?,
    ): String? {
        val raw = obj.optString(key, "").takeIf { it.isNotBlank() } ?: return null
        val decoded = decryptor(raw)?.takeIf { it.isNotBlank() }
        if (decoded == null && PoolCrypto.isEncrypted(raw)) {
            Timber.tag(TAG).w("Dropped encrypted pool field %s because no available key could decrypt it", key)
        }
        return decoded
    }

    private fun decryptorFor(root: JSONObject, readKey: String): (String) -> String? {
        val derivedKey = PoolCrypto.deriveClientKey(readKey)
        val encryptionScheme = root.optString("encryption", "")
        return { raw ->
            if (encryptionScheme == "read-key") {
                PoolCrypto.maybeDecryptWith(raw, derivedKey) ?: PoolCrypto.maybeDecrypt(raw)
            } else {
                PoolCrypto.maybeDecrypt(raw) ?: PoolCrypto.maybeDecryptWith(raw, derivedKey)
            }
        }
    }

    private suspend fun mergeReplacement(
        root: JSONObject,
        service: String,
        deadId: Long,
        readKey: String,
    ) {
        val ctx = appContext ?: return
        val replacementObj = root.optJSONObject("replacement") ?: return
        val decryptor = decryptorFor(replacementObj, readKey)
        val replacementArr = replacementObj.optJSONObject(service)?.optJSONArray("accounts") ?: JSONArray()

        refreshMutex.withLock {
            when (service) {
                "tidal" -> tidalCache = mergeList(tidalCache, deadId, TidalPoolAccount::id, replacementArr, ::parseTidal, decryptor) ?: return@withLock
                "qobuz" -> qobuzCache = mergeList(qobuzCache, deadId, QobuzPoolAccount::id, replacementArr, ::parseQobuz, decryptor) ?: return@withLock
                "deezer" -> deezerCache = mergeList(deezerCache, deadId, DeezerPoolAccount::id, replacementArr, ::parseDeezer, decryptor) ?: return@withLock
                "apple-music" -> appleMusicCache = mergeList(appleMusicCache, deadId, AppleMusicPoolAccount::id, replacementArr, ::parseAppleMusic, decryptor) ?: return@withLock
                else -> return@withLock
            }
            persist(ctx, tidalCache, qobuzCache, deezerCache, appleMusicCache)
        }
    }

    private fun <T> mergeList(
        cacheList: List<T>,
        deadId: Long,
        idOf: (T) -> Long?,
        replacementArr: JSONArray,
        parse: (JSONArray?, (String) -> String?) -> List<T>,
        decryptor: (String) -> String?,
    ): List<T>? {
        val deadIndex = cacheList.indexOfFirst { idOf(it) == deadId }
        if (deadIndex < 0) return null

        if (replacementArr.length() == 0) {
            return cacheList.filterIndexed { idx, _ -> idx != deadIndex }
        }

        val replacementEntry = parse(replacementArr, decryptor).firstOrNull() ?: return null

        return if (cacheList.any { idOf(it) == idOf(replacementEntry) }) {
            cacheList.filterIndexed { idx, _ -> idx != deadIndex }
        } else {
            cacheList.mapIndexed { idx, entry -> if (idx == deadIndex) replacementEntry else entry }
        }
    }

    private fun parseTidal(
        arr: JSONArray?,
        decryptor: (String) -> String?,
    ): List<TidalPoolAccount> {
        if (arr == null) return emptyList()
        val out = mutableListOf<TidalPoolAccount>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val token = field(obj, "token", decryptor) ?: continue
            out +=
                TidalPoolAccount(
                    id = entryId(obj),
                    token = token,
                    refreshToken = field(obj, "refreshToken", decryptor),
                    countryCode = field(obj, "countryCode", decryptor),
                    premium = obj.optBoolean("premium", false),
                )
        }
        return out
    }

    private fun parseQobuz(
        arr: JSONArray?,
        decryptor: (String) -> String?,
    ): List<QobuzPoolAccount> {
        if (arr == null) return emptyList()
        val out = mutableListOf<QobuzPoolAccount>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val token = field(obj, "token", decryptor) ?: continue
            val appId = field(obj, "appId", decryptor) ?: continue

            val appSecret = field(obj, "appSecret", decryptor) ?: continue
            out +=
                QobuzPoolAccount(
                    id = entryId(obj),
                    token = token,
                    appId = appId,
                    appSecret = appSecret,
                    premium = obj.optBoolean("premium", false),
                )
        }
        return out
    }

    private fun parseDeezer(
        arr: JSONArray?,
        decryptor: (String) -> String?,
    ): List<DeezerPoolAccount> {
        if (arr == null) return emptyList()
        val out = mutableListOf<DeezerPoolAccount>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val arl = field(obj, "arl", decryptor) ?: continue
            out +=
                DeezerPoolAccount(
                    id = entryId(obj),
                    arl = arl,
                    premium = obj.optBoolean("premium", false),
                    masterSecret = field(obj, "masterSecret", decryptor),
                )
        }
        return out
    }

    private fun entryId(obj: JSONObject): Long? =
        obj.optLong("id", 0L).takeIf { it > 0L }

    private fun parseAppleMusic(
        arr: JSONArray?,
        decryptor: (String) -> String?,
    ): List<AppleMusicPoolAccount> {
        if (arr == null) return emptyList()
        val out = mutableListOf<AppleMusicPoolAccount>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val token = field(obj, "token", decryptor) ?: continue
            if (!token.startsWith("0.")) continue
            out +=
                AppleMusicPoolAccount(
                    id = entryId(obj),
                    mediaUserToken = token,
                    premium = obj.optBoolean("premium", true),
                )
        }
        return out
    }
}
