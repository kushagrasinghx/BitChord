package com.music.bitchord.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.alarm.*
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PillTextField
import com.music.bitchord.ui.components.SearchField
import com.music.bitchord.ui.components.SongRow
import com.music.bitchord.ui.components.thumbnailBorder
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private data class EditingAlarm(val id: String, val isNew: Boolean)

private val AlarmSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val AlarmRowArtworkShape = RoundedCornerShape(8.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    remember { AlarmStore.init(context); true }
    val state by AlarmStore.alarms.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<EditingAlarm?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    val addAlarm = {
        editing = EditingAlarm(AlarmScheduler.create(context).id, true)
    }

    Surface(
        modifier = modifier
            .fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = contentPadding.calculateTopPadding() + 8.dp,
                        bottom = 14.dp,
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.alarm_alarms),
                    style = MaterialTheme.typography.displayLarge,
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = addAlarm),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.alarm_add),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    bottom = contentPadding.calculateBottomPadding() + 96.dp,
                ),
            ) {
                if (state.alarms.isEmpty()) {
                    item {
                        MessageState(
                            message = stringResource(R.string.alarm_empty),
                            actionLabel = stringResource(R.string.alarm_add),
                            onAction = addAlarm,
                        )
                    }
                } else {
                    item {
                        AlarmListGroup(
                            alarms = AlarmStateMachine.sorted(state.alarms),
                            onToggle = { alarm, enabled ->
                                AlarmScheduler.setEnabled(context, alarm.id, enabled)
                            },
                            onOpen = { editing = EditingAlarm(it.id, false) },
                        )
                    }
                }
                item { AlarmPermissionRows() }
            }
        }
    }

    editing?.let { target ->
        state.alarms.firstOrNull { it.id == target.id }?.let { alarm ->
            AlarmEditor(
                initial = alarm,
                onSave = { value ->
                    AlarmScheduler.update(context, alarm.id) { value }
                    editing = null
                },
                onDelete = { deleteTarget = alarm.id },
                onDismiss = {
                    if (target.isNew) AlarmScheduler.delete(context, target.id)
                    editing = null
                },
            )
        }
    }

    deleteTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.alarm_delete)) },
            text = { Text(stringResource(R.string.alarm_delete_confirmation)) },
            confirmButton = {
                TextButton({
                    AlarmScheduler.delete(context, id)
                    deleteTarget = null
                    editing = null
                }) { Text(stringResource(R.string.alarm_delete)) }
            },
            dismissButton = {
                TextButton({ deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun AlarmListGroup(
    alarms: List<AlarmConfig>,
    onToggle: (AlarmConfig, Boolean) -> Unit,
    onOpen: (AlarmConfig) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = GroupShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column {
            alarms.forEachIndexed { index, alarm ->
                if (index > 0) AlarmListDivider()
                AlarmRow(
                    alarm = alarm,
                    onToggle = { onToggle(alarm, it) },
                    onOpen = { onOpen(alarm) },
                )
            }
        }
    }
}

@Composable
private fun AlarmListDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 82.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun AlarmRow(alarm: AlarmConfig, onToggle: (Boolean) -> Unit, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .alpha(if (alarm.enabled) 1f else 0.55f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlarmArtwork(alarm.song, Modifier.size(52.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp),
                maxLines = 1,
            )
            if (alarm.label.isNotBlank()) {
                Text(
                    text = alarm.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = repeatSummary(alarm.repeatDays),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            alarm.song?.let {
                Text(
                    text = listOf(it.title, it.artist)
                        .filter(String::isNotBlank)
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            alarm.scheduledEpochMillis?.let {
                Text(
                    text = nextSummary(it),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = alarm.enabled,
            onCheckedChange = onToggle,
            enabled = alarm.song?.isValid() == true,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun AlarmArtwork(song: AlarmSong?, modifier: Modifier = Modifier) {
    val originalUrl = song?.artworkUrl
    if (originalUrl != null) {
        val resizedUrl = originalUrl.artworkAt(ROW_ART_PX)
        var useOriginal by remember(originalUrl, resizedUrl) { mutableStateOf(false) }
        AsyncImage(
            model = if (useOriginal) originalUrl else resizedUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = {
                if (!useOriginal && resizedUrl != originalUrl) useOriginal = true
            },
            modifier = modifier
                .clip(AlarmRowArtworkShape)
                .thumbnailBorder(AlarmRowArtworkShape),
        )
    } else {
        Box(
            modifier = modifier
                .clip(AlarmRowArtworkShape)
                .background(MaterialTheme.colorScheme.outline),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditor(
    initial: AlarmConfig,
    onSave: (AlarmConfig) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var alarm by remember(initial.id) { mutableStateOf(initial) }
    var timeDialog by remember { mutableStateOf(false) }
    var songDialog by remember { mutableStateOf(false) }

    AlarmSheet(onDismiss) {
        AlarmSheetHeader(
            title = stringResource(R.string.alarm_edit),
            onDismiss = onDismiss,
            actionLabel = stringResource(R.string.done),
            actionEnabled = alarm.song?.isValid() == true,
            onAction = { onSave(alarm) },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(bottom = 24.dp),
        ) {
            SettingsGroup(topSpacing = 10.dp) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    PillTextField(
                        value = alarm.label,
                        onValueChange = { alarm = alarm.copy(label = it.take(60)) },
                        placeholder = stringResource(R.string.alarm_label),
                        container = MaterialTheme.colorScheme.background,
                    )
                }
                RowDivider()
                SettingsRow(
                    icon = Icons.Rounded.Alarm,
                    title = stringResource(R.string.alarm_time),
                    value = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute),
                    onClick = { timeDialog = true },
                )
            }

            SettingsGroup(header = stringResource(R.string.alarm_repeat)) {
                AlarmDaySelector(
                    selectedDays = alarm.repeatDays,
                    onToggle = { day ->
                        alarm = alarm.copy(
                            repeatDays = if (day.value in alarm.repeatDays) {
                                alarm.repeatDays - day.value
                            } else {
                                alarm.repeatDays + day.value
                            },
                        )
                    },
                )
            }

            SettingsGroup(header = stringResource(R.string.alarm_song)) {
                AlarmSongRow(alarm.song) { songDialog = true }
            }

            SettingsGroup {
                SliderRow(
                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                    title = stringResource(R.string.alarm_volume_percent, alarm.targetVolumePercent),
                    value = "${alarm.targetVolumePercent}%",
                    sliderValue = alarm.targetVolumePercent.toFloat(),
                    onSliderValue = {
                        alarm = alarm.copy(targetVolumePercent = (it / 10).toInt() * 10)
                    },
                    valueRange = 10f..100f,
                    steps = 8,
                )
                RowDivider()
                SliderRow(
                    icon = Icons.Rounded.Alarm,
                    title = stringResource(R.string.alarm_snooze),
                    value = stringResource(R.string.alarm_snooze_duration, alarm.snoozeMinutes)
                        .substringAfter("· "),
                    sliderValue = alarm.snoozeMinutes.toFloat(),
                    onSliderValue = {
                        alarm = alarm.copy(snoozeMinutes = (it / 5).toInt() * 5)
                    },
                    valueRange = 5f..30f,
                    steps = 4,
                )
            }

            SettingsGroup(header = stringResource(R.string.alarm_volume_buttons)) {
                AlarmVolumeButtonAction.entries.forEachIndexed { index, action ->
                    if (index > 0) RowDivider()
                    AlarmChoiceRow(
                        label = stringResource(action.labelResource()),
                        selected = alarm.volumeButtonAction == action,
                        onClick = { alarm = alarm.copy(volumeButtonAction = action) },
                    )
                }
            }

            SettingsGroup {
                SettingsRow(
                    icon = Icons.Rounded.Tune,
                    title = stringResource(R.string.alarm_enabled),
                    trailing = {
                        Switch(
                            checked = alarm.enabled,
                            onCheckedChange = { alarm = alarm.copy(enabled = it) },
                            enabled = alarm.song?.isValid() == true,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                )
            }

            SettingsGroup {
                DestructiveRow(
                    label = stringResource(R.string.alarm_delete),
                    onClick = onDelete,
                )
            }
        }
    }

    if (timeDialog) {
        AlarmTimeDialog(
            alarm = alarm,
            dismiss = { timeDialog = false },
            confirm = { hour, minute ->
                alarm = alarm.copy(hour = hour, minute = minute)
                timeDialog = false
            },
        )
    }
    if (songDialog) {
        AlarmSongPicker(
            onPick = { song ->
                alarm = alarm.copy(song = song)
                songDialog = false
            },
            dismiss = { songDialog = false },
        )
    }
}

@Composable
private fun AlarmDaySelector(selectedDays: Set<Int>, onToggle: (DayOfWeek) -> Unit) {
    Row(
        modifier = Modifier.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DayOfWeek.entries.forEach { day ->
            val selected = day.value in selectedDays
            val background by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = tween(160),
                label = "alarmDayBackground",
            )
            val foreground by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(160),
                label = "alarmDayForeground",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(background)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .clickable { onToggle(day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = foreground,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AlarmSongRow(song: AlarmSong?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlarmArtwork(song, Modifier.size(52.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song?.title ?: stringResource(R.string.alarm_choose_song),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            song?.artist?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Chevron()
    }
}

@Composable
private fun AlarmChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(enabled = !selected, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmTimeDialog(alarm: AlarmConfig, dismiss: () -> Unit, confirm: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = alarm.hour,
        initialMinute = alarm.minute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = dismiss,
        text = { TimePicker(state) },
        confirmButton = {
            TextButton({ confirm(state.hour, state.minute) }) {
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = {
            TextButton(dismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmSongPicker(onPick: (AlarmSong) -> Unit, dismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf<String?>(null) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(submitted) {
        val searchQuery = submitted ?: return@LaunchedEffect
        loading = true
        songs = YtMusicRepository.search(searchQuery, SearchFilter.SONGS)
            .getOrDefault(emptyList())
            .mapNotNull {
                when (it) {
                    is SearchResult.TopTrack -> it.song
                    is SearchResult.Track -> it.song
                    else -> null
                }
            }
            .distinctBy(Song::videoId)
        loading = false
    }

    AlarmSheet(dismiss) {
        Text(
            text = stringResource(R.string.alarm_choose_song),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 18.dp),
        )
        SearchField(
            query = query,
            onQueryChange = { query = it },
            onSubmit = { submitted = query.trim().takeIf(String::isNotEmpty) },
            placeholder = stringResource(R.string.alarm_search_song),
            modifier = Modifier.padding(horizontal = 22.dp),
        )
        if (loading) {
            CircularProgressIndicator(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(24.dp),
            )
        }
        LazyColumn(Modifier.heightIn(max = 430.dp)) {
            items(songs, key = Song::videoId) { song ->
                SongRow(
                    song = song,
                    onClick = {
                        onPick(
                            AlarmSong(
                                song.videoId,
                                song.title,
                                song.artist,
                                song.thumbnailUrl,
                                song.durationText,
                            ),
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            shape = AlarmSheetShape,
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 34.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
                content()
            }
        }
    }
}

@Composable
private fun AlarmSheetHeader(
    title: String,
    onDismiss: () -> Unit,
    actionLabel: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onAction, enabled = actionEnabled) {
            Text(text = actionLabel, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun repeatSummary(days: Set<Int>) = if (days.isEmpty()) {
    stringResource(R.string.alarm_one_time)
} else {
    DayOfWeek.entries
        .filter { it.value in days }
        .joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

@Composable
private fun nextSummary(epoch: Long) = stringResource(
    R.string.alarm_next,
    remember(epoch) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(epoch))
    },
)

private fun AlarmVolumeButtonAction.labelResource() = when (this) {
    AlarmVolumeButtonAction.SNOOZE -> R.string.alarm_volume_buttons_snooze
    AlarmVolumeButtonAction.STOP -> R.string.alarm_volume_buttons_stop
    AlarmVolumeButtonAction.ADJUST_VOLUME -> R.string.alarm_volume_buttons_adjust
}

@Composable
private fun AlarmPermissionRows() {
    val context = LocalContext.current
    val needsFullScreen = Build.VERSION.SDK_INT >= 34 && !AlarmNotification.canUseFullScreen(context)
    val needsExact = !AlarmScheduler.canScheduleExact(context)
    if (!needsFullScreen && !needsExact) return

    SettingsGroup(topSpacing = 18.dp) {
        if (needsFullScreen) {
            SettingsRow(
                icon = Icons.Rounded.Tune,
                title = stringResource(R.string.alarm_allow_full_screen),
                subtitle = stringResource(R.string.alarm_channel_description),
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    }
                },
            )
        }
        if (needsFullScreen && needsExact) RowDivider()
        if (needsExact) {
            SettingsRow(
                icon = Icons.Rounded.Alarm,
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
    }
}
