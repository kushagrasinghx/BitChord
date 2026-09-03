package com.music.bitchord.data.sources

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.music.bitchord.BuildConfig
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.settings.AudioQuality
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import java.util.UUID

/**
 * One configured source: which protocol, which index.
 *
 * Stored in encrypted prefs — [baseUrl] is a module index the user called
 * "for my private use", not something to leave sitting in plain-text
 * SharedPreferences on a device someone else might get into.
 */
@Serializable
data class SourceConfig(
    val id: String = UUID.randomUUID().toString(),
    val kind: SourceKind,
    /** What the user called it. Blank falls back to the server's host, or the kind's own label. */
    val label: String = "",
    val baseUrl: String = "",
    val enabled: Boolean = true,
) {
    /** What the sources screen and the player show. Never blank. */
    val displayName: String
        get() = label.ifBlank {
            baseUrl.takeIf { it.isNotBlank() }
                ?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                ?: kind.label
        }

    /** Whether this has enough filled in to be worth contacting at all. */
    val isComplete: Boolean
        get() = !kind.needsServer || baseUrl.isNotBlank()
}

/**
 * The user's sources, always tried in a fixed order: the module source
 * first, YouTube Music second.
 *
 * [SourceKind.YOUTUBE] is seeded on first run and cannot be deleted, only
 * disabled — it needs no configuration, so a "remove" would delete something
 * the user could not then re-create by typing anything in, it would just be a
 * switch that hides itself. The module source is entirely optional: with none
 * configured, YouTube is all there is.
 */
object SourceRegistry {

    private const val TAG = "BitChord"

    private lateinit var prefs: SharedPreferences

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Every configured source, enabled or not. */
    val configs = MutableStateFlow<List<SourceConfig>>(emptyList())

    /**
     * Built instances, keyed by config id, rebuilt whenever [configs] changes.
     *
     * Held rather than constructed per call so that a source with any warmed
     * state — a module whose index has already been fetched — keeps it across
     * tracks instead of re-probing on every resolve.
     */
    private var instances: Map<String, MusicSource> = emptyMap()

    fun init(context: Context) {
        prefs = runCatching {
            EncryptedSharedPreferences.create(
                context,
                "bitchord_sources",
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse {
            // Same degradation as AuthStore: a handful of OEM builds cannot
            // init the keystore, and refusing to run at all is worse than
            // storing this the way every other setting in the app is stored.
            TrackLog.w(TAG, "EncryptedSharedPreferences unavailable for sources: ${it.message}")
            context.getSharedPreferences("bitchord_sources_plain", Context.MODE_PRIVATE)
        }

        val stored = prefs.getString(KEY_SOURCES, null)?.let(::decodeStored) ?: emptyList()

        // Seeded rather than persisted-on-first-write, so that a build that
        // adds a new built-in kind picks it up for existing installs too.
        val seeded = stored + BUILT_IN_KINDS
            .filter { kind -> stored.none { it.kind == kind } }
            .map { SourceConfig(kind = it, enabled = true) }

        // If a module index URL was baked in at build time, ensure it is the
        // one stored — add the module source if missing, or silently update its
        // URL if it changed. The toggle’s enabled state is always preserved so
        // the user’s on/off choice survives an app update.
        val envUrl = BuildConfig.MODULE_INDEX_URL.trim()
        val withModule = if (envUrl.isNotEmpty()) {
            val existingModule = seeded.firstOrNull { it.kind == SourceKind.MODULE }
            if (existingModule == null) {
                seeded + SourceConfig(
                    kind = SourceKind.MODULE,
                    label = ENV_MODULE_LABEL,
                    baseUrl = envUrl,
                    enabled = true,
                )
            } else if (existingModule.baseUrl != envUrl || existingModule.label.isBlank()) {
                // The label is filled in as well as the URL, so the env-managed
                // module is named rather than showing the bare index host —
                // which is what [SourceConfig.displayName] falls back to.
                seeded.map {
                    if (it.kind == SourceKind.MODULE) {
                        it.copy(baseUrl = envUrl, label = it.label.ifBlank { ENV_MODULE_LABEL })
                    } else {
                        it
                    }
                }
            } else {
                seeded
            }
        } else {
            // No env URL: keep whatever the user had stored, but ensure there
            // is no leftover env-managed module config lying around from a
            // previous build that did have one.
            seeded
        }

        // YouTube is not switchable — see [setEnabled] — so a config persisted
        // as disabled by an earlier build would strand the app with no source
        // it is allowed to turn back on.
        val after = withModule.map {
            if (it.kind == SourceKind.YOUTUBE && !it.enabled) it.copy(enabled = true) else it
        }

        publish(after, persist = after != stored)
    }

    /**
     * Decodes a stored source list one entry at a time rather than as a
     * single list, so one entry naming a kind this build no longer has —
     * left over from before a kind was retired — doesn't take every other
     * entry down with it. A strict `List<SourceConfig>` decode fails whole:
     * one bad enum value and the user's real, working module config is
     * silently gone along with it.
     */
    private fun decodeStored(raw: String): List<SourceConfig> {
        val elements = runCatching { json.parseToJsonElement(raw).jsonArray }
            .getOrElse { return emptyList() }
        return elements.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement(SourceConfig.serializer(), element) }
                .onFailure { TrackLog.w(TAG, "dropping unreadable stored source: ${it.message}") }
                .getOrNull()
        }
    }

    /** The enabled sources, module first and YouTube last, however they're stored. */
    fun active(): List<MusicSource> =
        configs.value
            .filter { it.enabled && it.isComplete }
            .sortedBy { it.kind.ordinal }
            .mapNotNull { instances[it.id] }

    fun instance(configId: String): MusicSource? = instances[configId]

    fun config(configId: String): SourceConfig? = configs.value.firstOrNull { it.id == configId }

    // ── Editing ─────────────────────────────────────────────────────────

    fun add(config: SourceConfig) = publish(configs.value + config)

    fun update(config: SourceConfig) =
        publish(configs.value.map { if (it.id == config.id) config else it })

    fun remove(configId: String) {
        val target = config(configId) ?: return
        if (target.kind in BUILT_IN_KINDS) return
        publish(configs.value.filterNot { it.id == configId })
    }

    /**
     * Turns one source on or off.
     *
     * YouTube is not switchable and silently ignores a request to disable it.
     * It is the only source that can supply a home feed, radio or related
     * tracks, and nothing else holds the full catalogue — switching it off
     * doesn't even stop it being played, because a YouTube-queued track whose
     * substitutes all miss still falls back to it. A switch that cannot honour
     * its own off position is worse than no switch, so it isn't offered one:
     * see [SourcesScreen][com.music.bitchord.ui.screens.SourcesScreen].
     */
    fun setEnabled(configId: String, enabled: Boolean) {
        if (!enabled && config(configId)?.kind == SourceKind.YOUTUBE) return
        publish(configs.value.map { if (it.id == configId) it.copy(enabled = enabled) else it })
    }

    /** Toggle the MODULE source ("Ricky's Addon") on or off by its config id. */
    fun setModuleEnabled(enabled: Boolean) {
        val module = configs.value.firstOrNull { it.kind == SourceKind.MODULE } ?: return
        setEnabled(module.id, enabled)
    }

    /** Toggle the JioSaavn source on or off by its config id. */
    fun setJioSaavnEnabled(enabled: Boolean) {
        val jioSaavn = configs.value.firstOrNull { it.kind == SourceKind.JIOSAAVN } ?: return
        setEnabled(jioSaavn.id, enabled)
    }

    /**
     * Puts the module and JioSaavn switches where the chosen [AudioQuality]
     * rung says they belong — see the rung-by-rung breakdown on
     * [AudioQuality] itself. Called wherever the user picks a rung on the
     * Settings screen, so the Sources screen always reads back whichever
     * quality is currently in force rather than a stale manual toggle.
     */
    fun applyQualityPreset(quality: AudioQuality) {
        when (quality) {
            AudioQuality.LOSSLESS -> {
                setModuleEnabled(true)
                setJioSaavnEnabled(true)
            }
            AudioQuality.HIGH -> {
                setModuleEnabled(false)
                setJioSaavnEnabled(true)
            }
            AudioQuality.MEDIUM, AudioQuality.LOW -> {
                setModuleEnabled(false)
                setJioSaavnEnabled(false)
            }
        }
    }

    /** The user's own module index, if they have set one. */
    fun customModule(): SourceConfig? =
        configs.value.firstOrNull { it.kind == SourceKind.CUSTOM_MODULE }

    /**
     * Points the custom module at [url], replacing whatever was there.
     *
     * Only ever one: a second index would be a second full search on every
     * track for a feature whose whole purpose is "use mine instead", and the
     * order between two of them would be arbitrary. So this replaces rather
     * than appends, and a blank [url] clears it.
     *
     * The replacement is a *new* [SourceConfig] rather than an edit of the old
     * one, so [publish] sees a different id and drops the warm [ModuleSource]
     * built against the previous index — see [configuredBy].
     */
    fun setCustomModule(url: String, label: String = "") {
        val trimmed = url.trim()
        val without = configs.value.filterNot { it.kind == SourceKind.CUSTOM_MODULE }
        if (trimmed.isEmpty()) {
            publish(without)
            return
        }
        publish(
            without + SourceConfig(
                kind = SourceKind.CUSTOM_MODULE,
                label = label.trim(),
                baseUrl = trimmed,
                enabled = true,
            ),
        )
    }

    private fun publish(next: List<SourceConfig>, persist: Boolean = true) {
        configs.value = next
        // Rebuilt against the previous map so that an untouched source keeps
        // the instance it already had, rather than being replaced by an
        // identical-but-cold one every time an unrelated row is toggled.
        val previous = instances
        instances = next.associate { config ->
            val existing = previous[config.id]?.takeIf { it.configuredBy(config) }
            config.id to (existing ?: build(config))
        }
        if (persist && ::prefs.isInitialized) {
            prefs.edit()
                .putString(KEY_SOURCES, json.encodeToString(ListSerializer(SourceConfig.serializer()), next))
                .apply()
        }
    }

    /**
     * Health-checks a config that hasn't been saved — what the editor's Test
     * button asks.
     *
     * Built fresh and thrown away rather than routed through [instances],
     * which hold the *stored* config: testing one of those would report on the
     * old address, which is precisely the state the user is in the middle of
     * correcting.
     */
    suspend fun probeCandidate(config: SourceConfig): SourceHealth = build(config).health()

    private fun build(config: SourceConfig): MusicSource = when (config.kind) {
        // Same protocol, same implementation — the kinds differ only in rank.
        SourceKind.CUSTOM_MODULE -> ModuleSource(config)
        SourceKind.MODULE -> ModuleSource(config)
        SourceKind.JIOSAAVN -> JioSaavnSource(config)
        SourceKind.YOUTUBE -> YouTubeSource(config)
    }

    /**
     * Whether an already-built instance still matches its stored config —
     * false after an edit that changes where it points, which is exactly when
     * the warm instance must be thrown away.
     */
    private fun MusicSource.configuredBy(config: SourceConfig): Boolean =
        this is ConfigBacked && this.config == config

    /** Implemented by sources that carry their [SourceConfig], so [publish] can tell a real edit from a no-op. */
    internal interface ConfigBacked {
        val config: SourceConfig
    }

    // ── Track identity ──────────────────────────────────────────────────

    /**
     * A source-backed track's id, as it travels through the queue.
     *
     * Packed into the existing [Song.videoId][com.music.bitchord.data.model.Song.videoId]
     * rather than added beside it: that field is the app's media id everywhere —
     * the queue, the notification, the history, the like state — and a second
     * identity field would have to be threaded through every one of them, with
     * each place that forgot silently falling back to treating the track as
     * YouTube's.
     */
    fun trackKey(configId: String, trackId: String) = "$PREFIX$configId$SEPARATOR$trackId"

    /** The `(configId, trackId)` inside a [trackKey], or null if this is an ordinary YouTube id. */
    fun parseTrackKey(key: String): Pair<String, String>? {
        if (!key.startsWith(PREFIX)) return null
        val body = key.removePrefix(PREFIX)
        val cut = body.indexOf(SEPARATOR)
        if (cut <= 0) return null
        return body.substring(0, cut) to body.substring(cut + SEPARATOR.length)
    }

    /** The playback URI for a source-backed track; [PlaybackService] resolves it at open time. */
    fun trackUri(configId: String, trackId: String): String =
        Uri.Builder()
            .scheme("bitchord")
            .authority("source")
            .appendQueryParameter("s", configId)
            .appendQueryParameter("t", trackId)
            .build()
            .toString()

    private val BUILT_IN_KINDS = listOf(SourceKind.JIOSAAVN, SourceKind.YOUTUBE)

    /** What the build-time module index is called on screen, in place of its host. */
    private const val ENV_MODULE_LABEL = "Ricky's Addon"

    private const val KEY_SOURCES = "sources"
    private const val PREFIX = "src:"
    private const val SEPARATOR = "::"
}
