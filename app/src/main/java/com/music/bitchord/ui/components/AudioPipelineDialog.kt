package com.music.bitchord.ui.components

import android.media.AudioFormat
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.AudioOutputStatus
import com.music.bitchord.playback.SpatialEffect
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import java.util.Locale

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
    spatialEffect: String?,
): String = when {
    loudnessActive -> "No — loudness normalization"
    eqActive -> "No — equalizer"
    spatialEffect == SpatialEffect.SPATIALIZE -> "No — Stereo Spatialization"
    spatialEffect != null -> "No — stereo widening"
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
) {
    val nerdStats by NerdStats.current.collectAsStateWithLifecycle()
    val outputStatus by AudioOutputStatus.current.collectAsStateWithLifecycle()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()

    val eqEnabled by AppSettings.equalizerEnabled.collectAsStateWithLifecycle()
    val eqPreset by AppSettings.equalizerPreset.collectAsStateWithLifecycle()
    val spatialAudio by AppSettings.spatialAudio.collectAsStateWithLifecycle()
    val loudnessNormalization by AppSettings.loudnessNormalization.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
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
                Column {
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
                        icon = Icons.AutoMirrored.Rounded.InsertDriveFile,
                        title = stringResource(R.string.pipeline_track_info),
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
                        icon = Icons.Rounded.Memory,
                        title = stringResource(R.string.pipeline_decoder),
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
                        icon = Icons.Rounded.Tune,
                        title = stringResource(R.string.pipeline_resampler),
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
                    // What the spatial stage is actually doing to the samples, as the audible sink reports it, not
                    // what is switched on: it pauses for Dolby Atmos, leaves mono and multichannel files alone, and
                    // passes through untouched at rates it has no speaker responses for.
                    val stereoExpandText = when {
                        !spatialAudio -> "100%"
                        nerdStats?.isDolbyAtmos == true -> "Paused (Dolby Atmos)"
                        outputStatus.spatialEffect == SpatialEffect.SPATIALIZE -> "Spatialized 5.1"
                        outputStatus.spatialEffect != null -> "250%"
                        outputStatus.spatialBypass != null -> "100% (${outputStatus.spatialBypass})"
                        else -> "100%"
                    }
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
                        icon = Icons.Rounded.GraphicEq,
                        title = stringResource(R.string.pipeline_dsp),
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
                                spatialEffect = outputStatus.spatialEffect,
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
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        title = stringResource(R.string.pipeline_output_device),
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

/** One pipeline stage: an icon, its title, and the label/value rows under it. */
@Composable
private fun PipelineSection(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PIPELINE_ICON_TINT,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.W600,
                    letterSpacing = 0.4.sp,
                ),
                color = Color.White.copy(alpha = 0.55f),
            )
        }
        Spacer(Modifier.height(8.dp))
        content()
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

/** Hairline separator between the header and each stage, matching [AlertRule]'s weight. */
@Composable
private fun PipelineRule() {
    Box(
        Modifier
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
