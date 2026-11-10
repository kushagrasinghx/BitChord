/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ai.AiLyricsRomanizer
import moe.rukamori.archivetune.ai.AiServiceConfig
import moe.rukamori.archivetune.constants.AiApiKeyKey
import moe.rukamori.archivetune.constants.AiCustomEndpointKey
import moe.rukamori.archivetune.constants.AiCustomModelKey
import moe.rukamori.archivetune.constants.AiProvider
import moe.rukamori.archivetune.constants.AiProviderKey
import moe.rukamori.archivetune.constants.AiRomanizeApiKeyKey
import moe.rukamori.archivetune.constants.AiRomanizeCustomEndpointKey
import moe.rukamori.archivetune.constants.AiRomanizeCustomModelKey
import moe.rukamori.archivetune.constants.AiRomanizeExcludedLanguagesKey
import moe.rukamori.archivetune.constants.AiRomanizeLyricsKey
import moe.rukamori.archivetune.constants.AiRomanizeProviderKey
import moe.rukamori.archivetune.constants.AiRomanizeSelectedModelKey
import moe.rukamori.archivetune.constants.AiRomanizeSeparateProviderKey
import moe.rukamori.archivetune.constants.AiSelectedModelKey
import moe.rukamori.archivetune.constants.AutoAiRomanizeLyricsKey
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import androidx.compose.runtime.getValue

object AiLyricsRomanization {
    private const val TAG = "AiRomanization"

    private const val CacheFileName = "ai_romanization_cache.json"
    private const val MaxCachedTracks = 256
    private const val SaveDebounceMs = 1_500L

    enum class RequestStatus {
        STARTED,

        ALREADY_CACHED,

        IN_FLIGHT,

        SETTINGS_DISABLED,

        NO_LYRICS,

        EXCLUDED_LANGUAGE,

        NO_ROMANIZABLE_SCRIPT,

        EMPTY_RESULT,
    }

    @Immutable
    data class Settings(
        val enabled: Boolean,
        val auto: Boolean,
        val excludedLanguages: Set<String>,
        val config: AiServiceConfig,
    ) {
        val active: Boolean get() = enabled && config.canCallApi

        val configKey: String
            get() = "${config.provider}|${config.apiKey}|${config.customEndpoint}|${config.model}"

        companion object {
            val Disabled =
                Settings(
                    enabled = false,
                    auto = false,
                    excludedLanguages = emptySet(),
                    config = AiServiceConfig(AiProvider.NONE, "", "", ""),
                )
        }
    }

    class Result(
        val sessionKey: String,
        val byLine: Map<String, String>,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val romanizer = AiLyricsRomanizer()
    private val inFlight = ConcurrentHashMap<String, Deferred<List<String?>?>>()

    private val cache =
        object : LinkedHashMap<String, Map<String, String>>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Map<String, String>>): Boolean =
                size > 64
        }

    private val _results = MutableStateFlow<Result?>(null)
    val results: StateFlow<Result?> = _results.asStateFlow()

    private val _requestOutcomes = MutableSharedFlow<RequestStatus>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    val requestOutcomes: SharedFlow<RequestStatus> = _requestOutcomes.asSharedFlow()

    private val _running = MutableStateFlow(false)

    val running: StateFlow<Boolean> = _running.asStateFlow()

    @Volatile
    private var cacheFile: File? = null

    private var saveJob: Job? = null

    fun attach(context: Context) {
        if (cacheFile != null) return
        val file = File(context.filesDir, CacheFileName)
        cacheFile = file
        scope.launch {
            val persisted = runCatching { readCacheFile(file) }.getOrNull()
            if (persisted != null) {
                synchronized(cache) {
                    cache.putAll(persisted)
                    trimCacheLocked()
                }
                Timber.tag(TAG).d("restored %d romanisation cache entries", persisted.size)
            }
        }
    }

    private fun readCacheFile(file: File): Map<String, Map<String, String>> {
        if (!file.exists()) return emptyMap()
        val root = JSONObject(file.readText())
        val out = LinkedHashMap<String, Map<String, String>>(root.length())
        for (key in root.keys()) {
            val byLine = root.optJSONObject(key) ?: continue
            val lines = LinkedHashMap<String, String>(byLine.length())
            for (lineKey in byLine.keys()) {
                val value = byLine.optString(lineKey, "")
                if (value.isNotEmpty()) lines[lineKey] = value
            }
            out[key] = lines
        }
        return out
    }

    private suspend fun persistCache() {
        val file = cacheFile ?: return
        val snapshot = synchronized(cache) {
            trimCacheLocked()
            LinkedHashMap(cache)
        }
        runCatching {
            val root = JSONObject()
            for ((requestKey, byLine) in snapshot) {
                root.put(requestKey, JSONObject(byLine as Map<*, *>))
            }
            val tmp = File(file.parentFile, "$CacheFileName.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }.onFailure { Timber.tag(TAG).w(it, "failed to persist romanisation cache") }
    }

    private fun schedulePersist() {
        if (cacheFile == null) return
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SaveDebounceMs)
            persistCache()
        }
    }

    @Composable
    fun rememberSettings(): Settings {
        val (enabled) = rememberPreference(AiRomanizeLyricsKey, defaultValue = false)
        val (auto) = rememberPreference(AutoAiRomanizeLyricsKey, defaultValue = false)
        val (excluded) = rememberPreference(AiRomanizeExcludedLanguagesKey, defaultValue = emptySet())

        val provider by rememberEnumPreference(AiProviderKey, AiProvider.NONE)
        val (apiKey) = rememberPreference(AiApiKeyKey, defaultValue = "")
        val (customEndpoint) = rememberPreference(AiCustomEndpointKey, defaultValue = "")
        val (selectedModel) = rememberPreference(AiSelectedModelKey, defaultValue = "")
        val (customModel) = rememberPreference(AiCustomModelKey, defaultValue = "")

        val (separateProviderEnabled) = rememberPreference(AiRomanizeSeparateProviderKey, defaultValue = false)
        val romanizeProvider by rememberEnumPreference(AiRomanizeProviderKey, AiProvider.NONE)
        val (romanizeApiKey) = rememberPreference(AiRomanizeApiKeyKey, defaultValue = "")
        val (romanizeCustomEndpoint) = rememberPreference(AiRomanizeCustomEndpointKey, defaultValue = "")
        val (romanizeSelectedModel) = rememberPreference(AiRomanizeSelectedModelKey, defaultValue = "")
        val (romanizeCustomModel) = rememberPreference(AiRomanizeCustomModelKey, defaultValue = "")

        val useSeparate = separateProviderEnabled && romanizeProvider != AiProvider.NONE
        val effectiveProvider = if (useSeparate) romanizeProvider else provider
        val effectiveApiKey = if (useSeparate) romanizeApiKey else apiKey
        val effectiveCustomEndpoint = if (useSeparate) romanizeCustomEndpoint else customEndpoint
        val effectiveModel =
            if (useSeparate) {
                if (romanizeProvider == AiProvider.CUSTOM) romanizeCustomModel else romanizeSelectedModel
            } else {
                if (provider == AiProvider.CUSTOM) customModel else selectedModel
            }

        return remember(
            enabled,
            auto,
            excluded,
            effectiveProvider,
            effectiveApiKey,
            effectiveCustomEndpoint,
            effectiveModel,
        ) {
            Settings(
                enabled = enabled,
                auto = auto,
                excludedLanguages = excluded,
                config =
                    AiServiceConfig(
                        provider = effectiveProvider,
                        apiKey = effectiveApiKey,
                        customEndpoint = effectiveCustomEndpoint,
                        model = effectiveModel,
                    ),
            )
        }
    }

    fun sessionKey(
        mediaId: String?,
        lyrics: String?,
    ): String = "${mediaId.orEmpty()}|${lyrics?.length ?: 0}|${lyrics?.hashCode() ?: 0}"

    fun linesFor(
        sessionKey: String,
        lines: List<String>,
        settings: Settings,
    ): List<String?> {
        val byLine = synchronized(cache) { cache[fullKey(sessionKey, settings)] } ?: return emptyList()
        return lines.map { byLine[it.trim()] }
    }

    fun linesOf(
        lyrics: String?,
        durationSeconds: Int? = null,
    ): List<String> {
        val text = lyrics?.trim().orEmpty()
        if (text.isEmpty() || text == LyricsEntity.LYRICS_NOT_FOUND) return emptyList()
        return runCatching {
            when {
                LyricsUtils.isTtml(text) -> LyricsUtils.parseTtml(text, durationSeconds).map { it.text }
                LyricsUtils.isLineSyncedLrc(text) -> LyricsUtils.parseLyrics(text).map { it.text }
                else -> text.lines().filter { it.isNotBlank() }.map { it.trim() }
            }
        }.getOrDefault(emptyList())
    }

    fun request(
        sessionKey: String,
        lines: List<String>,
        settings: Settings,
        force: Boolean = false,
    ): RequestStatus {
        if (!settings.active) return RequestStatus.SETTINGS_DISABLED
        if (lines.isEmpty()) return RequestStatus.NO_LYRICS

        val requestKey = fullKey(sessionKey, settings)

        synchronized(cache) { cache[requestKey] }?.let { cached ->
            publish(requestKey, cached)
            return RequestStatus.ALREADY_CACHED
        }
        if (inFlight.containsKey(requestKey)) return RequestStatus.IN_FLIGHT

        val dominant = LyricsUtils.detectDominantLanguageCode(lines.joinToString("\n"))
        if (dominant != null && LyricsUtils.matchesExcludedLanguage(dominant, settings.excludedLanguages)) {
            Timber.tag(TAG).d("skipping %s: %s is excluded", sessionKey, dominant)
            return RequestStatus.EXCLUDED_LANGUAGE
        }

        if (!force && lines.none { LyricsUtils.hasRomanizableScript(it) }) return RequestStatus.NO_ROMANIZABLE_SCRIPT

        val job =
            scope.async {
                _running.value = true
                try {
                    romanizer.romanize(settings.config, lines)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Timber.tag(TAG).w(t, "AI romanisation failed for %s", sessionKey)
                    null
                } finally {
                    _running.value = false
                }
            }
        inFlight[requestKey] = job
        scope.async {
            val result = runCatching { job.await() }.getOrNull()
            inFlight.remove(requestKey)
            if (result == null) return@async

            val byLine = LinkedHashMap<String, String>(result.size)
            lines.forEachIndexed { index, line ->
                val romanized = result.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEachIndexed
                byLine.putIfAbsent(line.trim(), romanized)
            }
            synchronized(cache) {
                cache[requestKey] = byLine
                trimCacheLocked()
            }
            schedulePersist()
            if (byLine.isNotEmpty()) {
                publish(requestKey, byLine)
            } else {
                _requestOutcomes.tryEmit(RequestStatus.EMPTY_RESULT)
            }
        }
        return RequestStatus.STARTED
    }

    private fun fullKey(
        sessionKey: String,
        settings: Settings,
    ): String = "$sessionKey|${settings.configKey}"

    private fun publish(
        requestKey: String,
        byLine: Map<String, String>,
    ) {
        _results.value = Result(sessionKey = requestKey, byLine = byLine)
    }

    private fun trimCacheLocked() {
        while (cache.size > MaxCachedTracks) {
            val eldest = cache.keys.firstOrNull() ?: break
            cache.remove(eldest)
        }
    }
}
