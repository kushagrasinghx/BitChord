package com.music.bitchord.ui.screens

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.BlurOff
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MotionPhotosOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.Account
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.playback.AudioCache
import com.music.bitchord.playback.DolbyAtmos
import kotlin.math.roundToInt

/**
 * Grouped settings, in the shape phones have taught people to expect: inset
 * cards of rows, a leading glyph per row, the current value on the right, and a
 * plain-language footer under any group whose effect isn't obvious from its
 * title. Anything with more than two choices opens a sheet rather than pushing
 * a row of chips into the layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    signedIn: Boolean,
    account: Account?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val wifiQuality by AppSettings.audioQualityWifi.collectAsStateWithLifecycle()
    val cellularQuality by AppSettings.audioQualityCellular.collectAsStateWithLifecycle()
    val metered by AppSettings.meteredConnection.collectAsStateWithLifecycle()
    val crossfade by AppSettings.crossfadeSeconds.collectAsStateWithLifecycle()
    val skipSilence by AppSettings.skipSilence.collectAsStateWithLifecycle()
    val spatialAudio by AppSettings.spatialAudio.collectAsStateWithLifecycle()
    val atmosSupported by DolbyAtmos.supported.collectAsStateWithLifecycle()
    val atmosEnabled by DolbyAtmos.enabledOnDevice.collectAsStateWithLifecycle()
    val nerdStats by AppSettings.showNerdStats.collectAsStateWithLifecycle()
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val speed by AppSettings.playbackSpeed.collectAsStateWithLifecycle()
    val theme by AppSettings.themeMode.collectAsStateWithLifecycle()
    val sessionId by AppSettings.audioSessionId.collectAsStateWithLifecycle()
    val cacheLimitBytes by AppSettings.audioCacheLimitBytes.collectAsStateWithLifecycle()

    var picking by remember { mutableStateOf<QualityTarget?>(null) }

    // Coming back from the system Atmos panel is the one moment the answer is
    // most likely to have changed, and on devices whose Atmos switch isn't
    // watchable it's the only moment we'd hear about it at all.
    LifecycleResumeEffect(Unit) {
        DolbyAtmos.refresh()
        onPauseOrDispose {}
    }

    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
        )

        AccountCard(signedIn = signedIn, account = account, onSignIn = onSignIn)

        SettingsGroup(
            header = "Audio quality",
            footer = "Each connection keeps its own ceiling, so Wi-Fi can stay on " +
                "High while mobile data is capped. High costs about " +
                "${AudioQuality.HIGH.hourly} of data.",
        ) {
            SettingsRow(
                icon = Icons.Rounded.Wifi,
                title = "On Wi-Fi",
                badge = "In use".takeIf { metered == false },
                value = wifiQuality.label,
                onClick = { picking = QualityTarget.WIFI },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.SignalCellularAlt,
                title = "On mobile data",
                badge = "In use".takeIf { metered == true },
                value = cellularQuality.label,
                onClick = { picking = QualityTarget.CELLULAR },
            )
        }

        SettingsGroup(header = "Playback") {
            SliderRow(
                icon = Icons.Rounded.Waves,
                title = "Crossfade",
                subtitle = "Blends one track into the next",
                value = if (crossfade == 0) "Off" else "${crossfade}s",
                sliderValue = crossfade.toFloat(),
                onSliderValue = { AppSettings.setCrossfadeSeconds(it.roundToInt()) },
                valueRange = 0f..12f,
                steps = 11,
            )
            RowDivider()
            SliderRow(
                icon = Icons.Rounded.Speed,
                title = "Playback speed",
                value = "${"%.2f".format(speed)}×",
                sliderValue = speed,
                onSliderValue = { AppSettings.setPlaybackSpeed((it * 20).roundToInt() / 20f) },
                valueRange = 0.5f..2.0f,
                steps = 29,
            )
            RowDivider()
            SettingsRow(
                icon = Icons.AutoMirrored.Rounded.VolumeOff,
                title = "Skip silence",
                subtitle = "Trim gaps longer than a second",
                trailing = {
                    Switch(
                        checked = skipSilence,
                        onCheckedChange = AppSettings::setSkipSilence,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                },
                onClick = { AppSettings.setSkipSilence(!skipSilence) },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.SurroundSound,
                title = "Spatial audio",
                subtitle = when {
                    !atmosSupported -> "Needs a device with Dolby Atmos"
                    !atmosEnabled -> "Turn on Dolby Atmos to use it"
                    else -> "Widens stereo tracks for a more immersive feel"
                },
                enabled = atmosSupported,
                trailing = {
                    Switch(
                        checked = spatialAudio && atmosEnabled,
                        onCheckedChange = { wanted ->
                            if (atmosEnabled) AppSettings.setSpatialAudio(wanted) else openAtmosSettings(context)
                        },
                        enabled = atmosSupported,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                },
                // With Atmos off, the switch has nothing to switch — the row
                // sends the user to the panel that does, and the state it comes
                // back with is picked up on resume.
                onClick = when {
                    !atmosSupported -> null
                    !atmosEnabled -> ({ openAtmosSettings(context) })
                    else -> ({ AppSettings.setSpatialAudio(!spatialAudio) })
                },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Tune,
                title = "Equalizer",
                subtitle = "Your device's system panel",
                onClick = { openEqualizer(context, sessionId) },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.GraphicEq,
                title = "Show stats for nerds",
                subtitle = "Codec, bitrate and sample rate on the player",
                trailing = {
                    Switch(
                        checked = nerdStats,
                        onCheckedChange = AppSettings::setShowNerdStats,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                },
                onClick = { AppSettings.setShowNerdStats(!nerdStats) },
            )
        }

        SettingsGroup(header = "Appearance") {
            SettingsRow(icon = Icons.Rounded.Brightness4, title = "Theme")
            SegmentedControl(
                options = ThemeMode.entries.map { it.label },
                selectedIndex = ThemeMode.entries.indexOf(theme),
                onSelect = { AppSettings.setThemeMode(ThemeMode.entries[it]) },
                modifier = Modifier.padding(start = ROW_INSET, end = ROW_INSET, bottom = 14.dp),
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.MotionPhotosOff,
                title = "Reduce animation",
                subtitle = "Freezes the main player's gradient instead of drifting",
                trailing = {
                    Switch(
                        checked = reduceAnimation,
                        onCheckedChange = AppSettings::setReduceAnimation,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                },
                onClick = { AppSettings.setReduceAnimation(!reduceAnimation) },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.BlurOff,
                title = "Reduce dynamic blur",
                subtitle = "Swaps frosted glass for solid fills across the app",
                trailing = {
                    Switch(
                        checked = reduceDynamicBlur,
                        onCheckedChange = AppSettings::setReduceDynamicBlur,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                },
                onClick = { AppSettings.setReduceDynamicBlur(!reduceDynamicBlur) },
            )
        }

        val cacheLimitMb = (cacheLimitBytes / (1024 * 1024)).toInt()
        SettingsGroup(header = "Storage") {
            SliderRow(
                icon = Icons.Rounded.Storage,
                title = "Song cache limit",
                subtitle = if (cacheLimitMb > CACHE_WARNING_MB) {
                    "Up to ${formatCacheSize(cacheLimitMb)} of downloaded audio kept on " +
                        "disk — that's a real chunk of most phones' free storage."
                } else {
                    "Downloaded audio kept on disk for instant seeking and replays"
                },
                value = formatCacheSize(cacheLimitMb),
                sliderValue = cacheLimitMb.toFloat(),
                onSliderValue = {
                    AppSettings.setAudioCacheLimitBytes(it.roundToInt().toLong() * 1024 * 1024)
                },
                valueRange = (AppSettings.DEFAULT_CACHE_LIMIT_BYTES / (1024 * 1024)).toFloat()..
                    (AppSettings.MAX_CACHE_LIMIT_BYTES / (1024 * 1024)).toFloat(),
                steps = 18,
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.DeleteSweep,
                title = "Clear song cache",
                subtitle = "Frees space used by downloaded audio",
                onClick = {
                    AudioCache.clear {
                        Toast.makeText(context, "Song cache cleared", Toast.LENGTH_SHORT).show()
                    }
                },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.DeleteSweep,
                title = "Clear image cache",
                subtitle = "Frees space used by album artwork",
                onClick = {
                    val loader = SingletonImageLoader.get(context)
                    loader.memoryCache?.clear()
                    loader.diskCache?.clear()
                    Toast.makeText(context, "Image cache cleared", Toast.LENGTH_SHORT).show()
                },
            )
        }

        if (signedIn) {
            SettingsGroup {
                DestructiveRow(label = "Sign out", onClick = onSignOut)
            }
        }

        Text(
            text = buildAnnotatedString {
                append("BitChord $version  ")
                val linkStyles = TextLinkStyles(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                    ),
                )
                withLink(LinkAnnotation.Url("https://github.com/kushagrasinghx/BitChord", linkStyles)) {
                    append("GitHub")
                }
                append("  ")
                withLink(LinkAnnotation.Url("https://github.com/kushagrasinghx", linkStyles)) {
                    append("Developer")
                }
                append("\n~YouTube Music Backend")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 8.dp),
        )
    }

    picking?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { picking = null },
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            QualitySheet(
                target = target,
                selected = when (target) {
                    QualityTarget.WIFI -> wifiQuality
                    QualityTarget.CELLULAR -> cellularQuality
                },
                onSelect = { quality ->
                    when (target) {
                        QualityTarget.WIFI -> AppSettings.setAudioQualityWifi(quality)
                        QualityTarget.CELLULAR -> AppSettings.setAudioQualityCellular(quality)
                    }
                    picking = null
                },
            )
        }
    }
}

/** Which ceiling the open picker is editing. */
private enum class QualityTarget(val title: String, val icon: ImageVector) {
    WIFI("Wi-Fi", Icons.Rounded.Wifi),
    CELLULAR("Mobile data", Icons.Rounded.SignalCellularAlt),
}

private fun openEqualizer(context: Context, sessionId: Int) {
    if (sessionId == 0) {
        Toast.makeText(
            context,
            "Play a track first, then open the equalizer",
            Toast.LENGTH_SHORT,
        ).show()
        return
    }
    val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
        putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "No system equalizer on this device", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Hands the user to whatever owns Dolby Atmos on this device. Nothing in the
 * public API lets an app flip that switch itself, so the honest move is to open
 * the panel rather than pretend the row can do it.
 */
private fun openAtmosSettings(context: Context) {
    val intent = DolbyAtmos.settingsIntent(context)
    if (intent == null) {
        Toast.makeText(context, "No Dolby Atmos panel on this device", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure {
        Toast.makeText(context, "Couldn't open Dolby Atmos settings", Toast.LENGTH_SHORT).show()
    }
}

/** Above this, the cache limit slider's subtitle warns rather than reassures. */
private const val CACHE_WARNING_MB = 2048

/** "512 MB", "2 GB", "2.5 GB" — whichever reads more naturally at that size. */
private fun formatCacheSize(mb: Int): String {
    if (mb < 1024) return "$mb MB"
    val gb = mb / 1024f
    return if (gb == gb.toInt().toFloat()) "${gb.toInt()} GB" else "%.1f GB".format(gb)
}

/** Who you're signed in as, straight from YouTube Music's account menu. */
@Composable
private fun AccountCard(
    signedIn: Boolean,
    account: Account?,
    onSignIn: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GROUP_INSET)
            .clip(GroupShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (signedIn) Modifier else Modifier.clickable(onClick = onSignIn))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (account?.thumbnailUrl != null) {
            AsyncImage(
                model = account.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = account?.name ?: if (signedIn) "Signed in" else "Not signed in",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = account?.email?.takeIf { it.isNotBlank() }
                    ?: if (signedIn) "YouTube Music account" else "Tap to sign in with Google",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!signedIn) {
            Spacer(Modifier.width(8.dp))
            Chevron()
        }
    }
}

/** The quality options for one connection, with what each costs in data. */
@Composable
private fun QualitySheet(
    target: QualityTarget,
    selected: AudioQuality,
    onSelect: (AudioQuality) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Row(
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = target.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = "Audio quality",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "While on ${target.title.lowercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)

        // Best first — the option most people want shouldn't be last.
        AudioQuality.entries.reversed().forEach { quality ->
            val chosen = quality == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(quality)
                    }
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = quality.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "${quality.detail} · ${quality.hourly}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (chosen) {
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

// ---- Building blocks --------------------------------------------------------

private val GroupShape = RoundedCornerShape(14.dp)
private val GROUP_INSET = 16.dp
private val ROW_INSET = 16.dp
private val ICON_SIZE = 22.dp
private val ICON_GAP = 14.dp

/** Where a row's text starts — dividers are inset to match, as on iOS. */
private val TEXT_INSET = ROW_INSET + ICON_SIZE + ICON_GAP

/**
 * One inset card of rows, with an uppercase header above and an optional
 * plain-language [footer] below. Rows are separated by [RowDivider].
 */
@Composable
private fun SettingsGroup(
    header: String? = null,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    if (header != null) {
        Text(
            text = header.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = GROUP_INSET + 4.dp,
                end = GROUP_INSET,
                top = 26.dp,
                bottom = 8.dp,
            ),
        )
    } else {
        Spacer(Modifier.height(26.dp))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GROUP_INSET)
            .clip(GroupShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        content()
    }
    if (footer != null) {
        Text(
            text = footer,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = GROUP_INSET + 4.dp,
                end = GROUP_INSET + 4.dp,
                top = 8.dp,
            ),
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = TEXT_INSET),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline,
    )
}

/**
 * The standard row: glyph, title, optional subtitle, and on the right either
 * [trailing] (a switch, say) or the current [value] followed by a chevron.
 */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    badge: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.45f)
            .heightIn(min = 52.dp)
            .padding(horizontal = ROW_INSET, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(ICON_SIZE),
        )
        Spacer(Modifier.width(ICON_GAP))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Badge(badge)
                }
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        if (trailing != null) {
            trailing()
        } else if (value != null || onClick != null) {
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.width(4.dp))
            }
            Chevron()
        }
    }
}

/** Marks the connection whose ceiling is actually in force right now. */
@Composable
private fun Badge(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun Chevron() {
    Icon(
        Icons.Rounded.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.size(20.dp),
    )
}

/** A continuous setting: label and current value on one line, track beneath. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SliderRow(
    icon: ImageVector,
    title: String,
    value: String,
    sliderValue: Float,
    onSliderValue: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    subtitle: String? = null,
) {
    val colors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.outline,
    )
    Column(Modifier.padding(start = ROW_INSET, end = ROW_INSET, top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(ICON_SIZE),
            )
            Spacer(Modifier.width(ICON_GAP))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = onSliderValue,
            valueRange = valueRange,
            steps = steps,
            colors = colors,
            // Bare track: the step ticks and the end-stop dot are noise when the
            // value is already spelled out on the line above.
            track = { state ->
                SliderDefaults.Track(
                    sliderState = state,
                    colors = colors,
                    drawStopIndicator = null,
                    drawTick = { _, _ -> },
                )
            },
            modifier = Modifier.padding(start = ICON_SIZE + ICON_GAP),
        )
    }
}

/** Sign out: centered, accent-coloured, no glyph — the shape of a real one. */
@Composable
private fun DestructiveRow(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Sliding pill selector, for the handful of settings with two or three states. */
@Composable
private fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.outline)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val chosen = index == selectedIndex
            val pill by animateColorAsState(
                targetValue = if (chosen) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                animationSpec = tween(160),
                label = "segmentPill",
            )
            val labelColor by animateColorAsState(
                targetValue = if (chosen) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = tween(160),
                label = "segmentLabel",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(pill)
                    .clickable {
                        if (!chosen) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(index)
                        }
                    }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = labelColor,
                    maxLines = 1,
                )
            }
        }
    }
}
