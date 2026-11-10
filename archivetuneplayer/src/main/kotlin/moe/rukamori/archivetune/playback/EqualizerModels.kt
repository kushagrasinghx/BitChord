/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class EqProfile(
    val id: String,
    val name: String,
    val bandCenterFreqHz: List<Int> = emptyList(),
    val bandLevelsMb: List<Int> = emptyList(),
    val outputGainMb: Int = 0,
    val outputGainEnabled: Boolean? = null,
    val bassBoostStrength: Int = 0,
    val bassBoostEnabled: Boolean? = null,
    val virtualizerStrength: Int = 0,
    val virtualizerEnabled: Boolean? = null,
    val autoHeadroomEnabled: Boolean = false,
    val reverbEnabled: Boolean = false,
    val reverbPreset: Int = 0,
    val balance: Float = 0f,
    val eightDEnabled: Boolean = false,
    val eightDSpeedHz: Float = 0.2f,
)

@Serializable
data class EqProfilesPayload(
    @SerialName("profiles")
    val profiles: List<EqProfile> = emptyList(),
)

data class EqCapabilities(
    val bandCount: Int,
    val minBandLevelMb: Int,
    val maxBandLevelMb: Int,
    val centerFreqHz: List<Int>,
    val systemPresets: List<String>,
)

data class EqSettings(
    val enabled: Boolean,
    val bandLevelsMb: List<Int>,
    val outputGainEnabled: Boolean,
    val outputGainMb: Int,
    val bassBoostEnabled: Boolean,
    val bassBoostStrength: Int,
    val virtualizerEnabled: Boolean,
    val virtualizerStrength: Int,
    val autoHeadroomEnabled: Boolean,
    val reverbEnabled: Boolean = false,
    val reverbPreset: Int = 0,
    val balance: Float = 0f,
    val eightDEnabled: Boolean = false,
    val eightDSpeedHz: Float = 0.2f,
    val bandFreqsHz: List<Int> = emptyList(),
)

enum class EqReverbPreset(
    val storageValue: Int,
) {
    NONE(0),
    SMALL_ROOM(1),
    MEDIUM_ROOM(2),
    LARGE_ROOM(3),
    MEDIUM_HALL(4),
    LARGE_HALL(5),
    PLATE(6),
    ;

    companion object {
        fun fromStorage(value: Int): EqReverbPreset = entries.firstOrNull { it.storageValue == value } ?: NONE
    }
}

internal object EqualizerJson {
    val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
}

fun mapBandLevelsByFrequency(
    levelsMb: List<Int>,
    sourceFreqHz: List<Int>,
    targetFreqHz: List<Int>,
): List<Int>? {
    if (levelsMb.isEmpty() || targetFreqHz.isEmpty()) return null
    if (sourceFreqHz.size != levelsMb.size) return null
    if (sourceFreqHz.any { it <= 0 } || targetFreqHz.any { it <= 0 }) return null
    if (levelsMb.size == targetFreqHz.size && sourceFreqHz == targetFreqHz) return levelsMb

    val sortedPairs = sourceFreqHz.zip(levelsMb).sortedBy { it.first }
    val sourceLog = sortedPairs.map { ln(it.first.toDouble()) }
    val sourceLevels = sortedPairs.map { it.second }

    fun levelAtLogFreq(logFreq: Double): Int {
        if (logFreq <= sourceLog.first()) return sourceLevels.first()
        if (logFreq >= sourceLog.last()) return sourceLevels.last()
        var hi = 1
        while (hi < sourceLog.lastIndex && sourceLog[hi] < logFreq) hi++
        val lo = hi - 1
        val span = sourceLog[hi] - sourceLog[lo]
        val t = if (span <= 0.0) 0.0 else (logFreq - sourceLog[lo]) / span
        val a = sourceLevels[lo]
        val b = sourceLevels[hi]
        return (a + ((b - a) * t)).toInt()
    }

    return targetFreqHz.map { freq -> levelAtLogFreq(ln(freq.toDouble())) }
}

private fun ln(value: Double): Double = kotlin.math.ln(value)
