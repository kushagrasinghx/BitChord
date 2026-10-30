package app.stemsep.dsp

import kotlin.math.floor

/**
 * DSP contract shared by the MDX-Net model profiles.
 *
 * The chunk length is derived from the model graph: `hop * (timeFrames - 1)`.
 * UVR calls the leading/trailing half-window "trim"; it is removed from the
 * model output and used to overlap neighbouring chunks.
 */
data class MdxConfig(
    val fftSize: Int,
    val hopLength: Int,
    val frequencyBins: Int,
    val timeFrames: Int,
    val segmentSamples: Int,
    val edgeTrimSamples: Int,
    val compensation: Float = 1f,
    val overlap: Float = 0.10f,
    val sampleRate: Int = 44_100,
    val zeroLowBins: Boolean = true,
) {
    init {
        require(fftSize > 0) { "fftSize must be positive" }
        require(hopLength > 0) { "hopLength must be positive" }
        require(frequencyBins in 1..(fftSize / 2 + 1)) { "frequencyBins outside the real FFT" }
        require(timeFrames >= 2) { "timeFrames must be at least 2" }
        require(segmentSamples == hopLength * (timeFrames - 1)) {
            "segmentSamples must equal hopLength * (timeFrames - 1)"
        }
        require(edgeTrimSamples == fftSize / 2) { "UVR MDX trim must be fftSize / 2" }
        require(segmentSamples > 2 * edgeTrimSamples) { "segment is too short for its trim" }
        require(overlap >= 0f && overlap < 1f) { "overlap must be in [0, 1)" }
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(compensation.isFinite() && compensation > 0f) { "invalid compensation" }
        require(strideSamples <= generatedSamples) { "overlap leaves an uncovered gap" }
    }

    val tensorElements: Int
        get() = 4 * frequencyBins * timeFrames

    val strideSamples: Int
        get() = maxOf(1, floor(segmentSamples * (1.0 - overlap)).toInt())

    val generatedSamples: Int
        get() = segmentSamples - 2 * edgeTrimSamples
}
