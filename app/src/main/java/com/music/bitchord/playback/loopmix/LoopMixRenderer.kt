package com.music.bitchord.playback.loopmix

import android.util.Log

/**
 * The native loop-mix renderer (native/loopmix/): two Signalsmith Stretch
 * decks, each a decoded region of one song, looped on whole bars and following
 * the mix's tempo ramp beat by beat, mixed to one stereo stream.
 *
 * Built and filled on a worker thread; after [prepare] only [render] (the
 * playback thread, inside the outgoing sink) and the small setters touch it.
 */
class LoopMixRenderer(val sampleRate: Int) : AutoCloseable {

    @Volatile
    private var handle: Long = if (available) nativeCreate(sampleRate) else 0L

    val isValid: Boolean get() = handle != 0L

    /** One deck: the region (planar, at [sampleRate]), its loop, and its beat knots. */
    class DeckSetup(
        val left: FloatArray,
        val right: FloatArray,
        /** Track seconds of sample 0. */
        val regionStart: Double,
        /** Loop bounds in region samples. */
        val loopStart: Long,
        val loopEnd: Long,
        /** Unwrapped region-sample position at which the loop lets go; huge for never. */
        val releaseAt: Double,
        /** Mix frames (0 = t0) and the unwrapped region-sample position heard at each. */
        val knotFrames: DoubleArray,
        val knotPositions: DoubleArray,
        /** After the last knot: carry on at rate 1 (true) or stop. */
        val runOn: Boolean,
        /** Mix seconds after which the deck renders nothing. */
        val stopAt: Double,
        val preroll: Double,
        val level: Float,
    )

    fun setDeck(index: Int, deck: DeckSetup) = nativeSetDeck(
        handle, index, deck.left, deck.right, deck.regionStart, deck.loopStart, deck.loopEnd, deck.releaseAt,
        deck.knotFrames, deck.knotPositions, deck.runOn, deck.stopAt, deck.preroll, deck.level,
    )

    /** Automation over mix seconds: [KIND_GAIN] (linear), [KIND_LOW_DB] (250 Hz shelf), [KIND_HIGH_PASS_HZ]. */
    fun setEnvelope(index: Int, kind: Int, times: DoubleArray, values: FloatArray, exponential: Boolean = false) =
        nativeSetEnvelope(handle, index, kind, times, values, exponential)

    fun prepare() = nativePrepare(handle)

    /** The earliest mix frame anything renders at; negative, before t0. */
    fun firstFrame(): Long = nativeFirstFrame(handle)

    /** Renders [frames] stereo frames from mix frame [frame] into [interleaved] at frame [offset]. */
    fun render(frame: Long, interleaved: FloatArray, offset: Int, frames: Int) =
        nativeRender(handle, frame, interleaved, offset, frames)

    /** Fade [index] out over 25 ms once it plays track second [trackSeconds] after its release. */
    fun setFadeOut(index: Int, trackSeconds: Double) = nativeSetFadeOut(handle, index, trackSeconds)

    /** Track seconds [index] played at the end of the last rendered block. */
    fun heard(index: Int): Double = nativeHeard(handle, index)

    override fun close() {
        val h = handle
        handle = 0L
        if (h != 0L) nativeDestroy(h)
    }

    companion object {
        const val OUT = 0
        const val IN = 1
        const val KIND_GAIN = 0
        const val KIND_LOW_DB = 1
        const val KIND_HIGH_PASS_HZ = 2

        val available: Boolean = runCatching { System.loadLibrary("bitchord_loopmix") }
            .onFailure { Log.w("BitChordLoopMix", "Loop mix renderer unavailable", it) }
            .isSuccess

        @JvmStatic private external fun nativeCreate(sampleRate: Int): Long
        @JvmStatic private external fun nativeDestroy(handle: Long)
        @JvmStatic private external fun nativeSetDeck(
            handle: Long, index: Int, left: FloatArray, right: FloatArray, regionStart: Double,
            loopStart: Long, loopEnd: Long, releaseAt: Double, knotFrames: DoubleArray, knotPositions: DoubleArray,
            runOn: Boolean, stopAt: Double, preroll: Double, level: Float,
        )
        @JvmStatic private external fun nativeSetEnvelope(
            handle: Long, index: Int, kind: Int, times: DoubleArray, values: FloatArray, exponential: Boolean,
        )
        @JvmStatic private external fun nativePrepare(handle: Long)
        @JvmStatic private external fun nativeFirstFrame(handle: Long): Long
        @JvmStatic private external fun nativeRender(handle: Long, frame: Long, interleaved: FloatArray, offset: Int, frames: Int)
        @JvmStatic private external fun nativeSetFadeOut(handle: Long, index: Int, trackSeconds: Double)
        @JvmStatic private external fun nativeHeard(handle: Long, index: Int): Double
    }
}
