package com.music.bitchord.playback.smart

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LoudnessMetrics(
    val integratedLufs: Double,
    val shortTermLufs: Double,
    val truePeakDbtp: Double,
)

/** Lightweight local BS.1770/R128 approximation; no model or retained PCM. */
fun measureLoudness(samples: FloatArray, sampleRate: Double): LoudnessMetrics? {
    if (samples.isEmpty() || sampleRate <= 0) return null
    val weighted = samples.copyOf()
    Biquad.highPass(sampleRate, 38.0, 0.5).process(weighted)
    Biquad.highShelf(sampleRate, 1_681.0, 4.0, 0.707).process(weighted)

    fun lufs(meanSquare: Double): Double = -0.691 + 10.0 * log10(meanSquare.coerceAtLeast(1e-12))
    val block = max(1, (sampleRate * 0.4).toInt())
    val hop = max(1, block / 4)
    val powers = ArrayList<Double>()
    var at = 0
    while (at < weighted.size) {
        val end = min(weighted.size, at + block)
        if (end - at < block / 2) break
        var power = 0.0
        for (i in at until end) power += weighted[i] * weighted[i]
        power /= (end - at)
        if (lufs(power) >= -70.0) powers += power
        at += hop
    }
    if (powers.isEmpty()) return null
    val absoluteMean = powers.average()
    val relativeGate = lufs(absoluteMean) - 10.0
    val gated = powers.filter { lufs(it) >= relativeGate }
    val integrated = lufs((gated.ifEmpty { powers }).average())

    val shortWindow = max(1, (sampleRate * 3.0).toInt())
    var shortPower = 0.0
    var shortPeak = 1e-12
    for (i in weighted.indices) {
        val value = weighted[i].toDouble()
        shortPower += value * value
        if (i >= shortWindow) {
            val old = weighted[i - shortWindow].toDouble()
            shortPower -= old * old
        }
        if (i >= shortWindow - 1) shortPeak = max(shortPeak, shortPower / shortWindow)
    }

    // Four-point linear oversampling is intentionally bounded and allocation-free.
    // It catches the common inter-sample overshoot while keeping the v2 pass tiny.
    var peak = 0.0
    for (i in 0 until samples.lastIndex) {
        val a = samples[i].toDouble()
        val b = samples[i + 1].toDouble()
        for (phase in 0..3) peak = max(peak, abs(a + (b - a) * phase / 4.0))
    }
    peak = max(peak, abs(samples.last().toDouble()))
    return LoudnessMetrics(integrated, lufs(shortPeak), 20.0 * log10(peak.coerceAtLeast(1e-9)))
}

private class Biquad(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    fun process(samples: FloatArray) {
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        for (i in samples.indices) {
            val x = samples[i].toDouble()
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            samples[i] = y.toFloat()
            x2 = x1; x1 = x; y2 = y1; y1 = y
        }
    }

    companion object {
        fun highPass(rate: Double, frequency: Double, q: Double): Biquad {
            val w = 2 * PI * frequency / rate
            val alpha = sin(w) / (2 * q)
            val a0 = 1 + alpha
            return Biquad(
                (1 + cos(w)) / 2 / a0,
                -(1 + cos(w)) / a0,
                (1 + cos(w)) / 2 / a0,
                -2 * cos(w) / a0,
                (1 - alpha) / a0,
            )
        }

        fun highShelf(rate: Double, frequency: Double, gainDb: Double, q: Double): Biquad {
            val a = 10.0.pow(gainDb / 40)
            val w = 2 * PI * frequency / rate
            val alpha = sin(w) / (2 * q)
            val root = 2 * sqrt(a) * alpha
            val a0 = (a + 1) - (a - 1) * cos(w) + root
            return Biquad(
                a * ((a + 1) + (a - 1) * cos(w) + root) / a0,
                -2 * a * ((a - 1) + (a + 1) * cos(w)) / a0,
                a * ((a + 1) + (a - 1) * cos(w) - root) / a0,
                2 * ((a - 1) - (a + 1) * cos(w)) / a0,
                ((a + 1) - (a - 1) * cos(w) - root) / a0,
            )
        }
    }
}

/** Bar/phrase segmentation from the curves already produced by the native analyzer. */
fun inferMusicalSections(
    duration: Double,
    phraseBoundaries: List<Double>,
    energyCurve: List<EnergySample>,
    vocalMask: List<Double>,
): Pair<List<MusicalSection>, Double> {
    if (duration <= 0 || energyCurve.size < 4) return emptyList<MusicalSection>() to 0.0
    val boundaries = (listOf(0.0) + phraseBoundaries + duration)
        .filter { it.isFinite() && it in 0.0..duration }
        .distinct().sorted()
        .let { if (it.size >= 3) it else listOf(0.0, duration * .25, duration * .5, duration * .75, duration) }
    val energies = energyCurve.map { it.energy }.filter { it.isFinite() }.sorted()
    val floor = energies[(energies.lastIndex * .1).toInt()]
    val ceiling = energies[(energies.lastIndex * .9).toInt()].coerceAtLeast(floor + 1e-9)
    fun norm(value: Double) = ((value - floor) / (ceiling - floor)).coerceIn(0.0, 1.0)

    val raw = boundaries.zipWithNext().mapIndexedNotNull { index, (start, end) ->
        if (end - start < 0.5) return@mapIndexedNotNull null
        val points = energyCurve.withIndex().filter { it.value.time in start..end }
        val energy = points.map { it.value.energy }.average().takeIf { it.isFinite() } ?: floor
        val vocal = if (vocalMask.size == energyCurve.size && points.isNotEmpty()) {
            points.map { vocalMask[it.index] }.average()
        } else 0.5
        val position = (start + end) / 2 / duration
        val level = norm(energy)
        val previous = rawEnergy(boundaries.getOrNull(index - 1), start, energyCurve)?.let(::norm) ?: level
        val next = rawEnergy(end, boundaries.getOrNull(index + 2), energyCurve)?.let(::norm) ?: level
        val type = when {
            index == 0 && position < .18 -> MusicalSectionType.INTRO
            index == boundaries.size - 2 && position > .80 -> MusicalSectionType.OUTRO
            level >= .78 && vocal >= .58 -> MusicalSectionType.CHORUS
            level >= .82 -> MusicalSectionType.DROP
            next - level >= .18 -> MusicalSectionType.BUILD
            level <= .28 && previous - level >= .12 -> MusicalSectionType.BREAK
            vocal >= .55 -> MusicalSectionType.VERSE
            else -> MusicalSectionType.UNKNOWN
        }
        val confidence = (0.48 + abs(level - .5) * .45 + abs(vocal - .5) * .2).coerceIn(0.0, 0.92)
        MusicalSection(start, end, type, confidence, level, vocal.coerceIn(0.0, 1.0))
    }
    if (raw.isEmpty()) return emptyList<MusicalSection>() to 0.0
    val merged = mutableListOf<MusicalSection>()
    for (section in raw) {
        val last = merged.lastOrNull()
        if (last != null && last.type == section.type) {
            val leftWeight = last.end - last.start
            val rightWeight = section.end - section.start
            merged[merged.lastIndex] = last.copy(
                end = section.end,
                confidence = max(last.confidence, section.confidence),
                energy = (last.energy * leftWeight + section.energy * rightWeight) / (leftWeight + rightWeight),
                vocalActivity = (last.vocalActivity * leftWeight + section.vocalActivity * rightWeight) /
                    (leftWeight + rightWeight),
            )
        } else merged += section
    }
    val known = merged.count { it.type != MusicalSectionType.UNKNOWN }.toDouble() / merged.size
    val boundaryEvidence = (phraseBoundaries.size / 6.0).coerceIn(0.0, 1.0)
    return merged to (known * .7 + boundaryEvidence * .3).coerceIn(0.0, 1.0)
}

private fun rawEnergy(start: Double?, end: Double?, curve: List<EnergySample>): Double? {
    if (start == null || end == null || end <= start) return null
    return curve.filter { it.time in start..end }.map { it.energy }.average().takeIf { it.isFinite() }
}

/** Session loudness correction, capped to ±6 dB and biased toward attenuation. */
fun loudnessGainDb(trackLufs: Double?, sessionTargetLufs: Double): Double {
    if (trackLufs == null || !trackLufs.isFinite()) return 0.0
    val raw = sessionTargetLufs - trackLufs
    return if (raw > 0) min(raw, 6.0) else max(raw, -6.0)
}

/** Additional overlap headroom required to remain below -1 dBTP. */
fun overlapHeadroomDb(outPeakDbtp: Double?, inPeakDbtp: Double?, outGainDb: Double, inGainDb: Double): Double {
    val out = (outPeakDbtp ?: -3.0) + outGainDb
    val incoming = (inPeakDbtp ?: -3.0) + inGainDb
    val summed = 20 * log10(10.0.pow(out / 20) + 10.0.pow(incoming / 20))
    return min(0.0, -1.0 - summed)
}
