package com.music.bitchord.data.shazam

/**
 * One peak in a Shazam fingerprint.
 *
 * [fftPassNumber] counts 128-sample hops from the start of the audio (one every
 * 8 ms at 16 kHz), so two signatures of the same recording line up at the same
 * pass numbers. [magnitude] is the reference's logarithmic peak height, and
 * [frequencyBin] is its interpolated FFT bin multiplied by 64.
 *
 * Both numbers are [Int]s rather than floats because the binary signature
 * stores them as 16-bit values; keeping the rounded values here means the
 * encoder can write the signature without doing the conversion twice.
 */
data class ShazamPeak(
    val fftPassNumber: Int,
    val magnitude: Int,
    val frequencyBin: Int,
)

/**
 * The four frequency bands a Shazam signature groups its peaks into.
 *
 * [index] is the band id written into the binary signature, so these entries
 * must stay in this order: the encoder writes them 0, 1, 2, 3.
 */
enum class ShazamFrequencyBand(
    val index: Int,
    val minimumHz: Int,
    val maximumHz: Int,
) {
    BAND_250_520(0, 250, 519),
    BAND_520_1450(1, 520, 1449),
    BAND_1450_3500(2, 1450, 3499),
    BAND_3500_5500(3, 3500, 5500),
    ;

    companion object {
        /** The band [frequencyHz] falls into, or null outside 250-5500 Hz. */
        fun forFrequency(frequencyHz: Int): ShazamFrequencyBand? = when (frequencyHz) {
            in BAND_250_520.minimumHz..BAND_250_520.maximumHz -> BAND_250_520
            in BAND_520_1450.minimumHz..BAND_520_1450.maximumHz -> BAND_520_1450
            in BAND_1450_3500.minimumHz..BAND_1450_3500.maximumHz -> BAND_1450_3500
            in BAND_3500_5500.minimumHz..BAND_3500_5500.maximumHz -> BAND_3500_5500
            else -> null
        }
    }
}

/**
 * A fingerprint of 16 kHz mono PCM, ready to be encoded and sent to Shazam.
 *
 * Peaks are grouped by band because that is how the binary format lays them
 * out. [sampleCount] is the length of the audio handed to the generator,
 * including any trailing samples too short for a full 128-sample hop: the
 * signature header reports the source length even though those samples cannot
 * contribute a peak.
 */
data class ShazamSignature(
    val sampleRateHz: Int,
    val sampleCount: Int,
    val peaksByBand: Map<ShazamFrequencyBand, List<ShazamPeak>>,
)
