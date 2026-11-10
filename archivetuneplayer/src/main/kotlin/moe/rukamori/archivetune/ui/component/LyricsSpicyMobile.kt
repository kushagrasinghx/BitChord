/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

private val SpicyLineSpacing = 24.dp
private const val SpicyManualResumeMs = 3000L
private const val SpicyInterludeGapMs = 5000

private const val SpicyWordDimAlpha = 0.55f
private const val SpicyBackgroundWordDimAlpha = 0.4f
private const val SpicyBackgroundWordBrightAlpha = 0.75f

private const val SpicyWordLiftEm = 0.055f
private const val SpicyWordRiseResponseSec = 1.4f
private const val SpicyBackgroundLiftMultiplier = 2f

private const val SpicyActiveAlpha = 1f
private const val SpicyInactiveAlpha = 0.64f
private const val SpicyUserScrollingAlpha = 0.8f
private const val SpicyInactiveScale = 0.98f
private const val SpicyLineOpacityMs = 700
private const val SpicyScaleInMs = 500
private const val SpicyScaleOutMs = 700

private const val SpicyBlurStrength = 0.6f
private const val SpicyBlurFadeMs = 500

private const val SpicyEmphasisMinMs = 1000
private const val SpicyEmphasisMaxLetters = 7
private const val SpicyEmphasisStrength = 0.6f
private const val SpicyEmphasisScale = 0.1f
private const val SpicyEmphasisBobEm = 0.02f

private const val SpicyBackgroundPresenceResponseSec = 0.8f
private const val SpicyBackgroundFoldedScale = 0.96f

private const val SpicyActiveGlowAlpha = 0.35f
private const val SpicyActiveGlowBlurRadius = 12f

private const val SpicyScrollStiffness = 100f
private const val SpicyScrollDamping = 0.9f

private const val SpicyDotEnterMs = 3000f
private const val SpicyDotDipMs = 3000f
private const val SpicyDotStillMs = 200f
private const val SpicyDotExitMs = 200f
private const val SpicyDotSmall = 0.92f
private const val SpicyDotBaseAlpha = 0.4f

private fun spicySpringProgress(seconds: Float, responseSec: Float): Float {
    if (seconds <= 0f) return 0f
    val phase = 2f * PI.toFloat() * seconds / responseSec.coerceAtLeast(0.001f)
    return 1f - (1f + phase) * exp(-phase)
}

private fun spicyWordRise(posMs: Long, wordStartMs: Long, lineEndMs: Long?): Float {
    fun up(ms: Long) = spicySpringProgress(ms / 1000f, SpicyWordRiseResponseSec)
    if (lineEndMs == null || posMs < lineEndMs) return up(posMs - wordStartMs)
    return up(lineEndMs - wordStartMs) * (1f - up(posMs - lineEndMs))
}

private fun spicyBackgroundPresence(posMs: Long, lineStartMs: Long, lineEndMs: Long?): Float {
    fun up(ms: Long) = spicySpringProgress(ms / 1000f, SpicyBackgroundPresenceResponseSec)
    if (lineEndMs == null || posMs < lineEndMs) return up(posMs - lineStartMs)
    return up(lineEndMs - lineStartMs) * (1f - up(posMs - lineEndMs))
}

private fun spicySmoothStep(x: Float): Float {
    val v = x.coerceIn(0f, 1f)
    return v * v * (3f - 2f * v)
}

private fun spicyBackgroundAlpha(presence: Float): Float =
    spicySmoothStep((presence - 0.3f) / 0.7f)

private fun spicyBlurEm(signedDistance: Int): Float =
    SpicyBlurStrength *
        when {
            signedDistance == 0 -> 0f
            signedDistance == -1 -> 0.08f
            signedDistance < 0 -> 0.12f
            signedDistance == 1 -> 0.07f
            signedDistance == 2 -> 0.11f
            signedDistance == 3 -> 0.13f
            else -> 0.15f
        }

private fun spicyIsCjk(text: String): Boolean =
    text.any { it in '㐀'..'䶿' || it in '一'..'鿿' || it in '぀'..'ヿ' }

private fun spicyEmphasizes(content: String, durationMs: Long): Boolean {
    if (durationMs < SpicyEmphasisMinMs) return false
    if (spicyIsCjk(content)) return true
    val letters = content.count { it.isLetter() }
    return letters in 2..SpicyEmphasisMaxLetters
}

private fun spicyWordFill(posMs: Int, startMs: Int, endMs: Int): Float {
    val duration = (endMs - startMs).coerceAtLeast(1)
    val featherMs = (duration * 0.45f).coerceIn(120f, 450f)
    return ((posMs - startMs) / featherMs).coerceIn(0f, 1f)
}

private fun spicyEmphasisCurve(progress: Float): Float {
    val x = progress.coerceIn(0f, 1f)
    return if (x < 0.5f) {
        spicySmoothStep(x / 0.5f)
    } else {
        1f - spicySmoothStep((x - 0.5f) / 0.5f)
    }
}

private fun spicyDotScale(t: Float, durationMs: Float): Float {
    if (durationMs <= 0f) return 0f
    val factor =
        (durationMs / (SpicyDotEnterMs + SpicyDotDipMs + SpicyDotStillMs + SpicyDotExitMs))
            .coerceAtMost(1f)
    val enterEnd = SpicyDotEnterMs * factor
    val exitStart = durationMs - SpicyDotExitMs * factor
    val stillStart = exitStart - SpicyDotStillMs * factor
    val dipStart = stillStart - SpicyDotDipMs * factor
    val breathing = (dipStart - enterEnd).coerceAtLeast(0f)
    val halfCycles =
        kotlin.math.round(breathing / 1500f).toInt().coerceAtLeast(1).let { if (it % 2 == 0) it + 1 else it }
    val period = 2f * breathing / halfCycles
    return when {
        t < 0f -> 0f
        t < enterEnd -> spicySmoothStep(t / enterEnd) * if (breathing > 16f) SpicyDotSmall else 1f
        breathing > 16f && t < dipStart ->
            (1f + SpicyDotSmall) / 2f -
                (1f - SpicyDotSmall) / 2f * kotlin.math.cos((t - enterEnd) / period * 2f * PI.toFloat())
        t < dipStart -> 1f
        t < stillStart ->
            SpicyDotSmall +
                (1f - SpicyDotSmall) *
                    kotlin.math.cos(
                        ((t - dipStart) / (stillStart - dipStart).coerceAtLeast(1e-6f))
                            .coerceIn(0f, 1f) * 2f * PI.toFloat(),
                    )
        t < exitStart -> 1f
        t < durationMs ->
            spicySmoothStep((durationMs - t) / (durationMs - exitStart).coerceAtLeast(1e-6f))
        else -> 0f
    }
}

private fun spicyDotAlpha(t: Float, durationMs: Float): Float = when {
    durationMs <= 0f -> 0f
    t < 0f -> 0f
    t < spicyDotEnterEnd(durationMs) -> spicySmoothStep(t / spicyDotEnterEnd(durationMs).coerceAtLeast(1e-6f))
    t < durationMs - SpicyDotExitMs * spicyDotFactor(durationMs) -> 1f
    else -> spicySmoothStep((durationMs - t) / (SpicyDotExitMs * spicyDotFactor(durationMs)).coerceAtLeast(1e-6f))
}

private fun spicyDotFactor(durationMs: Float): Float =
    (durationMs / (SpicyDotEnterMs + SpicyDotDipMs + SpicyDotStillMs + SpicyDotExitMs))
        .coerceAtMost(1f)

private fun spicyDotEnterEnd(durationMs: Float): Float = SpicyDotEnterMs * spicyDotFactor(durationMs)

private fun spicyDotLight(index: Int, t: Float, durationMs: Float): Float {
    val enterEnd = spicyDotEnterEnd(durationMs)
    val exitStart = durationMs - SpicyDotExitMs * spicyDotFactor(durationMs)
    val dotSpan = (exitStart - enterEnd).coerceAtLeast(1f) / 3f
    return SpicyDotBaseAlpha +
        (1f - SpicyDotBaseAlpha) * ((t - enterEnd - dotSpan * index) / dotSpan).coerceIn(0f, 1f)
}

@Composable
fun SpicyLyricsView(
    lyrics: SyncedLyrics,
    currentPosition: () -> Int,
    activeLineIndex: Int,
    onLineClicked: (ISyncedLine) -> Unit,
    onLinePressed: (ISyncedLine) -> Unit,
    modifier: Modifier = Modifier,
    normalTextStyle: TextStyle,
    accompanimentTextStyle: TextStyle,
    translationTextStyle: TextStyle,
    phoneticTextStyle: TextStyle,
    textColor: Color = Color.White,
    useBlurEffect: Boolean = true,
    showTranslation: Boolean = true,
    showPhonetic: Boolean = true,
    anchorTopPadding: Dp = 112.dp,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val anchorTopPx = with(density) { anchorTopPadding.roundToPx() }
    val latestPosition = rememberUpdatedState(currentPosition)

    var manualScrolling by remember { mutableStateOf(false) }
    var lastManualScrollAt by remember { mutableLongStateOf(0L) }
    val scrollConnection =
        remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source == NestedScrollSource.UserInput && available.y != 0f) {
                        manualScrolling = true
                        lastManualScrollAt = System.currentTimeMillis()
                    }
                    return Offset.Zero
                }
            }
        }
    LaunchedEffect(manualScrolling, lastManualScrollAt) {
        if (manualScrolling) {
            delay(SpicyManualResumeMs)
            if (System.currentTimeMillis() - lastManualScrollAt >= SpicyManualResumeMs - 64L) {
                manualScrolling = false
            }
        }
    }

    LaunchedEffect(activeLineIndex, manualScrolling, lyrics, anchorTopPx) {
        if (manualScrolling || lyrics.lines.isEmpty()) return@LaunchedEffect
        val target = activeLineIndex.coerceIn(0, lyrics.lines.lastIndex)
        val layoutInfo = listState.layoutInfo
        val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
        if (visible != null && abs(visible.offset - anchorTopPx) > 1) {
            listState.animateScrollBy(
                (visible.offset - anchorTopPx).toFloat(),
                spring(dampingRatio = SpicyScrollDamping, stiffness = SpicyScrollStiffness),
            )
        } else if (visible == null) {
            runCatching { listState.animateScrollToItem(target, -anchorTopPx) }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val bottomSafePadding = maxHeight - anchorTopPadding + 48.dp
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollConnection),
            contentPadding =
                PaddingValues(
                    top = anchorTopPadding,
                    bottom = bottomSafePadding,
                ),
            verticalArrangement = Arrangement.spacedBy(SpicyLineSpacing),
        ) {
            itemsIndexed(
                items = lyrics.lines,
                key = { index, line -> "${line.start}-${line.end}-$index" },
            ) { index, line ->
                val previous = lyrics.lines.getOrNull(index - 1)
                val gapBeforeMs =
                    if (previous == null || previous.end < 0) {
                        -1
                    } else {
                        line.start - previous.end
                    }
                SpicyLyricsLine(
                    line = line,
                    isActive = index == activeLineIndex,
                    signedDistance = index - activeLineIndex,
                    userScrolling = manualScrolling,
                    hasInterludeBefore = gapBeforeMs > SpicyInterludeGapMs,
                    interludeStartMs = previous?.end ?: 0,
                    interludeEndMs = line.start,
                    position = { latestPosition.value() },
                    normalTextStyle = normalTextStyle,
                    accompanimentTextStyle = accompanimentTextStyle,
                    translationTextStyle = translationTextStyle,
                    phoneticTextStyle = phoneticTextStyle,
                    textColor = textColor,
                    useBlurEffect = useBlurEffect,
                    showTranslation = showTranslation,
                    showPhonetic = showPhonetic,
                    onLineClicked = { onLineClicked(line) },
                    onLinePressed = { onLinePressed(line) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpicyLyricsLine(
    line: ISyncedLine,
    isActive: Boolean,
    signedDistance: Int,
    userScrolling: Boolean,
    hasInterludeBefore: Boolean,
    interludeStartMs: Int,
    interludeEndMs: Int,
    position: () -> Int,
    normalTextStyle: TextStyle,
    accompanimentTextStyle: TextStyle,
    translationTextStyle: TextStyle,
    phoneticTextStyle: TextStyle,
    textColor: Color,
    useBlurEffect: Boolean,
    showTranslation: Boolean,
    showPhonetic: Boolean,
    onLineClicked: () -> Unit,
    onLinePressed: () -> Unit,
) {
    val lineAlpha by animateFloatAsState(
        targetValue =
            when {
                isActive -> SpicyActiveAlpha
                userScrolling -> SpicyUserScrollingAlpha
                else -> SpicyInactiveAlpha
            },
        animationSpec = tween(durationMillis = SpicyLineOpacityMs, easing = FastOutSlowInEasing),
        label = "spicy-line-alpha",
    )
    val lineScale by animateFloatAsState(
        targetValue = if (isActive) 1f else SpicyInactiveScale,
        animationSpec =
            tween(
                durationMillis = if (isActive) SpicyScaleInMs else SpicyScaleOutMs,
                easing = if (isActive) LinearOutSlowInEasing else EaseInOut,
            ),
        label = "spicy-line-scale",
    )
    val blurRadius by animateDpAsState(
        targetValue =
            if (useBlurEffect && signedDistance != 0) {
                (spicyBlurEm(signedDistance) * normalTextStyle.fontSize.value).dp
            } else {
                0.dp
            },
        animationSpec = tween(durationMillis = SpicyBlurFadeMs, easing = FastOutSlowInEasing),
        label = "spicy-line-blur",
    )
    val rightAligned = (line as? KaraokeLine)?.alignment == KaraokeAlignment.End

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = lineAlpha
                    scaleX = lineScale
                    scaleY = lineScale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
                .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
                .combinedClickable(
                    onClick = onLineClicked,
                    onLongClick = onLinePressed,
                )
                .padding(vertical = 2.dp),
        horizontalAlignment = if (rightAligned) Alignment.End else Alignment.Start,
    ) {
        if (hasInterludeBefore) {
            SpicyInterludeDots(
                startMs = interludeStartMs,
                endMs = interludeEndMs,
                position = position,
                dotColor = textColor,
            )
            Spacer(Modifier.height(SpicyLineSpacing))
        }

        val lineEndMs: Long? =
            when (line) {
                is KaraokeLine -> if (line.end >= 0) line.end.toLong() else null
                is SyncedLine -> if (line.end >= 0) line.end.toLong() else null
                else -> null
            }

        when (line) {
            is KaraokeLine.MainKaraokeLine -> {
                SpicyWordsRow(
                    syllables = line.syllables,
                    lineStartMs = line.start.toLong(),
                    lineEndMs = lineEndMs,
                    position = position,
                    isActive = isActive,
                    textStyle = if (isActive) {
                        normalTextStyle.copy(
                            shadow = Shadow(
                                color = textColor.copy(alpha = SpicyActiveGlowAlpha),
                                blurRadius = SpicyActiveGlowBlurRadius,
                            ),
                        )
                    } else {
                        normalTextStyle
                    },
                    textColor = textColor,
                    isBackgroundRow = false,
                    showPhonetic = showPhonetic,
                )
                line.accompanimentLines?.forEach { bgLine ->
                    Spacer(Modifier.height(6.dp))
                    SpicyBackgroundRow(
                        syllables = bgLine.syllables,
                        lineStartMs = line.start.toLong(),
                        lineEndMs = lineEndMs,
                        position = position,
                        isActive = isActive,
                        textStyle = accompanimentTextStyle,
                        textColor = textColor,
                        showPhonetic = false,
                    )
                }
            }

            is KaraokeLine.AccompanimentKaraokeLine -> {
                SpicyWordsRow(
                    syllables = line.syllables,
                    lineStartMs = line.start.toLong(),
                    lineEndMs = lineEndMs,
                    position = position,
                    isActive = isActive,
                    textStyle = accompanimentTextStyle,
                    textColor = textColor,
                    isBackgroundRow = true,
                    showPhonetic = showPhonetic,
                )
            }

            is SyncedLine -> {
                SpicyPlainText(
                    content = line.content,
                    isActive = isActive,
                    position = position,
                    start = line.start,
                    end = line.end,
                    textStyle = if (isActive) {
                        normalTextStyle.copy(
                            shadow = Shadow(
                                color = textColor.copy(alpha = SpicyActiveGlowAlpha),
                                blurRadius = SpicyActiveGlowBlurRadius,
                            ),
                        )
                    } else {
                        normalTextStyle
                    },
                    textColor = textColor,
                )
            }

            else -> {}
        }

        val lineTranslation =
            when (line) {
                is KaraokeLine -> line.translation
                is SyncedLine -> line.translation
                else -> null
            }
        val linePhonetic =
            when (line) {
                is KaraokeLine -> line.phonetic
                else -> null
            }

        if (showTranslation && !lineTranslation.isNullOrBlank()) {
            Text(
                text = lineTranslation,
                style = translationTextStyle,
                color = textColor.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        if (showPhonetic && !linePhonetic.isNullOrBlank()) {
            Text(
                text = linePhonetic,
                style = phoneticTextStyle,
                color = textColor.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpicyWordsRow(
    syllables: List<KaraokeSyllable>,
    lineStartMs: Long,
    lineEndMs: Long?,
    position: () -> Int,
    isActive: Boolean,
    textStyle: TextStyle,
    textColor: Color,
    isBackgroundRow: Boolean,
    showPhonetic: Boolean,
) {
    val liftEm = SpicyWordLiftEm * (if (isBackgroundRow) SpicyBackgroundLiftMultiplier else 1f)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        syllables.forEach { syllable ->
            val phonetic = syllable.phonetic
            if (showPhonetic && !phonetic.isNullOrBlank()) {
                Column {
                    SpicyWord(
                        syllable = syllable,
                        lineEndMs = lineEndMs,
                        position = position,
                        isActive = isActive,
                        textStyle = textStyle,
                        textColor = textColor,
                        isBackgroundWord = isBackgroundRow,
                        liftEm = liftEm,
                    )
                    Text(
                        text = phonetic,
                        style = textStyle.copy(fontSize = textStyle.fontSize * 0.46f),
                        color = textColor.copy(alpha = 0.5f),
                    )
                }
            } else {
                SpicyWord(
                    syllable = syllable,
                    lineEndMs = lineEndMs,
                    position = position,
                    isActive = isActive,
                    textStyle = textStyle,
                    textColor = textColor,
                    isBackgroundWord = isBackgroundRow,
                    liftEm = liftEm,
                )
            }
        }
    }
}

@Composable
private fun SpicyBackgroundRow(
    syllables: List<KaraokeSyllable>,
    lineStartMs: Long,
    lineEndMs: Long?,
    position: () -> Int,
    isActive: Boolean,
    textStyle: TextStyle,
    textColor: Color,
    showPhonetic: Boolean,
) {
    val density = LocalDensity.current
    val fontSizePx = with(density) { textStyle.fontSize.toPx() }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val pos = position().toLong()
                    val presence =
                        if (isActive) {
                            spicyBackgroundPresence(pos, lineStartMs, lineEndMs)
                        } else {
                            0f
                        }
                    alpha = spicyBackgroundAlpha(presence)
                    val s = SpicyBackgroundFoldedScale + (1f - SpicyBackgroundFoldedScale) * presence
                    scaleX = s
                    scaleY = s
                    translationY = (1f - presence) * fontSizePx * 0.3f
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
    ) {
        SpicyWordsRow(
            syllables = syllables,
            lineStartMs = lineStartMs,
            lineEndMs = lineEndMs,
            position = position,
            isActive = isActive,
            textStyle = textStyle,
            textColor = textColor,
            isBackgroundRow = true,
            showPhonetic = showPhonetic,
        )
    }
}

@Composable
private fun SpicyWord(
    syllable: KaraokeSyllable,
    lineEndMs: Long?,
    position: () -> Int,
    isActive: Boolean,
    textStyle: TextStyle,
    textColor: Color,
    isBackgroundWord: Boolean,
    liftEm: Float,
) {
    val density = LocalDensity.current
    val fontSizePx = with(density) { textStyle.fontSize.toPx() }
    val emphasizes = spicyEmphasizes(syllable.content, (syllable.end - syllable.start).toLong())
    Text(
        text = syllable.content,
        style = textStyle,
        color = textColor,
        modifier =
            Modifier.graphicsLayer {
                val pos = position()
                val dim = if (isBackgroundWord) SpicyBackgroundWordDimAlpha else SpicyWordDimAlpha
                val bright =
                    if (isBackgroundWord) SpicyBackgroundWordBrightAlpha else 1f
                val alpha =
                    if (!isActive) {
                        dim
                    } else {
                        val fill = spicyWordFill(pos, syllable.start, syllable.end)
                        bright + (dim - bright) * (1f - fill)
                    }
                this.alpha = alpha
                val rise = spicyWordRise(pos.toLong(), syllable.start.toLong(), lineEndMs)
                var liftPx = liftEm * fontSizePx * rise
                if (emphasizes && isActive) {
                    val span = (syllable.end - syllable.start).coerceAtLeast(1)
                    val progress = ((pos - syllable.start).toFloat() / span).coerceIn(0f, 1f)
                    val grow = spicyEmphasisCurve(progress) * SpicyEmphasisStrength
                    val s = 1f + SpicyEmphasisScale * grow
                    scaleX = s
                    scaleY = s
                    liftPx += SpicyEmphasisBobEm * fontSizePx * sin(progress * PI.toFloat())
                }
                translationY = -liftPx
                transformOrigin = TransformOrigin(0.5f, 0.78f)
            },
    )
}

@Composable
private fun SpicyPlainText(
    content: String,
    isActive: Boolean,
    position: () -> Int,
    start: Int,
    end: Int,
    textStyle: TextStyle,
    textColor: Color,
) {
    Text(
        text = content,
        style = textStyle,
        color = textColor,
        modifier =
            Modifier.graphicsLayer {
                val pos = position()
                val alpha =
                    if (!isActive) {
                        SpicyWordDimAlpha
                    } else {
                        val span = (end - start).coerceAtLeast(1)
                        val p = ((pos - start).toFloat() / span).coerceIn(0f, 1f)
                        SpicyWordDimAlpha + (1f - SpicyWordDimAlpha) * p
                    }
                this.alpha = alpha
            },
    )
}

@Composable
private fun SpicyInterludeDots(
    startMs: Int,
    endMs: Int,
    position: () -> Int,
    dotColor: Color,
) {
    Row {
        repeat(3) { index ->
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = 5.dp)
                        .size(7.dp)
                        .graphicsLayer {
                            val t = (position() - startMs).toFloat()
                            val dur = (endMs - startMs).toFloat()
                            val scale = spicyDotScale(t, dur)
                            val alpha = spicyDotAlpha(t, dur) * spicyDotLight(index, t, dur)
                            this.alpha = alpha
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = 0.9f)),
            )
        }
    }
}
