package com.music.bitchord.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.sharedui.resources.*
import com.music.bitchord.ui.utils.containSheetGestures
import com.music.bitchord.ui.components.optimizedHazeEffect
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import java.util.Locale
import kotlin.math.roundToInt

private val DrawerShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
private val ControlShape = RoundedCornerShape(16.dp)
private val ScrimColor = Color.Black.copy(alpha = 0.5f)
private val DrawerMaxWidth = 640.dp
private const val StepMs = 100
private const val DismissDragFraction = 0.25f

/** A player drawer for shifting all synced lyrics against the playback clock. */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
internal fun LyricsOffsetSheet(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val offsetMs by PlayerSettings.lyricsOffsetMs.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    var drag by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(0) }
    val drawerOffset by animateFloatAsState(
        targetValue = drag,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "lyricsOffsetDrawerOffset",
    )
    val scrimAlpha = if (height > 0) {
        (1f - drawerOffset / height).coerceIn(0f, 1f)
    } else {
        1f
    }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val reduceDynamicBlur by PlayerSettings.reduceDynamicBlur.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .containSheetGestures()
            .background(ScrimColor.copy(alpha = ScrimColor.alpha * scrimAlpha))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = shown,
            enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it },
            exit = slideOutVertically(tween(180)) { it },
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = DrawerMaxWidth)
                    .fillMaxWidth()
                    .onSizeChanged { height = it.height }
                    .offset { IntOffset(0, drawerOffset.roundToInt()) }
                    .clip(DrawerShape)
                    .then(
                        if (reduceDynamicBlur) {
                            Modifier.background(Color(0xFF121212))
                        } else {
                            Modifier
                                .optimizedHazeEffect(
                                    state = hazeState,
                                    style = HazeMaterials.regular(Color(0xFF141414)),
                                )
                                .background(Color(0xFF121212).copy(alpha = 0.9f))
                        },
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                    )
                    .pointerInput(height) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (height > 0 && drag > height * DismissDragFraction) {
                                    onDismiss()
                                } else {
                                    drag = 0f
                                }
                            },
                            onDragCancel = { drag = 0f },
                        ) { _, delta -> drag = (drag + delta).coerceAtLeast(0f) }
                    }
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .padding(bottom = 12.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                )
                Text(
                    text = stringResource(Res.string.lyrics_offset),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                )
                Text(
                    text = stringResource(Res.string.lyrics_offset_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 3.dp, bottom = 14.dp),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ControlShape)
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = formatOffset(offsetMs),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color.White,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OffsetButton(
                            label = "−",
                            contentDescription = stringResource(Res.string.decrease_lyrics_offset),
                            enabled = offsetMs > MIN_LYRICS_OFFSET_MS,
                        ) {
                            haptics.play(Haptic.Select)
                            PlayerSettings.setLyricsOffsetMs(offsetMs - StepMs)
                        }
                        ThinSlider(
                            value = offsetToFraction(offsetMs),
                            onValueChange = { PlayerSettings.setLyricsOffsetMs(fractionToOffset(it)) },
                            idleHeight = 6.dp,
                            activeHeight = 10.dp,
                            modifier = Modifier.weight(1f),
                        )
                        OffsetButton(
                            label = "+",
                            contentDescription = stringResource(Res.string.increase_lyrics_offset),
                            enabled = offsetMs < MAX_LYRICS_OFFSET_MS,
                        ) {
                            haptics.play(Haptic.Select)
                            PlayerSettings.setLyricsOffsetMs(offsetMs + StepMs)
                        }
                    }
                }

                Text(
                    text = stringResource(Res.string.lyrics_offset_reset),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = if (offsetMs == 0) 0.35f else 0.8f),
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .clip(CircleShape)
                        .clickable(enabled = offsetMs != 0) {
                            haptics.play(Haptic.Tap)
                            PlayerSettings.setLyricsOffsetMs(0)
                        }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
private fun OffsetButton(
    label: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { this.contentDescription = contentDescription },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White.copy(alpha = if (enabled) 0.85f else 0.25f),
        )
    }
}

private fun offsetToFraction(offsetMs: Int): Float =
    (offsetMs - MIN_LYRICS_OFFSET_MS).toFloat() /
        (MAX_LYRICS_OFFSET_MS - MIN_LYRICS_OFFSET_MS)

private fun fractionToOffset(fraction: Float): Int {
    val raw = MIN_LYRICS_OFFSET_MS +
        fraction.coerceIn(0f, 1f) *
        (MAX_LYRICS_OFFSET_MS - MIN_LYRICS_OFFSET_MS)
    return (raw / StepMs).roundToInt() * StepMs
}

private fun formatOffset(offsetMs: Int): String = String.format(
    Locale.getDefault(),
    "%+.1f s",
    offsetMs / 1_000f,
)
