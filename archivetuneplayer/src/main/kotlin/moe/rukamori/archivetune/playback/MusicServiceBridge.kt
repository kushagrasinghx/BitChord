/*
 * BRIDGE (not an ArchiveTune file).
 *
 * ArchiveTune's player talks to its MusicService through ~20 members. That
 * service is a 10k-line playback engine this port does not carry; what the
 * copied player sources actually reach is mirrored here, backed by the host
 * bridge and the sleep timer (whose class is ArchiveTune's own, copied
 * verbatim). Everything the player can't reach in this app — Discord sync,
 * the multi-source resolvers, the infinite-queue loader — is inert.
 */

package moe.rukamori.archivetune.playback

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.rukamori.archivetune.constants.AudioSourceType
import moe.rukamori.archivetune.bridge.ArchiveTuneBridge
import moe.rukamori.archivetune.models.ActiveOutputDevice
import moe.rukamori.archivetune.models.PlayerOutputDevice
import moe.rukamori.archivetune.playback.EqCapabilities
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class MusicService(
    scope: CoroutineScope,
) {
    private val host get() = ArchiveTuneBridge.get()

    val sleepTimer = SleepTimer(
        scope = scope,
        player = host?.player() ?: PlaceholderPlayer,
        service = this,
    )

    /** The controller arrived (or changed) — keep the timer attached to it. */
    fun attachPlayer() {
        host?.player()?.let { sleepTimer.player = it }
    }

    fun pauseFromSleepTimer() {
        host?.pauseFromSleepTimer()
        host?.player()?.pause()
    }

    val crossfadeMixing: StateFlow<Boolean> = MutableStateFlow(false)

    val infiniteQueueLoading = MutableStateFlow(false)

    fun onInfiniteQueueDisabled() {}

    fun onInfiniteQueueEnabled(seedMediaId: String? = null) {}

    fun toggleLike() {
        host?.toggleLike()
    }

    fun stopAndClearPlayback(clearPersistentState: Boolean = false) {
        host?.stopAndClearPlayback()
    }

    fun refreshActiveDevice() {}

    fun forceDiscordSync(reason: String) {}

    val activeAudioDevice: StateFlow<ActiveOutputDevice> =
        MutableStateFlow(
            ActiveOutputDevice(PlayerOutputDevice.Unknown, PlayerOutputDevice.Unknown.defaultName),
        ).asStateFlow()

    // ---- The equalizer hook the player's equalizer sheet observes. No
    //      system equalizer is engaged through this port, so the
    //      capabilities stay absent and the presets apply to nothing.

    val eqCapabilities: StateFlow<EqCapabilities?> = MutableStateFlow(null)

    fun applyEqFlatPreset() {}

    fun applySystemEqPreset(presetIndex: Int) {}

    // ---- Multi-source resolution: ArchiveTune's per-song source picking.
    //      This app resolves streams its own way; the picker stays empty.

    fun availableSourcesForSong(mediaId: String): List<AudioSourceType> = emptyList()

    val resolvedSourcesRevision: StateFlow<Long> = MutableStateFlow(0L)

    fun refreshSourcesForSong(mediaId: String) {}

    fun setSongSourceOverride(
        mediaId: String,
        source: AudioSourceType?,
    ) {}

    fun setSongSourceOverrideWithQobuzTrackId(
        mediaId: String,
        source: AudioSourceType?,
        qobuzTrackId: String?,
    ) {}

    fun setSongSourceOverrideWithQobuzBackupVideoId(
        mediaId: String,
        source: AudioSourceType?,
        qobuzBackupVideoId: String?,
    ) {}

    fun setSongSourceOverrideWithDeezerTrackId(
        mediaId: String,
        source: AudioSourceType?,
        deezerTrackId: String?,
    ) {}

    fun setSongSourceOverrideWithTidalTrackId(
        mediaId: String,
        source: AudioSourceType?,
        tidalTrackId: String?,
    ) {}
}

/**
 * A Player that exists only so [SleepTimer] can be constructed before the
 * host's controller is up on a cold start: stopped, silent, empty timeline.
 * A reflective proxy rather than a hand-written implementation, so it stays
 * valid whatever members the Player interface grows.
 */
internal val PlaceholderPlayer: Player =
    Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java),
        InvocationHandler { _, method, _ ->
            when (method.name) {
                "equals" -> false
                "hashCode" -> System.identityHashCode(PlaceholderPlayer)
                "toString" -> "ArchiveTuneBridgePlaceholderPlayer"
                else -> defaultValue(method)
            }
        },
    ) as Player

private fun defaultValue(method: Method): Any? =
    when (method.returnType.name) {
        "boolean" -> false
        "int", "long", "short", "byte", "char" -> 0
        "float", "double" -> 0.0
        "void" -> null
        else -> null
    }
