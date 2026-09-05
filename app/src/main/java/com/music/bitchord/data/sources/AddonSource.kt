package com.music.bitchord.data.sources

import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.sources.addon.AddonClient
import com.music.bitchord.data.sources.addon.AddonException
import com.music.bitchord.data.sources.addon.AddonNotFound
import com.music.bitchord.data.sources.addon.AddonStream
import com.music.bitchord.data.sources.addon.AddonTrack
import com.music.bitchord.playback.StreamContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.Locale

/**
 * A [MusicSource] backed by one addon server the user pointed at.
 *
 * The [config]'s [SourceConfig.baseUrl] is the addon's own URL — the thing
 * pasted on the sources screen, token and all. Everything below that is
 * [AddonClient]: this class is the translation between what the addon says and
 * what the rest of the app understands, and it is deliberately the only place
 * that knows both vocabularies.
 *
 * Where [ModuleSource] has to fan out across every plugin in an index and
 * reconcile their answers, one addon is one catalogue and one search. There is
 * no interleaving, no per-module budget and no engine pool, which is most of
 * why this file is a third the length of that one. A user wanting two
 * catalogues adds two addons, and [SourceRegistry] walks them in order.
 */
class AddonSource(
    override val config: SourceConfig,
) : MusicSource, SourceRegistry.ConfigBacked {

    override val configId: String get() = config.id
    override val kind: SourceKind get() = config.kind
    override val displayName: String get() = config.displayName

    private val client = AddonClient(config.baseUrl)

    /**
     * Rows this addon has recently handed over, by their own track id.
     *
     * Two things need them after the fact and neither is reachable from a bare
     * id. The first is the duration the catalogue claimed, which
     * [QualityUpgrade][com.music.bitchord.playback.QualityUpgrade] checks a
     * substitute against before cutting it into a track already playing — a
     * match on title and artist alone has put a 189s cut under a 163s
     * recording before. The second is the row's own `streamURL`, which the
     * spec allows in place of a `/stream` answer and which is the only thing
     * left to try when that endpoint has nothing.
     *
     * Bounded and cleared wholesale rather than pruned: it is an optimisation
     * on both counts, and a miss costs a field of metadata, not a track.
     */
    private val rows = Collections.synchronizedMap(
        object : LinkedHashMap<String, AddonTrack>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, AddonTrack>) = size > MAX_ROWS
        },
    )

    // ── Health ────────────────────────────────────────────────────────────

    /**
     * Whether this addon is there and is one this app can use.
     *
     * The manifest answers all three questions at once — reachable, well
     * formed, and declaring the two resources a track needs — which is why the
     * distinction [SourceHealth] draws is available here for free.
     * [AddonException] is the addon or its URL being wrong in a way no amount
     * of retrying fixes, and so is a rejection; anything else is a server
     * having a bad minute and is worth trying again on the next track.
     */
    override suspend fun health(): SourceHealth = withContext(Dispatchers.IO) {
        if (config.baseUrl.isBlank()) {
            return@withContext SourceHealth.Rejected("An addon URL is required")
        }
        client.manifest().fold(
            onSuccess = { manifest ->
                SourceHealth.Ok(
                    listOfNotNull(
                        manifest.displayName.takeIf { it.isNotBlank() },
                        manifest.version.takeIf { it.isNotBlank() }?.let { "v$it" },
                    ).joinToString(" ").ifBlank { null },
                )
            },
            onFailure = { failure ->
                // No readable manifest is not the same as no addon. The
                // endpoints are what this app uses; the manifest only describes
                // them, and an addon that never published one is healthy the
                // moment it answers a search. Reported plainly rather than as a
                // success with a hidden caveat, so the row says which of the two
                // kinds of working this is.
                if (client.probeSearch().isSuccess) {
                    TrackLog.d(TAG, "${config.displayName}: no manifest, but search answers")
                    return@fold SourceHealth.Ok("No manifest · search works")
                }
                val reason = failure.message ?: "Could not reach the addon"
                if (failure is AddonException) {
                    SourceHealth.Rejected(reason)
                } else {
                    TrackLog.w(TAG, "addon unreachable for ${config.displayName}: $reason")
                    SourceHealth.Unreachable(reason)
                }
            },
        )
    }

    /**
     * What the addon calls itself, or null if it has not said yet.
     *
     * The sources screen stores this into the config's label so the row reads
     * "Unified · Quality First" rather than "unified-addon.netlify.app". Asking
     * the addon is the only honest way to get that: a hostname is where a thing
     * is, not what it is, and there is no reason to make a user invent a name
     * for something that already published one.
     *
     * Free after [health] — the manifest is shared and cached by the client, so
     * this reads what the probe already fetched.
     */
    suspend fun manifestName(): String? =
        client.manifest().getOrNull()?.displayName?.ifBlank { null }

    // ── Search ────────────────────────────────────────────────────────────

    /**
     * One request, one catalogue, in the order the addon returned it.
     *
     * [waitForAll] is ignored, and has nothing here to mean: it exists so a
     * background pass can wait out the slow plugin in a module index, and an
     * addon is a single call whose entire cost is one HTTP round trip. The
     * addon is asked at the tier the app would want at best — a search is
     * where a row's advertised quality is read, and asking at a capped tier
     * would have a tier-aware catalogue describe rows it was never going to be
     * asked for.
     *
     * The order is left exactly as it arrived.
     * [TrackMatcher][com.music.bitchord.data.sources.TrackMatcher] decides which
     * rows are the recording and [SourceResolver] decides which to open; an
     * addon knows its own catalogue better than a re-sort here would.
     */
    override suspend fun search(query: String, limit: Int, waitForAll: Boolean): List<Song> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val tracks = client.search(query, AddonClient.TIER_LOSSLESS).getOrElse { failure ->
                TrackLog.w(TAG, "${config.displayName}: search failed — ${failure.message}")
                return@withContext emptyList()
            }
            tracks.asSequence()
                .filter { it.id.isNotBlank() && it.title.isNotBlank() }
                .take(limit)
                .map { track ->
                    rows[track.id] = track
                    Song(
                        videoId = SourceRegistry.trackKey(config.id, track.id),
                        title = track.title,
                        artist = track.artist,
                        albumName = track.album.ifBlank { null },
                        thumbnailUrl = track.artwork,
                        durationText = track.durationSec?.let {
                            "${it / 60}:${"%02d".format(Locale.ROOT, it % 60)}"
                        },
                        sourceQuality = ModuleSource.qualityTier("${track.audioQuality} ${track.format}"),
                    )
                }
                .toList()
        }

    // ── Stream ────────────────────────────────────────────────────────────

    override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? =
        withContext(Dispatchers.IO) {
            val tier = request.tier
            val result = client.stream(trackId, tier)
            val answer = result.getOrNull()

            if (answer == null) {
                // A 404 is the addon saying it does not hold this recording,
                // which is a miss and belongs in no log. Anything else is a
                // fault worth a line, and both end the same way: whatever the
                // search row had, else null so [SourceResolver] moves on.
                val failure = result.exceptionOrNull()
                if (failure !is AddonNotFound) {
                    TrackLog.w(TAG, "${config.displayName}: stream failed for $trackId — ${failure?.message}")
                }
                return@withContext fromRow(trackId, tier)
            }

            val url = answer.url.ifBlank { null } ?: run {
                // The addon answered, and said it has nothing for this one. Its
                // own words when it gave any: some report a backend that came
                // up empty, others a credential the user has to go and set on
                // the addon's own setup page — and that second kind is only
                // ever actionable if it is said out loud here.
                TrackLog.w(
                    TAG,
                    "${config.displayName}: no stream for $trackId" +
                        (answer.error?.ifBlank { null }?.let { " — $it" } ?: ""),
                )
                return@withContext fromRow(trackId, tier)
            }

            openable(url, answer, trackId, tier)
        }

    /**
     * The stream the search row carried, when the `/stream` call had nothing.
     *
     * The spec allows a row to publish its own `streamURL` and says a host may
     * skip the stream call entirely when one is present. This app does not skip
     * it — a row's URL comes with none of the codec, container and transport
     * metadata that decides how the player opens it, and giving that up to save
     * one round trip is a poor trade. As a *fallback* it is worth having: an
     * addon that serves permanent direct links and treats `/stream` as optional
     * is otherwise unplayable here for no good reason.
     */
    private fun fromRow(trackId: String, tier: String): SourceStream? {
        val row = rows[trackId] ?: return null
        val url = row.streamURL?.ifBlank { null } ?: return null
        TrackLog.d(TAG, "${config.displayName}: using the search row's own URL for $trackId")
        return openable(url, AddonStream(url = url, format = row.format), trackId, tier)
    }

    /**
     * Everything between a URL the addon named and one the player may open.
     *
     * Three refusals, each of which is the only place it can be caught:
     *
     *  - **Unopenable.** [ModuleSource.malformed] is OkHttp's own parser plus
     *    the doubled-origin test, asked here where refusing costs nothing.
     *  - **Encrypted.** The spec forbids an addon sending a protected rendition
     *    unless the request asked for one, and this app never does. Handing the
     *    player bytes it has no key for fails later and less clearly.
     *  - **Immersive, on a device that can't.** [ModuleSource.unplayable] again,
     *    for exactly the reason set out there: an Atmos rendition is routinely
     *    the *only* one offered for a track, so there is no lower sibling to
     *    fall back to and refusing is what lets another source be heard.
     */
    private fun openable(url: String, answer: AddonStream, trackId: String, tier: String): SourceStream? {
        if (ModuleSource.malformed(url)) {
            TrackLog.w(
                TAG,
                "${config.displayName}: malformed URL for $trackId; skipping it — ${url.take(120)}",
            )
            return null
        }
        if (answer.isEncrypted) {
            TrackLog.w(
                TAG,
                "${config.displayName}: $trackId came back encrypted, which BitChord never asked for — passing",
            )
            return null
        }

        val format = formatOf(answer, url, tier)
        val playsAtmos = DeviceCodecs.playsDolbyAtmos
        if (ModuleSource.unplayable(format, atmosAllowed = playsAtmos && AppSettings.dolbyAtmos.value)) {
            val why = if (playsAtmos) {
                "which is switched off in Settings"
            } else {
                "which this device has no decoder for"
            }
            TrackLog.w(
                TAG,
                "${config.displayName}: answered a $tier request with ${format.summary}, $why — passing",
            )
            return null
        }

        // Said out loud rather than left to be sniffed. An addon serving an HLS
        // playlist from an extensionless path — which the reference addon does,
        // at `/dash/{id}` — is the exact shape that reaches a progressive
        // extractor and fails as an unreadable container. The addon told us
        // what it is; the player only needed to be passed it on.
        answer.transport?.let { StreamContainer.declare(url, it) }

        return SourceStream(
            url = url,
            format = format,
            durationSec = rows[trackId]?.durationSec,
        )
    }

    /**
     * What is really on the end of the URL, from what the addon said about it.
     *
     * Ordered by how much each claim is worth. A stated `codec` is the addon
     * answering the question directly and is taken as given. A container names
     * the envelope, which for `flac`, `wav` and `mp3` settles the codec too. A
     * mime type is next, then a quality label naming a lossless tier, and last
     * the URL's own extension — a guess, but the one that catches a server that
     * has quietly walked down its fallback chain and handed back `.128.mp3`.
     *
     * Null when none of them knows, which is left null rather than guessed at:
     * [StreamFormat.isLossless] answering "unknown" is information, and
     * answering "no" when it does not know is not.
     */
    private fun formatOf(answer: AddonStream, url: String, tier: String): StreamFormat {
        val codec = answer.statedCodec?.takeIf { it in AUDIO_CODECS }
            ?: answer.statedContainer?.takeIf { it in SELF_DESCRIBING_CONTAINERS }
            ?: answer.mimeType?.substringAfterLast('/')?.substringBefore(';')?.trim()
                ?.lowercase(Locale.ROOT)?.takeIf { it in AUDIO_CODECS }
            ?: if (answer.isDolbyAtmos) {
                "eac3-joc"
            } else if (ModuleSource.qualityTier(answer.qualityText) == ModuleSource.LOSSLESS) {
                "flac"
            } else {
                url.substringBefore('?').substringAfterLast('.')
                    .lowercase(Locale.ROOT).takeIf { it in AUDIO_CODECS }
            }

        return StreamFormat(
            codec = codec,
            // The tier's own published meaning is the last resort and only for
            // the lossy rungs, where "HIGH" is a statement of 320 and the
            // number is what says "this is not what you asked for".
            kbps = answer.kbps ?: when (tier) {
                AddonClient.TIER_HIGH -> 320
                AddonClient.TIER_LOW -> 128
                else -> null
            },
            sampleRateHz = answer.sampleRateHz,
            bitDepth = answer.bits,
        )
    }

    /** What this app asks an addon for, in the vocabulary the reference host uses. */
    private val StreamRequest.tier: String
        get() = when (this) {
            is StreamRequest.Lossless -> AddonClient.TIER_LOSSLESS
            is StreamRequest.Best -> AddonClient.TIER_HIGH
            is StreamRequest.Capped ->
                if (maxKbps <= ModuleSource.LOW_CEILING_KBPS) AddonClient.TIER_LOW else AddonClient.TIER_HIGH
        }

    /** Dropped along with the source it belongs to — see [SourceRegistry.publish]. */
    fun release() {
        rows.clear()
        client.clear()
    }

    private companion object {
        const val TAG = "BitChord"

        /** How many search rows to remember. A long queue's worth, several times over. */
        const val MAX_ROWS = 256

        /**
         * Containers that name their own codec. A `.flac` holds FLAC and
         * nothing else; an `mp4` or an `ogg` holds any of several things and
         * says nothing on its own, so those are deliberately absent.
         */
        val SELF_DESCRIBING_CONTAINERS = setOf("flac", "wav", "mp3", "aiff")

        /**
         * What may be believed as a codec, from a field or from a URL's
         * extension.
         *
         * `mp4` is in the list and is the interesting one. It names a container
         * rather than a codec, so it is not in [SELF_DESCRIBING_CONTAINERS] and
         * never overrides something better — but leaving it out entirely was
         * wrong in a way worth spelling out. A live addon's JioSaavn backend
         * answers `{"format": "mp4", "quality": "320kbps"}` with a URL ending
         * `_320.mp4`, and with `mp4` unrecognised *nothing* named the codec: the
         * format came back with a null one, and a null codec means
         * [StreamFormat.isLossless] answers "unknown" rather than "no".
         *
         * Unknown is not a harmless answer here. It is the answer that lets a
         * 320kbps AAC stream stay in contention against a FLAC when
         * [SourceResolver] is choosing between them, on the grounds that nobody
         * could rule it out. Recording the container says the true and useful
         * thing — this is MP4, and MP4 is not a lossless container — without
         * inventing the codec inside it.
         */
        val AUDIO_CODECS = setOf(
            "flac", "alac", "wav", "aiff", "mp3", "aac", "he-aac", "m4a", "mp4",
            "ogg", "opus", "vorbis", "webm", "eac3-joc", "ec3-joc",
        )
    }
}
