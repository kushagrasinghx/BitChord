package com.music.bitchord.playback.audio

/**
 * Supported raw PCM sample encodings for audio stream input decoding and output formatting.
 *
 * All multi-byte formats are assumed to be Little-Endian byte order, adhering to standard
 * Android AudioTrack, Linux ALSA, and AndroidX Media3 conventions.
 */
enum class PcmEncoding(
    val bytesPerSample: Int,
    val bitDepth: Int,
) {
    /** Signed 16-bit integer PCM, 2 bytes per sample (range: -32768..32767). */
    PCM_16BIT(bytesPerSample = 2, bitDepth = 16),

    /** Signed 24-bit integer PCM packed into 3 bytes per sample (range: -8388608..8388607). */
    PCM_24BIT_PACKED(bytesPerSample = 3, bitDepth = 24),

    /** Signed 32-bit integer PCM, 4 bytes per sample (range: -2147483648..2147483647). */
    PCM_32BIT(bytesPerSample = 4, bitDepth = 32),

    /** IEEE 754 32-bit floating point PCM, 4 bytes per sample (nominal range: -1.0f..1.0f). */
    PCM_FLOAT(bytesPerSample = 4, bitDepth = 32);

    /**
     * Number of bytes required to store one audio frame across [channelCount] channels.
     */
    fun bytesPerFrame(channelCount: Int): Int {
        require(channelCount > 0) { "channelCount must be positive: $channelCount" }
        return bytesPerSample * channelCount
    }

    /**
     * Number of bytes required to store [frames] audio frames across [channelCount] channels.
     */
    fun totalBytes(frames: Int, channelCount: Int): Int {
        require(frames >= 0) { "frames must be non-negative: $frames" }
        return frames * bytesPerFrame(channelCount)
    }
}
