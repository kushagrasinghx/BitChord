/*
 * BRIDGE — tf.monochrome DSP types the copied stats sheet observes. The
 * engine they belong to is ArchiveTune's; nothing of it runs here, so every
 * observation is empty. See playback/dsp/DspBridgeStubs.kt.
 */

package tf.monochrome.android.audio.dsp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChannelDetectorProcessor {
    val state: StateFlow<State> = MutableStateFlow(State()).asStateFlow()

    data class State(
        val channelCount: Int = 0,
        val activeChannels: Int = 0,
    )
}

class DownmixProcessor

object DspNativeLoader {
    fun ensureLoaded() {}
}

class MixBusProcessor

class SpectrumAnalyzerTap {
    val spectrum: StateFlow<FloatArray> = MutableStateFlow(FloatArray(0)).asStateFlow()
}
