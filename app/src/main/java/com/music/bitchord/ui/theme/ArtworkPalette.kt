package com.music.bitchord.ui.theme

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The colours an album, playlist or artist page paints itself in.
 *
 * Apple Music's release pages are not one design tinted five ways — the whole
 * page is derived from the sleeve, down to which grey the metadata line is. So
 * rather than hand callers a raw swatch and let each of them guess, this is the
 * finished set: a page tint, an accent that is legible *on that tint*, and the
 * two text colours and hairline that go with them.
 *
 * Every value is theme-aware. The same sleeve yields a near-black tint in dark
 * mode and a pale wash of the same hue in light mode, which is the only way the
 * pages stay readable when the app's theme disagrees with the artwork's.
 */
@Immutable
data class ArtworkPalette(
    /** The page's background wash. */
    val background: Color,
    /**
     * The colour the artwork's own bottom edge blurs down to.
     *
     * A blur wide enough to lose the picture leaves the mean of what it
     * sampled, so a page that starts from this colour where the artwork stops
     * reads as that blur carrying on rather than as a second surface beginning.
     * Lighter than [background], which the page still settles into further
     * down — the artwork's colour is strongest right under the artwork.
     */
    val wash: Color,
    /** Fill for the glass buttons and chips that sit on [background]. */
    val elevated: Color,
    /** The artwork's own colour, contrast-corrected — titles, icons, Play. */
    val accent: Color,
    val onBackground: Color,
    val onBackgroundVariant: Color,
    val divider: Color,
)

/**
 * Pulls [ArtworkPalette] out of the artwork at [imageUrl].
 *
 * Artwork that has already been read once is tinted on the very first frame,
 * off [seedCache] — a sheet opened from a page it shares a cover with, or a
 * page opened twice, has nothing to wait for and nothing to fade. Only a sleeve
 * genuinely being seen for the first time starts from the theme's own colours
 * and warms into the artwork's, so it never flashes a placeholder tint.
 * "Reduce animation" turns that crossfade into a cut.
 */
@Composable
fun rememberArtworkPalette(
    imageUrl: String?,
    dark: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f,
    /**
     * The artwork size to read, which should be whichever one the surface
     * already has on screen, matching the backdrop drawn from it.
     *
     * A quantiser cares about a thumbnail's resolution no more than a blur
     * does, so the only thing this choice decides is whether the read comes
     * out of the cache or off the network.
     */
    artPx: Int = CARD_ART_PX,
): ArtworkPalette {
    val scheme = MaterialTheme.colorScheme
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val seed = rememberArtworkSeed(imageUrl, artPx)
    // Whether the colours were there from the first frame. If they were, there
    // is nothing to crossfade *from* and animating would only put a delay in
    // front of a surface that could already be right.
    val knownUpFront = remember(imageUrl) { seed != null }

    val target = seed?.toPalette(dark) ?: ArtworkPalette(
        background = scheme.background,
        wash = scheme.background,
        elevated = scheme.surfaceVariant,
        accent = scheme.primary,
        onBackground = scheme.onBackground,
        onBackgroundVariant = scheme.onSurfaceVariant,
        divider = scheme.outline,
    )

    val spec: AnimationSpec<Color> = if (reduceAnimation || knownUpFront) {
        snap()
    } else {
        tween(TINT_FADE_MS)
    }
    return ArtworkPalette(
        background = animateColorAsState(target.background, spec, label = "tintBackground").value,
        wash = animateColorAsState(target.wash, spec, label = "tintWash").value,
        elevated = animateColorAsState(target.elevated, spec, label = "tintElevated").value,
        accent = animateColorAsState(target.accent, spec, label = "tintAccent").value,
        onBackground = animateColorAsState(target.onBackground, spec, label = "tintOn").value,
        onBackgroundVariant = animateColorAsState(
            target.onBackgroundVariant, spec, label = "tintOnVariant",
        ).value,
        divider = animateColorAsState(target.divider, spec, label = "tintDivider").value,
    )
}

/**
 * Reads top-band relative luminance from the cached palette decode.
 * Returns raw artwork luminance without applying top scrim calculations.
 */
@Composable
fun rememberArtworkTopBandLuminance(
    imageUrl: String?,
    artPx: Int = CARD_ART_PX,
): Float? = rememberArtworkSeed(imageUrl, artPx)?.topBandLuminance

@Composable
private fun rememberArtworkSeed(imageUrl: String?, artPx: Int): Seed? {
    val context = LocalContext.current
    var seed by remember(imageUrl) { mutableStateOf(imageUrl?.let(seedCache::get)) }

    LaunchedEffect(imageUrl, artPx) {
        if (imageUrl == null || seed != null) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(imageUrl.artworkAt(artPx))
            .size(PALETTE_PX)
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return@LaunchedEffect
        val found = withContext(Dispatchers.Default) { seedOf(bitmap) } ?: return@LaunchedEffect
        seedCache[imageUrl] = found
        seed = found
    }
    return seed
}

/**
 * Colours already read, keyed by artwork URL.
 *
 * Reading them again costs a decode and a quantise for an answer that cannot
 * have changed — the artwork at a URL is the artwork at that URL. Access is
 * from composition and from the resumption of [rememberArtworkPalette]'s
 * effect, both on the main thread, so it needs no locking of its own.
 */
private val seedCache = object : LinkedHashMap<String, Seed>(0, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, Seed>) = size > SEED_CACHE_ENTRIES
}

/** Deep enough to cover a session's browsing without holding a screenful of colours. */
private const val SEED_CACHE_ENTRIES = 128

private const val PALETTE_PX = 128

/** Short: this is a surface settling into its colour, not an effect in itself. */
private const val TINT_FADE_MS = 260

/**
 * The raw artwork colours: what the page is mostly made of, its brightest
 * note, and what its bottom edge averages out to.
 */
private data class Seed(
    val dominant: Color,
    val vibrant: Color,
    val edge: Color,
    val topBandLuminance: Float,
)

private fun seedOf(bitmap: Bitmap): Seed? {
    fun swatches(builder: Palette.Builder) =
        builder.maximumColorCount(SWATCH_COUNT).generate().swatches

    // The default filter throws away near-black and near-white, which on a
    // monochrome sleeve is the entire image — see MeshGradientBackground, which
    // hit the same wall.
    val found = swatches(Palette.from(bitmap)).ifEmpty {
        swatches(Palette.from(bitmap).clearFilters())
    }
    if (found.isEmpty()) return null

    val dominant = found.maxBy { it.population }
    // The accent has to earn its place twice over: a colour nobody sees enough
    // of reads as arbitrary, and a grey one isn't an accent at all. Scoring on
    // saturation against the *square root* of population is what stops a sleeve
    // that is four-fifths black sky from accenting in black.
    val vibrant = found.maxBy { swatch ->
        val hsl = FloatArray(3).also { ColorUtils.colorToHSL(swatch.rgb, it) }
        hsl[1] * sqrt(swatch.population.toFloat())
    }
    return Seed(
        dominant = Color(dominant.rgb),
        vibrant = Color(vibrant.rgb),
        edge = bitmap.bottomEdgeColor(),
        topBandLuminance = bitmap.topBandRelativeLuminance(),
    )
}

private const val SWATCH_COUNT = 24

/**
 * The mean of the artwork's bottom band — what a blur wide enough to lose the
 * picture leaves behind at that edge.
 *
 * A flat mean rather than a quantised swatch on purpose: a blur has no notion
 * of which colour is *important*, and the page under the artwork has to match
 * what the blur actually produced, not what the picture is about.
 */
private fun Bitmap.bottomEdgeColor(): Color {
    val band = (height * EDGE_BAND).toInt().coerceIn(1, height)
    val pixels = IntArray(width * band)
    getPixels(pixels, 0, width, 0, height - band, width, band)

    var red = 0L
    var green = 0L
    var blue = 0L
    pixels.forEach { pixel ->
        red += (pixel shr 16) and 0xFF
        green += (pixel shr 8) and 0xFF
        blue += pixel and 0xFF
    }
    val count = pixels.size.coerceAtLeast(1)
    return Color(
        red = (red / count).toInt(),
        green = (green / count).toInt(),
        blue = (blue / count).toInt(),
    )
}

/** How much of the artwork's height the edge colour is read from. */
private const val EDGE_BAND = 0.18f

/**
 * The status inset occupies only the upper sliver of the full-bleed hero on a
 * phone. Keep this tight so titles or faces lower in the cover do not decide
 * the icon colour for pixels that are never behind the system bar.
 */
private const val TOP_BAND = 0.10f

private fun Bitmap.topBandRelativeLuminance(): Float {
    val band = (height * TOP_BAND).toInt().coerceIn(1, height)
    val pixels = IntArray(width * band)
    getPixels(pixels, 0, width, 0, 0, width, band)
    return averageRelativeLuminance(pixels)
}

/** WCAG relative luminance: average in linear light, never gamma-encoded RGB. */
internal fun averageRelativeLuminance(pixels: IntArray): Float {
    if (pixels.isEmpty()) return 0f
    return pixels.sumOf { relativeLuminance(it).toDouble() }.div(pixels.size).toFloat()
}

internal fun relativeLuminance(argb: Int): Float {
    fun linear(channel: Int): Float {
        val srgb = channel / 255f
        return if (srgb <= 0.04045f) srgb / 12.92f else ((srgb + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    }
    return 0.2126f * linear((argb shr 16) and 0xFF) +
        0.7152f * linear((argb shr 8) and 0xFF) +
        0.0722f * linear(argb and 0xFF)
}

/**
 * Maps artwork luminance to top scrim opacity to keep white status bar
 * icons legible over light album covers while staying subtle on dark ones.
 */
internal fun topBandScrimAlpha(artworkLuminance: Float?): Float {
    val luminance = artworkLuminance?.coerceIn(0f, 1f) ?: 0f
    return PLAYER_STATUS_SCRIM_MIN_ALPHA +
        (PLAYER_STATUS_SCRIM_MAX_ALPHA - PLAYER_STATUS_SCRIM_MIN_ALPHA) * luminance
}

private const val PLAYER_STATUS_SCRIM_MIN_ALPHA = 0.16f
private const val PLAYER_STATUS_SCRIM_MAX_ALPHA = 0.65f

private fun Seed.toPalette(dark: Boolean): ArtworkPalette = if (dark) {
    ArtworkPalette(
        // Deep enough that white body text clears contrast on any sleeve, but
        // not so deep the hue is gone — the whole point is that the page is
        // recognisably *this* record's colour.
        background = dominant.withHsl(saturation = { it.coerceIn(0.20f, 0.62f) }, lightness = { 0.13f }),
        // Follows the edge's own brightness within a band that stays clear of
        // white body text at the top and of [background] at the bottom: a
        // sleeve that ends dark hands over almost invisibly, one that ends
        // bright leaves a page that is visibly lit from under the artwork.
        wash = edge.withHsl(
            saturation = { it.coerceIn(0.18f, 0.58f) },
            lightness = { it.coerceIn(0.14f, 0.24f) },
        ),
        elevated = dominant.withHsl(saturation = { it.coerceIn(0.20f, 0.62f) }, lightness = { 0.22f }),
        accent = vibrant.withHsl(
            saturation = { it.coerceAtLeast(0.55f) },
            lightness = { it.coerceIn(0.62f, 0.78f) },
        ),
        onBackground = Color.White,
        // Well above the grey the untinted screens use for secondary text. A
        // tint is a *coloured* background, not a black one, so the contrast a
        // dim grey has against black is not the contrast it has here — artist
        // names were sinking into the wash on mid-toned sleeves.
        onBackgroundVariant = Color.White.copy(alpha = 0.80f),
        divider = Color.White.copy(alpha = 0.12f),
    )
} else {
    ArtworkPalette(
        background = dominant.withHsl(saturation = { it.coerceIn(0.14f, 0.50f) }, lightness = { 0.91f }),
        wash = edge.withHsl(
            saturation = { it.coerceIn(0.12f, 0.46f) },
            lightness = { it.coerceIn(0.78f, 0.90f) },
        ),
        elevated = dominant.withHsl(saturation = { it.coerceIn(0.14f, 0.50f) }, lightness = { 0.83f }),
        accent = vibrant.withHsl(
            saturation = { it.coerceAtLeast(0.55f) },
            lightness = { it.coerceIn(0.30f, 0.44f) },
        ),
        onBackground = Color.Black,
        onBackgroundVariant = Color.Black.copy(alpha = 0.70f),
        divider = Color.Black.copy(alpha = 0.10f),
    )
}

private fun Color.withHsl(
    saturation: (Float) -> Float = { it },
    lightness: (Float) -> Float = { it },
): Color {
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(toArgb(), it) }
    hsl[1] = saturation(hsl[1]).coerceIn(0f, 1f)
    hsl[2] = lightness(hsl[2]).coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/** Perceived brightness, used only to tell a dark theme from a light one. */
private fun Color.luminance(): Float = ColorUtils.calculateLuminance(toArgb()).toFloat()
