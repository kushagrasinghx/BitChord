/*
 * BRIDGE — tf.monochrome DSP types the copied stats sheet observes. The
 * engine they belong to is ArchiveTune's; nothing of it runs here, so every
 * observation is empty. See playback/dsp/DspBridgeStubs.kt.
 *
 * The data classes mirror ArchiveTune's shapes (ChannelDetectorProcessor's
 * ChannelState, with the FloatArray-aware equals/hashCode) so the copied
 * ShowMediaInfo compiles unmodified; the flows simply never emit a value.
 */

package tf.monochrome.android.audio.dsp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChannelDetectorProcessor {
    data class ChannelState(
        val channelCount: Int,
        val sampleRate: Int,
        val isFloat: Boolean,
        val layoutName: String,
        val channelNames: List<String>,
        val peaksDb: FloatArray,
    ) {
        override fun equals(other: Any?): Boolean =
            other is ChannelState && other.channelCount == channelCount &&
                other.sampleRate == sampleRate && other.isFloat == isFloat &&
                other.layoutName == layoutName && other.channelNames == channelNames &&
                other.peaksDb.contentEquals(peaksDb)

        override fun hashCode(): Int = 31 * channelCount + peaksDb.contentHashCode()
    }

    private val _state = MutableStateFlow<ChannelState?>(null)
    val state: StateFlow<ChannelState?> = _state.asStateFlow()
}

class DownmixProcessor

object DspNativeLoader {
    fun ensureLoaded() {}
}

class MixBusProcessor

class SpectrumAnalyzerTap {
    val spectrum: StateFlow<FloatArray> = MutableStateFlow(FloatArray(0)).asStateFlow()
}
