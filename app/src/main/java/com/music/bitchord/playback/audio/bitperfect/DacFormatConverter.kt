/*
 * Copyright (C) 2026 BitChord Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.music.bitchord.playback.audio.bitperfect

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Fits a track to a DAC that cannot take it as it is: a 96 kHz/24-bit file on
 * a DAC that stops at 48 kHz/16-bit. This is the one place on the exclusive
 * path where samples change, so a stream through here is never reported as
 * bit-perfect.
 *
 * The alternative is not an untouched track but Android's mixer, which does
 * the same conversion at a quality nobody chose, along with everything else
 * it does. Rates are converted by a windowed-sinc filter, and whatever is
 * rounded to the DAC's depth gets TPDF dither. Silence stays digital silence,
 * so a DAC that mutes on it still does.
 */
internal class DacFormatConverter(
    private val encoding: Int,
    private val channels: Int,
    inputRate: Int,
    outputRate: Int,
    validBits: Int,
    private val containerBits: Int,
) {
    private val inFrameBytes = BitPerfectPcm.bytesPerSample(encoding) * channels
    private val outFrameBytes = containerBits / 8 * channels
    private val resampler = if (inputRate != outputRate) Resampler(inputRate, outputRate, channels) else null
    private val frame = DoubleArray(channels)

    /** A fixed seed: dither needs no secrecy, and a repeated track then renders identically. */
    private val random = Random(0)
    private val scale = (1L shl (validBits - 1)).toDouble()
    private val leftShift = 32 - validBits

    /** Converts until [input] has no whole frame left or [output] has no room for one. */
    fun process(input: ByteBuffer, output: ByteBuffer) {
        input.order(BitPerfectPcm.byteOrder(encoding))
        output.order(ByteOrder.LITTLE_ENDIAN)
        val resampler = resampler
        if (resampler == null) {
            while (input.remaining() >= inFrameBytes && output.remaining() >= outFrameBytes) {
                readFrame(input)
                writeFrame(output)
            }
            return
        }
        while (true) {
            while (resampler.ready) {
                if (output.remaining() < outFrameBytes) return
                resampler.pull(frame)
                writeFrame(output)
            }
            if (input.remaining() < inFrameBytes) return
            readFrame(input)
            resampler.push(frame)
        }
    }

    /**
     * After the last input: writes the frames still inside the filter, which
     * otherwise would cut off the end of the track. Returns true once none are left.
     */
    fun drain(output: ByteBuffer): Boolean {
        val resampler = resampler ?: return true
        output.order(ByteOrder.LITTLE_ENDIAN)
        while (!resampler.drained) {
            while (!resampler.ready) resampler.pushSilence()
            if (output.remaining() < outFrameBytes) return false
            resampler.pull(frame)
            writeFrame(output)
        }
        return true
    }

    fun reset() {
        resampler?.reset()
    }

    private fun readFrame(input: ByteBuffer) {
        for (channel in 0 until channels) frame[channel] = BitPerfectPcm.readNormalized(input, encoding)
    }

    private fun writeFrame(output: ByteBuffer) {
        for (channel in 0 until channels) {
            val sample = frame[channel]
            val scaled = if (sample == 0.0) 0.0 else sample * scale + random.nextDouble() - random.nextDouble()
            val rounded = floor(scaled + 0.5).coerceIn(-scale, scale - 1)
            BitPerfectPcm.putSample(output, (rounded.toLong() shl leftShift).toInt(), containerBits)
        }
    }

    /**
     * A polyphase windowed-sinc filter over the exact rational ratio, so the
     * output cannot drift against the track however long it runs. The filter
     * is centred on each output sample: output frame k is the input at time
     * k / outputRate, with no delay for the sink's clock to account for.
     */
    private class Resampler(inputRate: Int, outputRate: Int, private val channels: Int) {
        private val divisor = gcd(inputRate, outputRate)
        private val upFactor = outputRate / divisor
        private val downFactor = inputRate / divisor

        /** Input frames either side of an output frame. A lower output rate needs a proportionally longer filter. */
        private val halfTaps = ceil(ZERO_CROSSINGS * maxOf(1.0, downFactor.toDouble() / upFactor)).toInt()
        private val taps = halfTaps * 2
        private val table = buildTable()

        /** Interleaved input; the frames from [start] are the ones the next output reads. */
        private val history = DoubleArray(taps * 2 * channels)
        private var filled = 0
        private var start = 0
        private var phase = 0
        private var pushed = 0L
        private var pulled = 0L

        init {
            reset()
        }

        val ready: Boolean get() = start + taps <= filled

        /** Whether every output frame the input so far accounts for has been pulled. */
        val drained: Boolean
            get() = pulled >= (pushed * upFactor + downFactor - 1) / downFactor

        fun push(frame: DoubleArray) {
            pushed++
            append(frame)
        }

        fun pushSilence() {
            makeRoom()
            history.fill(0.0, filled * channels, (filled + 1) * channels)
            filled++
        }

        fun pull(into: DoubleArray) {
            val row = phase * taps
            for (channel in 0 until channels) {
                var sum = 0.0
                var at = start * channels + channel
                for (tap in 0 until taps) {
                    sum += table[row + tap] * history[at]
                    at += channels
                }
                into[channel] = sum * HEADROOM
            }
            phase += downFactor
            start += phase / upFactor
            phase %= upFactor
            pulled++
        }

        fun reset() {
            // The first output is centred on the first input, with silence before it.
            history.fill(0.0)
            filled = halfTaps - 1
            start = 0
            phase = 0
            pushed = 0
            pulled = 0
        }

        private fun append(frame: DoubleArray) {
            makeRoom()
            frame.copyInto(history, filled * channels)
            filled++
        }

        private fun makeRoom() {
            if ((filled + 1) * channels <= history.size) return
            history.copyInto(history, 0, start * channels, filled * channels)
            filled -= start
            start = 0
        }

        private fun buildTable(): DoubleArray {
            val result = DoubleArray(upFactor * taps)
            // In cycles per input sample, below whichever Nyquist is lower.
            val cutoff = 0.5 * CUTOFF * min(1.0, upFactor.toDouble() / downFactor)
            val windowDivisor = besselI0(KAISER_BETA)
            for (row in 0 until upFactor) {
                val offset = row.toDouble() / upFactor
                var sum = 0.0
                for (tap in 0 until taps) {
                    val distance = offset - (tap - (halfTaps - 1))
                    val position = distance / halfTaps
                    val window = besselI0(KAISER_BETA * sqrt(maxOf(0.0, 1 - position * position))) / windowDivisor
                    val value = sinc(2 * cutoff * distance) * window
                    result[row * taps + tap] = value
                    sum += value
                }
                // Each phase passes DC at exactly unity.
                for (tap in 0 until taps) result[row * taps + tap] /= sum
            }
            return result
        }

        private fun sinc(x: Double): Double = if (x == 0.0) 1.0 else sin(PI * x) / (PI * x)

        private fun besselI0(x: Double): Double {
            var sum = 1.0
            var term = 1.0
            var k = 1
            while (term > sum * 1e-12) {
                term *= (x / (2 * k)) * (x / (2 * k))
                sum += term
                k++
            }
            return sum
        }
    }

    companion object {
        private const val ZERO_CROSSINGS = 64

        /** Puts the filter's transition band just under the lower Nyquist. */
        private const val CUTOFF = 0.96

        /** About 100 dB of stopband. */
        private const val KAISER_BETA = 10.06

        /** -1 dB: a loud master peaks above full scale between its samples, and a filter reveals those peaks. */
        private const val HEADROOM = 0.891

        private const val MAX_FILTER_PHASES = 4096

        /** Whether the ratio is simple enough to tabulate; 44.1 to 48 kHz needs 160 phases. */
        fun canResample(inputRate: Int, outputRate: Int): Boolean =
            inputRate > 0 && outputRate > 0 && outputRate / gcd(inputRate, outputRate) <= MAX_FILTER_PHASES

        private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    }
}
