/*
 * Copyright (C) 2026 BitChord Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.music.bitchord.playback.audio.bitperfect

import android.media.AudioDeviceInfo
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer

/**
 * Sends each stream either to [bitPerfect] or to the app's ordinary sink.
 *
 * The choice is made per [configure], which Media3 calls for every new
 * format: a track the exclusive output can carry unchanged goes there, and
 * everything else — a lossy stream with encoder priming, a format the DAC
 * does not offer, any track while the mode is off — takes the [normal] path
 * exactly as before. With the mode off this class only forwards.
 *
 * Switching sinks mid-playlist hands over cleanly. The outgoing sink plays
 * out what it holds before the incoming one writes a frame, because both end
 * at the same DAC, and Android's bit-perfect mixer cannot be engaged under a
 * track that is still playing through the shared mixer, or released from
 * under one that is not.
 *
 * If the exclusive output fails — it could not open, or the route changed
 * under it — the stream continues on [normal] from the next buffer rather
 * than surfacing a playback error. After the direct USB driver lets a DAC go,
 * [normal] waits for Android to take it back, so nothing meant for the DAC
 * starts on the phone's speaker in between. Settings that both sinks must hold
 * (attributes, session, volume, listener) are always given to both, so
 * whichever one is chosen next is already up to date.
 */
@OptIn(UnstableApi::class)
internal class BitPerfectRoutingSink(
    private val normal: AudioSink,
    private val bitPerfect: BitPerfectAudioSink,
) : AudioSink {

    private var active: AudioSink = normal

    /** The sink being played out after a switch, before [active] writes. */
    private var retiring: AudioSink? = null
    private var lastConfig: AudioSink.AudioSinkConfig? = null
    private var playing = false

    /** Whether the current stream is going to the exclusive output. */
    val isBitPerfect: Boolean get() = active === bitPerfect

    override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
        lastConfig = audioSinkConfig
        val target = if (bitPerfect.canAccept(audioSinkConfig.format)) {
            try {
                bitPerfect.configure(audioSinkConfig)
                bitPerfect
            } catch (error: AudioSink.ConfigurationException) {
                Log.i(TAG, "Using normal output: ${error.message}")
                normal.configure(audioSinkConfig)
                normal
            }
        } else {
            normal.configure(audioSinkConfig)
            normal
        }
        switchTo(target)
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean {
        retiring?.let { outgoing ->
            outgoing.playToEndOfStream()
            if (!outgoing.isEnded()) return false
            outgoing.reset()
            retiring = null
            if (playing) active.play()
        }
        if (active !== bitPerfect) {
            // Straight after direct USB, until the DAC is an Android output again.
            if (bitPerfect.holdsNormalOutput()) return false
            return active.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }
        return try {
            bitPerfect.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        } catch (error: AudioSink.InitializationException) {
            fallBack(error)
            normal.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        } catch (error: AudioSink.WriteException) {
            fallBack(error)
            normal.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }
    }

    override fun playToEndOfStream() {
        retiring?.let { outgoing ->
            outgoing.playToEndOfStream()
            if (!outgoing.isEnded()) return
            outgoing.reset()
            retiring = null
            if (playing) active.play()
        }
        if (active !== bitPerfect) return active.playToEndOfStream()
        try {
            bitPerfect.playToEndOfStream()
        } catch (error: AudioSink.WriteException) {
            fallBack(error)
        }
    }

    override fun isEnded(): Boolean = retiring == null && active.isEnded()

    override fun hasPendingData(): Boolean = retiring?.hasPendingData() == true || active.hasPendingData()

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long =
        (retiring ?: active).getCurrentPositionUs(sourceEnded)

    override fun play() {
        playing = true
        (retiring ?: active).play()
    }

    override fun pause() {
        playing = false
        retiring?.pause()
        active.pause()
    }

    override fun flush() {
        retiring?.let { it.flush(); it.reset() }
        retiring = null
        active.flush()
    }

    override fun reset() {
        retiring?.reset()
        retiring = null
        normal.reset()
        bitPerfect.reset()
    }

    override fun release() {
        normal.release()
        bitPerfect.release()
    }

    override fun handleDiscontinuity() = active.handleDiscontinuity()

    override fun supportsFormat(format: Format): Boolean =
        bitPerfect.canAccept(format) || normal.supportsFormat(format)

    /**
     * Answered for the exclusive output first: this is what tells a FLAC
     * decoder it may emit float, the only form in which Android's decoders
     * hand over a 24-bit sample intact.
     */
    override fun getFormatSupport(format: Format): Int =
        if (bitPerfect.canAccept(format)) {
            AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        } else {
            normal.getFormatSupport(format)
        }

    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport =
        normal.getFormatOffloadSupport(format)

    override fun getAudioTrackBufferSizeUs(): Long = active.getAudioTrackBufferSizeUs()

    override fun getPlaybackParameters(): PlaybackParameters = active.playbackParameters

    override fun getSkipSilenceEnabled(): Boolean = active.skipSilenceEnabled

    override fun getAudioAttributes(): AudioAttributes? = normal.audioAttributes

    override fun getAudioCapabilities(): AudioCapabilities? = normal.audioCapabilities

    override fun setListener(listener: AudioSink.Listener) = both { it.setListener(listener) }
    override fun setPlayerId(playerId: PlayerId?) = both { it.setPlayerId(playerId) }
    override fun setClock(clock: Clock) = both { it.setClock(clock) }
    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) =
        both { it.setPlaybackParameters(playbackParameters) }
    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) =
        both { it.setSkipSilenceEnabled(skipSilenceEnabled) }
    override fun setAudioAttributes(audioAttributes: AudioAttributes) = both { it.setAudioAttributes(audioAttributes) }
    override fun setAudioSessionId(audioSessionId: Int) = both { it.setAudioSessionId(audioSessionId) }
    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) = both { it.setAuxEffectInfo(auxEffectInfo) }
    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) =
        both { it.setPreferredDevice(audioDeviceInfo) }
    override fun setVirtualDeviceId(virtualDeviceId: Int) = both { it.setVirtualDeviceId(virtualDeviceId) }
    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) =
        both { it.setOutputStreamOffsetUs(outputStreamOffsetUs) }
    override fun setVolume(volume: Float) = both { it.setVolume(volume) }
    override fun setOffloadMode(offloadMode: Int) = normal.setOffloadMode(offloadMode)
    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) =
        normal.setOffloadDelayPadding(delayInFrames, paddingInFrames)
    override fun setAudioOutputProvider(audioOutputProvider: AudioOutputProvider) =
        normal.setAudioOutputProvider(audioOutputProvider)
    override fun enableTunnelingV21() = normal.enableTunnelingV21()
    override fun disableTunneling() = normal.disableTunneling()

    private fun switchTo(target: AudioSink) {
        if (target === active) return
        val outgoing = active
        active = target
        if (outgoing.hasPendingData()) {
            // Gapless boundary: let the previous track finish where it is playing.
            retiring = outgoing
        } else {
            outgoing.reset()
            if (playing) target.play()
        }
    }

    /** The exclusive output failed; the stream carries on through [normal]. */
    private fun fallBack(error: Exception) {
        Log.w(TAG, "Bit-perfect output failed; continuing on normal output", error)
        bitPerfect.reset()
        active = normal
        lastConfig?.let(normal::configure)
        if (playing) normal.play()
    }

    private inline fun both(action: (AudioSink) -> Unit) {
        action(normal)
        action(bitPerfect)
    }

    private companion object {
        const val TAG = "BitPerfectRoutingSink"
    }
}
