package com.music.bitchord.data.sources

import android.media.MediaCodecList
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import com.music.bitchord.data.TrackLog
import java.util.Locale

/**
 * What this particular phone can actually decode, asked once and remembered.
 *
 * One question so far, and it is the one the rest of the app is otherwise free
 * to ignore all the way to silence: **Dolby Atmos**. E-AC-3 is licensed, so its
 * decoder ships with the *vendor* image rather than with Android —
 * `c2.dolby.eac3.decoder` is present on the Samsung this was traced on and
 * absent on plenty of devices that look identical from up here. Nothing else in
 * the app ever asked, so an Atmos rendition was handed to the player on every
 * device alike.
 *
 * Asked here, before the URL is returned, rather than left to the player,
 * because of how badly the player fails at it. A format no renderer supports is
 * not reliably an *error*: `DefaultTrackSelector` declines to select a track
 * whose support is `FORMAT_UNSUPPORTED_SUBTYPE`, the period then ends with no
 * renderer enabled, and the queue moves on — a song that skipped itself
 * instantly, with no exception for
 * [PlaybackService.recoverFrom][com.music.bitchord.playback.PlaybackService] to
 * recover from and nothing on screen to explain it. The other half of the time
 * it is a decoder-init failure, which recovers, but only by spending the
 * attempt budget and landing on YouTube. Both are worse than never offering the
 * rendition.
 *
 * ### Getting the answer right
 *
 * The verdict has to be the *renderer's* verdict, because the renderer is who
 * acts on it, and a hand-rolled second opinion that disagrees is worse than no
 * opinion at all — a false yes is the silent skip above, and a false no quietly
 * drops a capable flagship to YouTube for every Atmos track it will ever play.
 * So [MediaCodecUtil] is asked first: it is precisely what `MediaCodecSelector`
 * hands `MediaCodecAudioRenderer`, including Media3's per-device workarounds
 * and its blocklists for decoders that are advertised and don't work.
 *
 * Both mime types are asked for. A decoder that declares plain `audio/eac3` can
 * carry a JOC stream — the height objects are dropped and what comes out is the
 * 5.1 core downmixed, which is a lesser render but not a failure — and Media3
 * will fall back to one for exactly that reason. Asking for only the JOC mime
 * would refuse Atmos on every device that ships the ordinary E-AC-3 decoder.
 *
 * [MediaCodecList] is then a backstop rather than the primary answer, for the
 * case where the Media3 query throws (`DecoderQueryException` is a real
 * outcome on a device whose codec list is malformed). `REGULAR_CODECS`
 * deliberately, not `ALL_CODECS`: the extra entries in the full list exist only
 * for specific configurations, and counting them is how you end up saying yes
 * to a decoder the renderer will not be given.
 *
 * ### What this does not cover
 *
 * Passthrough. A phone with no decoder at all can still send E-AC-3 to a
 * receiver that has one, over HDMI or a capable USB interface, and Media3 will
 * do that when `AudioCapabilities` says the *current route* supports the
 * encoding. That answer changes when headphones are plugged in, so it cannot be
 * cached, and it is false on a phone's own speaker and over Bluetooth — which
 * is where this app is listened to. The cost of leaving it out is that someone
 * running a phone into an AVR sees the toggle greyed out; the cost of putting
 * it in is a capability that flickers with the audio route. Left out knowingly.
 */
object DeviceCodecs {

    private const val TAG = "BitChord"

    /**
     * E-AC-3 JOC, and the plain E-AC-3 core it degrades to. Either decoder is
     * enough to say yes — see the note on fallback above. Spelt out rather
     * than taken from Media3's `MimeTypes`, so that everything outside
     * [media3Decoders] stays clear of the unstable API surface.
     */
    private val DOLBY_MIMES = listOf("audio/eac3-joc", "audio/eac3")

    @Volatile
    private var probed: Boolean? = null

    /**
     * Test seam, and the field override of last resort. The decision that
     * depends on this lives in [ModuleSource.unplayable] and is tested
     * directly; this exists so a caller can stand in for a device without a
     * decoder.
     */
    @Volatile
    internal var forced: Boolean? = null

    /**
     * Whether an E-AC-3 (JOC) stream would find a decoder on this device.
     *
     * Answered once per process and cached: the codec list cannot change while
     * the app is running, and the query is not free — a `MediaCodecList` walk
     * costs tens of milliseconds, on a path that is otherwise holding up audio.
     */
    val playsDolbyAtmos: Boolean
        get() = forced ?: probed ?: probe().also { probed = it }

    /**
     * Yes on anything that cannot be established.
     *
     * A probe that fails outright is exotic and an unsupported device is not,
     * but the cost of the two mistakes is not symmetric: guessing "no" on a
     * capable flagship silently drops it from the tier it is paying for and
     * greys out the switch that would restore it, while guessing "yes" only
     * restores the behaviour that shipped before any of this existed. So the
     * uncertain case keeps the old behaviour.
     */
    private fun probe(): Boolean {
        val viaMedia3 = media3Decoders()
        if (viaMedia3 != null) {
            TrackLog.d(TAG, "Dolby Atmos decoders (Media3): ${viaMedia3.ifEmpty { "none" }}")
            return viaMedia3.isNotEmpty()
        }
        return runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
                !info.isEncoder && info.supportedTypes.any {
                    it.lowercase(Locale.ROOT) in DOLBY_MIMES
                }
            }.map { it.name }
        }.onSuccess {
            TrackLog.d(TAG, "Dolby Atmos decoders (platform): ${it.ifEmpty { "none" }}")
        }.map {
            it.isNotEmpty()
        }.getOrElse {
            TrackLog.w(TAG, "could not read the codec list; assuming Dolby Atmos plays — ${it.message}")
            true
        }
    }

    /**
     * The decoders Media3 itself would offer the renderer for an Atmos stream,
     * by name, or null if the query failed and the caller should fall back to
     * the platform's own list.
     *
     * Isolated so the unstable-API surface is one function wide.
     */
    @UnstableApi
    private fun media3Decoders(): List<String>? = runCatching {
        DOLBY_MIMES.flatMap { MediaCodecUtil.getDecoderInfos(it, false, false) }
            .map { it.name }
            .distinct()
    }.getOrElse {
        TrackLog.w(TAG, "Media3 could not enumerate Dolby decoders — ${it.message}")
        null
    }
}
