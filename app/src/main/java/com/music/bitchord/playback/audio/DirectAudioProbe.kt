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

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioProfile
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Live capability probe testing Android's official runtime direct-playback APIs (API 33+).
 *
 * Directly queries [AudioManager.getDirectPlaybackSupport] and [AudioManager.getDirectProfilesForAttributes]
 * to determine whether the OS audio policy and hardware HAL expose an unmixed, direct output
 * path for a given sample rate, channel count, and PCM format.
 */
object DirectAudioProbe {

    data class DirectSupport(
        val isDirectSupported: Boolean,
        val isOffloadSupported: Boolean,
        val supportsFloat: Boolean,
        val supportsPcm24: Boolean,
        val supportsPcm16: Boolean,
        val directProfiles: List<DirectProfileInfo> = emptyList(),
        val description: String,
    ) {
        companion object {
            val NONE = DirectSupport(
                isDirectSupported = false,
                isOffloadSupported = false,
                supportsFloat = false,
                supportsPcm24 = false,
                supportsPcm16 = false,
                description = "Direct playback not supported or API < 33",
            )
        }
    }

    data class DirectProfileInfo(
        val format: Int,
        val sampleRates: List<Int>,
        val channelMasks: List<Int>,
    )

    /**
     * Evaluates direct playback support for the specified audio stream parameters on the active route.
     *
     * @param audioManager System AudioManager service.
     * @param sampleRateHz Desired sample rate in Hz.
     * @param channelCount Channel count (typically 2 for stereo).
     * @param activeDevice Active output route AudioDeviceInfo, if known.
     */
    fun probeDirectSupport(
        audioManager: AudioManager?,
        sampleRateHz: Int,
        channelCount: Int,
        activeDevice: AudioDeviceInfo? = null,
    ): DirectSupport {
        if (audioManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return DirectSupport.NONE
        }

        return try {
            probeDirectSupportApi33(audioManager, sampleRateHz, channelCount, activeDevice)
        } catch (_: Throwable) {
            DirectSupport.NONE
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun probeDirectSupportApi33(
        audioManager: AudioManager,
        sampleRateHz: Int,
        channelCount: Int,
        activeDevice: AudioDeviceInfo?,
    ): DirectSupport {
        val channelMask = if (channelCount == 1) {
            AudioFormat.CHANNEL_OUT_MONO
        } else {
            AudioFormat.CHANNEL_OUT_STEREO
        }

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        // 1. Inspect all direct profiles for media attributes
        val directProfilesList = mutableListOf<DirectProfileInfo>()
        try {
            val profiles = audioManager.getDirectProfilesForAttributes(attributes)
            for (p in profiles) {
                directProfilesList.add(
                    DirectProfileInfo(
                        format = p.format,
                        sampleRates = p.sampleRates.toList(),
                        channelMasks = p.channelMasks.toList(),
                    ),
                )
            }
        } catch (_: Throwable) {
        }

        // 2. Query exact support for Float32, PCM24, and PCM16
        val floatFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRateHz)
            .setChannelMask(channelMask)
            .build()
        val floatSupport = AudioManager.getDirectPlaybackSupport(floatFormat, attributes)
        val supportsFloatDirect = (floatSupport != AudioManager.DIRECT_PLAYBACK_NOT_SUPPORTED)

        val pcm24Format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_24BIT_PACKED)
            .setSampleRate(sampleRateHz)
            .setChannelMask(channelMask)
            .build()
        val pcm24Support = AudioManager.getDirectPlaybackSupport(pcm24Format, attributes)
        val supportsPcm24Direct = (pcm24Support != AudioManager.DIRECT_PLAYBACK_NOT_SUPPORTED)

        val pcm16Format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRateHz)
            .setChannelMask(channelMask)
            .build()
        val pcm16Support = AudioManager.getDirectPlaybackSupport(pcm16Format, attributes)
        val supportsPcm16Direct = (pcm16Support != AudioManager.DIRECT_PLAYBACK_NOT_SUPPORTED)

        val anyDirect = supportsFloatDirect || supportsPcm24Direct || supportsPcm16Direct
        val anyOffload = (floatSupport and AudioManager.DIRECT_PLAYBACK_OFFLOAD_SUPPORTED) != 0 ||
            (pcm24Support and AudioManager.DIRECT_PLAYBACK_OFFLOAD_SUPPORTED) != 0 ||
            (pcm16Support and AudioManager.DIRECT_PLAYBACK_OFFLOAD_SUPPORTED) != 0

        val description = buildString {
            if (anyDirect) {
                append("Direct PCM supported: ")
                val formats = mutableListOf<String>()
                if (supportsFloatDirect) formats.add("Float32")
                if (supportsPcm24Direct) formats.add("24-bit")
                if (supportsPcm16Direct) formats.add("16-bit")
                append(formats.joinToString("/"))
                append(" @ $sampleRateHz Hz")
            } else {
                append("Direct PCM not exposed for $sampleRateHz Hz (AudioFlinger mixer route)")
            }
        }

        return DirectSupport(
            isDirectSupported = anyDirect,
            isOffloadSupported = anyOffload,
            supportsFloat = supportsFloatDirect,
            supportsPcm24 = supportsPcm24Direct,
            supportsPcm16 = supportsPcm16Direct,
            directProfiles = directProfilesList,
            description = description,
        )
    }
}
