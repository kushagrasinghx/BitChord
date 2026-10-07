package com.music.bitchord.playback

import com.music.bitchord.playback.audio.AudioBlock
import kotlin.math.abs
import kotlin.math.exp

/**
 * Loudness normalization, applied inside one player's own DSP chain, plus the
 * peak limiter that keeps the result — and a crossfade built on it — from
 * clipping.
 *
 * ## Why per player, not a session effect
 *
 * This used to be a single platform `LoudnessEnhancer` on the audio session
 * both players share. One effect means one gain, so at every crossfade the
 * outgoing track was re-levelled to the *incoming* track's figure the moment
 * the blend began: a quiet-mastered song leaving was suddenly boosted, a loud
 * one suddenly ducked, for the whole length of the overlap. Here each player
 * carries its own track's gain, so the two sides of a blend are each levelled
 * for the song they are actually playing.
 *
 * ## Which track
 *
 * The gain is read lazily through [gainFor] on the audio thread rather than
 * pushed in as a number. A YouTube figure often resolves a few seconds after
 * the track starts (the substitute lookup wins the race), and a pushed value
 * needed a retry timer to pick it up; a lazy read picks it up on the next
 * block, glided rather than stepped.
 *
 * [mediaId] is the track this chain is processing. At a gapless boundary the
 * sink calls [onStreamBoundary], which promotes [nextMediaId] at the exact
 * sample the new track begins — the service's own track-change callback
 * fires when the track becomes *audible*, which is an AudioTrack buffer too
 * late for a chain that runs ahead of the speaker.
 *
 * ## The limiter
 *
 * Only engages when a positive normalization gain could push a track past
 * full scale. Instant attack, a smooth release, no lookahead — a peak
 * catcher for the odd over, not a mastering limiter, and never asked to do
 * more than that.
 *
 * ## Blend trim
 *
 * [setBlendTrim] is what the crossfade drives, and it is a plain gain, not a
 * lower limiter ceiling. Two tracks faded equal-power sum to more than either
 * alone — up to 1.41 at the midpoint — so a little headroom is taken off both
 * while they overlap. It was first done by limiting each side's peaks to
 * `1 / (inGain + outGain)`, which on modern masters (peaking near 0 dBFS
 * almost continuously) meant limiting both songs by 3 dB for the whole blend:
 * audible pumping and grit, heard as the two songs clashing. A smooth trim of
 * `1 / sqrt(inGain + outGain)` costs at most 1.5 dB at the midpoint, keeps
 * the sum's peaks within 1.19 — rare enough to leave to the mixer — and
 * changes no waveform's shape.
 */
class LoudnessProcessor {

    /** Linear gain for a media id: 1 when unknown or normalization is off. Must be thread-safe. */
    @Volatile
    var gainFor: (String) -> Float = { 1f }

    /** The track this chain is currently processing. */
    @Volatile
    var mediaId: String? = null

    /** The track that follows [mediaId] gaplessly on this player, if any. */
    @Volatile
    var nextMediaId: String? = null

    @Volatile
    private var trimTarget = 1f

    private var sampleRate = 0
    private var channelCount = 0
    private var currentGain = 1f
    private var currentTrim = 1f
    private var reduction = 1f
    private var glideCoef = 1f
    private var releaseCoef = 1f

    /** Set by anything that breaks continuity, so the next block jumps rather than glides. */
    @Volatile
    private var snap = true

    /** Points this chain at a track, and the one after it. Called from the app thread. */
    fun track(mediaId: String?, nextMediaId: String?) {
        this.mediaId = mediaId
        this.nextMediaId = nextMediaId
    }

    /**
     * Headroom taken off this track while it overlaps another: 1 between
     * transitions, a little lower during one. Glided on the audio thread.
     */
    fun setBlendTrim(value: Float) {
        trimTarget = value.coerceIn(MIN_TRIM, 1f)
    }

    fun onStreamBoundary() {
        nextMediaId?.let { mediaId = it }
        snap = true
    }

    fun configure(sampleRate: Int, channelCount: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        glideCoef = coefficient(GLIDE_SECONDS, sampleRate)
        releaseCoef = coefficient(RELEASE_SECONDS, sampleRate)
        snap = true
    }

    fun flush() {
        reduction = 1f
        snap = true
    }

    fun reset() {
        sampleRate = 0
        channelCount = 0
        reduction = 1f
        snap = true
    }

    fun process(block: AudioBlock) {
        val frames = block.frameCount
        val channels = channelCount
        if (frames == 0 || channels < 1 || sampleRate <= 0) return

        val targetGain = targetGain()
        val targetTrim = trimTarget
        if (snap) {
            currentGain = targetGain
            currentTrim = targetTrim
            snap = false
        }
        // Parked: nothing to scale and nothing that could clip. Returning before
        // touching a sample keeps the chain bit-exact while normalization is off
        // or the track happens to need no correction.
        if (targetGain == 1f && targetTrim >= 1f &&
            abs(currentGain - 1f) < SETTLED && currentTrim >= 1f - SETTLED && reduction >= 1f - SETTLED
        ) {
            currentGain = 1f
            currentTrim = 1f
            reduction = 1f
            return
        }

        val samples = block.samples
        var gain = currentGain
        var trim = currentTrim
        var gr = reduction
        var index = 0
        for (frame in 0 until frames) {
            gain += (targetGain - gain) * glideCoef
            trim += (targetTrim - trim) * glideCoef
            val scale = gain * trim
            var peak = 0f
            for (channel in 0 until channels) {
                val magnitude = abs(samples[index + channel])
                if (magnitude > peak) peak = magnitude
            }
            peak *= scale
            val wanted = if (peak > LIMIT) LIMIT / peak else 1f
            gr = if (wanted < gr) wanted else gr + (wanted - gr) * releaseCoef
            val applied = scale * gr
            for (channel in 0 until channels) {
                samples[index + channel] *= applied
            }
            index += channels
        }
        currentGain = gain
        currentTrim = trim
        reduction = gr
    }

    private fun targetGain(): Float = mediaId?.let { runCatching { gainFor(it) }.getOrNull() } ?: 1f

    private fun coefficient(seconds: Double, rate: Int): Float =
        if (rate <= 0) 1f else (1.0 - exp(-1.0 / (seconds * rate))).toFloat()

    companion object {
        /** Just under full scale, so float and 16-bit outputs round the same way. */
        private const val LIMIT = 0.985f

        /** Never let a trim request take more than 6 dB off a track. */
        private const val MIN_TRIM = 0.5f

        /** Time constant for gain and trim changes: long enough not to zipper. */
        private const val GLIDE_SECONDS = 0.04

        /** How quickly the limiter lets go after a peak. */
        private const val RELEASE_SECONDS = 0.08

        private const val SETTLED = 1e-4f
    }
}
