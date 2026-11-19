package com.music.bitchord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.smartoffline.ActivityIndex
import com.music.bitchord.data.smartoffline.OfflineTrackSelector
import com.music.bitchord.data.smartoffline.Preferences
import com.music.bitchord.data.smartoffline.ScoredTrack
import com.music.bitchord.data.smartoffline.SelectionResult
import com.music.bitchord.data.smartoffline.SelectionStrategy
import com.music.bitchord.data.smartoffline.SelectorConfig
import com.music.bitchord.download.Downloads

/**
 * The Smart Offline rotation, as a preview.
 *
 * ## What this page is
 *
 * It shows what BitChord *would* keep ready for the listener: the tracks their
 * own listening history points at, in the order it points at them, with the
 * reasoning spelled out. That is the whole of it.
 *
 * ## What this page is not
 *
 * It downloads nothing, keeps nothing and deletes nothing. There is no
 * background work behind it and no storage is set aside. The page says so, in
 * as many words, because a page called "Smart Offline" that quietly fetched
 * files would be lying about what it does.
 *
 * ## Why it is worth showing before it downloads
 *
 * The ranking is the hard part of this feature and the part that cannot be
 * checked by looking at a download queue. A rotation that is right and one
 * that is wrong look identical once the files are on the disk; here they look
 * different, and the listener can say which they wanted.
 */
@Composable
fun SmartOfflineScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val strategy by AppSettings.smartOfflineStrategy.collectAsStateWithLifecycle()
    val windowDays by AppSettings.smartOfflineWindowDays.collectAsStateWithLifecycle()

    var result by remember { mutableStateOf<SelectionResult?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(strategy, windowDays) {
        loading = true
        val downloaded = Downloads.getDownloadedSongs(context)
        val activity = ActivityIndex.load(windowDays)
        result = OfflineTrackSelector.select(
            activity = activity,
            preferences = Preferences.lookup(downloaded),
            config = SelectorConfig(
                strategy = strategy,
                windowDays = windowDays,
                durations = downloaded.durations(),
            ),
        )
        loading = false
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "header") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.smart_offline),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.smart_offline_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        item(key = "strategy") {
            StrategyRow(
                selected = strategy,
                onSelect = AppSettings::setSmartOfflineStrategy,
            )
        }

        item(key = "window") {
            WindowRow(
                selected = windowDays,
                onSelect = AppSettings::setSmartOfflineWindowDays,
            )
        }

        val selection = result
        when {
            loading -> item(key = "loading") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            selection == null || selection.isEmpty -> item(key = "empty") {
                EmptyState()
            }

            else -> {
                if (selection.core.isNotEmpty()) {
                    item(key = "core-header") {
                        SectionTitle(stringResource(R.string.smart_offline_core))
                    }
                    items(selection.core, key = { "core-${it.id}" }) { track ->
                        TrackRow(track)
                    }
                }
                if (selection.rotationVariety.isNotEmpty()) {
                    item(key = "variety-header") {
                        SectionTitle(stringResource(R.string.smart_offline_variety))
                    }
                    items(selection.rotationVariety, key = { "variety-${it.id}" }) { track ->
                        TrackRow(track)
                    }
                }
            }
        }
    }
}

/** The three ways of reading the same history. */
@Composable
private fun StrategyRow(
    selected: SelectionStrategy,
    onSelect: (SelectionStrategy) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.smart_offline_strategy),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StrategyButton(
                label = stringResource(R.string.smart_offline_recently_played),
                chosen = selected == SelectionStrategy.RECENTLY_PLAYED,
                onClick = { onSelect(SelectionStrategy.RECENTLY_PLAYED) },
            )
            StrategyButton(
                label = stringResource(R.string.smart_offline_frequently_played),
                chosen = selected == SelectionStrategy.FREQUENTLY_PLAYED,
                onClick = { onSelect(SelectionStrategy.FREQUENTLY_PLAYED) },
            )
            StrategyButton(
                label = stringResource(R.string.smart_offline_smart),
                chosen = selected == SelectionStrategy.SMART,
                onClick = { onSelect(SelectionStrategy.SMART) },
            )
        }
    }
}

/** How far back the rotation looks. */
@Composable
private fun WindowRow(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.smart_offline_window),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (days in WINDOW_CHOICES) {
                StrategyButton(
                    label = stringResource(R.string.smart_offline_window_days, days),
                    chosen = selected == days,
                    onClick = { onSelect(days) },
                )
            }
        }
    }
}

/** One choice, pressed in or not. */
@Composable
private fun StrategyButton(
    label: String,
    chosen: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (chosen) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (chosen) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The heading between the two halves of the rotation. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** One track, with the reasons it earned its place. */
@Composable
private fun TrackRow(track: ScoredTrack) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val artwork = track.activity.artworkUrl
        if (artwork != null) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = track.activity.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.activity.artist.isNotBlank()) {
                Text(
                    text = track.activity.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = track.reasons.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Nothing to rank yet. */
@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 40.dp),
    ) {
        Text(
            text = stringResource(R.string.smart_offline_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.smart_offline_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** The windows the rotation may be drawn from. */
private val WINDOW_CHOICES = listOf(7, 28, 90)

/**
 * The durations of the tracks already on the device.
 *
 * The listening history does not keep a track's length, so the only lengths
 * available are the ones the downloaded files already state. A track whose
 * length is not known is not judged on how much of it was heard.
 */
private fun List<Song>.durations(): Map<String, Long> {
    val durations = HashMap<String, Long>()
    for (song in this) {
        val duration = song.durationText.durationMillis()
        if (duration > 0L) durations[song.videoId] = duration
    }
    return durations
}
