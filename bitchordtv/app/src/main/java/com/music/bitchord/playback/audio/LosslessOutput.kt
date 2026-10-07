package com.music.bitchord.playback.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.music.bitchord.data.TrackLog
import com.music.bitchord.playback.audio.bluetooth.BluetoothAudioTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Whether the phone has an output connected right now that can carry lossless
 * audio — the test behind an addon's `checkValidLossless`.
 *
 * Lossless here means the file's samples reach the listener's converter
 * without being re-encoded into a lossy format first. A cable does that by
 * construction; Bluetooth only does it — or comes close enough that addons
 * asking for this accept it — on a handful of high-bitrate codecs:
 *
 *  - **Wired:** 3.5mm headphones or headset, analog/digital line out, aux.
 *  - **USB:** USB-C headphones, USB DACs and USB audio accessories.
 *  - **HDMI:** HDMI, ARC and eARC (a TV, receiver or soundbar).
 *  - **Dock:** digital and analog docks.
 *  - **Bluetooth A2DP on LDAC, LHDC (V3/V4/V5) or aptX Lossless.** SBC, AAC,
 *    aptX, aptX HD, LC3 (LE Audio) and Opus are all lossy at bitrates well
 *    below CD audio, and do not count.
 *
 * The phone's own speaker, earpiece, hearing aids, casting and BLE Audio do
 * not count either. Bluetooth is only counted when Android names the codec,
 * which needs the Nearby devices permission on Android 12+; without it the
 * codec is unknown, and an unknown codec is reported as such rather than
 * guessed — see [State.bluetoothCodecUnknown].
 */
object LosslessOutput {

    enum class Kind { WIRED, USB, HDMI, DOCK, BLUETOOTH }

    data class State(
        val capable: Boolean = false,
        /** What qualified, when something did. */
        val via: Kind? = null,
        /** The Bluetooth codec in use, when Android named one. */
        val bluetoothCodec: String? = null,
        /** A Bluetooth output is connected but its codec could not be read — usually a missing permission. */
        val bluetoothCodecUnknown: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** The answer every gate reads. False until [init] has looked. */
    val capable: Boolean get() = _state.value.capable

    private var audioManager: AudioManager? = null
    private var tracker: BluetoothAudioTracker? = null

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }

    fun init(context: Context) {
        if (audioManager != null) return
        val app = context.applicationContext
        val manager = app.getSystemService(AudioManager::class.java) ?: return
        audioManager = manager
        manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        // Its own tracker rather than the playback service's: the gate has to
        // be right on the sources screen with nothing playing, when that one
        // is not running.
        val bluetooth = BluetoothAudioTracker(app).also { it.start() }
        tracker = bluetooth
        // Made here, not as a field: plain-JVM tests read [capable] through
        // the source registry, and have no main dispatcher to build one on.
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            .launch { bluetooth.telemetry.collect { recompute() } }
        recompute()
    }

    /** Asks Bluetooth again — after the Nearby devices permission is granted, say. */
    fun refresh() {
        tracker?.refreshCurrentDevice()
        recompute()
    }

    private fun recompute() {
        val outputs = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
            .filter { it.isSink }
            .map { it.type }
            .toSet()
        val telemetry = tracker?.telemetry?.value
        val next = decide(outputs, telemetry?.codecName?.takeIf { telemetry.hasNamedCodec })
        if (next != _state.value) {
            TrackLog.d(TAG, "lossless output: $next (outputs=$outputs)")
            _state.value = next
        }
    }

    /**
     * The decision itself, from the connected output types and the Bluetooth
     * codec Android named (null if it named none). Pure, for tests.
     */
    internal fun decide(outputTypes: Set<Int>, bluetoothCodec: String?): State {
        val cabled = when {
            outputTypes.any { it in WIRED } -> Kind.WIRED
            outputTypes.any { it in USB } -> Kind.USB
            outputTypes.any { it in HDMI } -> Kind.HDMI
            outputTypes.any { it in DOCK } -> Kind.DOCK
            else -> null
        }
        if (cabled != null) return State(capable = true, via = cabled, bluetoothCodec = bluetoothCodec)
        if (AudioDeviceInfo.TYPE_BLUETOOTH_A2DP !in outputTypes) return State()
        if (bluetoothCodec == null) return State(bluetoothCodecUnknown = true)
        return State(
            capable = isLosslessBluetoothCodec(bluetoothCodec),
            via = Kind.BLUETOOTH.takeIf { isLosslessBluetoothCodec(bluetoothCodec) },
            bluetoothCodec = bluetoothCodec,
        )
    }

    /** LDAC, any LHDC generation, or aptX Lossless — by the name Android reports. */
    internal fun isLosslessBluetoothCodec(name: String): Boolean {
        val codec = name.uppercase(Locale.ROOT)
        return "LDAC" in codec || "LHDC" in codec || ("APTX" in codec && "LOSSLESS" in codec)
    }

    // AudioDeviceInfo constants, by value so the newer ones compile into older
    // API levels as the plain ints they are; a phone that predates a type
    // simply never reports it.
    private val WIRED = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL,
        AudioDeviceInfo.TYPE_AUX_LINE,
    )
    private val USB = setOf(
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
    )
    private val HDMI = setOf(
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        29, // TYPE_HDMI_EARC, API 31
    )
    private val DOCK = setOf(
        AudioDeviceInfo.TYPE_DOCK,
        31, // TYPE_DOCK_ANALOG, API 34
    )

    private const val TAG = "BitChord"
}
