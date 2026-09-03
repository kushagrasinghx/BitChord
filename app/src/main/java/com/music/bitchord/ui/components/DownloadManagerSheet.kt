package com.music.bitchord.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.download.DownloadProgress
import com.music.bitchord.download.DownloadSession
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import androidx.compose.ui.res.pluralStringResource

/**
 * The download indicator in the top bar, beside the account photo.
 *
 * Absent until something is actually downloading, and absent again once the user
 * has looked at the result — neither of which is this composable's decision.
 * [DownloadSession.State.visible] owns both, because "should this be on screen"
 * is a question about a batch of work rather than about a bar, and the same
 * answer has to hold whichever page is showing.
 *
 * It draws the batch's overall progress rather than the running track's. The
 * track is what the notification reports on; from here the interesting number is
 * how much of the *album* is left, and a ring that restarts from zero forty
 * times says nothing about that. Once the queue is quiet the ring is dropped for
 * a tick or a warning — a full circle and a finished job look identical, and only
 * one of them is worth walking over to.
 */
@Composable
fun TopBarDownloadButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val session by DownloadSession.state.collectAsStateWithLifecycle()
    if (!session.visible) return

    val haptics = rememberHaptics()
    // Animated, because the fraction lands in steps — one track at a time, plus
    // whatever the running one reports — and a ring that jumps in twenty-fifths
    // reads as a stutter rather than as progress.
    val progress by animateFloatAsState(
        targetValue = session.fraction,
        animationSpec = tween(300),
        label = "downloadRingProgress",
    )
    val failed = session.failed > 0
    val tint by animateColorAsState(
        targetValue = when {
            failed -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(220),
        label = "downloadRingTint",
    )

    IconButton(
        onClick = {
            haptics.play(Haptic.Select)
            onClick()
        },
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (session.busy) {
                CircularProgressIndicator(
                    // Never quite zero: a ring pinned at nothing reads as
                    // stalled where the first sliver reads as starting.
                    progress = { progress.coerceAtLeast(0.02f) },
                    modifier = Modifier.size(RING_SIZE),
                    color = tint,
                    trackColor = tint.copy(alpha = 0.22f),
                    strokeWidth = 2.dp,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                )
            }
            Icon(
                imageVector = when {
                    failed -> Icons.Rounded.ErrorOutline
                    session.busy -> Icons.Rounded.Downloading
                    else -> Icons.Rounded.DownloadDone
                },
                contentDescription = when {
                    session.busy -> "Downloads · ${(session.fraction * 100).toInt()}%"
                    failed -> "Downloads · ${session.failed} failed"
                    else -> "Downloads · finished"
                },
                tint = tint,
                modifier = Modifier.size(if (session.busy) GLYPH_IN_RING else GLYPH_SIZE),
            )
        }
    }
}

/**
 * The list behind that indicator: every track asked for this session, what
 * became of it, and a way out of the ones still going.
 *
 * A list rather than a single line because the thing being reported on is a
 * batch. `SongActionsSheet`'s download row already answers "what about *this*
 * song" perfectly well and is the right size for that question; the question
 * here is the one it cannot answer — forty tracks were asked for, which of them
 * arrived — and that has as many answers as there were tracks.
 *
 * Rows carry the cover, the title and the credit because a filename is not how
 * anybody remembers a song, and because a batch download is precisely when a
 * user cannot tell from a name whether the right thing is being fetched: the
 * track that fails is one of forty and the only way to recognise it is to see it.
 *
 * @param onDismiss closes the sheet. Called by the header's own control rather
 *   than left to the drag, so there is something obvious to press once the list
 *   is read — and the host marks the batch seen on the way out, which is what
 *   takes the indicator down.
 */
@Composable
fun DownloadManagerSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val session by DownloadSession.state.collectAsStateWithLifecycle()
    // Newest ask last, the order the queue will actually reach them in.
    val items = remember(session.items) { session.items.sortedBy { it.sequence } }

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.downloads),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = session.summary(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (session.failed > 0 && !session.busy) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (session.busy) {
                TextButton(
                    onClick = {
                        // Only what is still going. Cancelling a finished row
                        // would drop it from the list, which is the one thing
                        // this sheet exists to still be showing.
                        items.filterNot { it.progress.settled }
                            .forEach { Downloads.cancel(it.videoId) }
                    },
                ) {
                    Text(stringResource(R.string.cancel_all))
                }
            } else {
                TextButton(
                    onClick = {
                        DownloadSession.clear()
                        onDismiss()
                    },
                ) {
                    Text(stringResource(R.string.clear))
                }
            }
        }

        // The batch's own bar, under the heading it belongs to. The per-row bars
        // below are about one track each; this is the one that answers "how much
        // longer", which is what someone opening this sheet mid-album wants.
        if (session.busy) {
            LinearProgressIndicator(
                progress = { session.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(8.dp))
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)

        // Capped rather than left to grow: a hundred-track playlist would
        // otherwise be a sheet that covers the screen and has to be scrolled
        // back up before anything else can be reached.
        LazyColumn(Modifier.heightIn(max = LIST_MAX_HEIGHT)) {
            items(items, key = { it.videoId }) { item ->
                DownloadManagerRow(
                    item = item,
                    onCancel = { Downloads.cancel(item.videoId) },
                    onRetry = { Downloads.enqueue(context, item.song, item.from) },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** One track: its cover, what it is, and where it has got to. */
@Composable
private fun DownloadManagerRow(
    item: DownloadSession.Item,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val progress = item.progress
    val failed = progress as? DownloadProgress.Failed

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(ART_SIZE), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = item.song.artworkAt(ROW_ART_PX),
                contentDescription = null,
                modifier = Modifier
                    .size(ART_SIZE)
                    .clip(RoundedCornerShape(8.dp))
                    .thumbnailBorder(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            // A finished or failed track is stated over its own cover rather
            // than in a fourth column: the list is scanned for the odd one out,
            // and a mark on the artwork is what the eye actually lands on.
            if (progress.settled) {
                Box(
                    modifier = Modifier
                        .size(ART_SIZE)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (failed != null) {
                            Icons.Rounded.ErrorOutline
                        } else {
                            Icons.Rounded.DownloadDone
                        },
                        contentDescription = null,
                        tint = if (failed != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            Color.White
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.song.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // The release is worth naming where there is one: in a list of
                // forty rows off three albums, the credit alone does not say
                // which batch a row belongs to.
                text = listOfNotNull(
                    item.song.artist.takeIf { it.isNotBlank() },
                    item.from?.takeIf { it.isNotBlank() && it != item.song.artist },
                ).joinToString(" · ").ifBlank { stringResource(R.string.unknown_artist) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            when (progress) {
                is DownloadProgress.Queued -> RowStatus(stringResource(R.string.download_row_queued))
                is DownloadProgress.Running -> {
                    LinearProgressIndicator(
                        // Indeterminate until the first response names a
                        // length — a bar frozen at nothing reads as broken.
                        progress = { progress.fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.height(3.dp))
                    RowStatus(
                        if (progress.fraction > 0f) {
                            stringResource(R.string.downloading_percent_x, (progress.fraction * 100).toInt())
                        } else {
                            stringResource(R.string.download_notification_starting)
                        },
                    )
                }
                is DownloadProgress.Done -> RowStatus("Saved to Music/BitChord")
                is DownloadProgress.Failed ->
                    RowStatus(progress.reason, MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.width(8.dp))
        // One control, and which one it is follows the row's state: a running
        // track can be stopped, a failed one can be asked for again, and a
        // finished one needs nothing at all.
        when {
            failed != null -> RowAction(Icons.Rounded.Refresh, stringResource(R.string.retry), onRetry)
            !progress.settled -> RowAction(Icons.Rounded.Close, stringResource(R.string.cancel), onCancel)
            else -> Spacer(Modifier.width(36.dp))
        }
    }
}

@Composable
private fun RowStatus(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun RowAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The one line under the heading — what state the batch as a whole is in.
 *
 * Counts rather than a percentage: the sheet already draws the percentage as a
 * bar, and what a percentage cannot say is that thirty-nine of forty arrived and
 * one did not, which is the only outcome anybody needs to act on.
 */
@Composable
private fun DownloadSession.State.summary(): String {
    val parts = buildList {
        if (waiting > 0) add(pluralStringResource(R.plurals.batch_waiting, waiting, waiting))
        if (finished > 0) add(pluralStringResource(R.plurals.batch_done, finished, finished))
        if (failed > 0) add(pluralStringResource(R.plurals.batch_failed, failed, failed))
    }
    return when {
        parts.isEmpty() -> stringResource(R.string.nothing_downloading)
        busy -> parts.joinToString(" · ")
        failed > 0 -> parts.joinToString(" · ")
        else -> pluralStringResource(R.plurals.batch_downloaded, finished, finished)
    }
}

/** The progress ring in the bar, sized to the account photo beside it. */
private val RING_SIZE = 26.dp

/** The glyph inside that ring, small enough to leave the stroke clear. */
private val GLYPH_IN_RING = 15.dp

/** With no ring around it, the glyph is a normal bar icon. */
private val GLYPH_SIZE = 22.dp

/** A row's cover: under a track row's 52dp, since this is a status list. */
private val ART_SIZE = 44.dp

/** How much of the screen the list may take before it scrolls instead. */
private val LIST_MAX_HEIGHT = 380.dp
