package com.music.bitchord.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ImageBitmap
import coil3.compose.LocalPlatformContext
import com.music.bitchord.ui.graphics.ColorUtils
import com.music.bitchord.ui.graphics.forPixelAccess
import com.music.bitchord.ui.graphics.paletteSwatches
import com.music.bitchord.ui.graphics.toImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.player.PlayerPlatform
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
     * Lighter than [background]. An artist page is this colour, flat, all the
     * way down under its photograph; a release page, whose sleeve sits in a
     * frame rather than bleeding into the page, is [background] instead.
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
 * Colours that came with the artwork rather than from decoding it — Apple
 * Music publishes a background and a text colour for every artist photograph.
 */
@Immutable
data class ArtworkKeyColors(val background: Color, val accent: Color)

/**
 * Pulls [ArtworkPalette] out of the artwork at [imageUrl], or out of
 * [keyColors] when the source already supplied them and there is nothing to
 * decode.
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
    keyColors: ArtworkKeyColors? = null,
): ArtworkPalette {
    val scheme = MaterialTheme.colorScheme
    val reduceAnimation by PlayerPlatform.host.settings.reduceAnimation.collectAsStateWithLifecycle()
    // Always asked, so the composable call is unconditional; handed nothing to
    // read when the colours are already in hand.
    val decoded = rememberArtworkSeed(if (keyColors == null) imageUrl else null, artPx)
    val seed = keyColors?.toSeed() ?: decoded
    // Whether the colours were there from the first frame. If they were, there
    // is nothing to crossfade *from* and animating would only put a delay in
    // front of a surface that could already be right.
    val knownUpFront = remember(imageUrl) { decoded != null }

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

/**
 * The pale tint the player sets its secondary text and glyphs in, or null
 * until the artwork has been read — and for artwork with no colour worth
 * echoing — so the player keeps its plain white rather than showing a
 * placeholder grey that reads as the colour having gone wrong.
 *
 * Not [ArtworkPalette.onBackgroundVariant], though it is lifted the same way:
 * that one always starts from the sleeve's most common colour, and on a cover
 * that is mostly black that colour is a near-black whose hue nobody can see.
 * HSL still gives it one — a black with a trace of red in it is "pink" at full
 * saturation — and lifting it pale put a pink on the player that is nowhere
 * on the cover. So the tint starts from the first colour that is actually
 * visible: the dominant one if it has real chroma, else the accent.
 */
@Composable
fun rememberPlayerTint(imageUrl: String?, artPx: Int = CARD_ART_PX): Color? {
    val seed = rememberArtworkSeed(imageUrl, artPx)
    return remember(seed) { seed?.playerTint() }
}

private fun Seed.playerTint(): Color? {
    val source = listOf(dominant, vibrant).firstOrNull { it.chroma() >= TINT_MIN_CHROMA } ?: return null
    // Much nearer white than an album page's secondary line: the player's
    // backdrop is the artwork itself, often as bright and as coloured as the
    // tint, and a tint at the page's 0.80 sank into it. This keeps a hint of
    // the sleeve while staying a light text colour on any backdrop.
    return source.withHsl(
        saturation = { adaptedArtworkSaturation(it, minimum = 0.18f, maximum = PLAYER_TINT_MAX_SATURATION) },
        lightness = { PLAYER_TINT_LIGHTNESS },
    )
}

private const val PLAYER_TINT_LIGHTNESS = 0.88f
private const val PLAYER_TINT_MAX_SATURATION = 0.40f

/**
 * How much of the page shows through a control dressed in the record's tint.
 *
 * This is the player's own disc — the three-dots button, the heart, the queue
 * — and the floating chrome outside the player copies it rather than inventing
 * a number of its own: a circle in the top bar that is not the same material as
 * the circle in the player reads as the controls breaking, not as the app being
 * dressed.
 */
const val SLEEVE_CHROME_ALPHA = 0.28f

/** The same material on a control whose state is switched on — the lit disc. */
const val SLEEVE_LIT_CHROME_ALPHA = 0.48f

/**
 * The same tint, handed down a page instead of threaded through every control's
 * caller. Null where the page is not dressed by a sleeve at all — the player, a
 * recordless screen — and those controls keep the theme's own surface.
 */
val LocalSleeveTint = compositionLocalOf<Color?> { null }

/**
 * A filled button cut from the sleeve, for the buttons that live inside a
 * surface the record already dresses — a sheet, a dialog card.
 *
 * A button is the loudest thing on those surfaces and the theme's accent is
 * not the record's colour, so an undressed one reads as a control borrowed
 * from another app. Where there is no sleeve the theme's own colours come back
 * untouched, which is what a recordless screen wants.
 */
@Composable
fun sleeveButtonColors(): ButtonColors {
    val sleeve = LocalSleeveTint.current ?: return ButtonDefaults.buttonColors()
    return ButtonDefaults.buttonColors(
        containerColor = sleeve,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        // Off is the same material quieter, not a slab of theme grey.
        disabledContainerColor = sleeve.copy(alpha = SLEEVE_CHROME_ALPHA),
        disabledContentColor = sleeve.copy(alpha = SECONDARY_ON_SLEEVE_ALPHA),
    )
}

/** How quiet a secondary line is when it is written in the sleeve's colour. */
const val SECONDARY_ON_SLEEVE_ALPHA = 0.75f

/**
 * A field or a panel sunk into a surface the record dresses: the same colour
 * as the card behind it, only quieter, rather than the theme's grey slab —
 * which on a tinted card reads as a hole cut in it.
 */
@Composable
fun sleeveFieldFill(): Color =
    LocalSleeveTint.current?.copy(alpha = SLEEVE_CHROME_ALPHA)
        ?: MaterialTheme.colorScheme.surfaceVariant

/**
 * A chip's fill in the sleeve's material: the veil while it is not the choice,
 * the colour itself once it is — the same two steps as a selected tab or a lit
 * disc, so a row of chips and a row of tabs say "this one" the same way.
 */
@Composable
fun sleeveChipFill(selected: Boolean): Color {
    val sleeve = LocalSleeveTint.current
    return if (selected) {
        sleeve ?: MaterialTheme.colorScheme.primary
    } else {
        sleeve?.copy(alpha = SLEEVE_CHROME_ALPHA) ?: MaterialTheme.colorScheme.surfaceVariant
    }
}

/** Whatever sits on a [sleeveChipFill] — readable on the veil and on the full colour alike. */
@Composable
fun sleeveChipContent(selected: Boolean): Color {
    val sleeve = LocalSleeveTint.current
    return when {
        selected -> MaterialTheme.colorScheme.onPrimary
        sleeve != null -> sleeve.copy(alpha = SECONDARY_ON_SLEEVE_ALPHA)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** Spread between the strongest and weakest channel — colour you can see, unlike HSL saturation. */
private fun Color.chroma(): Float = maxOf(red, green, blue) - minOf(red, green, blue)

/** Below this a swatch is black, white or grey to the eye, whatever hue HSL assigns it. */
private const val TINT_MIN_CHROMA = 0.08f

@Composable
private fun rememberArtworkSeed(imageUrl: String?, artPx: Int): Seed? {
    val context = LocalPlatformContext.current
    var seed by remember(imageUrl) { mutableStateOf(imageUrl?.let(seedCache::get)) }

    LaunchedEffect(imageUrl, artPx) {
        if (imageUrl == null || seed != null) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(imageUrl.artworkAt(artPx))
            .size(PALETTE_PX)
            .forPixelAccess()
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        val bitmap = (result as? SuccessResult)?.image?.toImageBitmap() ?: return@LaunchedEffect
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

private fun seedOf(bitmap: ImageBitmap): Seed? {
    fun swatches(clearFilters: Boolean) =
        paletteSwatches(bitmap, SWATCH_COUNT, clearFilters)

    // The default filter deliberately throws away near-black and near-white.
    // That is useful while looking for an accent, but it is the wrong answer
    // to "what colour is this page mostly made of?" A dark photograph would
    // otherwise be reduced to whatever warm face or tiny coloured detail
    // survived the filter, and a monochrome sleeve to an anti-aliased fringe.
    // Both cases used to turn into the same maroon page.
    val all = swatches(clearFilters = true)
    if (all.isEmpty()) return null
    val accentCandidates = swatches(clearFilters = false).ifEmpty { all }

    val dominant = all.maxBy { it.population }
    // The accent has to earn its place twice over: a colour nobody sees enough
    // of reads as arbitrary, and a grey one isn't an accent at all. Scoring on
    // saturation against the *square root* of population is what stops a sleeve
    // that is four-fifths black sky from accenting in black.
    val vibrant = accentCandidates.maxBy { swatch ->
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

/**
 * A seed with every note taken from the supplied colours: the page is the
 * background from top to edge, and the accent is the supplied one.
 */
private fun ArtworkKeyColors.toSeed() = Seed(
    dominant = background,
    vibrant = accent,
    edge = background,
    topBandLuminance = relativeLuminance(background.toArgb()),
)

private const val SWATCH_COUNT = 24

/**
 * The mean of the artwork's bottom band — what a blur wide enough to lose the
 * picture leaves behind at that edge.
 *
 * A flat mean rather than a quantised swatch on purpose: a blur has no notion
 * of which colour is *important*, and the page under the artwork has to match
 * what the blur actually produced, not what the picture is about.
 */
private fun ImageBitmap.bottomEdgeColor(): Color {
    val band = (height * EDGE_BAND).toInt().coerceIn(1, height)
    val pixels = IntArray(width * band)
    readPixels(pixels, startX = 0, startY = height - band, width = width, height = band)

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

private fun ImageBitmap.topBandRelativeLuminance(): Float {
    val band = (height * TOP_BAND).toInt().coerceIn(1, height)
    val pixels = IntArray(width * band)
    readPixels(pixels, startX = 0, startY = 0, width = width, height = band)
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
        background = dominant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.20f, maximum = 0.62f) },
            lightness = { 0.13f },
        ),
        // Follows the edge's own brightness within a band that stays clear of
        // white body text at the top and of [background] at the bottom: a
        // sleeve that ends dark hands over almost invisibly, one that ends
        // bright leaves a page that is visibly lit from under the artwork.
        wash = edge.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.18f, maximum = 0.58f) },
            lightness = { it.coerceIn(0.14f, 0.24f) },
        ),
        elevated = dominant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.20f, maximum = 0.62f) },
            lightness = { 0.22f },
        ),
        accent = vibrant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.55f, maximum = 1f) },
            lightness = { it.coerceIn(0.62f, 0.78f) },
        ),
        onBackground = Color.White,
        // A pale tint of the page's own hue rather than a translucent white —
        // Apple Music's secondary lines are the sleeve's colour, lifted, so a
        // blue record's credits read light blue on its blue page. Bright
        // enough that it still clears contrast on [background].
        onBackgroundVariant = dominant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.22f, maximum = 0.50f) },
            lightness = { 0.80f },
        ),
        divider = Color.White.copy(alpha = 0.12f),
    )
} else {
    ArtworkPalette(
        background = dominant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.14f, maximum = 0.50f) },
            lightness = { 0.91f },
        ),
        wash = edge.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.12f, maximum = 0.46f) },
            lightness = { it.coerceIn(0.78f, 0.90f) },
        ),
        elevated = dominant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.14f, maximum = 0.50f) },
            lightness = { 0.83f },
        ),
        accent = vibrant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.55f, maximum = 1f) },
            lightness = { it.coerceIn(0.30f, 0.44f) },
        ),
        onBackground = Color.Black,
        // The same hue taken down rather than up, for the pale page.
        onBackgroundVariant = dominant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.22f, maximum = 0.55f) },
            lightness = { 0.34f },
        ),
        divider = Color.Black.copy(alpha = 0.10f),
    )
}

/**
 * Keeps neutral artwork neutral instead of inventing a hue for it.
 *
 * HSL represents grey with hue zero. Raising that grey to a saturation floor
 * therefore does not make it "more colourful"; it manufactures red, which
 * becomes brown/maroon once the page lightness is lowered. A small real amount
 * of colour is kept as-is, while an unmistakably chromatic swatch can still be
 * strengthened enough to make controls legible and the page recognisable.
 */
internal fun adaptedArtworkSaturation(source: Float, minimum: Float, maximum: Float): Float {
    val saturation = source.coerceIn(0f, 1f)
    return if (saturation < CHROMATIC_SATURATION_THRESHOLD) {
        saturation
    } else {
        saturation.coerceIn(minimum, maximum)
    }
}

/** Below this, boosting saturation makes quantisation noise visible as a tint. */
private const val CHROMATIC_SATURATION_THRESHOLD = 0.12f

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
