package com.music.bitchord.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.HEADER_ART_PX
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.ROW_DIVIDER_INSET
import com.music.bitchord.ui.components.SHELF_CARD_WIDTH
import com.music.bitchord.ui.components.SongRow
import com.music.bitchord.ui.components.detailSkeleton

private const val MAX_ARTIST_SONGS = 20
private const val SONGS_PER_COLUMN = 4

/**
 * Album / artist / playlist page. Rendered inside the main content area
 * rather than as a sheet, so the tab bar and mini player stay visible.
 */
@Composable
fun DetailScreen(
    page: DetailPage,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onSectionItemClick: (ShelfItem) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val songs = (page.songs as? UiState.Success)?.data.orEmpty()
    val isArtist = page.type == BrowseType.ARTIST
    val shape = if (isArtist) CircleShape else RoundedCornerShape(10.dp)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PAGE_GUTTER + 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    model = page.thumbnailUrl.artworkAt(HEADER_ART_PX),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shadow(24.dp, shape)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (page.subtitle.isNotBlank()) {
                    Text(
                        text = page.subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        if (songs.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PAGE_GUTTER, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { onSongClick(songs, 0) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                    Button(
                        onClick = { onShuffle(songs) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onBackground,
                        ),
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        when (val state = page.songs) {
            is UiState.Loading -> detailSkeleton(isArtist)
            is UiState.Error -> item { MessageState(state.message) }
            is UiState.Success -> if (isArtist) {
                // An artist's full song list would bury the album shelves, so
                // it pages sideways four at a time and stops at twenty.
                item {
                    Text(
                        text = "Top songs",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(
                            start = PAGE_GUTTER, end = PAGE_GUTTER, top = 10.dp, bottom = 8.dp,
                        ),
                    )
                    val top = state.data.take(MAX_ARTIST_SONGS)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(top.chunked(SONGS_PER_COLUMN)) { column ->
                            Column(Modifier.fillParentMaxWidth(0.88f)) {
                                column.forEach { song ->
                                    CompactSongRow(
                                        song = song,
                                        onClick = { onSongClick(top, top.indexOf(song)) },
                                        onLongPress = { onSongLongPress(song) },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(state.data) { index, song ->
                    SongRow(
                        // Track rows on a release carry no art of their own.
                        song = song.copy(thumbnailUrl = song.thumbnailUrl ?: page.thumbnailUrl),
                        onClick = { onSongClick(state.data, index) },
                        onLongPress = { onSongLongPress(song) },
                        onSwipeToQueue = { onSongSwipe(song) },
                    )
                    if (index < state.data.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }

        // Albums / Singles & EPs carousels (artist pages).
        items(page.sections) { shelf ->
            Column(Modifier.padding(top = 22.dp)) {
                Text(
                    text = shelf.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 10.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(shelf.items) { item ->
                        SectionCard(item = item, onClick = { onSectionItemClick(item) })
                    }
                }
            }
        }
    }
}

/** Compact row used inside the artist song grid; no swipe, to keep the
 *  horizontal pager's gestures unambiguous. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactSongRow(
    song: Song,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.artworkAt(ROW_ART_PX),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onLongPress),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = "More",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SectionCard(item: ShelfItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(SHELF_CARD_WIDTH)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = item.thumbnailUrl.artworkAt(CARD_ART_PX),
            contentDescription = null,
            modifier = Modifier
                .width(SHELF_CARD_WIDTH)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
