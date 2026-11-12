package com.music.bitchord.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Earbuds
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.sharedui.resources.*
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Where the music is coming out, and how loud.
 *
 * Replaces two things that did not work. On Android 14 and up the headphones
 * glyph opened the *system* output switcher — a panel this app does not control,
 * styled like nothing else here, and one that routes the whole phone rather than
 * this player. Below that it opened a stock [android.app.AlertDialog] driven by
 * [android.media.MediaRouter], whose `selectRoute` is a request the framework is
 * free to ignore, and usually did.
 *
 * This one switches by telling our own player which sink to render into, which
 * is the only routing decision the app owns and the one that actually takes
 * effect — see [AudioRouting].
 *
 * A [PlayerDrawer] off the bottom edge, as the rest of the app's sheets are.
 * Outputs come first and volume follows. Every available device stays visible:
 * on a phone there are usually two, so a disclosure would cost a tap merely to
 * reveal a single row.
 */
@Composable
internal fun AudioOutputSheet(
    hazeState: HazeState,
    accountName: String?,
    onDismiss: () -> Unit,
    onOpenPipeline: () -> Unit,
    onOpenCast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outputs = rememberAudioOutputs()
    val cast by PlayerPlatform.host.castState.collectAsStateWithLifecycle()
    val watch by PlayerPlatform.host.watchOutput.collectAsStateWithLifecycle()
    val connect by PlayerPlatform.host.connectUi.collectAsStateWithLifecycle()

    // A watch or connect device comes and goes; ask again each time the drawer opens.
    LaunchedEffect(Unit) {
        PlayerPlatform.host.refreshWatchOutput()
        PlayerPlatform.host.refreshConnectDevices()
    }

    PlayerDrawer(
        hazeState = hazeState,
        title = stringResource(Res.string.audio_output),
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Discovered BitChord Connect TVs / Remote Devices (Spotify Connect style)
            if (connect.devices.isNotEmpty()) {
                Text(
                    text = "CONNECT TO A DEVICE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 11.sp,
                    ),
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                )
                connect.devices.forEach { device ->
                    ConnectDeviceRow(
                        device = device,
                        onSelect = { PlayerPlatform.host.selectConnectDevice(device.id) },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "THIS PHONE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 11.sp,
                    ),
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                )
            }

            outputs.forEach { device ->
                OutputRow(
                    device = device,
                    accountName = accountName,
                    onSelect = {
                        if (connect.isConnected) {
                            PlayerPlatform.host.disconnectConnectDevice(resumeHere = true)
                        }
                        PlayerPlatform.host.selectAudioOutput(device.id)
                    },
                )
            }
            // One more place to send the music, listed with the others.
            if (watch.available || watch.active) {
                WatchRow(watch = watch, onClick = { PlayerPlatform.host.selectWatchOutput() })
            }
            if (cast.supported) CastRow(cast = cast, onClick = onOpenCast)
        }

        Spacer(Modifier.height(10.dp))
        VolumeRow(
            routeKey = listOf(outputs, connect.activeDeviceName),
            isConnectActive = connect.isConnected,
        )

        Spacer(Modifier.height(6.dp))
        AudioPipelineRow(onClick = onOpenPipeline)
    }
}

/**
 * Drills into [com.music.bitchord.ui.components.AudioPipelineDialog] — the
 * subtitle is the negotiated output itself, read live off
 * [AudioOutputStatus], so the row states what's actually leaving the phone
 * before anyone taps in for the rest of the chain.
 */
@Composable
private fun AudioPipelineRow(onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val outputFormat by PlayerPlatform.host.outputFormat.collectAsStateWithLifecycle()
    val subtitle = outputFormat.summary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ROW_SHAPE)
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.play(Haptic.Select)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.audio_pipeline),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Opens the receiver picker. Lit like the playing output while the music is on
 * a receiver, with that receiver's name in place of the generic title — at that
 * point it *is* the output, and the rows above it are the way back.
 */
@Composable
private fun CastRow(cast: CastUi, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val casting = cast.connectedName != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ROW_SHAPE)
            .background(Color.White.copy(alpha = if (casting) 0.10f else 0.05f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.play(Haptic.Select)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (casting) 0.16f else 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (casting) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (casting) 1f else 0.7f),
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = cast.connectedName ?: stringResource(Res.string.cast),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (casting) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = Color.White.copy(alpha = if (casting) 1f else 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(
                    when {
                        cast.connecting -> Res.string.cast_connecting
                        casting -> Res.string.cast_casting
                        else -> Res.string.cast_row_subtitle
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * A discovered BitChord Connect television or remote speaker (Spotify Connect style).
 * Highlights with vibrant emerald/Spotify green glow and animated equalizers when active.
 */
@Composable
private fun ConnectDeviceRow(
    device: ConnectDeviceUi,
    onSelect: () -> Unit,
) {
    val haptics = rememberHaptics()
    val active = device.active
    val spotifyGreen = Color(0xFF1DB954)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ROW_SHAPE)
            .background(if (active) spotifyGreen.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f))
            .then(
                if (active) Modifier.border(1.dp, spotifyGreen.copy(alpha = 0.40f), ROW_SHAPE) else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !active,
            ) {
                haptics.play(Haptic.Select)
                onSelect()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (active) spotifyGreen.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                tint = if (active) spotifyGreen else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (active) Color.White else Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (active) "BitChord Connect • Listening now (FLAC)" else "BitChord Connect • TV",
                style = MaterialTheme.typography.labelMedium,
                color = if (active) spotifyGreen else Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (active) {
            AnimatedEqualizerBars(color = spotifyGreen)
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AnimatedEqualizerBars(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF1DB954),
) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val bar1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "b1",
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(360, easing = LinearEasing), RepeatMode.Reverse),
        label = "b2",
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(480, easing = LinearEasing), RepeatMode.Reverse),
        label = "b3",
    )

    Row(
        modifier = modifier.height(16.dp).width(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight(bar1).clip(RoundedCornerShape(1.dp)).background(color))
        Box(Modifier.width(3.dp).fillMaxHeight(bar2).clip(RoundedCornerShape(1.dp)).background(color))
        Box(Modifier.width(3.dp).fillMaxHeight(bar3).clip(RoundedCornerShape(1.dp)).background(color))
    }
}

/**
 * The BitChord watch app as the speaker. Drawn like an output of this phone —
 * lit, with a check, while it is the one playing — because to the listener that
 * is what it is.
 */
@Composable
private fun WatchRow(watch: WatchOutputUi, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val active = watch.active
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ROW_SHAPE)
            .background(Color.White.copy(alpha = if (active) 0.10f else 0.05f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !active,
            ) {
                haptics.play(Haptic.Select)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (active) 0.16f else 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Watch,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (active) 1f else 0.7f),
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = watch.name?.takeIf(String::isNotBlank) ?: stringResource(Res.string.watch_output),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = Color.White.copy(alpha = if (active) 1f else 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(if (active) Res.string.audio_output_playing else Res.string.watch_output_subtitle),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (active) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * One output. The playing one is lit and says so; the rest are there to be
 * tapped.
 */
@Composable
private fun OutputRow(
    device: AudioOutputDevice,
    accountName: String?,
    onSelect: () -> Unit,
) {
    val haptics = rememberHaptics()
    val active = device.isActive
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ROW_SHAPE)
            .background(Color.White.copy(alpha = if (active) 0.10f else 0.05f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !active,
            ) {
                haptics.play(Haptic.Select)
                onSelect()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (active) 0.16f else 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = iconFor(device),
                contentDescription = null,
                tint = Color.White.copy(alpha = if (active) 1f else 0.7f),
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = outputLabel(device, accountName),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = Color.White.copy(alpha = if (active) 1f else 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (active) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(Res.string.audio_output_playing),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        }
        if (active) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The phone's media volume, on the player's own slider rather than Material's.
 *
 * Two things move it, and only one of them announces itself. The hardware keys
 * broadcast `VOLUME_CHANGED_ACTION`, which is easy. A *route* change does not:
 * Android keeps one media volume index per output and silently swaps which one
 * is in force, so the number behind this bar changes with no event at all. Read
 * once at composition, as this was, the bar went on showing the level of the
 * device the listener had just moved away from.
 *
 * So [routeKey] — the outputs and the current choice — re-reads it, and re-reads
 * it again a beat later, because the framework swaps the index a moment after
 * the device list changes rather than in the same breath.
 */
@Composable
private fun VolumeRow(routeKey: Any, isConnectActive: Boolean = false) {
    val system = PlayerPlatform.host.volume
    val systemLevel by system.level.collectAsStateWithLifecycle()
    var level by remember(system) { mutableFloatStateOf(systemLevel) }
    var dragging by remember { mutableStateOf(false) }

    // The hardware keys move the system level; follow it, but never over a
    // finger: the listener's own drag is the one source of truth this must
    // not fight.
    LaunchedEffect(systemLevel) {
        if (!dragging) level = systemLevel
    }

    LaunchedEffect(routeKey) {
        repeat(VOLUME_REREADS) {
            if (!dragging) system.refresh()
            delay(VOLUME_REREAD_GAP_MS)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ROW_SHAPE)
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isConnectActive) Icons.Rounded.Tv else if (level > 0f) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
            contentDescription = null,
            tint = if (isConnectActive) Color(0xFF1DB954) else Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        ThinSlider(
            value = level,
            onValueChange = {
                dragging = true
                level = it
                system.set(it)
            },
            onValueChangeFinished = { dragging = false },
            idleHeight = 6.dp,
            activeHeight = 10.dp,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun iconFor(device: AudioOutputDevice): ImageVector = when (device.kind) {
    AudioOutputKind.PHONE -> Icons.Rounded.PhoneAndroid
    AudioOutputKind.WIRED -> Icons.Rounded.Headphones
    AudioOutputKind.USB -> Icons.Rounded.Usb
    // The same reading the output pill draws — see AudioOutputDevice.form.
    AudioOutputKind.BLUETOOTH -> when (device.form) {
        BluetoothForm.BUDS -> Icons.Rounded.Earbuds
        BluetoothForm.HEADPHONES -> Icons.Rounded.Headphones
        BluetoothForm.SPEAKER -> Icons.Rounded.Speaker
        BluetoothForm.CAR -> Icons.Rounded.DirectionsCar
        BluetoothForm.UNKNOWN -> Icons.Rounded.Bluetooth
    }
    AudioOutputKind.HDMI -> Icons.Rounded.Tv
    AudioOutputKind.OTHER -> Icons.Rounded.Speaker
}

/** How many times the level is re-read after a route change, and how far apart. */
private const val VOLUME_REREADS = 4
private const val VOLUME_REREAD_GAP_MS = 250L
