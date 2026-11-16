package com.music.bitchord.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Linux's answer to the Windows endpoint watcher (`window_jni.cpp`'s
 * `IMMNotificationClient`): notice that the set of outputs changed, and bump
 * [DesktopAudioDevices.systemChanged] so open lists re-enumerate and
 * [DesktopPlaybackEngine] reconfigures the line.
 *
 * Windows gets its event pushed by the OS. Linux offers no portable push for
 * this — the D-Bus route would mean speaking to PipeWire's private
 * `org.pipewire.PipeWire` protocol or parsing WirePlumper state, neither of
 * which a dependency-free watcher should — so this asks the kernel instead:
 * `/proc/asound/cards` names the cards ALSA (and so `default`, which PipeWire
 * occupies on a normal desktop) presents. Reading it every few seconds costs
 * almost nothing and misses nothing that matters; a Bluetooth or USB DAC
 * appearing is a card row, whether or not PipeWire was up when we started.
 *
 * What it deliberately does not do: watch the *default route's* target move
 * between existing cards (headphones vs speakers on one card, for instance).
 * On PipeWire the `default` mixer itself follows the desktop's routing, so an
 * open line on `default` already plays where the desktop says; only per-card
 * `plughw` lines need this watcher's reconfigure.
 */
internal object DesktopLinuxAudioWatcher {

    private const val CARDS = "/proc/asound/cards"
    private const val POLL_MS = 3_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var started = false

    /** Started once per process, from the engine's construction. */
    fun ensureStarted() {
        if (started || !DesktopPlatform.isLinux) return
        started = true
        scope.launch {
            var last = readCards()
            while (true) {
                delay(POLL_MS)
                val now = readCards()
                if (now != last) {
                    last = now
                    DesktopAudioDevices.systemChanged()
                    DesktopTrackLog.log("audio devices changed ($now)")
                }
            }
        }
    }

    /** The cards file's text, normalised so formatting churn does not count as a change. */
    private fun readCards(): String = runCatching {
        File(CARDS).readText().trim().replace(Regex("\\s+"), " ")
    }.getOrDefault("")
}