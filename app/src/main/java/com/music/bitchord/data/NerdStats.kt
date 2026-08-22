package com.music.bitchord.data

import com.music.bitchord.data.sources.StreamFormat
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * What the audio decoder is actually being fed, for "stats for nerds".
 *
 * Every figure here is measured rather than inferred. Codec, sample rate and
 * channel count come from the `Format` the audio renderer was configured with —
 * the decoder's own view of the stream. Bitrate is the one a container usually
 * withholds, so it falls back to the bitrate of the stream the resolver
 * genuinely chose for that track. Anything the player hasn't reported stays
 * null and is left out of the display instead of being guessed at.
 *
 * [claimed] is the one figure here that is *not* measured, and is kept apart
 * from the rest for that reason: it is what a source said it was about to send.
 * Holding both is the point — a source promising 24-bit/192kHz while the
 * decoder reports 16-bit/48kHz is the single most likely way for a lossless
 * setting to be quietly doing nothing, and it is invisible unless the two
 * numbers are put side by side. See [downgraded].
 */
object NerdStats {

    class Snapshot(
        val mimeType: String?,
        val bitrateKbps: Int?,
        val sampleRateHz: Int?,
        val channels: Int?,
        /** From the decoder's PCM encoding, where it states one. */
        val bitDepth: Int? = null,
        /** What the source said it would serve, when it came from one that says. */
        val claimed: StreamFormat? = null,
    ) {
        /**
         * Whether what arrived is measurably worse than what was promised.
         *
         * Only ever true when both figures are known — an absent measurement is
         * not evidence of a downgrade, and reporting one on that basis would
         * make the warning worthless the moment it fired on a container that
         * simply doesn't state its rate.
         */
        val downgraded: Boolean
            get() {
                val wantedRate = claimed?.sampleRateHz
                val wantedDepth = claimed?.bitDepth
                return (wantedRate != null && sampleRateHz != null && sampleRateHz < wantedRate) ||
                    (wantedDepth != null && bitDepth != null && bitDepth < wantedDepth)
            }

        /**
         * Whether the decoder is genuinely being fed a lossless codec — the
         * figure the Now Playing screen's "Lossless" badge is gated on, not
         * just what a source promised. [claimed] alone would let a source
         * that said "FLAC" and quietly served Opus still light the badge.
         *
         * Which is exactly what it did, because this was written as
         * `claimed?.isLossless == true || …` — the claim on its own, the very
         * thing the paragraph above says it must not be. Observed: an upgrade
         * to a Tidal FLAC was served, recorded as the declared format, and
         * then died on `ERROR_CODE_IO_BAD_HTTP_STATUS`; playback recovered
         * onto YouTube's Opus and the badge went on reading "Lossless" over
         * it, because the claim outlived the stream that made it.
         *
         * So the decoder gets the last word whenever it has said anything.
         * The claim is only consulted before the renderer has been
         * configured — the gap between a source answering and the first audio
         * frame — where it is the only evidence there is, and where a wrong
         * answer lasts a second rather than a song.
         */
        val isLossless: Boolean
            get() = when {
                mimeType != null -> isLosslessMime(mimeType)
                else -> claimed?.isLossless == true
            }

        /**
         * Whether this is better than CD quality — the line Tidal, Qobuz and
         * Apple Music all draw it at: past 16-bit or past 48kHz, not merely
         * lossless. A 16-bit/44.1kHz FLAC is a bit-exact CD rip and gets
         * called "Lossless"; a 24-bit/96kHz one is "Hi-Res Lossless", because
         * calling both the same thing would flatten a distinction the
         * listener can plausibly hear.
         */
        val isHiRes: Boolean
            get() = isLossless && ((bitDepth ?: 0) > 16 || (sampleRateHz ?: 0) > 48_000)

        /**
         * Whether this is lossy, but at the top of what lossy gets — a 320kbps
         * AAC or MP3 from a module's HIGH tier, rather than YouTube's 160kbps
         * Opus.
         *
         * Worth naming on screen because it is the honest answer often enough
         * to matter: plenty of catalogues simply have no lossless copy of a
         * track, and a badge with only two states — "Lossless" or nothing —
         * makes a good stream and a mediocre one look identical. Not called
         * lossless anywhere, because it isn't.
         *
         * Decided on the bitrate rather than on which source served it: a
         * 256kbps stream is a 256kbps stream wherever it came from.
         */
        val isHiQuality: Boolean
            get() = !isLossless && (bitrateKbps ?: claimed?.kbps ?: 0) >= HI_QUALITY_KBPS
    }

    /**
     * What the renderer's input mime type looks like when the bytes behind it
     * are bit-exact.
     *
     * `audio/raw` is here because that is how Media3 names PCM — a WAV stream
     * reaches the renderer as raw samples, not as `audio/wav`.
     */
    private val LOSSLESS_CODEC_SUFFIXES = listOf("flac", "alac", "raw")

    /**
     * Whether [mimeType] names a bit-exact codec.
     *
     * Exposed because the decoder's own verdict is also what decides whether a
     * track playing from the disk cache is worth hunting a better copy of — see
     * [QualityUpgrade.adoptUnresolved][com.music.bitchord.playback.QualityUpgrade.adoptUnresolved].
     * Reading [Snapshot.isLossless] there instead would mean trusting whichever
     * track the last [current] publish happened to describe, which after a
     * queue advance is the previous one.
     */
    fun isLosslessMime(mimeType: String?): Boolean =
        mimeType != null && LOSSLESS_CODEC_SUFFIXES.any { mimeType.endsWith(it) }

    /**
     * The bitrate a lossy stream has to reach to be worth calling out.
     *
     * At 256 an Apple-style AAC counts and YouTube's Opus, which tops out
     * around 160, does not — which is the distinction the label exists to
     * draw.
     */
    private const val HI_QUALITY_KBPS = 256

    val current = MutableStateFlow<Snapshot?>(null)

    /**
     * YouTube video ids with a module lookup racing YouTube's own resolve
     * for the stream to actually play — see
     * [PlaybackService][com.music.bitchord.playback.PlaybackService]'s
     * resolving data source. Both start together and whichever answers first
     * plays; the module is the one still worth hearing about, because a
     * YouTube win only means the search continues under the music — so this
     * is what the UI shows "looking for a better copy" from. A track leaves
     * the set the moment its own lookup settles either way, never on a timer.
     */
    val racingLossless = MutableStateFlow<Set<String>>(emptySet())

    fun onLosslessRaceStart(videoId: String) {
        racingLossless.value += videoId
    }

    fun onLosslessRaceEnd(videoId: String) {
        racingLossless.value -= videoId
    }

    /**
     * Bitrate in kbps of the stream picked for each videoId.
     *
     * Keyed by track rather than kept as a single "last picked": the read-ahead
     * resolves the *next* track through the same code, so one loose value would
     * end up describing the wrong song.
     */
    private val picked = ConcurrentHashMap<String, Int>()

    /** As [picked], for the richer format a non-YouTube source can state. */
    private val declared = ConcurrentHashMap<String, StreamFormat>()

    fun onStreamPicked(videoId: String, kbps: Int) {
        if (kbps <= 0) return
        // Enough for the queue in hand; this is a lookup, not a store.
        if (picked.size >= MAX_REMEMBERED) picked.clear()
        picked[videoId] = kbps
    }

    /** Recorded as a source hands over a stream, keyed by that source's own track id. */
    fun onSourceStream(trackId: String?, format: StreamFormat) {
        if (trackId.isNullOrBlank()) return
        if (declared.size >= MAX_REMEMBERED) declared.clear()
        declared[trackId] = format
    }

    fun pickedBitrateKbps(videoId: String?): Int? = videoId?.let { picked[it] }

    /**
     * Undoes [onSourceStream] for [trackId].
     *
     * For when a swap to a claimed-better stream doesn't pan out and the
     * player falls back to what it had — see
     * [PlaybackService][com.music.bitchord.playback.PlaybackService]'s
     * upgrade revert. Without this the claim from the abandoned stream keeps
     * describing the one that's actually playing, which is how a reverted
     * FLAC swap leaves the "Lossless" badge lit over plain Opus.
     */
    fun clearDeclared(trackId: String?) {
        if (trackId != null) declared.remove(trackId)
    }

    /**
     * @param mediaId the queue's id for the track, which for a source-backed
     *   one wraps the source's id — unwrapped here so callers don't each have
     *   to know the key format.
     */
    fun declaredFormat(mediaId: String?): StreamFormat? {
        val key = mediaId ?: return null
        return declared[key]
            ?: com.music.bitchord.data.sources.SourceRegistry.parseTrackKey(key)
                ?.second?.let { declared[it] }
    }

    private const val MAX_REMEMBERED = 64
}
