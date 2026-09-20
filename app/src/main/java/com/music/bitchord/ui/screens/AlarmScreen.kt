package com.music.bitchord.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.AccessAlarm
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.alarm.AlarmConfig
import com.music.bitchord.alarm.AlarmFailure
import com.music.bitchord.alarm.AlarmScheduleMode
import com.music.bitchord.alarm.AlarmScheduler
import com.music.bitchord.alarm.AlarmStore
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.components.thumbnailBorder
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Native settings page for BitChord's single music alarm. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext
    remember(app) {
        AlarmStore.init(app)
        true
    }
    val config by AlarmStore.config.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle, app) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) AlarmScheduler.reconcile(app)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    var pickingTime by remember { mutableStateOf(false) }
    var pickingPlaylist by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf<List<UserPlaylist>>(emptyList()) }
    var playlistsLoading by remember { mutableStateOf(false) }
    var playlistLoadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(pickingPlaylist) {
        if (!pickingPlaylist) return@LaunchedEffect
        playlistsLoading = true
        playlistLoadFailed = false
        YtMusicRepository.userPlaylists()
            .onSuccess { playlists = it }
            .onFailure { playlistLoadFailed = true }
        playlistsLoading = false
    }

    fun update(change: (AlarmConfig) -> AlarmConfig) {
        AlarmScheduler.updateConfiguration(app, change)
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = 36.dp),
    ) {
        SettingsGroup(
            header = stringResource(R.string.alarm_clock),
            footer = stringResource(R.string.alarm_media_volume_note),
        ) {
            SettingsRow(
                icon = Icons.Rounded.Alarm,
                title = stringResource(R.string.alarm_enabled),
                subtitle = if (config.playlistId.isBlank()) {
                    stringResource(R.string.alarm_no_playlist_warning)
                } else {
                    stringResource(R.string.alarm_enabled_subtitle)
                },
                trailing = {
                    Switch(
                        checked = config.enabled,
                        enabled = config.playlistId.isNotBlank(),
                        onCheckedChange = { enabled -> update { it.copy(enabled = enabled) } },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                },
                onClick = if (config.playlistId.isNotBlank()) {
                    { update { it.copy(enabled = !it.enabled) } }
                } else {
                    null
                },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.AccessTime,
                title = stringResource(R.string.alarm_time),
                value = String.format(Locale.getDefault(), "%02d:%02d", config.hour, config.minute),
                onClick = { pickingTime = true },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.EventRepeat,
                title = stringResource(R.string.alarm_repeat),
                value = repeatSummary(config.repeatDays),
            )
            FlowRow(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = day.value in config.repeatDays,
                        onClick = {
                            update { current ->
                                val days = if (day.value in current.repeatDays) {
                                    current.repeatDays - day.value
                                } else {
                                    current.repeatDays + day.value
                                }
                                current.copy(repeatDays = days)
                            }
                        },
                        label = {
                            Text(day.getDisplayName(TextStyle.NARROW, Locale.getDefault()))
                        },
                    )
                }
            }
            RowDivider()
            SettingsRow(
                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                title = stringResource(R.string.alarm_playlist),
                value = config.playlistTitle.ifBlank { stringResource(R.string.alarm_choose_playlist) },
                onClick = { pickingPlaylist = true },
            )
        }

        SettingsGroup(
            header = stringResource(R.string.alarm_schedule),
            footer = if (config.enabled && config.scheduleMode == AlarmScheduleMode.INEXACT) {
                stringResource(R.string.alarm_exact_explanation)
            } else {
                null
            },
        ) {
            SettingsRow(
                icon = Icons.Rounded.AccessAlarm,
                title = scheduleStatus(config),
                subtitle = nextOccurrence(config),
                badge = when (config.scheduleMode) {
                    AlarmScheduleMode.EXACT -> stringResource(R.string.alarm_exact)
                    AlarmScheduleMode.INEXACT -> stringResource(R.string.alarm_inexact)
                    null -> null
                },
            )
            if (config.enabled && !AlarmScheduler.canScheduleExact(app)) {
                RowDivider()
                SettingsRow(
                    icon = Icons.Rounded.AccessAlarm,
                    title = stringResource(R.string.alarm_allow_exact),
                    subtitle = stringResource(R.string.alarm_exact_explanation),
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                )
            }
            config.lastFailure?.let { failure ->
                RowDivider()
                SettingsRow(
                    icon = Icons.Rounded.AccessAlarm,
                    title = stringResource(R.string.alarm_last_problem),
                    subtitle = stringResource(
                        when (failure) {
                            AlarmFailure.SCHEDULING_UNAVAILABLE -> R.string.alarm_failure_scheduling
                            AlarmFailure.PLAYBACK_UNAVAILABLE -> R.string.alarm_failure_playback
                        },
                    ),
                )
            }
        }
    }

    if (pickingTime) {
        AlarmTimeDialog(
            config = config,
            onDismiss = { pickingTime = false },
            onConfirm = { hour, minute ->
                update { it.copy(hour = hour, minute = minute) }
                pickingTime = false
            },
        )
    }

    if (pickingPlaylist) {
        ModalBottomSheet(onDismissRequest = { pickingPlaylist = false }) {
            AlarmPlaylistPicker(
                playlists = playlists,
                loading = playlistsLoading,
                failed = playlistLoadFailed,
                onPick = { playlist ->
                    update {
                        it.copy(
                            playlistId = playlist.playlistId,
                            playlistTitle = playlist.title,
                            playlistArtworkUrl = playlist.thumbnailUrl,
                        )
                    }
                    pickingPlaylist = false
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmTimeDialog(
    config: AlarmConfig,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = config.hour,
        initialMinute = config.minute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun AlarmPlaylistPicker(
    playlists: List<UserPlaylist>,
    loading: Boolean,
    failed: Boolean,
    onPick: (UserPlaylist) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.alarm_choose_playlist),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
        )
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
        when {
            loading && playlists.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().height(140.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
            }
            playlists.isEmpty() -> Text(
                text = stringResource(
                    if (failed) R.string.alarm_playlist_load_failed else R.string.alarm_no_playlists,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(22.dp),
            )
            else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(playlists, key = { it.playlistId }) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(playlist) }
                            .padding(horizontal = 22.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = playlist.thumbnailUrl.artworkAt(ROW_ART_PX),
                            contentDescription = null,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .thumbnailBorder(RoundedCornerShape(7.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = playlist.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (playlist.subtitle.isNotBlank()) {
                                Text(
                                    text = playlist.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun repeatSummary(days: Set<Int>): String = when {
    days.isEmpty() -> stringResource(R.string.alarm_one_time)
    days.size == 7 -> stringResource(R.string.alarm_every_day)
    else -> DayOfWeek.entries
        .filter { it.value in days }
        .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

@Composable
private fun scheduleStatus(config: AlarmConfig): String = when {
    !config.enabled -> stringResource(R.string.alarm_off)
    config.scheduledEpochMillis == null -> stringResource(R.string.alarm_not_scheduled)
    else -> stringResource(R.string.alarm_scheduled)
}

@Composable
private fun nextOccurrence(config: AlarmConfig): String? {
    val epoch = config.scheduledEpochMillis ?: return null
    val formatted = remember(epoch) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(epoch))
    }
    return stringResource(R.string.alarm_next, formatted)
}
