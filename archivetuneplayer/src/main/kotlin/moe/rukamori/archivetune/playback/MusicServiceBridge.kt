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

import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.rukamori.archivetune.constants.AudioSourceType
import moe.rukamori.archivetune.audiosource.CurrentStreamInfo
import moe.rukamori.archivetune.bridge.ArchiveTuneBridge
import moe.rukamori.archivetune.models.ActiveOutputDevice
import moe.rukamori.archivetune.models.MediaMetadata
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

    // ---- Live-track facts the stats sheet (ShowMediaInfo) reads while the
    //      song it describes is the one playing. The host bridge does not
    //      expose ArchiveTune's stream probing, so they stay at "unknown",
    //      which is also what the sheet shows for a song that finished
    //      playing — the non-live rows still render from the database.

    val currentMediaMetadata: StateFlow<MediaMetadata?> = MutableStateFlow(null)

    val currentStreamInfo: StateFlow<CurrentStreamInfo?> = MutableStateFlow(null)

    val liveNormalizeFactor: StateFlow<Float> = MutableStateFlow(1f)

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
 *
 * Every answer has to be the exact type the interface declares: Kotlin
 * callers unbox `getCurrentPosition()` as a long and read
 * `currentTimeline.isEmpty` without a null check, so an Integer 0 or a null
 * Timeline here is a ClassCastException / NullPointerException in whatever
 * composes first against the placeholder.
 */
internal val PlaceholderPlayer: Player =
    Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java),
        InvocationHandler { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "ArchiveTuneBridgePlaceholderPlayer"
                "getPlaybackState" -> Player.STATE_IDLE
                "getNextMediaItemIndex", "getPreviousMediaItemIndex" -> C.INDEX_UNSET
                "getDuration", "getContentDuration" -> C.TIME_UNSET
                "getVolume" -> 1f
                else -> defaultValue(method)
            }
        },
    ) as Player

private fun defaultValue(method: Method): Any? =
    when (method.returnType.name) {
        "void" -> null
        "boolean" -> false
        "int" -> 0
        "long" -> 0L
        "short" -> (0).toShort()
        "byte" -> (0).toByte()
        "char" -> '\u0000'
        "float" -> 0f
        "double" -> 0.0
        else -> emptyMedia3Value(method.returnType)
    }

/**
 * The "nothing here" value of a media3 type a Player (or ExoPlayer) member
 * returns as non-null, or null for anything else (nullable returns, and
 * types no copied caller reads off a stand-in player).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun emptyMedia3Value(type: Class<*>): Any? =
    when (type) {
        Timeline::class.java -> Timeline.EMPTY
        PlaybackParameters::class.java -> PlaybackParameters.DEFAULT
        androidx.media3.common.MediaMetadata::class.java -> androidx.media3.common.MediaMetadata.EMPTY
        Tracks::class.java -> Tracks.EMPTY
        Player.Commands::class.java -> Player.Commands.EMPTY
        AudioAttributes::class.java -> AudioAttributes.DEFAULT
        VideoSize::class.java -> VideoSize.UNKNOWN
        DeviceInfo::class.java -> DeviceInfo.UNKNOWN
        CueGroup::class.java -> CueGroup.EMPTY_TIME_ZERO
        Looper::class.java -> Looper.getMainLooper()
        List::class.java -> emptyList<Any>()
        else -> null
    }
