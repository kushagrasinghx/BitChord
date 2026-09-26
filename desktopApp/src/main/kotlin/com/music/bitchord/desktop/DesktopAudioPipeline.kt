package com.music.bitchord.desktop

/**
 * The playback signal chain as it stands right now — what the engine can say about each stage
 * between the stream arriving and the samples reaching the device.
 *
 * Every field is nullable because the chain is only partly built until something is playing.
 */
internal data class DesktopAudioPipeline(
    /** What the engine is actually playing, which the screen's own copy can lag behind. */
    val sourceFormat: DesktopStreamFormat? = null,
    val decoderName: String? = null,
    val decodedSampleRateHz: Int? = null,
    val decodedChannels: Int? = null,
    val outputSampleRateHz: Int? = null,
    val outputChannels: Int? = null,
    val outputIsFloat: Boolean? = null,
    val outputBytesPerSample: Int? = null,
    val deviceName: String? = null,
    val bufferBytes: Int = 0,
    val equalizerEnabled: Boolean = false,
    val skipSilence: Boolean = false,
) {
    /** The decoder under its usual name, the same one the quality badge shows. */
    val decoderLabel: String?
        get() = decoderName?.let { DesktopStreamFormat(codec = it).codecLabel }

    /** Whether the decoder's rate had to be converted to reach the device. */
    val resampling: Boolean
        get() = decodedSampleRateHz != null && outputSampleRateHz != null &&
            decodedSampleRateHz != outputSampleRateHz
}
