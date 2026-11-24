package com.music.bitchord.ui.theme

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
import androidx.compose.ui.graphics.ImageBitmap
import coil3.compose.LocalPlatformContext
import com.music.bitchord.ui.graphics.ColorUtils
import com.music.bitchord.ui.graphics.Swatch
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
import kotlin.math.abs
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
 * Album pages take their foreground and background from the sleeve together;
 * other pages adapt the sleeve to the app theme.
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
    val onWhiteAction: Color = Color.Black,
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
    immersiveAlbum: Boolean = false,
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

    val fallback = remember(scheme) {
        ArtworkPalette(
            background = scheme.background,
            wash = scheme.background,
            elevated = scheme.surfaceVariant,
            accent = scheme.primary,
            onBackground = scheme.onBackground,
            onBackgroundVariant = scheme.onSurfaceVariant,
            divider = scheme.outline,
        )
    }

    val spec: AnimationSpec<Color> = if (reduceAnimation || knownUpFront) {
        snap()
    } else {
        tween(TINT_FADE_MS)
    }
    if (immersiveAlbum) {
        val ground by animateColorAsState(seed?.albumGround ?: fallback.background, spec, label = "albumTint")
        // Recompute the foreground from the actual animated surface. Crossfading
        // white into dark ink would make both unreadable halfway through.
        val accent = seed?.vibrant ?: fallback.accent
        return remember(ground, accent) { albumPalette(ground, accent) }
    }
    val target = remember(seed, dark, fallback) { seed?.toPalette(dark) ?: fallback }
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
    val albumGround: Color,
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

    var dominantIndex = 0
    for (index in 1 until all.size) {
        if (all[index].population > all[dominantIndex].population) dominantIndex = index
    }
    val dominant = all[dominantIndex]
    // The accent has to earn its place twice over: a colour nobody sees enough
    // of reads as arbitrary, and a grey one isn't an accent at all. Scoring on
    // saturation against the *square root* of population is what stops a sleeve
    // that is four-fifths black sky from accenting in black.
    val vibrant = accentCandidates.maxBy { swatch ->
        val hsl = FloatArray(3).also { ColorUtils.colorToHSL(swatch.rgb, it) }
        hsl[1] * sqrt(swatch.population.toFloat())
    }
    val edge = bitmap.bottomEdgeColor()
    return Seed(
        dominant = Color(dominant.rgb),
        vibrant = Color(vibrant.rgb),
        edge = edge,
        topBandLuminance = bitmap.topBandRelativeLuminance(),
        albumGround = albumGround(all, dominantIndex, edge),
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
    albumGround = background,
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

private class AlbumSwatch(val color: Color, val population: Int) {
    private val hsl = FloatArray(3).also { ColorUtils.colorToHSL(color.toArgb(), it) }
    val hue = hsl[0]
    // HSL saturation alone rates near-white and near-black noise highly.
    val chroma = hsl[1] * (1f - abs(2f * hsl[2] - 1f))
    val hueWeight = population * ((chroma - 0.065f) / 0.17f).coerceIn(0f, 1f)
}

private fun albumGround(swatches: List<Swatch>, dominantIndex: Int, edge: Color): Color {
    val samples = swatches.map { AlbumSwatch(Color(it.rgb), it.population) }
    val total = samples.sumOf { it.population }.toFloat()
    val dominant = samples[dominantIndex]
    // Count neighboring hues across several tones, but require the chosen
    // swatch itself to have both visible color and meaningful coverage.
    fun weight(hue: Float, sample: AlbumSwatch): Float {
        val distance = abs(hue - sample.hue)
        val arc = minOf(distance, 360f - distance)
        return sample.hueWeight * (1f - arc / 40f).coerceAtLeast(0f)
    }
    fun support(hue: Float): Float = samples.sumOf { sample ->
        weight(hue, sample).toDouble()
    }.toFloat() / total

    val eligible = samples.filter { it.chroma >= 0.08f }
    val first = eligible.firstOrNull()
    var candidate = first
    var coverage = first?.let { support(it.hue) } ?: 0f
    if (first != null && eligible.size > 1) {
        fun score(sample: AlbumSwatch, hueSupport: Float): Float =
            hueSupport * (0.45f + sample.chroma) * sqrt(sample.population / total)
        var bestScore = score(first, coverage)
        for (index in 1 until eligible.size) {
            val sample = eligible[index]
            val hueSupport = support(sample.hue)
            val sampleScore = score(sample, hueSupport)
            // Match maxByOrNull's Float ordering and keep the first on ties.
            if (java.lang.Float.compare(bestScore, sampleScore) < 0) {
                candidate = sample
                coverage = hueSupport
                bestScore = sampleScore
            }
        }
    }
    val mostlyNeutral = dominant.chroma < 0.09f && dominant.population / total >= 0.55f
    val chromatic = candidate?.takeIf { coverage >= 0.30f && !mostlyNeutral }
    if (chromatic == null) {
        val luminance = dominant.color.luminance()
        val edgeBias = (edge.luminance() - luminance).coerceIn(-0.09f, 0.09f) * 0.18f
        val target = (luminance + edgeBias).coerceIn(0.008f, 0.86f)
        return dominant.color.albumTone(Color.Black, Color.White) { it.luminance() <= target }
    }

    // The winning swatch locates the hue family; it does not get to set the
    // whole page's color. Pool its neighbors, including the muted tones.
    val family = samples.mapNotNull { sample ->
        val w = weight(chromatic.hue, sample)
        if (w > 0f) Triple(sample.color, w, sample.color.luminance()) else null
    }.sortedBy { it.third }
    var red = 0f
    var green = 0f
    var blue = 0f
    var mass = 0f
    family.forEach { (color, weight) ->
        red += color.red * weight
        green += color.green * weight
        blue += color.blue * weight
        mass += weight
    }
    val representative = Color(red = red / mass, green = green / mass, blue = blue / mass)

    // Distinguish genuinely high-key artwork from midtone artwork with a few
    // highlights. Prefer white ink without forcing the whole family dark.
    var cumulative = 0f
    val lowerQuartile = family.first { (_, weight) ->
        cumulative += weight
        cumulative >= mass * 0.25f
    }.third
    if (lowerQuartile >= 0.5f) return representative
    return representative.albumTone(Color.Black, representative, preserveChroma = true) {
        it.luminance() <= ALBUM_WHITE_INK_CEILING
    }
}

private fun albumPalette(ground: Color, artworkAccent: Color): ArtworkPalette {
    val y = ground.luminance()
    // Use one polarity for every role. White is preferred whenever it passes;
    // above that point black also has enough contrast.
    val lightInk = y <= ALBUM_WHITE_INK_CEILING
    fun ink(candidate: Color): Color = if (lightInk) {
        candidate.albumTone(Color.White, candidate) { it.luminance() + 0.05f >= ALBUM_TEXT_CONTRAST * (y + 0.05f) }
    } else {
        candidate.albumTone(Color.Black, candidate) { y + 0.05f >= ALBUM_TEXT_CONTRAST * (it.luminance() + 0.05f) }
    }
    fun groundInk(lightness: Float) = ground.withHsl(saturation = { it * 0.55f }, lightness = { lightness })
    val primary = if (lightInk) Color.White else ink(groundInk(0.10f))
    val actionInk = ground.withHsl(lightness = { 0.27f })
    return ArtworkPalette(
        background = ground,
        wash = ground,
        // Move solid control fills away from the ink so they don't spend its
        // contrast margin. Keep this close to the underlying page tint.
        elevated = ground.withHsl(lightness = { if (lightInk) it - 0.03f else it + 0.04f }),
        accent = ink(artworkAccent.withHsl(lightness = { if (lightInk) 0.85f else 0.25f })),
        onBackground = primary,
        onBackgroundVariant = ink(groundInk(if (lightInk) 0.86f else 0.29f)),
        divider = primary.copy(alpha = 0.13f),
        onWhiteAction = actionInk.albumTone(Color.Black, actionInk) {
            1.05f >= ALBUM_TEXT_CONTRAST * (it.luminance() + 0.05f)
        },
    )
}

// Leave a small margin over 4.5:1 for quantization at the render boundary.
private const val ALBUM_TEXT_CONTRAST = 4.55f
private const val ALBUM_WHITE_INK_CEILING = 1.05f / ALBUM_TEXT_CONTRAST - 0.05f

/** Fit an sRGB tone without rotating its hue; keep a known passing endpoint. */
private fun Color.albumTone(
    passing: Color,
    limit: Color,
    preserveChroma: Boolean = false,
    accepts: (Color) -> Boolean,
): Color {
    if (accepts(limit)) return limit
    var best = passing
    var passes = if (passing == Color.White) 1f else 0f
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(toArgb(), it) }
    val chroma = hsl[1] * (1f - abs(2f * hsl[2] - 1f))
    var fails = if (limit == Color.White) 1f else hsl[2]
    repeat(10) {
        val lightness = (passes + fails) / 2f
        hsl[2] = lightness
        if (preserveChroma) {
            // Holding HSL saturation fixed can increase chroma as a pale tone
            // approaches the middle of the gamut. Keep only the color we sampled.
            hsl[1] = (chroma / (1f - abs(2f * lightness - 1f))).coerceAtMost(1f)
        }
        val candidate = Color(ColorUtils.HSLToColor(hsl))
        if (accepts(candidate)) {
            best = candidate
            passes = lightness
        } else {
            fails = lightness
        }
    }
    return best
}

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
