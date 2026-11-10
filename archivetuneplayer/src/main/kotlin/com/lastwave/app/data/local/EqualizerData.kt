/*
 * BRIDGE — the pure-data half of ArchiveTune's
 * com.lastwave.app.data.local.EqualizerPreferences. The equalizer dialog the
 * ported player carries reads the preset table and band frequencies from
 * here; the preference-persisting half of the upstream file is not part of
 * the ported surface (the dialog's own preference keys carry the state).
 */

package com.lastwave.app.data.local

val EQ_BAND_FREQS_HZ = intArrayOf(25, 40, 63, 100, 160, 250, 400, 630, 1000, 1600, 2500, 4000, 6300, 10000, 16000)
const val EQ_MAX_GAIN_DB = 8f

fun eqBandLabel(hz: Int): String = if (hz >= 1000) "${hz / 1000}K" else "$hz"

data class EqPreset(
    val name: String,

    val gainsDb: List<Float>,
)

object EqualizerPresets {
    const val CUSTOM_NAME = "Custom"

    val FLAT = EqPreset("Default", List(EQ_BAND_FREQS_HZ.size) { 0f })

    val STUDIO_MASTER = EqPreset(
        "Studio Master",
        listOf(
            2.6f, 2.8f, 2.2f, 0.6f, -1.8f,
            -2.6f, -1.2f, 0.0f, 1.2f, 2.4f,
            3.6f, 4.0f, 4.2f, 4.5f, 4.8f,
        ),
    )

    val ALL: List<EqPreset> = listOf(
        FLAT,
        STUDIO_MASTER,
        EqPreset("Acoustic", listOf(3.0f, 2.5f, 2.0f, 1.5f, 0.5f, 1.0f, 1.5f, 2.0f, 2.0f, 1.5f, 1.0f, 1.0f, 1.0f, 1.5f, 1.5f)),
        EqPreset("Vocal Clarity", listOf(-1.5f, -1.0f, -0.5f, 0f, 0.5f, 1.0f, 2.0f, 3.0f, 3.5f, 3.0f, 2.5f, 2.0f, 1.5f, 1.5f, 1.0f)),
        EqPreset("Classical", listOf(3.0f, 2.5f, 2.0f, 1.5f, 0.5f, 0f, 0f, 0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.0f)),
        EqPreset("Jazz", listOf(2.5f, 2.0f, 1.5f, 1.0f, 0f, -0.5f, 0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.0f, 2.5f, 2.5f, 2.5f)),
        EqPreset("Rock", listOf(4.0f, 3.5f, 3.0f, 1.5f, 0f, -1.0f, -1.0f, -0.5f, 0.5f, 1.5f, 2.5f, 3.0f, 3.5f, 4.0f, 4.0f)),
        EqPreset("Pop", listOf(-1.0f, -0.5f, 0.0f, 1.0f, 1.5f, 2.0f, 2.0f, 1.5f, 1.0f, 0.5f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f)),
        EqPreset("Treble Air", listOf(0f, 0f, 0f, -0.5f, -0.5f, -0.5f, 0f, 0.5f, 1.0f, 1.5f, 2.0f, 3.0f, 3.5f, 4.5f, 5.0f)),
        EqPreset("Deep Bass Clean", listOf(5.0f, 4.5f, 3.5f, 2.5f, 1.0f, 0f, -0.5f, -0.5f, 0f, 0f, 0f, 0f, 0.5f, 0.5f, 0.5f)),
        EqPreset("Electronic", listOf(4.5f, 4.0f, 3.0f, 2.0f, 0.5f, -0.5f, 0f, 1.0f, 2.0f, 2.5f, 2.5f, 2.5f, 3.0f, 3.5f, 4.0f)),
        EqPreset("R&B", listOf(5.0f, 4.5f, 3.5f, 2.0f, 1.0f, -0.5f, -1.0f, -0.5f, 0.5f, 1.5f, 2.0f, 2.5f, 3.0f, 3.0f, 3.0f)),
    )

    fun byName(name: String): EqPreset? = ALL.firstOrNull { it.name.equals(name, ignoreCase = true) }
}

data class EqualizerSettings(
    val enabled: Boolean = false,
    val presetName: String = EqualizerPresets.FLAT.name,

    val gainsDb: List<Float> = EqualizerPresets.FLAT.gainsDb,
)
