/*
 * BRIDGE — the LastWave USB DAC monitor's shape, kept inert. See
 * tf/monochrome/android/audio/dsp/DspTypeStubs.kt.
 */
package com.lastwave.app.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UsbDacMonitor {
    val state: StateFlow<State> = MutableStateFlow(State()).asStateFlow()

    data class State(
        val dac: Dac? = null,
    )

    data class Dac(
        val vendorId: Int = -1,
        val productId: Int = -1,
    )
}

class NativePcmAudioProcessor
