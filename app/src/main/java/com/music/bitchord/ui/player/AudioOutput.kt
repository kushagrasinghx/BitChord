package com.music.bitchord.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.playback.AudioOutputStatus
import com.music.bitchord.playback.AudioRouting
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The outputs this phone can play music through, kept current while on screen.
 *
 * Several sources, because no one of them is both early and correct:
 *
 *  - `AudioDeviceCallback` sees everything the framework knows about, but it
 *    can arrive after the fact.
 *  - `ACTION_AUDIO_BECOMING_NOISY` is the *earliest* notice that an output has
 *    gone — the system sends it precisely so players can react before the sound
 *    lands on the speaker — which is what makes a disconnect show up at once
 *    rather than whenever the next callback happens to fire.
 *  - The headset and HDMI plug broadcasts land sooner than the callback on some
 *    devices, and ACL announces a Bluetooth connection before the audio device
 *    behind it exists at all.
 *
 * Every one of them is followed by [SETTLE_MS] re-reads as well as an immediate
 * one, because the event and the truth are not simultaneous: a headset is
 * announced while its A2DP profile is still negotiating and is not in
 * `getDevices` yet, and on the way out it lingers for a moment after the
 * broadcast. Reading once, on the event, is what made a disconnect take so long
 * to show — the read happened, saw the device still listed, and believed it.
 *
 * Which row is *active*, though, is not decided here a second time. It comes
 * from [AudioOutputStatus.activeDeviceId] — the exact device
 * `PlaybackService.resolveActiveOutputDevice` just handed the player — because
 * this function's own [AudioRouting.activeOf] and that resolution can name two
 * different devices: `activeOf` falls back through Bluetooth-then-wired-then-USB
 * on its own, while the player also weighs [com.music.bitchord.data.settings.AppSettings.preferUsbDac].
 * Two independent guesses is exactly what let the picker mark one output active
 * while the caption under the transport kept naming another. The picker still
 * decides *which devices exist to choose between*; only which one is lit comes
 * from the pipeline.
 */
@Composable
internal fun rememberAndroidAudioOutputs(): List<AudioOutputDevice> {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    // Read here as well as observed, so a device that comes back with a new id
    // while this is on screen re-marks the active row without waiting for a
    // broadcast that has already been and gone.
    val selectedId by AudioRouting.selectedId.collectAsStateWithLifecycle()
    var outputs by remember(manager) { mutableStateOf(AudioRouting.outputs(manager)) }

    val pipelineActiveId by remember {
        AudioOutputStatus.current.map { it.activeDeviceId }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = AudioOutputStatus.current.value.activeDeviceId)

    DisposableEffect(manager, selectedId) {
        fun refresh() {
            outputs = AudioRouting.outputs(manager)
        }
        refresh()

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        // Now, and again once the framework has caught up with itself.
        fun refreshSoon() {
            refresh()
            SETTLE_MS.forEach { delay -> handler.postDelayed(::refresh, delay) }
        }

        val deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) = refreshSoon()
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) = refreshSoon()
        }
        manager.registerAudioDeviceCallback(deviceCallback, handler)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = refreshSoon()
        }
        val filter = IntentFilter().apply {
            // First out of the gate when an output disappears.
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_HDMI_AUDIO_PLUG)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            manager.unregisterAudioDeviceCallback(deviceCallback)
            runCatching { context.unregisterReceiver(receiver) }
            handler.removeCallbacksAndMessages(null)
        }
    }

    // Only overridden once the pipeline has named a device this list actually
    // has a row for — early in the service's life, before anything has been
    // resolved once, [pipelineActiveId] is null and the list keeps the answer
    // [AudioRouting.outputs] already marked.
    return remember(outputs, pipelineActiveId) {
        if (pipelineActiveId != null && outputs.any { it.id == pipelineActiveId }) {
            outputs.map { it.copy(isActive = it.id == pipelineActiveId) }
        } else {
            outputs
        }.map { it.toOutputDevice() }
    }
}

private fun AudioRouting.Device.toOutputDevice() = AudioOutputDevice(
    id = id,
    name = name,
    kind = when (kind) {
        AudioRouting.Kind.PHONE -> AudioOutputKind.PHONE
        AudioRouting.Kind.WIRED -> AudioOutputKind.WIRED
        AudioRouting.Kind.USB -> AudioOutputKind.USB
        AudioRouting.Kind.BLUETOOTH -> AudioOutputKind.BLUETOOTH
        AudioRouting.Kind.HDMI -> AudioOutputKind.HDMI
        AudioRouting.Kind.OTHER -> AudioOutputKind.OTHER
    },
    isActive = isActive,
)

/**
 * When to look again after something changed, in milliseconds.
 *
 * Three reads rather than one: the first catches the common case immediately,
 * and the later two cover a Bluetooth profile that is still negotiating — a
 * headset is announced before it can be played to and is listed for a moment
 * after it is gone.
 */
private val SETTLE_MS = longArrayOf(350L, 1_200L, 2_500L)

/**
 * Opens the output picker, asking for the Bluetooth permission the first time.
 *
 * The permission is for names, not for switching: without it the framework
 * reports a paired headset generically, and a picker whose rows all read
 * "Bluetooth" is worse than no picker. So a refusal keeps the drawer shut
 * rather than opening a list that cannot say what anything is.
 *
 * Asked on the tap rather than at startup — nobody can judge a request for
 * nearby devices out of the blue, and everybody can judge one that arrives the
 * moment they ask where the music is playing.
 *
 * One consequence worth knowing: Android answers a permanently-denied request
 * immediately and silently, so for somebody who has denied it twice the
 * headphones glyph does nothing. That is this design, not a bug — the way back
 * is the app's own settings page in Android.
 */
@Composable
internal fun rememberAndroidOutputPicker(onOpen: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onOpen() }
    return {
        if (bluetoothNamesAllowed(context)) onOpen() else ask.launch(BLUETOOTH_CONNECT)
    }
}

private fun bluetoothNamesAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

private const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
