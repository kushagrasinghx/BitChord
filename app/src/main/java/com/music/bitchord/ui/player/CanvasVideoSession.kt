package com.music.bitchord.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.PixelCopy
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.music.bitchord.data.Http
import com.music.bitchord.data.canvas.CanvasCache
import com.music.bitchord.ui.performance.FrameDiagnostics
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * A video player's control Looper, kept off the UI thread because Media3 blocks
 * whichever thread owns a player while its renderer lets go of a surface. Work
 * posted before the Looper is ready queues here rather than waiting for it.
 */
internal class VideoControlThread(name: String) : Executor {
    private val lock = Any()
    private val pending = ArrayDeque<Runnable>()
    private var handler: Handler? = null
    private val thread = object : HandlerThread(name) {
        override fun onLooperPrepared() {
            synchronized(lock) {
                val ready = Handler(looper)
                handler = ready
                while (pending.isNotEmpty()) ready.post(pending.removeFirst())
            }
        }
    }.apply { start() }

    override fun execute(command: Runnable) {
        // Do not call thread.looper on the UI thread: it waits for startup.
        synchronized(lock) {
            val ready = handler
            if (ready == null) pending.addLast(command) else ready.post(command)
        }
    }
}

/** A single control Looper for silent artwork players, separate from audio. */
private val CanvasVideoThread = VideoControlThread("canvas-control")

/**
 * All Media3 access, including construction and surface changes, stays on the
 * control Looper. TextureView and Compose state remain on the main thread.
 */
@UnstableApi
internal class CanvasVideoSession(
    context: Context,
    private val onAspect: (Float) -> Unit,
    private val onPlaybackError: () -> Unit,
    private val onFailure: (Exception) -> Unit,
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val requestedGeneration = AtomicInteger()
    private var generation = 0 // Control-thread confined.
    @Volatile private var disposed = false
    val isClosed: Boolean get() = disposed || owner.isClosed

    // The Surface the decoder was last handed, for reading frames back off it.
    // Control-thread confined like the leases, which release it on this thread
    // only after the decoder has been moved off it — so a capture made here can
    // never find it released between checking it and copying from it.
    private var decoderOutput: Surface? = null
    private var captureHandler: Handler? = null // Control-thread confined.

    private val owner = AsyncVideoLifecycle<ExoPlayer, Surface>(
        executor = CanvasVideoThread,
        createPlayer = {
            FrameDiagnostics.measure("BitChord.canvasCreate") {
                ExoPlayer.Builder(app)
                    .setLooper(checkNotNull(Looper.myLooper()))
                    .setMediaSourceFactory(
                        DefaultMediaSourceFactory(CanvasCache.dataSourceFactory(OkHttpDataSource.Factory(Http.client))),
                    )
                    .build()
                    .apply {
                        volume = 0f
                        repeatMode = Player.REPEAT_MODE_ONE
                        trackSelectionParameters = trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                            .apply { CanvasPlaybackLimits.maxFrameRate?.let(::setMaxVideoFrameRate) }
                            .build()
                        addListener(object : Player.Listener {
                            override fun onVideoSizeChanged(videoSize: VideoSize) {
                                val width = videoSize.width * videoSize.pixelWidthHeightRatio
                                val aspect = if (width.isFinite() && width > 0f && videoSize.height > 0) {
                                    width / videoSize.height
                                } else 0f
                                deliver { onAspect(aspect) }
                            }

                            override fun onPlayerError(error: PlaybackException) {
                                deliver(onPlaybackError)
                            }
                        })
                    }
            }
        },
        setOutput = { player, surface ->
            FrameDiagnostics.measure("BitChord.canvasSetSurface") { player.setVideoSurface(surface) }
            decoderOutput = surface
        },
        releasePlayer = { player ->
            FrameDiagnostics.measure("BitChord.canvasRelease") { player.release() }
        },
        onFailure = { failure ->
            main.post { if (!disposed) onFailure(failure) }
        },
    )

    fun prepare(url: String, mimeType: String?) {
        val requested = requestedGeneration.incrementAndGet()
        owner.command { player ->
            generation = requested
            FrameDiagnostics.measure("BitChord.canvasPrepare") {
                player.setMediaItem(MediaItem.Builder().setUri(url).apply {
                    mimeType?.let(::setMimeType)
                }.build())
                player.prepare()
            }
        }
    }

    fun setPlaying(playing: Boolean) = owner.command { it.playWhenReady = playing }

    fun repaint() = owner.command { player ->
        if (player.playbackState != Player.STATE_IDLE) player.seekTo(player.currentPosition)
    }

    fun attach(texture: SurfaceTexture): VideoOutputLease<Surface> {
        val lease = VideoOutputLease(
            createOutput = { Surface(texture) },
            destroyOutput = Surface::release,
            destroyTexture = texture::release,
        )
        owner.attach(lease)
        return lease
    }

    fun detach(lease: VideoOutputLease<Surface>) = owner.detach(lease)

    /**
     * The frame the decoder last queued, scaled into a [width] by [height] copy
     * and passed through [finish], or null when there is none to give: no
     * surface yet, the session closing under the request, nothing queued to the
     * surface, or a copy that failed. A caller that gets null keeps what it had.
     *
     * Read back with [PixelCopy] from the decoder's own Surface rather than with
     * `TextureView.getBitmap`, which holds the UI thread until the render thread
     * has finished a GPU readback. The picture is the same: both stretch the
     * whole buffer into the requested size, and neither applies the view's
     * transform — hwui reads a TextureView's layer for getBitmap with the layer
     * transform switched off. The request is made on the control thread because
     * that is where the Surface is released, and below API 34 the request itself
     * waits out the readback, which is this thread's time to spend rather than
     * the UI's. [finish] runs here too, so the caller only receives the result.
     */
    suspend fun captureFrame(width: Int, height: Int, finish: (Bitmap) -> Bitmap?): Bitmap? =
        suspendCancellableCoroutine { continuation ->
            CanvasVideoThread.execute {
                val surface = decoderOutput?.takeIf { continuation.isActive && !isClosed && it.isValid }
                if (surface == null) {
                    continuation.resume(null)
                    return@execute
                }
                val handler = captureHandler ?: Handler(checkNotNull(Looper.myLooper())).also { captureHandler = it }
                val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    FrameDiagnostics.measure("BitChord.canvasCapture") {
                        PixelCopy.request(surface, frame, { result ->
                            val finished = if (result == PixelCopy.SUCCESS) {
                                runCatching { finish(frame) }.getOrNull()
                            } else null
                            continuation.resume(finished)
                        }, handler)
                    }
                } catch (_: IllegalArgumentException) {
                    // PixelCopy refuses a surface it finds no longer valid
                    // rather than reading from it. That is no frame this time,
                    // not a failure worth tearing anything down over.
                    continuation.resume(null)
                }
            }
        }

    fun close() {
        disposed = true // Suppress callbacks already queued to main.
        owner.close()
    }

    private fun deliver(callback: () -> Unit) {
        val fromGeneration = generation
        main.post {
            if (!disposed && !owner.isClosed && fromGeneration == requestedGeneration.get()) callback()
        }
    }
}
