package com.music.bitchord.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.SurfaceTexture
import android.os.Build
import android.util.Log
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.music.bitchord.ui.rememberIsForeground
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.music.bitchord.data.Http
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.canvas.CanvasCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import java.util.Locale

private const val TAG = "CanvasArtworkPlayer"

/**
 * How long a clip gets to paint itself onto a surface it was just handed back
 * before the still art is brought in behind it instead. Long enough to cover a
 * decoder being re-created from cold, short enough that a clip which is never
 * coming back does not sit there as a hole for the length of a glance.
 */
private const val REPAINT_TIMEOUT_MS = 700L

/**
 * The looping video that plays over a track's cover art, sized to fill and
 * clipped by whatever laid it out.
 *
 * A second, deliberately unassuming ExoPlayer: silent, with its audio track
 * switched off entirely so a clip's soundtrack is never even fetched, and no
 * audio attributes — taking focus here would duck the music this is decorating.
 * It follows the transport, so pausing the track stops the sleeve moving too.
 *
 * Nothing is drawn until the first frame arrives, and the fade in from there
 * means a failed or slow clip simply leaves the still art showing rather than
 * flashing a black square over it. [CanvasArtwork.fallbackUrl] gets one try if
 * the first rendition won't decode.
 */
@OptIn(UnstableApi::class)
@Composable
fun CanvasArtworkPlayer(
    canvas: CanvasArtwork,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    /** Fires once the clip has an actual frame on screen, and again if it drops back to none. */
    onRenderedChanged: (Boolean) -> Unit = {},
    /** A single frame off the playing clip, for callers that want to re-tint around it. */
    onFrameCaptured: (Bitmap) -> Unit = {},
    /**
     * Keep calling [onFrameCaptured] every so many milliseconds instead of
     * only once — for a caller re-tinting its backdrop off a playing clip,
     * which is worth following as it plays rather than settling on whatever
     * colours its opening frame happened to have. That holds for every
     * source, not just
     * [CanvasSource.SPOTIFY][com.music.bitchord.data.canvas.CanvasSource.SPOTIFY]:
     * a clip is a clip, and one that pans or cuts changes colour under its own
     * still sleeve exactly the same way regardless of who published it. Null
     * when a caller has nothing worth re-tinting off a moving colour at all —
     * re-reading a texture off the GPU costs a frame stall, so this stays
     * opt-in rather than always-on.
     */
    refreshFrameEveryMs: Long? = null,
    /**
     * The longest edge of the bitmap [onFrameCaptured] is handed.
     *
     * This is the whole cost of following a clip. `getBitmap()` with no
     * arguments hands back a copy at the view's own size — full-bleed, so most
     * of a phone screen, five or six megabytes read back off the GPU and
     * allocated afresh on every call. Nobody wants that resolution: the one
     * caller there is averages the frame down to a handful of colours. Asking
     * for a small copy instead makes the readback scale during the blit, which
     * is what turns a refresh from something worth doing every few seconds into
     * something affordable several times a second.
     */
    frameCapturePx: Int = FRAME_CAPTURE_PX,
    /**
     * How much of whatever is behind the clip it is currently hiding: 0 while
     * nothing is drawn, ramping to 1 as the first frame fades in, and back down
     * if it drops out again.
     *
     * A caller that stacks a still image under the clip needs this to take that
     * image back out from under it, and cannot get there from
     * [onRenderedChanged] alone — that fires when the fade *starts*. It matters
     * most with [bottomFade]: one gradient over each of two stacked layers
     * leaves the lower one showing through the upper one instead of the backdrop
     * showing through both, so the still art stays half-visible over the clip
     * for as long as it is left lit underneath.
     */
    onCoverChanged: (Float) -> Unit = {},
    /**
     * Share of the clip's height, measured up from its bottom edge, over which
     * it dissolves to nothing — 0 for a hard edge. See [setBottomFade] for why
     * this is a parameter here rather than a mask the caller could draw.
     */
    bottomFade: Float = 0f,
) {
    val context = LocalContext.current

    var url by remember(canvas) { mutableStateOf(canvas.url) }
    var rendered by remember(canvas) { mutableStateOf(false) }
    // Aspect of the clip itself. Zero until the decoder reports it, which is
    // also the signal that there is nothing sensible to crop to yet.
    var clipAspect by remember(canvas) { mutableFloatStateOf(0f) }
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    var textureView by remember(canvas) { mutableStateOf<TextureView?>(null) }
    // Frames are counted rather than flagged, because [rendered] cannot answer
    // the question the repaint below has to ask: "did a frame land on *this*
    // surface", not "has one ever landed".
    var frameTick by remember(canvas) { mutableIntStateOf(0) }
    // Bumped each time the view is handed a surface to replace one that was
    // taken away — which, in practice, means each time the app comes back from
    // off screen. Not bumped for the first surface of all, which arrives with
    // nothing needing doing to it. See the repaint effect below.
    var surfaceGeneration by remember(canvas) { mutableIntStateOf(0) }

    val player = remember {
        ExoPlayer.Builder(context)
            // Shares the app's one OkHttp client, as everything that fetches
            // over the network here does — and wrapped in CanvasCache so a
            // loop past the first is read off disk rather than re-fetched;
            // see that object's doc for why this matters far more here than
            // it would for a clip played once.
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(CanvasCache.dataSourceFactory(OkHttpDataSource.Factory(Http.client))),
            )
            .build()
            .apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val width = videoSize.width * videoSize.pixelWidthHeightRatio
                if (width > 0f && videoSize.height > 0) {
                    clipAspect = width / videoSize.height
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // One retry, at the other rendition. If that is the one that
                // just failed there is nowhere left to go: leave the still
                // art up rather than looping through a broken URL.
                val alternate = canvas.fallbackUrl
                if (alternate != null && alternate != url) {
                    url = alternate
                } else {
                    rendered = false
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(url) {
        rendered = false
        clipAspect = 0f
        val item = MediaItem.Builder().setUri(url)
        mimeTypeOf(url)?.let { item.setMimeType(it) }
        player.setMediaItem(item.build())
        player.prepare()
    }

    // Gated on the app being on screen as well as on the caller's own state.
    //
    // This is a video decoder. Left to [isPlaying] alone it goes on decoding
    // frames into a surface nobody can see for as long as the composition is
    // alive — which, with the phone in a pocket and music playing, is the whole
    // album. Worse on a detail page, whose caller passes a constant `true`
    // because "the page is only up while it's being read": true of a page being
    // looked at, not of one left open behind a locked screen.
    //
    // Held inside this component rather than asked of each caller, so no call
    // site can forget it. The player now runs continuously in the foreground
    // regardless of playback state, so coming back from background always has
    // a surface ready and `onRenderedFirstFrame()` fires naturally.
    val foreground = rememberIsForeground()
    LaunchedEffect(foreground) { player.playWhenReady = foreground }

    // Repaint onto a surface that has just been handed back. A TextureView's
    // SurfaceTexture does not survive every background/layout transition, and
    // ExoPlayer's old "first frame rendered" callback says nothing about the
    // replacement surface. Keep the still artwork visible while waiting, ask
    // the decoder to paint at its current position, and only hand back to the
    // clip when onSurfaceTextureUpdated confirms real pixels below.
    LaunchedEffect(surfaceGeneration) {
        if (surfaceGeneration == 0) return@LaunchedEffect
        rendered = false
        val before = frameTick
        if (player.playbackState != Player.STATE_IDLE) {
            player.seekTo(player.currentPosition)
        }
        delay(REPAINT_TIMEOUT_MS)
        if (frameTick == before) {
            rendered = false
        }
    }

    val reportRendered by rememberUpdatedState(onRenderedChanged)
    LaunchedEffect(rendered) {
        reportRendered(rendered)
        if (!rendered) return@LaunchedEffect
        // Let the surface actually paint the frame that just triggered this
        // before reading it back — grabbing it the instant the callback fires
        // can still catch the previous, empty buffer.
        withFrameMillis { }
        val view = textureView ?: return@LaunchedEffect
        val bitmap = view.captureAt(frameCapturePx)
        if (bitmap != null) {
            Log.d(TAG, "frame captured after rendered=true, size=${bitmap.width}x${bitmap.height}")
            onFrameCaptured(bitmap)
        } else {
            Log.w(TAG, "frame capture returned null after rendered=true")
        }
    }

    // The opt-in follow-up to the capture above, for a caller that asked for
    // one — see [refreshFrameEveryMs]. A separate effect rather than a loop
    // folded into the one above: that one is keyed on [rendered] so it fires
    // again on every fade-in, and this one only needs to start once a fade-in
    // has actually happened and then keep going for as long as it holds.
    LaunchedEffect(rendered, refreshFrameEveryMs, frameCapturePx) {
        val interval = refreshFrameEveryMs ?: return@LaunchedEffect
        Log.d(TAG, "periodic frame refresh started, interval=$interval")
        if (!rendered) return@LaunchedEffect
        while (isActive) {
            delay(interval)
            val view = textureView ?: continue
            val bitmap = view.captureAt(frameCapturePx)
            if (bitmap != null) {
                Log.d(TAG, "periodic frame captured, size=${bitmap.width}x${bitmap.height}")
                onFrameCaptured(bitmap)
            } else {
                Log.w(TAG, "periodic frame capture returned null")
            }
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (rendered) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "canvasAlpha",
    )

    // Published rather than left for the caller to mirror with a second
    // animation off [onRenderedChanged]: one fade, one account of how far along
    // it is. Zeroed on the way out, or a caller would be left holding something
    // hidden behind a clip that is no longer mounted.
    val reportCover by rememberUpdatedState(onCoverChanged)
    LaunchedEffect(Unit) { snapshotFlow { alpha }.collect { reportCover(it) } }
    DisposableEffect(Unit) {
        onDispose {
            // The parent owns the still/canvas handoff. Never leave it holding
            // a Success from a player or TextureView that no longer exists.
            reportRendered(false)
            reportCover(0f)
        }
    }

    AndroidView(
        factory = { viewContext ->
            val texture = TextureView(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                // Blend rather than punch a hole: the still sleeve stays
                // visible underneath for the length of the fade.
                isOpaque = false
                this.alpha = 0f
                player.setVideoTextureView(this)
                // setVideoTextureView installs ExoPlayer's own listener, and
                // the player has to keep it — it is how the surface reaches
                // the video renderer at all. So wrap it rather than replace
                // it: everything is passed straight through, and the one
                // callback that matters here is noted on the way past.
                //
                // Asking the lifecycle instead would be simpler and wrong. The
                // surface comes back on the first traversal after the activity
                // is visible, which is *after* ON_RESUME — a repaint fired
                // there lands on the placeholder surface and the real one
                // arrives blank a moment later.
                val delegate = surfaceTextureListener
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    /** Whether the next surface is a replacement for one taken away. */
                    private var replacing = false

                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        delegate?.onSurfaceTextureAvailable(surface, width, height)
                        // The first surface needs nothing: prepare() paints it.
                        if (!replacing) return
                        replacing = false
                        Log.d(TAG, "surface recreated (gen $surfaceGeneration), waiting for frame")
                        surfaceGeneration++
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        delegate?.onSurfaceTextureSizeChanged(surface, width, height)
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        replacing = true
                        Log.d(TAG, "surface destroyed, rendered=$rendered")
                        // The old buffer is gone now, not when/if ExoPlayer
                        // later reports another first frame. Restore the still
                        // artwork immediately so an empty replacement surface
                        // can never become the only visible artwork layer.
                        rendered = false
                        return delegate?.onSurfaceTextureDestroyed(surface) ?: true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                        delegate?.onSurfaceTextureUpdated(surface)
                        // This callback is the proof that the current
                        // TextureView, rather than some previously destroyed
                        // surface, contains a drawable video buffer.
                        if (!rendered) {
                            rendered = true
                            frameTick++
                            Log.d(TAG, "first frame on surface (tick $frameTick, gen $surfaceGeneration)")
                        }
                    }
                }
            }
            textureView = texture
            // Wrapped on every API level so there is one view tree to reason
            // about: below API 31 the frame is what draws [bottomFade], and
            // above it the frame is just a box around the texture.
            FadingBottomFrame(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                addView(texture)
            }
        },
        update = { frame ->
            val view = frame.getChildAt(0) as TextureView
            // Set on the view itself. A Compose alpha layer over a TextureView
            // is not reliably composited, and this is the same fade either way.
            view.alpha = alpha
            view.centerCrop(bounds, clipAspect)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                view.setBottomFade(bottomFade, bounds)
            } else {
                frame.fadeFraction = bottomFade
            }
        },
        modifier = modifier.onSizeChanged { bounds = it },
    )
}

/**
 * A frame off the clip, no bigger than [maxPx] on its longest edge — see
 * [CanvasArtworkPlayer]'s `frameCapturePx`.
 *
 * The aspect is kept rather than squared off. Nothing downstream draws this,
 * but everything downstream *averages* it, and squashing one axis would quietly
 * reweight which part of the frame each average is mostly made of.
 *
 * Null whenever the view has no frame to give — it is laid out but not yet
 * measured, or its surface has gone. A caller that gets null should keep what
 * it already had; the next tick will have one.
 */
private fun TextureView.captureAt(maxPx: Int): Bitmap? {
    val viewWidth = width
    val viewHeight = height
    if (viewWidth <= 0 || viewHeight <= 0) return null
    val scale = maxPx.toFloat() / maxOf(viewWidth, viewHeight)
    return runCatching {
        if (scale >= 1f) {
            getBitmap()
        } else {
            getBitmap(
                (viewWidth * scale).roundToInt().coerceAtLeast(1),
                (viewHeight * scale).roundToInt().coerceAtLeast(1),
            )
        }
    }.getOrNull()
}

/**
 * Big enough that averaging it is stable, small enough that reading it back off
 * the GPU is not an event. Every consumer reduces this to a handful of colours.
 */
private const val FRAME_CAPTURE_PX = 128

/**
 * A TextureView stretches its content to whatever bounds it was given, which
 * turns a 9:16 clip in a square sleeve into a smeared one. Undo that with a
 * transform: scale the axis that came up short until the clip covers the view
 * at its true aspect, and let the overflow fall outside the clip.
 */
private fun TextureView.centerCrop(bounds: IntSize, clipAspect: Float) {
    if (bounds.width == 0 || bounds.height == 0 || clipAspect <= 0f) return
    val viewAspect = bounds.width.toFloat() / bounds.height
    val pivotX = bounds.width / 2f
    val pivotY = bounds.height / 2f
    val matrix = Matrix().apply {
        if (clipAspect > viewAspect) {
            setScale(clipAspect / viewAspect, 1f, pivotX, pivotY)
        } else {
            setScale(1f, viewAspect / clipAspect, pivotX, pivotY)
        }
    }
    setTransform(matrix)
}

/**
 * Dissolves the clip's bottom edge into whatever is behind it.
 *
 * Done here, on the view's own RenderNode, rather than with a DstIn mask in the
 * caller's draw scope: a TextureView's frames are composited from its surface
 * and a Compose blend drawn over the node simply doesn't reach them — the mask
 * lands on the layer around the video and leaves the video's own hard edge
 * exactly where it was.
 *
 * [RenderEffect] is API 31+; below that [FadingBottomFrame] does the same job
 * the older way, with a saveLayer and a Porter-Duff mask.
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun TextureView.setBottomFade(fraction: Float, bounds: IntSize) {
    val height = bounds.height
    if (fraction <= 0.001f || height == 0) {
        setRenderEffect(null)
        return
    }
    val gradient = LinearGradient(
        0f,
        height * (1f - fraction.coerceAtMost(1f)),
        0f,
        height.toFloat(),
        android.graphics.Color.BLACK,
        android.graphics.Color.TRANSPARENT,
        Shader.TileMode.CLAMP,
    )
    // createOffsetEffect(0, 0) is the identity effect over the node's own
    // content, which is the only way to name "what this view drew" as the
    // destination of a blend.
    setRenderEffect(
        RenderEffect.createBlendModeEffect(
            RenderEffect.createOffsetEffect(0f, 0f),
            RenderEffect.createShaderEffect(gradient),
            BlendMode.DST_IN,
        ),
    )
}

/**
 * The pre-[Build.VERSION_CODES.S] bottom fade: the same dissolve
 * [setBottomFade] gets from a [RenderEffect], done the way it was done before
 * there was one.
 *
 * Draw the child into an offscreen layer, paint a gradient over that layer with
 * [PorterDuff.Mode.DST_IN], then compose the result down. Because the layer is
 * this group's — not the TextureView's own node — the video frames are inside it
 * by the time the mask lands, which is exactly what a Compose blend over the
 * texture cannot achieve.
 *
 * It costs a full-screen offscreen buffer per frame, so it stays off entirely
 * while [fadeFraction] is zero: with no fade asked for this is a plain
 * FrameLayout and `dispatchDraw` takes the ordinary path.
 */
private class FadingBottomFrame(context: Context) : FrameLayout(context) {
    /** Share of the height, from the bottom, over which the child dissolves. */
    var fadeFraction: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (clamped == field) return
            field = clamped
            // A software layer would defeat the point — the texture has to stay
            // hardware-composited — so this is only ever the invalidate.
            gradient = null
            invalidate()
        }

    private val maskPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private var gradient: LinearGradient? = null
    private var gradientHeight = 0

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gradient = null
    }

    override fun dispatchDraw(canvas: Canvas) {
        val fade = fadeFraction
        if (fade <= 0.001f || height == 0) {
            super.dispatchDraw(canvas)
            return
        }
        val shader = gradient?.takeIf { gradientHeight == height } ?: LinearGradient(
            0f,
            height * (1f - fade),
            0f,
            height.toFloat(),
            android.graphics.Color.BLACK,
            android.graphics.Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        ).also {
            gradient = it
            gradientHeight = height
        }
        maskPaint.shader = shader
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.dispatchDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)
        canvas.restoreToCount(layer)
    }
}

/**
 * Apple serves HLS, Tidal and the community index serve MP4. Naming the type
 * saves ExoPlayer a sniff, and an unrecognised URL is left for it to work out.
 */
private fun mimeTypeOf(url: String): String? {
    val path = url.substringBefore('?').lowercase(Locale.ROOT)
    return when {
        path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
        path.endsWith(".mp4") -> MimeTypes.VIDEO_MP4
        else -> null
    }
}
