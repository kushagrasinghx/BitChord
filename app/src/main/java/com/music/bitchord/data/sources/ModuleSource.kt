package com.music.bitchord.data.sources

import android.util.Log
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.module.ModuleManager
import com.music.bitchord.data.sources.module.ModuleSearchResult
import com.music.bitchord.data.sources.module.SpineModule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A [MusicSource] backed by one or more Convx-compatible JS module plugins.
 *
 * The [config]'s [SourceConfig.baseUrl] points at a module-index JSON
 * (e.g. `https://example.com/index.json`). That index lists `SpineModule`
 * descriptors; each one ships a JS file that exports `searchTracks()` and
 * `getTrackStreamUrl()`. This class fetches the index, loads the JS into a
 * QuickJS sandbox, and routes [search] / [stream] through those exports.
 *
 * Track IDs are encoded as `<moduleId>::<upstreamId>` so [stream] can
 * identify which loaded module to call.
 */
class ModuleSource(
    override val config: SourceConfig,
) : MusicSource, SourceRegistry.ConfigBacked {

    override val configId: String get() = config.id
    override val kind: SourceKind get() = SourceKind.MODULE
    override val displayName: String get() = config.displayName

    /**
     * One manager per source instance. The manager's in-memory module cache
     * survives across successive search calls on the same instance, which is
     * what keeps the QuickJS engines alive between a search and the stream
     * call for a result it returned.
     */
    private val manager = ModuleManager()

    // ── Health ────────────────────────────────────────────────────────────

    override suspend fun health(): SourceHealth = withContext(Dispatchers.IO) {
        if (config.baseUrl.isBlank()) {
            return@withContext SourceHealth.Rejected("A module index URL is required")
        }
        try {
            val modules = manager.fetchIndex(config.baseUrl).getOrThrow()
            when {
                modules.isEmpty() -> SourceHealth.Rejected(
                    "The index answered but listed no modules — check the URL"
                )
                else -> SourceHealth.Ok("${modules.size} module${if (modules.size == 1) "" else "s"}")
            }
        } catch (e: Exception) {
            TrackLog.w(TAG, "module index fetch failed for ${config.displayName}: ${e.message}")
            SourceHealth.Unreachable(e.message ?: "Could not reach the module index")
        }
    }

    // ── Search ────────────────────────────────────────────────────────────

    /**
     * Every module in the index, asked at once, answers interleaved, and
     * nobody waited on past the point of usefulness.
     *
     * Three things, all of which the obvious implementation gets wrong:
     *
     *  - **At once.** A module is a network fetch, a JS load and a search;
     *    done one after another, a three-module index spent three times as
     *    long answering as it had to, against a lookup that has to finish
     *    before audio can start.
     *  - **Interleaved.** Filling the result list module by module and
     *    stopping at [limit] means the first module's tail crowds out every
     *    other module's best hit — with a limit of eight and a chatty first
     *    module, the rest of the index was never asked at all. Round-robin
     *    puts each module's top result ahead of any module's second, so a
     *    track only one of them holds survives the cut.
     *  - **Not to the last straggler.** Asked at once but awaited together,
     *    the slowest module becomes the price of every track — 7.5s against
     *    1.5s for the fastest, measured on the same query. So the fan-out
     *    closes a short grace period after the first useful answer, and
     *    whoever hasn't spoken by then sits this track out.
     *
     * What comes back is a candidate list, not a ranking:
     * [TrackMatcher][com.music.bitchord.data.sources.TrackMatcher] decides
     * which rows are the recording and [SourceResolver] decides which of those
     * to open. This only has to be complete enough to contain the right one.
     */
    override suspend fun search(query: String, limit: Int, waitForAll: Boolean): List<Song> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val indexUrl = config.baseUrl
            val baseUrl = indexUrl.substringBeforeLast("/")

            val modules = manager.fetchIndex(indexUrl).getOrElse { e ->
                TrackLog.w(TAG, "${config.displayName}: index fetch failed — ${e.message}")
                return@withContext emptyList()
            }

            val perModule = coroutineScope {
                val answers = arrayOfNulls<List<Song>>(modules.size)
                val first = CompletableDeferred<Unit>()
                val jobs = modules.mapIndexed { at, module ->
                    launch {
                        val songs = searchOne(module, query, limit, baseUrl)
                        answers[at] = songs
                        if (songs.isNotEmpty()) first.complete(Unit)
                    }
                }
                // Everyone gets until someone useful answers, and a short
                // grace period after that. Waiting for all of them made the
                // slowest module the cost of every track — measured at 7.5s
                // against 1.5s for the fastest, on a lookup that has to finish
                // before audio can start. Waiting for only the first is the
                // opposite mistake: the fast module is not reliably the one
                // holding the best copy, and the grace period is what buys the
                // chance to compare them.
                if (waitForAll) {
                    withTimeoutOrNull(SEARCH_PATIENT_MS) { jobs.joinAll() }
                } else {
                    withTimeoutOrNull(SEARCH_BUDGET_MS) { first.await() }
                    withTimeoutOrNull(SEARCH_GRACE_MS) { jobs.joinAll() }
                }
                jobs.forEach { it.cancel() }
                answers.filterNotNull()
            }
            interleave(perModule).take(limit)
        }

    /** One module's answers, or an empty list if it couldn't give any. */
    private suspend fun searchOne(
        module: SpineModule,
        query: String,
        limit: Int,
        baseUrl: String,
    ): List<Song> {
        val loaded = manager.loadModule(module) { baseUrl }.getOrElse { e ->
            TrackLog.w(TAG, "${config.displayName}: load failed for ${module.id} — ${e.message}")
            return emptyList()
        }
        val searchResponse = manager.searchTracks(loaded, query, limit).getOrElse { e ->
            TrackLog.w(TAG, "${config.displayName}: search failed for ${module.id} — ${e.message}")
            return emptyList()
        }
        return searchResponse.tracks.map { track ->
            Song(
                // Encode module id + upstream id so stream() can route back.
                videoId = SourceRegistry.trackKey(config.id, "${module.id}$MOD_SEPARATOR${track.id}"),
                title = track.title,
                artist = track.artist,
                albumName = track.album.ifBlank { null },
                thumbnailUrl = track.albumCover,
                durationText = track.duration.takeIf { it > 0 }
                    ?.let { "${it / 60}:${"%02d".format(it % 60)}" },
                sourceQuality = rowTier(track),
            )
        }
    }

    /**
     * What this row can be had at, from what the module said about *it*.
     *
     * Precedence is the whole point. A row stating `format: flac` or
     * `FLAC 16-bit / 44.1kHz` is describing the copy it holds, and is worth
     * believing. A row listing `availableQualities: [LOSSLESS, HIGH, LOW]` is
     * describing what its backend might in principle be asked for, and is
     * worth much less: the aggregator module publishes that list on rows whose
     * lossless backend then declines and whose stream arrives from SoundCloud
     * at 128kbps. So the stated quality is read first and the menu of
     * possibilities only when nothing was stated — otherwise every row from
     * that module claims the top tier and the ordering it feeds is noise.
     */
    private fun rowTier(track: ModuleSearchResult): String? =
        qualityTier("${track.audioQuality} ${track.format}")
            ?: track.availableQualities
                .maxByOrNull { TIERS.indexOf(qualityTier(it)) }
                ?.let(::qualityTier)

    /** First of each, then second of each: nth place everywhere beats second place anywhere. */
    private fun interleave(lists: List<List<Song>>): List<Song> {
        val merged = mutableListOf<Song>()
        var rank = 0
        while (lists.any { it.size > rank }) {
            for (list in lists) list.getOrNull(rank)?.let(merged::add)
            rank++
        }
        return merged
    }

    // ── Stream ────────────────────────────────────────────────────────────

    override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? =
        withContext(Dispatchers.IO) {
            // trackId is "<moduleId>::<upstreamId>"
            val cut = trackId.indexOf(MOD_SEPARATOR)
            if (cut < 0) {
                TrackLog.w(TAG, "${config.displayName}: malformed trackId '$trackId'")
                return@withContext null
            }
            val moduleId = trackId.substring(0, cut)
            val upstreamId = trackId.substring(cut + MOD_SEPARATOR.length)

            // Find the module in the index, load it (cache hit after search),
            // then ask for the stream URL.
            val modules = manager.fetchIndex(config.baseUrl).getOrElse { e ->
                TrackLog.w(TAG, "${config.displayName}: index fetch failed — ${e.message}")
                return@withContext null
            }
            val module = modules.firstOrNull { it.id == moduleId } ?: run {
                TrackLog.w(TAG, "${config.displayName}: module '$moduleId' not found in index")
                return@withContext null
            }
            val baseUrl = config.baseUrl.substringBeforeLast("/")
            val loaded = manager.loadModule(module) { baseUrl }.getOrElse { e ->
                TrackLog.w(TAG, "${config.displayName}: load failed for $moduleId — ${e.message}")
                return@withContext null
            }
            val streamResponse = manager.getStreamUrl(
                loaded = loaded,
                trackId = upstreamId,
                quality = request.tier,
                settings = settingsFor(request),
            ).getOrElse { e ->
                TrackLog.w(TAG, "${config.displayName}: getStreamUrl failed for $upstreamId — ${e.message}")
                return@withContext null
            }
            val url = streamResponse.streamUrl.ifBlank { null } ?: run {
                TrackLog.w(TAG, "${config.displayName}: empty stream URL for $upstreamId")
                return@withContext null
            }

            val trackMeta = streamResponse.track
            SourceStream(
                url = url,
                format = StreamFormat(
                    codec = codecOf(trackMeta?.mimeType, trackMeta?.audioQuality, url),
                    kbps = kbpsFor(trackMeta?.audioQuality, url),
                    sampleRateHz = trackMeta?.sampleRate?.toInt()?.takeIf { it > 0 },
                    bitDepth = trackMeta?.bitDepth?.takeIf { it > 0 },
                ),
            )
        }

    /**
     * What is really on the other end of [url], as far as anything says it.
     *
     * Three sources, and nothing invented between them. The mime type is the
     * only one stated outright, and most modules don't send it. A quality
     * label naming a lossless tier is worth taking at its word. Failing both,
     * the URL's own extension — a guess, but the reliable one in the case that
     * matters: a server that has quietly walked down its fallback chain hands
     * back a link with `.128.mp3` in it, and reading that is the whole
     * difference between noticing and playing it.
     *
     * Null when none of the three knows, which is left as null rather than
     * guessed at — [kbpsFor] carries what is known about those instead.
     */
    private fun codecOf(mimeType: String?, quality: String?, url: String): String? {
        mimeType?.substringAfterLast('/')?.substringBefore(';')?.trim()?.lowercase()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        if (qualityTier(quality.orEmpty()) == LOSSLESS) return "flac"
        return url.substringBefore('?').substringAfterLast('.').lowercase()
            .takeIf { it in AUDIO_EXTENSIONS }
    }

    /**
     * The bitrate a module has committed to, from the number in its label, the
     * number in the URL, or the tier's own published meaning — the multi-source
     * module offers exactly `128kbps → LOW` and `320kbps → HIGH` in its
     * settings, so a bare `HIGH` is a stated 320, not an unknown.
     *
     * Worth pinning down because it is what says "this is not the lossless you
     * asked for" in the case where the codec never gets named.
     */
    private fun kbpsFor(quality: String?, url: String): Int? =
        kbpsIn(quality) ?: kbpsIn(url) ?: when (qualityTier(quality.orEmpty())) {
            HIGH -> 320
            LOW -> 128
            else -> null
        }

    /** The `128` in `…/ikpkCKbPKAqA.128.mp3` or in a `128kbps` label. */
    private fun kbpsIn(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val found = KBPS_LABEL.find(text) ?: KBPS_URL.find(text) ?: return null
        return found.groupValues[1].toIntOrNull()?.takeIf { it in 8..2_000 }
    }

    /**
     * The settings a module is handed for this request.
     *
     * `fallbackMode` is the important one, and `strict` is the right answer
     * whenever lossless was actually asked for. Left flexible, a module whose
     * lossless backend is slow or missing walks its *own* fallback chain —
     * Qobuz, then HiFi, then SoundCloud — and returns a 128kbps MP3 ten
     * seconds later, having spent that time getting further from what was
     * wanted. Strict makes it fail fast and say so, which hands the decision
     * back to [SourceResolver], who has two other modules to try and knows
     * which of them advertised a FLAC.
     */
    private fun settingsFor(request: StreamRequest): Map<String, String> = mapOf(
        "quality" to request.tier,
        "fallbackMode" to if (request is StreamRequest.Lossless) "strict" else "flexible",
    )

    private val StreamRequest.tier: String
        get() = when (this) {
            is StreamRequest.Lossless -> LOSSLESS
            is StreamRequest.Best -> HIGH
            is StreamRequest.Capped -> if (maxKbps <= LOW_CEILING_KBPS) LOW else HIGH
        }

    internal companion object {
        const val TAG = "BitChord"

        /**
         * The three tiers every Convx-compatible module speaks, whatever it
         * calls them on the way out: `LOSSLESS`, `FLAC 16-bit / 44.1kHz` and
         * `hires-96` are one tier; `320kbps` and `HIGH` are another.
         */
        const val LOSSLESS = "LOSSLESS"
        const val HIGH = "HIGH"
        const val LOW = "LOW"

        /** Worst to best, so a row's best available tier can be picked out by index. */
        val TIERS = listOf(LOW, HIGH, LOSSLESS)

        /**
         * Which tier a module's free-text quality label describes, or null
         * when it doesn't describe one.
         *
         * Order matters: `FLAC 16-bit / 44.1kHz` contains a bit depth *and* a
         * codec, and it is the codec that settles it.
         */
        fun qualityTier(label: String): String? {
            val text = label.uppercase()
            return when {
                text.isBlank() -> null
                LOSSLESS_HINTS.any { it in text } -> LOSSLESS
                LOW_HINTS.any { it in text } -> LOW
                HIGH_HINTS.any { it in text } -> HIGH
                else -> null
            }
        }

        private val LOSSLESS_HINTS =
            listOf("LOSSLESS", "FLAC", "ALAC", "HI-RES", "HI_RES", "HIRES", "24-BIT", "16-BIT", "WAV")
        private val LOW_HINTS = listOf("LOW", "128", "96KBPS", "64")
        private val HIGH_HINTS = listOf("HIGH", "320", "MP3", "AAC", "M4A", "OPUS", "OGG")

        /** Below this, a capped connection is better served by the module's own small tier. */
        const val LOW_CEILING_KBPS = 128

        private val AUDIO_EXTENSIONS =
            setOf("flac", "alac", "wav", "aiff", "mp3", "m4a", "aac", "ogg", "opus", "webm")

        /** `320kbps` in a label, and the `.128.` a CDN puts in the filename. */
        private val KBPS_LABEL = Regex("""(\d{2,4})\s*kbps""", RegexOption.IGNORE_CASE)
        private val KBPS_URL = Regex("""\.(\d{2,4})\.(?:mp3|m4a|aac|ogg)""", RegexOption.IGNORE_CASE)

        /**
         * How long the fan-out waits for a first useful answer, and how much
         * longer the rest then get. Both are latency the listener spends
         * staring at a paused player, so neither is generous.
         */
        const val SEARCH_BUDGET_MS = 8_000L
        const val SEARCH_GRACE_MS = 2_500L

        /**
         * The budget for the background pass, which runs while a track is
         * already playing and is therefore allowed to be slow. This is where
         * the module dropped at [SEARCH_GRACE_MS] gets its hearing — and it is
         * routinely the one holding the lossless copy.
         */
        const val SEARCH_PATIENT_MS = 25_000L

        /**
         * Delimiter between the module id and the upstream track id inside
         * the packed [Song.videoId]. Must not appear in either component;
         * `::` is safe because module ids use alphanumeric-plus-hyphen
         * naming and upstream ids don't include `::`.
         */
        const val MOD_SEPARATOR = "::"
    }
}
