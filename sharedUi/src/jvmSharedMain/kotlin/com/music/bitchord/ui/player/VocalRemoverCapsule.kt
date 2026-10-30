package com.music.bitchord.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.sharedui.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * The live vocal remover control: a 34.dp disc like [TranslationToggleButton]
 * (whose right-hand slot it takes on the lyrics screens) that morphs into an
 * Apple-Music-style capsule when enabled — the leading disc closes it again,
 * the slider mixes the vocals back over the instrumental at will.
 *
 * Draws nothing when the host provides no engine (desktop). The debug popup
 * is TEMPORARY, per docs/VOCAL_REMOVER.md: it renders [VocalRemoverDebug] and
 * goes away with that class.
 */
@Composable
internal fun VocalRemoverControl(reduceMotion: Boolean) {
    val remover = LocalVocalRemover.current ?: return
    val state by remover.snapshot.collectAsStateWithLifecycle()
    val tint = LocalPlayerTint.current
    val preparing = state.phase == VocalRemoverPhase.DOWNLOADING_MODEL ||
        state.phase == VocalRemoverPhase.FETCHING_STREAM ||
        state.phase == VocalRemoverPhase.DECODING ||
        state.phase == VocalRemoverPhase.SEPARATING
    val failed = state.phase == VocalRemoverPhase.ERROR ||
        state.phase == VocalRemoverPhase.UNSUPPORTED_SOURCE
    var showDebug by remember { mutableStateOf(false) }

    val expansion by animateFloatAsState(
        targetValue = if (state.enabled) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(240),
        label = "vocalRemoverMorph",
    )
    val capsuleAlpha by animateFloatAsState(
        targetValue = if (state.enabled) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(180, delayMillis = if (state.enabled) 120 else 0),
        label = "vocalRemoverContent",
    )
    val discAlpha by animateFloatAsState(
        targetValue = if (state.enabled) 0.34f else 0.18f,
        label = "vocalRemoverDisc",
    )

    Box(
        modifier = Modifier
            .width(34.dp + 182.dp * expansion)
            .height(34.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = discAlpha)),
    ) {
        // The leading disc: opens the remover, and closes it once open. Its
        // face follows the pipeline: a spinner while the track is fetched,
        // decoded and separated; a warning when the source or the model said
        // no; the mic either way.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(34.dp)
                .clip(CircleShape)
                .clickable { remover.setEnabled(!state.enabled) },
            contentAlignment = Alignment.Center,
        ) {
            val iconTint = when {
                failed -> MaterialTheme.colorScheme.error
                state.enabled -> tint
                else -> tint.copy(alpha = 0.78f)
            }
            when {
                preparing -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = iconTint,
                    strokeWidth = 1.7.dp,
                )
                failed -> Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = stringResource(Res.string.vocal_remover_debug),
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
                else -> Icon(
                    imageVector = if (state.enabled) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                    contentDescription = stringResource(
                        if (state.enabled) Res.string.vocal_remover_disable
                        else Res.string.vocal_remover_enable,
                    ),
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // The capsule's body, revealed as the pill opens: vocal level from
        // fully removed (left) to mixed back in (right), then the temporary
        // debug toggle.
        if (expansion > 0.02f) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .alpha(capsuleAlpha)
                    .padding(end = 8.dp)
                    .width(182.dp * expansion),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    value = state.vocalLevel,
                    onValueChange = { remover.setVocalLevel(it) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = tint,
                        activeTrackColor = tint,
                        inactiveTrackColor = tint.copy(alpha = 0.35f),
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp),
                )
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable { showDebug = !showDebug },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = stringResource(Res.string.vocal_remover_debug),
                        tint = tint.copy(alpha = if (showDebug) 1f else 0.6f),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }

        if (showDebug) {
            val rise = with(LocalDensity.current) { 42.dp.toPx() }.toInt()
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, -rise),
                onDismissRequest = { showDebug = false },
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 340.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                        .padding(10.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .heightIn(max = 300.dp),
                    ) {
                        Text(
                            text = vocalRemoverDebugText(state),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** DEBUG - TEMPORARY, remove before release polish (docs/VOCAL_REMOVER.md). */
private fun vocalRemoverDebugText(state: VocalRemoverState): String {
    val d = state.debug
    return buildString {
        appendLine("DEBUG - TEMPORARY")
        appendLine("enabled=${state.enabled} phase=${state.phase} fraction=${"%.2f".format(state.fraction)}")
        appendLine("error=${state.error}")
        appendLine("track=${d.videoId} session=${d.sessionUuid.take(8)}")
        appendLine("model=${d.modelId} sha=${d.modelSha.take(12)}")
        appendLine("threads setting=${d.threadsSetting} active=${d.workerThreads} deviceCores=${d.deviceCores}")
        appendLine("backend=${d.backend} fallback=${d.fallback}")
        appendLine("frames start=${d.startFrame} pcm=${d.pcmFrames} available=${d.availableFrames}")
        appendLine("playhead=${d.playheadFrame} expected=${d.expectedFrame} lead=${d.leadMs}ms drift=${d.driftMs}ms")
        appendLine("chunks=${d.chunkCount} last=${d.lastChunkMs}ms avg=${d.avgChunkMs}ms stalls=${d.stallingTicks} restarts=${d.restartCount}")
        appendLine("stems total=${state.totalStemMs}ms available=${state.availableStemMs}ms playhead=${state.playheadMs}ms")
        appendLine("vocalLevel=${"%.2f".format(state.vocalLevel)} streamBytes=${d.streamBytes}")
        appendLine("phaseStartedAt=${d.phaseStartedAt}")
        append("paths=${d.paths}")
    }
}
