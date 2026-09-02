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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

/**
 * A [MusicSource] backed by one or more compatible JS module plugins.
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
                    // Patient, not indefinite. A flat join on everyone made the
                    // *slowest* module the price of every single track — and on
                    // a batch download, where this path runs once per track back
                    // to back, a module that simply never answers was 20s of
                    // dead time per song and nothing to show for it. Every
                    // module still gets a real hearing, several times what the
                    // live path allows; what it no longer gets is unlimited
                    // time while the queue stands still.
                    withTimeoutOrNull(SEARCH_PATIENT_MS) {
                        withTimeoutOrNull(SEARCH_BUDGET_MS) { first.await() }
                        withTimeoutOrNull(SEARCH_PATIENT_GRACE_MS) { jobs.joinAll() }
                    }
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
                    ?.let { "${it / 60}:${"%02d".format(Locale.ROOT, it % 60)}" },
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
            val url = streamResponse.streamUrl?.ifBlank { null } ?: run {
                TrackLog.w(TAG, "${config.displayName}: empty stream URL for $upstreamId")
                return@withContext null
            }
            if (malformed(url)) {
                TrackLog.w(
                    TAG,
                    "${config.displayName}: $moduleId returned a malformed URL for " +
                        "$upstreamId; skipping it — ${url.take(120)}",
                )
                return@withContext null
            }

            val trackMeta = streamResponse.track
            SourceStream(
                url = url,
                format = StreamFormat(
                    codec = codecOf(trackMeta?.mimeType, trackMeta?.audioQuality, url),
                    kbps = trackMeta?.bitrate ?: kbpsFor(trackMeta?.audioQuality, url),
                    sampleRateHz = trackMeta?.sampleRate?.let {
                        if (it < 1000) (it * 1000).toInt() else it.toInt()
                    }?.takeIf { it > 0 },
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
        mimeType?.substringAfterLast('/')?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        val qualityText = quality.orEmpty().uppercase(Locale.ROOT)
        // Tidal's MAX endpoint identifies its Dolby Atmos HLS rendition as
        // EAC3_JOC. Older module responses expose only "Dolby Atmos", so
        // preserve its real codec instead of reducing the stream to unknown.
        if ("ATMOS" in qualityText || "EAC3_JOC" in qualityText || "EC-3" in qualityText) return "eac3-joc"
        if (qualityTier(quality.orEmpty()) == LOSSLESS) return "flac"
        return url.substringBefore('?').substringAfterLast('.').lowercase(Locale.ROOT)
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
         * Whether a module's stream URL is one no server could answer, decided
         * without asking one.
         *
         * A module is somebody else's JavaScript against somebody else's
         * backend, and when either has a bad day the damage lands here as a URL
         * that is syntactically fine and semantically nonsense. The case this is
         * written for, seen on the Tidal backend of `claudo-tidal` in August
         * 2026 on two unrelated devices:
         *
         * ```
         *   https://sp-ad-fa.audio.tidal.com/mediatracks/<blob>/
         *   https://sp-ad-fa.audio.tidal.com/mediatracks/<blob>/0.mp4?token=<exp>~<sig>
         * ```
         *
         * — its own origin pasted in twice, the second copy sitting in the path
         * of the first. Tidal answered 404 to every one. Not an expiry, which is
         * the other thing a signed URL does: the signature decodes to a single
         * copy of `/mediatracks/<blob>/` and was minted an hour before use.
         *
         * Nor is it the whole module going down for a while. Three seconds after
         * the URL above, the same module answered the same `LOSSLESS` request
         * for a different track with a clean Qobuz URL that played — so the
         * fault belongs to *one of the backends a module can resolve to*, and
         * one bad URL says nothing about the next. Which is why this is a test
         * on the URL in hand and nothing is remembered between calls.
         *
         * Caught here rather than downstream because here it is free: no
         * network, no playback attempt, and returning null is already how this
         * class says "I don't have this after all", so [SourceResolver] moves to
         * the next candidate, the next source, and finally to YouTube without a
         * listener hearing anything go wrong. That pays off on both paths this
         * feeds — the live resolve, where the alternative was a track that never
         * played at all, and the mid-song upgrade, where one doubled URL cost
         * four seconds of audition retries before being dropped.
         *
         * Repair is possible and is deliberately not done. The duplicated prefix
         * is removable and the token proves what the answer should be, so this
         * could hand the player a working FLAC rather than leaving it on Opus.
         * It stays a rejection because reconstructing the intent behind somebody
         * else's broken output is a liability the app would then own forever,
         * and the price of refusing is one quality tier on affected tracks
         * instead of silence.
         *
         * Deliberately narrower than "contains two schemes". A URL that passes a
         * target to a proxy — `https://cdn/get?url=https://real/f.flac`, or the
         * same thing in the path — holds two schemes and is perfectly good, and
         * some modules are entitled to hand one over. What is never good is a
         * URL carrying a *second copy of its own origin*, which is what a
         * concatenation against the wrong base produces and what a proxy
         * pointing somewhere else cannot.
         *
         * ### The other half: URLs the player cannot even parse
         *
         * The doubled URL is well formed, which is why it reached a server and
         * came back 404. A separate report — a Xiaomi on Android 14, and only
         * four lines of it — failed one step earlier:
         *
         * ```
         *   HttpDataSource$HttpDataSourceException: Malformed URL
         *     at OkHttpDataSource.makeRequest
         * ```
         *
         * That message is OkHttp's for a string its own parser refused, so no
         * request was ever made. Nothing between here and there edits the URL,
         * so whatever the module returned could not be parsed as http — a
         * relative path, a scheme OkHttp doesn't speak, an unescaped space, an
         * error string in the `streamUrl` field. The empty check above caught
         * only the blank case.
         *
         * So the test is [okhttp3.HttpUrl]'s own, not a hand-rolled one: the
         * question is precisely "will the layer that opens this accept it", and
         * the only answer that can't drift from the truth is that layer's
         * parser, which is already on the classpath and is what threw.
         */
        fun malformed(url: String): Boolean {
            // The gate the player itself will apply, asked here where refusing
            // is free. Restricting modules to http(s) as a side effect is not
            // unwelcome: a module cannot hand the player `file:///data/data/…`
            // and have it read local storage on the module's behalf.
            if (url.toHttpUrlOrNull() == null) return true
            // Parseable, and dead anyway.
            val schemeEnd = url.indexOf("://")
            if (schemeEnd < 0) return false
            val originEnd = url.indexOf('/', schemeEnd + 3)
            if (originEnd < 0) return false
            return url.indexOf(url.substring(0, originEnd), originEnd) >= 0
        }

        /**
         * The three tiers every compatible module speaks, whatever it
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
            val text = label.uppercase(Locale.ROOT)
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
         * How long the stragglers get once someone useful has answered, on the
         * patient path.
         *
         * The counterpart to [SEARCH_GRACE_MS], sized for a caller that is not
         * holding up audio: long enough for a slow-but-working module to land
         * its answer and be compared, short enough that a module which is not
         * going to answer at all cannot define the cost of the track. Ends the
         * search the moment everyone has spoken, so a healthy index never
         * spends any of it.
         */
        const val SEARCH_PATIENT_GRACE_MS = 8_000L

        /**
         * Delimiter between the module id and the upstream track id inside
         * the packed [Song.videoId]. Must not appear in either component;
         * `::` is safe because module ids use alphanumeric-plus-hyphen
         * naming and upstream ids don't include `::`.
         */
        const val MOD_SEPARATOR = "::"
    }
}
