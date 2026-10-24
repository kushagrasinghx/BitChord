package com.music.bitchord.wear.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.music.bitchord.shared.wear.SyncedLine
import kotlinx.coroutines.delay

object LyricsPalette {
    /** Neon cyan accent for sung syllables. */
    val Cyan = Color(0xFF00E5FF)

    /** Syllables of the active line that have not been sung yet. */
    val Upcoming = Color(0xFFE8EEF0)

    val AmbientPrimary = Color(0xFFFFFFFF)
    val AmbientSecondary = Color(0xFF8A9498)
}

private const val ACTIVE_SCALE = 1.14f
private const val INACTIVE_ALPHA = 0.38f
private const val SYLLABLE_PULSE_SCALE = 1.025f

/**
 * One lyric line.
 *
 * Recomposition cost: [positionState] is only read while the line is active, so only the active
 * line recomposes per frame; inactive lines are skipped entirely.
 *
 * @param isActive  the line currently being sung
 * @param altText   the phone's translation or Latin-script reading of this line, or null for the
 *                  original. It has no timing of its own: the sweep follows how much of the
 *                  ORIGINAL line has been sung, projected onto the new text (what the phone does).
 * @param ambient   simplified styling for always-on display (no animation, no accent, no glow)
 */
@Composable
fun LyricLineItem(
    line: SyncedLine,
    isActive: Boolean,
    positionState: State<Long>,
    modifier: Modifier = Modifier,
    altText: String? = null,
    ambient: Boolean = false,
) {
    val highlight = isActive && !ambient
    val alternative = altText?.takeIf { it.isNotBlank() }

    val scale by animateFloatAsState(
        targetValue = if (highlight) ACTIVE_SCALE else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "lineScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (highlight || ambient) 1f else INACTIVE_ALPHA,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "lineAlpha",
    )

    // Read position only for the active line (this is what subscribes to the 60 Hz clock).
    val positionMs = if (highlight) positionState.value else 0L
    val syllableIndex = if (highlight) line.syllableIndexAt(positionMs) else -1

    // Subtle spring "pop" every time a new syllable starts.
    var pulseTarget by remember { mutableFloatStateOf(1f) }
    val pulse by animateFloatAsState(
        targetValue = pulseTarget,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "syllablePulse",
    )
    LaunchedEffect(syllableIndex) {
        if (syllableIndex >= 0) {
            pulseTarget = SYLLABLE_PULSE_SCALE
            delay(70)
            pulseTarget = 1f
        }
    }

    val text: AnnotatedString = when {
        highlight && alternative != null -> buildSweepText(alternative, sungFraction(line, positionMs))
        highlight && line.syllables.isNotEmpty() -> buildSyllableText(line, positionMs, syllableIndex)
        alternative != null -> AnnotatedString(alternative)
        else -> AnnotatedString(line.text)
    }

    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = scale * pulse
                scaleX = s
                scaleY = s
                this.alpha = alpha
            },
        textAlign = TextAlign.Center,
        color = if (ambient) LyricsPalette.AmbientPrimary else Color.White,
        fontFamily = SFPro,
        fontSize = 15.sp,
        lineHeight = 19.sp,
        fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
    )
}

/**
 * Sung syllables -> cyan, the active syllable fills white -> cyan with its own progress and a soft
 * glow, upcoming syllables stay light. Gaps between syllables (spaces) keep the base style.
 */
private fun buildSyllableText(line: SyncedLine, positionMs: Long, activeIndex: Int): AnnotatedString =
    buildAnnotatedString {
        val full = line.text
        var cursor = 0
        line.syllables.forEachIndexed { i, syllable ->
            val start = syllable.charStart.coerceIn(cursor, full.length)
            val end = syllable.charEnd.coerceIn(start, full.length)
            if (start > cursor) append(full.substring(cursor, start))

            val style = when {
                i < activeIndex -> SpanStyle(color = LyricsPalette.Cyan)
                i == activeIndex -> {
                    val progress = ((positionMs - syllable.startMs).toFloat() / syllable.durationMs)
                        .coerceIn(0f, 1f)
                    SpanStyle(
                        color = lerp(LyricsPalette.Upcoming, LyricsPalette.Cyan, progress),
                        shadow = Shadow(
                            color = LyricsPalette.Cyan.copy(alpha = 0.55f * progress),
                            offset = Offset.Zero,
                            blurRadius = 14f,
                        ),
                    )
                }
                else -> SpanStyle(color = LyricsPalette.Upcoming)
            }
            withStyle(style) { append(full.substring(start, end)) }
            cursor = end
        }
        if (cursor < full.length) append(full.substring(cursor))
    }

/** How much of the ORIGINAL line has been sung at [positionMs], weighted by characters. */
private fun sungFraction(line: SyncedLine, positionMs: Long): Float {
    var total = 0f
    var sung = 0f
    for (s in line.syllables) {
        val length = (s.charEnd - s.charStart).coerceAtLeast(0).toFloat()
        total += length
        sung += when {
            positionMs >= s.endMs -> length
            positionMs <= s.startMs -> 0f
            else -> length * ((positionMs - s.startMs).toFloat() / s.durationMs)
        }
    }
    return if (total <= 0f) 0f else (sung / total).coerceIn(0f, 1f)
}

/** The translated text filled with the accent as far as the original has been sung. */
private fun buildSweepText(text: String, fraction: Float): AnnotatedString = buildAnnotatedString {
    val split = (text.length * fraction).toInt().coerceIn(0, text.length)
    if (split > 0) withStyle(SpanStyle(color = LyricsPalette.Cyan)) { append(text.substring(0, split)) }
    if (split < text.length) withStyle(SpanStyle(color = LyricsPalette.Upcoming)) { append(text.substring(split)) }
}
