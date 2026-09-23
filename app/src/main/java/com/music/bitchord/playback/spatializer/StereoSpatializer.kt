package com.music.bitchord.playback.spatializer

import kotlin.math.pow

/**
 * Stereo Spatialization for headphones. Upmix to 5.1 ([SurroundUpmixer]), then convolve the six channels with
 * binaural room responses ([PartitionedConvolver], [SpeakerResponses]) so they play as virtual speakers in a room,
 * then the output stage (headroom + [PeakLimiter]).
 *
 * Works in place on interleaved stereo Float32 of any block length, so it slots into the precision sink's DSP
 * chain like the other processors. Internally it runs one upmixer hop at a time; the output is the input delayed by
 * [latencyFrames] (about 48 ms), and after the last input sample [tailFrames] more frames of silence flush out the
 * delayed audio and the room's reverberant tail.
 *
 * @param outputStage false only for tests that check the raw upmix + convolution
 */
class StereoSpatializer(
    val sampleRate: Int,
    responses: SpeakerResponses,
    private val outputStage: Boolean = true,
) {
    init {
        require(responses.sampleRate == sampleRate) {
            "responses are for ${responses.sampleRate} Hz, stream is $sampleRate Hz"
        }
    }

    private val tables = UpmixerTables.forRate(sampleRate)
    private val upmixer = SurroundUpmixer(tables)
    val hop: Int = upmixer.hop
    private val convolver = PartitionedConvolver(responses.spectra(hop))
    private val limiter = if (outputStage) PeakLimiter(sampleRate) else null
    private val gain = if (outputStage) 10.0.pow(HEADROOM_DB / 20.0).toFloat() else 1f

    val latencyFrames: Int = upmixer.latencyFrames + (limiter?.latencyFrames ?: 0)
    val tailFrames: Int = latencyFrames + responses.taps

    private val inL = FloatArray(hop)
    private val inR = FloatArray(hop)
    private var fill = 0
    private val six = Array(6) { FloatArray(hop) }
    private val earL = FloatArray(hop)
    private val earR = FloatArray(hop)

    /** Output queue (interleaved), primed with one hop of silence: the upmixer's input buffering. */
    private val queue = FloatArray(4 * hop)
    private var qRead = 0
    private var qCount = 0

    init { reset() }

    fun reset() {
        upmixer.reset()
        convolver.reset()
        limiter?.reset()
        fill = 0
        queue.fill(0f)
        qRead = 0
        qCount = hop
    }

    /** [samples]: interleaved stereo, [frames] frames, processed in place. */
    fun process(samples: FloatArray, frames: Int) {
        val cap = queue.size / 2
        for (i in 0 until frames) {
            // a single NaN or infinity would otherwise stay in the upmixer's smoothed state until the next reset
            val l = samples[2 * i]
            val r = samples[2 * i + 1]
            inL[fill] = if (l.isFinite()) l else 0f
            inR[fill] = if (r.isFinite()) r else 0f
            if (++fill == hop) {
                fill = 0
                upmixer.processHop(inL, inR, six)
                convolver.process(six, earL, earR)
                var w = (qRead + qCount) % cap
                for (k in 0 until hop) {
                    queue[2 * w] = earL[k] * gain
                    queue[2 * w + 1] = earR[k] * gain
                    w = if (w + 1 == cap) 0 else w + 1
                }
                qCount += hop
            }
            samples[2 * i] = queue[2 * qRead]
            samples[2 * i + 1] = queue[2 * qRead + 1]
            qRead = if (qRead + 1 == cap) 0 else qRead + 1
            qCount--
        }
        limiter?.process(samples, frames)
    }

    companion object {
        /** [latencyFrames] of a spatializer at [sampleRate] (with the output stage), without building one. */
        fun latencyFramesFor(sampleRate: Int): Int =
            UpmixerTables.frameSizeFor(sampleRate) + PeakLimiter.latencyFramesFor(sampleRate)

        /**
         * Builds what a spatializer at [sampleRate] needs (upmixer tables, the responses and their partition
         * spectra) so the first spatialized block doesn't pay for it on the audio thread. Safe to call from any
         * thread, any number of times.
         */
        fun warmUp(sampleRate: Int) {
            UpmixerTables.forRate(sampleRate)
            SpeakerResponseStore.forRate(sampleRate)?.spectra(UpmixerTables.frameSizeFor(sampleRate) / 2)
        }

        /**
         * Output trim. The upmix + room raise peaks by up to ~9 dB over the stereo source (mostly bass, through the
         * LFE path) while loudness rises ~1.5 dB. With the limiter's ceiling at -0.3 dBFS, -8.3 dB leaves it idle on
         * most music and acting briefly on loud, dense masters, where it would otherwise pull the bass down with
         * every peak. It plays ~6 dB quieter than the stereo source.
         */
        const val HEADROOM_DB = -8.3
    }
}
