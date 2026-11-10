package moe.rukamori.archivetune.ui.component

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playback.dsp.AudioEngineRouterProcessor
import moe.rukamori.archivetune.playback.dsp.BitPerfectRuntime
import moe.rukamori.archivetune.playback.dsp.EngineRuntime
import java.util.Locale

data class LiveAudioChainLabels(
    val hasSignal: Boolean,
    val inputBits: String,
    val inputRate: String,
    val stage: String,
    val outputBits: String,
    val outputRate: String,
    val route: String,
    val statusLine: String?,
    val outputIsBitPerfect: Boolean,
)

@Composable
fun rememberLiveAudioChainLabels(): LiveAudioChainLabels {
    val status = BitPerfectRuntime.status
    val context = LocalContext.current

    val runtime = EngineRuntime
    val engine = runtime.activeEngineState
    val wantedEngine = runtime.wantedEngineState
    val tryptifyAvailable = runtime.tryptifyAvailableState
    val lastwaveAvailable = runtime.lastwaveAvailableState
    val revision = runtime.revision

    var pollTick by remember { mutableIntStateOf(0) }
    var routedLabelValue by remember { mutableStateOf("Android Mixer") }
    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) {
                routedLabelValue = readRoutedOutputLabel(context)
            }
            pollTick++
            delay(1_000L)
        }
    }
    val routedLabel = routedLabelValue
    val usbExclusive = remember(revision, pollTick) { runtime.usbExclusiveActive }
    val tryptifyPinActive = remember(revision, pollTick) { runtime.tryptifyUsbPinActive }
    val usbRateHz = remember(revision, pollTick) {
        runtime.lastwaveUsbRateHz.takeIf { it > 0 }
            ?: runtime.tryptifyUsbStream?.sampleRateHz?.takeIf { it > 0 }
    }
    val usbBits = remember(revision, pollTick) {
        runtime.lastwaveUsbBitsPerSample.takeIf { it > 0 }
            ?: runtime.tryptifyUsbStream?.bitsPerSample?.takeIf { it > 0 }
    }
    val floatRouteActive = remember(revision, pollTick) { runtime.bitPerfectSinkRouteActive }
    val sinkDecodedEncoding = remember(revision, pollTick) { runtime.sinkDecodedEncoding }

    val mixerConversionPossible =
        BitPerfectRuntime.requested &&
            !status.mixerBitPerfectActive &&
            !status.usbExclusiveActive &&
            !status.directPlaybackSupported

    val hasSignal = status.sourceSampleRate > 0
    val floatPcmLabel = stringResource(R.string.live_audio_chain_float_pcm)
    val lossyLabel = stringResource(R.string.live_audio_chain_lossy)
    val floatDepth = status.sourceBitDepth.takeIf { it > 0 } ?: 32
    val floatWithDepthLabel = stringResource(R.string.live_audio_chain_float_with_depth, floatDepth)
    val sinkOutputRateLabel = status.outputSampleRate.takeIf { it > 0 }?.let(::rateKhz)
    val inputBits =
        when {
            status.sourceIsLossy -> lossyLabel
            status.sourceEncoding == C.ENCODING_PCM_FLOAT -> floatPcmLabel
            else -> "${status.sourceBitDepth}-bit"
        }
    val inputRate = if (hasSignal) rateKhz(status.sourceSampleRate) else "—"

    val effectiveEngine =
        when {
            engine != AudioEngineRouterProcessor.Engine.NONE -> engine
            wantedEngine == AudioEngineRouterProcessor.Engine.TRYPTIFY && tryptifyAvailable ->
                AudioEngineRouterProcessor.Engine.TRYPTIFY
            wantedEngine == AudioEngineRouterProcessor.Engine.LASTWAVE && lastwaveAvailable ->
                AudioEngineRouterProcessor.Engine.LASTWAVE
            else -> AudioEngineRouterProcessor.Engine.NONE
        }
    val stage = when {
        effectiveEngine == AudioEngineRouterProcessor.Engine.TRYPTIFY -> "TRYPTIFY DSP"
        effectiveEngine == AudioEngineRouterProcessor.Engine.LASTWAVE -> "LASTWAVE DSP"
        usbExclusive || status.verifiedBitPerfect -> stringResource(R.string.live_audio_chain_direct_hal)
        else -> stringResource(R.string.live_audio_chain_android_mixer)
    }

    val outputBits: String
    val outputRate: String
    var outputIsBitPerfect = false
    when {
        usbExclusive && usbBits != null && usbRateHz != null -> {
            outputBits = "$usbBits-bit"
            outputRate = rateKhz(usbRateHz)
            outputIsBitPerfect = status.verifiedBitPerfect
        }

        status.verifiedBitPerfect -> {
            outputBits = "${status.outputBitDepth}-bit"
            outputRate =
                if (status.outputSampleRate > 0) rateKhz(status.outputSampleRate) else inputRate
            outputIsBitPerfect = true
        }

        floatRouteActive && sinkDecodedEncoding == C.ENCODING_PCM_FLOAT -> {
            outputBits = floatWithDepthLabel
            outputRate = sinkOutputRateLabel ?: inputRate
        }

        hasSignal -> {
            outputBits = "${status.outputBitDepth}-bit"

            outputRate = sinkOutputRateLabel ?: inputRate
        }

        else -> {
            outputBits = "—"
            outputRate = "—"
        }
    }

    val route = when {
        usbExclusive -> stringResource(R.string.live_audio_chain_usb_exclusive)
        tryptifyPinActive -> stringResource(R.string.live_audio_chain_usb_framework)
        else -> routedLabel
    }

    val engineNote = stringResource(R.string.live_audio_chain_engine_16bit_note)
    val statusLine = with(status) {
        when {
            usbExclusiveActive && verifiedBitPerfect && nativeRateMatched ->
                "Bit-Perfect • ${sourceBitDepth.takeIf { it > 0 } ?: decodedBitDepth}-bit • ${rateKhz(sourceSampleRate)}"
            verifiedBitPerfect && nativeRateMatched ->
                "Bit-Perfect • ${sourceBitDepth.takeIf { it > 0 } ?: decodedBitDepth}-bit • ${rateKhz(sourceSampleRate)}"
            verifiedBitPerfect ->
                "Native Rate • ${sourceBitDepth.takeIf { it > 0 } ?: decodedBitDepth}-bit • ${rateKhz(sourceSampleRate)}"
            mixerBitPerfectActive && sourceSampleRate > 0 ->
                "Bit-Perfect mixer • ${outputBitDepth}-bit • ${rateKhz(outputSampleRate)}"
            floatRouteActive && sinkDecodedEncoding == C.ENCODING_PCM_FLOAT && sourceBitDepth > 16 ->
                "Float route • ${sourceBitDepth}-bit depth into the Android mixer" +
                    if (mixerConversionPossible) " (mixer may convert)" else ""
            floatRouteActive && sinkDecodedEncoding == C.ENCODING_PCM_FLOAT ->
                "Float route • ${floatDepth}-bit depth into the Android mixer" +
                    if (mixerConversionPossible) " (mixer may convert)" else ""
            resamplerActive && sourceSampleRate > 0 && outputSampleRate > 0 ->
                "Resampling • ${sourceBitDepth}-bit/${rateKhz(sourceSampleRate)} → ${rateKhz(outputSampleRate)}"
            dspActive && sourceBitDepth > 16 && decodedBitDepth <= 16 ->
                "${sourceBitDepth}-bit → 16-bit • $engineNote"
            failureReason != null -> failureReason
            else -> null
        }
    }

    return LiveAudioChainLabels(
        hasSignal = hasSignal,
        inputBits = inputBits,
        inputRate = inputRate,
        stage = stage,
        outputBits = outputBits,
        outputRate = outputRate,
        route = route,
        statusLine = statusLine,
        outputIsBitPerfect = outputIsBitPerfect,
    )
}

internal fun rateKhz(hz: Int): String =
    if (hz % 1000 == 0) "${hz / 1000} kHz" else {
        val k = hz / 1000.0
        if (k == k.toInt().toDouble()) "${k.toInt()} kHz" else String.format(Locale.US, "%.1f kHz", k)
    }

internal fun readRoutedOutputLabel(context: Context): String {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        ?: return "Android Mixer"
    val outputs = runCatching {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
    }.getOrDefault(emptyList())
    val orderedTypes = intArrayOf(
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
    )
    var device: AudioDeviceInfo? = null
    for (type in orderedTypes) {
        val found = outputs.firstOrNull { it.type == type }
        if (found != null) {
            device = found
            break
        }
    }
    val routed = device ?: outputs.firstOrNull() ?: return "Android Mixer"
    return when (routed.type) {
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        -> "USB Audio"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        -> "Wired"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        else -> "Speaker"
    }
}

@Composable
fun LiveAudioChainPill(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val labels = rememberLiveAudioChainLabels()
    val accent = MaterialTheme.colorScheme.primary
    val outputAccent = MaterialTheme.colorScheme.tertiary

    val liveDotAlpha by rememberInfiniteTransition(label = "liveChainDot")
        .animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_200, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "liveChainDotAlpha",
        )

    val shimmerProgress = remember { Animatable(0f) }
    LaunchedEffect(labels.hasSignal) {
        if (labels.hasSignal) {
            while (true) {
                shimmerProgress.animateTo(1f, tween(2_600, easing = EaseInOutSine))
                shimmerProgress.animateTo(0f, tween(2_600, easing = EaseInOutSine))
            }
        } else {
            shimmerProgress.snapTo(0f)
        }
    }

    val shape = RoundedCornerShape(if (compact) 20.dp else 28.dp)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .shimmerOverlay(outputAccent.copy(alpha = 0.10f), shimmerProgress)
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(outputAccent.copy(alpha = liveDotAlpha)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.live_audio_chain_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 1.1.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = labels.route,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(10.dp))

        if (!labels.hasSignal) {
            Text(
                text = stringResource(R.string.live_audio_chain_idle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChainValueColumn(
                    label = stringResource(R.string.live_audio_chain_input),
                    value = labels.inputBits,
                    secondary = labels.inputRate,
                    tint = accent,
                    modifier = Modifier.weight(1.1f),
                )
                ChainStageBadge(
                    label = labels.stage,
                    modifier = Modifier.weight(1f),
                )
                ChainValueColumn(
                    label = stringResource(R.string.live_audio_chain_output),
                    value = labels.outputBits,
                    secondary = labels.outputRate,
                    tint = if (labels.outputIsBitPerfect) outputAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1.1f),
                )
            }

            if (labels.statusLine != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = labels.statusLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (labels.outputIsBitPerfect) outputAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun Modifier.shimmerOverlay(
    color: Color,
    progress: Animatable<Float, AnimationVector1D>,
): Modifier =
    drawBehind {
        val fraction = progress.value
        if (fraction <= 0f) return@drawBehind
        val bandWidth = size.width * 0.35f
        val x0 = -bandWidth + (size.width + 2 * bandWidth) * fraction
        if (x0 < size.width && x0 + bandWidth > 0f) {
            drawRect(
                brush =
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, color, Color.Transparent),
                        start = androidx.compose.ui.geometry.Offset(x0, 0f),
                        end = androidx.compose.ui.geometry.Offset(x0 + bandWidth, size.height),
                    ),
            )
        }
    }

@Composable
private fun ChainValueColumn(
    label: String,
    value: String,
    secondary: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        if (secondary.isNotBlank() && secondary != "—") {
            Text(
                text = secondary,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        } else {
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun ChainStageBadge(
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.graphic_eq),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
