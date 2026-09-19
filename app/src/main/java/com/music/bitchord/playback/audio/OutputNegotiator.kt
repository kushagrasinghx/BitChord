/*
 * Copyright (C) 2026 BitChord Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package com.music.bitchord.playback.audio

import android.media.AudioFormat
import com.music.bitchord.data.settings.OutputPcmMode
import com.music.bitchord.playback.AudioOutputPolicy
import com.music.bitchord.playback.AudioRouting
import com.music.bitchord.playback.audio.bluetooth.BluetoothTelemetry
import com.music.bitchord.playback.audio.usb.DirectUsbProbeResult

/**
 * Output transport mechanism selected by the universal best-path engine.
 */
enum class TransportType(val label: String) {
    AUDIO_TRACK("AudioTrack"),
    AUDIO_TRACK_DIRECT("AudioTrack (Direct)"),
    DIRECT_USB("Direct USB"),
}

/**
 * Authoritative rationale when the audio pipeline falls back from the listener's requested
 * format or transport to a lower-precision or system-mixed path.
 */
enum class FallbackReason(val label: String) {
    NONE("None"),
    UNSUPPORTED_FORMAT("Unsupported source format"),
    ROUTE_LIMITATION("Route limitation (device profile)"),
    OS_LIMITATION("OS limitation (system audio policy)"),
    DIRECT_USB_UNAVAILABLE("Direct USB unavailable"),
    DECODER_LIMITATION("Decoder limitation"),
}

data class SourceDescriptor(
    val encoding: String,
    val sampleRateHz: Int,
    val channelCount: Int,
    val bitDepth: Int?,
)

data class DecoderDescriptor(
    val name: String?,
    val encoding: String = "Float32",
    val sampleRateHz: Int,
    val channelCount: Int,
)

data class DspDescriptor(
    val format: String = "Float32",
    val sampleRateHz: Int,
    val channelCount: Int,
)

data class RouteDescriptor(
    val kind: AudioRouting.Kind,
    val deviceName: String,
    val isDirectUsbCapable: Boolean = false,
    val advertisedEncodings: List<Int> = emptyList(),
    val advertisedSampleRates: List<Int> = emptyList(),
    val directSupport: DirectAudioProbe.DirectSupport = DirectAudioProbe.DirectSupport.NONE,
    val bluetoothTelemetry: BluetoothTelemetry? = null,
)

data class OutputDescriptor(
    val transport: TransportType,
    val encoding: PcmEncoding,
    val sampleRateHz: Int,
    val channelCount: Int,
    val isDirect: Boolean = false,
    val systemMixerRateHz: Int? = null,
    val fallbackReason: FallbackReason = FallbackReason.NONE,
    val fallbackDetail: String? = null,
)

/**
 * Complete, authoritative snapshot of the 6-layer audio pipeline state:
 * Source -> Decoder -> DSP -> Route -> Output -> System.
 */
data class OutputNegotiationResult(
    val source: SourceDescriptor,
    val decoder: DecoderDescriptor,
    val dsp: DspDescriptor,
    val route: RouteDescriptor,
    val output: OutputDescriptor,
) {
    /** True if end-to-end sample rate was preserved without downstream mixer conversion. */
    val isSampleRatePreserved: Boolean
        get() = output.isDirect || output.systemMixerRateHz == null || output.sampleRateHz == output.systemMixerRateHz
}

/**
 * Universal Best-Path Output Selection Engine.
 *
 * Deterministic selection order:
 * 1. Native/direct playback when Android exposes a direct path for the exact format/rate/channel configuration.
 * 2. Direct USB transport when userspace USB streaming is actually possible and safe.
 * 3. Highest-quality Android Bluetooth output actually supported by the active route.
 * 4. Highest-quality normal AudioTrack route supported by the active route.
 * 5. Safe fallback.
 *
 * Core Rules:
 * - Internal DSP is ALWAYS canonical Float32.
 * - Built-in phone speaker is strictly capped at 16-bit PCM for hardware safety.
 * - External routes evaluate Float32, 24-bit packed, and 16-bit based on runtime direct profiles & advertised encodings.
 * - Native source sample rate is prioritized to eliminate unnecessary SRC.
 * - Zero marketing claims: only factual, runtime-verified capabilities are reported.
 */
object OutputNegotiator {

    fun negotiate(
        source: SourceDescriptor,
        decoderName: String?,
        decoderEncoding: String = "Float32",
        sampleRateHz: Int,
        channelCount: Int,
        routeKind: AudioRouting.Kind,
        deviceName: String,
        advertisedEncodings: List<Int>,
        advertisedSampleRates: List<Int>,
        requestedMode: OutputPcmMode,
        directUsbProbe: DirectUsbProbeResult? = null,
        directSupport: DirectAudioProbe.DirectSupport = DirectAudioProbe.DirectSupport.NONE,
        bluetoothTelemetry: BluetoothTelemetry? = null,
        delegateSupportsFloat: Boolean = true,
        delegateSupportsPcm24: Boolean = true,
        knownSystemMixerRateHz: Int? = null,
    ): OutputNegotiationResult {
        val decoder = DecoderDescriptor(
            name = decoderName,
            encoding = decoderEncoding,
            sampleRateHz = sampleRateHz,
            channelCount = channelCount,
        )

        val dsp = DspDescriptor(
            format = "Float32",
            sampleRateHz = sampleRateHz,
            channelCount = channelCount,
        )

        val route = RouteDescriptor(
            kind = routeKind,
            deviceName = deviceName,
            isDirectUsbCapable = directUsbProbe?.isViable == true,
            advertisedEncodings = advertisedEncodings,
            advertisedSampleRates = advertisedSampleRates,
            directSupport = directSupport,
            bluetoothTelemetry = bluetoothTelemetry,
        )

        // Evaluate Best Output
        val output = selectBestOutput(
            source = source,
            sampleRateHz = sampleRateHz,
            channelCount = channelCount,
            routeKind = routeKind,
            advertisedEncodings = advertisedEncodings,
            advertisedSampleRates = advertisedSampleRates,
            requestedMode = requestedMode,
            directUsbProbe = directUsbProbe,
            directSupport = directSupport,
            bluetoothTelemetry = bluetoothTelemetry,
            delegateSupportsFloat = delegateSupportsFloat,
            delegateSupportsPcm24 = delegateSupportsPcm24,
            knownSystemMixerRateHz = knownSystemMixerRateHz,
        )

        return OutputNegotiationResult(
            source = source,
            decoder = decoder,
            dsp = dsp,
            route = route,
            output = output,
        )
    }

    private fun selectBestOutput(
        source: SourceDescriptor,
        sampleRateHz: Int,
        channelCount: Int,
        routeKind: AudioRouting.Kind,
        advertisedEncodings: List<Int>,
        advertisedSampleRates: List<Int>,
        requestedMode: OutputPcmMode,
        directUsbProbe: DirectUsbProbeResult?,
        directSupport: DirectAudioProbe.DirectSupport,
        bluetoothTelemetry: BluetoothTelemetry?,
        delegateSupportsFloat: Boolean,
        delegateSupportsPcm24: Boolean,
        knownSystemMixerRateHz: Int?,
    ): OutputDescriptor {
        val advertisesFloat = advertisedEncodings.contains(AudioFormat.ENCODING_PCM_FLOAT)
        val advertisesPcm24 = advertisedEncodings.contains(AudioFormat.ENCODING_PCM_24BIT_PACKED)

        // 1. Phone Speaker Safety Rule: Always cap at PCM16
        if (routeKind == AudioRouting.Kind.PHONE) {
            val fallbackReason = if (requestedMode == OutputPcmMode.FLOAT_32) {
                FallbackReason.ROUTE_LIMITATION
            } else {
                FallbackReason.NONE
            }
            val fallbackDetail = if (fallbackReason != FallbackReason.NONE) {
                "Speaker output capped at 16-bit PCM to prevent OEM mixer distortion"
            } else {
                null
            }
            return OutputDescriptor(
                transport = TransportType.AUDIO_TRACK,
                encoding = PcmEncoding.PCM_16BIT,
                sampleRateHz = sampleRateHz,
                channelCount = channelCount,
                isDirect = false,
                systemMixerRateHz = knownSystemMixerRateHz,
                fallbackReason = fallbackReason,
                fallbackDetail = fallbackDetail,
            )
        }

        // 2. Direct USB Priority: userspace USB streaming if authorized and viable
        val isDirectUsbViable = routeKind == AudioRouting.Kind.USB && directUsbProbe?.isViable == true
        if (isDirectUsbViable) {
            return OutputDescriptor(
                transport = TransportType.DIRECT_USB,
                encoding = PcmEncoding.PCM_FLOAT,
                sampleRateHz = sampleRateHz,
                channelCount = channelCount,
                isDirect = true,
                systemMixerRateHz = null, // Bypasses Android AudioFlinger entirely
                fallbackReason = FallbackReason.NONE,
                fallbackDetail = null,
            )
        }

        // 3. Android DIRECT Playback Evaluation (API 33+)
        // Priority 3A: Direct Float32
        if (requestedMode == OutputPcmMode.FLOAT_32 && directSupport.supportsFloat && delegateSupportsFloat) {
            return OutputDescriptor(
                transport = TransportType.AUDIO_TRACK_DIRECT,
                encoding = PcmEncoding.PCM_FLOAT,
                sampleRateHz = sampleRateHz,
                channelCount = channelCount,
                isDirect = true,
                systemMixerRateHz = null, // Direct hardware output bypasses mixer
                fallbackReason = FallbackReason.NONE,
                fallbackDetail = null,
            )
        }

        // Priority 3B: Direct PCM24
        val isSourceHighRes = (source.bitDepth ?: 16) > 16
        if (requestedMode != OutputPcmMode.PCM_16 && isSourceHighRes && directSupport.supportsPcm24 && delegateSupportsPcm24) {
            val fallbackReason = if (requestedMode == OutputPcmMode.FLOAT_32 && !directSupport.supportsFloat) {
                FallbackReason.ROUTE_LIMITATION
            } else {
                FallbackReason.NONE
            }
            val fallbackDetail = if (fallbackReason != FallbackReason.NONE) {
                "Route exposes direct 24-bit PCM (Float32 converted to packed 24-bit)"
            } else {
                null
            }
            return OutputDescriptor(
                transport = TransportType.AUDIO_TRACK_DIRECT,
                encoding = PcmEncoding.PCM_24BIT_PACKED,
                sampleRateHz = sampleRateHz,
                channelCount = channelCount,
                isDirect = true,
                systemMixerRateHz = null,
                fallbackReason = fallbackReason,
                fallbackDetail = fallbackDetail,
            )
        }

        // Priority 3C: Direct PCM16
        if (directSupport.supportsPcm16) {
            val fallbackReason = when {
                requestedMode == OutputPcmMode.PCM_16 -> FallbackReason.NONE
                requestedMode == OutputPcmMode.FLOAT_32 -> FallbackReason.ROUTE_LIMITATION
                isSourceHighRes -> FallbackReason.ROUTE_LIMITATION
                else -> FallbackReason.NONE
            }
            val fallbackDetail = when {
                requestedMode == OutputPcmMode.FLOAT_32 ->
                    "Route does not expose direct Float32 or 24-bit PCM (using direct 16-bit PCM)"
                isSourceHighRes ->
                    "Route does not expose direct 24-bit PCM (using direct 16-bit PCM)"
                else -> null
            }
            return OutputDescriptor(
                transport = TransportType.AUDIO_TRACK_DIRECT,
                encoding = PcmEncoding.PCM_16BIT,
                sampleRateHz = sampleRateHz,
                channelCount = channelCount,
                isDirect = true,
                systemMixerRateHz = null,
                fallbackReason = fallbackReason,
                fallbackDetail = fallbackDetail,
            )
        }

        // 4. External Route Advertised Capabilities (Normal AudioTrack with framework mixing)
        // Check Float32 advertised:
        val canUseFloat = AudioOutputPolicy.shouldUseFloatOutput(requestedMode, routeKind, advertisesFloat) &&
            delegateSupportsFloat
        if (canUseFloat) {
            return OutputDescriptor(
                transport = TransportType.AUDIO_TRACK,
                encoding = PcmEncoding.PCM_FLOAT,
                sampleRateHz = sampleRateHz,
                channelCount = channelCount,
                isDirect = false,
                systemMixerRateHz = knownSystemMixerRateHz,
                fallbackReason = FallbackReason.NONE,
                fallbackDetail = null,
            )
        }

        // Check 24-bit advertised:
        if (requestedMode != OutputPcmMode.PCM_16 && isSourceHighRes && advertisesPcm24 && delegateSupportsPcm24) {
            val fallbackReason = if (requestedMode == OutputPcmMode.FLOAT_32) {
                FallbackReason.ROUTE_LIMITATION
            } else {
                FallbackReason.NONE
            }
            val fallbackDetail = if (fallbackReason != FallbackReason.NONE) {
                "${routeKind.name} route advertises 24-bit PCM (Float32 converted to packed 24-bit)"
            } else {
                null
            }
            return OutputDescriptor(
                transport = TransportType.AUDIO_TRACK,
                encoding = PcmEncoding.PCM_24BIT_PACKED,
                sampleRateHz = sampleRateHz,
                channelCount = channelCount,
                isDirect = false,
                systemMixerRateHz = knownSystemMixerRateHz,
                fallbackReason = fallbackReason,
                fallbackDetail = fallbackDetail,
            )
        }

        // 5. 16-bit PCM Fallback
        val (reason, detail) = when {
            routeKind == AudioRouting.Kind.USB -> {
                val r = if (directUsbProbe?.isViable == false) {
                    FallbackReason.DIRECT_USB_UNAVAILABLE
                } else {
                    FallbackReason.ROUTE_LIMITATION
                }
                val d = directUsbProbe?.diagnosticReason
                    ?: "USB device advertises 16-bit PCM only"
                Pair(r, d)
            }
            routeKind == AudioRouting.Kind.BLUETOOTH -> {
                val btSummary = bluetoothTelemetry?.codecName?.let { " ($it)" }.orEmpty()
                Pair(
                    FallbackReason.ROUTE_LIMITATION,
                    "Bluetooth route$btSummary advertises 16-bit PCM only",
                )
            }
            requestedMode == OutputPcmMode.FLOAT_32 -> {
                Pair(
                    FallbackReason.ROUTE_LIMITATION,
                    "${routeKind.name} route advertises 16-bit PCM only",
                )
            }
            isSourceHighRes && requestedMode != OutputPcmMode.PCM_16 -> {
                Pair(
                    FallbackReason.ROUTE_LIMITATION,
                    "${routeKind.name} route does not expose high-res output",
                )
            }
            else -> {
                Pair(FallbackReason.NONE, null)
            }
        }

        return OutputDescriptor(
            transport = TransportType.AUDIO_TRACK,
            encoding = PcmEncoding.PCM_16BIT,
            sampleRateHz = sampleRateHz,
            channelCount = channelCount,
            isDirect = false,
            systemMixerRateHz = knownSystemMixerRateHz,
            fallbackReason = reason,
            fallbackDetail = detail,
        )
    }
}
