package com.music.bitchord.data.theme

import android.util.Base64
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.music.bitchord.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The four seeds a palette is grown from, ported from ArchiveTune's
 * ThemeSeedPalette. A null seed everywhere (see [ThemePalettes.Default]) means
 * the app's own black-and-white scheme, untouched.
 */
data class ThemeSeedPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val neutral: Color,
)

/**
 * A named palette: an id to store in the preferences, the string to show for
 * it, and the seed colors it grows from. Ported from ArchiveTune's
 * ThemePalette with the same ids and colors, so a palette picked there reads
 * back here and vice versa.
 */
data class ThemePalette(
    val id: String,
    @StringRes val labelRes: Int,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val neutral: Color,
) {
    val onPrimary: Color get() = if (primary.luminance() > 0.5f) Color.Black else Color.White

    fun toSeedPalette(): ThemeSeedPalette = ThemeSeedPalette(primary, secondary, tertiary, neutral)
}

/**
 * The palette catalog, ported from ArchiveTune's ThemePalettes. Presets carry
 * the same colors and (English) names as there; ids that were retired there
 * resolve to their successors, and "generateRandomPalette" grows a fresh
 * palette on the same hue offsets.
 */
object ThemePalettes {
    val Default =
        ThemePalette(
            id = "default",
            labelRes = R.string.palette_default,
            primary = Color(0xFFED5564),
            secondary = Color(0xFFED5564),
            tertiary = Color(0xFFED5564),
            neutral = Color(0xFFED5564),
        )

    val OceanBlue =
        ThemePalette(
            id = "ocean_blue",
            labelRes = R.string.palette_ocean_blue,
            primary = Color(0xFF4A90D9),
            secondary = Color(0xFF4A90D9),
            tertiary = Color(0xFF4A90D9),
            neutral = Color(0xFF4A90D9),
        )

    val ArcticBlue =
        ThemePalette(
            id = "arctic_blue",
            labelRes = R.string.palette_arctic_blue,
            primary = Color(0xFF00BFFF),
            secondary = Color(0xFF00BFFF),
            tertiary = Color(0xFF00BFFF),
            neutral = Color(0xFF00BFFF),
        )

    val MidnightNavy =
        ThemePalette(
            id = "midnight_navy",
            labelRes = R.string.palette_midnight_navy,
            primary = Color(0xFF2C3E50),
            secondary = Color(0xFF2C3E50),
            tertiary = Color(0xFF2C3E50),
            neutral = Color(0xFF2C3E50),
        )

    val SkyBlue =
        ThemePalette(
            id = "sky_blue",
            labelRes = R.string.palette_sky_blue,
            primary = Color(0xFF87CEEB),
            secondary = Color(0xFF87CEEB),
            tertiary = Color(0xFF87CEEB),
            neutral = Color(0xFF87CEEB),
        )

    val CobaltBlue =
        ThemePalette(
            id = "cobalt_blue",
            labelRes = R.string.palette_cobalt_blue,
            primary = Color(0xFF0047AB),
            secondary = Color(0xFF0047AB),
            tertiary = Color(0xFF0047AB),
            neutral = Color(0xFF0047AB),
        )

    val ElectricBlue =
        ThemePalette(
            id = "electric_blue",
            labelRes = R.string.palette_electric_blue,
            primary = Color(0xFF7DF9FF),
            secondary = Color(0xFF7DF9FF),
            tertiary = Color(0xFF7DF9FF),
            neutral = Color(0xFF7DF9FF),
        )

    val EmeraldGreen =
        ThemePalette(
            id = "emerald_green",
            labelRes = R.string.palette_emerald_green,
            primary = Color(0xFF2ECC71),
            secondary = Color(0xFF2ECC71),
            tertiary = Color(0xFF2ECC71),
            neutral = Color(0xFF2ECC71),
        )

    val TealWave =
        ThemePalette(
            id = "teal_wave",
            labelRes = R.string.palette_teal_wave,
            primary = Color(0xFF1ABC9C),
            secondary = Color(0xFF1ABC9C),
            tertiary = Color(0xFF1ABC9C),
            neutral = Color(0xFF1ABC9C),
        )

    val ForestGreen =
        ThemePalette(
            id = "forest_green",
            labelRes = R.string.palette_forest_green,
            primary = Color(0xFF228B22),
            secondary = Color(0xFF228B22),
            tertiary = Color(0xFF228B22),
            neutral = Color(0xFF228B22),
        )

    val SpotifyGreen =
        ThemePalette(
            id = "spotify_green",
            labelRes = R.string.palette_spotify_green,
            primary = Color(0xFF1DB954),
            secondary = Color(0xFF1DB954),
            tertiary = Color(0xFF1DB954),
            neutral = Color(0xFF1DB954),
        )

    val MintFresh =
        ThemePalette(
            id = "mint_fresh",
            labelRes = R.string.palette_mint_fresh,
            primary = Color(0xFF98FF98),
            secondary = Color(0xFF98FF98),
            tertiary = Color(0xFF98FF98),
            neutral = Color(0xFF98FF98),
        )

    val OliveGarden =
        ThemePalette(
            id = "olive_garden",
            labelRes = R.string.palette_olive_garden,
            primary = Color(0xFF808000),
            secondary = Color(0xFF808000),
            tertiary = Color(0xFF808000),
            neutral = Color(0xFF808000),
        )

    val SageGreen =
        ThemePalette(
            id = "sage_green",
            labelRes = R.string.palette_sage_green,
            primary = Color(0xFF9CAF88),
            secondary = Color(0xFF9CAF88),
            tertiary = Color(0xFF9CAF88),
            neutral = Color(0xFF9CAF88),
        )

    val SunsetOrange =
        ThemePalette(
            id = "sunset_orange",
            labelRes = R.string.palette_sunset_orange,
            primary = Color(0xFFFF6F61),
            secondary = Color(0xFFFF6F61),
            tertiary = Color(0xFFFF6F61),
            neutral = Color(0xFFFF6F61),
        )

    val GoldenHour =
        ThemePalette(
            id = "golden_hour",
            labelRes = R.string.palette_golden_hour,
            primary = Color(0xFFFFB347),
            secondary = Color(0xFFFFB347),
            tertiary = Color(0xFFFFB347),
            neutral = Color(0xFFFFB347),
        )

    val WarmAmber =
        ThemePalette(
            id = "warm_amber",
            labelRes = R.string.palette_warm_amber,
            primary = Color(0xFFFFBF00),
            secondary = Color(0xFFFFBF00),
            tertiary = Color(0xFFFFBF00),
            neutral = Color(0xFFFFBF00),
        )

    val TangerineBlast =
        ThemePalette(
            id = "tangerine_blast",
            labelRes = R.string.palette_tangerine_blast,
            primary = Color(0xFFFF9966),
            secondary = Color(0xFFFF9966),
            tertiary = Color(0xFFFF9966),
            neutral = Color(0xFFFF9966),
        )

    val Peach =
        ThemePalette(
            id = "peach",
            labelRes = R.string.palette_peach,
            primary = Color(0xFFFFE5B4),
            secondary = Color(0xFFFFE5B4),
            tertiary = Color(0xFFFFE5B4),
            neutral = Color(0xFFFFE5B4),
        )

    val Mango =
        ThemePalette(
            id = "mango",
            labelRes = R.string.palette_mango,
            primary = Color(0xFFFFB347),
            secondary = Color(0xFFFFB347),
            tertiary = Color(0xFFFFB347),
            neutral = Color(0xFFFFB347),
        )

    val RoyalPurple =
        ThemePalette(
            id = "royal_purple",
            labelRes = R.string.palette_royal_purple,
            primary = Color(0xFF7851A9),
            secondary = Color(0xFF7851A9),
            tertiary = Color(0xFF7851A9),
            neutral = Color(0xFF7851A9),
        )

    val LavenderDream =
        ThemePalette(
            id = "lavender_dream",
            labelRes = R.string.palette_lavender_dream,
            primary = Color(0xFFE6E6FA),
            secondary = Color(0xFFE6E6FA),
            tertiary = Color(0xFFE6E6FA),
            neutral = Color(0xFFE6E6FA),
        )

    val GrapePurple =
        ThemePalette(
            id = "grape_purple",
            labelRes = R.string.palette_grape_purple,
            primary = Color(0xFF6F2DA8),
            secondary = Color(0xFF6F2DA8),
            tertiary = Color(0xFF6F2DA8),
            neutral = Color(0xFF6F2DA8),
        )

    val Violet =
        ThemePalette(
            id = "violet",
            labelRes = R.string.palette_violet,
            primary = Color(0xFF8F00FF),
            secondary = Color(0xFF8F00FF),
            tertiary = Color(0xFF8F00FF),
            neutral = Color(0xFF8F00FF),
        )

    val Amethyst =
        ThemePalette(
            id = "amethyst",
            labelRes = R.string.palette_amethyst,
            primary = Color(0xFF9966CC),
            secondary = Color(0xFF9966CC),
            tertiary = Color(0xFF9966CC),
            neutral = Color(0xFF9966CC),
        )

    val UltraViolet =
        ThemePalette(
            id = "ultra_violet",
            labelRes = R.string.palette_ultra_violet,
            primary = Color(0xFF4F42B5),
            secondary = Color(0xFF4F42B5),
            tertiary = Color(0xFF4F42B5),
            neutral = Color(0xFF4F42B5),
        )

    val CherryBlossom =
        ThemePalette(
            id = "cherry_blossom",
            labelRes = R.string.palette_cherry_blossom,
            primary = Color(0xFFFFB7C5),
            secondary = Color(0xFFFFB7C5),
            tertiary = Color(0xFFFFB7C5),
            neutral = Color(0xFFFFB7C5),
        )

    val RoseQuartz =
        ThemePalette(
            id = "rose_quartz",
            labelRes = R.string.palette_rose_quartz,
            primary = Color(0xFFF7CAC9),
            secondary = Color(0xFFF7CAC9),
            tertiary = Color(0xFFF7CAC9),
            neutral = Color(0xFFF7CAC9),
        )

    val MagentaPop =
        ThemePalette(
            id = "magenta_pop",
            labelRes = R.string.palette_magenta_pop,
            primary = Color(0xFFCA1F7B),
            secondary = Color(0xFFCA1F7B),
            tertiary = Color(0xFFCA1F7B),
            neutral = Color(0xFFCA1F7B),
        )

    val HotPink =
        ThemePalette(
            id = "hot_pink",
            labelRes = R.string.palette_hot_pink,
            primary = Color(0xFFFF69B4),
            secondary = Color(0xFFFF69B4),
            tertiary = Color(0xFFFF69B4),
            neutral = Color(0xFFFF69B4),
        )

    val Blush =
        ThemePalette(
            id = "blush",
            labelRes = R.string.palette_blush,
            primary = Color(0xFFDE5D83),
            secondary = Color(0xFFDE5D83),
            tertiary = Color(0xFFDE5D83),
            neutral = Color(0xFFDE5D83),
        )

    val Coral =
        ThemePalette(
            id = "coral",
            labelRes = R.string.palette_coral,
            primary = Color(0xFFFF7F50),
            secondary = Color(0xFFFF7F50),
            tertiary = Color(0xFFFF7F50),
            neutral = Color(0xFFFF7F50),
        )

    val Bubblegum =
        ThemePalette(
            id = "bubblegum",
            labelRes = R.string.palette_bubblegum,
            primary = Color(0xFFFFC1CC),
            secondary = Color(0xFFFFC1CC),
            tertiary = Color(0xFFFFC1CC),
            neutral = Color(0xFFFFC1CC),
        )

    val CrimsonRed =
        ThemePalette(
            id = "crimson_red",
            labelRes = R.string.palette_crimson_red,
            primary = Color(0xFFDC143C),
            secondary = Color(0xFFDC143C),
            tertiary = Color(0xFFDC143C),
            neutral = Color(0xFFDC143C),
        )

    val YouTubeRed =
        ThemePalette(
            id = "youtube_red",
            labelRes = R.string.palette_youtube_red,
            primary = Color(0xFFFF0000),
            secondary = Color(0xFFFF0000),
            tertiary = Color(0xFFFF0000),
            neutral = Color(0xFFFF0000),
        )

    val WineRed =
        ThemePalette(
            id = "wine_red",
            labelRes = R.string.palette_wine_red,
            primary = Color(0xFF722F37),
            secondary = Color(0xFF722F37),
            tertiary = Color(0xFF722F37),
            neutral = Color(0xFF722F37),
        )

    val RubyRed =
        ThemePalette(
            id = "ruby_red",
            labelRes = R.string.palette_ruby_red,
            primary = Color(0xFF9B111E),
            secondary = Color(0xFF9B111E),
            tertiary = Color(0xFF9B111E),
            neutral = Color(0xFF9B111E),
        )

    val Scarlet =
        ThemePalette(
            id = "scarlet",
            labelRes = R.string.palette_scarlet,
            primary = Color(0xFFFF2400),
            secondary = Color(0xFFFF2400),
            tertiary = Color(0xFFFF2400),
            neutral = Color(0xFFFF2400),
        )

    val Charcoal =
        ThemePalette(
            id = "charcoal",
            labelRes = R.string.palette_charcoal,
            primary = Color(0xFF36454F),
            secondary = Color(0xFF36454F),
            tertiary = Color(0xFF36454F),
            neutral = Color(0xFF36454F),
        )

    val Silver =
        ThemePalette(
            id = "silver",
            labelRes = R.string.palette_silver,
            primary = Color(0xFFC0C0C0),
            secondary = Color(0xFFC0C0C0),
            tertiary = Color(0xFFC0C0C0),
            neutral = Color(0xFFC0C0C0),
        )

    val Slate =
        ThemePalette(
            id = "slate",
            labelRes = R.string.palette_slate,
            primary = Color(0xFF708090),
            secondary = Color(0xFF708090),
            tertiary = Color(0xFF708090),
            neutral = Color(0xFF708090),
        )

    val Graphite =
        ThemePalette(
            id = "graphite",
            labelRes = R.string.palette_graphite,
            primary = Color(0xFF2C2C2C),
            secondary = Color(0xFF2C2C2C),
            tertiary = Color(0xFF2C2C2C),
            neutral = Color(0xFF2C2C2C),
        )

    val Terracotta =
        ThemePalette(
            id = "terracotta",
            labelRes = R.string.palette_terracotta,
            primary = Color(0xFFE2725B),
            secondary = Color(0xFFE2725B),
            tertiary = Color(0xFFE2725B),
            neutral = Color(0xFFE2725B),
        )

    val Coffee =
        ThemePalette(
            id = "coffee",
            labelRes = R.string.palette_coffee,
            primary = Color(0xFF6F4E37),
            secondary = Color(0xFF6F4E37),
            tertiary = Color(0xFF6F4E37),
            neutral = Color(0xFF6F4E37),
        )

    val Mocha =
        ThemePalette(
            id = "mocha",
            labelRes = R.string.palette_mocha,
            primary = Color(0xFF967969),
            secondary = Color(0xFF967969),
            tertiary = Color(0xFF967969),
            neutral = Color(0xFF967969),
        )

    val Sand =
        ThemePalette(
            id = "sand",
            labelRes = R.string.palette_sand,
            primary = Color(0xFFC2B280),
            secondary = Color(0xFFC2B280),
            tertiary = Color(0xFFC2B280),
            neutral = Color(0xFFC2B280),
        )

    val Clay =
        ThemePalette(
            id = "clay",
            labelRes = R.string.palette_clay,
            primary = Color(0xFFB66A50),
            secondary = Color(0xFFB66A50),
            tertiary = Color(0xFFB66A50),
            neutral = Color(0xFFB66A50),
        )

    val PastelPink =
        ThemePalette(
            id = "pastel_pink",
            labelRes = R.string.palette_pastel_pink,
            primary = Color(0xFFFFD1DC),
            secondary = Color(0xFFFFD1DC),
            tertiary = Color(0xFFFFD1DC),
            neutral = Color(0xFFFFD1DC),
        )

    val PastelBlue =
        ThemePalette(
            id = "pastel_blue",
            labelRes = R.string.palette_pastel_blue,
            primary = Color(0xFFAEC6CF),
            secondary = Color(0xFFAEC6CF),
            tertiary = Color(0xFFAEC6CF),
            neutral = Color(0xFFAEC6CF),
        )

    val PastelGreen =
        ThemePalette(
            id = "pastel_green",
            labelRes = R.string.palette_pastel_green,
            primary = Color(0xFF77DD77),
            secondary = Color(0xFF77DD77),
            tertiary = Color(0xFF77DD77),
            neutral = Color(0xFF77DD77),
        )

    val PastelYellow =
        ThemePalette(
            id = "pastel_yellow",
            labelRes = R.string.palette_pastel_yellow,
            primary = Color(0xFFFDFD96),
            secondary = Color(0xFFFDFD96),
            tertiary = Color(0xFFFDFD96),
            neutral = Color(0xFFFDFD96),
        )

    val PastelPurple =
        ThemePalette(
            id = "pastel_purple",
            labelRes = R.string.palette_pastel_purple,
            primary = Color(0xFFB39EB5),
            secondary = Color(0xFFB39EB5),
            tertiary = Color(0xFFB39EB5),
            neutral = Color(0xFFB39EB5),
        )

    val NeonGreen =
        ThemePalette(
            id = "neon_green",
            labelRes = R.string.palette_neon_green,
            primary = Color(0xFF39FF14),
            secondary = Color(0xFF39FF14),
            tertiary = Color(0xFF39FF14),
            neutral = Color(0xFF39FF14),
        )

    val NeonPink =
        ThemePalette(
            id = "neon_pink",
            labelRes = R.string.palette_neon_pink,
            primary = Color(0xFFFF10F0),
            secondary = Color(0xFFFF10F0),
            tertiary = Color(0xFFFF10F0),
            neutral = Color(0xFFFF10F0),
        )

    val NeonBlue =
        ThemePalette(
            id = "neon_blue",
            labelRes = R.string.palette_neon_blue,
            primary = Color(0xFF0FF0FF),
            secondary = Color(0xFF0FF0FF),
            tertiary = Color(0xFF0FF0FF),
            neutral = Color(0xFF0FF0FF),
        )

    val NeonOrange =
        ThemePalette(
            id = "neon_orange",
            labelRes = R.string.palette_neon_orange,
            primary = Color(0xFFFF6E27),
            secondary = Color(0xFFFF6E27),
            tertiary = Color(0xFFFF6E27),
            neutral = Color(0xFFFF6E27),
        )

    val Synthwave =
        ThemePalette(
            id = "synthwave",
            labelRes = R.string.palette_synthwave,
            primary = Color(0xFF2D0A5B),
            secondary = Color(0xFFD50F9B),
            tertiary = Color(0xFF0FD5D0),
            neutral = Color(0xFF2D0A5B),
        )

    val Ocean =
        ThemePalette(
            id = "ocean",
            labelRes = R.string.palette_ocean,
            primary = Color(0xFF006994),
            secondary = Color(0xFF40A8C4),
            tertiary = Color(0xFF87CEEB),
            neutral = Color(0xFF001F3F),
        )

    val Forest =
        ThemePalette(
            id = "forest",
            labelRes = R.string.palette_forest,
            primary = Color(0xFF228B22),
            secondary = Color(0xFF006400),
            tertiary = Color(0xFF8FBC8F),
            neutral = Color(0xFF1B4D3E),
        )

    val Autumn =
        ThemePalette(
            id = "autumn",
            labelRes = R.string.palette_autumn,
            primary = Color(0xFFFF4500),
            secondary = Color(0xFFD2691E),
            tertiary = Color(0xFFFF8C00),
            neutral = Color(0xFF8B4513),
        )

    val Winter =
        ThemePalette(
            id = "winter",
            labelRes = R.string.palette_winter,
            primary = Color(0xFFA0C4E8),
            secondary = Color(0xFFE0F0FF),
            tertiary = Color(0xFF6E93B7),
            neutral = Color(0xFF506B85),
        )

    val Spring =
        ThemePalette(
            id = "spring",
            labelRes = R.string.palette_spring,
            primary = Color(0xFFFFB7D5),
            secondary = Color(0xFFB2F7C6),
            tertiary = Color(0xFFFFF5A5),
            neutral = Color(0xFF9CD9B0),
        )

    val Summer =
        ThemePalette(
            id = "summer",
            labelRes = R.string.palette_summer,
            primary = Color(0xFFFF6F61),
            secondary = Color(0xFFFFD166),
            tertiary = Color(0xFF06D6A0),
            neutral = Color(0xFF118AB2),
        )

    val Twilight =
        ThemePalette(
            id = "twilight",
            labelRes = R.string.palette_twilight,
            primary = Color(0xFF301934),
            secondary = Color(0xFF7028A8),
            tertiary = Color(0xFFFF8C42),
            neutral = Color(0xFF1A1A2E),
        )

    val Aurora =
        ThemePalette(
            id = "aurora",
            labelRes = R.string.palette_aurora,
            primary = Color(0xFF00C9A7),
            secondary = Color(0xFF7F00FF),
            tertiary = Color(0xFFC7F464),
            neutral = Color(0xFF0B132B),
        )

    val allPalettes: List<ThemePalette> =
        listOf(
            Default,
            OceanBlue,
            ArcticBlue,
            MidnightNavy,
            SkyBlue,
            CobaltBlue,
            ElectricBlue,
            EmeraldGreen,
            TealWave,
            ForestGreen,
            SpotifyGreen,
            MintFresh,
            OliveGarden,
            SageGreen,
            SunsetOrange,
            GoldenHour,
            WarmAmber,
            TangerineBlast,
            Peach,
            Mango,
            RoyalPurple,
            LavenderDream,
            GrapePurple,
            Violet,
            Amethyst,
            UltraViolet,
            CherryBlossom,
            RoseQuartz,
            MagentaPop,
            HotPink,
            Blush,
            Coral,
            Bubblegum,
            CrimsonRed,
            YouTubeRed,
            WineRed,
            RubyRed,
            Scarlet,
            Charcoal,
            Silver,
            Slate,
            Graphite,
            Terracotta,
            Coffee,
            Mocha,
            Sand,
            Clay,
            PastelPink,
            PastelBlue,
            PastelGreen,
            PastelYellow,
            PastelPurple,
            NeonGreen,
            NeonPink,
            NeonBlue,
            NeonOrange,
            Synthwave,
            Ocean,
            Forest,
            Autumn,
            Winter,
            Spring,
            Summer,
            Twilight,
            Aurora,
        )

    /** Ids that were retired in ArchiveTune, mapped to the palette that took their place. */
    private val RetiredPaletteIds =
        mapOf(
            "cyberpunk" to "magenta_pop",
            "candy" to "hot_pink",
            "rainbow" to "youtube_red",
        )

    fun findByPrimaryColor(colorHex: String): ThemePalette? = allPalettes.find { it.primary.toHexArgb() == colorHex }

    fun findById(id: String): ThemePalette? =
        allPalettes.find { it.id == id }
            ?: RetiredPaletteIds[id]?.let { survivor -> allPalettes.find { it.id == survivor } }

    fun getRandomPalette(): ThemePalette = allPalettes.random()

    /**
     * Grows a palette from a random hue with the same offsets ArchiveTune uses:
     * secondary and tertiary within 30-90 degrees either side of the primary,
     * and a low-chroma neutral near it.
     */
    fun generateRandomPalette(): ThemePalette {
        val random = java.util.Random()
        val primaryHue = random.nextFloat() * 360f
        val primarySaturation = 0.5f + random.nextFloat() * 0.4f
        val primaryLightness = 0.4f + random.nextFloat() * 0.25f
        val primary = hctToColor(primaryHue, primarySaturation, primaryLightness)
        val secondaryHue = (primaryHue + 30f + random.nextFloat() * 60f) % 360f
        val secondary = hctToColor(secondaryHue, primarySaturation * 0.9f, primaryLightness * 1.1f)
        val tertiaryHue = (primaryHue - 30f - random.nextFloat() * 60f + 360f) % 360f
        val tertiary = hctToColor(tertiaryHue, primarySaturation * 0.8f, primaryLightness * 0.95f)
        val neutralHue = (primaryHue + random.nextFloat() * 20f - 10f) % 360f
        val neutral = hctToColor(neutralHue, 0.1f, primaryLightness * 0.8f)
        return ThemePalette(
            id = "random_" + System.currentTimeMillis(),
            labelRes = R.string.palette_custom,
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
            neutral = neutral,
        )
    }

    private fun hctToColor(
        hue: Float,
        saturation: Float,
        lightness: Float,
    ): Color {
        val hsv = floatArrayOf(hue, saturation, lightness)
        val argb = android.graphics.Color.HSVToColor(hsv)
        return Color(argb)
    }

    private fun Color.toHexArgb(): String = String.format("#%06X", 0xFFFFFF and toArgb())
}

/**
 * Reads and writes a [ThemeSeedPalette] as a preference value, ported from
 * ArchiveTune's ThemeSeedPaletteCodec: the same "seedPalette:" + URL-safe
 * base64 of a JSON payload, so themes exported there import here unchanged.
 */
object ThemeSeedPaletteCodec {
    private const val PreferencePrefix = "seedPalette:"
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }

    @Serializable
    private data class ThemeExportV1(
        val version: Int = 1,
        val name: String? = null,
        val primary: String,
        val secondary: String,
        val tertiary: String,
        val neutral: String,
    )

    fun encodeForPreference(
        palette: ThemeSeedPalette,
        name: String? = null,
    ): String {
        val payload =
            json.encodeToString(
                ThemeExportV1(
                    name = name,
                    primary = palette.primary.toHexArgbString(),
                    secondary = palette.secondary.toHexArgbString(),
                    tertiary = palette.tertiary.toHexArgbString(),
                    neutral = palette.neutral.toHexArgbString(),
                ),
            )
        val b64 = Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
        return PreferencePrefix + b64
    }

    fun decodeFromPreference(value: String): ThemeSeedPalette? {
        if (!value.startsWith(PreferencePrefix)) return null
        val b64 = value.removePrefix(PreferencePrefix)
        val decoded =
            runCatching {
                val bytes = Base64.decode(b64, Base64.URL_SAFE or Base64.NO_WRAP)
                bytes.toString(Charsets.UTF_8)
            }.getOrNull()
                ?: return null
        return decodeFromJson(decoded)
    }

    fun encodeAsJson(
        palette: ThemeSeedPalette,
        name: String? = null,
    ): String =
        json.encodeToString(
            ThemeExportV1(
                name = name,
                primary = palette.primary.toHexArgbString(),
                secondary = palette.secondary.toHexArgbString(),
                tertiary = palette.tertiary.toHexArgbString(),
                neutral = palette.neutral.toHexArgbString(),
            ),
        )

    fun decodeFromJson(text: String): ThemeSeedPalette? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            val element = json.parseToJsonElement(trimmed)
            val obj = element.jsonObject

            val version = obj["version"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
            if (version != 1) return@runCatching null

            fun getColor(key: String): Color? =
                obj[key]
                    ?.jsonPrimitive
                    ?.content
                    ?.toColorOrNull()

            val primary = getColor("primary") ?: return@runCatching null
            val secondary = getColor("secondary") ?: primary
            val tertiary = getColor("tertiary") ?: primary
            val neutral = getColor("neutral") ?: primary

            ThemeSeedPalette(
                primary = primary,
                secondary = secondary,
                tertiary = tertiary,
                neutral = neutral,
            )
        }.getOrNull()
            ?: decodeFromLegacyObject(trimmed)
    }

    fun extractNameFromJsonOrNull(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            val element: JsonElement = json.parseToJsonElement(trimmed)
            element.jsonObject["name"]
                ?.jsonPrimitive
                ?.content
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun extractNameFromPreference(value: String): String? {
        if (!value.startsWith(PreferencePrefix)) return null
        val b64 = value.removePrefix(PreferencePrefix)
        val decoded =
            runCatching {
                val bytes = Base64.decode(b64, Base64.URL_SAFE or Base64.NO_WRAP)
                bytes.toString(Charsets.UTF_8)
            }.getOrNull()
                ?: return null
        return extractNameFromJsonOrNull(decoded)
    }

    private fun decodeFromLegacyObject(text: String): ThemeSeedPalette? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{")) return null
        return runCatching {
            val element = json.parseToJsonElement(trimmed)
            val obj = element.jsonObject

            fun getHex(key: String): String? =
                obj[key]
                    ?.jsonPrimitive
                    ?.content
                    ?.takeIf { it.isNotBlank() }

            val primary = getHex("primary")?.toColorOrNull() ?: return@runCatching null
            val secondary = getHex("secondary")?.toColorOrNull() ?: primary
            val tertiary = getHex("tertiary")?.toColorOrNull() ?: primary
            val neutral = getHex("neutral")?.toColorOrNull() ?: primary

            ThemeSeedPalette(primary, secondary, tertiary, neutral)
        }.getOrNull()
    }

    private fun Color.toHexArgbString(): String = String.format("#%08X", this.toArgb())

    private fun String.toColorOrNull(): Color? {
        val normalized = trim()
        if (normalized.isEmpty()) return null
        return runCatching {
            val withHash = if (normalized.startsWith("#")) normalized else "#$normalized"
            Color(android.graphics.Color.parseColor(withHash))
        }.getOrNull()
    }
}
