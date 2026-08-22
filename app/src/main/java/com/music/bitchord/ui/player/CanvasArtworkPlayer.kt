package com.music.bitchord.ui.player

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.music.bitchord.data.Http
import com.music.bitchord.data.canvas.CanvasArtwork

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

    val player = remember {
        ExoPlayer.Builder(context)
            // Shares the app's one OkHttp client, as everything that fetches
            // over the network here does.
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(OkHttpDataSource.Factory(Http.client)),
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
            override fun onRenderedFirstFrame() {
                rendered = true
            }

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

    LaunchedEffect(isPlaying) { player.playWhenReady = isPlaying }

    LaunchedEffect(rendered) {
        onRenderedChanged(rendered)
        if (!rendered) return@LaunchedEffect
        // Let the surface actually paint the frame that just triggered this
        // before reading it back — grabbing it the instant the callback fires
        // can still catch the previous, empty buffer.
        withFrameMillis { }
        val view = textureView ?: return@LaunchedEffect
        runCatching { view.getBitmap() }.getOrNull()?.let(onFrameCaptured)
    }

    val alpha by animateFloatAsState(
        targetValue = if (rendered) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "canvasAlpha",
    )

    AndroidView(
        factory = { viewContext ->
            TextureView(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                // Blend rather than punch a hole: the still sleeve stays
                // visible underneath for the length of the fade.
                isOpaque = false
                this.alpha = 0f
                player.setVideoTextureView(this)
                textureView = this
            }
        },
        update = { view ->
            // Set on the view itself. A Compose alpha layer over a TextureView
            // is not reliably composited, and this is the same fade either way.
            view.alpha = alpha
            view.centerCrop(bounds, clipAspect)
            if (CANVAS_HERO_SUPPORTED) view.setBottomFade(bottomFade, bounds)
        },
        modifier = modifier.onSizeChanged { bounds = it },
    )
}

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
 * [RenderEffect] is API 31+. Below that the effect is skipped, which is why
 * [CANVAS_HERO_SUPPORTED] gates the full-bleed treatment: a banner running off
 * the top of the screen and stopping dead halfway down it looks like a bug,
 * where the same clip inside its sleeve looks intentional.
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

/** Whether motion artwork can be given the full-bleed treatment — see [setBottomFade]. */
val CANVAS_HERO_SUPPORTED = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Apple serves HLS, Tidal and the community index serve MP4. Naming the type
 * saves ExoPlayer a sniff, and an unrecognised URL is left for it to work out.
 */
private fun mimeTypeOf(url: String): String? {
    val path = url.substringBefore('?').lowercase()
    return when {
        path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
        path.endsWith(".mp4") -> MimeTypes.VIDEO_MP4
        else -> null
    }
}
