package com.music.bitchord.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.cancel
import com.music.bitchord.sharedui.resources.clear_loop
import com.music.bitchord.sharedui.resources.loop_active
import com.music.bitchord.sharedui.resources.loop_section
import com.music.bitchord.sharedui.resources.set_point_a
import com.music.bitchord.sharedui.resources.set_point_b
import com.music.bitchord.sharedui.resources.waiting_for_point_a
import com.music.bitchord.sharedui.resources.waiting_for_point_b
import com.music.bitchord.playback.SectionLoop
import com.music.bitchord.playback.SectionLoopState
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import com.music.bitchord.ui.icons.BitChordIcons
import org.jetbrains.compose.resources.stringResource

/**
 * Frosted-glass capsule displayed on the player screen during an active section loop
 * or during the manual A-B setting flow.
 *
 * States rendered:
 * - [SectionLoopState.WaitingForA]: Prompts listener to mark Point A at current playhead.
 * - [SectionLoopState.SettingA]: Point A has been captured; prompts listener to mark Point B.
 * - [SectionLoopState.Active]: Loop is running between Point A and Point B; offers clear button.
 * - [SectionLoopState.Idle]: Hidden cleanly with [AnimatedVisibility].
 */
@Composable
fun SectionLoopBar(
    state: SectionLoopState,
    activeLoop: SectionLoop?,
    onSetPointA: () -> Unit,
    onSetPointB: () -> Unit,
    onClearLoop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceAnimation by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val visible = state !is SectionLoopState.Idle

    val enterTransition = if (reduceAnimation) {
        EnterTransition.None
    } else {
        fadeIn(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) +
            scaleIn(initialScale = 0.92f)
    }

    val exitTransition = if (reduceAnimation) {
        ExitTransition.None
    } else {
        fadeOut(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) +
            scaleOut(targetScale = 0.92f)
    }

    AnimatedVisibility(
        visible = visible,
        enter = enterTransition,
        exit = exitTransition,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .height(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .animateContentSize(
                    animationSpec = if (reduceAnimation) {
                        spring(dampingRatio = 1f, stiffness = Spring.StiffnessHigh)
                    } else {
                        spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                is SectionLoopState.WaitingForA -> {
                    WaitingForARow(
                        onSetPointA = {
                            haptics.play(Haptic.Select)
                            onSetPointA()
                        },
                        onCancel = {
                            haptics.play(Haptic.ToggleOff)
                            onClearLoop()
                        },
                    )
                }

                is SectionLoopState.SettingA -> {
                    SettingBRow(
                        startMs = state.startMs,
                        onSetPointB = {
                            haptics.play(Haptic.ToggleOn)
                            onSetPointB()
                        },
                        onCancel = {
                            haptics.play(Haptic.ToggleOff)
                            onClearLoop()
                        },
                    )
                }

                is SectionLoopState.Active -> {
                    val loop = state.loop
                    ActiveLoopRow(
                        startMs = loop.startMs,
                        endMs = loop.endMs,
                        onClear = {
                            haptics.play(Haptic.ToggleOff)
                            onClearLoop()
                        },
                    )
                }

                is SectionLoopState.Idle -> {
                    // Nothing to display
                }
            }
        }
    }
}

@Composable
private fun WaitingForARow(
    onSetPointA: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = BitChordIcons.Repeat,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.80f),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(Res.string.waiting_for_point_a),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
        Spacer(Modifier.width(10.dp))
        SectionLoopDivider()
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .sizeIn(minWidth = 44.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = Color.White),
                    onClick = onSetPointA,
                )
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.set_point_a),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(2.dp))
        SectionLoopDivider()
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .sizeIn(minWidth = 40.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = Color.White),
                    onClick = onCancel,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(Res.string.cancel),
                tint = Color.White.copy(alpha = 0.70f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SettingBRow(
    startMs: Long,
    onSetPointB: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "A: ${formatLoopTime(startMs)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.95f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "·",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.50f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(Res.string.waiting_for_point_b),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
        Spacer(Modifier.width(10.dp))
        SectionLoopDivider()
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .sizeIn(minWidth = 44.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = Color.White),
                    onClick = onSetPointB,
                )
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.set_point_b),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(2.dp))
        SectionLoopDivider()
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .sizeIn(minWidth = 40.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = Color.White),
                    onClick = onCancel,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(Res.string.clear_loop),
                tint = Color.White.copy(alpha = 0.70f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ActiveLoopRow(
    startMs: Long,
    endMs: Long,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = BitChordIcons.Repeat,
            contentDescription = stringResource(Res.string.loop_active),
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${formatLoopTime(startMs)} – ${formatLoopTime(endMs)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        Spacer(Modifier.width(10.dp))
        SectionLoopDivider()
        Spacer(Modifier.width(4.dp))
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .sizeIn(minWidth = 44.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = Color.White),
                    onClick = onClear,
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(Res.string.clear_loop),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(Res.string.clear_loop),
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun SectionLoopDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(18.dp)
            .background(Color.White.copy(alpha = 0.20f)),
    )
}

/** Formats a timestamp in milliseconds into a concise `m:ss` display string. */
private fun formatLoopTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val secStr = if (seconds < 10) "0$seconds" else "$seconds"
    return "$minutes:$secStr"
}
