package com.music.bitchord.ui.tv.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.TvSettings

// ─────────────────────────────────────────────────────────────────────────────
// Immutable palette data class — one instance per theme variant
// ─────────────────────────────────────────────────────────────────────────────

@Immutable
data class TvColorPalette(
    // Accents
    val accentRed: Color,
    val accentRedGlow: Color,
    val accentPink: Color,
    val accentPurple: Color,
    val accentBlue: Color,

    // Canvas & surfaces
    val background: Color,
    val backgroundElevated: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceFocused: Color,
    val surfaceSelected: Color,

    // Text hierarchy
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,

    // Borders & focus
    val borderSubtle: Color,
    val borderFocused: Color,
    val borderFocusedWhite: Color,

    // Overlays & scrims
    val scrimDark: Color,
    val glassOverlay: Color,
    val glassOverlayFocused: Color,

    // Background gradient
    val backgroundGradient: Brush,

    // Whether this is a dark palette
    val isDark: Boolean,

    // tvOS canvas: the two ends of the screen-filling background gradient
    val canvasTop: Color = Color(0xFF26272C),
    val canvasBottom: Color = Color(0xFF101012),
    // Whether the canvas picks up a tint from the playing song's artwork
    val tintsCanvas: Boolean = true,
)

// ─────────────────────────────────────────────────────────────────────────────
// 1. Dark Palette (Dynamic Artwork luxury dark canvas)
// ─────────────────────────────────────────────────────────────────────────────

val TvDarkPalette = TvColorPalette(
    accentRed = Color(0xFFFFFFFF),
    accentRedGlow = Color(0x66FFFFFF),
    accentPink = Color(0xFFE5E5EA),
    accentPurple = Color(0xFFD1D1D6),
    accentBlue = Color(0xFF0A84FF),

    background = Color(0xFF08080B),
    backgroundElevated = Color(0xFF101015),
    surface = Color(0xFF17171E),
    surfaceVariant = Color(0xFF22222C),
    surfaceFocused = Color(0xFF323242),
    surfaceSelected = Color(0xFF38151D),

    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA0A0AB),
    textMuted = Color(0xFF6E6E7A),

    borderSubtle = Color(0xFF282834),
    borderFocused = Color(0xFFFFFFFF),
    borderFocusedWhite = Color(0xFFFFFFFF),

    scrimDark = Color(0xCC000000),
    glassOverlay = Color(0x3320202E),
    glassOverlayFocused = Color(0x44FFFFFF),

    backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF150D18),
            Color(0xFF0A090D),
            Color(0xFF050507),
        ),
    ),

    isDark = true,
)

// ─────────────────────────────────────────────────────────────────────────────
// 2. Pure Black OLED Palette (True #000000 Canvas for 100% OLED Pixel Shutdown)
// ─────────────────────────────────────────────────────────────────────────────

val TvOledPalette = TvColorPalette(
    accentRed = Color(0xFFFFFFFF),
    accentRedGlow = Color(0x66FFFFFF),
    accentPink = Color(0xFFE5E5EA),
    accentPurple = Color(0xFFD1D1D6),
    accentBlue = Color(0xFF0A84FF),

    background = Color(0xFF000000),
    backgroundElevated = Color(0xFF080808),
    surface = Color(0xFF0E0E0E),
    surfaceVariant = Color(0xFF161616),
    surfaceFocused = Color(0xFF282828),
    surfaceSelected = Color(0xFF202020),

    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA5A5B0),
    textMuted = Color(0xFF666670),

    borderSubtle = Color(0xFF1A1A1A),
    borderFocused = Color(0xFFFFFFFF),
    borderFocusedWhite = Color(0xFFFFFFFF),

    scrimDark = Color(0xFA000000),
    glassOverlay = Color(0x22000000),
    glassOverlayFocused = Color(0x44FFFFFF),

    backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF000000),
            Color(0xFF000000),
            Color(0xFF000000),
        ),
    ),

    isDark = true,
    canvasTop = Color(0xFF000000),
    canvasBottom = Color(0xFF000000),
    tintsCanvas = false,
)

// ─────────────────────────────────────────────────────────────────────────────
// 3. Midnight Palette (Deep Navy / Sapphire Dark Canvas)
// ─────────────────────────────────────────────────────────────────────────────

val TvMidnightPalette = TvColorPalette(
    accentRed = Color(0xFF0A84FF),
    accentRedGlow = Color(0x660A84FF),
    accentPink = Color(0xFF5E5CE6),
    accentPurple = Color(0xFFBF5AF2),
    accentBlue = Color(0xFF64D2FF),

    background = Color(0xFF070B14),
    backgroundElevated = Color(0xFF0D1424),
    surface = Color(0xFF121B30),
    surfaceVariant = Color(0xFF1B2844),
    surfaceFocused = Color(0xFF283B64),
    surfaceSelected = Color(0xFF172B4D),

    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA0B0CB),
    textMuted = Color(0xFF657595),

    borderSubtle = Color(0xFF1E2E50),
    borderFocused = Color(0xFFFFFFFF),
    borderFocusedWhite = Color(0xFFFFFFFF),

    scrimDark = Color(0xCC000000),
    glassOverlay = Color(0x33121B30),
    glassOverlayFocused = Color(0x44FFFFFF),

    backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0D162B),
            Color(0xFF080D1A),
            Color(0xFF050810),
        ),
    ),

    isDark = true,
    canvasTop = Color(0xFF18213A),
    canvasBottom = Color(0xFF070A12),
)



// ─────────────────────────────────────────────────────────────────────────────
// CompositionLocal & Static Accessors
// ─────────────────────────────────────────────────────────────────────────────

val LocalTvColors = compositionLocalOf { TvDarkPalette }

object TvColors {
    val AccentRed: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current.accentRed

    val AccentRedGlow: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current.accentRedGlow

    val AccentPink: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current.accentPink

    val AccentPurple: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current.accentPurple

    val AccentBlue: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current.accentBlue

    val Background: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current.background

    val BackgroundElevated: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current.backgroundElevated

    val Surface: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current.surface

    val SurfaceVariant: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current.surfaceVariant

    val SurfaceFocused: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current.surfaceFocused

    val SurfaceSelected: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current.surfaceSelected

    val TextPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current.textPrimary

    val TextSecondary: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current.textSecondary

    val TextMuted: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current.textMuted

    val BorderSubtle: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current.borderSubtle

    val BorderFocused: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current.borderFocused

    val BorderFocusedWhite: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current.borderFocusedWhite

    val ScrimDark: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current.scrimDark

    val GlassOverlay: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current.glassOverlay

    val GlassOverlayFocused: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current.glassOverlayFocused

    val BackgroundGradient: Brush
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current.backgroundGradient
}

object TvThemeColors {
    val current: TvColorPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current
}

// ─────────────────────────────────────────────────────────────────────────────
// Font family
// ─────────────────────────────────────────────────────────────────────────────

val TvSFProDisplay = FontFamily(
    Font(R.font.sf_pro_display_regular, FontWeight.W400),
    Font(R.font.sf_pro_display_medium, FontWeight.W500),
    Font(R.font.sf_pro_display_semibold, FontWeight.W600),
    Font(R.font.sf_pro_display_bold, FontWeight.W700),
    Font(R.font.sf_pro_display_heavy, FontWeight.W800),
)

val TvGoogleSans = FontFamily(
    Font(R.font.google_sans_regular, FontWeight.W400),
    Font(R.font.google_sans_medium, FontWeight.W500),
    Font(R.font.google_sans_bold, FontWeight.W700),
)

val TvMinecraft = FontFamily(
    Font(R.font.minecraft, FontWeight.W400),
    Font(R.font.minecraft_bold, FontWeight.W700),
)

// ─────────────────────────────────────────────────────────────────────────────
// 10-foot TV typography scale
// ─────────────────────────────────────────────────────────────────────────────

fun createTvTypography(palette: TvColorPalette): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = TvSFProDisplay,
        fontWeight = FontWeight.W800,
        fontSize = 37.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.7).sp,
        color = palette.textPrimary,
    ),
    displayMedium = TextStyle(
        fontFamily = TvSFProDisplay,
        fontWeight = FontWeight.W800,
        fontSize = 31.sp,
        lineHeight = 37.sp,
        letterSpacing = (-0.5).sp,
        color = palette.textPrimary,
    ),
    displaySmall = TextStyle(
        fontFamily = TvSFProDisplay,
        fontWeight = FontWeight.W700,
        fontSize = 24.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.3).sp,
        color = palette.textPrimary,
    ),
    headlineLarge = TextStyle(
        fontFamily = TvSFProDisplay,
        fontWeight = FontWeight.W700,
        fontSize = 20.sp,
        lineHeight = 27.sp,
        color = palette.textPrimary,
    ),
    headlineMedium = TextStyle(
        fontFamily = TvSFProDisplay,
        fontWeight = FontWeight.W700,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        color = palette.textPrimary,
    ),
    headlineSmall = TextStyle(
        fontFamily = TvSFProDisplay,
        fontWeight = FontWeight.W600,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = palette.textPrimary,
    ),
    titleLarge = TextStyle(
        fontFamily = TvSFProDisplay,
        fontWeight = FontWeight.W600,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = palette.textPrimary,
    ),
    titleMedium = TextStyle(
        fontFamily = TvSFProDisplay,
        fontWeight = FontWeight.W600,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        color = palette.textPrimary,
    ),
    titleSmall = TextStyle(
        fontFamily = TvSFProDisplay,
        fontWeight = FontWeight.W500,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = palette.textSecondary,
    ),
    bodyLarge = TextStyle(
        fontFamily = TvSFProDisplay,
        fontWeight = FontWeight.W400,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = palette.textPrimary,
    ),
    bodyMedium = TextStyle(
        fontFamily = TvSFProDisplay,
        fontWeight = FontWeight.W400,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = palette.textSecondary,
    ),
    bodySmall = TextStyle(
        fontFamily = TvSFProDisplay,
        fontWeight = FontWeight.W400,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        color = palette.textMuted,
    ),
    labelLarge = TextStyle(
        fontFamily = TvSFProDisplay,
        fontWeight = FontWeight.W600,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        color = palette.textPrimary,
    ),
    labelMedium = TextStyle(
        fontFamily = TvSFProDisplay,
        fontWeight = FontWeight.W600,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        color = palette.textSecondary,
    ),
    labelSmall = TextStyle(
        fontFamily = TvSFProDisplay,
        fontWeight = FontWeight.W600,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        color = palette.textMuted,
    ),
)

object TvDimensions {
    val SafeMarginHorizontal = 38.dp
    val SafeMarginVertical = 22.dp
    val CardSpacing = 16.dp
    val ShelfSpacing = 24.dp
    val TopNavBarHeight = 51.dp

    /** Where scrolling content starts so its first row clears the floating tab bar. */
    val ContentTop = 69.dp

    val NavigationRailWidth = 61.dp
    val NavigationRailExpandedWidth = 176.dp
}

/**
 * tvOS "materials" on a dark canvas. Everything is white at an alpha so the same
 * values read correctly on every theme, and nothing here needs a theme lookup.
 */
object TvGlass {
    /** Resting fill for buttons, rows, the tab bar's capsule. */
    val Fill = Color.White.copy(alpha = 0.10f)
    /** Resting fill for a primary button, a touch brighter than [Fill]. */
    val FillStrong = Color.White.copy(alpha = 0.18f)
    /** The selected-but-unfocused tab or segment. */
    val FillSelected = Color.White.copy(alpha = 0.28f)
    /** The tab bar's own capsule. */
    val Bar = Color.White.copy(alpha = 0.08f)
    /** Placeholder behind artwork that hasn't loaded. */
    val Placeholder = Color(0xFF2E2F33)
    val Hairline = Color.White.copy(alpha = 0.10f)

    /** The focused platter: tvOS inverts a focused control to white. */
    val Platter = Color.White
    val OnPlatter = Color(0xFF0C0C0E)
    val OnPlatterSecondary = Color(0xFF0C0C0E).copy(alpha = 0.60f)

    val TextPrimary = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.60f)
    val TextTertiary = Color.White.copy(alpha = 0.38f)

    /** Apple Music red, kept for the few places that mean "music": playing, liked. */
    val AppleRed = Color(0xFFFA2D48)
    val Destructive = Color(0xFFFF453A)
}

/** The 10-foot type ramp, in the dp space of a 1080p panel (960 × 540 dp). */
object TvType {
    val LargeTitle = TextStyle(fontFamily = TvSFProDisplay, fontWeight = FontWeight.W700, fontSize = 29.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp)
    val Title = TextStyle(fontFamily = TvSFProDisplay, fontWeight = FontWeight.W700, fontSize = 22.sp, lineHeight = 27.sp, letterSpacing = (-0.3).sp)
    val Headline = TextStyle(fontFamily = TvSFProDisplay, fontWeight = FontWeight.W600, fontSize = 16.sp, lineHeight = 20.sp)
    val Body = TextStyle(fontFamily = TvSFProDisplay, fontWeight = FontWeight.W500, fontSize = 14.sp, lineHeight = 18.sp)
    val Callout = TextStyle(fontFamily = TvSFProDisplay, fontWeight = FontWeight.W400, fontSize = 13.sp, lineHeight = 18.sp)
    val CardTitle = TextStyle(fontFamily = TvSFProDisplay, fontWeight = FontWeight.W500, fontSize = 12.sp, lineHeight = 15.sp)
    val Caption = TextStyle(fontFamily = TvSFProDisplay, fontWeight = FontWeight.W400, fontSize = 11.sp, lineHeight = 14.sp)
    val Tab = TextStyle(fontFamily = TvSFProDisplay, fontWeight = FontWeight.W600, fontSize = 13.sp, lineHeight = 17.sp)
}

// ─────────────────────────────────────────────────────────────────────────────
// Font Customization & CompositionLocal — Unified Apple SF Pro Display typography
// ─────────────────────────────────────────────────────────────────────────────

val LocalTvFontFamily = compositionLocalOf { TvSFProDisplay }

// ─────────────────────────────────────────────────────────────────────────────
// Theme wrapper — reacts directly to TvSettings.tvTheme
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BitChordTvTheme(
    content: @Composable () -> Unit,
) {
    val tvThemeId by TvSettings.tvTheme.collectAsState()
    val tvUiScale by TvSettings.tvUiScale.collectAsState()

    val palette = when (tvThemeId.lowercase()) {
        "pure_black", "oled", "pure_black_oled" -> TvOledPalette
        "midnight" -> TvMidnightPalette
        else -> TvDarkPalette // Dynamic Artwork (default) — light/white mode removed
    }

    val colorScheme = darkColorScheme(
        primary = palette.accentRed,
        onPrimary = Color.Black,
        primaryContainer = palette.surfaceVariant,
        onPrimaryContainer = palette.textPrimary,
        secondary = palette.accentPink,
        onSecondary = Color.Black,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceVariant,
        onSurfaceVariant = palette.textSecondary,
        border = palette.borderSubtle,
    )

    val typography = remember(palette) {
        createTvTypography(palette)
    }

    // Apply user DPI scale by overriding LocalDensity for the entire TV UI tree
    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, tvUiScale) {
        Density(density = baseDensity.density * tvUiScale, fontScale = baseDensity.fontScale * tvUiScale)
    }

    CompositionLocalProvider(
        LocalTvColors provides palette,
        LocalTvFontFamily provides TvSFProDisplay,
        LocalDensity provides scaledDensity,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}
