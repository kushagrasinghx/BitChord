package com.music.bitchord.wear.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.music.bitchord.wear.lyrics.SyncedLine
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
private const val BG_LEAD_MS = 250L
private const val BG_TAIL_MS = 700L

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
    fontScale: Float = 1f,
    ambient: Boolean = false,
) {
    if (line.isGap) {
        InterludeDots(line, isActive, positionState, modifier)
    } else {
        SungLine(line, isActive, positionState, modifier, altText, fontScale, ambient)
    }
}

@Composable
private fun SungLine(
    line: SyncedLine,
    isActive: Boolean,
    positionState: State<Long>,
    modifier: Modifier,
    altText: String?,
    fontScale: Float,
    ambient: Boolean,
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

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = scale * pulse
                scaleX = s
                scaleY = s
                this.alpha = alpha
            },
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = if (ambient) LyricsPalette.AmbientPrimary else Color.White,
            fontFamily = SFPro,
            fontSize = 15.sp * fontScale,
            lineHeight = 19.sp * fontScale,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
        )
        if (highlight) BackgroundVocal(line, positionState, fontScale)
    }
}

/**
 * The answering vocal, sung under the lead. It is small, comes in while it is sung and goes again,
 * and has its own clock: it does not move the line it belongs to.
 */
@Composable
private fun BackgroundVocal(line: SyncedLine, positionState: State<Long>, fontScale: Float) {
    val text = line.bgText ?: return
    val position = positionState.value
    val visible = position >= line.bgStartMs - BG_LEAD_MS && position <= line.bgEndMs + BG_TAIL_MS
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + expandVertically(tween(220)),
        exit = fadeOut(tween(300)) + shrinkVertically(tween(300)),
    ) {
        val span = (line.bgEndMs - line.bgStartMs).coerceAtLeast(1L)
        val fraction = ((position - line.bgStartMs).toFloat() / span).coerceIn(0f, 1f)
        Text(
            text = buildSweepText(text, fraction),
            modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = 0.8f },
            textAlign = TextAlign.Center,
            color = LyricsPalette.Upcoming,
            fontFamily = SFPro,
            fontSize = 11.sp * fontScale,
            lineHeight = 14.sp * fontScale,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Three dots for an instrumental stretch, as on the phone: they light one after another across the
 * gap, so the wait can be read off them, and shrink away just before the singing comes back. Other
 * gaps stay folded to a hairline, so only the one being played takes room.
 */
@Composable
private fun InterludeDots(
    line: SyncedLine,
    isActive: Boolean,
    positionState: State<Long>,
    modifier: Modifier,
) {
    val height by animateDpAsState(
        targetValue = if (isActive) 34.dp else 2.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "gapHeight",
    )
    Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        if (!isActive) return@Box
        val total = (line.endMs - line.startMs).coerceAtLeast(1L)
        val progress = ((positionState.value - line.startMs).toFloat() / total).coerceIn(0f, 1f)
        val breathe by rememberInfiniteTransition(label = "gapBreathe").animateFloat(
            initialValue = 1f,
            targetValue = 1.18f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "gapBreatheScale",
        )
        val outro = if (progress > 0.92f) 1f - (progress - 0.92f) / 0.08f * 0.6f else 1f
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until 3) {
                val fill = (progress * 3f - i).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .size(8.dp)
                        .graphicsLayer {
                            val scale = breathe * (0.78f + 0.22f * fill) * outro
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.26f + 0.74f * fill)),
                )
            }
        }
    }
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
