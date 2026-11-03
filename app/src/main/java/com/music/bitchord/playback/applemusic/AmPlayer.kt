package com.music.bitchord.playback.applemusic

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player

/**
 * Single-song MusicKit transport, not a MusicSource and never a source of audio URLs.
 *
 * Calls and listener delivery must be serialized on the service's main thread. [load]
 * prepares exactly one catalog song WITHOUT starting audio; it must cooperate with
 * cancellation and must not publish/play a cancelled load later. Only [play] starts
 * audio. The adapter owns local Android audio focus and abandons it on stop/release.
 * Disable SDK auto-advance/repeat: the phone's queue is authoritative.
 */
interface AmPlayer {
    /** Cheap, thread-safe availability, stable for this instance; the phone loader reads it too. */
    val available: Boolean
    /** Live SDK clock, or TIME_UNSET when not known; read on the main thread. */
    val positionMs: Long get() = C.TIME_UNSET
    var listener: Listener?

    /** Credentials are ephemeral: clear on stop/release and never log this request. */
    class Request(
        val requestId: Long,
        val catalogId: String,
        val developerToken: String,
        val userToken: String,
        val storefront: String,
        val positionMs: Long,
        val playbackStorefront: String? = null,
    )

    data class Snapshot(
        val requestId: Long,
        /** One of Player.STATE_*; ENDED means this one song actually finished. */
        val playbackState: Int = Player.STATE_READY,
        val playWhenReady: Boolean = false,
        val isPlaying: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = C.TIME_UNSET,
    )

    interface Listener {
        /** Every callback must carry the request id, including delayed native callbacks. */
        fun onStateChanged(snapshot: Snapshot)
        fun onError(requestId: Long)
    }

    suspend fun load(request: Request)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun stop()
    fun release()

    fun interface Factory {
        /** Null for unsupported ABI, missing app/SDK, or failed runtime checks. */
        fun create(context: Context): AmPlayer?
    }
}

/**
 * Source-set factory with an optional verified replacement for SDK versions.
 * The native source-set factory independently checks the build gate and runtime.
 * AAR detection alone never enables the BuildConfig/UI flag.
 */
object AmPlayerFactory {
    @Volatile private var verifiedFactory: AmPlayer.Factory? = null

    fun registerVerifiedRealAdapter(factory: AmPlayer.Factory) {
        verifiedFactory = factory
    }

    fun create(context: Context): AmPlayer? = try {
        (verifiedFactory?.create(context.applicationContext) ?: createMusicKitPlayer(context.applicationContext))?.let { player ->
            if (player.available) player else {
                player.release()
                null
            }
        }
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    } catch (_: LinkageError) {
        null
    }
}
