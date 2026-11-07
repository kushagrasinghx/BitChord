package com.music.bitchord.playback

import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.UpgradeOffers
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.sources.SourceStream
import com.music.bitchord.data.sources.StreamFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The better copy of a track that started on YouTube, held for the listener
 * to ask for instead of swapped in.
 *
 * With [AppSettings.directSourceStartOnYouTube] on, a track whose addon answer
 * isn't ready when it has to start plays from YouTube — see
 * `PlaybackService.resolveDirectStartingOnYouTube`. The addon lookup runs on,
 * and whatever [QualityUpgrade] finds is parked here rather than handed to the
 * swap: a swap cuts the audio, and the point of the setting is that nothing
 * does unless the listener asks. The player shows the offer through
 * [UpgradeOffers]; a tap, or "Upgrade quality" in the player menu, swaps it in.
 */
object HeldUpgrade {

    private const val TAG = "BitChord"

    private val held = ConcurrentHashMap<String, SourceStream>()

    /** Tracks whose upgrade the listener asked for from the menu: theirs swaps in as usual. */
    private val byHand = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /** Whether a better copy found for [mediaId] is offered rather than swapped in. */
    fun holds(mediaId: String): Boolean =
        AppSettings.directSourceStartOnYouTube.value && DirectSourceGate.isActive() && mediaId !in byHand

    /** Offers [stream] for [mediaId], replacing any offer for another track. */
    fun offer(mediaId: String, stream: SourceStream) {
        held.keys.filter { it != mediaId }.forEach(::drop)
        held[mediaId] = stream
        val label = labelFor(stream.format)
        UpgradeOffers.publish(mediaId, label)
        TrackLog.d(TAG, "better copy of $mediaId found ($label); offered instead of swapped in", about = mediaId)
    }

    /** Whether [mediaId] has a copy on offer. */
    fun has(mediaId: String): Boolean = held.containsKey(mediaId)

    /** Takes the copy on offer for [mediaId], to swap it in. */
    fun take(mediaId: String): SourceStream? {
        val stream = held.remove(mediaId)
        UpgradeOffers.withdraw(mediaId)
        return stream
    }

    /** The listener asked for [mediaId]'s upgrade from the menu, so its result swaps in. */
    fun askedByHand(mediaId: String) {
        byHand += mediaId
    }

    /** Drops offers and hand requests for tracks that are no longer playing. Main thread. */
    fun onPlaying(audible: Set<String>) {
        held.keys.filter { it !in audible }.forEach(::drop)
        byHand.retainAll(audible)
    }

    private fun drop(mediaId: String) {
        held.remove(mediaId)
        UpgradeOffers.withdraw(mediaId)
    }

    /** What the offer button names: "Dolby Atmos", "Hi-Res", the lossless codec, or the bitrate. */
    internal fun labelFor(format: StreamFormat): String = when {
        format.isDolbyAtmos -> "Dolby Atmos"
        format.isLossless == true ->
            if ((format.bitDepth ?: 16) > 16 || (format.sampleRateHz ?: 44_100) > 48_000) {
                "Hi-Res"
            } else {
                format.codec?.uppercase(Locale.ROOT)?.takeIf { it.length <= 5 } ?: "Lossless"
            }
        format.kbps != null -> "${format.kbps} kbps"
        else -> format.summary
    }
}
