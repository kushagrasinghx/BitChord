/*
 * BRIDGE — see tf/monochrome/android/audio/dsp/DspTypeStubs.kt.
 */
package tf.monochrome.android.audio.pipeline

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioPipelineMonitor {
    val stream: StateFlow<StreamFacts> = MutableStateFlow(StreamFacts()).asStateFlow()
    val decoderName: StateFlow<String?> = MutableStateFlow(null).asStateFlow()

    data class StreamFacts(
        val sampleRateHz: Int = 0,
        val channels: Int = 0,
    )
}

class OutputDeviceProbe {
    fun halSampleRateHz(): Int? = null

    val routed: StateFlow<String?> = MutableStateFlow(null).asStateFlow()
}
