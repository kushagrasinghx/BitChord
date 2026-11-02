/*
 * Copyright (C) 2026 Kushagra Singh
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.music.bitchord.playback.smart

import android.media.MediaDataSource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * The DJ-style three-band waveform the Automix popup draws: lows, mids and
 * highs as separate peak envelopes over a short region of one track.
 *
 * Not from the stored analysis — its energy curve is a second per point, a
 * smear at the scale of a beat — but decoded fresh from the region the blend
 * covers. Streamed and reduced as it decodes, so the PCM is never held: a
 * transition is exactly when [TrackAnalyzer] may be at the heap ceiling.
 */
object MixWaveform {

    /** [decodedFrom] is where the decoder really started, for telling a working seek from one that read the file from the top. */
    class Bands(val low: FloatArray, val mid: FloatArray, val high: FloatArray, val decodedFrom: Double = 0.0)

    /** Below this is the bass layer. */
    private const val LOW_HZ = 200.0

    /** Above this is the highs layer; between the two, mids. */
    private const val HIGH_HZ = 2_500.0

    /** Audio decoded ahead of the window to settle the filters. */
    private const val WARM_UP_SECONDS = 0.05

    fun build(
        source: MediaDataSource,
        fromSeconds: Double,
        toSeconds: Double,
        bins: Int,
        abort: () -> Boolean = { false },
    ): Bands? {
        if (bins <= 0 || toSeconds <= fromSeconds) return null
        val low = FloatArray(bins)
        val mid = FloatArray(bins)
        val high = FloatArray(bins)
        val span = toSeconds - fromSeconds
        // Two one-pole stages per split: a gentle 12 dB/octave, which is all a
        // picture needs and keeps this cheap enough to run at full rate.
        var lowA = 0f
        var highA = 0f
        var configuredRate = 0
        var l1 = 0f
        var l2 = 0f
        var h1 = 0f
        var h2 = 0f
        // Filters settle in a few ms; audio further ahead of the window than
        // this is skipped whole rather than filtered for nothing.
        val warmFrom = fromSeconds - WARM_UP_SECONDS
        val binsPerSecond = bins / span
        val decoded = AudioDecoder.streamRegion(BlockBuffered(source), max(0.0, warmFrom), toSeconds, abort) { samples, at, rate ->
            if (rate <= 0) return@streamRegion
            if (at + samples.size.toDouble() / rate < warmFrom) return@streamRegion
            if (rate != configuredRate) {
                configuredRate = rate
                lowA = (1.0 - exp(-2.0 * PI * LOW_HZ / rate)).toFloat()
                highA = (1.0 - exp(-2.0 * PI * HIGH_HZ / rate)).toFloat()
            }
            val firstBin = (at - fromSeconds) * binsPerSecond
            val binStep = binsPerSecond / rate
            for (index in samples.indices) {
                val x = samples[index]
                l1 += lowA * (x - l1)
                l2 += lowA * (l1 - l2)
                h1 += highA * (x - h1)
                h2 += highA * (h1 - h2)
                val position = firstBin + index * binStep
                if (position < 0.0) continue
                val bin = position.toInt()
                if (bin >= bins) break
                val lo = abs(l2)
                val mi = abs(h2 - l2)
                val hi = abs(x - h2)
                if (lo > low[bin]) low[bin] = lo
                if (mi > mid[bin]) mid[bin] = mi
                if (hi > high[bin]) high[bin] = hi
            }
        } ?: return null
        normalize(low, 1f)
        normalize(mid, 0.82f)
        normalize(high, 0.5f)
        return Bands(low, mid, high, decodedFrom = decoded.second)
    }

    /**
     * Reads [source] a block at a time and keeps the last few blocks.
     *
     * Seeking into a WebM, the extractor walks the file in a great many small
     * reads, and each one that reaches the cache pays that path's whole
     * overhead: a window three minutes into a song took seven seconds, nearly
     * all of it spent getting to the window rather than decoding it.
     */
    private class BlockBuffered(private val source: MediaDataSource) : MediaDataSource() {
        private val blocks = object : LinkedHashMap<Long, ByteArray>(BLOCKS, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?) = size > BLOCKS
        }
        private val length by lazy { source.size }

        override fun getSize(): Long = length

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (size <= 0) return 0
            val total = length
            if (total >= 0 && position >= total) return -1
            var done = 0
            while (done < size) {
                val at = position + done
                if (total >= 0 && at >= total) break
                val index = at / BLOCK_BYTES
                val block = blocks[index] ?: load(index) ?: break
                val within = (at - index * BLOCK_BYTES).toInt()
                if (within >= block.size) break
                val n = minOf(size - done, block.size - within)
                System.arraycopy(block, within, buffer, offset + done, n)
                done += n
            }
            return if (done == 0) -1 else done
        }

        private fun load(index: Long): ByteArray? {
            val block = ByteArray(BLOCK_BYTES)
            var filled = 0
            while (filled < BLOCK_BYTES) {
                val n = source.readAt(index * BLOCK_BYTES + filled, block, filled, BLOCK_BYTES - filled)
                if (n <= 0) break
                filled += n
            }
            if (filled == 0) return null
            val loaded = if (filled == BLOCK_BYTES) block else block.copyOf(filled)
            blocks[index] = loaded
            return loaded
        }

        override fun close() = source.close()

        private companion object {
            const val BLOCK_BYTES = 256 * 1024
            const val BLOCKS = 8
        }
    }

    /**
     * Scales a band so its loud passages reach [ceiling], judged by a high
     * percentile rather than the single biggest peak, which one transient would
     * otherwise set for the whole picture.
     */
    private fun normalize(band: FloatArray, ceiling: Float) {
        val sorted = band.filter { it > 0f }.sorted()
        if (sorted.isEmpty()) return
        val reference = sorted[(sorted.size * 0.97).toInt().coerceAtMost(sorted.size - 1)]
        if (reference <= 1e-6f) return
        for (index in band.indices) band[index] = (band[index] / reference * ceiling).coerceAtMost(ceiling * 1.08f)
    }
}
