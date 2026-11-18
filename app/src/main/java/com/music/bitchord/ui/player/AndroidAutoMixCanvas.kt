package com.music.bitchord.ui.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.music.bitchord.data.canvas.CanvasArtwork
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** The ordinary Now Playing clip and its optional standby share these two leases. Main only. */
internal object NowPlayingCanvasDecoders {
    private const val MAX_DECODERS = 2
    private val inUse = MutableStateFlow(0)

    suspend fun awaitCapacity() {
        inUse.first { it < MAX_DECODERS }
    }

    fun open(context: Context): Lease? {
        if (inUse.value >= MAX_DECODERS) return null
        val player = runCatching { newCanvasDecoder(context) }.getOrNull() ?: return null
        inUse.value++
        return Lease(player) { inUse.value-- }
    }

    class Lease(val player: ExoPlayer, private val returnSlot: () -> Unit) {
        var closed = false
            private set

        fun close() {
            if (closed) return
            closed = true
            try {
                runCatching { player.release() }
            } finally {
                returnSlot()
            }
        }
    }
}

/** Warms a real video output, not just STATE_READY or a cached URL. It is never drawn in Phase C. */
internal fun prepareAndroidAutoMixCanvas(
    context: Context,
    clip: CanvasArtwork,
    onFrame: (Boolean) -> Unit,
): (() -> Unit)? {
    val lease = NowPlayingCanvasDecoders.open(context) ?: return null
    val texture = runCatching { SurfaceTexture(false) }.getOrElse {
        lease.close()
        return null
    }
    val surface = runCatching { Surface(texture) }.getOrElse {
        runCatching { texture.release() }
        lease.close()
        return null
    }
    val player = lease.player
    var ready = false
    lateinit var listener: Player.Listener
    fun close() {
        if (lease.closed) return
        runCatching { texture.setOnFrameAvailableListener(null) }
        runCatching { player.removeListener(listener) }
        lease.close()
        runCatching { surface.release() }
        runCatching { texture.release() }
    }
    listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (lease.closed) return
            close()
            onFrame(false)
        }
    }
    return runCatching {
        texture.setOnFrameAvailableListener({ output ->
            if (!lease.closed && output === texture && !ready) {
                // This output has no consumer until it attaches to a view. Stop at its first frame.
                ready = true
                onFrame(true)
            }
        }, Handler(Looper.getMainLooper()))
        player.addListener(listener)
        player.setVideoSurface(surface)
        player.playWhenReady = false
        val item = MediaItem.Builder().setUri(clip.url)
        mimeTypeOf(clip.url)?.let { item.setMimeType(it) }
        player.setMediaItem(item.build(), 0L)
        player.prepare()
        ::close
    }.getOrElse {
        close()
        null
    }
}
