package com.music.bitchord.playback.cast

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException

/**
 * A transport other than ExoPlayer playing the phone's queue: Cast, the watch,
 * or an on-device DRM player.
 *
 * The model is [CastPlayback]'s, and every receiver keeps to it: the phone's
 * player stays the owner of the *queue* and is held paused, the receiver owns
 * the *clock*, and the session player reports the receiver's state in place of
 * the paused local one — so the app, the notification, the widgets and the
 * watch all show what is actually playing. The phone moving to another track
 * loads it on the receiver; the receiver moving on moves the phone.
 */
interface RemoteSpeaker {
    var sink: CastSink?

    /** Whether this receiver is the speaker. Everything else is meaningful only while it is. */
    val active: Boolean

    /** On-device DRM playback uses Android's music-stream volume, not receiver volume. */
    val isLocalOutput: Boolean get() = false
    val playerError: PlaybackException? get() = null
    val playWhenReady: Boolean
    val isPlaying: Boolean

    /** In [Player]'s own terms. */
    val playbackState: Int
    val positionMs: Long

    /** [C.TIME_UNSET] when the receiver does not know yet. */
    val durationMs: Long

    /** 0..100. */
    val deviceVolume: Int

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)

    /** 0..1 of the receiver's own range. */
    fun setVolume(fraction: Float)

    /** A new track became the phone's current one. */
    fun onLocalTransition(item: MediaItem?, reason: Int)

    /** The phone's queue changed under the playing track. */
    fun onLocalQueueChanged()
    fun onLocalRepeatChanged(repeatMode: Int)

    /** Something started the phone's own (paused) player while this is the speaker. */
    fun onLocalStartedPlaying(player: Player)
}

/**
 * Every receiver, as one: whichever is the speaker answers, and the phone's
 * moves are told to all of them (each ignores them unless it is active).
 */
class Speakers(
    private val all: List<RemoteSpeaker>,
    private val onRouteChanged: () -> Unit = {},
) : RemoteSpeaker {

    private val current: RemoteSpeaker get() = all.firstOrNull { it.active } ?: all.first()

    override var sink: CastSink? = null
        set(value) {
            field = value
            all.forEach { speaker ->
                speaker.sink = if (value == null) null else object : CastSink {
                    override fun onRemotePlaybackChanged() = value.onRemotePlaybackChanged()
                    override fun onRemoteVolumeChanged() = value.onRemoteVolumeChanged()
                    override fun onRemoteRouteChanged() {
                        onRouteChanged()
                        value.onRemoteRouteChanged()
                    }
                }
            }
        }

    override val active: Boolean get() = all.any { it.active }
    override val isLocalOutput: Boolean get() = current.isLocalOutput
    override val playerError: PlaybackException? get() = current.playerError
    override val playWhenReady: Boolean get() = current.playWhenReady
    override val isPlaying: Boolean get() = current.isPlaying
    override val playbackState: Int get() = current.playbackState
    override val positionMs: Long get() = current.positionMs
    override val durationMs: Long get() = current.durationMs
    override val deviceVolume: Int get() = current.deviceVolume

    override fun play() = current.play()
    override fun pause() = current.pause()
    override fun seekTo(positionMs: Long) = current.seekTo(positionMs)
    override fun setVolume(fraction: Float) = current.setVolume(fraction)

    override fun onLocalTransition(item: MediaItem?, reason: Int) = all.forEach { it.onLocalTransition(item, reason) }
    override fun onLocalQueueChanged() = all.forEach { it.onLocalQueueChanged() }
    override fun onLocalRepeatChanged(repeatMode: Int) = all.forEach { it.onLocalRepeatChanged(repeatMode) }
    override fun onLocalStartedPlaying(player: Player) = all.forEach { it.onLocalStartedPlaying(player) }
}

/** A receiver that never is one — the TV build's watch speaker. */
object NoSpeaker : RemoteSpeaker {
    override var sink: CastSink? = null
    override val active = false
    override val playWhenReady = false
    override val isPlaying = false
    override val playbackState = Player.STATE_IDLE
    override val positionMs = 0L
    override val durationMs = C.TIME_UNSET
    override val deviceVolume = 0
    override fun play() = Unit
    override fun pause() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun setVolume(fraction: Float) = Unit
    override fun onLocalTransition(item: MediaItem?, reason: Int) = Unit
    override fun onLocalQueueChanged() = Unit
    override fun onLocalRepeatChanged(repeatMode: Int) = Unit
    override fun onLocalStartedPlaying(player: Player) = Unit
}
