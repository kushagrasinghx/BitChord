/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.lyrics.LyricsEntry
import moe.rukamori.archivetune.lyrics.LyricsUtils

fun LyricsEntry.toLyricsShareLine(): LyricsShareLine =
    LyricsShareLine(
        text = text,
        translation = LyricsUtils.providedTranslationTextForEntry(this),
        romanisation = providerRomanizedText?.trim()?.takeIf { it.isNotEmpty() }
            ?: romanizedTextFlow.value?.trim()?.takeIf { it.isNotEmpty() },
    )

enum class LyricsShareAspectRatio(
    @StringRes val labelRes: Int,
    val exportWidth: Int,
    val exportHeight: Int,
) {
    Square(
        labelRes = R.string.lyrics_share_layout_square,
        exportWidth = 3072,
        exportHeight = 3072,
    ),
    Portrait(
        labelRes = R.string.lyrics_share_layout_portrait,
        exportWidth = 3072,
        exportHeight = 3840,
    ),
    Story(
        labelRes = R.string.lyrics_share_layout_story,
        exportWidth = 2160,
        exportHeight = 3840,
    ),
    ;

    val previewAspectRatio: Float
        get() = exportWidth.toFloat() / exportHeight.toFloat()
}

@Immutable
data class LyricsShareImageOptions(
    val aspectRatio: LyricsShareAspectRatio = LyricsShareAspectRatio.Square,

    val style: LyricsShareStyle = LyricsShareStyle.LIQUID_GLASS,

    val blurRadius: Float = 24f,

    val dimAmount: Float = 0.45f,

    val liquidyAmount: Float = 0.35f,

    val refractionAmount: Float = 0.45f,

    val glassOpacity: Float = 0.55f,
    val showArtwork: Boolean = true,

    val vinylMode: Boolean = false,

    val showTranslation: Boolean = false,

    val showRomanisation: Boolean = false,
) {
    val sanitizedBlurRadius: Float
        get() = blurRadius.coerceIn(0f, 48f)

    val sanitizedDimAmount: Float
        get() = dimAmount.coerceIn(0f, 1f)

    val sanitizedLiquidyAmount: Float
        get() = liquidyAmount.coerceIn(0f, 1f)

    val sanitizedRefractionAmount: Float
        get() = refractionAmount.coerceIn(0f, 1f)

    val sanitizedGlassOpacity: Float
        get() = glassOpacity.coerceIn(0f, 1f)
}

enum class LyricsShareStyle(
    @StringRes val labelRes: Int,
) {
    LIQUID_GLASS(R.string.lyrics_share_style_liquid_glass),
    FROSTED_DARK(R.string.lyrics_share_style_frosted_dark),
    FROSTED_LIGHT(R.string.lyrics_share_style_frosted_light),
    CLEAR_GLASS(R.string.lyrics_share_style_clear_glass),
    DEEP_BLUR(R.string.lyrics_share_style_deep_blur),
    VIVID_GLOW(R.string.lyrics_share_style_vivid_glow),
}

@Immutable
data class LyricsShareLine(
    val text: String,
    val translation: String? = null,
    val romanisation: String? = null,
)

@Immutable
data class LyricsSharePayload(
    val lyricsText: String,
    val songTitle: String,
    val artists: String,
    val lines: List<LyricsShareLine> = emptyList(),
) {
    val hasTranslation: Boolean
        get() = lines.any { !it.translation.isNullOrBlank() }

    val hasRomanisation: Boolean
        get() = lines.any { !it.romanisation.isNullOrBlank() }

    fun shareDisplayText(
        showTranslation: Boolean,
        showRomanisation: Boolean,
    ): Pair<String, Set<Int>> {
        if (lines.isEmpty()) return lyricsText to emptySet()
        val builder = StringBuilder()
        val romanisedLineIndices = mutableSetOf<Int>()
        var lineIndex = 0
        var wroteAny = false
        for (line in lines) {
            fun appendLine(content: String, romanised: Boolean) {
                val trimmed = content.trim()
                if (trimmed.isEmpty()) return
                if (wroteAny) {
                    builder.append('\n')
                    lineIndex++
                }
                if (romanised) romanisedLineIndices += lineIndex
                builder.append(trimmed)
                wroteAny = true
            }
            appendLine(line.text, romanised = false)
            if (showTranslation) line.translation?.let { appendLine(it, romanised = false) }
            if (showRomanisation) line.romanisation?.let { appendLine(it, romanised = true) }
        }
        return builder.toString() to romanisedLineIndices
    }
}
