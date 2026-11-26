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
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import com.music.bitchord.ui.graphics.FADE_CURVE_STOPS
import com.music.bitchord.ui.graphics.smootherStep
import com.music.bitchord.ui.rememberIsForeground
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.music.bitchord.data.Http
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.canvas.CanvasCache
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.ceil
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

/** How long after a clip leaves the screen its player is released — see its onDispose. */
private const val DEFERRED_RELEASE_MS = 1_000L

private val mainHandler = Handler(Looper.getMainLooper())

/** Whether and when a clip plays on — see the playback gate and [onResumed]. */
private class ResumeGate {
    /** Whether the last pause was for a transition; starts true for the player opening. */
    var heldForTransition = true

    /** Whether the frame on screen is one a caller holds a still of. */
    var holdingStill = false
}

/** What a clip's player needs to leave the screen without blocking it. */
private class Teardown {
    var disposed = false

    /**
     * A surface taken away while the player was closing or collapsing, kept
     * alive rather than handed back to ExoPlayer. Told the surface is gone,
     * ExoPlayer blocks the main thread until the decoder has stopped writing
     * to it, which is the decoder being torn down. Left holding a surface
     * that still exists, it can be stopped and released at leisure, or move
     * onto the next surface it is given; this one is released after either.
     */
    var orphan: SurfaceTexture? = null

    fun releaseOrphan() {
        orphan?.release()
        orphan = null
    }
}

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
internal fun AndroidCanvasArtworkPlayer(
    canvas: CanvasArtwork,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    contentMode: CanvasContentMode = CanvasContentMode.CROP,
    /** Align a contained portrait clip to the top; other callers retain centered FIT. */
    alignPortraitTop: Boolean = false,
    /** The decoded video's display aspect, or zero until Media3 knows it. */
    onAspectRatioChanged: (Float) -> Unit = {},
    /** The full player bounds required before a portrait hero may reveal its first frame. */
    portraitRevealBounds: IntSize = IntSize.Zero,
    /** Fades a full-player portrait clip away as the existing sleeve collapses. */
    presentationAlpha: () -> Float = { 1f },
    organicReveal: CanvasOrganicReveal? = null,
    /** Fires once the clip has an actual frame on screen, and again if it drops back to none. */
    onRenderedChanged: (Boolean) -> Unit = {},
    /** A single frame off the playing clip, for callers that want to re-tint around it. */
    onFrameCaptured: (ImageBitmap) -> Unit = {},
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
     *
     * 0 for no frames at all: even a small one is read back on the main
     * thread, and a caller with no use for it should not pay that on every
     * fade-in.
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
     * it dissolves to nothing — 0 for a hard edge. See [createBottomFade] for why
     * this is a parameter here rather than a mask the caller could draw.
     */
    bottomFade: Float = 0f,
    /** Optional end of the fade in view pixels; defaults to the view's bottom edge. */
    bottomFadeEndPx: Float? = null,
    /**
     * Halts decoding for the length of a caller-driven transition — the sleeve
     * collapsing into the queue or lyrics panel and back — rather than only at
     * the two ends of it. That collapse is driven by the same clock as this
     * clip's own fade, and a decoder left running through it competes with the
     * slide for the same frame budget; the stutter that produced this flag was
     * the decode, not the animation. The clip keeps its last frame on screen
     * while paused, so there is nothing to fade back in once it lifts.
     */
    pausedForTransition: Boolean = false,
    /** See [CanvasArtworkPlayer]'s parameter of the same name. */
    resumeDelayMs: Long = 0L,
    startPositionMs: Long = 0L,
    revealInstantly: Boolean = false,
    captureStill: Boolean = false,
    onStillCaptured: (ImageBitmap?, Long) -> Unit = { _, _ -> },
    onResumed: () -> Unit = {},
    nowPlaying: Boolean = false,
) {
    val context = LocalContext.current

    var url by remember(canvas) { mutableStateOf(canvas.url) }
    var rendered by remember(canvas) { mutableStateOf(false) }
    // Aspect of the clip itself. Zero until the decoder reports it, which is
    // also the signal that there is nothing sensible to crop to yet.
    var clipAspect by remember(canvas) { mutableFloatStateOf(0f) }
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    var textureView by remember(canvas) { mutableStateOf<TextureView?>(null) }
    // A redraw on the same TextureView can still refer to the failed rendition.
    var lastMediaTimestamp by remember { mutableLongStateOf(0L) }
    var pendingTextureFrame by remember { mutableStateOf<SurfaceTexture?>(null) }
    // Frames are counted rather than flagged, because [rendered] cannot answer
    // the question the repaint below has to ask: "did a frame land on *this*
    // surface", not "has one ever landed".
    var frameTick by remember(canvas) { mutableIntStateOf(0) }
    // Bumped each time the view is handed a surface to replace one that was
    // taken away — which, in practice, means each time the app comes back from
    // off screen. Not bumped for the first surface of all, which arrives with
    // nothing needing doing to it. See the repaint effect below.
    var surfaceGeneration by remember(canvas) { mutableIntStateOf(0) }
    val currentContentMode by rememberUpdatedState(contentMode)
    val currentAlignPortraitTop by rememberUpdatedState(alignPortraitTop)
    val currentPortraitRevealBounds by rememberUpdatedState(portraitRevealBounds)
    val currentPresentationAlpha by rememberUpdatedState(presentationAlpha)
    val currentPausedForTransition by rememberUpdatedState(pausedForTransition)
    val reportAspect by rememberUpdatedState(onAspectRatioChanged)
    val currentStartPositionMs by rememberUpdatedState(startPositionMs)
    val currentResumeDelayMs by rememberUpdatedState(resumeDelayMs)
    val reportResumed by rememberUpdatedState(onResumed)
    val teardown = remember { Teardown() }
    val gate = remember { ResumeGate() }

    val owned = if (nowPlaying) rememberNowPlayingDecoder(context, teardown) else null
    val player = if (nowPlaying) owned?.player ?: return else remember { newCanvasDecoder(context) }

    fun confirmTextureFrame(surface: SurfaceTexture): Boolean {
        if (owned?.closed == true) return false
        val view = textureView ?: return false
        if (nowPlaying && (surface !== view.surfaceTexture || surface.timestamp == lastMediaTimestamp ||
                player.playerError != null || clipAspect <= 0f)
        ) return false
        if (gate.holdingStill && player.playWhenReady) {
            gate.holdingStill = false
            reportResumed()
        }
        if (rendered) return true
        val transformed = view.applyContentTransform(clipAspect, currentContentMode, currentAlignPortraitTop)
        val portrait = currentContentMode == CanvasContentMode.FIT_PORTRAIT && clipAspect > 0f && clipAspect < 1f
        val expected = currentPortraitRevealBounds
        val layoutReady = !portrait ||
            (expected != IntSize.Zero && view.width == expected.width && view.height == expected.height)
        if (!layoutReady || (currentContentMode != CanvasContentMode.CROP && !transformed)) return false
        rendered = true
        frameTick++
        return true
    }

    LaunchedEffect(pendingTextureFrame, url) {
        val surface = pendingTextureFrame ?: return@LaunchedEffect
        // The render thread may latch the buffer after TextureView notifies.
        // Paused clips might not send a second notification.
        withTimeoutOrNull(REPAINT_TIMEOUT_MS) {
            do {
                withFrameMillis { }
            } while (textureView?.surfaceTexture === surface && !confirmTextureFrame(surface))
        }
        pendingTextureFrame = null
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val width = videoSize.width * videoSize.pixelWidthHeightRatio
                val aspect = if (width.isFinite() && width > 0f && videoSize.height > 0) {
                    width / videoSize.height
                } else 0f
                if (aspect != clipAspect) rendered = false
                clipAspect = aspect
                reportAspect(aspect)
                textureView?.applyContentTransform(clipAspect, currentContentMode, currentAlignPortraitTop)
            }

            override fun onPlayerError(error: PlaybackException) {
                // One retry, at the other rendition. If that is the one that
                // just failed there is nowhere left to go: leave the still
                // art up rather than looping through a broken URL.
                val alternate = canvas.fallbackUrl
                if (nowPlaying) {
                    pendingTextureFrame = null
                    lastMediaTimestamp = textureView?.surfaceTexture?.timestamp ?: 0L
                }
                rendered = false
                if (alternate != null && alternate != url) {
                    url = alternate
                }
            }
        }
        player.addListener(listener)
        onDispose {
            if (owned?.closed != true) player.removeListener(listener)
            teardown.disposed = true
            if (!nowPlaying) {
                // Unmanaged callers retain the deferred teardown. Now Playing
                // holds its decoder lease until release() finishes.
                player.stop()
                mainHandler.postDelayed(
                    {
                        player.release()
                        teardown.releaseOrphan()
                    },
                    DEFERRED_RELEASE_MS,
                )
            }
        }
    }

    LaunchedEffect(url) {
        if (nowPlaying) lastMediaTimestamp = textureView?.surfaceTexture?.timestamp ?: 0L
        rendered = false
        clipAspect = 0f
        reportAspect(0f)
        val item = MediaItem.Builder().setUri(url)
        mimeTypeOf(url)?.let { item.setMimeType(it) }
        // From where a still of this clip was taken, if there is one, so the
        // first frame painted is that still and takes over from it unseen.
        val startAt = currentStartPositionMs
        gate.holdingStill = startAt > 0L
        if (nowPlaying) {
            runCatching {
                player.setMediaItem(item.build(), startAt)
                player.prepare()
            }.onFailure { rendered = false }
        } else {
            player.setMediaItem(item.build(), startAt)
            player.prepare()
        }
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
    LaunchedEffect(foreground, pausedForTransition) {
        val play = foreground && !pausedForTransition
        val afterTransition = gate.heldForTransition
        gate.heldForTransition = pausedForTransition
        // Held a moment longer on the way out of a transition, if asked: the
        // player has only just settled, and the clip starting with it reads
        // as part of the movement rather than as the clip.
        if (play && afterTransition && currentResumeDelayMs > 0L) delay(currentResumeDelayMs)
        player.playWhenReady = play
    }

    // The frame on screen, kept for the caller to stand in for the clip while
    // it moves — see [captureStill]. Read off the decoder's own output, not the
    // view: the copy runs on the render thread and leaves this one free.
    val reportStill by rememberUpdatedState(onStillCaptured)
    LaunchedEffect(captureStill) {
        if (!captureStill) return@LaunchedEffect
        // The pause was asked for this frame. Let the decoder's last frame land.
        withFrameMillis { }
        withFrameMillis { }
        // Nothing on screen to keep — a clip that has only just mounted, its
        // caller still holding the last still. Leave that one standing.
        val view = textureView
        if (!rendered || view == null) return@LaunchedEffect
        val surface = view.surfaceTexture ?: return@LaunchedEffect
        val report = reportStill
        val position = player.currentPosition
        val capturedUrl = url
        val still = view.copyStill(player.videoSize, clipAspect, contentMode, alignPortraitTop)
        // Do not assign a copy to a new rendition or a replacement surface.
        if (!isActive || !rendered || url != capturedUrl || view.surfaceTexture !== surface) {
            still?.recycle()
            return@LaunchedEffect
        }
        if (still != null) gate.holdingStill = true
        report(still?.asImageBitmap(), position)
    }

    // Repaint onto a surface that has just been handed back. A TextureView's
    // SurfaceTexture does not survive every background/layout transition, and
    // ExoPlayer's old "first frame rendered" callback says nothing about the
    // replacement surface. Keep the still artwork visible while waiting, ask
    // the decoder to paint at its current position, and only hand back to the
    // clip when onSurfaceTextureUpdated confirms real pixels below.
    LaunchedEffect(surfaceGeneration) {
        if (surfaceGeneration == 0) return@LaunchedEffect
        if (!nowPlaying) rendered = false
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
    }
    val reportFrame by rememberUpdatedState(onFrameCaptured)
    LaunchedEffect(rendered, frameCapturePx) {
        if (!rendered) return@LaunchedEffect
        // Let the surface actually paint the frame that just triggered this
        // before reading it back — grabbing it the instant the callback fires
        // can still catch the previous, empty buffer.
        if (frameCapturePx <= 0) return@LaunchedEffect
        withFrameMillis { }
        val view = textureView ?: return@LaunchedEffect
        view.captureAt(frameCapturePx, clipAspect, contentMode, alignPortraitTop)
            ?.let { reportFrame(it.asImageBitmap()) }
    }

    // The opt-in follow-up to the capture above, for a caller that asked for
    // one — see [refreshFrameEveryMs]. A separate effect rather than a loop
    // folded into the one above: that one is keyed on [rendered] so it fires
    // again on every fade-in, and this one only needs to start once a fade-in
    // has actually happened and then keep going for as long as it holds. Not
    // while the clip holds still, whose next frame would be this one again.
    LaunchedEffect(
        rendered, refreshFrameEveryMs, frameCapturePx, clipAspect, contentMode, alignPortraitTop,
        isPlaying, pausedForTransition,
    ) {
        val interval = refreshFrameEveryMs ?: return@LaunchedEffect
        if (frameCapturePx <= 0) return@LaunchedEffect
        if (!rendered || !isPlaying || pausedForTransition) return@LaunchedEffect
        while (isActive) {
            delay(interval)
            val view = textureView ?: continue
            view.captureAt(frameCapturePx, clipAspect, contentMode, alignPortraitTop)
                ?.let { reportFrame(it.asImageBitmap()) }
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (rendered) 1f else 0f,
        animationSpec = if (revealInstantly) snap() else tween(durationMillis = 320),
        label = "canvasAlpha",
    )

    // Published rather than left for the caller to mirror with a second
    // animation off [onRenderedChanged]: one fade, one account of how far along
    // it is. Zeroed on the way out, or a caller would be left holding something
    // hidden behind a clip that is no longer mounted.
    val reportCover by rememberUpdatedState(onCoverChanged)
    // Standby reports readiness, not coverage. Its callers ignore coverage until
    // promotion; do not create a second observer of every reveal sample.
    val reportsCover = organicReveal == null || organicReveal.outgoingLiveAlpha != null
    LaunchedEffect(reportsCover) {
        if (reportsCover) snapshotFlow { alpha * currentPresentationAlpha() }.collect { reportCover(it) }
    }
    DisposableEffect(Unit) {
        onDispose {
            // The parent owns the still/canvas handoff. Never leave it holding
            // a Success from a player or TextureView that no longer exists.
            reportRendered(false)
            reportCover(0f)
            reportAspect(0f)
        }
    }

    val reducedMotion = if (organicReveal?.allowSpatialMask == true) AppSettings.reduceAnimation.collectAsState() else null
    val systemMotion = if (organicReveal?.allowSpatialMask == true) rememberCoroutineScope().coroutineContext[MotionDurationScale] else null
    val incomingLivePair = organicReveal?.takeIf { it.outgoingLiveAlpha == null }?.livePair
    if (incomingLivePair != null) {
        DisposableEffect(incomingLivePair) {
            onDispose { incomingLivePair.maskedIncomingBounds = IntSize.Zero }
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
                if (nowPlaying) setLayerPaint(Paint().apply { this.alpha = 0 }) else this.alpha = 0f
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
                        if (nowPlaying) lastMediaTimestamp = 0L
                        delegate?.onSurfaceTextureAvailable(surface, width, height)
                        // The player has moved onto the new surface, so the
                        // one it was left holding can go.
                        teardown.releaseOrphan()
                        // The first surface needs nothing: prepare() paints it.
                        if (!replacing) return
                        replacing = false
                        surfaceGeneration++
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        delegate?.onSurfaceTextureSizeChanged(surface, width, height)
                        if (currentContentMode == CanvasContentMode.FIT_PORTRAIT && clipAspect in 0f..1f) {
                            rendered = false
                        }
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        replacing = true
                        // The old buffer is gone now, not when/if ExoPlayer
                        // later reports another first frame. Restore the still
                        // artwork immediately so an empty replacement surface
                        // can never become the only visible artwork layer.
                        rendered = false
                        pendingTextureFrame = null
                        if (owned?.released == true) return true
                        // Going with the player — see [Teardown.orphan].
                        if (currentPausedForTransition || teardown.disposed) {
                            teardown.orphan = surface
                            return false
                        }
                        return delegate?.onSurfaceTextureDestroyed(surface) ?: true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                        delegate?.onSurfaceTextureUpdated(surface)
                        if (!confirmTextureFrame(surface) && nowPlaying && !rendered && owned?.closed != true) {
                            pendingTextureFrame = surface
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
            val applied = frame.applied
            // Set on the view itself. A Compose alpha layer over a TextureView
            // is not reliably composited, and this is the same fade either way.
            val ready = !((nowPlaying && !rendered) || (contentMode == CanvasContentMode.FIT_PORTRAIT && clipAspect <= 0f))
            val visibility = if (ready) {
                // Called here, in the view's update, so a fade driven by the
                // player's collapse re-runs this block rather than
                // recomposing the player around it.
                alpha * presentationAlpha()
            } else 0f
            val progress = organicReveal?.progress?.invoke() ?: 1f
            val outgoingStill = organicReveal?.outgoingStill
            val outgoingLiveVisibility = organicReveal?.outgoingLiveAlpha?.let { if (ready) alpha * it() else 0f }
            val viewBounds = IntSize(view.width, view.height)
            // Only when something they are made of has changed. The fade above
            // re-runs this block on every frame of a collapse or of the player
            // closing into the mini player, and each pass built a fresh matrix,
            // gradient and three RenderEffects for a picture that hadn't
            // moved — and re-setting the effect makes the node rebuild it.
            if (applied.transformDiffers(clipAspect, contentMode, alignPortraitTop, view.width, view.height)) {
                view.applyContentTransform(clipAspect, contentMode, alignPortraitTop)
            }
            val masked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val fadeChanged = applied.fadeDiffers(bottomFade, bounds, bottomFadeEndPx)
                if (fadeChanged) frame.updateBottomFade(bottomFade, bounds, bottomFadeEndPx)
                val generation = organicReveal?.generation?.takeIf {
                    organicReveal.allowSpatialMask && nowPlaying && (outgoingLiveVisibility ?: visibility) > 0f &&
                        (outgoingLiveVisibility == null || organicReveal.livePair?.maskedIncomingBounds == viewBounds) &&
                        reducedMotion?.value != true && systemMotion?.scaleFactor != 0f
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    frame.applyOrganicReveal(view, generation, progress, fadeChanged, outgoingStill, outgoingLiveVisibility != null)
                } else {
                    if (fadeChanged) view.setRenderEffect(frame.bottomEffect)
                    false
                }
            } else {
                frame.fadeFraction = bottomFade
                frame.fadeEndPx = bottomFadeEndPx
                false
            }
            // Only compensate a real spatial reveal, not an opacity fallback or a
            // view whose presentation is fading independently of the mask.
            incomingLivePair?.maskedIncomingBounds = if (masked && visibility == 1f) viewBounds else IntSize.Zero
            // Texture paint keeps hidden standby traversable so it can latch a frame.
            // Only readiness/presentation goes here when the shader supplies coverage.
            val opacity = when {
                outgoingStill != null -> if (masked) alpha * outgoingStill.presentationAlpha() else visibility
                outgoingLiveVisibility != null -> if (masked) outgoingLiveVisibility else visibility
                masked -> visibility
                else -> visibility * progress
            }
            if (nowPlaying) frame.setTextureOpacity(view, opacity) else view.alpha = opacity
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
private fun TextureView.captureAt(
    maxPx: Int,
    clipAspect: Float,
    contentMode: CanvasContentMode,
    alignPortraitTop: Boolean,
): Bitmap? {
    val viewWidth = width
    val viewHeight = height
    if (viewWidth <= 0 || viewHeight <= 0) return null
    val scale = maxPx.toFloat() / maxOf(viewWidth, viewHeight)
    return runCatching {
        // TextureView.getBitmap copies its texture layer, before the view's
        // RenderEffect; the pre-31 mask is on the parent frame instead.
        // Neither display-only fade is baked into the mesh's sampled frame.
        val frame = if (scale >= 1f) {
            getBitmap()
        } else {
            getBitmap(
                (viewWidth * scale).roundToInt().coerceAtLeast(1),
                (viewHeight * scale).roundToInt().coerceAtLeast(1),
            )
        } ?: return null
        if (contentMode != CanvasContentMode.FIT_PORTRAIT || clipAspect >= 1f || clipAspect <= 0f) {
            return frame
        }

        // getBitmap includes the view's transform. Read back only at the small
        // requested size, then exclude the transparent contain margins before
        // handing pixels to the mesh or legacy palette (both average RGB).
        val viewAspect = viewWidth.toFloat() / viewHeight
        val contentWidth = if (clipAspect < viewAspect) frame.height * clipAspect else frame.width.toFloat()
        val contentHeight = if (clipAspect < viewAspect) frame.height.toFloat() else frame.width / clipAspect
        val left = ceil((frame.width - contentWidth) / 2f).toInt().coerceIn(0, frame.width - 1)
        val top = if (alignPortraitTop) 0 else
            ceil((frame.height - contentHeight) / 2f).toInt().coerceIn(0, frame.height - 1)
        val right = (frame.width - left).coerceAtLeast(left + 1)
        val bottom = if (alignPortraitTop) contentHeight.toInt().coerceIn(1, frame.height) else
            (frame.height - top).coerceAtLeast(top + 1)
        Bitmap.createBitmap(frame, left, top, right - left, bottom - top)
    }.getOrNull()
}

/**
 * The clip's current frame at the size it is shown, for a caller to draw in
 * its place — see [AndroidCanvasArtworkPlayer]'s `captureStill`.
 *
 * Copied asynchronously off the decoder's last buffer with [PixelCopy]. It comes back
 * as the picture itself: no view transform, no transparent margins, the
 * clip's own shape, for the caller to crop exactly as the view does. Where
 * that copy fails, read back off the view instead, as [captureAt] does.
 */
private suspend fun TextureView.copyStill(
    video: VideoSize,
    clipAspect: Float,
    contentMode: CanvasContentMode,
    alignPortraitTop: Boolean,
): Bitmap? {
    val texture = surfaceTexture ?: return null
    val videoWidth = (video.width * video.pixelWidthHeightRatio).roundToInt()
    val videoHeight = video.height
    if (videoWidth <= 0 || videoHeight <= 0 || width <= 0 || height <= 0) return null
    // No bigger than the view shows it: across its width when contained, and
    // covering it when cropped.
    val contained = contentMode == CanvasContentMode.FIT_PORTRAIT && clipAspect > 0f && clipAspect < 1f
    val scale = minOf(
        1f,
        if (contained) {
            width.toFloat() / videoWidth
        } else {
            maxOf(width.toFloat() / videoWidth, height.toFloat() / videoHeight)
        },
    )
    val bitmap = Bitmap.createBitmap(
        (videoWidth * scale).roundToInt().coerceAtLeast(1),
        (videoHeight * scale).roundToInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val result = suspendCancellableCoroutine<Int> { continuation ->
        val surface = Surface(texture)
        try {
            PixelCopy.request(
                surface,
                bitmap,
                { result ->
                    // The request may still be using both objects after cancellation.
                    surface.release()
                    if (continuation.isActive) continuation.resume(result) else bitmap.recycle()
                },
                mainHandler,
            )
        } catch (_: IllegalArgumentException) {
            surface.release()
            continuation.resume(PixelCopy.ERROR_SOURCE_INVALID)
        }
    }
    if (result == PixelCopy.SUCCESS) return bitmap
    Log.w(TAG, "still copy failed ($result), reading the view instead")
    bitmap.recycle()
    val fallback = captureAt(maxOf(width, height), clipAspect, contentMode, alignPortraitTop) ?: return null
    // getBitmap can return an unchanged, transparent bitmap on copy failure.
    if (fallback.getPixel(fallback.width / 2, fallback.height / 2) ushr 24 == 0) {
        fallback.recycle()
        return null
    }
    return fallback
}

/**
 * Big enough that averaging it is stable, small enough that reading it back off
 * the GPU is not an event. Every consumer reduces this to a handful of colours.
 */
private const val FRAME_CAPTURE_PX = 128

/**
 * A TextureView stretches its content to its own bounds. Compensate with a
 * transform: cover by default, or contain for a portrait clip when the hero
 * explicitly requests it. An unknown size clears the old transform.
 */
private fun TextureView.applyContentTransform(
    clipAspect: Float,
    contentMode: CanvasContentMode,
    alignPortraitTop: Boolean,
): Boolean {
    val bounds = IntSize(width, height)
    if (bounds.width <= 0 || bounds.height <= 0 || !clipAspect.isFinite() || clipAspect <= 0f) {
        setTransform(Matrix())
        return false
    }
    val viewAspect = bounds.width.toFloat() / bounds.height
    val pivotX = bounds.width / 2f
    val pivotY = if (alignPortraitTop && contentMode == CanvasContentMode.FIT_PORTRAIT && clipAspect < 1f) {
        0f
    } else bounds.height / 2f
    val matrix = Matrix().apply {
        val fit = contentMode == CanvasContentMode.FIT_PORTRAIT && clipAspect < 1f
        if (fit && clipAspect > viewAspect) {
            setScale(1f, viewAspect / clipAspect, pivotX, pivotY)
        } else if (fit) {
            setScale(clipAspect / viewAspect, 1f, pivotX, pivotY)
        } else if (clipAspect > viewAspect) {
            setScale(clipAspect / viewAspect, 1f, pivotX, pivotY)
        } else {
            setScale(1f, viewAspect / clipAspect, pivotX, pivotY)
        }
    }
    setTransform(matrix)
    return true
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
private fun createBottomFade(fraction: Float, bounds: IntSize, endPx: Float?): LinearGradient? {
    val endY = endPx?.coerceIn(0f, bounds.height.toFloat()) ?: bounds.height.toFloat()
    if (fraction <= 0.001f || endY <= 0f) return null
    // Along smootherstep, as the still sleeve's banner dissolves, so the clip
    // and a still of it standing in for it end the same way.
    return LinearGradient(
        0f,
        endY * (1f - fraction.coerceAtMost(1f)),
        0f,
        endY,
        IntArray(FADE_CURVE_STOPS + 1) { i ->
            android.graphics.Color.argb(1f - smootherStep(i / FADE_CURVE_STOPS.toFloat()), 0f, 0f, 0f)
        },
        FloatArray(FADE_CURVE_STOPS + 1) { i -> i / FADE_CURVE_STOPS.toFloat() },
        Shader.TileMode.CLAMP,
    )
}

/**
 * The pre-[Build.VERSION_CODES.S] bottom fade: the same dissolve
 * [createBottomFade] gets from a [RenderEffect], done the way it was done before
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
/**
 * What the update block last applied to the clip's view, so it re-applies a
 * transform or a fade only when one of its inputs has actually changed.
 */
private class AppliedLook {
    private var aspect = Float.NaN
    private var mode: CanvasContentMode? = null
    private var alignTop = false
    private var width = -1
    private var height = -1

    private var fade = Float.NaN
    private var fadeBounds = IntSize(-1, -1)
    private var fadeEnd: Float? = Float.NaN

    fun transformDiffers(
        clipAspect: Float,
        contentMode: CanvasContentMode,
        alignPortraitTop: Boolean,
        viewWidth: Int,
        viewHeight: Int,
    ): Boolean {
        if (clipAspect == aspect && contentMode == mode && alignPortraitTop == alignTop &&
            viewWidth == width && viewHeight == height
        ) return false
        aspect = clipAspect
        mode = contentMode
        alignTop = alignPortraitTop
        width = viewWidth
        height = viewHeight
        return true
    }

    fun fadeDiffers(fraction: Float, bounds: IntSize, endPx: Float?): Boolean {
        if (fraction == fade && bounds == fadeBounds && endPx == fadeEnd) return false
        fade = fraction
        fadeBounds = bounds
        fadeEnd = endPx
        return true
    }
}

private class FadingBottomFrame(context: Context) : FrameLayout(context) {
    /** See [AppliedLook]. */
    val applied = AppliedLook()
    private var bottomShader: LinearGradient? = null
    var bottomEffect: RenderEffect? = null
        private set
    private var textureOpacity = 0
    private var reveal: LiveAutoMixMask? = null
    private var revealFailed = false
    private var revealProgress = 0f
    private var revealBounds = IntSize.Zero
    private var revealGeneration: Long? = null
    private var outgoingMask: OutgoingFeatherRevealMask? = null
    private var outgoingMaskFailed = false
    private var outgoingLiveMaskFailed = false
    private var stillBounds = IntSize.Zero
    private var stillFraction = 0f
    private var stillShader: LinearGradient? = null
    private var outgoingFeatherApplied: Shader? = null

    @RequiresApi(Build.VERSION_CODES.S)
    fun updateBottomFade(fraction: Float, bounds: IntSize, endPx: Float?) {
        bottomShader = createBottomFade(fraction, bounds, endPx)
        bottomEffect = bottomShader?.let {
            RenderEffect.createBlendModeEffect(
                RenderEffect.createOffsetEffect(0f, 0f), // This node's own content.
                RenderEffect.createShaderEffect(it),
                BlendMode.DST_IN,
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun applyOrganicReveal(
        view: TextureView,
        generation: Long?,
        progress: Float,
        fadeChanged: Boolean,
        outgoingStill: CanvasStillRevealTarget?,
        outgoingLive: Boolean,
    ): Boolean {
        val bounds = IntSize(view.width, view.height)
        if (generation == null || !view.isHardwareAccelerated || !(progress > 0f && progress < 1f) ||
            bounds.width <= 0 || bounds.height <= 0
        ) {
            restoreBottomEffect(view, fadeChanged)
            return false
        }
        if (outgoingStill != null || outgoingLive) {
            val maskFailed = if (outgoingLive) outgoingLiveMaskFailed else outgoingMaskFailed
            val invalidStill = outgoingStill != null && (!stillRevealMaskAvailable() ||
                outgoingStill.bounds.width != bounds.width || outgoingStill.bounds.height !in 1..bounds.height)
            if (maskFailed || invalidStill) {
                restoreBottomEffect(view, fadeChanged)
                return false
            }
            if (outgoingStill != null && (stillBounds != outgoingStill.bounds || stillFraction != outgoingStill.bottomFade)) {
                stillBounds = outgoingStill.bounds
                stillFraction = outgoingStill.bottomFade
                stillShader = createBottomFade(stillFraction, stillBounds, null)
            }
            // Matched live slots have the same top-aligned footprint and feather.
            val feather = if (outgoingLive) bottomShader else stillShader
            val target = outgoingStill?.bounds ?: bounds
            if (feather == null) {
                restoreBottomEffect(view, fadeChanged)
                return false
            }
            if (!fadeChanged && feather === outgoingFeatherApplied &&
                progress == revealProgress && bounds == revealBounds && generation == revealGeneration
            ) return true
            return runCatching {
                val mask = outgoingMask ?: OutgoingFeatherRevealMask().also { outgoingMask = it }
                view.setRenderEffect(
                    mask.effect(target.width.toFloat(), target.height.toFloat(), progress, generation, bottomShader, feather),
                )
                outgoingFeatherApplied = feather
                revealProgress = progress
                revealBounds = bounds
                revealGeneration = generation
                true
            }.getOrElse {
                if (outgoingLive) outgoingLiveMaskFailed = true else outgoingMaskFailed = true
                restoreBottomEffect(view, fadeChanged)
                false
            }
        }
        if (revealFailed) {
            restoreBottomEffect(view, fadeChanged)
            return false
        }
        if (!fadeChanged && outgoingFeatherApplied == null && progress == revealProgress &&
            bounds == revealBounds && generation == revealGeneration
        ) return true
        return runCatching {
            val mask = reveal ?: LiveAutoMixMask().also { reveal = it }
            // Bind to the RenderNode that records drawTextureLayer, preserving its
            // existing bottom gradient without an intermediate image filter.
            view.setRenderEffect(mask.effect(bounds.width.toFloat(), bounds.height.toFloat(), progress, generation, bottomShader))
            outgoingFeatherApplied = null
            revealProgress = progress
            revealBounds = bounds
            revealGeneration = generation
            true
        }.getOrElse {
            revealFailed = true
            restoreBottomEffect(view, fadeChanged)
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun restoreBottomEffect(view: TextureView, fadeChanged: Boolean) {
        if (revealProgress == 0f && !fadeChanged) return
        view.setRenderEffect(bottomEffect)
        revealProgress = 0f
        outgoingFeatherApplied = null
    }

    fun setTextureOpacity(view: TextureView, opacity: Float) {
        val alpha = (opacity.coerceIn(0f, 1f) * 255).roundToInt()
        if (alpha == textureOpacity) return
        textureOpacity = alpha
        view.setLayerPaint(if (alpha == 255) null else Paint().apply { this.alpha = alpha })
    }

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
    /** Optional video bottom in this frame's pixels; null retains the historical view bottom. */
    var fadeEndPx: Float? = null
        set(value) {
            if (value == field) return
            field = value
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
        val endY = fadeEndPx?.coerceIn(0f, height.toFloat()) ?: height.toFloat()
        if (fade <= 0.001f || endY <= 0f) {
            super.dispatchDraw(canvas)
            return
        }
        // Along smootherstep, the same dissolve the still artwork's banner uses,
        // so neither end of it shows as a line across the video.
        val shader = gradient?.takeIf { gradientHeight == height } ?: LinearGradient(
            0f,
            endY * (1f - fade),
            0f,
            endY,
            IntArray(FADE_CURVE_STOPS + 1) { i ->
                android.graphics.Color.argb(1f - smootherStep(i / FADE_CURVE_STOPS.toFloat()), 0f, 0f, 0f)
            },
            FloatArray(FADE_CURVE_STOPS + 1) { i -> i / FADE_CURVE_STOPS.toFloat() },
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
internal fun mimeTypeOf(url: String): String? {
    val path = url.substringBefore('?').lowercase(Locale.ROOT)
    return when {
        path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
        path.endsWith(".mp4") -> MimeTypes.VIDEO_MP4
        else -> null
    }
}

/** Shared by the displayed clip and the AutoMix standby; silent and no audio focus. */
internal fun newCanvasDecoder(context: Context): ExoPlayer {
    val player = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(CanvasCache.dataSourceFactory(OkHttpDataSource.Factory(Http.client))),
        )
        .build()
    try {
        player.volume = 0f
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .apply { CanvasPlaybackLimits.maxFrameRate?.let(::setMaxVideoFrameRate) }
            .build()
        return player
    } catch (e: Exception) {
        runCatching { player.release() }
        throw e
    }
}

@Composable
private fun rememberNowPlayingDecoder(context: Context, teardown: Teardown): NowPlayingCanvasDecoders.Lease? {
    var lease by remember(context) { mutableStateOf<NowPlayingCanvasDecoders.Lease?>(null) }
    LaunchedEffect(context) {
        // Do not allocate a third player during the apply pass of a layout switch.
        NowPlayingCanvasDecoders.awaitCapacity()
        val opened = NowPlayingCanvasDecoders.open(context) ?: return@LaunchedEffect
        try {
            lease = opened
            awaitCancellation()
        } finally {
            teardown.disposed = true
            // The surface stays orphaned until the deferred release has let go of it.
            opened.close { teardown.releaseOrphan() }
        }
    }
    return lease
}

/** The phone's decoder behind [CanvasArtworkPlayer] — see [PlayerHost.CanvasVideo]. */
@Composable
internal fun AndroidCanvasVideo(spec: CanvasVideoSpec, modifier: Modifier) {
    key(spec.canvas.url.takeIf { spec.nowPlaying }) {
        AndroidCanvasVideoContents(spec, modifier)
    }
}

@Composable
private fun AndroidCanvasVideoContents(spec: CanvasVideoSpec, modifier: Modifier) {
    AndroidCanvasArtworkPlayer(
        canvas = spec.canvas,
        isPlaying = spec.isPlaying,
        modifier = modifier,
        contentMode = spec.contentMode,
        alignPortraitTop = spec.alignPortraitTop,
        onAspectRatioChanged = spec.onAspectRatioChanged,
        portraitRevealBounds = spec.portraitRevealBounds,
        presentationAlpha = spec.presentationAlpha,
        organicReveal = spec.organicReveal,
        onRenderedChanged = spec.onRenderedChanged,
        onFrameCaptured = spec.onFrameCaptured,
        refreshFrameEveryMs = spec.refreshFrameEveryMs,
        frameCapturePx = spec.frameCapturePx,
        onCoverChanged = spec.onCoverChanged,
        bottomFade = spec.bottomFade,
        bottomFadeEndPx = spec.bottomFadeEndPx,
        pausedForTransition = spec.pausedForTransition,
        resumeDelayMs = spec.resumeDelayMs,
        startPositionMs = spec.startPositionMs,
        revealInstantly = spec.revealInstantly,
        captureStill = spec.captureStill,
        onStillCaptured = spec.onStillCaptured,
        onResumed = spec.onResumed,
        nowPlaying = spec.nowPlaying,
    )
}

/**
 * A ceiling on the frame rate canvas videos are decoded at, or null for none.
 * Left unset on the phone; the Android TV app sets it, where a looping 60 fps
 * clip behind the player costs GPU the television does not have to spare.
 */
object CanvasPlaybackLimits {
    @Volatile
    var maxFrameRate: Int? = null
}
