package com.music.bitchord.data.sources

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.music.bitchord.auth.EncryptedPrefs
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.sources.addon.AddonClient
import com.music.bitchord.data.sources.addon.AddonException
import com.music.bitchord.data.sources.addon.DetectedFormat
import com.music.bitchord.data.sources.addon.SourceFormats
import com.music.bitchord.data.settings.AudioQuality
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
    /** The catalogue is opt-in because catalogue matches can select the wrong recording. */
    val enabled: Boolean = kind != SourceKind.CATALOGUE,
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
 * The user's sources, always tried in the fixed order [SourceKind] declares:
 * their own addons first, then YouTube Music.
 *
 * [SourceKind.YOUTUBE] is seeded on first run and cannot be deleted, only
 * disabled — it needs no configuration, so a "remove" would delete something
 * the user could not then re-create by typing anything in, it would just be a
 * switch that hides itself. Addons are entirely optional: with none
 * configured, YouTube is the only active source on a fresh install. The
 * catalogue source is present but off until the user opts in.
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
        // Same repair and degradation as AuthStore; see EncryptedPrefs.
        prefs = EncryptedPrefs.open(context, "bitchord_sources", "bitchord_sources_plain")

        val stored = prefs.getString(KEY_SOURCES, null)?.let(::decodeStored) ?: emptyList()
        val catalogueOptInMigrationDone = prefs.getBoolean(KEY_CATALOGUE_OPT_IN_V1, false)
        val after = sourcesForInit(stored, forceCatalogueOff = !catalogueOptInMigrationDone)

        // Publish state and the migration marker in one preferences edit. If a
        // process dies after recording the marker but before recording the
        // disabled source, the next launch would otherwise believe the forced
        // opt-out had already happened and silently restore the old on state.
        publish(after, persist = false)
        if (after != stored || !catalogueOptInMigrationDone) {
            prefs.edit()
                .putString(KEY_SOURCES, json.encodeToString(ListSerializer(SourceConfig.serializer()), after))
                .putBoolean(KEY_CATALOGUE_OPT_IN_V1, true)
                .apply()
        }
    }

    /**
     * Built-in seeding and one-time source migrations, kept pure for tests.
     *
     * [forceCatalogueOff] is true exactly once for every install that first runs
     * this version, including upgrades whose stored config currently says on.
     * Once the marker is written, a user who deliberately enables the catalogue
     * is left enabled on subsequent launches.
     */
    internal fun sourcesForInit(
        stored: List<SourceConfig>,
        forceCatalogueOff: Boolean,
    ): List<SourceConfig> {
        // Seeded rather than persisted-on-first-write, so that a build that
        // adds a new built-in kind picks it up for existing installs too.
        // SourceConfig's default is the policy: catalogue off, YouTube on.
        val seeded = stored + BUILT_IN_KINDS
            .filter { kind -> stored.none { it.kind == kind } }
            .map { SourceConfig(kind = it) }

        // The retired built-in module is removed, while a custom module entered
        // by the user is preserved.
        return seeded
            .filterNot { it.kind == SourceKind.MODULE }
            .map { config ->
                when {
                    config.kind == SourceKind.YOUTUBE && !config.enabled -> config.copy(enabled = true)
                    config.kind == SourceKind.CATALOGUE && forceCatalogueOff -> config.copy(enabled = false)
                    else -> config
                }
            }
    }

    /**
     * Decodes a stored source list one entry at a time rather than as a
     * single list, so one entry naming a kind this build no longer has —
     * left over from before a kind was retired — doesn't take every other
     * entry down with it. A strict `List<SourceConfig>` decode fails whole:
     * one bad enum value and the user's real, working module config is
     * silently gone along with it.
     *
     * Entries stored under the retired `JIOSAAVN` kind name are migrated to
     * [SourceKind.CATALOGUE], which is the same source renamed.
     */
    private fun decodeStored(raw: String): List<SourceConfig> {
        val elements = runCatching { json.parseToJsonElement(raw).jsonArray }
            .getOrElse { return emptyList() }
        return elements.mapNotNull { element ->
            val migrated = (element as? kotlinx.serialization.json.JsonObject)
                ?.let { obj ->
                    if ((obj["kind"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "JIOSAAVN") {
                        kotlinx.serialization.json.JsonObject(
                            obj.filterKeys { it != "kind" } +
                                ("kind" to kotlinx.serialization.json.JsonPrimitive("CATALOGUE")),
                        )
                    } else {
                        obj
                    }
                } ?: element
            runCatching { json.decodeFromJsonElement(SourceConfig.serializer(), migrated) }
                .onFailure { TrackLog.w(TAG, "dropping unreadable stored source: ${it.message}") }
                .getOrNull()
        }
    }

    /**
     * The enabled sources, module first and YouTube last, however they're stored.
     *
     * The user's standing choice and nothing else. Both playback and downloads
     * start here, so disabling the catalogue or an addon excludes it from both. A
     * stream is budgeted further by [activeForPlayback]; downloads deliberately
     * apply no connection-quality ceiling — see [SourceResolver.forDownload].
     */
    fun active(): List<MusicSource> =
        enabledConfigs(configs.value)
            .sortedBy { it.kind.rank }
            .mapNotNull { instances[it.id] }

    /** The common eligibility gate used by playback and download source walks. */
    internal fun enabledConfigs(configs: List<SourceConfig>): List<SourceConfig> =
        configs.filter { it.enabled && it.isComplete }

    /**
     * [active], minus the sources the ceiling on the connection in hand does
     * not pay for — see [AudioQuality.permits].
     *
     * Every path that starts or plans a *stream* asks this rather than
     * [active], so the Wi-Fi and mobile-data rungs stay two independent
     * answers to "what does this minute cost" and switching networks switches
     * between them. Downloads deliberately keep asking [active]: what a saved
     * file is worth is [DownloadQuality][com.music.bitchord.data.settings.DownloadQuality]'s
     * question, and when it may be fetched is
     * [AppSettings.wifiOnlyDownloads][com.music.bitchord.data.settings.AppSettings.wifiOnlyDownloads]'s.
     */
    fun activeForPlayback(): List<MusicSource> {
        val ceiling = AppSettings.effectiveAudioQuality
        return active().filter { ceiling.permits(it.kind) }
    }

    fun instance(configId: String): MusicSource? = instances[configId]

    fun config(configId: String): SourceConfig? = configs.value.firstOrNull { it.id == configId }

    /**
     * Drops completed addon track answers for an explicit quality retry.
     *
     * Empty responses are normally legitimate and cached, but an addon whose
     * upstream was temporarily down can express that outage as HTTP 200 with
     * no rows. The listener pressing "Upgrade quality" is an explicit request
     * to ask again now, not to repeat that cached answer or a previously issued
     * stream URL. In-flight calls stay shared so a quick second press cannot
     * duplicate work already on the wire.
     */
    fun clearCompletedAddonTrackCalls() {
        instances.values
            .filterIsInstance<AddonSource>()
            .forEach(AddonSource::clearCompletedTrackCalls)
    }

    // ── Editing ─────────────────────────────────────────────────────────

    fun add(config: SourceConfig) = publish(configs.value + config.tidied())

    fun update(config: SourceConfig) =
        config.tidied().let { tidied ->
            publish(configs.value.map { if (it.id == tidied.id) tidied else it })
        }

    /**
     * The config as it should be stored, rather than as it was typed.
     *
     * Only addons have anything to tidy, and only their URL: the two forms
     * people paste — the addon's root and its `manifest.json` — address the
     * same server, and storing them as typed makes two configs that behave
     * identically look different on screen and compare unequal in
     * [configuredBy], which would rebuild a warm source over a cosmetic edit.
     * Normalising here rather than in the editor keeps it true for every caller
     * and not just the one with a text field.
     */
    private fun SourceConfig.tidied(): SourceConfig =
        if (kind == SourceKind.ADDON) copy(baseUrl = AddonClient.normalizeBase(baseUrl)) else this

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

    /**
     * Puts the addons in [orderedIds], which is the order they will be asked in.
     *
     * The stored list *is* the priority order and needs no rank field to carry
     * it: [active] sorts by [SourceKind.ordinal] and Kotlin's sort is stable,
     * so two sources of the same kind keep the order they are held in here.
     * Writing a rank alongside would be a second source of truth for a fact the
     * list already states, and the two would drift the first time one was
     * written without the other.
     *
     * Ids that name nothing are dropped and addons the caller forgot to mention
     * are appended, so a list that has moved on since the drag started — an
     * addon removed on another screen, say — reorders what it can rather than
     * deleting the rest. Everything that is not an addon keeps its place, since
     * its rank is decided by its kind and is not the user's to set.
     */
    fun reorderAddons(orderedIds: List<String>) {
        val addons = configs.value.filter { it.kind.isUserAdded }
        if (addons.size < 2) return
        val byId = addons.associateBy { it.id }
        val moved = orderedIds.mapNotNull(byId::get)
        val missed = addons.filterNot { config -> moved.any { it.id == config.id } }
        val reordered = moved + missed
        if (reordered.map { it.id } == addons.map { it.id }) return
        publish(reordered + configs.value.filterNot { it.kind.isUserAdded })
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
        // Whatever the rebuild above left behind, told so. An addon that was
        // edited or removed is holding a manifest, a set of search answers and
        // a handful of stream URLs that all describe the server it used to
        // point at, and its client's own scope keeps them alive whether or not
        // anything still references the source. Serving one of those afterwards
        // would be answering a question about the old address with the new one
        // selected.
        previous.values.filterNot { it in instances.values }
            .filterIsInstance<AddonSource>()
            .forEach { it.release() }
        if (persist && ::prefs.isInitialized) {
            prefs.edit()
                .putString(KEY_SOURCES, json.encodeToString(ListSerializer(SourceConfig.serializer()), next))
                .apply()
        }
    }

    /**
     * Works out what is at [url] and returns a config that speaks to it, or a
     * refusal saying what was found instead.
     *
     * This is what makes the editor accept "any JSON" rather than one shape.
     * The kind is *detected* rather than chosen up front: pasting an addon's
     * root, an addon's `manifest.json`, or a module index all end here, and
     * which [MusicSource] gets built is decided by what the server actually
     * returned. Anything recognised but unplayable — an extension registry, a
     * manifest that cannot stream — comes back as a failure carrying a line
     * written for the person reading it.
     *
     * [existing] is carried through so editing a saved source keeps its id and
     * its on/off state; a new source gets a fresh config.
     */
    suspend fun identify(url: String, existing: SourceConfig? = null): Result<SourceConfig> {
        val detected = SourceFormats.identify(url).getOrElse { return Result.failure(it) }
        return when (detected) {
            is DetectedFormat.Addon -> Result.success(
                (existing ?: SourceConfig(kind = SourceKind.ADDON)).copy(
                    kind = SourceKind.ADDON,
                    baseUrl = detected.baseUrl,
                    label = detected.manifest.displayName,
                ),
            )
            is DetectedFormat.ModuleIndex -> Result.success(
                (existing ?: SourceConfig(kind = SourceKind.CUSTOM_MODULE)).copy(
                    kind = SourceKind.CUSTOM_MODULE,
                    // The index URL itself, not a base: a module index is the
                    // document, where an addon's manifest only points at one.
                    baseUrl = detected.url,
                    label = existing?.label.orEmpty(),
                ),
            )
            is DetectedFormat.Unsupported -> Result.failure(AddonException(detected.reason))
        }
    }

    /**
     * The already-configured source pointing at [url], if there is one.
     *
     * Compared after [identify] has run rather than on the raw text, which is
     * what makes this catch the cases worth catching: an addon's root and its
     * `manifest.json` are the same server typed two ways, and both normalise to
     * one base before they reach here. A trailing slash, a `MANIFEST.JSON`, and
     * a host in a different case are all the same source too.
     *
     * [exceptId] is the source being edited, which is not its own duplicate —
     * without it, saving an existing addon without touching its URL would
     * refuse itself.
     */
    fun duplicateOf(url: String, exceptId: String? = null): SourceConfig? {
        val wanted = canonicalUrl(url)
        if (wanted.isEmpty()) return null
        return configs.value.firstOrNull { it.id != exceptId && canonicalUrl(it.baseUrl) == wanted }
    }

    /**
     * A URL reduced to the form two spellings of the same address share.
     *
     * Scheme and host are lowercased because they are case-insensitive; the
     * path deliberately is not, because on this protocol the path can carry a
     * user's token and two tokens differing only in case are two different
     * credentials. Falls back to the trimmed text when the URL will not parse,
     * so a malformed entry still compares equal to itself.
     */
    private fun canonicalUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        val parsed = trimmed.toHttpUrlOrNull() ?: return trimmed
        val path = parsed.encodedPath.trimEnd('/')
        val query = parsed.encodedQuery?.let { "?$it" }.orEmpty()
        return "${parsed.scheme}://${parsed.host}${if (parsed.port != defaultPort(parsed.scheme)) ":${parsed.port}" else ""}$path$query"
    }

    private fun defaultPort(scheme: String) = if (scheme == "https") 443 else 80

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
        SourceKind.ADDON -> AddonSource(config)
        // Same protocol, same implementation — the kinds differ only in rank.
        SourceKind.CUSTOM_MODULE -> ModuleSource(config)
        SourceKind.MODULE -> ModuleSource(config)
        SourceKind.CATALOGUE -> CatalogueSource(config)
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

    /**
     * Kinds the sources screen may not delete. The catalogue is seeded like
     * YouTube but off by default; an install that holds one could not
     * re-create it by typing anything in, so a "remove" would just be a
     * switch that hides itself — the same reason YouTube is listed.
     */
    private val BUILT_IN_KINDS = listOf(SourceKind.CATALOGUE, SourceKind.YOUTUBE)

    private const val KEY_SOURCES = "sources"
    /** One-shot migration: existing users must explicitly opt in again. */
    private const val KEY_CATALOGUE_OPT_IN_V1 = "catalogue_opt_in_v1"
    private const val PREFIX = "src:"
    private const val SEPARATOR = "::"
}
