package com.music.bitchord.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.alarm.*
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.components.SearchField
import com.music.bitchord.ui.components.SongRow
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private data class EditingAlarm(val id: String, val isNew: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AlarmScreen(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    remember { AlarmStore.init(context); true }
    val state by AlarmStore.alarms.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<EditingAlarm?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    Column(modifier.padding(contentPadding)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(stringResource(R.string.alarm_alarms), style = MaterialTheme.typography.headlineLarge)
            FilledIconButton({ editing = EditingAlarm(AlarmScheduler.create(context).id, true) }) { Icon(Icons.Rounded.Add, stringResource(R.string.alarm_add)) }
        }
        if (state.alarms.isEmpty()) Text(stringResource(R.string.alarm_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        else LazyColumn(Modifier.weight(1f)) { items(AlarmStateMachine.sorted(state.alarms), key = AlarmConfig::id) { alarm ->
            AlarmCard(alarm, { AlarmScheduler.setEnabled(context, alarm.id, it) }, { editing = EditingAlarm(alarm.id, false) })
        } }
        AlarmPermissionRows()
    }
    editing?.let { target -> state.alarms.firstOrNull { it.id == target.id }?.let { alarm ->
        AlarmEditor(alarm, { value -> AlarmScheduler.update(context, alarm.id) { value }; editing = null }, { deleteTarget = alarm.id }, {
            if (target.isNew) AlarmScheduler.delete(context, target.id); editing = null
        })
    } }
    deleteTarget?.let { id -> AlertDialog(
        onDismissRequest = { deleteTarget = null },
        title = { Text(stringResource(R.string.alarm_delete)) },
        text = { Text(stringResource(R.string.alarm_delete_confirmation)) },
        confirmButton = { TextButton({ AlarmScheduler.delete(context, id); deleteTarget = null; editing = null }) { Text(stringResource(R.string.alarm_delete)) } },
        dismissButton = { TextButton({ deleteTarget = null }) { Text(stringResource(R.string.cancel)) } },
    ) }
}

@Composable private fun AlarmCard(alarm: AlarmConfig, onToggle: (Boolean) -> Unit, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp).clickable(onClick = onOpen)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute), style = MaterialTheme.typography.displayLarge)
                Switch(alarm.enabled, onToggle, enabled = alarm.song?.isValid() == true)
            }
            if (alarm.label.isNotBlank()) Text(alarm.label, style = MaterialTheme.typography.titleMedium)
            Text(repeatSummary(alarm.repeatDays), color = MaterialTheme.colorScheme.onSurfaceVariant)
            alarm.song?.let { Text(listOf(it.title, it.artist).filter(String::isNotBlank).joinToString(" · ")) }
            alarm.scheduledEpochMillis?.let { Text(nextSummary(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable private fun AlarmEditor(initial: AlarmConfig, onSave: (AlarmConfig) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var alarm by remember(initial.id) { mutableStateOf(initial) }; var timeDialog by remember { mutableStateOf(false) }; var songDialog by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.alarm_edit), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(alarm.label, { alarm = alarm.copy(label = it.take(60)) }, label = { Text(stringResource(R.string.alarm_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        ListItem({ Text(stringResource(R.string.alarm_time)) }, trailingContent = { Text(String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute)) }, modifier = Modifier.clickable { timeDialog = true })
        Text(stringResource(R.string.alarm_repeat), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) { DayOfWeek.entries.forEach { day -> FilterChip(day.value in alarm.repeatDays, { alarm = alarm.copy(repeatDays = if (day.value in alarm.repeatDays) alarm.repeatDays - day.value else alarm.repeatDays + day.value) }, { Text(day.getDisplayName(TextStyle.NARROW, Locale.getDefault())) }) } }
        ListItem({ Text(stringResource(R.string.alarm_song)) }, supportingContent = { Text(alarm.song?.let { "${it.title} · ${it.artist}" } ?: stringResource(R.string.alarm_choose_song)) }, leadingContent = { alarm.song?.artworkUrl?.let { AsyncImage(it, null, Modifier.size(48.dp)) } }, modifier = Modifier.clickable { songDialog = true })
        Text(stringResource(R.string.alarm_volume_percent, alarm.targetVolumePercent)); Slider(alarm.targetVolumePercent.toFloat(), { alarm = alarm.copy(targetVolumePercent = (it / 10).toInt() * 10) }, valueRange = 10f..100f, steps = 8)
        Text(stringResource(R.string.alarm_snooze_duration, alarm.snoozeMinutes)); Slider(alarm.snoozeMinutes.toFloat(), { alarm = alarm.copy(snoozeMinutes = (it / 5).toInt() * 5) }, valueRange = 5f..30f, steps = 4)
        Text(stringResource(R.string.alarm_volume_buttons), style = MaterialTheme.typography.titleMedium)
        AlarmVolumeButtonAction.entries.forEach { action -> FilterChip(alarm.volumeButtonAction == action, { alarm = alarm.copy(volumeButtonAction = action) }, { Text(stringResource(action.labelResource())) }) }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text(stringResource(R.string.alarm_enabled)); Switch(alarm.enabled, { alarm = alarm.copy(enabled = it) }, enabled = alarm.song?.isValid() == true) }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { TextButton(onDelete) { Text(stringResource(R.string.alarm_delete), color = MaterialTheme.colorScheme.error) }; Button({ onSave(alarm) }, enabled = alarm.song?.isValid() == true) { Text(stringResource(R.string.done)) } }
    } }
    if (timeDialog) AlarmTimeDialog(alarm, { timeDialog = false }) { h, m -> alarm = alarm.copy(hour = h, minute = m); timeDialog = false }
    if (songDialog) AlarmSongPicker({ song -> alarm = alarm.copy(song = song); songDialog = false }, { songDialog = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AlarmTimeDialog(alarm: AlarmConfig, dismiss: () -> Unit, confirm: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(alarm.hour, alarm.minute, android.text.format.DateFormat.is24HourFormat(LocalContext.current))
    AlertDialog(dismiss, text = { TimePicker(state) }, confirmButton = { TextButton({ confirm(state.hour, state.minute) }) { Text(stringResource(R.string.done)) } }, dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.cancel)) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AlarmSongPicker(onPick: (AlarmSong) -> Unit, dismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }; var submitted by remember { mutableStateOf<String?>(null) }; var songs by remember { mutableStateOf<List<Song>>(emptyList()) }; var loading by remember { mutableStateOf(false) }
    LaunchedEffect(submitted) { val q = submitted ?: return@LaunchedEffect; loading = true; songs = YtMusicRepository.search(q, SearchFilter.SONGS).getOrDefault(emptyList()).mapNotNull { when (it) { is SearchResult.TopTrack -> it.song; is SearchResult.Track -> it.song; else -> null } }.distinctBy(Song::videoId); loading = false }
    ModalBottomSheet(dismiss) { Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(stringResource(R.string.alarm_choose_song), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(22.dp))
        SearchField(query, { query = it }, { submitted = query.trim().takeIf(String::isNotEmpty) }, placeholder = stringResource(R.string.alarm_search_song), modifier = Modifier.padding(horizontal = 22.dp))
        if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
        LazyColumn(Modifier.heightIn(max = 430.dp)) { items(songs, key = Song::videoId) { song -> SongRow(song, onClick = { onPick(AlarmSong(song.videoId, song.title, song.artist, song.thumbnailUrl, song.durationText)) }) } }
    } }
}

@Composable private fun repeatSummary(days: Set<Int>) = if (days.isEmpty()) stringResource(R.string.alarm_one_time) else DayOfWeek.entries.filter { it.value in days }.joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
@Composable private fun nextSummary(epoch: Long) = stringResource(R.string.alarm_next, remember(epoch) { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epoch)) })
private fun AlarmVolumeButtonAction.labelResource() = when (this) { AlarmVolumeButtonAction.SNOOZE -> R.string.alarm_volume_buttons_snooze; AlarmVolumeButtonAction.STOP -> R.string.alarm_volume_buttons_stop; AlarmVolumeButtonAction.ADJUST_VOLUME -> R.string.alarm_volume_buttons_adjust }

@Composable private fun AlarmPermissionRows() { val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= 34 && !AlarmNotification.canUseFullScreen(context)) TextButton({ runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))) } }, Modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.alarm_allow_full_screen)) }
    if (!AlarmScheduler.canScheduleExact(context)) TextButton({ context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }, Modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.alarm_allow_exact)) }
}
