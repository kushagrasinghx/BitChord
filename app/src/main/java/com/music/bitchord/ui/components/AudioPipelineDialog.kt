package com.music.bitchord.ui.components

import android.media.AudioFormat
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.ui.utils.containSheetGestures
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.AudioOutputStatus
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val PIPELINE_CARD_SHAPE = RoundedCornerShape(ALERT_CORNER)
private val PIPELINE_SCRIM_COLOR = Color.Black.copy(alpha = 0.4f)
private val PIPELINE_WIDTH = 320.dp
private val PIPELINE_CONTENT_MAX_HEIGHT = 420.dp
private val PIPELINE_ICON_TINT = Color.White.copy(alpha = 0.6f)

/**
 * Whether decoded samples are reaching the output unaltered, and what is
 * altering them when they aren't.
 *
 * Worth stating outright rather than leaving to be inferred from the rows
 * above it, because it is the one claim in this dialog a listener might act
 * on, and because the app otherwise breaks bit-exactness whenever the
 * equaliser or spatial audio is switched on without saying so anywhere.
 *
 * There are two independent ways to lose it, and the ordering names the
 * *first* one in the signal chain, which is also the first thing someone
 * would change to get it back:
 *
 * 1. A DSP stage is running. Every one of these is a filter or a gain, and
 *    none of them can run and leave the output bit-exact.
 * 2. The output encoding cannot carry the source — a 24-bit stream on a route
 *    that will not open a float track, or 32-bit integer PCM, which no route
 *    can carry. [outputExact] is the sink's own verdict on that; see
 *    `PrecisionAudioSink.publishOutputExactness`.
 *
 * This used to be gated behind a Bit-perfect mode that turned the DSP stages
 * off for you. The mode is gone — with every stage already idle the samples
 * were bit-exact without it, so the toggle mostly duplicated the Output
 * precision preference — but the readout is not, because "is it exact right
 * now" is worth answering whether or not there is a switch that forces it.
 */
private fun bitExactVerdict(
    outputExact: Boolean,
    outputExactDetail: String?,
    loudnessActive: Boolean,
    eqActive: Boolean,
    spatialActive: Boolean,
): String = when {
    loudnessActive -> "No — loudness normalization"
    eqActive -> "No — equalizer"
    spatialActive -> "No — spatial audio"
    outputExact -> "Yes${outputExactDetail?.let { " ($it)" }.orEmpty()}"
    else -> "No — ${outputExactDetail ?: "converted downstream"}"
}

/**
 * Full audio playback pipeline inspection surface, opened from the "Audio Pipeline"
 * row at the bottom of [com.music.bitchord.ui.player.AudioOutputSheet].
 *
 * Displays live, authoritative details for each stage in the audio pipeline:
 * Track Info -> Decoder -> Resampler -> DSP -> Output Device.
 *
 * Same frosted card shape as [LyricsSourcesDialog] and [UpdateAvailableDialog] —
 * header with a title and subtitle, hairline-separated groups, a full-width
 * closing action — fixed to this player's own dark palette rather than the
 * theme-adaptive one those settings dialogs use, since everything else on this
 * screen is drawn in [Color.White] alphas regardless of the app's light/dark theme.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun AudioPipelineDialog(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
) {
    val nerdStats by NerdStats.current.collectAsStateWithLifecycle()
    val outputStatus by AudioOutputStatus.current.collectAsStateWithLifecycle()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()

    val eqEnabled by AppSettings.equalizerEnabled.collectAsStateWithLifecycle()
    val eqPreset by AppSettings.equalizerPreset.collectAsStateWithLifecycle()
    val spatialAudio by AppSettings.spatialAudio.collectAsStateWithLifecycle()
    val loudnessNormalization by AppSettings.loudnessNormalization.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val density = LocalDensity.current
    val isReducedMotion = remember(context) {
        try {
            val resolver = context.contentResolver
            val animScale = Settings.Global.getFloat(
                resolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f,
            )
            animScale == 0f
        } catch (_: Exception) {
            false
        }
    }

    val isSignalActive = isPlaying && !isReducedMotion

    val flowAlpha by animateFloatAsState(
        targetValue = if (isSignalActive) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isSignalActive) 450 else 320,
            easing = if (isSignalActive) LinearOutSlowInEasing else FastOutLinearInEasing,
        ),
        label = "pipelineFlowAlpha",
    )

    var pulseProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isSignalActive) {
        if (!isSignalActive) {
            pulseProgress = 0f
            return@LaunchedEffect
        }
        pulseProgress = 0f
        val anim = Animatable(0f)
        while (isActive) {
            anim.snapTo(0f)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 2400,
                    easing = LinearEasing,
                ),
            ) {
                pulseProgress = value
            }
        }
    }

    var columnCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val stageCenters = remember { mutableStateMapOf<Int, Offset>() }
    val interactionRadiusPx = with(density) { 24.dp.toPx() }

    val stageActivation: (Int) -> Float = { idx ->
        calculateStageActivation(idx, pulseProgress, flowAlpha, stageCenters, interactionRadiusPx)
    }
    val onStageIconPositioned: (Int, Offset) -> Unit = { idx, center ->
        // Reported on every layout pass; only a real move should redraw.
        if (stageCenters[idx] != center) stageCenters[idx] = center
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .containSheetGestures()
            .background(PIPELINE_SCRIM_COLOR)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(PIPELINE_WIDTH)
                .clip(PIPELINE_CARD_SHAPE)
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
                    }
                )
                // Swallows the tap before it reaches the scrim behind, so
                // touching the card itself never dismisses it.
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 19.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.audio_pipeline),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.W600,
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.audio_pipeline_subtitle),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    ),
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }

            Box(
                modifier = Modifier
                    .heightIn(max = PIPELINE_CONTENT_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState()),
            ) {
                // The signal flow redraws every frame while playing. On its own
                // layer, each frame re-records just these lines rather than every
                // stage row and the frosted card they sit on.
                Spacer(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer {}
                        .drawBehind {
                            drawSignalFlow(stageCenters, flowAlpha, pulseProgress, interactionRadiusPx)
                        },
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { columnCoordinates = it },
                ) {
                    // 1. Track Info Stage
                    val sourceName = nerdStats?.sourceName ?: "—"
                    val format = NerdStats.codecLabel(nerdStats?.mimeType) ?: nerdStats?.mimeType ?: "—"
                    val bitDepth = nerdStats?.bitDepth?.let { "$it-bit" }
                        ?: nerdStats?.claimed?.bitDepth?.let { "$it-bit" }
                        ?: "—"
                    val sampleRate = nerdStats?.sampleRateHz?.let { "$it Hz" }
                        ?: nerdStats?.claimed?.sampleRateHz?.let { "$it Hz" }
                        ?: "—"
                    val bitrate = nerdStats?.bitrateKbps?.let { "$it kbps" } ?: "—"
                    val channels = when (nerdStats?.channels) {
                        1 -> stringResource(R.string.mono)
                        2 -> stringResource(R.string.stereo)
                        null -> "—"
                        else -> "${nerdStats?.channels} (Surround)"
                    }

                    PipelineRule()
                    PipelineSection(
                        stageIndex = 0,
                        icon = Icons.AutoMirrored.Rounded.InsertDriveFile,
                        title = stringResource(R.string.pipeline_track_info),
                        columnCoordinates = columnCoordinates,
                        stageActivationProvider = stageActivation,
                        onStageIconPositioned = onStageIconPositioned,
                    ) {
                        PipelineRow(stringResource(R.string.pipeline_source), sourceName)
                        PipelineRow(stringResource(R.string.pipeline_format), format)
                        PipelineRow(stringResource(R.string.pipeline_bit_depth), bitDepth)
                        PipelineRow(stringResource(R.string.pipeline_sample_rate), sampleRate)
                        PipelineRow(stringResource(R.string.pipeline_bitrate), bitrate)
                        PipelineRow(stringResource(R.string.pipeline_channels), channels)
                    }

                    // 2. Decoder Stage
                    val decoderName = outputStatus.decoderName ?: "—"

                    PipelineRule()
                    PipelineSection(
                        stageIndex = 1,
                        icon = Icons.Rounded.Memory,
                        title = stringResource(R.string.pipeline_decoder),
                        columnCoordinates = columnCoordinates,
                        stageActivationProvider = stageActivation,
                        onStageIconPositioned = onStageIconPositioned,
                    ) {
                        PipelineRow(stringResource(R.string.pipeline_decoder_name), decoderName)
                        outputStatus.decoderOutputEncoding?.let {
                            PipelineRow(stringResource(R.string.pipeline_format), it)
                        }
                    }

                    // 3. Resampler Stage
                    val inRate = nerdStats?.sampleRateHz
                    val outRate = outputStatus.actualSampleRateHz ?: inRate
                    val isPassthrough = inRate != null && outRate != null && inRate == outRate
                    val ioRateText = if (inRate != null && outRate != null) {
                        "$inRate Hz → $outRate Hz"
                    } else if (inRate != null) {
                        "$inRate Hz → —"
                    } else if (outRate != null) {
                        "— → $outRate Hz"
                    } else {
                        "—"
                    }
                    val resamplerType = when {
                        inRate == null && outRate == null -> "—"
                        isPassthrough -> "None"
                        else -> "Resampler"
                    }
                    val qualityText = when {
                        inRate == null && outRate == null -> "—"
                        isPassthrough -> "Passthrough"
                        else -> "Resampled"
                    }

                    PipelineRule()
                    PipelineSection(
                        stageIndex = 2,
                        icon = Icons.Rounded.Tune,
                        title = stringResource(R.string.pipeline_resampler),
                        columnCoordinates = columnCoordinates,
                        stageActivationProvider = stageActivation,
                        onStageIconPositioned = onStageIconPositioned,
                    ) {
                        PipelineRow(stringResource(R.string.pipeline_io_rate), ioRateText)
                        PipelineRow(stringResource(R.string.pipeline_type), resamplerType)
                        PipelineRow(stringResource(R.string.pipeline_cutoff), "—")
                        PipelineRow(stringResource(R.string.pipeline_quality), qualityText)
                    }

                    // 4. DSP Stage
                    val pcmFormat = outputStatus.dspFormat
                    val dspRate = outputStatus.actualSampleRateHz ?: nerdStats?.sampleRateHz
                    val dspRateText = if (dspRate != null) "$dspRate Hz" else "—"
                    val eqPresetText = if (eqEnabled) {
                        eqPreset.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
                    } else {
                        "Flat"
                    }
                    val stereoExpandText = if (spatialAudio) "250%" else "100%"
                    val buffersText = outputStatus.bufferSize?.let { size ->
                        val rate = outputStatus.actualSampleRateHz
                        val bytesPerSample = when (outputStatus.actualEncoding) {
                            AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
                            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                            else -> 2
                        }
                        val channelCount = nerdStats?.channels ?: 2
                        val bytesPerFrame = bytesPerSample * channelCount
                        val frames = if (bytesPerFrame > 0) size / bytesPerFrame else 0
                        if (rate != null && rate > 0 && frames > 0) {
                            val ms = (frames * 1000L) / rate
                            "2x (${ms}ms, $frames frames)"
                        } else {
                            "—"
                        }
                    } ?: "—"

                    // Loudness reads before the rest of the DSP stage because
                    // that is the order the samples meet them in — see
                    // [DspChain] on why the level correction goes first.
                    val loudnessGainText = when {
                        !loudnessNormalization -> stringResource(R.string.off)
                        outputStatus.loudnessGainDb == null -> "—"
                        else -> stringResource(
                            R.string.loudness_gain_db,
                            "%+.1f".format(Locale.ROOT, outputStatus.loudnessGainDb),
                        )
                    }
                    val loudnessMeasuredText = outputStatus.loudnessLufs?.let {
                        stringResource(R.string.loudness_lufs, "%+.1f".format(Locale.ROOT, it))
                    } ?: "—"

                    PipelineRule()
                    PipelineSection(
                        stageIndex = 3,
                        icon = Icons.Rounded.GraphicEq,
                        title = stringResource(R.string.pipeline_dsp),
                        columnCoordinates = columnCoordinates,
                        stageActivationProvider = stageActivation,
                        onStageIconPositioned = onStageIconPositioned,
                    ) {
                        PipelineRow(stringResource(R.string.pipeline_pcm_format), pcmFormat)
                        PipelineRow(stringResource(R.string.pipeline_sample_rate), dspRateText)
                        PipelineRow(stringResource(R.string.pipeline_loudness_gain), loudnessGainText)
                        PipelineRow(stringResource(R.string.pipeline_loudness_measured), loudnessMeasuredText)
                        PipelineRow(stringResource(R.string.pipeline_eq_preset), eqPresetText)
                        PipelineRow(stringResource(R.string.pipeline_stereo_expand), stereoExpandText)
                        PipelineRow(stringResource(R.string.pipeline_buffers), buffersText)
                        PipelineRow(stringResource(R.string.pipeline_output_api), outputStatus.sink.ifBlank { "AAudio" })
                        // The verdict, rather than the settings. Names whichever
                        // stage is altering samples — or, when none is, whether
                        // the route can carry the decoder's own encoding —
                        // instead of leaving the reader to infer it from four
                        // rows above.
                        PipelineRow(
                            stringResource(R.string.pipeline_bit_exact),
                            bitExactVerdict(
                                outputExact = outputStatus.outputExact,
                                outputExactDetail = outputStatus.outputExactDetail,
                                loudnessActive = loudnessNormalization && outputStatus.loudnessGainDb != null,
                                eqActive = eqEnabled,
                                spatialActive = spatialAudio,
                            ),
                        )
                    }

                    // 5. Output Device Stage
                    val deviceName = outputStatus.deviceName.ifBlank { "System default" }
                    val audioTrackEncoding = when (outputStatus.actualEncoding) {
                        AudioFormat.ENCODING_PCM_FLOAT -> "Float32"
                        AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM24"
                        AudioFormat.ENCODING_PCM_32BIT -> "PCM32"
                        AudioFormat.ENCODING_PCM_16BIT -> "PCM16"
                        else -> "Float32"
                    }
                    val audioTrackRate = outputStatus.actualSampleRateHz ?: nerdStats?.sampleRateHz ?: 48000
                    val audioTrackText = "$audioTrackEncoding / $audioTrackRate Hz"

                    PipelineRule()
                    PipelineSection(
                        stageIndex = 4,
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        title = stringResource(R.string.pipeline_output_device),
                        columnCoordinates = columnCoordinates,
                        stageActivationProvider = stageActivation,
                        onStageIconPositioned = onStageIconPositioned,
                    ) {
                        PipelineRow(stringResource(R.string.pipeline_device_name), deviceName)
                        PipelineRow("Route", outputStatus.routeKind.name)
                        PipelineRow("Transport", outputStatus.transportType.label)

                        val directStatusText = when {
                            outputStatus.transportType == com.music.bitchord.playback.audio.TransportType.DIRECT_USB ->
                                "Active (Direct Userspace USB)"
                            outputStatus.directPlaybackActual ->
                                "Active (Direct AudioTrack, Bypasses Mixer)"
                            outputStatus.directPlaybackRejected ->
                                "Rejected"
                            outputStatus.directPlaybackSupported ->
                                "Supported (Framework Mixed)"
                            else ->
                                "Not Supported (Mixed Path)"
                        }
                        PipelineRow("Direct", directStatusText)
                        PipelineRow("AudioTrack", audioTrackText)

                        val mixerText = when {
                            outputStatus.transportType == com.music.bitchord.playback.audio.TransportType.DIRECT_USB ->
                                "Direct (Bypasses System Mixer)"
                            outputStatus.directPlaybackActual ->
                                "Direct path active; endpoint format not independently verified"
                            outputStatus.systemMixerRateHz != null -> {
                                val hal = outputStatus.halFormat
                                if (hal != null) {
                                    "AudioFlinger Mixer ${outputStatus.systemMixerRateHz} Hz, HAL $hal"
                                } else {
                                    "AudioFlinger Mixer ${outputStatus.systemMixerRateHz} Hz"
                                }
                            }
                            else -> null
                        }
                        mixerText?.let {
                            PipelineRow("System", it)
                        }

                        outputStatus.usbEndpointFormat?.let {
                            PipelineRow("USB Device Capability", formatUsbCapability(it))
                            PipelineNote("Reported by Android for the connected USB device. This describes device capabilities and may differ from the active playback format.")
                        }

                        if (outputStatus.routeKind == com.music.bitchord.playback.AudioRouting.Kind.BLUETOOTH) {
                            val bt = outputStatus.bluetoothTelemetry
                            outputStatus.bluetoothProfile?.let { PipelineRow("Bluetooth", it) }
                            if (bt != null && bt.isConnected) {
                                PipelineRow("Codec", if (bt.hasNamedCodec) bt.codecName else "System Managed")
                                bt.bitDepth?.let { PipelineRow("Codec Bits", "$it-bit") }
                                bt.sampleRateHz?.let { PipelineRow("Codec Sample Rate", "$it Hz") }
                                PipelineRow("Codec Bitrate", bt.bitrateLabel)
                                bt.mode?.let { PipelineRow("Codec Mode", it) }
                            } else {
                                PipelineRow("Codec", "System Managed")
                            }
                        }

                        if (outputStatus.fallbackReason != com.music.bitchord.playback.audio.FallbackReason.NONE) {
                            val fallbackText = outputStatus.fallbackDetail ?: outputStatus.fallbackReason.label
                            PipelineRow("Fallback", fallbackText)
                        }
                    }
                }
            }

            PipelineRule()
            PipelineDoneAction(label = stringResource(R.string.done), onClick = onDismiss)
        }
    }
}

/** The signal bus, the travelling pulse and the node rings behind the five stage icons. */
private fun DrawScope.drawSignalFlow(
    stageCenters: Map<Int, Offset>,
    flowAlpha: Float,
    pulseProgress: Float,
    interactionRadiusPx: Float,
) {
    if (stageCenters.size < 5) return

    val p0 = stageCenters[0] ?: return
    val p1 = stageCenters[1] ?: return
    val p2 = stageCenters[2] ?: return
    val p3 = stageCenters[3] ?: return
    val p4 = stageCenters[4] ?: return

    val xCenter = p0.x
    val y0 = p0.y
    val y4 = p4.y
    val totalHeight = y4 - y0
    if (totalHeight <= 0f) return

    val currentFlowAlpha = flowAlpha
    val currentProgress = pulseProgress
    val stagePoints = listOf(p0, p1, p2, p3, p4)
    val nodeRadius = 11.5.dp.toPx()

    // 1. Static Node-to-Node Signal Bus Segments (~2.dp, butt caps at node rims)
    val busAlpha = if (currentFlowAlpha > 0f) 0.22f + 0.06f * currentFlowAlpha else 0.18f
    val busStroke = 2.dp.toPx()

    for (i in 0 until stagePoints.size - 1) {
        val yStart = stagePoints[i].y + nodeRadius
        val yEnd = stagePoints[i + 1].y - nodeRadius
        if (yEnd > yStart) {
            drawLine(
                color = Color.White.copy(alpha = busAlpha),
                start = Offset(xCenter, yStart),
                end = Offset(xCenter, yEnd),
                strokeWidth = busStroke,
                cap = StrokeCap.Butt,
            )

            // Subtle ambient under-glow along the bus when powered
            if (currentFlowAlpha > 0f) {
                drawLine(
                    color = Color.White.copy(alpha = 0.05f * currentFlowAlpha),
                    start = Offset(xCenter, yStart),
                    end = Offset(xCenter, yEnd),
                    strokeWidth = 4.5.dp.toPx(),
                    cap = StrokeCap.Butt,
                )
            }
        }
    }

    // 2. Dynamic Travelling Signal Pulse along Segments (When Playing)
    if (currentFlowAlpha > 0f && currentProgress <= 0.95f) {
        val pulseVisibility = if (currentProgress <= 0.85f) {
            1f
        } else {
            (1f - (currentProgress - 0.85f) / 0.10f).coerceIn(0f, 1f)
        } * currentFlowAlpha

        if (pulseVisibility > 0.01f) {
            val s = (currentProgress / 0.85f).coerceIn(0f, 1f)
            val yPulse = y0 + s * totalHeight

            val tailLength = 38.dp.toPx()
            val leadLength = 8.dp.toPx()

            for (i in 0 until stagePoints.size - 1) {
                val yStart = stagePoints[i].y + nodeRadius
                val yEnd = stagePoints[i + 1].y - nodeRadius
                if (yEnd <= yStart) continue

                val pStart = max(yStart, yPulse - tailLength)
                val pEnd = min(yEnd, yPulse + leadLength)

                if (pEnd > pStart) {
                    // A. Soft Halo Line along Segment
                    val haloBrush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.60f to Color.White.copy(alpha = 0.14f * pulseVisibility),
                            0.88f to Color.White.copy(alpha = 0.28f * pulseVisibility),
                            1.0f to Color.Transparent,
                        ),
                        startY = yPulse - tailLength,
                        endY = yPulse + leadLength,
                    )
                    drawLine(
                        brush = haloBrush,
                        start = Offset(xCenter, pStart),
                        end = Offset(xCenter, pEnd),
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Butt,
                    )

                    // B. Brilliant Core Energy Line along Segment
                    val coreBrush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.50f to Color.White.copy(alpha = 0.45f * pulseVisibility),
                            0.86f to Color.White.copy(alpha = 0.98f * pulseVisibility),
                            1.0f to Color.Transparent,
                        ),
                        startY = yPulse - tailLength,
                        endY = yPulse + leadLength,
                    )
                    drawLine(
                        brush = coreBrush,
                        start = Offset(xCenter, pStart),
                        end = Offset(xCenter, pEnd),
                        strokeWidth = 2.25.dp.toPx(),
                        cap = StrokeCap.Butt,
                    )

                    // C. Leading Micro-Photon Pip (when pulse head is on this segment)
                    if (yPulse in yStart..yEnd) {
                        val headCenter = Offset(xCenter, yPulse)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.35f * pulseVisibility),
                                    Color.Transparent,
                                ),
                                center = headCenter,
                                radius = 7.dp.toPx(),
                            ),
                            radius = 7.dp.toPx(),
                            center = headCenter,
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.98f * pulseVisibility),
                            radius = 1.75.dp.toPx(),
                            center = headCenter,
                        )
                    }
                }
            }
        }
    }

    // 3. Precision Node Anchors & White Outline Glow at Each Stage
    for (i in stagePoints.indices) {
        val stageCenter = Offset(xCenter, stagePoints[i].y)
        val act = calculateStageActivation(i, currentProgress, currentFlowAlpha, stageCenters, interactionRadiusPx)

        // A. Dark Backing Base (Seamless with card, guarantees zero bleed into icon interior)
        drawCircle(
            color = Color(0xFF131315),
            radius = nodeRadius,
            center = stageCenter,
        )

        // B. Subtle White Ambient Halo (Restrained when idle, blooms gently when active)
        val haloAlpha = 0.10f + 0.24f * act * currentFlowAlpha
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to Color.White.copy(alpha = haloAlpha * 0.40f),
                    0.45f to Color.White.copy(alpha = haloAlpha),
                    1.0f to Color.Transparent,
                ),
                center = stageCenter,
                radius = nodeRadius + 5.dp.toPx(),
            ),
            radius = nodeRadius + 5.dp.toPx(),
            center = stageCenter,
        )

        // C. Active Outer Luminous Flare Ring (When pulse arrives)
        if (currentFlowAlpha > 0f && act > 0.02f) {
            drawCircle(
                color = Color.White.copy(alpha = 0.20f * act * currentFlowAlpha),
                radius = nodeRadius + 1.2.dp.toPx(),
                center = stageCenter,
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        // D. Precision White Outline Micro-Ring (Crisp hardware boundary)
        val ringAlpha = (0.35f + 0.55f * act * currentFlowAlpha).coerceIn(0f, 1f)
        drawCircle(
            color = Color.White.copy(alpha = ringAlpha),
            radius = nodeRadius,
            center = stageCenter,
            style = Stroke(width = 1.dp.toPx()),
        )

        // E. High-Luminance Core Edge (When peak active)
        if (currentFlowAlpha > 0f && act > 0.05f) {
            drawCircle(
                color = Color.White.copy(alpha = 0.60f * act * currentFlowAlpha),
                radius = nodeRadius,
                center = stageCenter,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
    }
}

private fun calculateStageActivation(
    stageIndex: Int,
    progress: Float,
    flowAlpha: Float,
    stageCenters: Map<Int, Offset>,
    interactionRadiusPx: Float,
): Float {
    if (flowAlpha <= 0.001f || stageCenters.size < 5) return 0f
    val p0 = stageCenters[0] ?: return 0f
    val p4 = stageCenters[4] ?: return 0f
    val totalHeight = p4.y - p0.y
    if (totalHeight <= 0f) return 0f

    val targetY = stageCenters[stageIndex]?.y ?: return 0f

    val yPulse = if (progress <= 0.85f) {
        val s = (progress / 0.85f).coerceIn(0f, 1f)
        p0.y + s * totalHeight
    } else {
        p4.y
    }

    val dy = abs(yPulse - targetY)
    if (dy > interactionRadiusPx) return 0f

    val p = 1f - (dy / interactionRadiusPx)
    val raw = (0.5f - 0.5f * kotlin.math.cos(p * Math.PI.toFloat())).coerceIn(0f, 1f)

    val absorption = if (stageIndex == 4 && progress > 0.85f) {
        if (progress >= 0.95f) 0f else 1f - ((progress - 0.85f) / 0.10f)
    } else 1f

    return (raw * absorption * flowAlpha).coerceIn(0f, 1f)
}

/** One pipeline stage: an icon, its title, and the label/value rows under it. */
@Composable
private fun PipelineSection(
    stageIndex: Int,
    icon: ImageVector,
    title: String,
    columnCoordinates: LayoutCoordinates?,
    stageActivationProvider: (Int) -> Float,
    onStageIconPositioned: (Int, Offset) -> Unit,
    content: @Composable () -> Unit,
) {
    var iconCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    fun reportCenter(child: LayoutCoordinates?, parent: LayoutCoordinates?) {
        if (child == null || parent == null || !child.isAttached || !parent.isAttached) return
        val center = parent.localPositionOf(
            child,
            Offset(child.size.width / 2f, child.size.height / 2f),
        )
        onStageIconPositioned(stageIndex, center)
    }

    // Covers the first pass, when the column may not have reported yet.
    LaunchedEffect(iconCoordinates, columnCoordinates) {
        reportCenter(iconCoordinates, columnCoordinates)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        // Left Column: Dedicated Icon / Signal Node lane (icon size strictly preserved at 15dp)
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(15.dp)
                    .onGloballyPositioned { coords ->
                        iconCoordinates = coords
                        // The coordinates object never changes, so the effect
                        // above only runs once. A section growing when stats
                        // arrive moves every icon below it; track that here.
                        reportCenter(coords, columnCoordinates)
                    }
                    .graphicsLayer {
                        val act = stageActivationProvider(stageIndex)
                        alpha = (0.80f + 0.20f * act).coerceIn(0f, 1f)
                    },
            )
        }

        Spacer(Modifier.width(14.dp))

        // Right Column: Dedicated Information lane (Title + Telemetry Rows)
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.W600,
                    letterSpacing = 0.5.sp,
                ),
                color = Color.White,
                modifier = Modifier
                    .padding(top = 3.dp)
                    .graphicsLayer {
                        val act = stageActivationProvider(stageIndex)
                        alpha = (0.65f + 0.32f * act).coerceIn(0f, 1f)
                    },
            )
            Spacer(Modifier.height(7.dp))
            content()
        }
    }
}

@Composable
private fun PipelineRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
    ) {
        val text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Color.White)) {
                append("$label: ")
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = Color.White.copy(alpha = 0.82f))) {
                append(value)
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
            ),
        )
    }
}

/** Hairline separator between stages, indented to align with the text column and clear the signal lane. */
@Composable
private fun PipelineRule(modifier: Modifier = Modifier.padding(start = 52.dp, end = 16.dp)) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Color.White.copy(alpha = 0.14f)),
    )
}

/** Full-bleed closing action, [AlertAction]'s shape fixed to this screen's white-on-dark palette. */
@Composable
private fun PipelineDoneAction(label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ACTION_HEIGHT)
            .background(
                if (pressed) Color.White.copy(alpha = 0.09f) else Color.Transparent,
            )
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.W600,
            ),
            color = Color.White,
        )
    }
}

@Composable
private fun PipelineNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = Color.White.copy(alpha = 0.60f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 1.dp, bottom = 4.dp),
    )
}

internal fun formatUsbCapability(raw: String): String {
    var formatted = raw
        .replace("PCM32", "PCM 32-bit")
        .replace("PCM24", "PCM 24-bit")
        .replace("PCM16", "PCM 16-bit")
        .replace("Float32", "Float 32-bit")

    val hzRegex = Regex("""(\d+)\s*Hz""")
    formatted = hzRegex.replace(formatted) { matchResult ->
        val hz = matchResult.groupValues[1].toIntOrNull()
            ?: return@replace matchResult.value
        "${"%.1f".format(Locale.ROOT, hz / 1000f).removeSuffix(".0")} kHz"
    }
    return formatted
}
