package com.lastwave.app.playback

data class ClarityPreset(
    val index: Int,
    val key: String,
    val displayName: String,
    val description: String,
    val trimsDb: FloatArray,
)

object ClarityPresets {
    const val TRIM_COUNT = 8

    const val REFERENCE_INDEX = 0
    const val SPEAKER_INDEX = 1
    const val HEADPHONE_INDEX = 2
    const val DAC_INDEX = 3

    val REFERENCE = ClarityPreset(
        index = REFERENCE_INDEX,
        key = "reference",
        displayName = "Reference",
        description = "Unmodified shipping curve; every stage trim is 0 dB.",
        trimsDb = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
    )

    val SPEAKER = ClarityPreset(
        index = SPEAKER_INDEX,
        key = "speaker",
        displayName = "Speaker",
        description = "Reduced sub-bass lift (-1.5 dB) and gentler air (-0.5 dB) for small drivers.",
        trimsDb = floatArrayOf(0f, -1.5f, 0f, 0f, 0f, -0.5f, 0f, 0f),
    )

    val HEADPHONE = ClarityPreset(
        index = HEADPHONE_INDEX,
        key = "headphone",
        displayName = "Headphone",
        description = "Lighter bass (-0.5 dB) and softer presence (-1.0 dB) for close-coupled drivers.",
        trimsDb = floatArrayOf(0f, -0.5f, 0f, 0f, -1.0f, 0f, 0f, 0f),
    )

    val DAC = ClarityPreset(
        index = DAC_INDEX,
        key = "dac",
        displayName = "DAC",
        description = "Gentler air (-1.5 dB shelf, -1.0 dB exciter) for revealing chains.",
        trimsDb = floatArrayOf(0f, 0f, 0f, 0f, 0f, -1.5f, 0f, -1.0f),
    )

    val ALL: List<ClarityPreset> = listOf(REFERENCE, SPEAKER, HEADPHONE, DAC)

    fun fromIndex(index: Int): ClarityPreset =
        ALL.firstOrNull { it.index == index } ?: REFERENCE
}
