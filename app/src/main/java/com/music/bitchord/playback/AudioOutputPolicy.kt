package com.music.bitchord.playback

import com.music.bitchord.data.settings.OutputPcmMode

/**
 * Decides whether Media3 may open a PCM-float AudioTrack.
 *
 * Float output is not a quality switch that is safe on every Android route.
 * Some OEM speaker paths accept the AudioTrack and then convert it to PCM16 in
 * AudioFlinger; on affected Samsung FLAC decoders that combination can also
 * produce timestamp discontinuities and severely distorted output.
 * The phone speaker stays capped at PCM16. On Bluetooth, an explicit Float32
 * request can try native Float32 despite an incomplete A2DP encoding list,
 * with the sink retaining PCM16 fallback when Float32 is unsupported.
 */
internal object AudioOutputPolicy {
    /** Backwards-compatible USB-specific check. */
    fun shouldUseFloatOutput(
        requestedMode: OutputPcmMode,
        isPreferredUsbRoute: Boolean,
        advertisesPcmFloat: Boolean,
    ): Boolean = requestedMode == OutputPcmMode.FLOAT_32 &&
        isPreferredUsbRoute &&
        advertisesPcmFloat

    /**
     * Route-aware float output decision.
     *
     * - Built-in phone speaker (PHONE) must remain capped at 16-bit to prevent OEM mixer
     *   issues and distortion on affected device paths.
     * - USB/Wired/HDMI require Float32 advertised by AudioDeviceInfo when requested.
     * - Bluetooth also permits an explicit Float32 request even if A2DP advertises
     *   only PCM16: some Android mixers can accept Float32 AudioTracks regardless
     *   of the device encoding list. The sink still checks native Float32 format
     *   support and falls back to PCM16 if it cannot open that path.
     * - A Bluetooth route whose negotiated A2DP codec carries more than 16 bits
     *   (LDAC / aptX HD at 24 or 32 bits) uses float regardless of the requested mode.
     *   A2DP devices almost never list PCM_FLOAT in their encodings, and without a
     *   float track Media3 truncates a hi-res source to 16 bits before the codec
     *   ever sees it. [bluetoothCodecBitDepth] must come from authoritative telemetry.
     */
    fun shouldUseFloatOutput(
        requestedMode: OutputPcmMode,
        routeKind: AudioRouting.Kind,
        advertisesPcmFloat: Boolean,
        bluetoothCodecBitDepth: Int? = null,
    ): Boolean {
        if (routeKind == AudioRouting.Kind.PHONE) return false
        if (routeKind == AudioRouting.Kind.BLUETOOTH && (bluetoothCodecBitDepth ?: 0) > 16) return true
        if (requestedMode != OutputPcmMode.FLOAT_32) return false
        if (routeKind == AudioRouting.Kind.BLUETOOTH) return true
        return advertisesPcmFloat
    }

    /** Samsung's vendor FLAC decoder emits invalid timestamps with PCM float. */
    fun isUnsafeFloatFlacDecoder(name: String): Boolean {
        val normalized = name.lowercase()
        return normalized == "c2.sec.flac.decoder" ||
            (normalized.startsWith("omx.sec.") && normalized.contains("flac"))
    }
}
