package com.music.bitchord.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.ui.player.PlayerSettings
import com.music.bitchord.ui.player.rememberArtworkColors
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * What Home needs to offer the Replay year story: which year, the artwork its
 * colour is taken from, and what opening it does. Null on Home means no button,
 * and the header is exactly what it was.
 */
@Immutable
class ReplayYearBadge(
    val year: Int,
    /** The year's top song's artwork, which the pill takes its colour from, as the story does. */
    val artworkUrl: String?,
    val contentDescription: String,
    val onOpen: () -> Unit,
)

/**
 * `REPLAY'26 ▶`, beside the wordmark on Home.
 *
 * ## The letters
 *
 * Each character is its own glyph and rolls into the pill on its own: odd ones
 * drop in from above, even ones rise from below, a beat apart, landing with a
 * small overshoot, so the word clicks into place like a split-flap board. The
 * pill clips them, so nothing is ever drawn outside it. Then the play mark pops.
 *
 * Every five seconds the letters carry on through and out the other side, then
 * roll straight back in, and in between the pill sits still. Something that moves all the time
 * on a home screen stops being noticed; something that moves now and then is
 * what the eye goes back to.
 *
 * All of it runs off two timers read in each glyph's layer, so the rolling
 * redraws layers and composes nothing. With animation reduced it is simply
 * there.
 */
@Composable
fun ReplayYearButton(badge: ReplayYearBadge, height: Dp, modifier: Modifier = Modifier) {
    val reduceMotion by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()
    val glyphs = remember(badge.year) { "REPLAY'" + (badge.year % 100).toString().padStart(2, '0') }
    val fill = pillColor(rememberArtworkColors(badge.artworkUrl).colors)

    // Time into the roll-in and the roll-out, in ms.
    val rollIn = remember { Animatable(0f) }
    val rollOut = remember { Animatable(0f) }
    val inEnd = glyphs.length * GLYPH_STAGGER + GLYPH_MS + PLAY_MS
    val outEnd = glyphs.length * OUT_STAGGER + OUT_MS
    LaunchedEffect(reduceMotion, glyphs) {
        rollOut.snapTo(0f)
        if (reduceMotion) {
            rollIn.snapTo(inEnd)
            return@LaunchedEffect
        }
        rollIn.snapTo(0f)
        rollIn.animateTo(inEnd, tween(inEnd.toInt(), easing = LinearEasing))
        while (true) {
            delay(LOOP_PAUSE_MS)
            rollOut.animateTo(outEnd, tween(outEnd.toInt(), easing = LinearEasing))
            rollIn.snapTo(0f)
            rollOut.snapTo(0f)
            rollIn.animateTo(inEnd, tween(inEnd.toInt(), easing = LinearEasing))
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.96f else 1f, tween(120), label = "replay-year-press")
    val haptics = LocalHapticFeedback.current
    val fontSize = with(LocalDensity.current) { (height * 0.5f).toSp() }
    val style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.W900, color = Color.White, lineHeight = fontSize)

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(fill)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                badge.onOpen()
            }
            .semantics { contentDescription = badge.contentDescription }
            .clipToBounds()
            .padding(start = height * 0.42f, end = height * 0.3f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        glyphs.forEachIndexed { index, char ->
            // Odd glyphs (counting from one) come from above, even from below.
            val from = if (index % 2 == 0) -1f else 1f
            Text(
                text = char.toString(),
                style = style,
                maxLines = 1,
                modifier = Modifier.graphicsLayer {
                    val span = size.height + 4.dp.toPx()
                    val p = window(rollIn.value, index * GLYPH_STAGGER, GLYPH_MS)
                    val q = window(rollOut.value, index * OUT_STAGGER, OUT_MS)
                    translationY = from * span * (1f - backOut(p)) - from * span * q * q
                },
            )
        }
        Spacer(Modifier.width(height * 0.16f))
        Box(
            Modifier.graphicsLayer {
                val p = window(rollIn.value, glyphs.length * GLYPH_STAGGER + GLYPH_MS / 2, PLAY_MS)
                val q = window(rollOut.value, 0f, OUT_MS)
                val s = (0.6f + 0.4f * backOut(p)) * (1f - q)
                scaleX = s
                scaleY = s
                alpha = min(1f, p * 2f) * (1f - q)
            },
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(height * 0.62f),
            )
        }
    }
}

private fun window(t: Float, start: Float, duration: Float): Float =
    ((t - start) / duration).coerceIn(0f, 1f)

private fun backOut(x: Float): Float {
    val c1 = 1.9f
    val c3 = c1 + 1f
    val y = x - 1f
    return 1f + c3 * y * y * y + c1 * y * y
}

/**
 * The pill's fill: the most colourful of the artwork's mesh colours, held dark
 * and rich enough for white type, so the button already looks like the story
 * it opens. Worked out in plain RGB→HSL here because this module also runs on
 * the desktop, without Android's colour utilities.
 */
private fun pillColor(colors: List<Color>): Color {
    val best = colors.maxByOrNull { c ->
        val (_, s, l) = hsl(c)
        s * (1f - abs(l - 0.5f))
    } ?: return FALLBACK_FILL
    val (h, s, _) = hsl(best)
    return Color.hsl(h, s.coerceIn(0.55f, 0.85f), 0.40f)
}

private fun hsl(c: Color): Triple<Float, Float, Float> {
    val mx = max(c.red, max(c.green, c.blue))
    val mn = min(c.red, min(c.green, c.blue))
    val l = (mx + mn) / 2f
    if (mx == mn) return Triple(0f, 0f, l)
    val d = mx - mn
    val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
    val h = when (mx) {
        c.red -> ((c.green - c.blue) / d + if (c.green < c.blue) 6f else 0f)
        c.green -> (c.blue - c.red) / d + 2f
        else -> (c.red - c.green) / d + 4f
    } * 60f
    return Triple(h, s, l)
}

private val FALLBACK_FILL = Color(0xFFE5243B)
private const val GLYPH_STAGGER = 45f
private const val GLYPH_MS = 380f
private const val PLAY_MS = 300f
private const val OUT_STAGGER = 30f
private const val OUT_MS = 240f
private const val LOOP_PAUSE_MS = 5_000L
