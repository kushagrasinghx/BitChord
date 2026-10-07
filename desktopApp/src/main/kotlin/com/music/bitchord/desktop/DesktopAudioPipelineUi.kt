package com.music.bitchord.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The signal chain behind what is playing, stage by stage: the stream that arrived, the decoder,
 * whether anything is being resampled, the processing on top, and the device it lands on.
 *
 * Android opens the same readout from the quality badge — see its `AudioPipelineDialog`.
 */
@Composable
internal fun DesktopAudioPipelineDialog(
    format: DesktopStreamFormat?,
    sourceName: String?,
    pipeline: DesktopAudioPipeline,
    onDismiss: () -> Unit,
) {
    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 460) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                DesktopStrings["audio_pipeline", "Audio pipeline"],
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            // The engine's own copy, so Source and Decoder cannot disagree while an upgrade lands.
            val source = pipeline.sourceFormat ?: format
            PipelineStage(DesktopStrings["pipeline_source", "Source"]) {
                PipelineRow(DesktopStrings["pipeline_format", "Format"], source?.codecLabel)
                PipelineRow(DesktopStrings["pipeline_bitrate", "Bitrate"], source?.kbps?.let { "$it kbps" })
                PipelineRow(
                    DesktopStrings["pipeline_sample_rate", "Sample rate"],
                    source?.sampleRateHz?.let { "${it / 1000.0} kHz" },
                )
                PipelineRow(DesktopStrings["pipeline_bit_depth", "Bit depth"], source?.bitDepth?.let { "$it-bit" })
                PipelineRow(DesktopStrings["pipeline_channels", "Channels"], channelLabel(source?.channels))
                PipelineRow(DesktopStrings["source", "Source"], sourceName)
            }
            PipelineStage(DesktopStrings["pipeline_decoder", "Decoder"]) {
                PipelineRow(DesktopStrings["pipeline_decoder_name", "Decoder"], pipeline.decoderLabel)
                PipelineRow(
                    DesktopStrings["pipeline_io_rate", "Decoded rate"],
                    pipeline.decodedSampleRateHz?.let { "${it / 1000.0} kHz" },
                )
                PipelineRow(DesktopStrings["pipeline_channels", "Channels"], channelLabel(pipeline.decodedChannels))
            }
            PipelineStage(DesktopStrings["pipeline_resampler", "Resampler"]) {
                PipelineRow(
                    DesktopStrings["pipeline_resampler", "Resampler"],
                    if (pipeline.resampling) {
                        "${pipeline.decodedSampleRateHz?.div(1000.0)} kHz → ${pipeline.outputSampleRateHz?.div(1000.0)} kHz"
                    } else {
                        DesktopStrings["d_bit_perfect", "Bit-perfect · no conversion"]
                    },
                )
            }
            PipelineStage(DesktopStrings["pipeline_dsp", "DSP"]) {
                PipelineRow(
                    DesktopStrings["equalizer", "Equalizer"],
                    if (pipeline.equalizerEnabled) DesktopStrings["on", "On"] else DesktopStrings["off", "Off"],
                )
                PipelineRow(
                    DesktopStrings["skip_silence", "Skip silence"],
                    if (pipeline.skipSilence) DesktopStrings["on", "On"] else DesktopStrings["off", "Off"],
                )
            }
            PipelineStage(DesktopStrings["pipeline_output_device", "Output"]) {
                PipelineRow(DesktopStrings["pipeline_output_api", "Output API"], "javax.sound")
                PipelineRow(DesktopStrings["pipeline_device_name", "Device"], pipeline.deviceName)
                PipelineRow(
                    DesktopStrings["pipeline_pcm_format", "PCM format"],
                    pipeline.outputBytesPerSample?.let {
                        if (pipeline.outputIsFloat == true) "32-bit float" else "${it * 8}-bit"
                    },
                )
                PipelineRow(
                    DesktopStrings["pipeline_sample_rate", "Sample rate"],
                    pipeline.outputSampleRateHz?.let { "${it / 1000.0} kHz" },
                )
                PipelineRow(DesktopStrings["pipeline_channels", "Channels"], channelLabel(pipeline.outputChannels))
                PipelineRow(
                    DesktopStrings["pipeline_buffers", "Buffer"],
                    pipeline.bufferBytes.takeIf { it > 0 }?.let { "${it / 1024} KB" },
                )
            }
        }
    }
}

@Composable
private fun PipelineStage(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title.uppercase(),
            color = DesktopSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Column(Modifier.desktopCardInset(RoundedCornerShape(10.dp)).padding(vertical = 4.dp)) { content() }
    }
}

/** One measurement. Nothing the engine cannot answer is listed at all, rather than shown as "—". */
@Composable
private fun PipelineRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // The label keeps its room: a long value used to squeeze it to one character per line.
        Text(
            label,
            color = DesktopSecondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.9f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.1f),
        )
    }
}

private fun channelLabel(channels: Int?): String? = when (channels) {
    null -> null
    1 -> DesktopStrings["mono", "Mono"]
    2 -> DesktopStrings["stereo", "Stereo"]
    else -> "$channels ch"
}
