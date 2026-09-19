/*
 * Copyright (C) 2026 BitChord Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package com.music.bitchord.playback

import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import com.music.bitchord.data.settings.OutputPcmMode
import com.music.bitchord.playback.audio.DirectAudioProbe
import com.music.bitchord.playback.audio.FallbackReason
import com.music.bitchord.playback.audio.OutputNegotiationResult
import com.music.bitchord.playback.audio.PcmEncoding
import com.music.bitchord.playback.audio.TransportType
import com.music.bitchord.playback.audio.bluetooth.BluetoothTelemetry
import com.music.bitchord.playback.audio.usb.DirectUsbProbeResult
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Live facts about the Android output route and negotiated audio pipeline,
 * kept separate from source-format statistics.
 *
 * This snapshot transparently reports:
 * - Route kind and active transport (AudioTrack, AudioTrack (Direct), or Direct USB)
 * - Actual decoder output format, DSP format, and AudioTrack format
 * - Direct playback support status (Android 13+ AudioManager direct profiles)
 * - Realtime Bluetooth codec configuration and telemetry (LDAC, LHDC, AAC, SBC)
 * - Exact fallback reason and explanation if fallback occurred
 * - Downstream system mixer sample rate (e.g. 48 kHz AudioFlinger)
 */
object AudioOutputStatus {
    data class Snapshot(
        val sink: String = "AudioTrack",
        val requestedPcmMode: OutputPcmMode = OutputPcmMode.PCM_16,
        val deviceName: String = "System default",
        /**
         * The [AudioDeviceInfo.getId] of whatever [deviceName] names — the exact
         * device the player resolved and is rendering to, not a second guess at
         * it. The output picker and the caption under the transport both mark
         * their "active" row off this rather than recomputing their own answer,
         * which is what let the two disagree.
         */
        val activeDeviceId: Int? = null,
        val sampleRatesHz: IntArray = IntArray(0),
        val encodings: IntArray = IntArray(0),
        val isUsb: Boolean = false,
        val routeKind: AudioRouting.Kind = AudioRouting.Kind.PHONE,
        val requestedTransportType: TransportType = TransportType.AUDIO_TRACK,
        val transportType: TransportType = TransportType.AUDIO_TRACK,
        val actualEncoding: Int? = null,
        val actualSampleRateHz: Int? = null,
        val floatFallback: Boolean = false,
        val fallbackReason: FallbackReason = FallbackReason.NONE,
        val fallbackDetail: String? = null,
        val systemMixerRateHz: Int? = null,
        val decoderName: String? = null,
        val bufferSize: Int? = null,
        val decoderOutputEncoding: String? = null,
        val dspFormat: String = "Float32",
        val directUsbProbe: DirectUsbProbeResult? = null,
        val directSupport: DirectAudioProbe.DirectSupport? = null,
        val directPlaybackSupported: Boolean = false,
        val directPlaybackSelected: Boolean = false,
        val directPlaybackRequested: Boolean = false,
        val directPlaybackActual: Boolean = false,
        val directPlaybackRejected: Boolean = false,
        val directPlaybackDetail: String? = null,
        val halFormat: String? = null,
        val usbEndpointFormat: String? = null,
        val bluetoothTelemetry: BluetoothTelemetry? = null,
        val negotiationResult: OutputNegotiationResult? = null,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Snapshot) return false
            return sink == other.sink &&
                requestedPcmMode == other.requestedPcmMode &&
                deviceName == other.deviceName &&
                activeDeviceId == other.activeDeviceId &&
                sampleRatesHz.contentEquals(other.sampleRatesHz) &&
                encodings.contentEquals(other.encodings) &&
                isUsb == other.isUsb &&
                routeKind == other.routeKind &&
                requestedTransportType == other.requestedTransportType &&
                transportType == other.transportType &&
                actualEncoding == other.actualEncoding &&
                actualSampleRateHz == other.actualSampleRateHz &&
                floatFallback == other.floatFallback &&
                fallbackReason == other.fallbackReason &&
                fallbackDetail == other.fallbackDetail &&
                systemMixerRateHz == other.systemMixerRateHz &&
                decoderName == other.decoderName &&
                bufferSize == other.bufferSize &&
                decoderOutputEncoding == other.decoderOutputEncoding &&
                dspFormat == other.dspFormat &&
                directUsbProbe == other.directUsbProbe &&
                directSupport == other.directSupport &&
                directPlaybackSupported == other.directPlaybackSupported &&
                directPlaybackSelected == other.directPlaybackSelected &&
                directPlaybackRequested == other.directPlaybackRequested &&
                directPlaybackActual == other.directPlaybackActual &&
                directPlaybackRejected == other.directPlaybackRejected &&
                directPlaybackDetail == other.directPlaybackDetail &&
                halFormat == other.halFormat &&
                usbEndpointFormat == other.usbEndpointFormat &&
                bluetoothTelemetry == other.bluetoothTelemetry &&
                negotiationResult == other.negotiationResult
        }

        override fun hashCode(): Int {
            var result = sink.hashCode()
            result = 31 * result + requestedPcmMode.hashCode()
            result = 31 * result + deviceName.hashCode()
            result = 31 * result + (activeDeviceId ?: 0)
            result = 31 * result + sampleRatesHz.contentHashCode()
            result = 31 * result + encodings.contentHashCode()
            result = 31 * result + isUsb.hashCode()
            result = 31 * result + routeKind.hashCode()
            result = 31 * result + requestedTransportType.hashCode()
            result = 31 * result + transportType.hashCode()
            result = 31 * result + (actualEncoding ?: 0)
            result = 31 * result + (actualSampleRateHz ?: 0)
            result = 31 * result + floatFallback.hashCode()
            result = 31 * result + fallbackReason.hashCode()
            result = 31 * result + (fallbackDetail?.hashCode() ?: 0)
            result = 31 * result + (systemMixerRateHz ?: 0)
            result = 31 * result + (decoderName?.hashCode() ?: 0)
            result = 31 * result + (bufferSize ?: 0)
            result = 31 * result + (decoderOutputEncoding?.hashCode() ?: 0)
            result = 31 * result + dspFormat.hashCode()
            result = 31 * result + (directUsbProbe?.hashCode() ?: 0)
            result = 31 * result + (directSupport?.hashCode() ?: 0)
            result = 31 * result + directPlaybackSupported.hashCode()
            result = 31 * result + directPlaybackSelected.hashCode()
            result = 31 * result + directPlaybackRequested.hashCode()
            result = 31 * result + directPlaybackActual.hashCode()
            result = 31 * result + directPlaybackRejected.hashCode()
            result = 31 * result + (directPlaybackDetail?.hashCode() ?: 0)
            result = 31 * result + (halFormat?.hashCode() ?: 0)
            result = 31 * result + (usbEndpointFormat?.hashCode() ?: 0)
            result = 31 * result + (bluetoothTelemetry?.hashCode() ?: 0)
            result = 31 * result + (negotiationResult?.hashCode() ?: 0)
            return result
        }
    }

    val current = MutableStateFlow(Snapshot())

    fun publish(
        manager: AudioManager,
        requestedPcmMode: OutputPcmMode,
        preferred: AudioDeviceInfo?,
        floatEnabled: Boolean,
        routeKind: AudioRouting.Kind? = null,
        directUsbProbe: DirectUsbProbeResult? = null,
        directSupport: DirectAudioProbe.DirectSupport? = null,
        bluetoothTelemetry: BluetoothTelemetry? = null,
        systemMixerRateHz: Int? = null,
    ) {
        val device = preferred ?: manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.isSink }
        val isUsbDevice = device?.type in setOf(
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
        )
        val computedRouteKind = routeKind ?: when {
            isUsbDevice -> AudioRouting.Kind.USB
            device?.type in setOf(
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER,
                AudioDeviceInfo.TYPE_BLE_BROADCAST,
                AudioDeviceInfo.TYPE_HEARING_AID,
            ) -> AudioRouting.Kind.BLUETOOTH
            device?.type in setOf(
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_LINE_ANALOG,
                AudioDeviceInfo.TYPE_AUX_LINE,
            ) -> AudioRouting.Kind.WIRED
            device?.type in setOf(
                AudioDeviceInfo.TYPE_HDMI,
                AudioDeviceInfo.TYPE_HDMI_ARC,
            ) -> AudioRouting.Kind.HDMI
            else -> AudioRouting.Kind.PHONE
        }

        val isDirectUsbViable = directUsbProbe?.isViable == true && isUsbDevice
        val isDirectAudioTrack = directSupport?.isDirectSupported == true && computedRouteKind != AudioRouting.Kind.PHONE
        val transport = when {
            isDirectUsbViable -> TransportType.DIRECT_USB
            isDirectAudioTrack -> TransportType.AUDIO_TRACK_DIRECT
            else -> TransportType.AUDIO_TRACK
        }

        val fallback = requestedPcmMode == OutputPcmMode.FLOAT_32 && !floatEnabled
        val (reason, detail) = when {
            isDirectUsbViable -> Pair(FallbackReason.NONE, null)
            computedRouteKind == AudioRouting.Kind.PHONE && fallback ->
                Pair(FallbackReason.ROUTE_LIMITATION, "Speaker output capped at 16-bit to avoid OEM mixer distortion")
            computedRouteKind == AudioRouting.Kind.USB && fallback ->
                Pair(
                    if (directUsbProbe?.isViable == false) FallbackReason.DIRECT_USB_UNAVAILABLE else FallbackReason.ROUTE_LIMITATION,
                    directUsbProbe?.diagnosticReason ?: "USB route does not advertise Float32 output",
                )
            computedRouteKind == AudioRouting.Kind.BLUETOOTH && fallback -> {
                val btCode = bluetoothTelemetry?.codecName?.let { " ($it)" }.orEmpty()
                Pair(FallbackReason.ROUTE_LIMITATION, "Bluetooth route$btCode does not advertise Float32 output")
            }
            fallback ->
                Pair(FallbackReason.ROUTE_LIMITATION, "${computedRouteKind.name} route does not advertise Float32")
            else ->
                Pair(FallbackReason.NONE, null)
        }

        val baseSnapshot = current.value.copy(
            requestedPcmMode = requestedPcmMode,
            deviceName = device?.productName?.toString()?.ifBlank { null } ?: "System default",
            activeDeviceId = device?.id,
            sampleRatesHz = device?.sampleRates ?: IntArray(0),
            encodings = device?.encodings ?: IntArray(0),
            isUsb = isUsbDevice,
            routeKind = computedRouteKind,
            requestedTransportType = transport,
            transportType = transport,
            floatFallback = fallback,
            fallbackReason = reason,
            fallbackDetail = detail,
            systemMixerRateHz = systemMixerRateHz ?: if (isUsbDevice && !isDirectAudioTrack && !isDirectUsbViable) 48000 else null,
            directUsbProbe = directUsbProbe,
            directSupport = directSupport,
            directPlaybackSupported = directSupport?.isDirectSupported == true,
            directPlaybackSelected = isDirectAudioTrack,
            directPlaybackDetail = directSupport?.description,
            bluetoothTelemetry = bluetoothTelemetry,
        )
        current.value = evaluateActualPath(baseSnapshot)
    }

    fun publishNegotiation(result: OutputNegotiationResult) {
        val baseSnapshot = current.value.copy(
            negotiationResult = result,
            routeKind = result.route.kind,
            requestedTransportType = result.output.transport,
            transportType = result.output.transport,
            directPlaybackSupported = result.route.directSupport.isDirectSupported,
            directPlaybackSelected = result.output.isDirect && result.output.transport != TransportType.DIRECT_USB,
            directPlaybackDetail = result.route.directSupport.description,
            bluetoothTelemetry = result.route.bluetoothTelemetry,
            fallbackReason = result.output.fallbackReason,
            fallbackDetail = result.output.fallbackDetail,
            systemMixerRateHz = result.output.systemMixerRateHz,
            decoderOutputEncoding = result.decoder.encoding,
            dspFormat = result.dsp.format,
        )
        current.value = evaluateActualPath(baseSnapshot)
    }

    fun publishDecoder(decoderName: String?) {
        current.value = current.value.copy(decoderName = decoderName)
    }

    fun publishDsp(decoderOutputEncoding: String?, dspFormat: String = "Float32") {
        current.value = current.value.copy(
            decoderOutputEncoding = decoderOutputEncoding,
            dspFormat = dspFormat,
        )
    }

    fun publishAudioTrack(encoding: Int, sampleRateHz: Int, bufferSize: Int? = null) {
        val isFloat = encoding == AudioFormat.ENCODING_PCM_FLOAT
        val baseSnapshot = current.value.copy(
            actualEncoding = encoding,
            actualSampleRateHz = sampleRateHz,
            bufferSize = bufferSize ?: current.value.bufferSize,
            floatFallback = current.value.requestedPcmMode == OutputPcmMode.FLOAT_32 && !isFloat,
        )
        current.value = evaluateActualPath(baseSnapshot)
    }

    /**
     * Authoritative runtime verification of whether the active AudioTrack path is genuinely direct
     * and bypassing AudioFlinger mixer, versus being rejected by AudioPolicy and placed on a MixerThread.
     */
    internal fun evaluateActualPath(snapshot: Snapshot): Snapshot {
        // Direct USB (userspace USB stream) directly communicates with USB endpoints,
        // bypassing Android AudioFlinger and AudioPolicy entirely.
        if (snapshot.transportType == TransportType.DIRECT_USB ||
            snapshot.requestedTransportType == TransportType.DIRECT_USB
        ) {
            return snapshot.copy(
                transportType = TransportType.DIRECT_USB,
                directPlaybackRequested = true,
                directPlaybackActual = true,
                directPlaybackRejected = false,
                directPlaybackSelected = false,
                systemMixerRateHz = null,
            )
        }

        val requestedDirect = snapshot.requestedTransportType == TransportType.AUDIO_TRACK_DIRECT ||
            snapshot.directPlaybackSelected ||
            (snapshot.directSupport?.isDirectSupported == true && snapshot.routeKind != AudioRouting.Kind.PHONE)

        val sampleRate = snapshot.actualSampleRateHz
            ?: snapshot.negotiationResult?.output?.sampleRateHz
            ?: snapshot.negotiationResult?.source?.sampleRateHz
            ?: 48000

        val encoding = snapshot.actualEncoding
            ?: when (snapshot.negotiationResult?.output?.encoding) {
                PcmEncoding.PCM_FLOAT -> AudioFormat.ENCODING_PCM_FLOAT
                PcmEncoding.PCM_24BIT_PACKED -> AudioFormat.ENCODING_PCM_24BIT_PACKED
                PcmEncoding.PCM_32BIT -> AudioFormat.ENCODING_PCM_32BIT
                PcmEncoding.PCM_16BIT -> AudioFormat.ENCODING_PCM_16BIT
                null -> null
            }

        val defaultMixerRate = snapshot.systemMixerRateHz ?: 48000

        return when (snapshot.routeKind) {
            AudioRouting.Kind.PHONE -> {
                snapshot.copy(
                    transportType = TransportType.AUDIO_TRACK,
                    directPlaybackRequested = false,
                    directPlaybackActual = false,
                    directPlaybackRejected = false,
                    directPlaybackSelected = false,
                    systemMixerRateHz = defaultMixerRate,
                    halFormat = "PCM24 packed",
                    usbEndpointFormat = null,
                )
            }
            AudioRouting.Kind.USB -> {
                val directSupported = snapshot.directSupport?.isDirectSupported == true
                val sampleRateSupported = snapshot.sampleRatesHz.isEmpty() ||
                    snapshot.sampleRatesHz.contains(sampleRate) ||
                    directSupported
                val encodingSupported = snapshot.encodings.isEmpty() ||
                    (encoding != null && snapshot.encodings.contains(encoding)) ||
                    directSupported
                val isFloatPcm = encoding == AudioFormat.ENCODING_PCM_FLOAT

                val maxUsbRate = snapshot.sampleRatesHz.maxOrNull() ?: 48000
                val usbEnc = when {
                    snapshot.encodings.contains(AudioFormat.ENCODING_PCM_32BIT) -> "PCM32"
                    snapshot.encodings.contains(AudioFormat.ENCODING_PCM_24BIT_PACKED) -> "PCM24"
                    snapshot.encodings.contains(AudioFormat.ENCODING_PCM_FLOAT) -> "Float32"
                    snapshot.encodings.contains(AudioFormat.ENCODING_PCM_16BIT) -> "PCM16"
                    else -> "PCM16"
                }
                val usbEndpointStr = "$usbEnc / $maxUsbRate Hz"

                val isGenuineDirect = requestedDirect &&
                    directSupported &&
                    sampleRateSupported &&
                    encodingSupported &&
                    !isFloatPcm

                if (isGenuineDirect) {
                    snapshot.copy(
                        transportType = TransportType.AUDIO_TRACK_DIRECT,
                        directPlaybackRequested = true,
                        directPlaybackActual = true,
                        directPlaybackRejected = false,
                        directPlaybackSelected = true,
                        systemMixerRateHz = null,
                        halFormat = null,
                        usbEndpointFormat = usbEndpointStr,
                    )
                } else if (requestedDirect || snapshot.directSupport?.isDirectSupported == true) {
                    snapshot.copy(
                        transportType = TransportType.AUDIO_TRACK,
                        directPlaybackRequested = true,
                        directPlaybackActual = false,
                        directPlaybackRejected = true,
                        directPlaybackSelected = false,
                        systemMixerRateHz = defaultMixerRate,
                        halFormat = "PCM24 packed",
                        usbEndpointFormat = usbEndpointStr,
                        fallbackReason = FallbackReason.ROUTE_LIMITATION,
                        fallbackDetail = "Direct playback unavailable for active USB device",
                    )
                } else {
                    snapshot.copy(
                        transportType = TransportType.AUDIO_TRACK,
                        directPlaybackRequested = false,
                        directPlaybackActual = false,
                        directPlaybackRejected = false,
                        directPlaybackSelected = false,
                        systemMixerRateHz = defaultMixerRate,
                        halFormat = "PCM24 packed",
                        usbEndpointFormat = usbEndpointStr,
                    )
                }
            }
            AudioRouting.Kind.BLUETOOTH -> {
                snapshot.copy(
                    transportType = TransportType.AUDIO_TRACK,
                    directPlaybackRequested = false,
                    directPlaybackActual = false,
                    directPlaybackRejected = false,
                    directPlaybackSelected = false,
                    systemMixerRateHz = defaultMixerRate,
                    halFormat = "PCM24 packed",
                    usbEndpointFormat = null,
                )
            }
            else -> {
                val sampleRateSupported = snapshot.sampleRatesHz.isEmpty() || snapshot.sampleRatesHz.contains(sampleRate)
                val isGenuineDirect = requestedDirect &&
                    snapshot.directSupport?.isDirectSupported == true &&
                    sampleRateSupported

                if (isGenuineDirect) {
                    snapshot.copy(
                        transportType = TransportType.AUDIO_TRACK_DIRECT,
                        directPlaybackRequested = true,
                        directPlaybackActual = true,
                        directPlaybackRejected = false,
                        directPlaybackSelected = true,
                        systemMixerRateHz = null,
                        halFormat = null,
                        usbEndpointFormat = null,
                    )
                } else {
                    snapshot.copy(
                        transportType = TransportType.AUDIO_TRACK,
                        directPlaybackRequested = requestedDirect,
                        directPlaybackActual = false,
                        directPlaybackRejected = requestedDirect,
                        directPlaybackSelected = false,
                        systemMixerRateHz = defaultMixerRate,
                        halFormat = "PCM24 packed",
                        usbEndpointFormat = null,
                        fallbackReason = if (requestedDirect) FallbackReason.ROUTE_LIMITATION else snapshot.fallbackReason,
                        fallbackDetail = if (requestedDirect) "Direct playback unavailable for active ${snapshot.routeKind.name} device" else snapshot.fallbackDetail,
                    )
                }
            }
        }
    }

    fun reset() {
        current.value = Snapshot()
    }

    fun encodingLabel(snapshot: Snapshot): String = when (snapshot.actualEncoding) {
        AudioFormat.ENCODING_PCM_FLOAT -> "32-bit float"
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24-bit PCM"
        AudioFormat.ENCODING_PCM_32BIT -> "32-bit PCM"
        AudioFormat.ENCODING_PCM_16BIT -> if (snapshot.floatFallback) "16-bit fallback" else "16-bit PCM"
        null -> if (snapshot.floatFallback) "16-bit fallback" else snapshot.requestedPcmMode.label
        else -> "PCM (${snapshot.actualEncoding})"
    }
}
