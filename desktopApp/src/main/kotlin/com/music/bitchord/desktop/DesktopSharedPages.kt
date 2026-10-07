package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.music.bitchord.ui.icons.BitChordIcons
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.SearchHistoryEntity
import com.music.bitchord.data.settings.LibrarySort
import com.music.bitchord.data.settings.LibraryViewType
import com.music.bitchord.ui.AppUiHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

// What the phone's pages — Home, Explore, Library, Search, shared from :sharedUi —
// read from the app around them, answered from this computer's own stores.

/** The shared pages' settings and downloads, from the desktop's preferences. */
internal object DesktopAppUiHost : AppUiHost {

    private val persistence = DesktopPersistence()

    override val swipeToPlayNext: StateFlow<Boolean> =
        MutableStateFlow(persistence.boolean(KEY_SWIPE_PLAY_NEXT, false))

    private val _homeRecentsViewType = MutableStateFlow(
        runCatching { LibraryViewType.valueOf(persistence.string(KEY_HOME_RECENTS_VIEW, LibraryViewType.LIST.name)) }
            .getOrDefault(LibraryViewType.LIST),
    )
    override val homeRecentsViewType: StateFlow<LibraryViewType> = _homeRecentsViewType

    override fun setHomeRecentsViewType(value: LibraryViewType) {
        persistence.saveString(KEY_HOME_RECENTS_VIEW, value.name)
        _homeRecentsViewType.value = value
    }

    /** Nothing is pinned here: the desktop has no pin action to put anything first. */
    override val pinnedPlaylists: StateFlow<List<String>> = MutableStateFlow(emptyList())

    private val _librarySort = MutableStateFlow(
        runCatching { LibrarySort.valueOf(persistence.string(KEY_LIBRARY_SORT, LibrarySort.DEFAULT.name)) }
            .getOrDefault(LibrarySort.DEFAULT),
    )
    override val librarySort: StateFlow<LibrarySort> = _librarySort

    fun setLibrarySort(value: LibrarySort) {
        persistence.saveString(KEY_LIBRARY_SORT, value.name)
        _librarySort.value = value
    }

    /** Kept current by the app, which owns the download list — see `BitChordDesktopApp`. */
    val downloaded = MutableStateFlow<Set<String>>(emptySet())

    @Composable
    override fun downloadedIds(): Set<String> {
        val ids by downloaded.collectAsState()
        return ids
    }

    private const val KEY_SWIPE_PLAY_NEXT = "swipe_play_next"
    private const val KEY_HOME_RECENTS_VIEW = "home_recents_view"
}

/**
 * Recent searches as the phone keeps them: the thing that was picked — a track,
 * an album, an artist, a playlist — with its artwork, rather than the words
 * typed to find it. Tapping one goes straight back to it.
 */
internal object DesktopSearchHistory {

    private val persistence = DesktopPersistence()
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SearchHistoryEntity.serializer())

    private val _recent = MutableStateFlow(load())
    val recent: StateFlow<List<SearchHistoryEntity>> = _recent

    /** Puts [entity] at the top, dropping any earlier record of the same thing. */
    fun record(entity: SearchHistoryEntity) {
        if (entity.id.isBlank()) return
        save((listOf(entity) + _recent.value.filterNot { it.id == entity.id }).take(MAX_ENTRIES))
    }

    fun remove(id: String) = save(_recent.value.filterNot { it.id == id })

    fun clear() = save(emptyList())

    private fun save(value: List<SearchHistoryEntity>) {
        _recent.value = value
        persistence.saveString(KEY, json.encodeToString(serializer, value))
    }

    private fun load(): List<SearchHistoryEntity> =
        runCatching { json.decodeFromString(serializer, persistence.string(KEY, "[]")) }.getOrDefault(emptyList())

    private const val KEY = "search_entities"
    private const val MAX_ENTRIES = 20
}

/**
 * Opens a page's menus where the pointer is.
 *
 * The shared pages say "this row was held" — a long press on the phone, a
 * right-click here — without saying where it was; the desktop's menus hang off
 * an anchor. So this watches the pointer over the page without taking any of
 * its events, and plants a one-point anchor wherever it last was when a menu is
 * asked for.
 *
 * [menu] draws the menu for whatever was asked for; [content] is handed the
 * call that asks.
 */
@Composable
internal fun <T : Any> DesktopPointerMenuHost(
    menu: @Composable (target: T, onDismiss: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (open: (T) -> Unit) -> Unit,
) {
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var target by remember { mutableStateOf<Pair<T, Offset>?>(null) }
    Box(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.firstOrNull()?.let { pointer = it.position }
                    }
                }
            },
    ) {
        content { item -> target = item to pointer }
        target?.let { (item, at) ->
            Box(
                Modifier
                    .offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }
                    .size(1.dp),
            ) {
                menu(item) { target = null }
            }
        }
    }
}

/** The phone's sort for a Library "Show all" grid: the one thing its cards carry is a title. */
@Composable
internal fun DesktopLibrarySortMenu() {
    val sort by DesktopAppUiHost.librarySort.collectAsState()
    var open by remember { mutableStateOf(false) }
    Box {
        DesktopToolbarButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, DesktopStrings["sort_library", "Sort library"], tint = Color.White)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (option) {
                                LibrarySort.DEFAULT -> DesktopStrings["sort_default", "Default order"]
                                LibrarySort.TITLE_ASC -> DesktopStrings["sort_title_ascending", "Alphabetical (A to Z)"]
                                LibrarySort.TITLE_DESC -> DesktopStrings["sort_title_descending", "Alphabetical (Z to A)"]
                            },
                        )
                    },
                    trailingIcon = if (option == sort) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        DesktopAppUiHost.setLibrarySort(option)
                        open = false
                    },
                )
            }
        }
    }
}

/**
 * The phone's Play control on a release or artist page (DetailScreen's `PlayPill`): a white
 * surface with black on it, so the primary action survives every palette. A release asks for the
 * icon-only circle; an artist keeps the labelled pill.
 */
@Composable
internal fun DesktopPlayPill(
    label: String,
    onClick: () -> Unit,
    iconOnly: Boolean = false,
    size: Dp = 50.dp,
) {
    Row(
        Modifier
            .then(if (iconOnly) Modifier.size(size) else Modifier.height(size))
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick)
            .then(if (iconOnly) Modifier else Modifier.padding(horizontal = 32.dp)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            BitChordIcons.Play,
            contentDescription = if (iconOnly) label else null,
            tint = Color.Black,
            modifier = Modifier.size(if (iconOnly) size * 0.44f else 18.dp),
        )
        if (!iconOnly) {
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, color = Color.Black)
        }
    }
}

/**
 * The phone's round icon button beside Play (DetailScreen's `CircleIconButton`): a translucent
 * disc — the phone's is glass over the page's artwork tint — with the glyph in white.
 */
@Composable
internal fun DesktopCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp = 50.dp,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(size * 0.44f))
    }
}
