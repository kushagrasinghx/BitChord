package app.stemsep.dsp

import kotlin.math.PI
import kotlin.math.cos

internal fun periodicHann(size: Int): FloatArray {
    require(size > 0)
    return FloatArray(size) { i ->
        (0.5 - 0.5 * cos(2.0 * PI * i / size)).toFloat()
    }
}

/** Matches numpy.hanning(length), including its two zero endpoints. */
internal fun symmetricHann(size: Int): FloatArray {
    if (size <= 1) return FloatArray(size.coerceAtLeast(0)) { 1f }
    val denominator = (size - 1).toDouble()
    return FloatArray(size) { i ->
        (0.5 - 0.5 * cos(2.0 * PI * i / denominator)).toFloat()
    }
}
