/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player.bitchord

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.lyrics.LyricsEntry
import moe.rukamori.archivetune.lyrics.LyricsUtils.isLineSyncedLrc
import moe.rukamori.archivetune.lyrics.LyricsUtils.isTtml
import moe.rukamori.archivetune.lyrics.LyricsUtils.normalizeLyricsText
import moe.rukamori.archivetune.lyrics.LyricsUtils.parseLyrics
import moe.rukamori.archivetune.lyrics.LyricsUtils.parseTtml
import moe.rukamori.archivetune.lyrics.LyricsUtils.providedTranslationTextForEntry
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

data class LyricWord(val startMs: Long, val endMs: Long, val text: String)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val sungUntilMs: Long? = null,
    val background: LyricLine? = null,

    val translation: String? = null,
    val providerRomanizedText: String? = null,
    val providerRomanizedLanguage: String? = null,
) {
    val isGap: Boolean get() = text.isEmpty()

    val isWordSynced: Boolean get() = words.isNotEmpty()

    val hasKnownEnd: Boolean get() = words.isNotEmpty() || sungUntilMs != null

    val endMs: Long
        get() {
            val lead = words.lastOrNull()?.endMs ?: sungUntilMs ?: timeMs
            return maxOf(lead, background?.endMs ?: lead)
        }

    fun revealedChars(positionMs: Long): Float {
        if (words.isEmpty()) return if (positionMs >= timeMs) text.length.toFloat() else 0f
        var offset = 0
        words.forEachIndexed { index, word ->

            val start = text.indexOf(word.text, offset).takeIf { it >= 0 } ?: offset
            val end = start + word.text.length
            if (positionMs < word.startMs) return start.toFloat()
            if (positionMs < word.endMs) {
                val span = (word.endMs - word.startMs).coerceAtLeast(1L)
                val through = (positionMs - word.startMs).toFloat() / span
                return start + through * word.text.length
            }

            val next = words.getOrNull(index + 1)
            if (next != null && positionMs < next.startMs) {
                val gapStart = text.indexOf(next.text, end).takeIf { it >= 0 } ?: end
                val pause = (next.startMs - word.endMs).coerceAtLeast(1L)
                val through = (positionMs - word.endMs).toFloat() / pause
                return end + through * (gapStart - end)
            }
            offset = end
        }
        return text.length.toFloat()
    }

    fun glowIntensity(positionMs: Long): Float {
        val word = words.firstOrNull { positionMs < it.endMs } ?: return 0f
        if (positionMs < word.startMs) return 0f

        val held = (word.endMs - word.startMs).coerceAtLeast(1L)
        val through = ((positionMs - word.startMs).toFloat() / held).coerceIn(0f, 1f)
        val envelope = when {
            through < GLOW_ATTACK -> through / GLOW_ATTACK
            through > 1f - GLOW_RELEASE -> (1f - through) / GLOW_RELEASE
            else -> 1f
        }
        val pace = ((held - GLOW_FAST_MS).toFloat() / (GLOW_SLOW_MS - GLOW_FAST_MS))
            .coerceIn(0f, 1f)
        return (GLOW_FLOOR + (1f - GLOW_FLOOR) * pace) * envelope.coerceIn(0f, 1f)
    }
}

private const val GLOW_FAST_MS = 130L

private const val GLOW_SLOW_MS = 800L

private const val GLOW_FLOOR = 0.22f

private const val GLOW_ATTACK = 0.18f
private const val GLOW_RELEASE = 0.38f

internal fun List<LyricsEntry>.toBitChordLyrics(): List<LyricLine> =
    map { entry ->
        val lead = mutableListOf<LyricWord>()
        val backing = mutableListOf<LyricWord>()
        entry.words.orEmpty().forEach { word ->

            val w =
                LyricWord(
                    startMs = (word.startTime * 1000.0).toLong(),
                    endMs = (word.endTime * 1000.0).toLong(),
                    text = word.text.trim(),
                )
            if (word.isBackground) backing += w else lead += w
        }
        val text = entry.text
        LyricLine(
            timeMs = entry.time,
            text = text,
            words = lead,
            sungUntilMs = entry.durationMs.takeIf { it > 0L }?.let { entry.time + it },
            background =
                backing
                    .takeIf { it.isNotEmpty() }
                    ?.let { words ->

                        LyricLine(
                            timeMs = words.first().startMs,
                            text = words.joinToString(" ") { w -> w.text },
                            words = words,
                        )
                    },
            translation = providedTranslationTextForEntry(entry),
            providerRomanizedText = entry.providerRomanizedText,
            providerRomanizedLanguage = entry.providerRomanizedLanguage,
        )
    }

internal class BitChordParsedLyrics(
    val lines: List<LyricLine>,
    val isSynced: Boolean,
)

internal fun parseBitChordLyrics(raw: String, durationSeconds: Int?): BitChordParsedLyrics? {
    val normalized = normalizeLyricsText(raw)
    if (normalized.isEmpty() || normalized == LyricsEntity.LYRICS_NOT_FOUND) return null

    val syncedEntries =
        when {
            isLineSyncedLrc(normalized) -> parseLyrics(normalized).takeIf { it.isNotEmpty() }
            isTtml(normalized) -> parseTtml(normalized, durationSeconds).takeIf { it.isNotEmpty() }
            else -> null
        }
    if (syncedEntries != null) return BitChordParsedLyrics(syncedEntries.toBitChordLyrics(), isSynced = true)

    val plainLines =
        normalized
            .lines()
            .map { line -> line.replace(WHITESPACE, " ").trim() }
            .filter { it.isNotEmpty() }
            .map { line -> LyricLine(timeMs = 0L, text = line) }
    if (plainLines.isEmpty()) return null
    return BitChordParsedLyrics(plainLines, isSynced = false)
}

private val WHITESPACE = Regex("\\s+")

@Composable
internal fun rememberLyricClock(positionMs: Long, isPlaying: Boolean): MutableLongState {
    val clock = remember { mutableLongStateOf(positionMs) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var foreground by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            foreground = event == Lifecycle.Event.ON_RESUME
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(positionMs, isPlaying, foreground) {
        clock.longValue = positionMs
        if (!isPlaying || !foreground) return@LaunchedEffect
        var previousFrame = withFrameMillis { it }
        while (true) {
            withFrameMillis { frame ->
                clock.longValue += frame - previousFrame
                previousFrame = frame
            }
        }
    }
    return clock
}

@Composable
private fun SweptLyricLine(
    line: LyricLine,
    clock: MutableLongState,
    style: TextStyle,
    dimAlpha: Float,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    glowAlpha: Float = 0f,
    glowRadius: Dp = GLOW_RADIUS,
    glowRoom: Dp = 0.dp,
) {
    var layout by remember(line) { mutableStateOf<TextLayoutResult?>(null) }

    val room = if (glowRoom > 0.dp) Modifier.padding(glowRoom) else Modifier

    val sweep = Modifier.drawWithContent {
        val position = clock.longValue
        when {
            position >= line.endMs -> drawContent()

            position <= line.timeMs -> Unit
            else -> layout?.let { sweepTo(it, line.revealedChars(position)) }
        }
    }

    Box(modifier) {
        Text(
            text = line.text,
            style = style,
            color = Color.White.copy(alpha = dimAlpha),
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = { layout = it },
            modifier = room,
        )
        if (glowAlpha > 0.01f) {
            Text(
                text = line.text,
                style = style,
                color = Color.White,
                maxLines = maxLines,
                overflow = overflow,
                modifier = Modifier

                    .graphicsLayer { alpha = glowAlpha * line.glowIntensity(clock.longValue) }
                    .blur(glowRadius, BlurredEdgeTreatment.Unbounded)
                    .then(room)

                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        val measured = layout ?: return@drawWithContent
                        val position = clock.longValue
                        glowAt(
                            layout = measured,
                            revealedChars = line.revealedChars(position),
                            intensity = line.glowIntensity(position),
                        )
                    },
            )
        }
        Text(
            text = line.text,
            style = style,
            color = Color.White,
            maxLines = maxLines,
            overflow = overflow,
            modifier = room.then(sweep),
        )
    }
}

private fun ContentDrawScope.glowAt(
    layout: TextLayoutResult,
    revealedChars: Float,
    intensity: Float,
) {
    val length = layout.layoutInput.text.length
    if (length == 0 || revealedChars <= 0f || intensity <= 0f) return

    val edge = revealedChars.coerceIn(0f, length.toFloat())
    val visualLine = layout.getLineForOffset(edge.toInt().coerceIn(0, length - 1))
    val lineStart = layout.getLineStart(visualLine)
    val lineEnd = layout.getLineEnd(visualLine, visibleEnd = true)

    val right = horizontalAt(layout, edge.coerceIn(lineStart.toFloat(), lineEnd.toFloat()), lineStart, lineEnd)
    val trail = GLOW_TRAIL.toPx() * (GLOW_TRAIL_FLOOR + (1f - GLOW_TRAIL_FLOOR) * intensity)
    val left = (right - trail).coerceAtLeast(layout.getLineLeft(visualLine))
    if (right <= left) return

    clipRect(
        left = left,
        top = layout.getLineTop(visualLine),
        right = right,
        bottom = layout.getLineBottom(visualLine),
    ) {
        this@glowAt.drawContent()
    }

    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.45f to Color.White.copy(alpha = 0.22f),
            1f to Color.White,
            startX = left,
            endX = right,
        ),
        blendMode = BlendMode.DstIn,
    )
}

private fun horizontalAt(
    layout: TextLayoutResult,
    chars: Float,
    lineStart: Int,
    lineEnd: Int,
): Float {
    val index = chars.toInt().coerceIn(lineStart, lineEnd)
    val here = layout.getHorizontalPosition(index, usePrimaryDirection = true)
    val next = layout.getHorizontalPosition(
        (index + 1).coerceAtMost(lineEnd),
        usePrimaryDirection = true,
    )
    return here + (next - here) * (chars - index)
}

private fun ContentDrawScope.sweepTo(layout: TextLayoutResult, revealedChars: Float) {
    if (revealedChars <= 0f) return
    if (revealedChars >= layout.layoutInput.text.length) {
        drawContent()
        return
    }
    for (visualLine in 0 until layout.lineCount) {
        val start = layout.getLineStart(visualLine)

        if (revealedChars <= start) return
        val end = layout.getLineEnd(visualLine, visibleEnd = true)
        val right = if (revealedChars >= end) {
            layout.getLineRight(visualLine)
        } else {
            horizontalAt(layout, revealedChars, start, end)
        }
        clipRect(
            left = layout.getLineLeft(visualLine),
            top = layout.getLineTop(visualLine),
            right = right,
            bottom = layout.getLineBottom(visualLine),
        ) {
            this@sweepTo.drawContent()
        }
    }
}

@Composable
internal fun CurrentLyricLine(
    lines: List<LyricLine>,
    trackKey: Any,
    positionMs: Long,
    isPlaying: Boolean,
    durationMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    synced: Boolean = true,
) {
    val clock = rememberLyricClock(positionMs, isPlaying)

    val index by remember(lines, synced) {
        derivedStateOf {
            if (!synced) 0 else lines.indexOfLast { it.timeMs <= clock.longValue }
        }
    }
    val current = lines.getOrNull(index)

    val instrumental = synced && (current == null || current.isGap)

    val firstSung = remember(lines) { lines.indexOfFirst { !it.isGap } }
    val intro = instrumental && firstSung >= 0 && index < firstSung

    val introLine = remember(trackKey) { INTRO_LINES.random() }

    val text = when {
        intro -> introLine
        instrumental -> INSTRUMENTAL_MARK
        else -> current!!.text
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .graphicsLayer {
                if (!synced || instrumental) {
                    alpha = 0.5f
                    return@graphicsLayer
                }
                val start = lines.getOrNull(index)?.timeMs ?: 0L
                val end = lines.getOrNull(index + 1)?.timeMs
                    ?: durationMs.takeIf { it > start }
                    ?: (start + 4_000L)
                val fade = ((end - start) * LYRIC_FADE_FRACTION)
                    .coerceIn(LYRIC_FADE_MIN_MS, LYRIC_FADE_MAX_MS)
                val remaining = (end - clock.longValue).toFloat()
                alpha = 0.78f * (remaining / fade).coerceIn(0f, 1f)
            },
    ) {
        if (instrumental) {
            Icon(
                imageVector = BitChordIcons.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        val swept = current?.takeIf { !instrumental && it.isWordSynced }
        if (swept != null) {
            SweptLyricLine(
                line = swept,
                clock = clock,
                style = MaterialTheme.typography.titleMedium,
                dimAlpha = UNSUNG_ALPHA_STRIP,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Spacer(Modifier.width(6.dp))

        Icon(
            imageVector = BitChordIcons.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
internal fun LyricsUnavailableLine(
    trackKey: Any,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    var visible by remember(trackKey) { mutableStateOf(true) }
    LaunchedEffect(trackKey) {
        delay(LYRICS_UNAVAILABLE_HOLD_MS)
        visible = false
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 0.55f else 0f,
        animationSpec = tween(durationMillis = LYRICS_UNAVAILABLE_FADE_MS),
        label = "lyricsUnavailableAlpha",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = "Lyrics not available",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .graphicsLayer { this.alpha = alpha },
        )
        if (onClick != null) {
            Spacer(Modifier.width(6.dp))

            Icon(
                imageVector = BitChordIcons.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
internal fun LyricsLoadingLine(
    trackKey: Any,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val text = remember(trackKey) { LYRICS_LOADING_LINES.random() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onClick != null) {
            Spacer(Modifier.width(6.dp))

            Icon(
                imageVector = BitChordIcons.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

internal fun keepScrollInList(listState: LazyListState) = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = available

    override suspend fun onPreFling(available: Velocity): Velocity =
        if (available.y > 0f && !listState.canScrollBackward) available else Velocity.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

internal fun Modifier.bleedHorizontally(gutter: Dp): Modifier = layout { measurable, constraints ->
    val extra = gutter.roundToPx() * 2
    val widened = if (constraints.hasBoundedWidth) {
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = constraints.maxWidth + extra,
        )
    } else {
        constraints
    }
    val placeable = measurable.measure(widened)
    val width = (placeable.width - extra).coerceAtLeast(0)
    layout(width, placeable.height) {
        placeable.place(-(placeable.width - width) / 2, 0)
    }
}

internal fun Modifier.fadingEdges(): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fade = 28.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = 0f,
                endY = fade,
            ),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = size.height - fade,
                endY = size.height,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
