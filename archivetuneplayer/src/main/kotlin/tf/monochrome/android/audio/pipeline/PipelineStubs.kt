/*
 * BRIDGE — see tf/monochrome/android/audio/dsp/DspTypeStubs.kt.
 *
 * The pipeline facts the copied stats sheet reads: DecodedStream and
 * RoutedOutput mirror ArchiveTune's shapes so ShowMediaInfo compiles
 * unmodified; the monitor never publishes anything, which renders every
 * pipeline row as its "not engaged" em dash.
 */
package tf.monochrome.android.audio.pipeline

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DecodedStream(
    val mimeType: String? = null,
    val sampleRate: Int? = null,
    val channelCount: Int? = null,
    val bitrate: Int? = null,
    val pcmBits: Int? = null,
    val pcmIsFloat: Boolean = false,
)

data class RoutedOutput(
    val name: String,
    val typeLabel: String,
)

class AudioPipelineMonitor {
    val stream: StateFlow<DecodedStream?> = MutableStateFlow<DecodedStream?>(null).asStateFlow()
    val decoderName: StateFlow<String?> = MutableStateFlow<String?>(null).asStateFlow()
}

class OutputDeviceProbe {
    fun halSampleRateHz(): Int? = null

    val routed: StateFlow<RoutedOutput?> = MutableStateFlow<RoutedOutput?>(null).asStateFlow()
}
