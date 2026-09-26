package com.music.bitchord.desktop

import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

/** What playback got to start with, and what is still being looked for. */
internal class DesktopLiveResolution(
    val stream: DesktopStream,
    /**
     * The substitution search that had not finished when sound started, or null when nothing is
     * outstanding.
     */
    val pendingSubstitute: Deferred<DesktopStream?>? = null,
)

internal data class DesktopSourceDescriptor(
    val id: String,
    val name: String,
    val detail: String,
    val kind: DesktopSourceKind,
    val configured: Boolean,
    val enabled: Boolean,
    val supportsLossless: Boolean,
    /** Enabled, but the ceiling in force will not let it answer a stream. */
    val skippedByQuality: Boolean = false,
)

/** Desktop counterpart of Android's SourceRegistry. */
internal object DesktopSourceRegistry {
    private interface SourceAdapter {
        val config: DesktopSourceConfig
        val descriptor: DesktopSourceDescriptor
        suspend fun resolve(song: Song, quality: String?): Result<DesktopStream?>
        /** Every copy this source holds of [song], most confident first. */
        suspend fun matches(song: Song): List<Song> = emptyList()

        suspend fun match(song: Song): Song? = matches(song).firstOrNull()
        fun owns(song: Song): Boolean = false
    }

    private class AddonAdapter(
        override val config: DesktopSourceConfig,
    ) : SourceAdapter {
        override val descriptor: DesktopSourceDescriptor
            get() = config.descriptor()

        override fun owns(song: Song): Boolean =
            DesktopAddonSource.parseTrack(song.videoId)?.sourceId == config.id

        override suspend fun resolve(song: Song, quality: String?): Result<DesktopStream?> =
            DesktopAddonSource.stream(config, song, quality)

        override suspend fun matches(song: Song): List<Song> = DesktopAddonSource.matches(config, song)
    }

    private class ModuleAdapter(
        override val config: DesktopSourceConfig,
    ) : SourceAdapter {
        override val descriptor: DesktopSourceDescriptor
            get() = config.descriptor()

        override fun owns(song: Song): Boolean =
            DesktopModuleSource.parseTrack(song.videoId)?.let { ref ->
                ref.sourceId == null || ref.sourceId == config.id
            } == true

        override suspend fun resolve(song: Song, quality: String?): Result<DesktopStream?> =
            DesktopModuleSource.stream(config, song, quality)

        override suspend fun matches(song: Song): List<Song> = DesktopModuleSource.matches(config, song)
    }

    private class JioAdapter(
        override val config: DesktopSourceConfig,
    ) : SourceAdapter {
        override val descriptor: DesktopSourceDescriptor
            get() = config.descriptor()

        override fun owns(song: Song): Boolean = song.videoId.startsWith("jiosaavn:")

        override suspend fun resolve(song: Song, quality: String?): Result<DesktopStream?> =
            DesktopJioSaavn.stream(song.videoId)

        override suspend fun matches(song: Song): List<Song> = DesktopJioSaavn.matches(song)
    }

    private class YouTubeAdapter(
        override val config: DesktopSourceConfig,
    ) : SourceAdapter {
        override val descriptor: DesktopSourceDescriptor
            get() = config.descriptor()

        override suspend fun resolve(song: Song, quality: String?): Result<DesktopStream?> =
            DesktopStreamClient.resolve(song, youtubeCeiling(quality)).map { it.copy(sourceId = config.id) }

        /** Allows an unavailable module/Jio stream to fall back by identity. */
        override suspend fun matches(song: Song): List<Song> {
            if (song.title.isBlank() || song.isVideo) return emptyList()
            for (query in DesktopTrackMatcher.queries(song)) {
                val candidates = DesktopSearchClient.search(query, SearchFilter.SONGS)
                    .getOrDefault(emptyList())
                    .mapNotNull { (it as? SearchResult.Track)?.song }
                DesktopTrackMatcher.ranked(candidates, song).ifEmpty { null }?.let { return it }
            }
            return emptyList()
        }
    }

    private fun DesktopSourceConfig.descriptor() = DesktopSourceDescriptor(
        id = id,
        name = displayName,
        detail = if (kind.needsServer && baseUrl.isBlank()) "Setup required" else kind.detail,
        kind = kind,
        configured = isComplete,
        enabled = enabled,
        supportsLossless = kind.supportsLossless,
        skippedByQuality = enabled && !DesktopPersistence().audioQuality().permits(kind),
    )

    private fun configs(): List<DesktopSourceConfig> = DesktopPersistence().sourceConfigs()

    /**
     * The sources a *stream* may come from: enabled, complete, and permitted by the ceiling in
     * force.
     */
    private fun playbackAdapters(quality: String?): List<SourceAdapter> {
        val ceiling = ceiling(quality)
        return adapters().filter { ceiling.permits(it.config.kind) }
    }

    private fun adapters(): List<SourceAdapter> =
        configs()
            .inSourceOrder()
            .filter { it.enabled && it.isComplete }
            .map { config ->
                when (config.kind) {
                    DesktopSourceKind.ADDON -> AddonAdapter(config)
                    DesktopSourceKind.CUSTOM_MODULE,
                    DesktopSourceKind.MODULE,
                    -> ModuleAdapter(config)
                    DesktopSourceKind.JIOSAAVN -> JioAdapter(config)
                    DesktopSourceKind.YOUTUBE -> YouTubeAdapter(config)
                }
            }

    fun descriptors(): List<DesktopSourceDescriptor> = configs()
        .inSourceOrder()
        .map { it.descriptor() }

    fun isEnabled(sourceId: String): Boolean = configs().firstOrNull { it.id == sourceId }?.enabled == true

    /** Searching is YouTube Music's job, and only YouTube Music's. */
    suspend fun search(query: String, filter: SearchFilter): Result<List<SearchResult>> =
        DesktopSearchClient.search(query, filter)

    suspend fun resolve(
        song: Song,
        quality: String?,
        excludedSourceId: String? = null,
    ): Result<DesktopStream> = runCatching {
        val available = playbackAdapters(quality)
        val moduleReference = DesktopModuleSource.parseTrack(song.videoId)
        val addonReference = DesktopAddonSource.parseTrack(song.videoId)
        val owned = when {
            addonReference != null -> available.firstOrNull { it.config.id == addonReference.sourceId }
            moduleReference?.sourceId != null -> available.firstOrNull { it.config.id == moduleReference.sourceId }
            moduleReference != null -> available.firstOrNull {
                it.config.kind == DesktopSourceKind.CUSTOM_MODULE || it.config.kind == DesktopSourceKind.MODULE
            }
            song.videoId.startsWith("jiosaavn:") -> available.firstOrNull { it.config.kind == DesktopSourceKind.JIOSAAVN }
            else -> null
        }

        if (addonReference != null || moduleReference != null || song.videoId.startsWith("jiosaavn:")) {
            if (owned == null) error("The source for this track is disabled or no longer configured")
            // This path has nothing to race.
            DesktopTrackLog.log(
                "resolving '${song.title}' — this row belongs to ${owned.descriptor.name}" +
                    ", asking at the ${(quality ?: DesktopPersistence().audioQuality().name).lowercase()} rung",
            )
            if (owned.config.id != excludedSourceId) {
                owned.resolve(song, quality).getOrNull()
                    ?.takeIf { playableHere(owned, it) }
                    ?.let {
                        DesktopTrackLog.log(
                            "  ${owned.descriptor.name} served it at " +
                                it.format.summary.ifBlank { "an unstated format" },
                        )
                        return@runCatching it
                    }
                DesktopTrackLog.log("  ${owned.descriptor.name} had no usable stream for it")
            }
            // Android walks on to other active sources when the pinned source cannot produce a
            // stream.
            for (fallback in available.filter { it !== owned && it.config.id != excludedSourceId }) {
                val matched = fallback.match(song) ?: continue
                fallback.resolve(matched, quality).getOrNull()
                    ?.takeIf { playableHere(fallback, it) }
                    ?.let {
                        DesktopTrackLog.log(
                            "  fell through to ${fallback.descriptor.name} at " +
                                it.format.summary.ifBlank { "an unstated format" },
                        )
                        return@runCatching it
                    }
            }
            error("${owned.descriptor.name} did not return an audio stream")
        }

        // A normal YouTube row is offered to every higher-priority source first so a configured
        // FLAC/module or JioSaavn copy is used when available.
        for (source in available.filter {
            it.config.kind != DesktopSourceKind.YOUTUBE &&
                it.config.id != excludedSourceId &&
                !DesktopOriginalVersion.isPinned(song.videoId)
        }) {
            val matched = source.match(song) ?: continue
            source.resolve(matched, quality).getOrNull()
                ?.takeIf { playableHere(source, it) }
                ?.let { return@runCatching it }
        }
        val youtube = available.firstOrNull {
            it.config.kind == DesktopSourceKind.YOUTUBE && it.config.id != excludedSourceId
        }
            // YouTube is the non-removable fallback and is force-enabled on read.
            ?: error(
                if (excludedSourceId != null) {
                    "No other source has a copy of this track"
                } else {
                    "No enabled music source is configured"
                },
            )
        youtube.resolve(song, quality).fold(
            onSuccess = { it ?: error("${youtube.descriptor.name} did not return an audio stream") },
            onFailure = { throw it },
        )
    }

    /**
     * Resolves a track for immediate playback, racing the sources ranked above YouTube against
     * YouTube itself.
     */
    suspend fun resolveLive(
        song: Song,
        quality: String?,
        excludedSourceId: String? = null,
    ): Result<DesktopLiveResolution> = runCatching {
        val available = playbackAdapters(quality)
        val owned = available.any { it.owns(song) } ||
            DesktopModuleSource.parseTrack(song.videoId) != null ||
            song.videoId.startsWith("jiosaavn:")
        if (owned) {
            return@runCatching DesktopLiveResolution(resolve(song, quality, excludedSourceId).getOrThrow())
        }

        val youtube = available.firstOrNull {
            it.config.kind == DesktopSourceKind.YOUTUBE && it.config.id != excludedSourceId
        }
        val higher = if (DesktopOriginalVersion.isPinned(song.videoId)) {
            // Sent back to YouTube's own upload by hand.
            DesktopTrackLog.log("'${song.title}' is pinned to YouTube's original by the listener")
            emptyList()
        } else {
            available.filter {
                it.config.kind != DesktopSourceKind.YOUTUBE && it.config.id != excludedSourceId
            }
        }
        // Nothing outranks YouTube, so there is no race to run — this is the plain resolve.
        if (higher.isEmpty() || youtube == null) {
            return@runCatching DesktopLiveResolution(resolve(song, quality, excludedSourceId).getOrThrow())
        }

        // Both legs are parented to the registry's own scope rather than the caller's.
        DesktopTrackLog.log(
            "resolving '${song.title}' by '${song.artist}' — racing " +
                higher.joinToString { it.descriptor.name } + " against YouTube",
        )
        if (song.isVideo) {
            // A video upload is another recording, and can be another song altogether — the sources
            // are never asked to stand in for one.
            DesktopTrackLog.log("  this row is a video upload, so no source will be asked to match it")
        } else {
            DesktopTrackLog.log(
                "  asking for: " + DesktopTrackMatcher.queries(song).joinToString(" | ").ifBlank { "(nothing)" },
            )
        }
        val lookup = scope.async {
            withTimeoutOrNull(SUBSTITUTE_TIMEOUT_MS) { firstSubstitute(higher, song, quality) }
        }
        val fallback = scope.async { youtube.resolve(song, quality).getOrNull() }

        val quick: DesktopStream? = select {
            lookup.onAwait { it }
            // A YouTube leg that finished without a URL has not won anything.
            fallback.onAwait { stream -> if (stream != null) null else lookup.await() }
        }

        if (quick != null) {
            fallback.cancel()
            DesktopTrackLog.log(
                "substituted: '${song.title}' served by ${sourceNameFor(quick)} over YouTube" +
                    " at ${quick.format.summary.ifBlank { "an unstated format" }}",
            )
            // Winning the race is not the same as being the best copy.
            return@runCatching DesktopLiveResolution(quick, betterThan(quick, higher, song, quality))
        }

        val youtubeStream = fallback.await()
            ?: lookup.await()
            ?: error("No enabled music source could play this track")
        // Still running means it lost the race rather than ran out of answers.
        val pending = lookup.takeIf { it.isActive }
        if (pending != null) {
            DesktopTrackLog.log("'${song.title}' started on YouTube while the other sources keep looking")
        }
        DesktopLiveResolution(youtubeStream, pending)
    }

    /**
     * A patient second look for a copy that beats the one now playing, or null when nothing could.
     */
    private fun betterThan(
        playing: DesktopStream,
        candidates: List<SourceAdapter>,
        song: Song,
        quality: String?,
    ): Deferred<DesktopStream?>? {
        if (playing.format.isLossless) return null
        val winnerRank = configs().firstOrNull { it.id == playing.sourceId }?.kind?.rank
            ?: DesktopSourceKind.YOUTUBE.rank
        val better = candidates.filter { it.config.kind.rank < winnerRank }
        if (better.isEmpty()) return null
        DesktopTrackLog.log(
            "still looking for a better copy of '${song.title}' from " +
                better.joinToString { it.descriptor.name },
        )
        // The registry's own scope, like both legs of the race above: this outlives the call that
        // started it by design.
        return scope.async { withTimeoutOrNull(UPGRADE_TIMEOUT_MS) { firstSubstitute(better, song, quality) } }
    }

    /** The first usable stream from any of [sources], each asked in parallel. */
    private suspend fun firstSubstitute(
        sources: List<SourceAdapter>,
        song: Song,
        quality: String?,
    ): DesktopStream? = coroutineScope {
        val running = sources
            .map { source ->
                async { openBest(source, song, quality) }
            }
            .toMutableList()
        try {
            while (running.isNotEmpty()) {
                val done = select<Deferred<DesktopStream?>> {
                    running.forEach { candidate -> candidate.onAwait { candidate } }
                }
                running -= done
                done.await()?.let { return@coroutineScope it }
            }
            null
        } finally {
            running.forEach { it.cancel() }
        }
    }

    /** Whether a copy found after the fact is worth interrupting playback for. */
    internal fun worthSwapping(candidate: DesktopStreamFormat, playing: DesktopStreamFormat?): Boolean {
        if (candidate.isLossless) return true
        val gain = (candidate.kbps ?: return false) - (playing?.kbps ?: return false)
        return gain >= UPGRADE_MIN_GAIN_KBPS
    }

    /**
     * A second look, asked from scratch once the first one has settled for nothing better.
     *
     * The live race deliberately takes the first copy that beats YouTube, so a slower lossless
     * source can still be searching when a lossy one wins — and a source that answered HTTP 502
     * that minute may well answer properly now. Mirrors Android's `SourceResolver.upgradeFor`.
     */
    suspend fun upgradeFor(
        song: Song,
        playing: DesktopStream?,
        quality: String? = null,
    ): DesktopStream? {
        val playingRank = configs().firstOrNull { it.id == playing?.sourceId }?.kind?.rank
            ?: DesktopSourceKind.YOUTUBE.rank
        val better = playbackAdapters(quality).filter {
            it.config.kind != DesktopSourceKind.YOUTUBE && it.config.kind.rank < playingRank
        }
        if (better.isEmpty()) return null
        DesktopTrackLog.log(
            "asking ${better.joinToString { it.descriptor.name }} again for a better copy of " +
                "'${song.title}'",
        )
        return withTimeoutOrNull(UPGRADE_TIMEOUT_MS) { firstSubstitute(better, song, quality) }
    }

    /** Whether a stream a source handed back can actually be played here. */
    /**
     * The best copy [source] can actually serve for [song], or null when it has none.
     *
     * More than one row is opened, because a catalogue that holds a track can still fail on the
     * particular row matched first — and rows advertising the tier being asked for go first.
     */
    private suspend fun openBest(
        source: SourceAdapter,
        song: Song,
        quality: String?,
    ): DesktopStream? {
        val matches = source.matches(song)
        if (matches.isEmpty()) {
            // A leg cancelled by a sibling winning the race has not missed anything.
            if (currentCoroutineContext().isActive) {
                DesktopTrackLog.log("${source.descriptor.name} has no copy of '${song.title}'")
            }
            return null
        }
        val wantsLossless = ceiling(quality) == DesktopAudioQuality.LOSSLESS
        var settleFor: DesktopStream? = null
        for (match in preferred(matches, song, wantsLossless).take(STREAM_ATTEMPTS)) {
            val stream = source.resolve(match, quality).getOrNull() ?: continue
            if (!playableHere(source, stream)) continue
            val served = stream.format
            if (!wantsLossless || served.isLossless || served.isDolbyAtmos) {
                DesktopTrackLog.log(
                    "${source.descriptor.name} matched '${match.title}' by '${match.artist}'" +
                        " → ${served.summary.ifBlank { "an unstated format" }}",
                )
                return stream
            }
            DesktopTrackLog.log(
                "${source.descriptor.name} offered ${served.summary.ifBlank { "an unstated format" }}" +
                    " for '${match.title}'; looking further",
            )
            // The floor is the best of what was refused, not the first of it.
            settleFor = betterOf(settleFor, stream)
        }
        if (settleFor == null && currentCoroutineContext().isActive) {
            DesktopTrackLog.log("${source.descriptor.name} matched '${song.title}' but returned no stream")
        }
        return settleFor
    }

    /** The matching rows in the order they are worth opening. */
    internal fun preferred(matches: List<Song>, target: Song, wantsLossless: Boolean): List<Song> {
        val sameLength = matches.filter { DesktopTrackMatcher.withinSeconds(it, target, SAME_RECORDING_SEC) }
        val eligible = sameLength.ifEmpty { matches }
        if (!wantsLossless) return eligible
        // Stable, so the confidence order the matcher produced survives inside each tier.
        return eligible.sortedByDescending { it.sourceQuality == DesktopModuleSource.LOSSLESS }
    }

    /** Which of two renditions of the same recording is the better one. */
    private fun betterOf(current: DesktopStream?, candidate: DesktopStream): DesktopStream {
        val held = current ?: return candidate
        val a = candidate.format
        val b = held.format
        if (a.isLossless != b.isLossless) return if (a.isLossless) candidate else held
        if (a.isDolbyAtmos != b.isDolbyAtmos) return if (a.isDolbyAtmos) candidate else held
        return if ((a.kbps ?: 0) > (b.kbps ?: 0)) candidate else held
    }

    private fun playableHere(source: SourceAdapter, stream: DesktopStream): Boolean {
        if (stream.isDolbyAtmos && !DesktopCodecs.supportsDolbyAtmos) {
            DesktopTrackLog.log(
                "${source.descriptor.name} offered a Dolby Atmos rendition, " +
                    "which this build has no E-AC-3 decoder for",
            )
            return false
        }
        return true
    }

    /** Whether this row has a YouTube upload behind it to go back to. */
    fun hasYouTubeOriginal(song: Song): Boolean =
        DesktopAddonSource.parseTrack(song.videoId) == null &&
            DesktopModuleSource.parseTrack(song.videoId) == null &&
            !song.videoId.startsWith("jiosaavn:") &&
            song.videoId.isNotBlank()

    /** The configured source a stream came from, for a log line. */
    internal fun sourceNameFor(stream: DesktopStream): String =
        configs().firstOrNull { it.id == stream.sourceId }?.displayName ?: "a configured source"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** How long the live path waits on the sources before starting on whatever YouTube has. */
    private const val SUBSTITUTE_TIMEOUT_MS = 20_000L

    /**
     * The patient second look's budget. Longer than the race's, because nobody is waiting on the
     * first note by then and a cold addon routinely needs more than one call to settle.
     */
    private const val UPGRADE_TIMEOUT_MS = 90_000L

    /** How many matched rows are opened before a source is written off. */
    private const val STREAM_ATTEMPTS = 3

    /** The runtime agreement that says two rows are the same recording. */
    private const val SAME_RECORDING_SEC = 2

    /** The smallest bitrate gain worth a break in the audio for. */
    private const val UPGRADE_MIN_GAIN_KBPS = 96

    /** The ceiling a call is made under: an explicit override, else the stored rung. */
    internal fun ceiling(qualityOverride: String?): DesktopAudioQuality =
        qualityOverride?.let { override ->
            DesktopAudioQuality.entries.firstOrNull { it.name.equals(override, ignoreCase = true) }
        } ?: DesktopPersistence().audioQuality()

    private fun youtubeCeiling(qualityOverride: String?): Int = ceiling(qualityOverride).maxKbps
}
