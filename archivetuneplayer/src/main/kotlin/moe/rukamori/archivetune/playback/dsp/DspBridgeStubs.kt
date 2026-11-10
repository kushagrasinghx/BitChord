/*
 * BRIDGE — stubs for ArchiveTune's audio-DSP engine chain.
 *
 * The live-audio-chain pill and the nerd-stats sheet observe this machinery
 * through the copied UI. The engines themselves (Tryptify, LastWave, the USB
 * userspace driver) run inside ArchiveTune's MusicService with its native
 * libraries — none of which exist here. The stubs keep every observed value
 * at "nothing engaged", which is exactly what the pill renders when no DSP
 * is running: it simply stays hidden.
 */

package moe.rukamori.archivetune.playback.dsp

import androidx.media3.common.C

class AudioEngineRouterProcessor {
    enum class Engine {
        NONE,
        TRYPTIFY,
        LASTWAVE,
    }
}

object EngineRuntime {
    val activeEngineState: AudioEngineRouterProcessor.Engine = AudioEngineRouterProcessor.Engine.NONE
    val wantedEngineState: AudioEngineRouterProcessor.Engine = AudioEngineRouterProcessor.Engine.NONE
    val tryptifyAvailableState: Boolean = false
    val lastwaveAvailableState: Boolean = false
    val revision: Long = 0L
    val usbExclusiveActive: Boolean = false
    val tryptifyUsbPinActive: Boolean = false
    val lastwaveUsbRateHz: Int = 0
    val lastwaveUsbBitsPerSample: Int = 0
    val tryptifyUsbStream: BypassDiagnostics? = null
    val bitPerfectSinkRouteActive: Boolean = false
    val sinkDecodedEncoding: Int = C.ENCODING_PCM_16BIT
    val outputFloatState: Boolean = false
}

/** The USB stream facts the pill prints when an engine owns the output. */
interface BypassDiagnostics {
    val sampleRateHz: Int
    val bitsPerSample: Int
}

object BitPerfectRuntime {
    val requested: Boolean = false

    val status: Status = Status.idle()

    data class Status(
        val sourceEncoding: Int = C.ENCODING_PCM_16BIT,
        val sourceBitDepth: Int = 16,
        val sourceSampleRate: Int = 0,
        val sourceIsLossy: Boolean = false,
        val decodedEncoding: Int = C.ENCODING_PCM_16BIT,
        val decodedBitDepth: Int = 16,
        val outputEncoding: Int = C.ENCODING_PCM_16BIT,
        val outputBitDepth: Int = 16,
        val outputSampleRate: Int = 0,
        val channels: Int = 2,
        val directPlaybackSupported: Boolean = false,
        val nativeRateMatched: Boolean = false,
        val resamplerActive: Boolean = false,
        val dspActive: Boolean = false,
        val softwareVolumeActive: Boolean = false,
        val usbExclusiveActive: Boolean = false,
        val mixerBitPerfectActive: Boolean = false,
        val verifiedBitPerfect: Boolean = false,
        val failureReason: String? = null,
    ) {
        companion object {
            fun idle(): Status = Status()
        }
    }
}
