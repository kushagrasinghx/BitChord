package com.music.bitchord.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas as BitmapCanvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.LocalPlatformContext
import com.music.bitchord.ui.graphics.argbPixels
import com.music.bitchord.ui.graphics.forPixelAccess
import com.music.bitchord.ui.graphics.imageBitmapOf
import com.music.bitchord.ui.graphics.toImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.graphics.ColorUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.PlatformContext
import coil3.BitmapImage
import coil3.Extras
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import coil3.size.Scale
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.QueueEntryIdentity
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.artworkAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The artwork's own colours, upside down and reduced to a mesh, for the player
 * to stand on.
 *
 * [MeshGradientBackground] answers a different question: it asks the quantiser
 * what colours a sleeve is *about* and paints four blobs of them. That is why a
 * cover that is nine-tenths black with a red stripe came out as a red screen —
 * the quantiser reports red because red is the interesting answer, and nothing
 * downstream knows how little of the picture it was. It also has no idea *where*
 * in the frame that red was.
 *
 * This is the other approach: no quantiser at all. The sleeve is averaged into a
 * [MESH_GRID] square of means; the row against the seam is kept in place, and
 * everything below it is flipped and then rotated sideways — see
 * [rotatedBelowSeam]. What the backdrop holds is the cover's own colours,
 * roughly in the cover's own proportions (nine-tenths black stays nine-tenths
 * black) and with each cell's neighbours exactly what they were in the source,
 * but not lined up in the cover's own *position*: a flip on its own reads as a
 * reflection on any cover with real structure to it — a face, a horizon, a
 * logo — however coarse the grid, because the layout still lines up column for
 * column with what's on screen above it. The rotation is what actually breaks
 * that column-for-column match; the coarseness just keeps any one cell from
 * being recognisable on its own.
 *
 * Held as a tiny bitmap rather than a list of colours because that is exactly
 * what the hardware wants: one [DrawScope.drawImage] with bilinear filtering
 * interpolates the whole mesh in the sampler. The alternative — a blob per cell,
 * as the old backdrop draws — would be thirty-six full-screen radial gradients.
 */
@Immutable
class ArtworkMesh internal constructor(internal val image: ImageBitmap)

/**
 * One pre-blurred bitmap for the full-cover player backdrop.
 *
 * This deliberately does not use Compose's full-screen `Modifier.blur`: that is
 * a live RenderEffect and the first frame which makes it visible can pay for a
 * screen-sized blur while the player is already animating. Instead a 128px copy
 * is decoded from Coil's existing artwork request, blurred once on a worker
 * thread, and cached by URL. Drawing it thereafter is one bilinear image sample.
 * The small decode and CPU pass are staggered by the caller after a song change,
 * so they never land on the track hand-off itself.
 *
 * Hold the last blur until the selected cover is ready. Clear it on failure
 * or when there is no selected cover, rather than retaining a stale bitmap.
 */
@Composable
fun rememberFullArtworkBlurImage(
    imageUrl: String?,
    artPx: Int = CARD_ART_PX,
    prepare: Boolean = true,
): ImageBitmap? = rememberFullArtworkBlurImage(imageUrl, artPx, prepare, seed = null)

@Composable
internal fun rememberFullArtworkBlurImage(
    imageUrl: String?, artPx: Int, prepare: Boolean, seed: ImageBitmap?,
    currentSelectionOnly: Boolean = false,
): ImageBitmap? {
    val context = LocalPlatformContext.current
    var loaded by remember { mutableStateOf(imageUrl to (seed ?: cachedFullArtworkBlur(imageUrl))) }

    LaunchedEffect(imageUrl, artPx, prepare, seed) {
        val image = seed ?: cachedFullArtworkBlur(imageUrl) ?: if (prepare && imageUrl != null) {
            loadFullArtworkBlur(context, imageUrl, artPx)
        } else null
        loaded = imageUrl to image
    }
    return if (currentSelectionOnly && loaded.first != imageUrl) cachedFullArtworkBlur(imageUrl) else loaded.second
}

private suspend fun loadFullArtworkBlur(context: PlatformContext, imageUrl: String, artPx: Int): ImageBitmap? {
    val request = artworkPixelsRequest(context, imageUrl, artPx, FULL_BLUR_SOURCE_PX)
    val result = SingletonImageLoader.get(context).execute(request)
    val bitmap = (result as? SuccessResult)?.image?.toImageBitmap() ?: return null
    val ready = withContext(Dispatchers.Default) { bitmap.boxBlurred(FULL_BLUR_PASSES) }
    fullBlurCache[imageUrl] = ready
    return ready
}

// Read the same artwork URL as the sleeve, so small pixel reads can share its disk entry.
private fun artworkPixelsRequest(
    context: PlatformContext,
    imageUrl: String,
    artPx: Int,
    decodePx: Int,
): ImageRequest = ImageRequest.Builder(context)
    .data(imageUrl.artworkAt(artPx))
    .size(decodePx)
    .forPixelAccess()
    .build()

/** Transport hint only; not part of the decoded-artwork cache identity. */
val SpeculativeArtworkRequest = Extras.Key(default = false)

/** Cache-only proof that the ordinary full-player request has a drawable still. Call on Main. */
fun hasPrefetchedFullPlayerArtwork(fullUrl: String): Boolean = WarmPlayerCovers.get(fullUrl) != null

/** Raw aliases have distinct ordinary backdrop entries, even when their full cover request is shared. */
fun hasPreparedPlayerArtworkAmbient(imageUrl: String): Boolean =
    meshCache[imageUrl] != null && fullBlurCache[imageUrl] != null && cachedArtworkPalette(imageUrl) != null

/** Call on Main. Coil does the fetch/decode; the same pixels serve drawing and ambient analysis. */
suspend fun prefetchFullPlayerArtwork(context: PlatformContext, imageUrl: String, isCurrent: () -> Boolean): SuccessResult? {
    val url = imageUrl.artworkAt(ART_PX) ?: return null
    val request = playerCoverRequest(context, url).apply { extras[SpeculativeArtworkRequest] = true }.build()
    val result = SingletonImageLoader.get(context).execute(request)
        as? SuccessResult ?: return null
    if (!isCurrent()) return null
    (result.image as? BitmapImage)?.let { WarmPlayerCovers.remember(url, it) }
    return result
}

/** Use the existing small ambient caches. Never re-fetch the full-player source for them. */
suspend fun preparePlayerArtworkAmbient(imageUrl: String, cover: SuccessResult, isCurrent: () -> Boolean) {
    val bitmap = (cover.image as? BitmapImage)?.toImageBitmap() ?: return
    val needsMesh = meshCache[imageUrl] == null
    val needsBlur = fullBlurCache[imageUrl] == null
    val needsPalette = cachedArtworkPalette(imageUrl) == null
    if (!needsMesh && !needsBlur && !needsPalette) return
    val (mesh, blur, palette) = withContext(Dispatchers.Default) {
        val small = bitmap.ambientThumbnail()
        Triple(
            if (needsMesh) meshOf(small, imageUrl.hashCode()) else null,
            if (needsBlur) small.boxBlurred(FULL_BLUR_PASSES) else null,
            if (needsPalette) MeshPalette(artworkMeshColors(small)) else null,
        )
    }
    if (!isCurrent()) return
    mesh?.let { meshCache[imageUrl] = it }
    blur?.let { fullBlurCache[imageUrl] = it }
    palette?.let { cacheArtworkPalette(imageUrl, it) }
}

private fun ImageBitmap.ambientThumbnail(): ImageBitmap {
    val ratio = FULL_BLUR_SOURCE_PX.toFloat() / maxOf(width, height)
    if (ratio >= 1f) return this
    val target = IntSize((width * ratio).roundToInt().coerceAtLeast(1), (height * ratio).roundToInt().coerceAtLeast(1))
    return ImageBitmap(target.width, target.height).also {
        BitmapCanvas(it).drawImageRect(this, dstSize = target, paint = Paint().apply { filterQuality = FilterQuality.Low })
    }
}

fun retainAutoMixPlayerCover(generation: Long, cover: SuccessResult) {
    AutoMixPreparedCover.retain(generation, cover)
}

fun retainAutoMixPlayerAmbient(generation: Long, imageUrl: String) {
    preparedAutoMixAmbient = PreparedAutoMixAmbient(generation, imageUrl).also {
        it.mesh = meshCache[imageUrl]
        it.blur = fullBlurCache[imageUrl]
        it.palette = cachedArtworkPalette(imageUrl)
    }
}

/** Mini players use a different URL/size from the full-player cover. */
suspend fun prefetchAutoMixMiniArtwork(context: PlatformContext, imageUrl: String, generation: Long, isArmed: () -> Boolean) {
    val result = SingletonImageLoader.get(context).execute(
        ImageRequest.Builder(context).data(imageUrl.artworkAt(ROW_ART_PX)).size(ROW_ART_PX)
            .apply { extras[SpeculativeArtworkRequest] = true }
            .scale(Scale.FILL).precision(Precision.INEXACT).build(),
    )
    if (result is SuccessResult && isArmed()) AutoMixPreparedCover.retainMini(generation, result)
}

fun clearPrefetchedPlayerArtwork() {
    WarmPlayerCovers.clear()
    AutoMixPreparedCover.clear()
    preparedAutoMixAmbient = null
}

/**
 * The player's backdrop wherever there is no sleeve to hang a mesh off: the
 * lyrics, the queue and a tablet — and the main player too under
 * [PlayerTheme.SIMPLE][com.music.bitchord.data.settings.PlayerTheme.SIMPLE]. One
 * copy of the cover cropped across the complete bounds and blurred in place,
 * with any artwork above feathering its own bottom edge into it.
 *
 * The blur is baked into [image] (see [rememberFullArtworkBlurImage]), so this
 * is a plain image draw: nothing here re-blurs while the player moves or a
 * clip plays over it. A new cover crossfades in over the last one.
 *
 * It looks the same in all three states and reads nothing that moves, so moving
 * between them never redraws it: only what sits on top of it changes.
 */
@Composable
fun FullArtworkBlurBackdrop(
    image: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    FullArtworkBlurBackdrop(image, modifier, mix = null, currentIsOutgoing = false)
}

/** Cold selections start on the fallback; warm selections keep the ordinary fade. */
@Composable
internal fun coldArtworkResetKey(selection: Any?, ready: Boolean, mixing: Boolean): Int {
    var previous by remember { mutableStateOf(selection) }
    var reset by remember { mutableStateOf(0) }
    val nextReset = if (!mixing && previous != selection && !ready) reset + 1 else reset
    SideEffect {
        if (!mixing) {
            previous = selection
            reset = nextReset
        }
    }
    return nextReset
}

@Composable
internal fun FullArtworkBlurBackdrop(
    image: ImageBitmap?, modifier: Modifier, mix: AutoMixAmbientMix?, currentIsOutgoing: Boolean,
    immediate: Boolean = false, selectionKey: Any? = null,
) {
    Box(modifier = modifier.fillMaxSize().background(FallbackBackdrop)) {
        val history = remember { BlurLayers() }
        val coldReset = coldArtworkResetKey(selectionKey, ready = image != null, mixing = mix != null)
        val heldTarget = remember(mix) {
            if (mix == null) null else history.target ?: cachedFullArtworkBlur(mix.outgoingUrl)
                ?: image.takeIf { currentIsOutgoing }
        }
        key(coldReset) {
            FullBlurTrackFade(if (mix == null) image else heldTarget, history, draw = mix == null, immediate = immediate)
        }
        if (mix != null) {
            val held = remember(mix) {
                history.layers?.map { (bitmap, alpha) -> bitmap to alpha.value }
                    ?: listOfNotNull((cachedFullArtworkBlur(mix.outgoingUrl)
                        ?: image.takeIf { currentIsOutgoing })?.let { it to 1f })
            }
            held.forEach { (bitmap, opacity) ->
                FullBlurImage(bitmap, Modifier.fillMaxSize().graphicsLayer { alpha = opacity })
            }
            mix.prepared?.blur?.let {
                FullBlurImage(it, Modifier.fillMaxSize().graphicsLayer { alpha = mix.ease() })
            }
        }

        // Everything on this is white: the lyrics, the queue and the main
        // player's controls. Preserve the cover's colour and layout, but keep
        // bright sleeves from lowering contrast.
        Canvas(Modifier.fillMaxSize()) { drawRect(brush = FullArtworkScrim) }
    }
}

private class BlurLayers {
    var target: ImageBitmap? = null
    var layers: List<Pair<ImageBitmap, State<Float>>>? = null
}

/** The ordinary Crossfade behavior, with its visible layers accessible for the AutoMix hold. */
@Composable
private fun FullBlurTrackFade(image: ImageBitmap?, history: BlurLayers, draw: Boolean, immediate: Boolean) {
    val fade = updateTransition(image, label = "preparedBackdropImage")
    val visible = remember { mutableStateListOf(fade.currentState) }
    if (immediate || (fade.currentState == fade.targetState && visible.size > 1)) {
        visible.removeAll { it != fade.targetState }
    }
    if (fade.targetState !in visible) visible.add(fade.targetState)
    val layers = visible.mapNotNull { bitmap ->
        key(bitmap) {
            val alpha = fade.animateFloat(transitionSpec = { if (immediate) snap() else tween(320, easing = FastOutSlowInEasing) }) {
                if (it == bitmap) 1f else 0f
            }
            bitmap?.let {
                if (draw) FullBlurImage(it, Modifier.fillMaxSize().graphicsLayer { this.alpha = if (immediate) 1f else alpha.value })
                it to alpha
            }
        }
    }
    SideEffect {
        if (draw) {
            history.target = image
            history.layers = layers
        }
    }
}

@Composable
private fun FullBlurImage(bitmap: ImageBitmap, modifier: Modifier) {
    val painter = remember(bitmap) { BitmapPainter(bitmap) }
    Image(painter = painter, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}

/**
 * The mesh for the artwork at [imageUrl], or null until one has been read.
 *
 * [canvasFrame] — a frame grabbed off a playing motion cover — takes over when
 * one arrives, for the reason [rememberArtworkColors] describes: a clip is
 * frequently graded nothing like the still sleeve it replaces. A caller that
 * keeps sending fresh ones gets a backdrop that follows the clip; see
 * [CanvasArtworkPlayer]'s `frameCapturePx` for what one of them costs.
 */
@Composable
fun rememberArtworkMesh(
    imageUrl: String?,
    canvasFrame: ImageBitmap? = null,
    /**
     * The artwork size to read, which should be the one the caller already has
     * on screen. Nothing here survives being averaged into thirty-six cells, so
     * resolution buys nothing and a shared disk-cache entry buys everything —
     * ask for a size no one is showing and the backdrop sits flat until a
     * second copy of the same cover comes over the wire.
     */
    artPx: Int = CARD_ART_PX,
): ArtworkMesh? = rememberArtworkMesh(imageUrl, canvasFrame, artPx, seed = null)

@Composable
internal fun rememberArtworkMesh(
    imageUrl: String?, canvasFrame: ImageBitmap?, artPx: Int, seed: ArtworkMesh?,
    clipKey: Any? = imageUrl,
): ArtworkMesh? {
    val context = LocalPlatformContext.current
    // Keep the old single displayed mesh, but scope clip-derived pixels to the
    // clip owner and let the backdrop fade to fallback when a still is missing.
    var mesh by remember(imageUrl, clipKey) {
        mutableStateOf(seed ?: clipMeshFor(clipKey) ?: imageUrl?.let(meshCache::get))
    }

    LaunchedEffect(imageUrl, artPx, clipKey) {
        if (imageUrl == null || mesh != null) return@LaunchedEffect
        val request = artworkPixelsRequest(context, imageUrl, artPx, MESH_PX)
        // Tried more than once, because this effect is keyed on the artwork and
        // nothing else: a read that fails is not retried by Coil and cannot be
        // re-triggered from here, so one dropped connection used to leave the
        // backdrop flat — no colour behind the player at all — until the track
        // changed. The cover on top of it has the same guard for the same
        // reason; see [NowPlayingScreen]'s artAttempt.
        repeat(MESH_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(MESH_RETRY_DELAY_MS)
            val result = SingletonImageLoader.get(context).execute(request)
            val bitmap = (result as? SuccessResult)?.image?.toImageBitmap()
            if (bitmap != null) {
                val found = cacheStillMesh(imageUrl, bitmap)
                if (found != null) mesh = found
                // A cover that decoded but had no mesh in it — see [meshOf] —
                // is an answer, not a failure. Asking again gets the same one.
                return@LaunchedEffect
            }
        }
    }

    LaunchedEffect(imageUrl, clipKey, canvasFrame) {
        val frame = canvasFrame ?: return@LaunchedEffect
        // Reject frames that are too dark or uniform — they are almost always
        // the first read after a surface recreation, before ExoPlayer has
        // decoded real content.  A frame whose mean luminance is below this
        // threshold is discarded; the last valid mesh is kept instead.
        val found = withContext(Dispatchers.Default) {
            if (isLikelyBlackFrame(frame)) null else meshOf(frame, imageUrl?.hashCode() ?: 0)
        } ?: return@LaunchedEffect
        mesh = found
        if (clipKey != null) lastClipMesh = ClipMesh(clipKey, found)
    }
    return mesh
}

/**
 * The player's backdrop: [mesh] hung from where the artwork stops, and stretched
 * over everything below it.
 *
 * The mesh is anchored rather than centred. Its first row is the sleeve's own
 * bottom edge and it is drawn starting at [seam], so the colour immediately
 * under the artwork is the colour the artwork ended on and there is no join to
 * hide. Above [seam] that first row is simply held — that stretch is behind the
 * artwork, and holding it means the backdrop is continuous everywhere rather
 * than only below the sleeve.
 *
 * ### What this costs
 *
 * One bilinear draw of a 32-texel texture, stretched by the sampler, and no
 * `RenderEffect`. The softness a full-screen blur used to supply is baked into
 * that texture off the main thread when it is read — see [MESH_BLUR_RADIUS] — so
 * nothing here has to be re-blurred as it changes, whatever it is sitting under.
 * A skip or a clip's new colours cost one fade of two such draws, and between
 * them the backdrop is only composited, however the player above it moves.
 *
 * The old backdrop paid a 60Hz blur permanently, for blobs orbiting behind a
 * screen nobody was looking at; see [MeshGradientBackground]'s note.
 */
@Composable
fun ArtworkMeshBackdrop(
    mesh: ArtworkMesh?,
    modifier: Modifier = Modifier,
    /**
     * How far down the surface the artwork's bottom edge sits. Zero for a
     * surface with no artwork over it, which puts the whole mesh on screen. Read
     * in the draw, so a seam that moves costs no composition.
     */
    seam: () -> Dp = { 0.dp },
    /** What the surface is before any artwork has been read. */
    fallback: Color = FallbackBackdrop,
) {
    ArtworkMeshBackdrop(mesh, modifier, seam, fallback, mix = null)
}

@Composable
internal fun ArtworkMeshBackdrop(
    mesh: ArtworkMesh?, modifier: Modifier, seam: () -> Dp, fallback: Color = FallbackBackdrop,
    mix: AutoMixAmbientMix?, currentIsOutgoing: Boolean = false,
    directSelection: Pair<Long, QueueEntryIdentity>? = null, directStill: Boolean = false,
    selectionKey: Any? = null,
) {
    val reduceAnimation by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()

    // The mesh on screen, and the one fading in over it. Two states rather than
    // one animated value because what crossfades here is a pair of bitmaps, not
    // a pair of colours: the outgoing one has to stay drawable until the fade
    // has finished with it.
    var shown by remember { mutableStateOf(mesh) }
    var incoming by remember { mutableStateOf<ArtworkMesh?>(null) }
    var fadingToFallback by remember { mutableStateOf(false) }
    val fade = remember { Animatable(0f) }
    var appliedDirect by remember { mutableStateOf<Pair<Long, QueueEntryIdentity>?>(null) }
    var appliedSelection by remember { mutableStateOf(selectionKey) }
    val immediate = (selectionKey != appliedSelection && mesh == null) ||
        (directSelection != null && (appliedDirect != directSelection || directStill))

    LaunchedEffect(mesh, mix, reduceAnimation, directSelection, directStill, selectionKey) {
        if (mix != null) return@LaunchedEffect
        val next = mesh
        appliedSelection = selectionKey
        if (immediate) {
            shown = next
            incoming = null
            fadingToFallback = false
            appliedDirect = directSelection
            return@LaunchedEffect
        }
        // A change arriving mid-fade. Whatever was fading in has been on screen
        // for a while and is what the new one should fade *from*; leaving it in
        // [incoming] would restart the fade from the mesh before it, and a clip
        // sending frames faster than the fade runs would never settle at all.
        incoming?.let { shown = it }
        incoming = null
        fadingToFallback = false
        val current = shown
        if (next === current) return@LaunchedEffect

        // Nothing to fade *from* on the first read, and nothing to fade at all
        // when the user has asked for less motion.
        if (current == null || reduceAnimation) {
            shown = next
            return@LaunchedEffect
        }
        incoming = next
        fadingToFallback = next == null
        fade.snapTo(0f)
        fade.animateTo(1f, tween(MESH_FADE_MS, easing = FastOutSlowInEasing))
        shown = next
        incoming = null
        fadingToFallback = false
    }

    // If a still/frame fade was running, pin the last drawn blend, not its target.
    val drawn = remember { DrawnMesh() }
    val outgoing = remember(mix) {
        if (mix == null) null else drawn.copy().also {
            if (!it.wasDrawn) it.base = mix.outgoingUrl?.let { url -> clipMeshFor(url) ?: meshCache[url] }
                ?: mesh.takeIf { currentIsOutgoing }
        }
    }
    Canvas(
        modifier = modifier
            .fillMaxSize()
            // A flat opaque floor: the player is a sheet with the app's own pages
            // behind it, and every pixel here has to be opaque.
            .background(fallback),
    ) {
        val seamY = seam().toPx().coerceIn(0f, size.height)
        if (outgoing != null && mix != null) {
            outgoing.base?.let { drawMesh(it, seamY, alpha = outgoing.baseAlpha) }
            outgoing.overlay?.let { drawMesh(it, seamY, alpha = outgoing.alpha) }
            mix.prepared?.mesh?.let { drawMesh(it, seamY, alpha = mix.ease()) }
        } else {
            drawn.wasDrawn = true
            // Paint the selected mesh (or the regular fallback) even before the effect settles.
            drawn.base = if (immediate) mesh else shown
            drawn.baseAlpha = if (!immediate && fadingToFallback) 1f - fade.value else 1f
            drawn.overlay = incoming.takeUnless { immediate }
            drawn.alpha = fade.value
            drawn.base?.let { drawMesh(it, seamY, alpha = drawn.baseAlpha) }
            drawn.overlay?.let { drawMesh(it, seamY, alpha = drawn.alpha) }
        }

        // Enough of a scrim to keep white text off a bright sleeve, and no
        // more. The old backdrop needed a heavier one because it lightened
        // every colour it drew to a fixed band; these are the sleeve's own,
        // and a sleeve that ends dark should leave a dark screen.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.06f),
                    Color.Black.copy(alpha = 0.30f),
                ),
            ),
        )
    }
}

private data class DrawnMesh(
    var wasDrawn: Boolean = false,
    var base: ArtworkMesh? = null,
    var baseAlpha: Float = 1f,
    var overlay: ArtworkMesh? = null,
    var alpha: Float = 0f,
)

/**
 * Paints [mesh] over the whole surface: the grid from [seamY] down, and its
 * first row held from there up. Two draws of one small texture, both stretched
 * by the sampler.
 */
private fun DrawScope.drawMesh(mesh: ArtworkMesh, seamY: Float, alpha: Float) {
    if (alpha <= 0.001f) return
    val image = mesh.image
    val width = size.width.roundToInt()

    // Above the seam: the sleeve's bottom edge, held. Skipped when the seam is
    // at the very top, where there is no such stretch to fill.
    if (seamY > 0.5f) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, 1),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(width, seamY.roundToInt()),
            alpha = alpha,
            filterQuality = FilterQuality.Low,
        )
    }

    // From the seam down: the mesh itself, stretched over whatever is left.
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(0, seamY.roundToInt()),
        dstSize = IntSize(width, (size.height - seamY).roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Low,
    )
}

/** The shade over the blurred cover, deepening toward the controls at the foot. */
private val FullArtworkScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.34f),
    0.55f to Color.Black.copy(alpha = 0.48f),
    1f to Color.Black.copy(alpha = 0.64f),
)

/** Drawn only until an artwork has been read — never a colour anyone chose. */
private val FallbackBackdrop = Color(0xFF121212)

/** A screen cannot recover detail discarded here; small is the performance feature. */
private const val FULL_BLUR_SOURCE_PX = 128
private const val FULL_BLUR_PASSES = 3

/** A handful of recent covers; each entry is only 64 KiB at 128x128 ARGB. */
internal fun cachedFullArtworkBlur(imageUrl: String?): ImageBitmap? = imageUrl?.let(fullBlurCache::get)

private val fullBlurCache = object : LinkedHashMap<String, ImageBitmap>(0, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, ImageBitmap>) = size > 8
}

/** Three small box passes approximate a broad Gaussian blur at thumbnail scale. */
private fun ImageBitmap.boxBlurred(passes: Int): ImageBitmap {
    val width = width
    val height = height
    if (width < 2 || height < 2) return this
    var source = argbPixels()
    var target = IntArray(source.size)
    val radius = (minOf(width, height) / 12).coerceAtLeast(2)

    repeat(passes) {
        boxBlurPass(source, target, width, height, radius, horizontal = true)
        boxBlurPass(target, source, width, height, radius, horizontal = false)
    }
    return imageBitmapOf(source, width, height)
}

private fun boxBlurPass(
    source: IntArray,
    target: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean,
) {
    val major = if (horizontal) width else height
    val minor = if (horizontal) height else width
    val window = radius * 2 + 1
    repeat(minor) { fixed ->
        var red = 0
        var green = 0
        var blue = 0
        fun pixel(at: Int): Int {
            val position = at.coerceIn(0, major - 1)
            return if (horizontal) source[fixed * width + position]
            else source[position * width + fixed]
        }
        for (offset in -radius..radius) {
            val color = pixel(offset)
            red += color shr 16 and 0xFF
            green += color shr 8 and 0xFF
            blue += color and 0xFF
        }
        repeat(major) { moving ->
            val index = if (horizontal) fixed * width + moving else moving * width + fixed
            target[index] = argb(
                (red + window / 2) / window,
                (green + window / 2) / window,
                (blue + window / 2) / window,
            )
            val leaving = pixel(moving - radius)
            val entering = pixel(moving + radius + 1)
            red += (entering shr 16 and 0xFF) - (leaving shr 16 and 0xFF)
            green += (entering shr 8 and 0xFF) - (leaving shr 8 and 0xFF)
            blue += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}

/**
 * Meshes already read, keyed by artwork URL — the artwork at a URL cannot
 * change, so reading it twice buys a decode and a downsample for the same
 * answer. Composition, [rememberArtworkMesh]'s effect and AutoMix prefetch
 * all access it on the main thread, so it needs no lock.
 *
 * Clip frames never land here: the next one for the same URL is a different
 * picture, and there would be a new entry several times a second.
 */
private val meshCache = object : LinkedHashMap<String, ArtworkMesh>(0, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, ArtworkMesh>) = size > MESH_CACHE_ENTRIES
}

private suspend fun cacheStillMesh(imageUrl: String, bitmap: ImageBitmap): ArtworkMesh? {
    val found = withContext(Dispatchers.Default) { meshOf(bitmap, imageUrl.hashCode()) }
    if (found != null) meshCache[imageUrl] = found
    return found
}

/** The mesh of the clip last on screen, kept for when the player comes back to it. */
private class ClipMesh(val key: Any, val mesh: ArtworkMesh)

/** One at a time: a clip's frames only matter for the clip that is playing. */
private var lastClipMesh: ClipMesh? = null

private fun clipMeshFor(key: Any?): ArtworkMesh? =
    lastClipMesh?.takeIf { it.key == key }?.mesh

/** A session's worth of covers, at four kilobytes of texture each. */
private const val MESH_CACHE_ENTRIES = 64

/**
 * What a still sleeve is decoded at before it is averaged down. A whole
 * multiple of [MESH_GRID], so every cell is an equal number of source pixels
 * and no column comes out weighted differently from its neighbour.
 */
private const val MESH_PX = 120

/**
 * How many goes the backdrop's read gets before it is given up on, and how long
 * it waits between them.
 *
 * Matched to the cover's own retry in [NowPlayingScreen] rather than chosen
 * separately: the two read the same URL out of the same cache, so a run of
 * attempts that gave up at a different point from the cover's would be a way for
 * the backdrop and the artwork on top of it to disagree about whether this track
 * has a picture.
 */
private const val MESH_ATTEMPTS = 4

/** @see MESH_ATTEMPTS */
private const val MESH_RETRY_DELAY_MS = 1_500L

/**
 * How many cells across the mesh is.
 *
 * Coarse deliberately — see [ArtworkMesh]. Enough that a cover's layout
 * survives (where the bright part was, how much of the frame it took) and few
 * enough that nothing recognisable does.
 */
private const val MESH_GRID = 6

/**
 * How far the grid is interpolated on the CPU before the GPU stretches it.
 *
 * Bilinear filtering straight off a 6x6 texture is smooth but not *soft*: it is
 * piecewise-linear, and the eye finds the crease at every cell boundary when
 * they are a hundred and eighty pixels apart. Resampling to this first, with a
 * curve that flattens at each control point, leaves creases too small to see
 * before they are magnified. It costs about a thousand lerps, off the main
 * thread, and the control points come through untouched, so the artwork's
 * proportions are exactly what they were.
 */
private const val MESH_TEX = 32

/**
 * The mesh's blur, baked in rather than left to a `RenderEffect`: a full-screen
 * one is paid again on every frame the backdrop changes, and keeps a layer the
 * size of the screen the rest of the time. Three box passes of this radius make
 * a Gaussian of about 1.4 texels, the same softness on a phone as the 32dp live
 * blur this replaces, for the same clamped edges and so the same opaque ones.
 */
private const val MESH_BLUR_RADIUS = 1
private const val MESH_BLUR_PASSES = 3

/**
 * How long the backdrop takes to change colour, whether that is a track skip or
 * the next frame of a clip playing over the sleeve.
 *
 * Long enough to read as the screen changing colour rather than cutting, and
 * comfortably inside the interval a clip's frames arrive on — see
 * `MESH_REFRESH_MS` in `NowPlayingScreen`. It has to finish before the next one
 * lands, or every fade is cut off partway and the backdrop never settles where
 * a frame actually put it.
 */
private const val MESH_FADE_MS = 900

/**
 * The most rows and columns of [source] that are actually looked at.
 *
 * A still sleeve is decoded at [MESH_PX] and falls well under this, but a clip
 * frame is whatever size the caller grabbed it at, and every pixel of it would
 * be read to produce thirty-six averages. Sampling on a stride instead bounds
 * the work at a fixed cost per frame however large the frame is, which is what
 * makes it safe to do this several times a second.
 */
private const val MESH_SAMPLE = 128

/**
 * Averages [source] into the mesh texture, flipped top to bottom and then
 * rotated sideways — see [rotatedBelowSeam] for why the flip alone isn't the
 * finish line.
 *
 * Row 0 of the grid is the sleeve's own bottom edge and later rows run back up
 * into it, which is the order [ArtworkMeshBackdrop] draws down the screen.
 *
 * A mean per cell, not a quantised swatch: what should sit under the artwork is
 * what a blur of the artwork would leave there, and a blur has no opinion about
 * which colour in the frame was the interesting one.
 */
private fun meshOf(source: ImageBitmap, seed: Int): ArtworkMesh? {
    val width = source.width
    val height = source.height
    if (width < 1 || height < 1) return null

    // A cell nothing lands in would come out black, so the grid never asks for
    // more cells than the artwork has pixels.
    val cols = MESH_GRID.coerceAtMost(width)
    val rows = MESH_GRID.coerceAtMost(height)
    val rowStep = (height / MESH_SAMPLE).coerceAtLeast(1)
    val colStep = (width / MESH_SAMPLE).coerceAtLeast(1)

    val cells = rows * cols
    val red = LongArray(cells)
    val green = LongArray(cells)
    val blue = LongArray(cells)
    val count = IntArray(cells)

    // One row at a time, so the scratch buffer is the width of the artwork
    // rather than the whole of it. A clip frame read whole is megabytes.
    val line = IntArray(width)
    var y = 0
    while (y < height) {
        source.readPixels(line, startX = 0, startY = y, width = width, height = 1)
        // Flipped as it is read — see the note above.
        val rowBase = ((height - 1 - y) * rows / height) * cols
        var x = 0
        while (x < width) {
            val cell = rowBase + x * cols / width
            val pixel = line[x]
            red[cell] += (pixel shr 16) and 0xFF
            green[cell] += (pixel shr 8) and 0xFF
            blue[cell] += pixel and 0xFF
            count[cell]++
            x += colStep
        }
        y += rowStep
    }

    val grid = IntArray(cells) { cell ->
        val n = count[cell].coerceAtLeast(1)
        argb((red[cell] / n).toInt(), (green[cell] / n).toInt(), (blue[cell] / n).toInt()).lifted()
    }
    val texels = grid.rotatedBelowSeam(cols, rows, seed).resampled(cols, rows, MESH_TEX)
    val scratch = IntArray(texels.size)
    repeat(MESH_BLUR_PASSES) {
        boxBlurPass(texels, scratch, MESH_TEX, MESH_TEX, MESH_BLUR_RADIUS, horizontal = true)
        boxBlurPass(scratch, texels, MESH_TEX, MESH_TEX, MESH_BLUR_RADIUS, horizontal = false)
    }
    return ArtworkMesh(imageBitmapOf(texels, MESH_TEX, MESH_TEX))
}

/**
 * Every row but row 0 — the seam row [meshOf] keeps literal, for the
 * no-visible-join reason [ArtworkMeshBackdrop] documents — rotated sideways
 * by the same random amount, and mirrored left-to-right half the time.
 *
 * The flip alone reads as a mirror on a cover with any real layout to it: a
 * face, a logo, a horizon line reappears upside down in the same columns it
 * left off in, and that symmetry is what makes the backdrop look like a
 * reflection rather than a colour field. An earlier version broke that by
 * shuffling every cell below the seam independently, which also broke the
 * *mesh*: a photo's neighbouring regions are usually close in colour, and a
 * random per-cell permutation puts wildly different cells next to each other
 * far more often than the photo itself ever did, so the smooth blend between
 * them read as a patchwork of small blended islands — a mosaic — rather than
 * the two or three broad blobs a real mesh gradient has.
 *
 * A cyclic shift doesn't have that problem: every cell keeps the exact
 * neighbours it started with, just carried around the row, so whatever
 * gradient existed between them survives untouched. What moves is *where on
 * screen* that gradient sits — which is exactly enough to stop the column
 * directly under the artwork's face or logo from being the same column that
 * face or logo was in above it, without inventing a single new boundary
 * between colours that weren't adjacent in the source.
 *
 * Seeded rather than re-rolled on every call: the same artwork should land in
 * the same arrangement each time it's read, or a canvas clip refreshing this
 * once a second (see `MESH_REFRESH_MS`) would show its layout visibly turning
 * underneath its own colours instead of just changing hue.
 */
private fun IntArray.rotatedBelowSeam(cols: Int, rows: Int, seed: Int): IntArray {
    if (rows <= 1) return this
    val random = Random(seed)
    val mirror = random.nextBoolean()
    val shift = random.nextInt(cols)
    val out = copyOf()
    for (row in 1 until rows) {
        val base = row * cols
        for (x in 0 until cols) {
            val src = if (mirror) cols - 1 - x else x
            out[base + x] = this[base + (src + shift) % cols]
        }
    }
    return out
}

/**
 * Smoothly resamples a [cols] x [rows] grid of control points up to a
 * [size] x [size] texture — see [MESH_TEX] for why this happens at all.
 */
private fun IntArray.resampled(cols: Int, rows: Int, size: Int): IntArray {
    val out = IntArray(size * size)
    for (ty in 0 until size) {
        val fy = (ty + 0.5f) / size * rows - 0.5f
        val y0 = floor(fy).toInt().coerceIn(0, rows - 1)
        val y1 = (y0 + 1).coerceAtMost(rows - 1)
        val wy = smoothstep(fy - y0)
        for (tx in 0 until size) {
            val fx = (tx + 0.5f) / size * cols - 0.5f
            val x0 = floor(fx).toInt().coerceIn(0, cols - 1)
            val x1 = (x0 + 1).coerceAtMost(cols - 1)
            val wx = smoothstep(fx - x0)
            val top = lerpArgb(this[y0 * cols + x0], this[y0 * cols + x1], wx)
            val bottom = lerpArgb(this[y1 * cols + x0], this[y1 * cols + x1], wx)
            out[ty * size + tx] = lerpArgb(top, bottom, wy)
        }
    }
    return out
}

/** Flat at both ends, so no cell boundary shows up as a crease once magnified. */
private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun lerpArgb(from: Int, to: Int, t: Float): Int {
    if (t <= 0f) return from
    if (t >= 1f) return to
    fun channel(shift: Int): Int {
        val a = (from shr shift) and 0xFF
        val b = (to shr shift) and 0xFF
        return (a + ((b - a) * t)).roundToInt().coerceIn(0, 255)
    }
    return argb(channel(16), channel(8), channel(0))
}

private fun argb(red: Int, green: Int, blue: Int): Int =
    (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

/**
 * The one liberty taken with the artwork's colours.
 *
 * Saturation is nudged because averaging a block of pixels greys it — the mean
 * of a red stripe and the black around it is a dull maroon, and this puts back
 * roughly what the averaging took, not more. Lightness is only floored, off
 * pure black, which is not so much a colour choice as somewhere for the blur to
 * find an edge. Neither touches the *proportions*, which is the whole point of
 * this backdrop: a sleeve that is mostly black stays mostly black.
 */
private fun Int.lifted(): Int {
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(this, it) }
    hsl[1] = (hsl[1] * MESH_VIBRANCE).coerceAtMost(1f)
    hsl[2] = hsl[2].coerceAtLeast(MESH_FLOOR)
    return ColorUtils.HSLToColor(hsl)
}

private const val MESH_VIBRANCE = 1.12f
private const val MESH_FLOOR = 0.045f
