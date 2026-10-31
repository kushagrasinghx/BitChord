package com.music.bitchord.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.graphics.argbPixels
import com.music.bitchord.ui.graphics.forPixelAccess
import com.music.bitchord.ui.graphics.imageBitmapOf
import com.music.bitchord.ui.graphics.smootherStep
import com.music.bitchord.ui.graphics.toImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The main player's backdrop: the cover runs on down the screen and melts, with
 * blur growing as it goes, into a mesh made of the cover's own colours.
 *
 * There is no live blur anywhere in this. [bakeArtworkMelt] runs once per cover
 * off the main thread and leaves one small bitmap, so the draw is a single
 * stretched image and an idle frame costs the same as any other picture.
 *
 * Between that bitmap and the sharp artwork sits [strip]: the lightest stops of
 * the blur, which need more resolution than the backdrop has. Without them the
 * art would dissolve straight into a medium blur, and leaves or any other fine
 * detail show that as an edge. See [eraseMeltFade].
 */
@Stable
class ArtworkMelt {
    internal val backdrop = mutableStateOf<ImageBitmap?>(null)
    internal val strip = mutableStateOf<ImageBitmap?>(null)
    private val readyUrl = mutableStateOf<String?>(null)

    /** Read from draw and layout blocks, so a new frame reaches only them. */
    val ready: Boolean get() = backdrop.value != null

    /** Whether what is held is for this cover, rather than the last one still waiting to be replaced. */
    fun isReadyFor(url: String?): Boolean = readyUrl.value == url

    internal fun show(url: String?, backdrop: ImageBitmap, strip: ImageBitmap?) {
        this.backdrop.value = backdrop
        this.strip.value = strip
        readyUrl.value = url
    }

    /** A clip's next frame: only the backdrop follows it. */
    internal fun showFrame(url: String?, backdrop: ImageBitmap) {
        this.backdrop.value = backdrop
        readyUrl.value = url
    }
}

private class BakedMelt(val backdrop: ImageBitmap, val strip: ImageBitmap?)

/**
 * The [ArtworkMelt] for a cover, updated as covers and clip frames are baked.
 */
@Composable
fun rememberArtworkMeltImage(
    imageUrl: String?,
    artPx: Int,
    /** The player's size in pixels. */
    playerWidth: Int,
    playerHeight: Int,
    /** How far down the player the artwork stops: the banner, or a taller clip's own foot. */
    artBottom: Int,
    /** The banner's height, which the melt's lengths are measured in whatever the art's shape. */
    bannerHeight: Int,
    /**
     * A frame off a playing clip, which takes over from the still sleeve as it
     * arrives. A function so that a new one reaches this alone: as a value it
     * would recompose the caller's whole screen every time one was grabbed.
     */
    clipFrame: () -> ImageBitmap?,
    /** The sleeve is showing the still cover rather than a clip, at [artBottom]'s height. */
    stillArt: Boolean,
    prepare: Boolean,
): ArtworkMelt {
    val context = LocalPlatformContext.current
    val outHeight = if (playerWidth > 0) MELT_WIDTH * playerHeight / playerWidth else 0
    val outBottom = if (playerWidth > 0) MELT_WIDTH * artBottom / playerWidth else 0
    val outBanner = if (playerWidth > 0) MELT_WIDTH * bannerHeight / playerWidth else 0
    val key = "$imageUrl|$outHeight|$outBottom|$outBanner|$stillArt"
    // The last cover's melt is held until this one is ready, so a skip fades
    // from one to the other rather than through whatever is underneath.
    val melt = remember { ArtworkMelt() }
    // In composition rather than the effect below: a cover seen before should be
    // whole in its first frame, with no frame of black ahead of it.
    remember(key) { meltCache[key]?.let { melt.show(imageUrl, it.backdrop, it.strip) } }

    LaunchedEffect(key, prepare) {
        if (!prepare || imageUrl == null || outHeight <= 0 || outBottom <= 0 || outBanner <= 0) {
            return@LaunchedEffect
        }
        fun bake(pixels: IntArray, width: Int, height: Int): ImageBitmap {
            val out = bakeArtworkMelt(
                art = pixels,
                artWidth = width,
                artHeight = height,
                outWidth = MELT_WIDTH,
                outHeight = outHeight,
                bannerHeight = outBottom.toFloat(),
                seed = imageUrl.hashCode(),
                fadeUnit = outBanner.toFloat(),
                // A still's melt answers to the credits; a clip's to its own foot.
                meltAnchor = if (stillArt) outBanner.toFloat() else outBottom.toFloat(),
            )
            // Uploaded here, so the frame that first shows it doesn't pay.
            return imageBitmapOf(out, MELT_WIDTH, outHeight).also { it.prepareToDraw() }
        }

        // A clip's frame already in hand beats reading a still it would replace.
        if (meltCache[key] == null && clipFrame() == null) {
            val request = ImageRequest.Builder(context)
                .data(imageUrl.artworkAt(artPx))
                .size(MELT_STRIP_WIDTH)
                .forPixelAccess()
                .build()
            // Keyed on the cover alone, so a dropped read has nothing else to
            // re-trigger it; the backdrop's retry is matched to the sleeve's own.
            for (attempt in 0 until MELT_ATTEMPTS) {
                if (attempt > 0) delay(MELT_RETRY_DELAY_MS)
                val result = SingletonImageLoader.get(context).execute(request)
                val art = (result as? SuccessResult)?.image?.toImageBitmap() ?: continue
                val baked = withContext(Dispatchers.Default) {
                    val pixels = art.argbPixels()
                    // A clip's own picture isn't this cover, so it has no use for one.
                    val strip = if (stillArt) {
                        val stripBanner = MELT_STRIP_WIDTH.toFloat() * bannerHeight / playerWidth
                        val stripArt = MELT_STRIP_WIDTH.toFloat() * artBottom / playerWidth
                        val rows = meltStripRows(stripBanner)
                        val out = bakeMeltStrip(
                            pixels, art.width, art.height, MELT_STRIP_WIDTH, stripBanner, stripArt,
                        )
                        imageBitmapOf(out, MELT_STRIP_WIDTH, rows).also { it.prepareToDraw() }
                    } else null
                    // Twice what the backdrop can show, so it starts from a clean half.
                    val backdrop = if (art.width >= 2 * MELT_WIDTH && art.height >= 2) {
                        bake(halved(pixels, art.width, art.height), art.width / 2, art.height / 2)
                    } else {
                        bake(pixels, art.width, art.height)
                    }
                    BakedMelt(backdrop, strip)
                }
                meltCache[key] = baked
                melt.show(imageUrl, baked.backdrop, baked.strip)
                break
            }
        }

        // Clip frames never land in the cache: the next one is a different picture.
        var lastColors: IntArray? = null
        snapshotFlow(clipFrame).filterNotNull().collect { frame ->
            val colors = withContext(Dispatchers.Default) {
                // The first read after a surface comes back is a black buffer, not the clip.
                if (isLikelyBlackFrame(frame)) null else coarseColors(frame)
            } ?: return@collect
            // A clip that isn't changing colour has nothing new to bake.
            if (lastColors?.let { sameColors(it, colors) } == true) return@collect
            lastColors = colors
            val baked = withContext(Dispatchers.Default) {
                bake(frame.argbPixels(), frame.width, frame.height)
            }
            melt.showFrame(imageUrl, baked)
        }
    }
    return melt
}

/**
 * A new cover crossfades over the last, with the strip on top of it. Reads nothing
 * that moves, so it never redraws by itself.
 */
@Composable
fun ArtworkMeltBackdrop(
    melt: ArtworkMelt,
    /** The banner's height in pixels, which the strip is placed against. */
    bannerHeight: () -> Float,
    /** Off under a clip, whose picture the still cover's strip isn't. */
    showStrip: Boolean,
    modifier: Modifier = Modifier,
    /**
     * Long enough on a clip, whose frames come every few seconds, to read as colour
     * drifting. None on a still: a new cover comes in with its art, from black.
     */
    fadeMillis: Int = 0,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Crossfade(
            targetState = melt.backdrop.value,
            animationSpec = tween(fadeMillis, easing = FastOutSlowInEasing),
            modifier = Modifier.fillMaxSize(),
            label = "artworkMelt",
        ) { bitmap ->
            if (bitmap != null) {
                val painter = remember(bitmap) { BitmapPainter(bitmap) }
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (showStrip) {
            Canvas(Modifier.fillMaxSize()) {
                val strip = melt.strip.value ?: return@Canvas
                val banner = bannerHeight()
                val top = (banner * MELT_STRIP_TOP).roundToInt()
                drawImage(
                    image = strip,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(strip.width, strip.height),
                    dstOffset = IntOffset(0, top),
                    dstSize = IntSize(size.width.roundToInt(), (banner * MELT_STRIP_BOTTOM).roundToInt() - top),
                    filterQuality = FilterQuality.Low,
                )
            }
        }
    }
}

/** An 8x8 of mean colours, which is all a frame has to offer a blurred backdrop. */
private fun coarseColors(frame: ImageBitmap): IntArray {
    val pixels = frame.argbPixels()
    val width = frame.width
    val height = frame.height
    val sums = IntArray(COARSE_CELLS * COARSE_CELLS * 4)
    for (y in 0 until height) {
        val row = y * COARSE_CELLS / height * COARSE_CELLS
        for (x in 0 until width) {
            val cell = (row + x * COARSE_CELLS / width) * 4
            val pixel = pixels[y * width + x]
            sums[cell] += (pixel shr 16) and 0xFF
            sums[cell + 1] += (pixel shr 8) and 0xFF
            sums[cell + 2] += pixel and 0xFF
            sums[cell + 3]++
        }
    }
    return IntArray(COARSE_CELLS * COARSE_CELLS * 3) {
        sums[it / 3 * 4 + it % 3] / sums[it / 3 * 4 + 3].coerceAtLeast(1)
    }
}

private fun sameColors(a: IntArray, b: IntArray): Boolean {
    for (i in a.indices) if (abs(a[i] - b[i]) > SAME_COLOR_LEVELS) return false
    return true
}

/** Every 2x2 of [art] averaged into one pixel. */
private fun halved(art: IntArray, width: Int, height: Int): IntArray {
    val outWidth = width / 2
    val outHeight = height / 2
    val out = IntArray(outWidth * outHeight)
    for (y in 0 until outHeight) {
        var i = y * 2 * width
        val to = y * outWidth
        for (x in 0 until outWidth) {
            val a = art[i]
            val b = art[i + 1]
            val c = art[i + width]
            val d = art[i + width + 1]
            out[to + x] = (0xFF shl 24) or
                ((((a shr 16 and 0xFF) + (b shr 16 and 0xFF) + (c shr 16 and 0xFF) + (d shr 16 and 0xFF)) / 4) shl 16) or
                ((((a shr 8 and 0xFF) + (b shr 8 and 0xFF) + (c shr 8 and 0xFF) + (d shr 8 and 0xFF)) / 4) shl 8) or
                (((a and 0xFF) + (b and 0xFF) + (c and 0xFF) + (d and 0xFF)) / 4)
            i += 2
        }
    }
    return out
}

private const val COARSE_CELLS = 8
private const val SAME_COLOR_LEVELS = 3

/**
 * Dissolves the banner's foot in the melt's shape: level across the middle,
 * lifting only at the left and right edges. [strength] 0 leaves the banner whole.
 *
 * One small shared texture rather than a gradient, because a vertical gradient
 * cannot bend, and because a single sample is cheaper than a many-stop shader.
 */
internal fun DrawScope.eraseMeltFade(strength: Float, bannerShare: Float) {
    val banner = size.height * bannerShare
    val width = size.width.roundToInt()
    val top = (banner * MELT_SIDE_START).roundToInt()
    val bottom = (banner * MELT_MASK_END).roundToInt()
    drawImage(
        image = meltFadeMask,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(meltFadeMask.width, meltFadeMask.height),
        dstOffset = IntOffset(0, top),
        dstSize = IntSize(width, bottom - top),
        alpha = strength,
        blendMode = BlendMode.DstOut,
        filterQuality = FilterQuality.Low,
    )
    // All of what the mask's foot leaves below it.
    drawRect(
        color = Color.Black,
        topLeft = Offset(0f, bottom.toFloat()),
        size = Size(size.width, size.height - bottom),
        alpha = strength,
        blendMode = BlendMode.DstOut,
    )
}

private val meltFadeMask: ImageBitmap by lazy {
    val width = 64
    val height = 48
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val row = MELT_SIDE_START + (MELT_MASK_END - MELT_SIDE_START) * (y + 0.5f) / height
        for (x in 0 until width) {
            // Smoothstep rather than the steeper smootherstep: this fade over
            // fine detail is the one the eye tracks, so it spreads over all its band.
            val t = ((row - meltStart((x + 0.5f) / width)) / MELT_SHARP_BAND).coerceIn(0f, 1f)
            val shown = t * t * (3f - 2f * t)
            pixels[y * width + x] = ((shown * 255f).roundToInt() shl 24) or 0xFFFFFF
        }
    }
    imageBitmapOf(pixels, width, height)
}

/** Where the melt begins as a share of the banner's height, [across] the width in 0..1. */
private fun meltStart(across: Float): Float {
    val t = abs(2f * across - 1f)
    // t^3.5: flat for most of the width, lifting only at the edges.
    return MELT_CENTER_START - (MELT_CENTER_START - MELT_SIDE_START) * t * t * t * sqrt(t)
}

/** A few recent covers, for skipping back; each entry is well under a megabyte. */
private val meltCache = object : LinkedHashMap<String, BakedMelt>(0, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, BakedMelt>) = size > 6
}

/** A clip frame's longest edge. Every read stalls the main thread, so no more than the melt can use. */
internal const val MELT_FRAME_CAPTURE_PX = 160

/**
 * A square clip dissolves straight across, as a share of its height. Longer than
 * a still's, because a frame this small is not enough to bake a strip from.
 */
internal const val MELT_CLIP_FADE_START = 0.86f
internal const val MELT_CLIP_FADE_END = 1f
internal const val MELT_CLIP_FADE = (MELT_CLIP_FADE_END - MELT_CLIP_FADE_START) / MELT_CLIP_FADE_END

/** How long a clip's next frame takes to drift in. */
internal const val MELT_CLIP_CROSSFADE_MS = 900

/** The baked width. Everything left at this scale is blurred past what the stretch can show. */
internal const val MELT_WIDTH = 216
private const val MELT_ATTEMPTS = 4
private const val MELT_RETRY_DELAY_MS = 1_500L

/**
 * A still cover is shown this tall against the player's width, so there is real
 * artwork under the credits for the melt to run through.
 */
internal const val MELT_STILL_ART_TALLNESS = 1.2f

/**
 * All as shares of the banner's height, which is where the credits start. The
 * sharp art leaves first, over the strip's lightest blur, and the strip hands on
 * to the backdrop's heavier blur around where the credits start. Every layer dims along
 * one curve, because a bright cover over an already dimmed layer draws a line
 * however long the fade between them.
 */
private const val MELT_CENTER_START = 0.88f
private const val MELT_SIDE_START = 0.84f
private const val MELT_SHARP_BAND = 0.14f
private const val MELT_MASK_END = MELT_CENTER_START + MELT_SHARP_BAND

/** Short enough that a cover's lettering doesn't read behind the credits, long enough to still melt. */
private const val MELT_BLUR_RUN = 0.36f
private const val MELT_DIM_RUN = 0.30f

/**
 * Slow on purpose, and well behind the blur: the art is a plain wash of its own
 * colours before the mesh has much say, so one turns into the other across most
 * of the screen, whatever colour the cover happened to end on.
 */
private const val MELT_MESH_DELAY = 0.22f
private const val MELT_MESH_RUN = 0.90f

/** Where the strip moves between its three stops, and where it runs out, below the start. */
private const val STRIP_MIX_START = 0.015f
private const val STRIP_MIX_RUN = 0.05f
private const val STRIP_FADE_START = 0.09f
private const val STRIP_FADE_RUN = 0.12f

/** Which part of the banner the strip covers, top and bottom. */
private const val MELT_STRIP_TOP = MELT_SIDE_START
private const val MELT_STRIP_BOTTOM = MELT_CENTER_START + STRIP_FADE_START + STRIP_FADE_RUN

/** The strip's width, and so what a still cover is decoded at. */
private const val MELT_STRIP_WIDTH = 432

/** How bright the mesh may be, in OKLab lightness; white controls stand on it. */
private const val MESH_L_MIN = 0.20f
private const val MESH_L_MAX = 0.64f

/** The same limits for the melting art, as gamma luma: `L ≈ luma^0.733` and is cheap to solve. */
private const val MELT_LUMA_MIN = 0.0822f * 255f
private const val MELT_LUMA_MAX = 0.544f * 255f

/** How far highlights come down at [dim] along the melt's one dimming curve. */
private fun meltDimScale(luma: Float, dim: Float): Float =
    if (luma > MELT_LUMA_MAX) 1f + dim * (MELT_LUMA_MAX / luma - 1f) else 1f

/** How far shadows come up at the same point. */
private fun meltDimLift(luma: Float, dim: Float): Float =
    if (luma < MELT_LUMA_MIN) dim * (MELT_LUMA_MIN - luma) else 0f

private fun meltStripRows(banner: Float): Int =
    (banner * (MELT_STRIP_BOTTOM - MELT_STRIP_TOP)).roundToInt().coerceAtLeast(2)

/**
 * The lightest stops of the blur as [outWidth] wide packed ARGB, for the stretch
 * of a [banner]-tall banner from [MELT_STRIP_TOP] to [MELT_STRIP_BOTTOM]. Its
 * alpha runs out where the backdrop's own blur has caught up with it.
 */
internal fun bakeMeltStrip(
    art: IntArray,
    artWidth: Int,
    artHeight: Int,
    outWidth: Int,
    banner: Float,
    artBottom: Float = banner,
): IntArray {
    val rows = meltStripRows(banner)
    val firstRow = (banner * MELT_STRIP_TOP).roundToInt()
    val out = IntArray(outWidth * rows)
    if (artWidth < 2 || artHeight < 2) return out
    val light = Plane(outWidth, rows, 1)
    fillCropped(light, art, artWidth, artHeight, firstRow, artBottom)
    light.blur(radiusX = 1, radiusY = 2, passes = 2)
    val medium = light.copy()
    medium.blur(radiusX = 2, radiusY = 3, passes = 2)
    val heavy = medium.copy()
    heavy.blur(radiusX = 4, radiusY = 5, passes = 2)

    val mixPerRow = 1f / (banner * STRIP_MIX_RUN)
    val mixStart = banner * STRIP_MIX_START
    val fadeStart = banner * STRIP_FADE_START
    val fadeRun = banner * STRIP_FADE_RUN
    val dimRun = banner * MELT_DIM_RUN
    val starts = FloatArray(outWidth) { banner * meltStart((it + 0.5f) / outWidth) }
    for (y in 0 until rows) {
        val at = y * outWidth
        val row = (firstRow + y).toFloat()
        for (x in 0 until outWidth) {
            val below = row - starts[x]
            val toMedium = smootherStep((below - mixStart) * mixPerRow)
            val toHeavy = smootherStep((below - mixStart) * mixPerRow - 1f)
            val alpha = 1f - smootherStep((below - fadeStart) / fadeRun)
            val i = at + x
            var r = light.r[i] + (medium.r[i] - light.r[i]) * toMedium
            var g = light.g[i] + (medium.g[i] - light.g[i]) * toMedium
            var b = light.b[i] + (medium.b[i] - light.b[i]) * toMedium
            r += (heavy.r[i] - r) * toHeavy
            g += (heavy.g[i] - g) * toHeavy
            b += (heavy.b[i] - b) * toHeavy
            val dim = smootherStep(below / dimRun)
            val luma = 0.25f * r + 0.68f * g + 0.07f * b
            val scale = meltDimScale(luma, dim)
            val lift = meltDimLift(luma, dim)
            r = r * scale + lift
            g = g * scale + lift
            b = b * scale + lift
            out[i] = ((alpha * 255f).toInt() shl 24) or
                (r.toInt().coerceIn(0, 255) shl 16) or
                (g.toInt().coerceIn(0, 255) shl 8) or
                b.toInt().coerceIn(0, 255)
        }
    }
    return out
}

/**
 * [outWidth] x [outHeight] packed ARGB for [art]: mesh everywhere, with the art's
 * melt over it from a little above [meltAnchor]. [bannerHeight] is where the art
 * stops. Lengths are in [fadeUnit], so art of any height melts over the same
 * distance. Pure, so it can be run and timed away from a device. Rows the
 * sharp art always covers are not computed.
 */
internal fun bakeArtworkMelt(
    art: IntArray,
    artWidth: Int,
    artHeight: Int,
    outWidth: Int,
    outHeight: Int,
    bannerHeight: Float,
    seed: Int,
    fadeUnit: Float = bannerHeight,
    meltAnchor: Float = bannerHeight,
): IntArray {
    // The samplers below read a pixel's neighbour, which a one-pixel cover hasn't got.
    if (artWidth < 2 || artHeight < 2) {
        return bakeArtworkMelt(IntArray(4) { art[0] }, 2, 2, outWidth, outHeight, bannerHeight, seed, fadeUnit, meltAnchor)
    }
    val out = IntArray(outWidth * outHeight)
    val gridHeight = (MESH_GRID_WIDTH * outHeight / outWidth).coerceAtLeast(2)
    val grid = meshGrid(art, artWidth, artHeight, gridHeight, outHeight.toFloat() / outWidth, seed)

    // Half way up the banner is as high as a clip's own, longer fade can reveal.
    val firstRow = (meltAnchor - fadeUnit * 0.5f).toInt().coerceIn(0, outHeight - 1)
    val lastMeltRow = (meltAnchor + fadeUnit * (MELT_CENTER_START - 1f + MELT_MESH_DELAY + MELT_MESH_RUN))
        .toInt().coerceIn(firstRow, outHeight - 1)
    val pyramid = meltPyramid(art, artWidth, artHeight, outWidth, firstRow, lastMeltRow, bannerHeight)

    val starts = FloatArray(outWidth) { meltAnchor - fadeUnit * (1f - meltStart((it + 0.5f) / outWidth)) }
    val curves = MeltCurves(fadeUnit)
    val cellX = IntArray(outWidth)
    val weightX = FloatArray(outWidth)
    for (x in 0 until outWidth) {
        val at = ((x + 0.5f) / outWidth * MESH_GRID_WIDTH - 0.5f).coerceIn(0f, MESH_GRID_WIDTH - 1.001f)
        cellX[x] = at.toInt()
        weightX[x] = at - cellX[x]
    }
    val meshRow = FloatArray(MESH_GRID_WIDTH * 3)
    val sample = FloatArray(3)
    var noise = seed or 1

    for (y in firstRow until outHeight) {
        val rowAt = ((y + 0.5f) / outHeight * gridHeight - 0.5f).coerceIn(0f, gridHeight - 1.001f)
        val row0 = rowAt.toInt()
        val rowT = rowAt - row0
        for (i in meshRow.indices) {
            val a = grid[row0 * MESH_GRID_WIDTH * 3 + i]
            meshRow[i] = a + (grid[(row0 + 1) * MESH_GRID_WIDTH * 3 + i] - a) * rowT
        }
        val base = y * outWidth
        for (x in 0 until outWidth) {
            val c = cellX[x] * 3
            val t = weightX[x]
            var r = meshRow[c] + (meshRow[c + 3] - meshRow[c]) * t
            var g = meshRow[c + 1] + (meshRow[c + 4] - meshRow[c + 1]) * t
            var b = meshRow[c + 2] + (meshRow[c + 5] - meshRow[c + 2]) * t

            val curve = curves.indexOf(y - starts[x])
            val artShare = curves.art[curve]
            if (y <= lastMeltRow && artShare > 0f) {
                pyramid.sample(x, y - firstRow, curves.level[curve], sample)
                var mr = sample[0]
                var mg = sample[1]
                var mb = sample[2]
                // Whites and blacks settle into the mesh's range as they blur,
                // so what the text stands on is calm whatever the cover did.
                val dim = curves.dim[curve]
                if (dim > 0f) {
                    val luma = 0.25f * mr + 0.68f * mg + 0.07f * mb
                    val scale = meltDimScale(luma, dim)
                    val lift = meltDimLift(luma, dim)
                    mr = mr * scale + lift
                    mg = mg * scale + lift
                    mb = mb * scale + lift
                }
                r += (mr - r) * artShare
                g += (mg - g) * artShare
                b += (mb - b) * artShare
            }

            // Triangular dither. These gradients are shallow and get stretched
            // five times over, and 8-bit steps would otherwise show as contours.
            noise = noise xor (noise shl 13)
            noise = noise xor (noise ushr 17)
            noise = noise xor (noise shl 5)
            val dither = ((noise and 0xFF) + ((noise ushr 8) and 0xFF) - 255) / 255f + 0.5f
            out[base + x] = (0xFF shl 24) or
                ((r + dither).toInt().coerceIn(0, 255) shl 16) or
                ((g + dither).toInt().coerceIn(0, 255) shl 8) or
                (b + dither).toInt().coerceIn(0, 255)
        }
    }
    // Always behind the sharp art; only here so the bitmap has no hole.
    for (y in 0 until firstRow) System.arraycopy(out, firstRow * outWidth, out, y * outWidth, outWidth)
    return out
}

/** The melt's curves against distance below where it starts, looked up rather than solved per pixel. */
private class MeltCurves(banner: Float) {
    private val stepsPerRow = CURVE_STEPS / (banner * (MELT_MESH_DELAY + MELT_MESH_RUN))
    val level = FloatArray(CURVE_STEPS + 1)
    val dim = FloatArray(CURVE_STEPS + 1)
    val art = FloatArray(CURVE_STEPS + 1)

    init {
        for (i in 0..CURVE_STEPS) {
            val below = i / stepsPerRow
            // Sharp art covers the first two of the five blur stops, so the
            // bitmap only has to hold from the third on.
            val depth = (below / (banner * MELT_BLUR_RUN)).coerceIn(0f, 1f)
            level[i] = (depth.pow(0.8f) * 5f - 2f).coerceIn(0f, PYRAMID_LEVELS - 1f)
            dim[i] = smootherStep(below / (banner * MELT_DIM_RUN))
            art[i] = 1f - smootherStep((below - banner * MELT_MESH_DELAY) / (banner * MELT_MESH_RUN))
        }
    }

    fun indexOf(below: Float): Int =
        if (below <= 0f) 0 else (below * stepsPerRow).toInt().coerceAtMost(CURVE_STEPS)
}

private const val CURVE_STEPS = 256
private const val PYRAMID_LEVELS = 4

/**
 * The art, cropped as the banner crops it and with its last row held on down,
 * at four strengths of blur, each at half the scale of the last. Blurs an eighth
 * of the pixels for the widest stop, which is what keeps this cheap: at the full
 * scale the same radius would be a hundred times the work.
 */
private class MeltPyramid(val levels: Array<Plane>) {
    /** [level] between stops; both neighbours are read only where it isn't exact. */
    fun sample(x: Int, y: Int, level: Float, out: FloatArray) {
        val low = level.toInt()
        val t = level - low
        levels[low].sample(x, y, out)
        if (t > 0f && low + 1 < levels.size) {
            val r = out[0]
            val g = out[1]
            val b = out[2]
            levels[low + 1].sample(x, y, out)
            out[0] = r + (out[0] - r) * t
            out[1] = g + (out[1] - g) * t
            out[2] = b + (out[2] - b) * t
        }
    }
}

private class Plane(val width: Int, val height: Int, val scale: Int) {
    val r = FloatArray(width * height)
    val g = FloatArray(width * height)
    val b = FloatArray(width * height)

    fun sample(x: Int, y: Int, out: FloatArray) {
        val u = ((x + 0.5f) / scale - 0.5f).coerceIn(0f, width - 1.001f)
        val v = ((y + 0.5f) / scale - 0.5f).coerceIn(0f, height - 1.001f)
        val x0 = u.toInt()
        val y0 = v.toInt()
        val tx = u - x0
        val ty = v - y0
        val i = y0 * width + x0
        val j = i + width
        val weight00 = (1f - tx) * (1f - ty)
        val weight10 = tx * (1f - ty)
        val weight01 = (1f - tx) * ty
        val weight11 = tx * ty
        out[0] = r[i] * weight00 + r[i + 1] * weight10 + r[j] * weight01 + r[j + 1] * weight11
        out[1] = g[i] * weight00 + g[i + 1] * weight10 + g[j] * weight01 + g[j + 1] * weight11
        out[2] = b[i] * weight00 + b[i + 1] * weight10 + b[j] * weight01 + b[j + 1] * weight11
    }

    fun copy(): Plane {
        val next = Plane(width, height, scale)
        r.copyInto(next.r)
        g.copyInto(next.g)
        b.copyInto(next.b)
        return next
    }

    fun blur(radiusX: Int, radiusY: Int, passes: Int) {
        val scratch = FloatArray(maxOf(width, height))
        for (channel in arrayOf(r, g, b)) {
            repeat(passes) {
                for (y in 0 until height) boxLine(channel, y * width, 1, width, radiusX, scratch)
                for (x in 0 until width) boxLine(channel, x, width, height, radiusY, scratch)
            }
        }
    }

    fun halved(): Plane {
        val next = Plane(maxOf(width / 2, 2), maxOf(height / 2, 2), scale * 2)
        for (y in 0 until next.height) {
            val y0 = (y * 2).coerceAtMost(height - 1)
            val y1 = (y0 + 1).coerceAtMost(height - 1)
            for (x in 0 until next.width) {
                val x0 = (x * 2).coerceAtMost(width - 1)
                val x1 = (x0 + 1).coerceAtMost(width - 1)
                val a = y0 * width + x0
                val c = y0 * width + x1
                val d = y1 * width + x0
                val e = y1 * width + x1
                val to = y * next.width + x
                next.r[to] = (r[a] + r[c] + r[d] + r[e]) * 0.25f
                next.g[to] = (g[a] + g[c] + g[d] + g[e]) * 0.25f
                next.b[to] = (b[a] + b[c] + b[d] + b[e]) * 0.25f
            }
        }
        return next
    }
}

/** A running-sum box along one line of [data], with its ends held. Cost doesn't grow with [radius]. */
private fun boxLine(data: FloatArray, start: Int, stride: Int, length: Int, radius: Int, scratch: FloatArray) {
    for (i in 0 until length) scratch[i] = data[start + i * stride]
    var sum = scratch[0] * (radius + 1)
    for (i in 1..radius) sum += scratch[minOf(i, length - 1)]
    val scale = 1f / (2 * radius + 1)
    var to = start
    for (i in 0 until length) {
        data[to] = sum * scale
        sum += scratch[minOf(i + radius + 1, length - 1)] - scratch[maxOf(i - radius, 0)]
        to += stride
    }
}

/** Fills [plane] from [firstRow] of the banner down, with the art's last row held past its foot. */
private fun fillCropped(plane: Plane, art: IntArray, artWidth: Int, artHeight: Int, firstRow: Int, banner: Float) {
    // ContentScale.Crop into the banner, in this bitmap's pixels.
    val scale = maxOf(plane.width.toFloat() / artWidth, banner / artHeight)
    val cropX = (artWidth - plane.width / scale) / 2f
    val cropY = (artHeight - banner / scale) / 2f
    val lastSrcY = artHeight - 1.001f
    val lastSrcX = artWidth - 1.001f
    val bannerRow = banner - 0.5f
    for (y in 0 until plane.height) {
        val row = minOf(firstRow + y.toFloat(), bannerRow)
        val v = ((row + 0.5f) / scale + cropY - 0.5f).coerceIn(0f, lastSrcY)
        val v0 = v.toInt()
        val tv = v - v0
        for (x in 0 until plane.width) {
            val u = ((x + 0.5f) / scale + cropX - 0.5f).coerceIn(0f, lastSrcX)
            val u0 = u.toInt()
            val tu = u - u0
            val i = v0 * artWidth + u0
            val a = art[i]
            val c = art[i + 1]
            val d = art[i + artWidth]
            val e = art[i + artWidth + 1]
            val w00 = (1f - tu) * (1f - tv)
            val w10 = tu * (1f - tv)
            val w01 = (1f - tu) * tv
            val w11 = tu * tv
            val to = y * plane.width + x
            plane.r[to] = ((a ushr 16) and 0xFF) * w00 + ((c ushr 16) and 0xFF) * w10 +
                ((d ushr 16) and 0xFF) * w01 + ((e ushr 16) and 0xFF) * w11
            plane.g[to] = ((a ushr 8) and 0xFF) * w00 + ((c ushr 8) and 0xFF) * w10 +
                ((d ushr 8) and 0xFF) * w01 + ((e ushr 8) and 0xFF) * w11
            plane.b[to] = (a and 0xFF) * w00 + (c and 0xFF) * w10 + (d and 0xFF) * w01 + (e and 0xFF) * w11
        }
    }
}

private fun meltPyramid(
    art: IntArray,
    artWidth: Int,
    artHeight: Int,
    outWidth: Int,
    firstRow: Int,
    lastRow: Int,
    banner: Float,
): MeltPyramid {
    val top = Plane(outWidth, lastRow - firstRow + 1, 1)
    fillCropped(top, art, artWidth, artHeight, firstRow, banner)
    // Taller than wide throughout, so colour drips down instead of fogging over.
    top.blur(radiusX = 2, radiusY = 3, passes = 2)
    val second = top.halved().also { it.blur(2, 3, 2) }
    val third = second.halved().also { it.blur(3, 4, 2) }
    val fourth = third.halved().also { it.blur(3, 4, 2) }
    return MeltPyramid(arrayOf(top, second, third, fourth))
}

/**
 * The mesh, as sRGB floats on a grid coarse enough that computing it per cell is
 * nothing: it is blurred far wider than a cell, so bilinear loses none of it.
 *
 * Blobs of the cover's own colours at seeded random spots, so the same cover
 * always lands the same way and nothing is laid out like the cover above it.
 */
private fun meshGrid(
    art: IntArray,
    artWidth: Int,
    artHeight: Int,
    gridHeight: Int,
    tallness: Float,
    seed: Int,
): FloatArray {
    val palette = coverPalette(art, artWidth, artHeight)
    val colorCount = palette.size / 4
    val random = Random(seed)

    val blobCount = BLOB_COLUMNS * BLOB_ROWS
    val blobX = FloatArray(blobCount)
    val blobY = FloatArray(blobCount)
    val blobFalloff = FloatArray(blobCount)
    val picks = blobColors(palette, blobCount, random)
    val spread = tallness - BLOB_TOP
    for (i in 0 until blobCount) {
        val column = i % BLOB_COLUMNS
        val row = i / BLOB_COLUMNS
        blobX[i] = (column + 0.1f + random.nextFloat() * 0.8f) / BLOB_COLUMNS
        blobY[i] = BLOB_TOP + (row + 0.1f + random.nextFloat() * 0.8f) / BLOB_ROWS * spread
        val radius = 0.15f + random.nextFloat() * 0.12f
        blobFalloff[i] = 0.75f / (radius * radius)
    }
    val phaseA = random.nextFloat() * 6.28f
    val phaseB = random.nextFloat() * 6.28f
    val phaseC = random.nextFloat() * 6.28f
    val phaseD = random.nextFloat() * 6.28f

    val cells = MESH_GRID_WIDTH * gridHeight
    val labL = FloatArray(cells)
    val labA = FloatArray(cells)
    val labB = FloatArray(cells)
    for (gy in 0 until gridHeight) {
        val y0 = (gy + 0.5f) / gridHeight * tallness
        for (gx in 0 until MESH_GRID_WIDTH) {
            val x0 = (gx + 0.5f) / MESH_GRID_WIDTH
            // Bends the whole field, so blobs read as flows and not as circles.
            val x = x0 + 0.2f * (sin(3.1f * x0 + 2.3f * y0 + phaseA) + sin(-1.7f * x0 + 3.9f * y0 + phaseB))
            val y = y0 + 0.2f * (sin(2.7f * x0 - 3.3f * y0 + phaseC) + sin(4.1f * x0 + 1.9f * y0 + phaseD))
            var sumL = 0f
            var sumA = 0f
            var sumB = 0f
            var sumChroma = 0f
            var weight = 0f
            for (i in 0 until blobCount) {
                val dx = x - blobX[i]
                val dy = y - blobY[i]
                val g = exp(-(dx * dx + dy * dy) * blobFalloff[i])
                val c = picks[i] * 4
                val a = palette[c + 1]
                val b = palette[c + 2]
                sumL += g * palette[c]
                sumA += g * a
                sumB += g * b
                sumChroma += g * hypot(a, b)
                weight += g
            }
            weight = weight.coerceAtLeast(1e-6f)
            val a = sumA / weight
            val b = sumB / weight
            // Averaging opposite hues goes through grey; most of the chroma of
            // what is being mixed goes back, not all, or the hue would flip
            // hard enough to draw a line where they cross.
            val restore = 1f + 0.6f * (minOf(sumChroma / weight / hypot(a, b).coerceAtLeast(1e-4f), 1.8f) - 1f)
            val cell = gy * MESH_GRID_WIDTH + gx
            labL[cell] = sumL / weight
            labA[cell] = a * restore
            labB[cell] = b * restore
        }
    }
    val scratch = FloatArray(maxOf(MESH_GRID_WIDTH, gridHeight))
    for (channel in arrayOf(labL, labA, labB)) {
        repeat(3) {
            for (y in 0 until gridHeight) boxLine(channel, y * MESH_GRID_WIDTH, 1, MESH_GRID_WIDTH, 3, scratch)
            for (x in 0 until MESH_GRID_WIDTH) boxLine(channel, x, MESH_GRID_WIDTH, gridHeight, 3, scratch)
        }
    }
    val grid = FloatArray(cells * 3)
    for (cell in 0 until cells) oklabToSrgb(labL[cell], labA[cell], labB[cell], grid, cell * 3)
    return grid
}

private const val MESH_GRID_WIDTH = 28
private const val BLOB_COLUMNS = 3
private const val BLOB_ROWS = 4
private const val BLOB_TOP = 0.72f

/**
 * Which palette colour each blob gets: as near as a whole number of blobs can be
 * to the share of the cover that colour has, at seeded random places. A cover
 * that is mostly a white building is mostly that behind the controls, and a bit
 * of foliage gets a blob's worth or none.
 */
private fun blobColors(palette: FloatArray, blobCount: Int, random: Random): IntArray {
    val colorCount = palette.size / 4
    val exact = FloatArray(colorCount) { palette[it * 4 + 3] * blobCount }
    val counts = IntArray(colorCount) { exact[it].toInt() }
    var left = blobCount - counts.sum()
    while (left > 0) {
        var best = 0
        for (c in 1 until colorCount) if (exact[c] - counts[c] > exact[best] - counts[best]) best = c
        counts[best]++
        left--
    }
    val picks = IntArray(blobCount)
    var at = 0
    for (c in 0 until colorCount) repeat(counts[c]) { picks[at++] = c }
    for (i in blobCount - 1 downTo 1) {
        val j = random.nextInt(i + 1)
        val swap = picks[i]
        picks[i] = picks[j]
        picks[j] = swap
    }
    return picks
}

/**
 * What colours the cover is made of, in OKLab, as `L, a, b, share` per colour:
 * its pixels grouped by colour, whites and greys included, each group as large as
 * the part of the cover it really covers. Only brightness is limited.
 *
 * A group keeps the average saturation of its pixels rather than the saturation
 * of their average, which averaging a varied group would otherwise wash out.
 */
private fun coverPalette(art: IntArray, width: Int, height: Int): FloatArray {
    val capacity = (width + PALETTE_STRIDE - 1) / PALETTE_STRIDE * ((height + PALETTE_STRIDE - 1) / PALETTE_STRIDE)
    val pixelL = FloatArray(capacity)
    val pixelA = FloatArray(capacity)
    val pixelB = FloatArray(capacity)
    val lab = FloatArray(3)
    var n = 0
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            srgbToOklab(art[y * width + x], lab)
            pixelL[n] = lab[0]
            pixelA[n] = lab[1]
            pixelB[n] = lab[2]
            n++
            x += PALETTE_STRIDE
        }
        y += PALETTE_STRIDE
    }

    // Seeded by what is furthest from what's been picked, so it is the same each
    // read and no real colour on the cover goes without a group to start from.
    val centerL = FloatArray(PALETTE_COLORS)
    val centerA = FloatArray(PALETTE_COLORS)
    val centerB = FloatArray(PALETTE_COLORS)
    val nearest = FloatArray(n) { Float.MAX_VALUE }
    var next = 0
    var groups = 0
    while (groups < PALETTE_COLORS) {
        centerL[groups] = pixelL[next]
        centerA[groups] = pixelA[next]
        centerB[groups] = pixelB[next]
        var furthest = 0f
        for (i in 0 until n) {
            val d = colorDistance(pixelL[i], pixelA[i], pixelB[i], centerL[groups], centerA[groups], centerB[groups])
            if (d < nearest[i]) nearest[i] = d
            if (nearest[i] > furthest) {
                furthest = nearest[i]
                next = i
            }
        }
        groups++
        if (furthest < SAME_COLOR_DISTANCE) break
    }

    val count = IntArray(groups)
    val sumL = FloatArray(groups)
    val sumA = FloatArray(groups)
    val sumB = FloatArray(groups)
    val sumChroma = FloatArray(groups)
    repeat(PALETTE_ROUNDS) {
        count.fill(0)
        sumL.fill(0f)
        sumA.fill(0f)
        sumB.fill(0f)
        sumChroma.fill(0f)
        for (i in 0 until n) {
            var best = 0
            var bestDistance = Float.MAX_VALUE
            for (g in 0 until groups) {
                val d = colorDistance(pixelL[i], pixelA[i], pixelB[i], centerL[g], centerA[g], centerB[g])
                if (d < bestDistance) {
                    bestDistance = d
                    best = g
                }
            }
            count[best]++
            sumL[best] += pixelL[i]
            sumA[best] += pixelA[i]
            sumB[best] += pixelB[i]
            sumChroma[best] += hypot(pixelA[i], pixelB[i])
        }
        for (g in 0 until groups) {
            if (count[g] == 0) continue
            centerL[g] = sumL[g] / count[g]
            centerA[g] = sumA[g] / count[g]
            centerB[g] = sumB[g] / count[g]
        }
    }

    val out = FloatArray(count.count { it > 0 } * 4)
    var at = 0
    for (g in 0 until groups) {
        if (count[g] == 0) continue
        val restore = (sumChroma[g] / count[g]) / hypot(centerA[g], centerB[g]).coerceAtLeast(1e-4f)
        out[at] = centerL[g].coerceIn(MESH_L_MIN, MESH_L_MAX)
        out[at + 1] = centerA[g] * restore
        out[at + 2] = centerB[g] * restore
        out[at + 3] = count[g].toFloat() / n
        at += 4
    }
    return out
}

/** Hue and saturation count for more than lightness, or one colour's shading would split it. */
private fun colorDistance(l1: Float, a1: Float, b1: Float, l2: Float, a2: Float, b2: Float): Float {
    val dl = l1 - l2
    val da = a1 - a2
    val db = b1 - b2
    return dl * dl + 4f * (da * da + db * db)
}

private const val PALETTE_STRIDE = 3
private const val PALETTE_COLORS = 7
private const val PALETTE_ROUNDS = 6
private const val SAME_COLOR_DISTANCE = 0.0004f

private val srgbToLinear = FloatArray(256) {
    val c = it / 255f
    if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
}

private fun srgbToOklab(argb: Int, out: FloatArray) {
    val r = srgbToLinear[(argb ushr 16) and 0xFF]
    val g = srgbToLinear[(argb ushr 8) and 0xFF]
    val b = srgbToLinear[argb and 0xFF]
    val l = cbrt(0.4122215f * r + 0.5363325f * g + 0.05144599f * b)
    val m = cbrt(0.2119035f * r + 0.6806995f * g + 0.1073970f * b)
    val s = cbrt(0.08830246f * r + 0.2817188f * g + 0.6299787f * b)
    out[0] = 0.2104543f * l + 0.7936178f * m - 0.004072047f * s
    out[1] = 1.977998f * l - 2.428592f * m + 0.4505937f * s
    out[2] = 0.02590404f * l + 0.7827718f * m - 0.8086758f * s
}

/** To sRGB 0..255 at [out]`[at]`, pulling chroma in just enough to stay in gamut so nothing clips to an edge. */
private fun oklabToSrgb(lightness: Float, a: Float, b: Float, out: FloatArray, at: Int) {
    var low = 0f
    var high = 1f
    if (!oklabToLinear(lightness, a, b, out, at)) {
        repeat(7) {
            val keep = (low + high) * 0.5f
            if (oklabToLinear(lightness, a * keep, b * keep, out, at)) low = keep else high = keep
        }
        oklabToLinear(lightness, a * low, b * low, out, at)
    }
    for (i in at until at + 3) out[i] = gammaEncode(out[i].coerceIn(0f, 1f)) * 255f
}

private fun oklabToLinear(lightness: Float, a: Float, b: Float, out: FloatArray, at: Int): Boolean {
    val l = lightness + 0.3963378f * a + 0.2158038f * b
    val m = lightness - 0.1055613f * a - 0.06385417f * b
    val s = lightness - 0.08948418f * a - 1.291486f * b
    val ll = l * l * l
    val mm = m * m * m
    val ss = s * s * s
    val red = 4.076742f * ll - 3.307712f * mm + 0.2309699f * ss
    val green = -1.268438f * ll + 2.609757f * mm - 0.3413194f * ss
    val blue = -0.004196086f * ll - 0.7034186f * mm + 1.707615f * ss
    out[at] = red
    out[at + 1] = green
    out[at + 2] = blue
    return red >= 0f && red <= 1f && green >= 0f && green <= 1f && blue >= 0f && blue <= 1f
}

private fun gammaEncode(c: Float): Float =
    if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - 0.055f
