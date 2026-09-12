package com.music.bitchord.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.system.exitProcess
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.music.bitchord.data.model.artworkAt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials
import com.music.bitchord.data.settings.AutomixPerformanceMode
import com.music.bitchord.data.settings.SmartAnalysis
import com.music.bitchord.data.settings.TrackAnalysisState
import com.music.bitchord.data.settings.TransitionWindow
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.SubscriptionState
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.model.isSameTrackAs
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.HEADER_ART_PX
import com.music.bitchord.data.model.PLAYER_ART_PX
import com.music.bitchord.data.model.ROW_ART_PX
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import bitchord.desktopapp.generated.resources.Res
import bitchord.desktopapp.generated.resources.logo
import bitchord.desktopapp.generated.resources.sf_pro_display_bold
import bitchord.desktopapp.generated.resources.sf_pro_display_heavy
import bitchord.desktopapp.generated.resources.sf_pro_display_medium
import bitchord.desktopapp.generated.resources.sf_pro_display_regular
import bitchord.desktopapp.generated.resources.sf_pro_display_semibold
import org.jetbrains.compose.resources.Font as composeFont
import org.jetbrains.compose.resources.painterResource
import java.util.UUID

/**
 * The margin every page keeps from the window's edge.
 *
 * One value, named, because the pages were carrying their own: most used 28 and Replay used 38, so
 * opening Replay after anything else stepped the whole page inward by ten points.
 */
internal val DesktopPageGutter = 28.dp

internal val DesktopBackground = Color.Black
internal val DesktopSurface = Color(0xFF0D0D0F)
private val DesktopSurfaceRaised = Color(0xFF1C1C1E)
internal val DesktopGlass = Color(0x661C1C1E)
internal val DesktopGlassStrong = Color(0xCC0D0D0F)

/** The floating bars in the narrow layout. */
private val DesktopBarGlass = Color(0xB324242A)

/** The backdrop the floating bars blur. */
private val LocalDesktopHaze = staticCompositionLocalOf<HazeState?> { null }

/** How much a page's scrollable should keep clear of the floating bars. */
private val LocalDesktopBottomInset = staticCompositionLocalOf { 0.dp }

/** Android's own edge on these bars: `Color.White.copy(alpha = 0.10f)`. */
private val DesktopBarEdge = Color(0x1AFFFFFF)
internal val DesktopAccent = Color(0xFFFA2D48)
internal val DesktopSecondary = Color(0xFF8E8E93)
internal val DesktopDivider = Color(0xFF2C2C2E)
private const val STATS_SAMPLE_MS = 5_000L

/** How often the playhead is offered to the history tracker. */
private const val HISTORY_SAMPLE_MS = 5_000L

/** How long the search field is left alone before its typeahead is asked for. */
private const val SUGGESTION_DEBOUNCE_MS = 220L

/** Line spacing with the leading trimmed off both ends. */
private val TIGHT_LINE_HEIGHT = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

/** How many tracks a station is built with when one is started explicitly. */
private const val INITIAL_RADIO_TRACKS = 24

/** An artist's top songs, capped so the release shelves are not buried. */
private const val MAX_ARTIST_SONGS = 20

/** The artist photograph's height. */
private val ARTIST_BANNER_HEIGHT = 340.dp

/** The library shelf the account's own playlists arrive on, and the one edits are spliced into. */
private const val PLAYLISTS_SHELF = "Playlists"
private const val STATS_MAX_DELTA_MS = 15_000L
private const val SCROBBLE_THRESHOLD_MS = 180_000L

@Composable
private fun desktopTypography(): Typography {
    val sfProDisplay = FontFamily(
        composeFont(Res.font.sf_pro_display_regular, FontWeight.W400),
        composeFont(Res.font.sf_pro_display_medium, FontWeight.W500),
        composeFont(Res.font.sf_pro_display_semibold, FontWeight.W600),
        composeFont(Res.font.sf_pro_display_bold, FontWeight.W700),
        composeFont(Res.font.sf_pro_display_heavy, FontWeight.W800),
    )
    val defaults = Typography()
    return defaults.copy(
        displayLarge = defaults.displayLarge.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W800, fontSize = 34.sp, letterSpacing = (-0.8).sp),
        displayMedium = defaults.displayMedium.copy(fontFamily = sfProDisplay),
        displaySmall = defaults.displaySmall.copy(fontFamily = sfProDisplay),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W800, fontSize = 30.sp, letterSpacing = (-0.7).sp),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W700, fontSize = 22.sp, letterSpacing = (-0.4).sp),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = sfProDisplay),
        titleLarge = defaults.titleLarge.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W700, fontSize = 20.sp, letterSpacing = (-0.3).sp),
        titleMedium = defaults.titleMedium.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W600, fontSize = 16.sp, letterSpacing = (-0.2).sp),
        titleSmall = defaults.titleSmall.copy(fontFamily = sfProDisplay),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W400, fontSize = 16.sp),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W400, fontSize = 14.sp),
        bodySmall = defaults.bodySmall.copy(fontFamily = sfProDisplay),
        labelLarge = defaults.labelLarge.copy(fontFamily = sfProDisplay),
        labelMedium = defaults.labelMedium.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W600, fontSize = 12.sp),
        labelSmall = defaults.labelSmall.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W600, fontSize = 11.sp),
    )
}

private enum class DesktopDestination(val id: String, val label: String) {
    LISTEN_NOW("listen-now", "Listen Now"),
    EXPLORE("explore", "Explore"),
    LIBRARY("library", "Library"),
    SEARCH("search", "Search"),
    HISTORY("history", "History"),
    DOWNLOADS("downloads", "Downloads"),
    LOCAL_MUSIC("local-music", "Local Music"),
    SETTINGS("settings", "Settings"),
}

private enum class DesktopRepeatMode {
    OFF,
    ALL,
    ONE,
    ;

    fun next(): DesktopRepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }

    fun label(): String = when (this) {
        OFF -> "Off"
        ALL -> "All"
        ONE -> "One"
    }
}

@Composable
fun BitChordDesktopApp() {
    val scope = rememberCoroutineScope()
    val persistence = remember { DesktopPersistence() }
    var destination by remember { mutableStateOf(DesktopDestination.LISTEN_NOW) }
    var query by remember { mutableStateOf("") }
    var searchFilter by remember { mutableStateOf(SearchFilter.ALL) }
    var searchRows by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    // What has been searched before, and what YouTube thinks is being typed.
    var searchHistory by remember { mutableStateOf(persistence.searchHistory()) }
    var searchSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    // False while the query is being set *by* the app.
    var searchTyping by remember { mutableStateOf(false) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var homeState by remember { mutableStateOf<UiState<List<HomeShelf>>>(UiState.Loading) }
    // The home feed is paged: `FEmusic_home` answers with a couple of shelves and a token, and
    // everything else arrives by following it.
    var homeContinuation by remember { mutableStateOf<String?>(null) }
    var homeLoadingMore by remember { mutableStateOf(false) }
    var homeSupplementsLoaded by remember { mutableStateOf(false) }
    var exploreState by remember { mutableStateOf<UiState<List<MoodGenreSection>>>(UiState.Loading) }
    var selectedMoodGenre by remember { mutableStateOf<MoodGenre?>(null) }
    // Bumped by a retry so the loaders below re-run without the state they are keyed on having to
    // change to something and back again.
    var exploreReloads by remember { mutableStateOf(0) }
    var moodGenreReloads by remember { mutableStateOf(0) }
    var moodGenreShelves by remember { mutableStateOf<UiState<List<HomeShelf>>>(UiState.Loading) }
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    // The live queue: what is playing, what played before it, what is next.
    var liveQueue by remember {
        mutableStateOf(
            DesktopQueue.restored(
                persistence.queue(),
                persistence.string("queue_index", "0").toIntOrNull() ?: 0,
            ),
        )
    }
    // The order the queue was in before shuffle rearranged it, so the toggle can be undone.
    var preShuffleOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    val queue = liveQueue.songs
    // What was played on this computer. The account's own history replaces it
    // while signed in — see [remoteHistory] — because that is what Android's
    // History screen is and what makes a phone and a desktop agree.
    var history by remember { mutableStateOf(persistence.history()) }
    var remoteHistory by remember { mutableStateOf<List<Song>>(emptyList()) }
    var likedIds by remember { mutableStateOf(persistence.likedIds()) }
    var dislikedIds by remember { mutableStateOf(persistence.dislikedIds()) }
    val overlays = remember { DesktopOverlays() }
    var downloads by remember { mutableStateOf(persistence.downloads()) }
    var localSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val downloadQueue by DesktopDownloadQueue.active.collectAsState()
    val downloadInProgress = downloadQueue.keys
    var playlists by remember { mutableStateOf(persistence.playlists()) }
    var statsRevision by remember { mutableStateOf(0L) }
    var replayPeriod by remember { mutableStateOf(DesktopReplayPeriod.ALL_TIME) }
    val replayHolder = remember { DesktopAccounts.active()?.name.orEmpty() }
    var replaySummary by remember { mutableStateOf(DesktopListeningStats.summary()) }
    var statsCountedSongs by remember { mutableStateOf<Set<String>>(emptySet()) }
    var playlistTarget by remember { mutableStateOf<Song?>(null) }
    // One busy flag and one message for every playlist edit: only one of them can be in flight,
    // because each is driven by a dialog that is modal.
    var playlistBusy by remember { mutableStateOf(false) }
    var playlistError by remember { mutableStateOf<String?>(null) }
    // The album and artist pages the playing track belongs to, looked up while
    // the player is open. Whatever started a track knew its title and artwork
    // but rarely its ids — a home-feed tile carries neither — so without this
    // the credit under the title is not a link at all. Android does the same
    // thing, and for the same reason; see its `links` in MainActivity.
    var trackLinks by remember { mutableStateOf<Song?>(null) }
    // Which side panel the player is showing.
    var playerPanel by remember { mutableStateOf(DesktopPlayerPanel.LYRICS) }
    // Settings is a modal over whatever page is open, the way Music puts its own preferences in a
    // sheet rather than a navigation destination.
    var autoplay by remember { mutableStateOf(persistence.boolean("autoplay", true)) }
    var dontRepeatSuggestions by remember {
        mutableStateOf(persistence.boolean(KEY_DONT_REPEAT_SUGGESTIONS, false))
    }
    /** Everything AutoPlay has played or offered this run — see [KEY_DONT_REPEAT_SUGGESTIONS]. */
    val sessionSongHistory = remember { mutableListOf<Song>() }
    var filterNonMusicAudio by remember {
        mutableStateOf(persistence.boolean(DesktopLocalMusic.KEY_FILTER_NON_MUSIC_AUDIO, false))
    }
    var localMusicRevision by remember { mutableStateOf(0) }
    var shelfSort by remember {
        mutableStateOf(
            runCatching { DesktopShelfSort.valueOf(persistence.string(KEY_LIBRARY_SORT, "DEFAULT")) }
                .getOrDefault(DesktopShelfSort.DEFAULT),
        )
    }
    // The track the queued station was built around, so a top-up is asked for once per song rather
    // than once per recomposition.
    var autoplaySeed by remember { mutableStateOf<String?>(null) }
    var autoplayJob by remember { mutableStateOf<Job?>(null) }
    var automix by remember { mutableStateOf(persistence.boolean("automix", false)) }
    var automixPerformance by remember {
        mutableStateOf(
            runCatching { AutomixPerformanceMode.valueOf(persistence.string("automix_performance", "BALANCED")) }
                .getOrDefault(AutomixPerformanceMode.BALANCED),
        )
    }
    var shuffle by remember { mutableStateOf(persistence.boolean("shuffle", false)) }
    var repeatMode by remember {
        mutableStateOf(
            runCatching { DesktopRepeatMode.valueOf(persistence.string("repeat_mode", "OFF")) }
                .getOrDefault(DesktopRepeatMode.OFF),
        )
    }
    var playbackSpeed by remember { mutableStateOf(persistence.string("playback_speed", "1.0").toFloatOrNull() ?: 1.0f) }
    var volume by remember { mutableStateOf(persistence.string("volume", "1.0").toFloatOrNull()?.coerceIn(0.0f, 1.0f) ?: 1.0f) }
    var crossfadeSeconds by remember { mutableStateOf(persistence.string("crossfade_seconds", "0").toIntOrNull()?.coerceIn(0, 12) ?: 0) }
    var downloadQuality by remember { mutableStateOf(persistence.string("download_quality", "LOSSLESS")) }
    var trayIconEnabled by remember { mutableStateOf(persistence.boolean("tray_icon", true)) }
    var closeToTray by remember { mutableStateOf(persistence.boolean("close_to_tray", true)) }
    // The three the FFmpeg engine unlocked: none of them could exist while JavaFX owned the decode,
    // because none of them can be done without the samples themselves.
    var spatialAudio by remember { mutableStateOf(persistence.boolean("spatial_audio", false)) }
    var skipSilence by remember { mutableStateOf(persistence.boolean("skip_silence", false)) }
    var outputPrecision by remember { mutableStateOf(persistence.string("output_precision", "PCM_16")) }
    // The blob backdrop the mesh replaced, kept as an opt-out.
    var legacyMeshGradient by remember { mutableStateOf(persistence.boolean("legacy_mesh_gradient", false)) }
    var animatedCanvas by remember { mutableStateOf(persistence.boolean("animated_canvas", true)) }
    var spotifyCanvasCookie by remember { mutableStateOf(DesktopSpotifyToken.cookie()) }
    var showNerdStats by remember { mutableStateOf(persistence.boolean("show_nerd_stats", false)) }
    var syncedLyrics by remember {
        mutableStateOf(persistence.boolean(DesktopLyricsClient.KEY_SYNCED_LYRICS, true))
    }
    var lyricsBlur by remember {
        mutableStateOf(persistence.boolean(DesktopLyricsClient.KEY_LYRICS_BLUR, true))
    }
    var prioritizeSyllables by remember {
        mutableStateOf(persistence.boolean(DesktopLyricsClient.KEY_PRIORITIZE_SYLLABLES, false))
    }
    var lyricsOrder by remember { mutableStateOf(persistence.lyricsSourceOrder()) }
    var lyricsOn by remember { mutableStateOf(persistence.lyricsEnabledSources()) }
    var fullBleedArtwork by remember { mutableStateOf(persistence.boolean("full_bleed_artwork", false)) }
    var accounts by remember { mutableStateOf(DesktopAccounts.accounts()) }
    var activeAccountId by remember { mutableStateOf(DesktopAccounts.activeAccountId()) }
    var activeProfileId by remember { mutableStateOf(DesktopAccounts.activeProfileId()) }
    var signInBusy by remember { mutableStateOf<String?>(null) }
    var signInError by remember { mutableStateOf<String?>(null) }
    val activeAccount = accounts.firstOrNull { it.accountId == activeAccountId } ?: accounts.firstOrNull()
    // The account's own library, fetched once a session is in force.
    var libraryState by remember { mutableStateOf<UiState<LibraryPage>>(UiState.Loading) }
    // Whether YouTube is actually accepting the session, which is not the same as having an account
    // saved.
    var youtubeSignedIn by remember { mutableStateOf(false) }
    // Set by an edit that the library feed has not caught up with yet.
    var libraryStale by remember { mutableStateOf(false) }
    // Bumped when the session changes underneath, so the loader below re-runs without anything it
    // is keyed on having to change to something and back.
    var sessionRevision by remember { mutableStateOf(0) }
    // Read off the library rather than fetched again: the Playlists shelf is the same feed the
    // picker wants, minus the entries no edit can be aimed at.
    val accountPlaylists = remember(libraryState) {
        (libraryState as? UiState.Success)?.data?.shelves
            ?.firstOrNull { it.title == PLAYLISTS_SHELF }
            ?.items
            ?.let(DesktopSearchClient::userPlaylists)
            .orEmpty()
    }

    fun reloadLibrary() {
        libraryState = UiState.Loading
        sessionRevision++
    }

    /** Edits the Playlists shelf in place, without re-fetching it. */
    fun editPlaylistShelf(edit: (List<ShelfItem>) -> List<ShelfItem>) {
        val page = (libraryState as? UiState.Success)?.data ?: return
        val index = page.shelves.indexOfFirst { it.title == PLAYLISTS_SHELF }
        val shelves = if (index >= 0) {
            page.shelves.toMutableList().apply {
                this[index] = this[index].copy(items = edit(this[index].items))
            }
        } else {
            // A fresh account has no Playlists shelf at all, and it is exactly the account most
            // likely to be making its first one.
            val items = edit(emptyList())
            if (items.isEmpty()) return
            listOf(HomeShelf(PLAYLISTS_SHELF, items)) + page.shelves
        }
        libraryState = UiState.Success(page.copy(shelves = shelves.toList()))
    }

    // Whoever was signed in last is signed in again, before anything asks YouTube for something
    // that depends on it.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { DesktopAccounts.activate() }
        sessionRevision++
    }
    var audioQuality by remember { mutableStateOf(persistence.audioQuality()) }
    var sourceConfigs by remember { mutableStateOf(persistence.sourceConfigs()) }
    var sourceStatus by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val youtubeSourceEnabled = sourceConfigs.any {
        it.kind == DesktopSourceKind.YOUTUBE && it.enabled
    }
    val sleepTimerMinutes by DesktopSleepTimer.minutes.collectAsState()
    val sleepAfterTrack by DesktopSleepTimer.afterTrack.collectAsState()
    var sleepTimerTick by remember { mutableStateOf(0L) }
    LaunchedEffect(sleepTimerMinutes) {
        while (sleepTimerMinutes != null && currentCoroutineContext().isActive) {
            delay(1_000L)
            sleepTimerTick++
        }
    }
    val sleepRemainingMs = remember(sleepTimerMinutes, sleepTimerTick) {
        DesktopSleepTimer.remainingMs()
    }
    var openedCollection by remember { mutableStateOf<DesktopCollection?>(null) }
    var collectionLoadingMore by remember { mutableStateOf(false) }
    // The artist page is its own destination rather than a collection with a different header.
    var openedArtist by remember { mutableStateOf<DesktopArtistTarget?>(null) }
    var artistState by remember { mutableStateOf<UiState<ArtistPage>>(UiState.Loading) }
    var artistReloads by remember { mutableStateOf(0) }

    fun canonicalSong(song: Song): Song {
        val homeSong = (homeState as? UiState.Success)?.data.orEmpty()
            .asSequence()
            .flatMap { it.items.asSequence() }
            .filter { it.videoId == song.videoId }
            .map(ShelfItem::toSong)
            .firstOrNull()
        return if (homeSong != null && song.artist.isUnknownArtist()) {
            song.copy(
                artist = homeSong.artist,
                durationText = song.durationText ?: homeSong.durationText,
                thumbnailUrl = song.thumbnailUrl ?: homeSong.thumbnailUrl,
            )
        } else {
            song
        }
    }

    lateinit var startSong: (Song, Boolean) -> Unit
    lateinit var playbackEngine: DesktopPlaybackEngine

    fun saveQueue() {
        persistence.saveQueue(liveQueue.songs)
        persistence.saveString("queue_index", liveQueue.index.toString())
    }

    /** Opens whatever the queue is currently pointing at. */
    fun playCurrent(startPlaying: Boolean = true) {
        val song = liveQueue.current ?: return
        selectedSong = song
        history = (listOf(song) + history.filterNot { it.videoId == song.videoId }).take(50)
        saveQueue()
        persistence.saveHistory(history)
        playbackEngine.load(song, startPlaying)
        scope.launch { DesktopScrobbling.updateNowPlaying(song) }
    }

    /** A song played on its own — from a search row, a shelf card, history. */
    fun playSong(song: Song, startPlaying: Boolean = true) {
        liveQueue = DesktopQueue.of(canonicalSong(song))
        playCurrent(startPlaying)
    }

    /** Takes a captured cookie all the way to a saved account. */
    suspend fun signIn(cookie: String, label: String, sourceProfile: String? = null) {
        signInError = null
        signInBusy = label
        try {
            val scope = withContext(Dispatchers.IO) { DesktopYouTubeSession.adoptSessionScope(cookie) }
            if (scope == null) {
                signInError = "$label is not signed in to YouTube Music."
                return
            }
            DesktopYouTubeAuth.adopt(scope)
            // Asked with the session in force, so they describe the account that was just captured
            // rather than nobody in particular.
            val details = DesktopSearchClient.accountMenu().getOrNull()?.let(DesktopAccountParser::account)
            val channels = DesktopSearchClient.accountsList().getOrNull()
                ?.let(DesktopAccountParser::channels)
                .orEmpty()
            // Off the UI thread: saving reaches the platform keyring over D-Bus, and a blocking
            // call to another process is not something to do on the thread that is drawing.
            withContext(Dispatchers.IO) { DesktopAccounts.save(cookie, scope, details, channels, sourceProfile) }
            accounts = withContext(Dispatchers.IO) { DesktopAccounts.accounts() }
            activeAccountId = DesktopAccounts.activeAccountId()
            activeProfileId = DesktopAccounts.activeProfileId()
            overlays.signIn = false
            overlays.accounts = false
        } finally {
            signInBusy = null
        }
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val playable = songs.map(::canonicalSong)
        liveQueue = if (shuffle) {
            DesktopQueue.shuffledStartingAt(playable, startIndex)
        } else {
            // Rows above the one picked have not played, so they are not history and do not belong
            // behind the needle.
            DesktopQueue.startingAt(playable, startIndex)
        }
        preShuffleOrder = if (shuffle) playable.map(Song::videoId) else emptyList()
        playCurrent()
    }

    /** Moves within the queue, dropping whatever a forward jump passed over. */
    fun playQueueIndex(target: Int) {
        if (target !in liveQueue.songs.indices) return
        liveQueue = liveQueue.jumpTo(target)
        playCurrent()
    }

    fun cycleSleepTimer() {
        when {
            sleepAfterTrack -> DesktopSleepTimer.cancel()
            sleepTimerMinutes == null -> DesktopSleepTimer.start(DesktopSleepTimer.presets.first())
            sleepTimerMinutes == DesktopSleepTimer.presets.last() -> DesktopSleepTimer.startAfterTrack()
            else -> {
                val current = DesktopSleepTimer.presets.indexOf(sleepTimerMinutes)
                DesktopSleepTimer.start(DesktopSleepTimer.presets.getOrElse(current + 1) { DesktopSleepTimer.presets.last() })
            }
        }
    }
    startSong = ::playSong

    /** Applies a rating locally and carries it to the account. */
    fun rate(song: Song, status: LikeStatus) {
        likedIds = if (status == LikeStatus.LIKE) likedIds + song.videoId else likedIds - song.videoId
        dislikedIds = if (status == LikeStatus.DISLIKE) {
            dislikedIds + song.videoId
        } else {
            dislikedIds - song.videoId
        }
        persistence.saveLikedIds(likedIds)
        persistence.saveDislikedIds(dislikedIds)
        scope.launch {
            DesktopSearchClient.rate(song.videoId, status)
                .onFailure { DesktopTrackLog.log("youtube: rating ${song.videoId} failed: ${it.message}") }
        }
    }

    fun toggleLike(song: Song) =
        rate(song, if (song.videoId in likedIds) LikeStatus.INDIFFERENT else LikeStatus.LIKE)

    fun toggleDislike(song: Song) =
        rate(song, if (song.videoId in dislikedIds) LikeStatus.INDIFFERENT else LikeStatus.DISLIKE)

    /** Slots a track in right after the one playing. */
    fun playNext(song: Song) {
        val queued = canonicalSong(song).copy(radioName = liveQueue.current?.radioName)
        liveQueue = liveQueue.insert(liveQueue.index + 1, queued)
        saveQueue()
    }

    /** Puts a track at the end of what the listener queued — not the end of the queue. */
    fun addToQueue(song: Song) {
        val queued = canonicalSong(song).copy(radioName = liveQueue.current?.radioName)
        liveQueue = liveQueue.insert(liveQueue.autoplaySectionStart, queued)
        saveQueue()
    }

    /** Starts the station YouTube Music builds around one track. */
    fun startRadio(song: Song) {
        val seed = canonicalSong(song).copy(radioName = song.title)
        scope.launch {
            val related = DesktopAutoplay.tracksFor(listOf(seed), seed, INITIAL_RADIO_TRACKS)
                .getOrNull()
                .orEmpty()
                // The station *is* the queue, not a top-up behind it.
                .map { it.copy(fromAutoplay = false) }
            if (related.isEmpty()) {
                DesktopTrackLog.log("radio: nothing to build a station on for '${song.title}'")
                return@launch
            }
            if (liveQueue.current?.videoId == seed.videoId) {
                liveQueue = DesktopQueue(listOf(liveQueue.current!!) + related, index = 0)
                saveQueue()
            } else {
                playSongs(listOf(seed) + related, 0)
            }
            DesktopTrackLog.log("radio: started a station on '${song.title}' with ${related.size} tracks")
        }
    }

    fun openAlbum(browseId: String) {
        scope.launch {
            DesktopSearchClient.browse(browseId).onSuccess { openedCollection = it }
        }
    }

    /** Copies the track's YouTube Music link. */
    fun shareSong(song: Song) {
        DesktopExternalLinks.copy("https://music.youtube.com/watch?v=${song.videoId}")
        DesktopTrackLog.log("copied a link to '${song.title}'")
    }

    fun downloadSong(song: Song) {
        // A second tap on something already queued or running calls it off.
        if (song.videoId in downloadInProgress) {
            DesktopDownloadQueue.cancel(song.videoId)
            return
        }
        if (song.videoId in downloads.map(Song::videoId) || song.localPath != null) return
        DesktopDownloadQueue.enqueue(song)
    }


    /** Deletes the file a download saved and forgets the record. */
    fun removeDownload(song: Song) {
        scope.launch {
            withContext(Dispatchers.IO) { DesktopDownloadManager.delete(song) }
            downloads = downloads.filterNot { it.videoId == song.videoId }
            persistence.saveDownloads(downloads)
        }
    }

    /** Everything on a page, in the order it is listed. */
    fun downloadAll(songs: List<Song>) {
        val have = downloads.map(Song::videoId).toSet()
        DesktopDownloadQueue.enqueueAll(
            songs.filterNot { it.videoId in have || it.localPath != null },
        )
    }

    /** Keeps a station queued ahead of whatever is playing. */

    fun loadAutoplaySongs(playFirst: Boolean = false) {
        val current = selectedSong ?: return
        if (!autoplay || repeatMode == DesktopRepeatMode.ALL) return

        if (dontRepeatSuggestions) sessionSongHistory += current

        val queued = liveQueue.songs.drop(liveQueue.index + 1).count { it.fromAutoplay }
        val needed = MAX_QUEUED_AUTOPLAY - queued
        if (needed <= 0 && !playFirst) return
        // One request per seed.
        if (!playFirst && autoplaySeed == current.videoId) return
        autoplaySeed = current.videoId

        autoplayJob?.cancel()
        autoplayJob = scope.launch {
            DesktopTrackLog.log("autoplay: building a station from '${current.title}'")
            val at = liveQueue.songs.size
            DesktopAutoplay.tracksFor(
                // What the queue holds, plus everything this session has already offered.
                existing = if (dontRepeatSuggestions) {
                    liveQueue.songs + sessionSongHistory
                } else {
                    liveQueue.songs
                },
                seedSong = current,
                limit = needed.coerceAtLeast(1),
            ).onSuccess { suggestions ->
                if (suggestions.isEmpty()) {
                    // Worth saying: an empty station and a station that could not be reached look
                    // identical from the queue.
                    DesktopTrackLog.log("autoplay: no station came back for '${current.title}'")
                    return@onSuccess
                }
                // The listener may have moved on while the station was being fetched; appending
                // then would attach it to the wrong seed.
                if (selectedSong?.videoId != current.videoId || !autoplay) return@onSuccess
                liveQueue = liveQueue.append(suggestions)
                saveQueue()
                if (dontRepeatSuggestions) sessionSongHistory += suggestions
                DesktopTrackLog.log(
                    "autoplay: queued ${suggestions.size} after '${current.title}'",
                )
                // The mix continues from where it was added rather than starting a queue of its
                // own.
                if (playFirst) playQueueIndex(at)
            }.onFailure { failure ->
                autoplaySeed = null
                DesktopTrackLog.log("autoplay: could not build a station: ${failure.message}")
            }
        }
    }

    /**
     * Turning AutoPlay on or off, and what that does to the queue.
     *
     * Android runs one path for this — `toggleAutoplayFromNotification` — whether the switch was
     * thrown in the player or anywhere else, and it does more than set a flag. Off drops the
     * suggestions it had already queued, because switching it off is the listener saying they do
     * not want them. On clears the seed first, so the station is fetched again for the track
     * already playing rather than being refused as one this seed has been loaded for.
     */
    fun setAutoplay(enabled: Boolean) {
        autoplay = enabled
        persistence.saveBoolean("autoplay", enabled)
        autoplayJob?.cancel()
        autoplayJob = null
        autoplaySeed = null
        if (enabled) {
            loadAutoplaySongs()
        } else {
            val trimmed = liveQueue.withoutAutoplay()
            if (trimmed !== liveQueue) {
                liveQueue = trimmed
                saveQueue()
            }
        }
    }

    fun playNext() {
        when {
            liveQueue.hasNext -> {
                liveQueue = liveQueue.next()
                playCurrent()
            }
            repeatMode == DesktopRepeatMode.ALL && liveQueue.songs.isNotEmpty() -> {
                liveQueue = liveQueue.copy(index = 0)
                playCurrent()
            }
            autoplay -> loadAutoplaySongs(playFirst = true)
        }
    }

    fun playPrevious() {
        if (!liveQueue.hasPrevious) return
        liveQueue = liveQueue.previous()
        playCurrent()
    }

    /**
     * Shuffle rearranges the queue rather than switching playback to a hidden random order, so what
     * the queue shows stays what plays.
     */
    fun setShuffle(enabled: Boolean) {
        shuffle = enabled
        persistence.saveBoolean("shuffle", enabled)
        liveQueue = if (enabled) {
            preShuffleOrder = liveQueue.songs.map(Song::videoId)
            liveQueue.shuffledAhead()
        } else {
            liveQueue.inOrderOf(preShuffleOrder).also { preShuffleOrder = emptyList() }
        }
        saveQueue()
    }

    fun closePlaylistDialogs() {
        overlays.playlistDialog = false
        playlistTarget = null
        overlays.rename = false
        overlays.delete = false
        playlistError = null
        playlistBusy = false
    }

    /** Creates a playlist — on the account when there is one, otherwise here. */
    fun createPlaylist(title: String, privacy: PlaylistPrivacy) {
        val name = title.trim()
        if (name.isBlank()) return
        val seed = playlistTarget
        // A track from another source cannot go in an account playlist, so the playlist made to
        // hold it is one kept here.
        if (!DesktopYouTubeAuth.isSignedIn || (seed != null && !DesktopSearchClient.isVideoId(seed.videoId))) {
            playlists = playlists + DesktopPlaylist(title = name, songs = listOfNotNull(seed))
            persistence.savePlaylists(playlists)
            closePlaylistDialogs()
            return
        }
        playlistBusy = true
        playlistError = null
        scope.launch {
            DesktopSearchClient.createPlaylist(name, privacy, listOfNotNull(seed?.videoId))
                .onSuccess { playlistId ->
                    closePlaylistDialogs()
                    editPlaylistShelf { items ->
                        val card = ShelfItem(
                            title = name,
                            // Only what this request itself establishes: an unseeded playlist gets
                            // a card of just its name rather than a guess at what the feed will
                            // call it.
                            subtitle = if (seed != null) "1 song" else "",
                            thumbnailUrl = seed?.thumbnailUrl,
                            videoId = null,
                            browseId = "VL$playlistId",
                        )
                        // Leads the shelf because it is the newest, which is the order the feed
                        // itself comes in.
                        listOf(card) + items.filterNot { it.browseId == card.browseId }
                    }
                    libraryStale = true
                }
                .onFailure {
                    playlistBusy = false
                    playlistError = it.message ?: "Could not create the playlist"
                }
        }
    }

    fun openPlaylist(playlist: DesktopPlaylist) {
        openedCollection = DesktopCollection(
            browseId = playlist.id,
            title = playlist.title,
            subtitle = "Playlist • ${playlist.songs.size} songs",
            thumbnailUrl = playlist.songs.firstOrNull()?.thumbnailUrl,
            type = BrowseType.PLAYLIST,
            songs = playlist.songs,
        )
    }

    fun addToPlaylist(playlist: DesktopPlaylist) {
        val song = playlistTarget ?: return
        playlists = playlists.map { current ->
            if (current.id == playlist.id) current.copy(songs = (current.songs + song).distinctBy(Song::videoId)) else current
        }
        persistence.savePlaylists(playlists)
        playlistTarget = null
    }

    fun addToAccountPlaylist(playlist: UserPlaylist) {
        val song = playlistTarget ?: return
        playlistBusy = true
        playlistError = null
        scope.launch {
            DesktopSearchClient.addToPlaylist(playlist.playlistId, listOf(song.videoId))
                .onSuccess { closePlaylistDialogs() }
                .onFailure {
                    DesktopTrackLog.log("playlist: could not add ${song.videoId}: ${it.message}")
                    playlistBusy = false
                    playlistError = it.message ?: "Could not add to ${playlist.title}"
                }
        }
    }

    playbackEngine = remember {
        DesktopPlaybackEngine(
            onEnded = {
                selectedSong?.let(DesktopScrobbling::onPlaybackEnded)
                if (DesktopSleepTimer.afterTrack.value) {
                    DesktopSleepTimer.cancel()
                } else if (repeatMode == DesktopRepeatMode.ONE) {
                    selectedSong?.let { startSong(it, true) }
                } else {
                    playNext()
                }
            },
            onCrossfaded = { song ->
                scope.launch {
                    selectedSong = song
                    history = (listOf(song) + history.filterNot { it.videoId == song.videoId }).take(50)
                    persistence.saveHistory(history)
                    DesktopScrobbling.updateNowPlaying(song)
                }
            },
        )
    }
    val mprisController = remember(playbackEngine) {
        DesktopMprisController(
            onPlay = { playbackEngine.play() },
            onPause = { playbackEngine.pause() },
            onPlayPause = { playbackEngine.togglePlayPause() },
            onNext = ::playNext,
            onPrevious = ::playPrevious,
            onShuffleChanged = { enabled ->
                shuffle = enabled
                persistence.saveBoolean("shuffle", enabled)
            },
            onLoopStatusChanged = { status ->
                repeatMode = when (status) {
                    "Track" -> DesktopRepeatMode.ONE
                    "Playlist" -> DesktopRepeatMode.ALL
                    else -> DesktopRepeatMode.OFF
                }
                persistence.saveString("repeat_mode", repeatMode.name)
            },
            onRateChanged = { value ->
                playbackSpeed = value.toFloat().coerceIn(0.25f, 3.0f)
                persistence.saveString("playback_speed", playbackSpeed.toString())
            },
            onVolumeChanged = { value ->
                volume = value.toFloat().coerceIn(0.0f, 1.0f)
                persistence.saveString("volume", volume.toString())
            },
            onSeek = { positionMs -> playbackEngine.seekTo(positionMs) },
        )
    }
    DisposableEffect(mprisController) {
        mprisController.start()
        onDispose { mprisController.stop() }
    }
    // The Windows half of the same idea. Linux has MPRIS above; on Windows this
    // is what the media keys and the volume flyout's card talk to.
    DisposableEffect(playbackEngine) {
        DesktopWindowsMedia.start(
            DesktopWindowsMedia.Controller(
                onPlay = { playbackEngine.play() },
                onPause = { playbackEngine.pause() },
                onNext = ::playNext,
                onPrevious = ::playPrevious,
                onStop = { playbackEngine.pause() },
            ),
        )
        onDispose { DesktopWindowsMedia.stop() }
    }

    LaunchedEffect(playbackSpeed) { playbackEngine.setPlaybackSpeed(playbackSpeed) }
    LaunchedEffect(volume) { playbackEngine.setVolume(volume) }
    LaunchedEffect(audioQuality) { playbackEngine.setAudioQuality(audioQuality.name) }
    LaunchedEffect(automixPerformance) {
        playbackEngine.setAutomixPerformance(automixPerformance)
    }
    // Every one of the seven settings renders the same curve, so they are collected together
    // rather than destructured — seven sources of one tuning is seven chances to read them in the
    // wrong order.
    val eqEnabled by DesktopEqualizerSettings.enabled.collectAsState()
    val eqMode by DesktopEqualizerSettings.mode.collectAsState()
    val eqToneX by DesktopEqualizerSettings.toneX.collectAsState()
    val eqToneY by DesktopEqualizerSettings.toneY.collectAsState()
    val eqFocused by DesktopEqualizerSettings.focused.collectAsState()
    val eqBalance by DesktopEqualizerSettings.balance.collectAsState()
    val eqBands by DesktopEqualizerSettings.bands.collectAsState()
    LaunchedEffect(eqEnabled, eqMode, eqToneX, eqToneY, eqFocused, eqBalance, eqBands) {
        playbackEngine.setEqualizer(eqEnabled, DesktopEqualizerSettings.curve(), eqBalance)
    }
    LaunchedEffect(spatialAudio, skipSilence, outputPrecision) {
        playbackEngine.setSpatialAudio(spatialAudio)
        playbackEngine.setSkipSilence(skipSilence)
        playbackEngine.setOutputPrecision(outputPrecision)
    }
    LaunchedEffect(automix, crossfadeSeconds, audioQuality, selectedSong?.videoId, queue, shuffle, repeatMode) {
        playbackEngine.setAutomixEnabled(automix)
        playbackEngine.setCrossfadeSeconds(crossfadeSeconds)
        val currentIndex = queue.indexOfFirst { it.videoId == selectedSong?.videoId }
        val next = selectedSong?.let {
            if (shuffle) {
                queue.filterIndexed { index, _ -> index != currentIndex }.firstOrNull()
            } else {
                queue.drop(currentIndex + 1).firstOrNull()
            } ?: if (repeatMode == DesktopRepeatMode.ALL) queue.firstOrNull() else null
        }
        playbackEngine.prepareNext(next)
    }
    // Topped up on every track change, the way Android does it, rather than only once the queue has
    // run dry.
    LaunchedEffect(selectedSong?.videoId, autoplay, repeatMode) {
        loadAutoplaySongs()
    }
    LaunchedEffect(Unit) {
        while (currentCoroutineContext().isActive) {
            delay(1_000L)
            if (DesktopSleepTimer.isExpired()) {
                DesktopSleepTimer.cancel()
                playbackEngine.pause()
            }
        }
    }
    val playback by playbackEngine.state.collectAsState()

    // Lyrics and motion artwork follow whatever is *playing*, not whatever page happens to be open.
    // This is where Android keeps it — `MainActivity`, keyed on the player's own track — so the
    // lookup starts the moment a track does and the panel is already populated by the time anyone
    // opens the player. Run from the player page instead, nothing was fetched until it was opened,
    // and a track half a minute in then showed a skeleton and arrived mid-song.
    var lyrics by remember { mutableStateOf<DesktopLyrics?>(null) }
    var lyricsLoading by remember { mutableStateOf(false) }
    var lyricsError by remember { mutableStateOf<String?>(null) }
    var canvas by remember { mutableStateOf<DesktopCanvasArtwork?>(null) }

    // Keyed on the duration too: it lands a beat after the track, and a database match needs it,
    // so looking up against a length of zero would settle on the wrong recording.
    LaunchedEffect(
        selectedSong?.videoId,
        playback.durationMs,
        syncedLyrics,
        prioritizeSyllables,
        lyricsOn,
        lyricsOrder,
    ) {
        val current = selectedSong
        lyrics = null
        lyricsError = null
        if (current == null) {
            lyricsLoading = false
            return@LaunchedEffect
        }
        if (!syncedLyrics) {
            lyricsError = "Synced lyrics are switched off"
            lyricsLoading = false
            return@LaunchedEffect
        }
        // A length is what a database match is made on, and it lands a beat after the track. Looking
        // up without one would settle on whichever recording shares the name — so this waits, the
        // same way Android's `loadLyrics` turns the call away until a duration exists.
        val length = playback.durationMs.takeIf { it > 0L } ?: current.durationMillis()
        if (length <= 0L) {
            lyricsLoading = true
            return@LaunchedEffect
        }
        lyricsLoading = true
        DesktopLyricsClient.lookup(current, length).fold(
            onSuccess = { lyrics = it },
            onFailure = { lyricsError = it.message ?: "Lyrics unavailable" },
        )
        lyricsLoading = false
    }

    LaunchedEffect(selectedSong?.videoId, animatedCanvas) {
        val current = selectedSong
        if (current == null || !animatedCanvas) {
            canvas = null
            return@LaunchedEffect
        }
        // Paint what is already known before waiting on anything: re-opening the player on a track
        // resolved a minute ago should not go dark on its way back to the same clip.
        canvas = DesktopCanvasClient.cached(current)
        canvas = withContext(Dispatchers.IO) { DesktopCanvasClient.lookup(current) }
    }

    LaunchedEffect(
        playback.song?.videoId,
        playback.isPlaying,
        playback.durationMs,
        playback.volume,
        playback.error,
        shuffle,
        repeatMode,
        playbackSpeed,
    ) {
        mprisController.update(
            playback = playback,
            shuffle = shuffle,
            loopStatus = when (repeatMode) {
                DesktopRepeatMode.OFF -> "None"
                DesktopRepeatMode.ALL -> "Playlist"
                DesktopRepeatMode.ONE -> "Track"
            },
            rate = playbackSpeed.toDouble(),
        )
    }
    LaunchedEffect(playback.song?.videoId, playback.isPlaying, playback.durationMs) {
        DesktopScrobbling.onPlaybackStateChanged(playback)
        DesktopWindowsMedia.publish(playback)
    }
    // Refetched on opening the page rather than cached for the session: the
    // point of it is that something played elsewhere shows up here.
    LaunchedEffect(destination, youtubeSignedIn, sessionRevision) {
        if (destination != DesktopDestination.HISTORY || !youtubeSignedIn) return@LaunchedEffect
        DesktopSearchClient.history().onSuccess { remoteHistory = it }
    }
    LaunchedEffect(selectedSong?.videoId, overlays.nowPlaying) {
        trackLinks = null
        val current = selectedSong ?: return@LaunchedEffect
        // Only while the player is up, so playing from the mini player costs
        // nothing, and only when something is actually missing.
        if (!overlays.nowPlaying) return@LaunchedEffect
        if (current.artistId != null && current.albumId != null) return@LaunchedEffect
        trackLinks = DesktopSearchClient.trackLinks(current.videoId).getOrNull()
    }
    // Keyed on the same things, plus the rate: Discord counts the bar down on
    // its own clock, so only a change to what it was told is worth another push.
    LaunchedEffect(playback.song?.videoId, playback.isPlaying, playback.durationMs, playbackSpeed) {
        DesktopDiscordRpc.onPlaybackStateChanged(playback, playbackSpeed)
    }
    // The account's own history on YouTube Music, which is what the home feed is built out of.
    DisposableEffect(playback.song?.videoId) {
        val previous = playbackEngine.state.value.positionMs / 1_000
        onDispose { DesktopPlaybackTracker.onTrackChanged(previous) }
    }
    LaunchedEffect(playback.song?.videoId, playback.isPlaying) {
        val songId = playback.song?.videoId ?: return@LaunchedEffect
        if (!playback.isPlaying) return@LaunchedEffect
        DesktopPlaybackTracker.onPlaying(songId)
        while (currentCoroutineContext().isActive) {
            delay(HISTORY_SAMPLE_MS)
            val snapshot = playbackEngine.state.value
            if (snapshot.song?.videoId != songId) break
            DesktopPlaybackTracker.onProgress(songId, snapshot.positionMs / 1_000)
        }
    }
    LaunchedEffect(playback.song?.videoId, playback.isPlaying) {
        val songId = playback.song?.videoId ?: return@LaunchedEffect
        if (!playback.isPlaying) return@LaunchedEffect
        var previousPosition = playback.positionMs
        var countedAsPlay = songId in statsCountedSongs
        while (currentCoroutineContext().isActive) {
            delay(STATS_SAMPLE_MS)
            val snapshot = playbackEngine.state.value
            if (!snapshot.isPlaying || snapshot.song?.videoId != songId) break
            val position = snapshot.positionMs
            val delta = (position - previousPosition).takeIf { it in 1L..STATS_MAX_DELTA_MS } ?: 0L
            if (delta > 0L) {
                val threshold = snapshot.durationMs.takeIf { it > 0L }?.let { minOf(it / 2L, SCROBBLE_THRESHOLD_MS) }
                    ?: SCROBBLE_THRESHOLD_MS
                val reachedThreshold = !countedAsPlay && position >= threshold
                DesktopListeningStats.record(snapshot.song, delta, reachedThreshold)
                if (reachedThreshold) {
                    countedAsPlay = true
                    statsCountedSongs = statsCountedSongs + songId
                }
                statsRevision++
            }
            previousPosition = position
        }
    }
    LaunchedEffect(statsRevision) {
        replaySummary = withContext(Dispatchers.IO) { DesktopListeningStats.summary(replayPeriod) }
    }
    DisposableEffect(playbackEngine) {
        onDispose { playbackEngine.release() }
    }

    /** Opens an artist's page, from a card, a search row or the player's credit. */
    fun openArtist(browseId: String, name: String) {
        val target = DesktopArtistTarget(browseId, name)
        if (openedArtist == target && artistState is UiState.Success) return
        openedArtist = target
        artistState = UiState.Loading
        artistReloads++
    }

    /**
     * The "…" menu for one row, wherever it is drawn.
     *
     * Built once so a row on Downloads or Local Music offers what the player's own "…" offers —
     * Android opens the same `SongActionsSheet` from every one of them.
     */
    fun songActionsFor(song: Song) = DesktopSongActions(
        signedIn = youtubeSignedIn,
        disliked = song.videoId in dislikedIds,
        downloaded = downloads.any { it.videoId == song.videoId },
        downloadInProgress = song.videoId in downloadInProgress,
        sleepTimerMinutes = sleepTimerMinutes,
        sleepAfterTrack = sleepAfterTrack,
        onToggleDislike = ::toggleDislike,
        onAddToPlaylist = { playlistTarget = it },
        onDownload = ::downloadSong,
        onRemoveDownload = ::removeDownload,
        onStartRadio = ::startRadio,
        onPlayNext = ::playNext,
        onAddToQueue = ::addToQueue,
        onOpenAlbum = ::openAlbum,
        onOpenArtist = { id -> openArtist(id, song.artist) },
        onSleepTimer = { minutes ->
            if (minutes == null) DesktopSleepTimer.cancel() else DesktopSleepTimer.start(minutes)
        },
        onSleepAfterTrack = { DesktopSleepTimer.startAfterTrack() },
        onShare = ::shareSong,
    )

    /** The "…" a song row hangs off, built the same way on every page that offers one. */
    @Composable
    fun songMenu(song: Song) = DesktopSongMenuAnchor(
        song = song,
        liked = song.videoId in likedIds,
        actions = songActionsFor(song),
        onToggleLike = { toggleLike(song) },
        onRevertToOriginal = null,
        onUpgradeQuality = null,
    )

    /** Replay counts artists by name, so the page has to be found from the name alone. */
    fun openArtistByName(name: String) {
        scope.launch {
            val match = DesktopSearchClient.search(name, SearchFilter.ARTISTS)
                .getOrDefault(emptyList())
                .filterIsInstance<SearchResult.Browse>()
                .map { it.item }
                .firstOrNull { it.browseId.isNotBlank() }
                ?: return@launch
            openArtist(match.browseId, match.title)
        }
    }

    LaunchedEffect(openedArtist, artistReloads) {
        val target = openedArtist ?: return@LaunchedEffect
        if (artistState !is UiState.Loading) return@LaunchedEffect
        DesktopSearchClient.artistPage(target.browseId).fold(
            onSuccess = { artistState = UiState.Success(it) },
            onFailure = { artistState = UiState.Error(it.message ?: "Could not open ${target.name}") },
        )
    }

    fun toggleSubscription(subscription: SubscriptionState) {
        val target = openedArtist ?: return
        val next = !subscription.subscribed
        // Painted before the network answers and put back if it refuses.
        artistState = (artistState as? UiState.Success)?.let { current ->
            UiState.Success(current.data.copy(subscription = subscription.copy(subscribed = next)))
        } ?: artistState
        scope.launch {
            DesktopSearchClient.setSubscribed(subscription.channelId, next).onFailure { failure ->
                DesktopTrackLog.log("subscription: ${target.name} failed: ${failure.message}")
                artistState = (artistState as? UiState.Success)?.let { current ->
                    UiState.Success(current.data.copy(subscription = subscription))
                } ?: artistState
            }
        }
    }

    fun openShelfItem(item: ShelfItem) {
        val videoId = item.videoId
        val browseId = item.browseId
        when {
            videoId != null -> {
                playSong(item.toSong())
            }
            // An artist is a page of its own, not a list of tracks with a photograph on top.
            browseTypeOf(browseId.orEmpty()) == BrowseType.ARTIST ->
                openArtist(browseId!!, item.title)
            browseId != null -> {
                scope.launch {
                    DesktopSearchClient.browse(
                        browseId = browseId,
                        fallback = BrowseItem(
                            browseId = browseId,
                            title = item.title,
                            subtitle = item.subtitle,
                            thumbnailUrl = item.thumbnailUrl,
                            type = BrowseType.OTHER,
                        ),
                    ).onSuccess {
                        openedCollection = it
                    }
                }
            }
        }
    }

    fun recordSearch(term: String) {
        val entry = term.trim()
        if (entry.isBlank()) return
        searchHistory = persistence.saveSearchHistory(
            listOf(entry) + searchHistory.filterNot { it.equals(entry, ignoreCase = true) },
        )
    }

    fun forgetSearch(term: String) {
        searchHistory = persistence.saveSearchHistory(
            searchHistory.filterNot { it.equals(term, ignoreCase = true) },
        )
    }

    fun search() {
        if (query.isBlank() || searchLoading) return
        destination = DesktopDestination.SEARCH
        searchTyping = false
        searchSuggestions = emptyList()
        recordSearch(query)
        searchLoading = true
        searchError = null
        scope.launch {
            try {
                DesktopMusicSources.search(query, searchFilter).fold(
                    onSuccess = { searchRows = it },
                    onFailure = { searchError = it.message ?: "Search failed" },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                searchError = failure.message ?: "Search failed"
            } finally {
                // OBS and other desktop capture tools can briefly interrupt focus/coroutines.
                searchLoading = false
            }
        }
    }

    /** The typeahead, one request behind the keystrokes rather than one per. */
    LaunchedEffect(query, searchTyping) {
        if (!searchTyping || query.isBlank()) {
            searchSuggestions = emptyList()
            return@LaunchedEffect
        }
        delay(SUGGESTION_DEBOUNCE_MS)
        DesktopSearchClient.searchSuggestions(query)
            .onSuccess { if (searchTyping) searchSuggestions = it }
            .onFailure { searchSuggestions = emptyList() }
    }

    fun runSearch(term: String) {
        query = term
        searchTyping = false
        searchSuggestions = emptyList()
        search()
    }

    fun selectDestination(next: DesktopDestination) {
        if (next == DesktopDestination.LIBRARY && libraryStale) {
            libraryStale = false
            reloadLibrary()
        }
        openedArtist = null
        openedCollection = null
        overlays.replay = false
        selectedMoodGenre = null
        destination = next
    }

    /** Whatever has been played here lately, newest first — the lead shelf. */
    fun recentsShelf(): HomeShelf? {
        val recent = history.distinctBy(Song::videoId).take(RECENTS_LIMIT)
        if (recent.isEmpty()) return null
        return HomeShelf(
            title = DesktopStrings["d_recents", "Recents"],
            items = recent.map {
                ShelfItem(it.title, it.artist, it.thumbnailUrl, it.videoId, null)
            },
        )
    }

    fun shelvesOf(state: UiState<List<HomeShelf>>): List<HomeShelf> =
        (state as? UiState.Success)?.data.orEmpty()

    /** Appends shelves already on the page, dropping any repeat of a heading. */
    fun appendHomeShelves(more: List<HomeShelf>) {
        val existing = shelvesOf(homeState)
        val seen = existing.mapTo(HashSet()) { it.title }
        val added = more.filter { it.items.isNotEmpty() && seen.add(it.title) }
        if (added.isNotEmpty()) homeState = UiState.Success(existing + added)
    }

    suspend fun loadHome() {
        if (!youtubeSourceEnabled) {
            homeState = UiState.Error("YouTube Music is disabled")
            return
        }
        homeState = UiState.Loading
        homeContinuation = null
        homeSupplementsLoaded = false
        DesktopSearchClient.home().fold(
            onSuccess = { feed ->
                homeContinuation = feed.continuation
                homeState = UiState.Success(listOfNotNull(recentsShelf()) + feed.shelves)
            },
            onFailure = { homeState = UiState.Error(it.message ?: "Could not load Listen Now") },
        )
    }

    fun loadMoreHome() {
        val token = homeContinuation
        if (homeLoadingMore || homeState !is UiState.Success) return
        // The feed's own pages first; once they run out, the supplementary browse feeds are what
        // the rest of the page is made of.
        if (token == null && homeSupplementsLoaded) return
        homeLoadingMore = true
        scope.launch {
            if (token != null) {
                DesktopSearchClient.moreHome(token).fold(
                    onSuccess = { feed ->
                        homeContinuation = feed.continuation
                        appendHomeShelves(feed.shelves)
                    },
                    onFailure = { homeContinuation = null },
                )
            } else {
                homeSupplementsLoaded = true
                DesktopSearchClient.HOME_SUPPLEMENT_BROWSE_IDS.forEach { browseId ->
                    DesktopSearchClient.homeSupplement(browseId)
                        .onSuccess(::appendHomeShelves)
                }
            }
            homeLoadingMore = false
        }
    }

    // The tray icon speaks StatusNotifierItem rather than going through AWT, which only implements
    // the XEmbed tray a Wayland session does not have.
    val tray = remember {
        DesktopStatusNotifierController(
            // Clicking the icon is how the window comes back once it has been closed to the tray,
            // so it has to raise the window before it does anything inside it.
            onActivate = {
                DesktopWindowVisibility.show()
                overlays.nowPlaying = true
            },
            onPlayPause = { if (selectedSong != null) playbackEngine.togglePlayPause() },
        )
    }
    DisposableEffect(tray, trayIconEnabled) {
        if (trayIconEnabled) tray.start()
        onDispose { tray.stop() }
    }
    // Deliberately not a key on the effect above.
    DisposableEffect(trayIconEnabled, closeToTray) {
        // The listener's choice, with the tray as its precondition: hiding with no tray to come
        // back from leaves an audible process and no window.
        DesktopWindowVisibility.keepRunningWhenClosed = trayIconEnabled && closeToTray
        onDispose { DesktopWindowVisibility.keepRunningWhenClosed = false }
    }
    LaunchedEffect(tray, trayIconEnabled) {
        if (!trayIconEnabled) return@LaunchedEffect
        // The logo travels to the tray as pixels: a themed icon name would be whatever the user's
        // theme draws for a generic music player.
        runCatching { Res.readBytes("drawable/logo.svg") }.onSuccess(tray::setIcon)
    }
    LaunchedEffect(tray) {
        DesktopTrayMenu.bind(
            onPlayPause = { if (selectedSong != null) playbackEngine.togglePlayPause() },
            onNext = ::playNext,
            onPrevious = ::playPrevious,
            onOpenPlayer = {
                DesktopWindowVisibility.show()
                overlays.nowPlaying = true
            },
            onOpenSettings = {
                DesktopWindowVisibility.show()
                overlays.settings = true
            },
            onQuit = { exitProcess(0) },
        )
    }
    LaunchedEffect(selectedSong, playback.isPlaying) {
        val title = selectedSong?.let { "${it.title} — ${it.artist}" }
        tray.update(title = title, isPlaying = playback.isPlaying)
        DesktopTrayMenu.publish(title = title, playing = playback.isPlaying)
    }

    LaunchedEffect(Unit) {
        // Unpacks the analyser and its models on first run.
        DesktopAnalysisRuntime.ensureStarted()
        DesktopHorizontalScroll.install()
        localSongs = withContext(Dispatchers.IO) { DesktopLocalMusic.scan() }
        // A download record is a claim about a folder this app does not own, so it can outlive the
        // file it names. Checked once at startup rather than trusted: a stale entry showed the
        // track as downloaded, refused to fetch it again, and then would not play.
        val onDisk = withContext(Dispatchers.IO) { DesktopDownloadManager.verified(downloads) }
        if (onDisk.size != downloads.size) {
            DesktopTrackLog.log(
                "downloads: ${downloads.size - onDisk.size} recorded file(s) are gone; forgetting them",
            )
            downloads = onDisk
            persistence.saveDownloads(onDisk)
        }
        loadHome()
    }

    // What reached disk goes into the library's own record, which is what the Downloads page and
    // every row's tick read from.
    LaunchedEffect(Unit) {
        DesktopDownloadQueue.finished.collect { saved ->
            downloads = (listOf(saved) + downloads.filterNot { it.videoId == saved.videoId }).take(200)
            persistence.saveDownloads(downloads)
        }
    }

    // The charts are period-filtered at read time, so a different period is a different read.
    LaunchedEffect(replayPeriod) {
        replaySummary = withContext(Dispatchers.IO) { DesktopListeningStats.summary(replayPeriod) }
    }

    // Re-scanned when the filter or the folder changes, never on first composition.
    LaunchedEffect(localMusicRevision) {
        if (localMusicRevision == 0) return@LaunchedEffect
        localSongs = withContext(Dispatchers.IO) { DesktopLocalMusic.scan() }
        DesktopLocalMusicWatcher.restart()
    }

    // A track dropped into the folder appears without a relaunch.
    LaunchedEffect(Unit) {
        DesktopLocalMusicWatcher.restart()
        DesktopLocalMusicWatcher.changes.collect {
            localSongs = withContext(Dispatchers.IO) { DesktopLocalMusic.scan() }
        }
    }

    suspend fun loadExplore() {
        if (!youtubeSourceEnabled) {
            exploreState = UiState.Error("YouTube Music is disabled")
            return
        }
        exploreState = UiState.Loading
        DesktopSearchClient.moodAndGenres().fold(
            onSuccess = { sections ->
                exploreState = if (sections.isEmpty()) {
                    UiState.Error("Nothing to explore right now")
                } else {
                    UiState.Success(sections)
                }
            },
            onFailure = { exploreState = UiState.Error(it.message ?: "Could not load Explore") },
        )
    }

    LaunchedEffect(destination, exploreReloads) {
        if (destination == DesktopDestination.EXPLORE &&
            (exploreState is UiState.Loading || exploreReloads > 0)
        ) {
            loadExplore()
        }
    }

    // Category buttons carry no artwork of their own, so a few are resolved at a time from the
    // shelves they open.
    LaunchedEffect(exploreState) {
        val sections = (exploreState as? UiState.Success)?.data ?: return@LaunchedEffect
        if (sections.all { section -> section.items.all { it.thumbnailUrl != null } }) return@LaunchedEffect
        val limiter = Semaphore(4)
        coroutineScope {
            sections.flatMap(MoodGenreSection::items)
                .filter { it.thumbnailUrl == null }
                .forEach { item ->
                    launch {
                        val artwork = limiter.withPermit {
                            DesktopSearchClient.moodGenreArtwork(item.browseId, item.params).getOrNull()
                        } ?: return@launch
                        val current = (exploreState as? UiState.Success)?.data ?: return@launch
                        exploreState = UiState.Success(
                            current.map { section ->
                                section.copy(
                                    items = section.items.map { entry ->
                                        if (entry.browseId == item.browseId && entry.params == item.params) {
                                            entry.copy(thumbnailUrl = artwork)
                                        } else {
                                            entry
                                        }
                                    },
                                )
                            },
                        )
                    }
                }
        }
    }

    /** The next page of the open collection, appended in place. */
    fun loadMoreCollectionSongs() {
        val collection = openedCollection ?: return
        val token = collection.continuation ?: return
        if (collectionLoadingMore) return
        collectionLoadingMore = true
        scope.launch {
            DesktopSearchClient.moreCollectionSongs(token, collection.songs.firstOrNull()?.artist)
                .onSuccess { (songs, next) ->
                    val current = openedCollection
                    if (current != null && current.browseId == collection.browseId) {
                        openedCollection = current.copy(
                            songs = (current.songs + songs).distinctBy(Song::videoId),
                            continuation = next,
                        )
                    }
                }
                .onFailure {
                    DesktopTrackLog.log("collection: no more rows for ${collection.browseId}: ${it.message}")
                    val current = openedCollection
                    if (current != null && current.browseId == collection.browseId) {
                        // Dropped rather than retried on scroll for ever: the trailing row is what
                        // asks, and leaving the token in place would have it ask again on every
                        // frame.
                        openedCollection = current.copy(continuation = null)
                    }
                }
            collectionLoadingMore = false
        }
    }

    fun renameOpenPlaylist(title: String) {
        val collection = openedCollection ?: return
        playlistBusy = true
        playlistError = null
        scope.launch {
            DesktopSearchClient.renamePlaylist(collection.playlistId, title)
                .onSuccess {
                    // Renamed in place as well as re-fetched: the page that ordered it is still on
                    // screen showing the old name, and the library reload behind it takes a moment.
                    openedCollection = openedCollection
                        ?.takeIf { it.browseId == collection.browseId }
                        ?.copy(title = title)
                        ?: openedCollection
                    editPlaylistShelf { items ->
                        items.map { if (it.browseId == collection.browseId) it.copy(title = title) else it }
                    }
                    closePlaylistDialogs()
                    libraryStale = true
                }
                .onFailure {
                    playlistBusy = false
                    playlistError = it.message ?: "Could not rename the playlist"
                }
        }
    }

    fun deleteOpenPlaylist() {
        val collection = openedCollection ?: return
        playlistBusy = true
        playlistError = null
        scope.launch {
            DesktopSearchClient.deletePlaylist(collection.playlistId)
                .onSuccess {
                    closePlaylistDialogs()
                    // Its page is the one open, and a deleted playlist has nothing left to show.
                    if (openedCollection?.browseId == collection.browseId) openedCollection = null
                    editPlaylistShelf { items -> items.filterNot { it.browseId == collection.browseId } }
                    libraryStale = true
                }
                .onFailure {
                    playlistBusy = false
                    playlistError = it.message ?: "Could not delete the playlist"
                }
        }
    }

    /** Takes one row back out of the playlist it is being read in. */
    fun removeFromOpenPlaylist(song: Song) {
        val collection = openedCollection ?: return
        val setVideoId = song.setVideoId ?: return
        scope.launch {
            DesktopSearchClient.removeFromPlaylist(
                collection.playlistId,
                listOf(setVideoId to song.videoId),
            ).onSuccess {
                val current = openedCollection
                if (current != null && current.browseId == collection.browseId) {
                    openedCollection = current.copy(
                        songs = current.songs.filterNot { it.setVideoId == setVideoId },
                    )
                }
            }.onFailure {
                DesktopTrackLog.log("playlist: could not remove ${song.videoId}: ${it.message}")
            }
        }
    }

    /**
     * The account's library, re-read whenever the session behind it changes — signing in, switching
     * channel, signing out.
     */
    LaunchedEffect(activeAccountId, activeProfileId, sessionRevision) {
        youtubeSignedIn = DesktopYouTubeAuth.isSignedIn
        if (!DesktopYouTubeAuth.isSignedIn) {
            libraryState = UiState.Success(LibraryPage(emptyList(), emptyList(), emptyList()))
            return@LaunchedEffect
        }
        libraryState = UiState.Loading
        DesktopSearchClient.library().fold(
            onSuccess = { page ->
                libraryState = UiState.Success(page)
                DesktopTrackLog.log(
                    "library: ${page.likedSongs.size} liked, ${page.librarySongs.size} added, " +
                        "shelves ${page.shelves.joinToString { "${it.title}=${it.items.size}" }}",
                )
                val fromAccount = page.likedSongs.mapTo(HashSet(), Song::videoId)
                if (!likedIds.containsAll(fromAccount)) {
                    likedIds = likedIds + fromAccount
                    // Off the drawing thread: an account with a long Liked Music writes a few tens
                    // of kilobytes through the preference store, and that is a disk flush.
                    withContext(Dispatchers.IO) { persistence.saveLikedIds(likedIds) }
                }
            },
            onFailure = { libraryState = UiState.Error(it.message ?: "Could not load your library") },
        )
        // A session refused mid-flight is dropped by the request itself, so the answer to "are we
        // signed in" is only settled once the call is over.
        youtubeSignedIn = DesktopYouTubeAuth.isSignedIn
    }

    fun openMoodGenre(item: MoodGenre) {
        selectedMoodGenre = item
        moodGenreShelves = UiState.Loading
    }

    LaunchedEffect(selectedMoodGenre, moodGenreReloads) {
        val item = selectedMoodGenre ?: return@LaunchedEffect
        if (moodGenreShelves !is UiState.Loading) return@LaunchedEffect
        DesktopSearchClient.moodGenreShelves(item.browseId, item.params).fold(
            onSuccess = { shelves ->
                moodGenreShelves = if (shelves.isEmpty()) {
                    UiState.Error("Nothing to explore here yet")
                } else {
                    UiState.Success(shelves)
                }
            },
            onFailure = { moodGenreShelves = UiState.Error(it.message ?: "Could not load this category") },
        )
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = DesktopAccent,
            onPrimary = Color.White,
            primaryContainer = Color(0xFF5E172C),
            onPrimaryContainer = Color.White,
            background = DesktopBackground,
            onBackground = Color.White,
            surface = DesktopSurface,
            onSurface = Color.White,
            surfaceVariant = DesktopSurfaceRaised,
            onSurfaceVariant = DesktopSecondary,
            outline = Color.White.copy(alpha = 0.22f),
            outlineVariant = DesktopDivider,
            surfaceTint = DesktopAccent,
        ),
        typography = desktopTypography(),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides Color.White,
            LocalNowPlaying provides selectedSong,
        ) {
            DesktopFrame(
                containerColor = DesktopSurface,
                // Only Replay dresses itself; everywhere else the chrome sits on the plain surface.
                backdrop = {
                    DesktopPageBackdrop(
                        artworkUrl = replaySummary.songs.firstOrNull()?.song?.thumbnailUrl
                            .takeIf { overlays.replay },
                    )
                },
                modifier = Modifier.onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.MediaPlayPause -> {
                            if (selectedSong != null) playbackEngine.togglePlayPause()
                            true
                        }
                        Key.MediaNext -> {
                            playNext()
                            true
                        }
                        Key.MediaPrevious -> {
                            playPrevious()
                            true
                        }
                        // One layer at a time, innermost first: the queue popover, then the
                        // player's own side panel, then the player.
                        Key.Escape -> when {
                            overlays.queue -> {
                                overlays.queue = false
                                true
                            }
                            overlays.nowPlaying && playerPanel != DesktopPlayerPanel.NONE -> {
                                playerPanel = DesktopPlayerPanel.NONE
                                true
                            }
                            overlays.nowPlaying -> {
                                overlays.nowPlaying = false
                                true
                            }
                            else -> false
                        }
                        else -> false
                    }
                },
                topBar = { compact ->
                    DesktopTopBar(
                        compact = compact,
                        song = selectedSong,
                        isPlaying = playback.isPlaying,
                        volume = playback.volume,
                        shuffle = shuffle,
                        repeatMode = repeatMode,
                        onPlayPause = { if (selectedSong != null) playbackEngine.togglePlayPause() },
                        onPrevious = ::playPrevious,
                        onNext = ::playNext,
                        onShuffleChange = ::setShuffle,
                        onRepeatModeChange = {
                            repeatMode = it
                            persistence.saveString("repeat_mode", it.name)
                        },
                        onOpenNowPlaying = { overlays.nowPlaying = true },
                        onVolumeChange = {
                            volume = it
                            persistence.saveString("volume", it.toString())
                        },
                        onOpenQueue = { overlays.queue = true },
                        accountAvatar = activeAccount?.avatar
                            ?: activeAccount?.profiles?.firstOrNull()?.avatar,
                        onOpenAccounts = { DesktopTrackLog.log("accounts: opening the switcher"); overlays.accounts = true },
                    )
                },
                sidebar = {
                    DesktopSidebar(
                        destination = destination,
                        settingsOpen = overlays.settings,
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = ::search,
                        onDestinationSelected = ::selectDestination,
                        onOpenSettings = { overlays.settings = true },
                    )
                },
                bottomBar = { compact ->
                    DesktopBottomChrome(
                        compact = compact,
                        destination = destination,
                        song = selectedSong,
                        isPlaying = playback.isPlaying,
                        onDestinationSelected = ::selectDestination,
                        onExpand = { overlays.nowPlaying = true },
                        onPlayPause = { if (selectedSong != null) playbackEngine.togglePlayPause() },
                        onNext = ::playNext,
                    )
                },
                overlay = {
                    if (overlays.nowPlaying && selectedSong != null) {
                        val playerSong = selectedSong!!.let { current ->
                            val extra = trackLinks?.takeIf { it.videoId == current.videoId }
                                ?: return@let current
                            current.copy(
                                artistId = current.artistId ?: extra.artistId,
                                albumId = current.albumId ?: extra.albumId,
                                albumName = current.albumName ?: extra.albumName,
                            )
                        }
                        DesktopNowPlayingPage(
                            song = playerSong,
                            // Opening a page means leaving the player, the same way tapping a
                            // credit collapses Android's sheet.
                            onOpenArtist = { song ->
                                song.artistId?.let {
                                    overlays.nowPlaying = false
                                    openArtist(it, song.artist)
                                }
                            },
                            syncedLyrics = syncedLyrics,
                            lyricsBlur = lyricsBlur,
                            prioritizeSyllables = prioritizeSyllables,
                            lyricsSources = DesktopLyricsClient.enabledSources(lyricsOrder, lyricsOn),
                            actions = DesktopSongActions(
                                signedIn = youtubeSignedIn,
                                disliked = selectedSong!!.videoId in dislikedIds,
                                downloaded = downloads.any { it.videoId == selectedSong!!.videoId },
                                downloadInProgress = selectedSong!!.videoId in downloadInProgress,
                                sleepTimerMinutes = sleepTimerMinutes,
                                sleepAfterTrack = sleepAfterTrack,
                                onToggleDislike = ::toggleDislike,
                                onAddToPlaylist = { playlistTarget = it },
                                onDownload = ::downloadSong,
                                onRemoveDownload = ::removeDownload,
                                onStartRadio = ::startRadio,
                                onPlayNext = ::playNext,
                                onAddToQueue = ::addToQueue,
                                // Both leave the player, the way opening a page from Android's
                                // sheet collapses it.
                                onOpenAlbum = { id ->
                                    overlays.nowPlaying = false
                                    openAlbum(id)
                                },
                                onOpenArtist = { id ->
                                    overlays.nowPlaying = false
                                    openArtist(id, selectedSong!!.artist)
                                },
                                onSleepTimer = { minutes ->
                                    if (minutes == null) DesktopSleepTimer.cancel() else DesktopSleepTimer.start(minutes)
                                },
                                onSleepAfterTrack = { DesktopSleepTimer.startAfterTrack() },
                                onShare = ::shareSong,
                            ),
                            panel = playerPanel,
                            onPanelChange = { playerPanel = it },
                            isPlaying = playback.isPlaying,
                            progressMs = playback.positionMs,
                            durationMs = playback.durationMs,
                            error = playback.error,
                            streamFormat = playback.streamFormat,
                            isResolving = playback.isLoading,
                            searchingBetter = playback.searchingBetter,
                            automix = automix,
                            smartAnalysis = playback.smartAnalysis,
                            transitionWindow = playback.transitionWindow,
                            mixing = playback.mixing,
                            // A stream from anything but YouTube is a substitute, and is what a
                            // revert undoes.
                            substituted = playback.streamSourceId != null &&
                                playback.streamSourceId != "youtube" &&
                                DesktopMusicSources.hasYouTubeOriginal(selectedSong!!),
                            pinnedToOriginal = DesktopOriginalVersion.isPinned(selectedSong!!.videoId),
                            liked = selectedSong!!.videoId in likedIds,
                            shuffle = shuffle,
                            autoplay = autoplay,
                            repeatMode = repeatMode,
                            playbackSpeed = playbackSpeed,
                            animatedCanvas = animatedCanvas,
                            showNerdStats = showNerdStats,
                            fullBleedArtwork = fullBleedArtwork,
                            legacyMeshGradient = legacyMeshGradient,
                            volume = volume,
                            upcoming = liveQueue.upcoming,
                            // Where the mix begins, expressed within the upcoming rows the panel is
                            // drawing.
                            autoplayStart = (liveQueue.autoplaySectionStart - liveQueue.index - 1)
                                .coerceIn(0, liveQueue.upcoming.size),
                            crossfadeSeconds = crossfadeSeconds,
                            onClose = {
                                DesktopWindowMode.exit()
                                overlays.nowPlaying = false
                            },
                            onPlayPause = { playbackEngine.togglePlayPause() },
                            onRevertToOriginal = {
                                DesktopOriginalVersion.pin(selectedSong!!.videoId)
                                playbackEngine.reloadCurrent()
                            },
                            onUpgradeQuality = {
                                DesktopOriginalVersion.clear(selectedSong!!.videoId)
                                playbackEngine.reloadCurrent()
                            },
                            onToggleLike = {
                                toggleLike(selectedSong!!)
                            },
                            onSeek = { playbackEngine.seekTo(it) },
                            onPrevious = ::playPrevious,
                            onNext = ::playNext,
                            onShuffleChange = ::setShuffle,
                            onAutoplayChange = ::setAutoplay,
                            onRepeatModeChange = {
                                repeatMode = it
                                persistence.saveString("repeat_mode", it.name)
                            },
                            onPlaybackSpeedChange = {
                                playbackSpeed = it
                                persistence.saveString("playback_speed", it.toString())
                            },
                            onVolumeChange = {
                                volume = it
                                persistence.saveString("volume", it.toString())
                            },
                            // A click in Up Next is a move within the queue, not a new one-song
                            // queue.
                            onQueueSongClick = { at -> playQueueIndex(liveQueue.index + 1 + at) },
                            onClearQueue = {
                                // Clears what is still to come; the track playing and its history
                                // stay where they are.
                                liveQueue = liveQueue.copy(songs = liveQueue.songs.take(liveQueue.index + 1))
                                saveQueue()
                            },
                            lyrics = lyrics,
                            lyricsLoading = lyricsLoading,
                            lyricsError = lyricsError,
                            canvas = canvas,
                            onCrossfadeSecondsChange = {
                                crossfadeSeconds = it
                                persistence.saveString("crossfade_seconds", it.toString())
                            },
                        )
                    }
                    if (overlays.settings) {
                        DesktopSettingsDialog(
                            autoplay = autoplay,
                            onAutoplayChange = ::setAutoplay,
                            automix = automix,
                            onAutomixChange = {
                                automix = it
                                persistence.saveBoolean("automix", it)
                            },
                            automixPerformance = automixPerformance,
                            onAutomixPerformanceChange = {
                                automixPerformance = it
                                persistence.saveString("automix_performance", it.name)
                            },
                            shuffle = shuffle,
                            onShuffleChange = ::setShuffle,
                            repeatMode = repeatMode,
                            onRepeatModeChange = {
                                repeatMode = it
                                persistence.saveString("repeat_mode", it.name)
                            },
                            playbackSpeed = playbackSpeed,
                            onPlaybackSpeedChange = {
                                playbackSpeed = it
                                persistence.saveString("playback_speed", it.toString())
                            },
                            crossfadeSeconds = crossfadeSeconds,
                            onCrossfadeSecondsChange = {
                                crossfadeSeconds = it
                                persistence.saveString("crossfade_seconds", it.toString())
                            },
                            animatedCanvas = animatedCanvas,
                            showNerdStats = showNerdStats,
                            fullBleedArtwork = fullBleedArtwork,
                            onAnimatedCanvasChange = {
                                animatedCanvas = it
                                persistence.saveBoolean("animated_canvas", it)
                            },
                            spotifyCanvasReady = spotifyCanvasCookie.isNotBlank(),
                            onOpenSpotifyCanvasSetup = { overlays.spotifyCanvasSetup = true },
                            dontRepeatSuggestions = dontRepeatSuggestions,
                            onDontRepeatSuggestionsChange = {
                                dontRepeatSuggestions = it
                                persistence.saveBoolean(KEY_DONT_REPEAT_SUGGESTIONS, it)
                            },
                            onChooseLocalMusicFolder = { onChosen ->
                                DesktopLocalMusic.chooseFolder()?.let { chosen ->
                                    DesktopLocalMusic.setFolder(chosen)
                                    onChosen()
                                    localMusicRevision++
                                }
                            },
                            onLocalMusicFolderChanged = { localMusicRevision++ },
                            filterNonMusicAudio = filterNonMusicAudio,
                            onFilterNonMusicAudioChange = {
                                filterNonMusicAudio = it
                                persistence.saveBoolean(DesktopLocalMusic.KEY_FILTER_NON_MUSIC_AUDIO, it)
                                // The scan's result changes with it, so it has to be taken again.
                                localMusicRevision++
                            },
                            syncedLyrics = syncedLyrics,
                            onSyncedLyricsChange = {
                                syncedLyrics = it
                                persistence.saveBoolean(DesktopLyricsClient.KEY_SYNCED_LYRICS, it)
                            },
                            lyricsBlur = lyricsBlur,
                            onLyricsBlurChange = {
                                lyricsBlur = it
                                persistence.saveBoolean(DesktopLyricsClient.KEY_LYRICS_BLUR, it)
                            },
                            enabledLyricsSources = DesktopLyricsClient.enabledSources(lyricsOrder, lyricsOn),
                            onOpenLyricsSources = { overlays.lyricsSources = true },
                            onOpenTranslationLanguage = { overlays.translationLanguage = true },
                            onOpenEqualizer = { overlays.equalizer = true },
                            onShowNerdStatsChange = {
                                showNerdStats = it
                                persistence.saveBoolean("show_nerd_stats", it)
                            },
                            onFullBleedArtworkChange = {
                                fullBleedArtwork = it
                                persistence.saveBoolean("full_bleed_artwork", it)
                            },
                            legacyMeshGradient = legacyMeshGradient,
                            onLegacyMeshGradientChange = {
                                legacyMeshGradient = it
                                persistence.saveBoolean("legacy_mesh_gradient", it)
                            },
                            spatialAudio = spatialAudio,
                            onSpatialAudioChange = {
                                spatialAudio = it
                                persistence.saveBoolean("spatial_audio", it)
                            },
                            skipSilence = skipSilence,
                            onSkipSilenceChange = {
                                skipSilence = it
                                persistence.saveBoolean("skip_silence", it)
                            },
                            outputPrecision = outputPrecision,
                            onOutputPrecisionChange = {
                                outputPrecision = it
                                persistence.saveString("output_precision", it)
                            },
                            outputSummary = playbackEngine.outputSummary(),
                            trayIconEnabled = trayIconEnabled,
                            closeToTray = closeToTray,
                            onCloseToTrayChange = {
                                closeToTray = it
                                persistence.saveBoolean("close_to_tray", it)
                            },
                            onTrayIconChange = {
                                trayIconEnabled = it
                                persistence.saveBoolean("tray_icon", it)
                            },
                            downloadQuality = downloadQuality,
                            onDownloadQualityChange = {
                                downloadQuality = it
                                persistence.saveString("download_quality", it)
                            },
                            audioQuality = audioQuality,
                            onAudioQualityChange = {
                                audioQuality = it
                                persistence.saveAudioQuality(it)
                            },
                            sleepTimerMinutes = sleepTimerMinutes,
                            sleepAfterTrack = sleepAfterTrack,
                            sleepRemainingMs = sleepRemainingMs,
                            onSleepTimerCycle = ::cycleSleepTimer,
                            sourceConfigs = sourceConfigs,
                            sourceStatus = sourceStatus,
                            onSourceEnabledChange = { config, enabled ->
                                val next = sourceConfigs.map {
                                    if (it.id == config.id && it.kind != DesktopSourceKind.YOUTUBE) {
                                        it.copy(enabled = enabled)
                                    } else {
                                        it
                                    }
                                }
                                persistence.saveSourceConfigs(next)
                                sourceConfigs = persistence.sourceConfigs()
                                DesktopModuleSource.reload()
                            },
                            onSaveSource = { saved ->
                                // Replaced by id, so any number of addons can be configured.
                                val without = sourceConfigs.filterNot {
                                    it.id == saved.id ||
                                        (saved.kind == DesktopSourceKind.CUSTOM_MODULE &&
                                            it.kind == DesktopSourceKind.CUSTOM_MODULE)
                                }
                                persistence.saveSourceConfigs((without + saved).inSourceOrder())
                                sourceConfigs = persistence.sourceConfigs()
                                // Whatever was held for this entry describes a server that may no
                                // longer be the one selected.
                                DesktopAddonSource.forget(saved.id)
                                DesktopModuleSource.reload()
                            },
                            onRemoveSource = { config ->
                                if (config.isUserAdded) {
                                    persistence.saveSourceConfigs(sourceConfigs.filterNot { it.id == config.id })
                                    sourceConfigs = persistence.sourceConfigs()
                                    DesktopAddonSource.forget(config.id)
                                    DesktopModuleSource.reload()
                                }
                            },
                            onTestSource = { candidate ->
                                sourceStatus = sourceStatus + (candidate.id to "Checking source…")
                                scope.launch {
                                    val health = if (candidate.kind == DesktopSourceKind.ADDON) {
                                        DesktopAddonSource.health(candidate)
                                    } else {
                                        DesktopModuleSource.health(candidate)
                                    }
                                    health.fold(
                                        onSuccess = { sourceStatus = sourceStatus + (candidate.id to it) },
                                        onFailure = {
                                            sourceStatus = sourceStatus +
                                                (candidate.id to (it.message ?: "Source unavailable"))
                                        },
                                    )
                                }
                            },
                            onOpenIntegrations = { overlays.integrations = true },
                            onDismiss = { overlays.settings = false },
                        )
                    }
                    if (overlays.accounts) {
                        DesktopAccountSelector(
                            accounts = accounts,
                            activeAccountId = activeAccountId,
                            activeProfileId = activeProfileId,
                            busy = signInBusy != null,
                            onSelect = { account, profile ->
                                DesktopAccounts.select(account.accountId, profile.profileId)
                                activeAccountId = account.accountId
                                activeProfileId = profile.profileId
                            },
                            onAddAccount = { overlays.accounts = false; overlays.signIn = true },
                            onRemoveAccount = { account ->
                                scope.launch {
                                    accounts = withContext(Dispatchers.IO) {
                                        DesktopAccounts.remove(account.accountId)
                                        DesktopAccounts.accounts()
                                    }
                                    activeAccountId = DesktopAccounts.activeAccountId()
                                    activeProfileId = DesktopAccounts.activeProfileId()
                                }
                            },
                            onOpenSettings = { overlays.accounts = false; overlays.settings = true },
                            onDismiss = { overlays.accounts = false },
                        )
                    }

                    if (overlays.signIn) {
                        DesktopSignInDialog(
                            busy = signInBusy,
                            error = signInError,
                            onImport = { profile ->
                                scope.launch {
                                    when (val found = withContext(Dispatchers.IO) { DesktopBrowserCookies.read(profile) }) {
                                        is DesktopBrowserCookies.Result.Session ->
                            signIn(found.cookie, profile.label, profile.database.toString())
                                        DesktopBrowserCookies.Result.SignedOut ->
                                            signInError = "${profile.label} is not signed in."
                                        is DesktopBrowserCookies.Result.Unavailable ->
                                            signInError = "${profile.label}: ${found.reason}."
                                    }
                                }
                            },
                            onPaste = { pasted ->
                                scope.launch {
                                    if (!DesktopBrowserCookies.hasSigningSecret(pasted)) {
                                        signInError = "That cookie has no signing secret in it — copy the whole header."
                                    } else {
                                        signIn(pasted, "the cookie you pasted")
                                    }
                                }
                            },
                            onDismiss = { overlays.signIn = false; signInError = null },
                        )
                    }
                    if (overlays.playlistDialog || playlistTarget != null) {
                        DesktopPlaylistDialog(
                            song = playlistTarget,
                            accountPlaylists = accountPlaylists,
                            localPlaylists = playlists,
                            signedIn = youtubeSignedIn,
                            canUseAccount = playlistTarget
                                ?.let { DesktopSearchClient.isVideoId(it.videoId) } ?: true,
                            busy = playlistBusy,
                            error = playlistError,
                            onPickAccount = ::addToAccountPlaylist,
                            onPickLocal = ::addToPlaylist,
                            onCreate = ::createPlaylist,
                            onDismiss = ::closePlaylistDialogs,
                        )
                    }
                    if (overlays.rename) {
                        DesktopRenamePlaylistDialog(
                            current = openedCollection?.title.orEmpty(),
                            busy = playlistBusy,
                            error = playlistError,
                            onRename = ::renameOpenPlaylist,
                            onDismiss = ::closePlaylistDialogs,
                        )
                    }
                    if (overlays.delete) {
                        DesktopDeletePlaylistDialog(
                            title = openedCollection?.title.orEmpty(),
                            busy = playlistBusy,
                            error = playlistError,
                            onDelete = ::deleteOpenPlaylist,
                            onDismiss = ::closePlaylistDialogs,
                        )
                    }
                    if (overlays.downloadManager) {
                        DesktopDownloadManagerDialog(onDismiss = { overlays.downloadManager = false })
                    }
                    if (overlays.spotifyCanvasSetup) {
                        DesktopSpotifyCanvasDialog(
                            onDismiss = { overlays.spotifyCanvasSetup = false },
                            onSaved = { spotifyCanvasCookie = it },
                        )
                    }
                    if (overlays.equalizer) {
                        DesktopEqualizerDialog(onDismiss = { overlays.equalizer = false })
                    }
                    if (overlays.translationLanguage) {
                        DesktopTranslationLanguageDialog(onDismiss = { overlays.translationLanguage = false })
                    }
                    if (overlays.lyricsSources) {
                        DesktopLyricsSourcesDialog(
                            order = lyricsOrder,
                            enabled = lyricsOn,
                            prioritizeSyllables = prioritizeSyllables,
                            onReorder = {
                                lyricsOrder = it
                                persistence.saveLyricsSourceOrder(it)
                            },
                            onToggle = { name ->
                                lyricsOn = if (name in lyricsOn) lyricsOn - name else lyricsOn + name
                                persistence.saveLyricsEnabledSources(lyricsOn)
                            },
                            onPrioritizeSyllables = {
                                prioritizeSyllables = it
                                persistence.saveBoolean(DesktopLyricsClient.KEY_PRIORITIZE_SYLLABLES, it)
                            },
                            onReset = {
                                lyricsOrder = DesktopLyricsClient.sources.map { it.name }
                                lyricsOn = lyricsOrder.toSet()
                                prioritizeSyllables = false
                                persistence.saveLyricsSourceOrder(lyricsOrder)
                                persistence.saveLyricsEnabledSources(lyricsOn)
                                persistence.saveBoolean(DesktopLyricsClient.KEY_PRIORITIZE_SYLLABLES, false)
                            },
                            onDismiss = { overlays.lyricsSources = false },
                        )
                    }
                    if (overlays.integrations) {
                        DesktopIntegrationsDialog(
                            song = playback.song,
                            onOpenLastfm = { overlays.lastfmLogin = true },
                            onOpenListenBrainz = { overlays.listenBrainzToken = true },
                            onOpenDiscordToken = { overlays.discordToken = true },
                            onDismiss = { overlays.integrations = false },
                        )
                    }
                    if (overlays.lastfmLogin) {
                        DesktopLastfmLoginDialog(onDismiss = { overlays.lastfmLogin = false })
                    }
                    if (overlays.listenBrainzToken) {
                        DesktopListenBrainzTokenDialog(onDismiss = { overlays.listenBrainzToken = false })
                    }
                    if (overlays.discordToken) {
                        DesktopDiscordTokenDialog(onDismiss = { overlays.discordToken = false })
                    }
                    if (overlays.queue) {
                        DesktopQueueOverlay(
                            // The whole live queue, so history is visible above the needle the way
                            // the player's own list shows it.
                            queue = liveQueue.songs,
                            currentIndex = liveQueue.index,
                            onDismiss = { overlays.queue = false },
                            onSongClick = { at ->
                                playQueueIndex(at)
                                overlays.queue = false
                            },
                        )
                    }
                },
            ) { contentPadding ->
                Box(Modifier.fillMaxSize()) {
                    when {
                        overlays.replay -> DesktopReplayPage(
                            summary = replaySummary,
                            period = replayPeriod,
                            holder = replayHolder,
                            onPeriodChange = { replayPeriod = it },
                            onBack = { overlays.replay = false },
                            onPlaySong = { playSong(it) },
                            onOpenArtist = ::openArtistByName,
                            contentPadding = contentPadding,
                        )
                        openedArtist != null -> DesktopArtistPage(
                            state = artistState,
                            fallbackName = openedArtist!!.name,
                            likedIds = likedIds,
                            downloadedIds = downloads.map(Song::videoId).toSet(),
                            downloadInProgress = downloadInProgress,
                            onBack = { openedArtist = null },
                            onRetry = {
                                artistState = UiState.Loading
                                artistReloads++
                            },
                            onPlaySongs = ::playSongs,
                            onShuffle = { songs -> playSongs(songs, 0) },
                            onToggleLike = { song -> toggleLike(song) },
                            onDownload = ::downloadSong,
                            onAddToPlaylist = { playlistTarget = it },
                            onShelfItemClick = ::openShelfItem,
                            // A channel subscription is the account's, so a guest is never shown
                            // the button.
                            onToggleSubscription = if (youtubeSignedIn) ::toggleSubscription else null,
                            contentPadding = contentPadding,
                        )
                        openedCollection != null -> DesktopCollectionPage(
                            collection = openedCollection!!,
                            loadingMore = collectionLoadingMore,
                            onLoadMore = ::loadMoreCollectionSongs,
                            onRename = { overlays.rename = true }.takeIf { openedCollection?.owned == true },
                            onDelete = { overlays.delete = true }.takeIf { openedCollection?.owned == true },
                            onRemoveFromPlaylist = ::removeFromOpenPlaylist
                                .takeIf { openedCollection?.owned == true },
                            likedIds = likedIds,
                            onBack = {
                                openedCollection = null
                            },
                            onPlaySongs = ::playSongs,
                            onShuffle = { songs -> playSongs(songs.shuffled()) },
                            onDownloadAll = ::downloadAll,
                            animatedCanvas = animatedCanvas,
                            onToggleLike = { song ->
                                toggleLike(song)
                            },
                            onAddToPlaylist = { playlistTarget = it },
                            onDownload = ::downloadSong,
                            downloadedIds = downloads.map(Song::videoId).toSet(),
                            downloadInProgress = downloadInProgress,
                            contentPadding = contentPadding,
                        )
                        destination == DesktopDestination.LISTEN_NOW -> DesktopHomePage(
                            state = homeState,
                            loadingMore = homeLoadingMore,
                            hasMore = homeContinuation != null || !homeSupplementsLoaded,
                            onLoadMore = ::loadMoreHome,
                            onRetry = { scope.launch { loadHome() } },
                            onItemClick = ::openShelfItem,
                            contentPadding = contentPadding,
                        )
                        destination == DesktopDestination.EXPLORE && selectedMoodGenre != null ->
                            DesktopMoodGenrePage(
                                title = selectedMoodGenre!!.title,
                                state = moodGenreShelves,
                                onBack = { selectedMoodGenre = null },
                                onItemClick = ::openShelfItem,
                                onRetry = {
                                    moodGenreShelves = UiState.Loading
                                    moodGenreReloads++
                                },
                                contentPadding = contentPadding,
                            )
                        destination == DesktopDestination.EXPLORE -> DesktopExplorePage(
                            state = exploreState,
                            onCategoryClick = ::openMoodGenre,
                            onRetry = { exploreReloads++ },
                            contentPadding = contentPadding,
                        )
                        destination == DesktopDestination.SEARCH -> DesktopSearchPage(
                            query = query,
                            onQueryChange = {
                                searchTyping = true
                                query = it
                            },
                            history = searchHistory,
                            // The typed text leads the list, put there by the keystroke rather than
                            // taken from the response.
                            suggestions = if (searchTyping && query.isNotBlank()) {
                                listOf(query) +
                                    searchSuggestions.filterNot { it.equals(query, ignoreCase = true) }
                            } else {
                                emptyList()
                            },
                            onPickTerm = ::runSearch,
                            onFillTerm = {
                                // Still composing: the arrow puts the term in the field to be added
                                // to, so the typeahead should follow it rather than close.
                                searchTyping = true
                                query = it
                            },
                            onForgetTerm = ::forgetSearch,
                            onClearHistory = { searchHistory = persistence.saveSearchHistory(emptyList()) },
                            filter = searchFilter,
                            onFilterChange = {
                                searchFilter = it
                                if (query.isNotBlank()) search()
                            },
                            rows = searchRows,
                            loading = searchLoading,
                            error = searchError,
                            onSearch = ::search,
                            onSongClick = { playSong(it) },
                            onBrowseClick = { item ->
                                if (item.type == BrowseType.ARTIST) {
                                    openArtist(item.browseId, item.title)
                                } else {
                                    scope.launch {
                                        DesktopSearchClient.browse(item.browseId, item).onSuccess {
                                            openedCollection = it
                                        }
                                    }
                                }
                            },
                            likedIds = likedIds,
                            onToggleLike = { song ->
                                toggleLike(song)
                            },
                            onAddToPlaylist = { playlistTarget = it },
                            onDownload = ::downloadSong,
                            downloadedIds = downloads.map(Song::videoId).toSet(),
                            downloadInProgress = downloadInProgress,
                            contentPadding = contentPadding,
                            menu = { song -> songMenu(song) },
                        )
                        destination == DesktopDestination.LIBRARY -> DesktopLibraryPage(
                            replay = replaySummary,
                            signedIn = youtubeSignedIn,
                            cloud = libraryState,
                            playlists = playlists,
                            onOpenPlaylist = ::openPlaylist,
                            onCreatePlaylist = { overlays.playlistDialog = true },
                            onOpenReplay = { overlays.replay = true },
                            onShelfItemClick = ::openShelfItem,
                            onSignIn = { overlays.accounts = true },
                            onRetryCloud = ::reloadLibrary,
                            shelfSort = shelfSort,
                            onShelfSortChange = {
                                shelfSort = it
                                persistence.saveString(KEY_LIBRARY_SORT, it.name)
                            },
                            contentPadding = contentPadding,
                        )
                        destination == DesktopDestination.HISTORY -> DesktopHistoryPage(
                            // The account's history when there is one, and what
                            // this computer played when there is not.
                            history = remoteHistory.ifEmpty { history },
                            onSongClick = { playSong(it) },
                            onDownload = ::downloadSong,
                            onAddToPlaylist = { playlistTarget = it },
                            downloadedIds = downloads.map(Song::videoId).toSet(),
                            downloadInProgress = downloadInProgress,
                            contentPadding = contentPadding,
                            menu = { song -> songMenu(song) },
                        )
                        destination == DesktopDestination.DOWNLOADS -> Box(Modifier.fillMaxSize()) {
                            DesktopDownloadsPage(
                                downloads = downloads,
                                onSongClick = { playSong(it) },
                                contentPadding = contentPadding,
                                menu = { song -> songMenu(song) },
                            )
                            // Only while there is a queue to look at.
                            if (downloadInProgress.isNotEmpty()) {
                                DesktopActionButton(
                                    "Queue · ${downloadInProgress.size}",
                                    Icons.Rounded.Download,
                                    onClick = { overlays.downloadManager = true },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(end = 28.dp, top = 28.dp),
                                )
                            }
                        }
                        destination == DesktopDestination.LOCAL_MUSIC -> DesktopLocalMusicPage(
                            songs = localSongs,
                            onSongClick = { playSong(it) },
                            contentPadding = contentPadding,
                            menu = { song -> songMenu(song) },
                        )
                    }

            }
        }
        }
    }

}

@Composable
private fun DesktopQueueOverlay(
    queue: List<Song>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onSongClick: (Int) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // Same scrim as the account switcher's, and `indication = null` is the load-bearing half.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        val shape = RoundedCornerShape(16.dp)
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 64.dp, end = 16.dp)
                .widthIn(min = 300.dp, max = 420.dp)
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .clip(shape)
                // The same pane the floating bars are made of.
                .desktopBarGlass(shape),
            shape = shape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
            // No elevation: a transparent Surface has nothing to hide its own shadow, so it draws
            // inside the pane as a second, inset box.
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, top = 14.dp, end = 10.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(DesktopStrings["up_next", "Up Next"], style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    DesktopToolbarButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, DesktopStrings["d_close_up_next", "Close Up Next"], tint = DesktopSecondary)
                    }
                }
                HorizontalDivider(color = DesktopDivider)
                if (queue.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().height(120.dp).padding(18.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(DesktopStrings["d_your_queue_is_empty", "Your queue is empty."], color = DesktopSecondary)
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(queue) { index, song ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onSongClick(index) }
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                DesktopArtwork(song.thumbnailUrl, Modifier.size(42.dp).clip(RoundedCornerShape(6.dp)), px = ROW_ART_PX)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        song.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (index == currentIndex) DesktopAccent else Color.White,
                                    )
                                    Text(
                                        song.artist,
                                        color = DesktopSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopTopBar(
    compact: Boolean,
    song: Song?,
    isPlaying: Boolean,
    volume: Float,
    shuffle: Boolean,
    repeatMode: DesktopRepeatMode,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onRepeatModeChange: (DesktopRepeatMode) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onOpenQueue: () -> Unit,
    accountAvatar: String?,
    onOpenAccounts: () -> Unit,
) {
    // Also a drag handle for the window, on the platform where the window has no system frame.
    DesktopTitleBarDragArea(Modifier.fillMaxWidth().height(56.dp)) {
    // No rule around the bar: it dissolves into the page instead. Glass by the stretch rather than
    // across the whole bar, because the sidebar carries on under the first 220dp and a pane that
    // faded out there would leave a pale band along the top of it.
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .width(220.dp)
                .fillMaxHeight()
                .desktopChromeGlass(if (compact) DesktopChromeEdge.BOTTOM else DesktopChromeEdge.NONE)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(Res.drawable.logo),
                contentDescription = "BitChord",
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "BitChord",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .desktopChromeGlass(DesktopChromeEdge.BOTTOM)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Transport and the now-playing pill belong to one player at a time.
            if (!compact) {
            DesktopToolbarButton(onClick = { onShuffleChange(!shuffle) }) {
                Icon(Icons.Rounded.Shuffle, DesktopStrings["shuffle", "Shuffle"], tint = if (shuffle) DesktopAccent else DesktopSecondary)
            }
            DesktopToolbarButton(onClick = onPrevious) {
                Icon(Icons.Rounded.FastRewind, DesktopStrings["widget_previous", "Previous"], modifier = Modifier.size(20.dp))
            }
            DesktopToolbarButton(onClick = onPlayPause) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            DesktopToolbarButton(onClick = onNext) {
                Icon(Icons.Rounded.FastForward, DesktopStrings["widget_next", "Next"], modifier = Modifier.size(20.dp))
            }
            DesktopToolbarButton(onClick = { onRepeatModeChange(repeatMode.next()) }) {
                Icon(
                    // Repeat-one says which mode is on rather than only that one is, the way the
                    // player's own control does.
                    if (repeatMode == DesktopRepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "Repeat ${repeatMode.label()}",
                    tint = if (repeatMode != DesktopRepeatMode.OFF) DesktopAccent else DesktopSecondary,
                )
            }
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier
                        .widthIn(min = 280.dp, max = 420.dp)
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .clickable(onClick = onOpenNowPlaying),
                    color = Color.White.copy(alpha = 0.12f),
                    tonalElevation = 0.dp,
                ) {
                    if (song == null) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            Icon(Icons.Rounded.MusicNote, DesktopStrings["playback_channel_name", "Now playing"], tint = DesktopSecondary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("BitChord", color = DesktopSecondary, style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        Row(
                            Modifier.fillMaxSize().padding(end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DesktopArtwork(song.thumbnailUrl, Modifier.size(42.dp).clip(RoundedCornerShape(2.dp)), px = ROW_ART_PX)
                            Column(
                                Modifier.weight(1f).padding(horizontal = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    song.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    song.artist,
                                    color = DesktopSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
            }
            // Without the pill taking the slack, the utilities would slide over and sit against the
            // wordmark.
            if (compact) Spacer(Modifier.weight(1f))
            Row(
                Modifier.width(280.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.VolumeUp, DesktopStrings["d_volume", "Volume"], tint = DesktopSecondary, modifier = Modifier.size(18.dp))
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    modifier = Modifier.width(92.dp).height(28.dp),
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        activeTrackColor = DesktopSecondary,
                        inactiveTrackColor = DesktopDivider,
                        thumbColor = Color.White,
                    ),
                )
                // No second button for the player here.
                DesktopToolbarButton(onClick = onOpenQueue) {
                    Icon(Icons.Rounded.QueueMusic, DesktopStrings["queue", "Queue"], tint = Color.White, modifier = Modifier.size(20.dp))
                }
                // Last in the row, which is where an account lives on every desktop that has one.
                DesktopAccountButton(avatar = accountAvatar, onClick = onOpenAccounts)
            }
        }
    }
    }
}

@Composable
private fun DesktopToolbarButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .desktopHoverWash()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun DesktopSidebar(
    destination: DesktopDestination,
    settingsOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onDestinationSelected: (DesktopDestination) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val searchFocusRequester = remember { FocusRequester() }
    val windowInfo = LocalWindowInfo.current
    var searchFocused by remember { mutableStateOf(false) }
    var restoreSearchFocus by remember { mutableStateOf(false) }

    LaunchedEffect(windowInfo.isWindowFocused) {
        if (!windowInfo.isWindowFocused) {
            restoreSearchFocus = searchFocused
        } else if (restoreSearchFocus) {
            // Let the desktop window finish its activation before asking Compose to put the native
            // text input target back in focus.
            yield()
            searchFocusRequester.requestFocus()
            restoreSearchFocus = false
        }
    }

    Column(
        Modifier
            .width(220.dp)
            .fillMaxHeight()
            .desktopChromeGlass(DesktopChromeEdge.END, fade = 0.07f)
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        DesktopSearchField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            focusRequester = searchFocusRequester,
            onFocusChanged = { searchFocused = it },
        )
        Spacer(Modifier.height(24.dp))
        Text(DesktopStrings["d_music", "MUSIC"], color = DesktopSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp))
        Spacer(Modifier.height(8.dp))
        DesktopSidebarItem(Icons.Rounded.Home, "Listen Now", destination == DesktopDestination.LISTEN_NOW) {
            onDestinationSelected(DesktopDestination.LISTEN_NOW)
        }
        DesktopSidebarItem(Icons.Rounded.Explore, "Explore", destination == DesktopDestination.EXPLORE) {
            onDestinationSelected(DesktopDestination.EXPLORE)
        }
        DesktopSidebarItem(Icons.Rounded.LibraryMusic, "Library", destination == DesktopDestination.LIBRARY) {
            onDestinationSelected(DesktopDestination.LIBRARY)
        }
        DesktopSidebarItem(Icons.Rounded.Search, "Search", destination == DesktopDestination.SEARCH) {
            onDestinationSelected(DesktopDestination.SEARCH)
        }
        Spacer(Modifier.height(20.dp))
        Text(DesktopStrings["d_your_collection", "YOUR COLLECTION"], color = DesktopSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp))
        Spacer(Modifier.height(8.dp))
        DesktopSidebarItem(Icons.Rounded.History, "History", destination == DesktopDestination.HISTORY) {
            onDestinationSelected(DesktopDestination.HISTORY)
        }
        val queued by DesktopDownloadQueue.active.collectAsState()
        DesktopSidebarItem(
            Icons.Rounded.Download,
            if (queued.isEmpty()) "Downloads" else "Downloads · ${queued.size}",
            destination == DesktopDestination.DOWNLOADS,
        ) {
            onDestinationSelected(DesktopDestination.DOWNLOADS)
        }
        DesktopSidebarItem(Icons.Rounded.LibraryMusic, "Local Music", destination == DesktopDestination.LOCAL_MUSIC) {
            onDestinationSelected(DesktopDestination.LOCAL_MUSIC)
        }
        Spacer(Modifier.weight(1f))
        HorizontalDivider(color = DesktopDivider)
        Spacer(Modifier.height(8.dp))
        DesktopSidebarItem(Icons.Rounded.Settings, "Settings", settingsOpen) {
            onOpenSettings()
        }
    }
}

@Composable
private fun DesktopSidebarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = if (selected) DesktopAccent else DesktopSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = if (selected) Color.White else DesktopSecondary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DesktopBottomChrome(
    compact: Boolean,
    destination: DesktopDestination,
    song: Song?,
    isPlaying: Boolean,
    onDestinationSelected: (DesktopDestination) -> Unit,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (compact && song != null) {
            DesktopMiniPlayer(
                song = song,
                isPlaying = isPlaying,
                onExpand = onExpand,
                onPlayPause = onPlayPause,
                onNext = onNext,
            )
        }
        DesktopFloatingNavigation(destination, onDestinationSelected)
    }
}

@Composable
private fun DesktopFloatingNavigation(
    destination: DesktopDestination,
    onDestinationSelected: (DesktopDestination) -> Unit,
) {
    val tabs = listOf(
        DesktopDestination.LISTEN_NOW to (Icons.Rounded.Home to "Play"),
        DesktopDestination.EXPLORE to (Icons.Rounded.Explore to "Explore"),
        DesktopDestination.LIBRARY to (Icons.Rounded.LibraryMusic to "Library"),
        DesktopDestination.SEARCH to (Icons.Rounded.Search to "Search"),
    )
    val selected = tabs.firstOrNull { it.first == destination }?.first ?: DesktopDestination.LIBRARY
    val shape = RoundedCornerShape(percent = 50)
    Surface(
        modifier = Modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .clip(shape)
            .desktopBarGlass(shape),
        shape = shape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tabs.forEach { (item, iconAndLabel) ->
                val isSelected = item == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (isSelected) DesktopAccent.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { onDestinationSelected(item) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(iconAndLabel.first, iconAndLabel.second, tint = if (isSelected) DesktopAccent else DesktopSecondary, modifier = Modifier.size(20.dp))
                    Text(iconAndLabel.second, color = if (isSelected) Color.White else DesktopSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun DesktopMiniPlayer(
    song: Song,
    isPlaying: Boolean,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        Modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .clip(shape)
            .desktopBarGlass(shape)
            .clickable(onClick = onExpand)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DesktopArtwork(song.thumbnailUrl, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)), px = ROW_ART_PX)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(song.artist, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onPlayPause) {
            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Pause" else "Play", tint = Color.White)
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Rounded.FastForward, DesktopStrings["widget_next", "Next"], tint = Color.White)
        }
    }
}

@Composable
internal fun DesktopSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChanged: (Boolean) -> Unit = {},
    placeholder: String = "Search",
) {
    val searchShape = RoundedCornerShape(7.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(searchShape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), searchShape)
            .clickable { focusRequester.requestFocus() }
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChanged(it.isFocused) }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                        onSearch()
                        true
                    } else {
                        false
                    }
                },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
            cursorBrush = SolidColor(DesktopAccent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { innerTextField ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = DesktopSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isBlank()) {
                            Text(placeholder, color = DesktopSecondary, maxLines = 1)
                        }
                        innerTextField()
                    }
                }
            },
        )
    }
}

@Composable
private fun DesktopFrame(
    containerColor: Color,
    modifier: Modifier = Modifier,
    /**
     * The open page's own colours, painted across the whole window rather than only the content
     * area, so the title bar, top bar and sidebar take their tint from the page they are framing
     * instead of staying a flat slab beside it.
     */
    backdrop: @Composable () -> Unit,
    topBar: @Composable (Boolean) -> Unit,
    sidebar: @Composable () -> Unit,
    bottomBar: @Composable (Boolean) -> Unit,
    overlay: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    // Held out here rather than inside the constraints box.
    val haze = remember { HazeState() }
    CompositionLocalProvider(LocalDesktopHaze provides haze) {
        Box(modifier.fillMaxSize().background(containerColor)) {
            // Both sources of the same state: the chrome blurs the backdrop behind it, and the
            // floating bottom bar blurs the page scrolling under it.
            Box(Modifier.fillMaxSize().hazeSource(haze)) { backdrop() }
            Column(Modifier.fillMaxSize()) {
                // Above everything, and outside the box the rest of the window is drawn in, because
                // that is what a title bar is.
                DesktopTitleBar()
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val compact = maxWidth < 980.dp
                        Column(Modifier.fillMaxSize()) {
                            topBar(compact)
                            Row(Modifier.fillMaxWidth().weight(1f)) {
                                if (!compact) sidebar()
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    Box(Modifier.fillMaxSize().hazeSource(haze)) {
                                        content(
                                            PaddingValues(
                                                start = if (compact) 0.dp else 8.dp,
                                                end = if (compact) 0.dp else 16.dp,
                                                // Room for the bar that floats over this, so the
                                                // last row can still be scrolled clear of it.
                                                bottom = if (compact) 128.dp else 16.dp,
                                            ),
                                        )
                                    }
                                    if (compact) {
                                        Box(Modifier.align(Alignment.BottomCenter)) { bottomBar(true) }
                                    }
                                }
                            }
                        }
                    }
                    overlay()
                }
            }
        }
    }
}

/**
 * What [DesktopFrame] paints behind the window.
 *
 * [artworkUrl] is the page's lead artwork when it has one, and the mesh built from it is laid under
 * ink so a page of text stays readable over it.
 */
@Composable
private fun DesktopPageBackdrop(artworkUrl: String?) {
    Box(Modifier.fillMaxSize().background(DesktopSurface)) {
        if (artworkUrl == null) return@Box
        DesktopMesh(artworkUrl)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.30f),
                        Color.Black.copy(alpha = 0.72f),
                        Color.Black.copy(alpha = 0.88f),
                    ),
                ),
            ),
        )
    }
}

/** The artwork's own colours under the player — see [DesktopArtworkMesh]. */
@Composable
private fun DesktopMeshBackdrop(url: String?, source: ImageBitmap? = null) {
    // Motion artwork wins when it is playing: it is what is actually on screen, and it is usually a
    // different shot from the still with a palette of its own.
    if (source != null) {
        val fromCanvas = remember(source) { DesktopArtworkMesh.of(source, seed = url.hashCode()) }
        if (fromCanvas != null) {
            MeshCanvas(fromCanvas)
            return
        }
    }
    val mesh by produceState<ImageBitmap?>(initialValue = null, key1 = url) {
        // Dropped before the next one is fetched. `produceState` restarts its producer when the key
        // changes but keeps whatever it last published, so without this the previous track's colours
        // stayed behind the new cover for as long as the fetch took — and if the fetch was slow that
        // was the whole song, with a green backdrop behind a black-and-white sleeve.
        value = null
        value = withContext(Dispatchers.IO) {
            DesktopArtworkCache.load(url.artworkAt(MESH_SOURCE_PX) ?: url)
                ?.let { DesktopArtworkMesh.of(it, seed = url.hashCode()) }
        }
    }
    val texture = mesh
    if (texture == null) {
        Box(Modifier.fillMaxSize().background(DesktopBackground))
        return
    }
    MeshCanvas(texture)
}

@Composable
private fun MeshCanvas(texture: ImageBitmap) {
    Canvas(Modifier.fillMaxSize()) {
        // One draw, bilinear: the sampler interpolates the whole mesh.
        drawImage(
            image = texture,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.Low,
        )
        // Android's phone player puts the mesh behind a full-bleed sleeve and stacks its text over
        // the artwork itself.
        drawRect(Color.Black.copy(alpha = MESH_SCRIM_ALPHA))
    }
}

/** What the mesh is averaged from — coarse by design. */
/** Whether AutoPlay may offer a song this session has already played or suggested. */
internal const val KEY_DONT_REPEAT_SUGGESTIONS = "dont_repeat_suggestions"

internal const val KEY_LIBRARY_SORT = "library_sort"

/** How a Library shelf's cards are ordered. A card carries a title and nothing else to sort on. */
enum class DesktopShelfSort { DEFAULT, TITLE_ASC, TITLE_DESC }

internal fun DesktopShelfSort.label(): String = when (this) {
    DesktopShelfSort.DEFAULT -> "Default order"
    DesktopShelfSort.TITLE_ASC -> "Alphabetical (A to Z)"
    DesktopShelfSort.TITLE_DESC -> "Alphabetical (Z to A)"
}

internal fun HomeShelf.sortedForLibrary(sort: DesktopShelfSort): HomeShelf = when (sort) {
    DesktopShelfSort.DEFAULT -> this
    DesktopShelfSort.TITLE_ASC ->
        copy(items = items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, ShelfItem::title)))
    DesktopShelfSort.TITLE_DESC ->
        copy(items = items.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER, ShelfItem::title)))
}

private const val MESH_SOURCE_PX = 120

/** How far the mesh is taken down so white content reads over it. */
private const val MESH_SCRIM_ALPHA = 0.55f

@Composable
private fun DesktopBackdrop(palette: DesktopArtworkPalette = DesktopArtworkPalette.Default) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        palette.secondary.copy(alpha = 0.34f),
                        DesktopBackground,
                        palette.primary.copy(alpha = 0.18f),
                    ),
                ),
            ),
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(620.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(palette.primary.copy(alpha = 0.30f), Color.Transparent),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .size(540.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(palette.secondary.copy(alpha = 0.24f), Color.Transparent),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, DesktopBackground.copy(alpha = 0.58f)),
                    ),
                ),
        )
    }
}

@Composable
private fun DesktopHomePage(
    state: UiState<List<HomeShelf>>,
    loadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onItemClick: (ShelfItem) -> Unit,
    contentPadding: PaddingValues,
) {
    DesktopPageScaffold(contentPadding) {
        when (state) {
            UiState.Loading -> DesktopLoadingPage("Loading your music…")
            is UiState.Error -> DesktopErrorPage(state.message, onRetry)
            is UiState.Success -> {
                LazyColumn(
                    contentPadding = pagePadding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    item { PageHeading(DesktopStrings["listen_now", "Listen Now"], DesktopStrings["d_your_music_made_personal", "Your music, made personal"]) }
                    if (state.data.isEmpty()) {
                        item { DesktopEmptyPage(Icons.Rounded.Home, "Your Listen Now feed is empty", "Search for an artist or song to get started.") }
                    } else {
                        state.data.forEachIndexed { index, shelf ->
                            item(key = "home-${shelf.title}-$index") {
                                DesktopShelf(
                                    shelf = shelf,
                                    hero = index == 0,
                                    onItemClick = onItemClick,
                                )
                            }
                        }
                    }
                    if (hasMore) {
                        // Reaching this row is the signal to fetch the next page, the same way the
                        // official client pages as you scroll rather than on a button.
                        item(key = "home-more") {
                            LaunchedEffect(state.data.size) { onLoadMore() }
                            Box(
                                Modifier.fillMaxWidth().height(72.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (loadingMore) {
                                    CircularProgressIndicator(
                                        color = DesktopAccent,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopExplorePage(
    state: UiState<List<MoodGenreSection>>,
    onCategoryClick: (MoodGenre) -> Unit,
    onRetry: () -> Unit,
    contentPadding: PaddingValues,
) {
    DesktopPageScaffold(contentPadding) {
        when (state) {
            UiState.Loading -> DesktopLoadingPage("Finding something new…")
            is UiState.Error -> DesktopErrorPage(state.message, onRetry)
            is UiState.Success -> LazyColumn(
                contentPadding = pagePadding(bottom = 32.dp),
            ) {
                item { PageHeading(DesktopStrings["explore", "Explore"], DesktopStrings["d_new_music_moods_and_discoveries", "New music, moods, and discoveries"]) }
                if (state.data.isEmpty()) {
                    item {
                        DesktopEmptyPage(
                            Icons.Rounded.Explore,
                            "Nothing to explore yet",
                            "Try again when YouTube Music is reachable.",
                        )
                    }
                } else {
                    state.data.forEach { section ->
                        item(key = "mood-${section.title}") {
                            DesktopMoodGenreGrid(section = section, onCategoryClick = onCategoryClick)
                        }
                    }
                }
            }
        }
    }
}

/** One server-defined group of category buttons. */
@Composable
private fun DesktopMoodGenreGrid(
    section: MoodGenreSection,
    onCategoryClick: (MoodGenre) -> Unit,
) {
    Column(Modifier.padding(bottom = 22.dp)) {
        Text(
            section.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = MOOD_GUTTER, end = MOOD_GUTTER, bottom = 12.dp),
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = ((maxWidth - MOOD_GUTTER * 2) / 300.dp).toInt().coerceIn(2, 5)
            val cardWidth = (maxWidth - MOOD_GUTTER * 2 - MOOD_SPACING * (columns - 1)) / columns
            Column(
                verticalArrangement = Arrangement.spacedBy(MOOD_SPACING),
                modifier = Modifier.padding(horizontal = MOOD_GUTTER),
            ) {
                section.items.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(MOOD_SPACING)) {
                        row.forEach { item ->
                            DesktopMoodGenreCard(
                                item = item,
                                onClick = { onCategoryClick(item) },
                                modifier = Modifier.width(cardWidth),
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.width(cardWidth)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopMoodGenreCard(
    item: MoodGenre,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = moodColor(item.title)
    Box(
        modifier = modifier
            .height(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        color,
                        color.copy(
                            red = color.red * .68f,
                            green = color.green * .68f,
                            blue = color.blue * .68f,
                        ),
                    ),
                ),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                // Pushed beyond the corner and rotated, so it reads as a cropped sleeve rather than
                // a floating rectangle.
                .offset(x = 10.dp, y = 12.dp)
                .size(82.dp)
                .graphicsLayer { rotationZ = 16f }
                .clip(RoundedCornerShape(7.dp))
                .background(Color.White.copy(alpha = .22f)),
        ) {
            item.thumbnailUrl?.let { artwork ->
                DesktopArtwork(artwork, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart).padding(end = 48.dp),
        )
    }
}

/** Android's own palette for category tiles, keyed the same way. */
private fun moodColor(title: String): Color = when ((title.hashCode() and Int.MAX_VALUE) % 8) {
    0 -> Color(0xFFE64A19)
    1 -> Color(0xFFEC0B65)
    2 -> Color(0xFF8664AC)
    3 -> Color(0xFF6B4EFF)
    4 -> Color(0xFFBE6100)
    5 -> Color(0xFF233C78)
    6 -> Color(0xFF4D97E5)
    else -> Color(0xFFAA267E)
}

/** Enough to scroll through, short of turning the shelf into the history page. */
private const val RECENTS_LIMIT = 20

private val MOOD_GUTTER = 28.dp
private val MOOD_SPACING = 12.dp

/** The playlist shelves behind one Explore category. */
@Composable
private fun DesktopMoodGenrePage(
    title: String,
    state: UiState<List<HomeShelf>>,
    onBack: () -> Unit,
    onItemClick: (ShelfItem) -> Unit,
    onRetry: () -> Unit,
    contentPadding: PaddingValues,
) {
    DesktopPageScaffold(contentPadding) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, top = 8.dp, end = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DesktopToolbarButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, DesktopStrings["d_back_to_explore", "Back to Explore"], tint = Color.White)
                }
            }
            when (state) {
                UiState.Loading -> DesktopLoadingPage("Loading $title…")
                is UiState.Error -> DesktopErrorPage(state.message, onRetry)
                is UiState.Success -> LazyColumn(
                    contentPadding = pagePadding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    item { PageHeading(title, "Playlists picked for this mood") }
                    state.data.forEachIndexed { index, shelf ->
                        item(key = "mood-shelf-${shelf.title}-$index") {
                            DesktopShelf(shelf, hero = false, onItemClick = onItemClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopSearchPage(
    query: String,
    onQueryChange: (String) -> Unit,
    /** Terms searched before, most recent first; kept on this computer only. */
    history: List<String>,
    /** YouTube's typeahead for what is being typed now. */
    suggestions: List<String>,
    onPickTerm: (String) -> Unit,
    /**
     * Puts a suggestion in the field without running it, so it can be added to — the arrow at the
     * end of a typeahead row, as YouTube Music's own has.
     */
    onFillTerm: (String) -> Unit,
    onForgetTerm: (String) -> Unit,
    onClearHistory: () -> Unit,
    filter: SearchFilter,
    onFilterChange: (SearchFilter) -> Unit,
    rows: List<SearchResult>,
    loading: Boolean,
    error: String?,
    onSearch: () -> Unit,
    onSongClick: (Song) -> Unit,
    onBrowseClick: (com.music.bitchord.data.model.BrowseItem) -> Unit,
    likedIds: Set<String>,
    onToggleLike: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    downloadedIds: Set<String>,
    downloadInProgress: Set<String>,
    contentPadding: PaddingValues,
    menu: (@Composable (Song) -> Unit)? = null,
) {
    val searchFocusRequester = remember { FocusRequester() }
    val windowInfo = LocalWindowInfo.current
    var searchFocused by remember { mutableStateOf(false) }
    var restoreSearchFocus by remember { mutableStateOf(false) }

    LaunchedEffect(windowInfo.isWindowFocused) {
        if (!windowInfo.isWindowFocused) {
            restoreSearchFocus = searchFocused
        } else if (restoreSearchFocus) {
            yield()
            searchFocusRequester.requestFocus()
            restoreSearchFocus = false
        }
    }

    DesktopPageScaffold(contentPadding) {
        Column(Modifier.fillMaxSize().padding(horizontal = DesktopPageGutter)) {
            PageHeading(DesktopStrings["search", "Search"], DesktopStrings["d_find_anything_in_youtube_music", "Find anything in YouTube Music"], gutter = 0.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                DesktopSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSearch = onSearch,
                    focusRequester = searchFocusRequester,
                    onFocusChanged = { searchFocused = it },
                    modifier = Modifier
                        .weight(1f),
                    placeholder = DesktopStrings["d_artists_songs_albums_playlists", "Artists, songs, albums, playlists"],
                )
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = onSearch, enabled = query.isNotBlank() && !loading) {
                    Icon(Icons.Rounded.ArrowForward, DesktopStrings["search", "Search"])
                }
            }
            Spacer(Modifier.height(14.dp))
            // A non-empty suggestion list means the field is mid-edit.
            val suggesting = suggestions.isNotEmpty()
            if (!suggesting) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchFilter.entries.forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { onFilterChange(option) },
                            label = { Text(option.label) },
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
            when {
                suggesting -> LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding(bottom = 32.dp)) {
                    itemsIndexed(suggestions, key = { _, term -> "suggestion:$term" }) { index, term ->
                        DesktopTermRow(
                            term = term,
                            icon = Icons.Rounded.Search,
                            onClick = { onPickTerm(term) },
                            trailingIcon = Icons.Rounded.NorthWest,
                            trailingDescription = "Use this search",
                            // The lead row *is* what is in the field, so there is nothing to fill
                            // it with and the arrow would be a button that does nothing.
                            onTrailing = if (index == 0) null else ({ onFillTerm(term) }),
                        )
                    }
                }
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DesktopAccent) }
                error != null -> DesktopErrorPage(error) { onSearch() }
                // Emptying the field is also how the recent searches are got back to, which is the
                // only way back to them once a search has put results on the page.
                (rows.isEmpty() || query.isBlank()) && history.isNotEmpty() -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = pagePadding(bottom = 32.dp),
                ) {
                    item(key = "recent:header") {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                DesktopStrings["recent_searches", "Recent searches"],
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onClearHistory) { Text(DesktopStrings["clear", "Clear"], color = DesktopSecondary) }
                        }
                    }
                    items(history, key = { "recent:$it" }) { term ->
                        DesktopTermRow(
                            term = term,
                            icon = Icons.Rounded.History,
                            onClick = { onPickTerm(term) },
                            trailingIcon = Icons.Rounded.Close,
                            trailingDescription = "Forget $term",
                            onTrailing = { onForgetTerm(term) },
                        )
                    }
                }
                rows.isEmpty() -> DesktopEmptyPage(Icons.Rounded.Search, "Search BitChord", "Your results will appear here.")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = pagePadding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // The unfiltered response nominates its own best music hit, and YouTube Music
                    // promotes it rather than listing it — so does Android.
                    val topResult = rows.filterIsInstance<SearchResult.TopTrack>().firstOrNull()
                    if (filter == SearchFilter.ALL && topResult != null) {
                        item(key = "search:top:${topResult.song.videoId}") {
                            DesktopTopResultCard(
                                song = topResult.song,
                                liked = topResult.song.videoId in likedIds,
                                downloaded = topResult.song.videoId in downloadedIds,
                                downloadInProgress = topResult.song.videoId in downloadInProgress,
                                onPlay = { onSongClick(topResult.song) },
                                onToggleLike = onToggleLike,
                                onDownload = onDownload,
                                onAddToPlaylist = onAddToPlaylist,
                            )
                        }
                    }
                    desktopSearchSections(rows, filter).forEach { section ->
                        section.title?.let { title ->
                            item(key = "search-section:$title") {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                                )
                            }
                        }
                        items(section.rows, key = { row -> row.key() }) { result ->
                            when (result) {
                                // Already drawn above, as the promoted card.
                                is SearchResult.TopTrack -> Unit
                                is SearchResult.Track -> DesktopSongRow(
                                    song = result.song,
                                    liked = result.song.videoId in likedIds,
                                    onClick = onSongClick,
                                    onToggleLike = onToggleLike,
                                    onDownload = onDownload,
                                    onAddToPlaylist = onAddToPlaylist,
                                    downloaded = result.song.videoId in downloadedIds,
                                    downloadInProgress = result.song.videoId in downloadInProgress,
                                    menu = menu,
                                )
                                is SearchResult.Browse -> DesktopBrowseRow(result.item, onBrowseClick)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * An artist: their picture, the numbers under it, their top songs, and the carousels of what they
 * have released.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesktopArtistPage(
    state: UiState<ArtistPage>,
    fallbackName: String,
    likedIds: Set<String>,
    downloadedIds: Set<String>,
    downloadInProgress: Set<String>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onToggleLike: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onShelfItemClick: (ShelfItem) -> Unit,
    /** Null for a guest: a channel subscription is the account's. */
    onToggleSubscription: ((SubscriptionState) -> Unit)?,
    contentPadding: PaddingValues,
) {
    DesktopPageScaffold(contentPadding) {
        when (state) {
            UiState.Loading -> Box(Modifier.fillMaxSize()) {
                DesktopBackButton(onBack)
                DesktopLoadingPage("Loading $fallbackName…")
            }
            is UiState.Error -> Box(Modifier.fillMaxSize()) {
                DesktopBackButton(onBack)
                DesktopErrorPage(state.message, onRetry)
            }
            is UiState.Success -> {
                val artist = state.data
                val name = artist.name?.takeIf(String::isNotBlank) ?: fallbackName
                val palette = rememberDesktopArtworkPalette(artist.thumbnailUrl)
                // Android caps the top-songs block and pages it sideways so the release shelves are
                // not buried under a hundred rows.
                val top = artist.songs.take(MAX_ARTIST_SONGS)
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        contentPadding = pagePadding(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        item(key = "artist-header") {
                            DesktopArtistBanner(artist.thumbnailUrl, name, palette)
                        }
                        if (artist.subscriberCountText != null || artist.monthlyListenerCount != null) {
                            item(key = "artist-stats") {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    // YouTube's own text already reads "1.2M subscribers" in full,
                                    // so only the number is kept and the label re-said in the app's
                                    // words.
                                    artist.subscriberCountText?.let {
                                        DesktopStatChip(Icons.Rounded.Person, "${it.substringBefore(' ')} subscribers")
                                    }
                                    artist.monthlyListenerCount?.let {
                                        DesktopStatChip(Icons.Rounded.GraphicEq, "${it.substringBefore(' ')} monthly listeners")
                                    }
                                }
                            }
                        }
                        item(key = "artist-actions") {
                            FlowRow(
                                Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                DesktopActionButton(DesktopStrings["play", "Play"], Icons.Rounded.PlayArrow) {
                                    if (top.isNotEmpty()) onPlaySongs(top, 0)
                                }
                                DesktopActionButton(DesktopStrings["shuffle", "Shuffle"], Icons.Rounded.Shuffle) { onShuffle(top) }
                                val subscription = artist.subscription
                                if (subscription != null && onToggleSubscription != null) {
                                    DesktopSubscribeButton(subscription.subscribed) {
                                        onToggleSubscription(subscription)
                                    }
                                }
                            }
                        }
                        artist.description?.takeIf(String::isNotBlank)?.let { blurb ->
                            item(key = "artist-about") {
                                Column(Modifier.padding(top = 16.dp)) {
                                    SectionTitle(DesktopStrings["d_about_artist", "About artist"])
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        blurb,
                                        color = DesktopSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(horizontal = 28.dp),
                                    )
                                }
                            }
                        }
                        if (top.isNotEmpty()) {
                            // Listed, the way an album or a playlist lists its tracks here.
                            item(key = "artist-top-songs") {
                                Column(Modifier.padding(top = 20.dp, bottom = 6.dp)) {
                                    SectionTitle(DesktopStrings["top_songs", "Top songs"])
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                            item(key = "artist-top-songs-header") {
                                Box(Modifier.padding(horizontal = 28.dp)) {
                                    DesktopCollectionTableHeader(hasRemove = false)
                                }
                            }
                            itemsIndexed(top, key = { _, song -> "artist-song-${song.videoId}" }) { index, song ->
                                Box(Modifier.padding(horizontal = 28.dp)) {
                                    DesktopCollectionSongRow(
                                        song = song,
                                        collectionType = BrowseType.ARTIST,
                                        collectionTitle = name,
                                        liked = song.videoId in likedIds,
                                        onClick = { onPlaySongs(top, index) },
                                        onToggleLike = onToggleLike,
                                        onDownload = onDownload,
                                        onAddToPlaylist = onAddToPlaylist,
                                        downloaded = song.videoId in downloadedIds,
                                        downloadInProgress = song.videoId in downloadInProgress,
                                        number = index + 1,
                                    )
                                }
                            }
                        }
                        artist.sections.forEach { shelf ->
                            item(key = "artist-shelf-${shelf.title}") {
                                Box(Modifier.padding(top = 18.dp)) {
                                    DesktopShelf(shelf, hero = false, onItemClick = onShelfItemClick)
                                }
                            }
                        }
                        if (top.isEmpty() && artist.sections.isEmpty()) {
                            item {
                                DesktopEmptyPage(
                                    Icons.Rounded.Person,
                                    "Nothing to show for $name",
                                    "YouTube Music has no songs or releases on this page.",
                                )
                            }
                        }
                    }
                    DesktopBackButton(onBack)
                }
            }
        }
    }
}

/** The photograph, and the name across the foot of it. */
@Composable
private fun DesktopArtistBanner(url: String?, name: String, palette: DesktopArtworkPalette) {
    Box(Modifier.fillMaxWidth().height(ARTIST_BANNER_HEIGHT)) {
        DesktopArtwork(url, Modifier.matchParentSize(), px = HEADER_ART_PX)
        // Two washes rather than one.
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to palette.primary.copy(alpha = 0.18f),
                    1f to DesktopSurface,
                ),
            ),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.35f),
                    0.4f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.45f),
                ),
            ),
        )
        Text(
            name,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 28.dp, end = 28.dp, bottom = 18.dp),
        )
    }
}

@Composable
private fun DesktopStatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(DesktopGlass.copy(alpha = 0.55f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = DesktopSecondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Color.White)
    }
}

/** Subscribe, and its opposite. */
@Composable
private fun DesktopSubscribeButton(subscribed: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (subscribed) Color.Transparent else DesktopAccent)
            .border(
                0.5.dp,
                if (subscribed) Color.White.copy(alpha = 0.25f) else Color.Transparent,
                CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (subscribed) Icons.Rounded.Check else Icons.Rounded.Add,
            null,
            tint = if (subscribed) DesktopSecondary else Color.White,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (subscribed) "Subscribed" else "Subscribe",
            color = if (subscribed) DesktopSecondary else Color.White,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** The way back off a page that has no navigation of its own. */
@Composable
private fun BoxScope.DesktopBackButton(onBack: () -> Unit) {
    IconButton(
        onClick = onBack,
        modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 16.dp),
    ) {
        Icon(Icons.Rounded.ArrowBack, DesktopStrings["back", "Back"], tint = Color.White)
    }
}

/** A credit that leads somewhere, when the catalogue gave it a browse id. */
private fun Modifier.opensArtist(artistId: String?, onOpen: (String) -> Unit): Modifier =
    if (artistId.isNullOrBlank()) {
        this
    } else {
        clip(RoundedCornerShape(6.dp)).clickable { onOpen(artistId) }
    }

/** The artist page that is open, and the name to bill it under until it loads. */
private data class DesktopArtistTarget(val browseId: String, val name: String)

private data class DesktopSearchSection(val title: String?, val rows: List<SearchResult>)

/**
 * The unfiltered page, grouped so its mixed result types are readable at a glance — songs, then
 * artists, albums, playlists and whatever else came back.
 */
private fun desktopSearchSections(rows: List<SearchResult>, filter: SearchFilter): List<DesktopSearchSection> {
    if (filter != SearchFilter.ALL) return listOf(DesktopSearchSection(null, rows))
    fun browse(type: BrowseType) = rows.filterIsInstance<SearchResult.Browse>().filter { it.item.type == type }
    return listOf(
        DesktopSearchSection("Songs", rows.filterIsInstance<SearchResult.Track>()),
        DesktopSearchSection("Artists", browse(BrowseType.ARTIST)),
        DesktopSearchSection("Albums", browse(BrowseType.ALBUM)),
        DesktopSearchSection("Playlists", browse(BrowseType.PLAYLIST)),
        DesktopSearchSection("More", browse(BrowseType.OTHER)),
    ).filter { it.rows.isNotEmpty() }
}

/** The All response's highest-confidence music hit, promoted out of the list. */
@Composable
private fun DesktopTopResultCard(
    song: Song,
    liked: Boolean,
    downloaded: Boolean,
    downloadInProgress: Boolean,
    onPlay: () -> Unit,
    onToggleLike: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
) {
    Column(
        Modifier
            // Full width, like the rows under it.
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DesktopGlass.copy(alpha = 0.38f))
            .clickable(onClick = onPlay)
            .padding(16.dp),
    ) {
        Text(DesktopStrings["d_top_result", "Top result"], style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DesktopArtwork(
                song.thumbnailUrl,
                Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)),
                px = ROW_ART_PX,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DesktopSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { onToggleLike(song) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    "Favorite",
                    tint = if (liked) DesktopAccent else DesktopSecondary,
                    modifier = Modifier.size(19.dp),
                )
            }
            IconButton(
                onClick = { if (!downloaded) onDownload(song) },
                modifier = Modifier.size(36.dp),
            ) {
                if (downloadInProgress) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = DesktopAccent, strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (downloaded) Icons.Rounded.Download else Icons.Rounded.CloudDownload,
                        if (downloaded) "Downloaded" else "Download",
                        tint = if (downloaded) DesktopAccent else DesktopSecondary,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DesktopActionButton(DesktopStrings["play", "Play"], Icons.Rounded.PlayArrow, onClick = onPlay)
            DesktopActionButton(DesktopStrings["playlist_action", "Playlist"], Icons.AutoMirrored.Rounded.PlaylistAdd) { onAddToPlaylist(song) }
        }
    }
}

/** One term in the search box's own lists — a typeahead suggestion or a recent search. */
@Composable
private fun DesktopTermRow(
    term: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    trailingDescription: String,
    onTrailing: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = DesktopSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(14.dp))
        Text(term, Modifier.weight(1f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onTrailing == null) {
            Spacer(Modifier.width(34.dp))
        } else {
            IconButton(onClick = onTrailing, modifier = Modifier.size(34.dp)) {
                Icon(trailingIcon, trailingDescription, tint = DesktopSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** The Library tab: what the account has saved, and what this computer has. */
@Composable
private fun DesktopLibraryPage(
    replay: DesktopReplaySummary,
    playlists: List<DesktopPlaylist>,
    signedIn: Boolean,
    cloud: UiState<LibraryPage>,
    onOpenPlaylist: (DesktopPlaylist) -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenReplay: () -> Unit,
    onShelfItemClick: (ShelfItem) -> Unit,
    onSignIn: () -> Unit,
    onRetryCloud: () -> Unit,
    shelfSort: DesktopShelfSort,
    onShelfSortChange: (DesktopShelfSort) -> Unit,
    contentPadding: PaddingValues,
) {
    DesktopPageScaffold(contentPadding) {
        LazyColumn(
            // No horizontal padding of its own.
            contentPadding = pagePadding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                var sortMenuOpen by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().padding(end = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        PageHeading(DesktopStrings["library", "Library"], DesktopStrings["d_everything_you_save_and_play", "Everything you save and play"])
                    }
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.Rounded.Sort, DesktopStrings["d_sort_library", "Sort library"], tint = Color.White)
                        }
                        DropdownMenu(sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            DesktopShelfSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label()) },
                                    trailingIcon = if (option == shelfSort) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        onShelfSortChange(option)
                                        sortMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            // Drawn whether or not anything has been played: with nothing behind it the page still
            // has to say the feature exists.
            item(key = "replay") {
                DesktopReplayBanner(replay.heroCards().firstOrNull(), onOpenReplay)
            }
            when {
                !signedIn -> item {
                    DesktopEmptyPage(
                        Icons.Rounded.AccountCircle,
                        "Your YouTube Music library",
                        "Sign in to your Google account to see your liked songs, playlists, and YouTube Music history.",
                        onSignIn,
                        "Sign in",
                    )
                }
                cloud is UiState.Loading -> item { DesktopLoadingPage("Loading your library…") }
                cloud is UiState.Error -> item { DesktopErrorPage(cloud.message, onRetryCloud) }
                cloud is UiState.Success && cloud.data.shelves.isEmpty() -> item {
                    DesktopEmptyPage(
                        Icons.Rounded.LibraryMusic,
                        "Nothing saved yet",
                        "Playlists, albums and artists you save on YouTube Music show up here.",
                    )
                }
                cloud is UiState.Success -> cloud.data.shelves.forEach { shelf ->
                    item(key = "library-${shelf.title}") {
                        DesktopShelf(shelf.sortedForLibrary(shelfSort), hero = false, onItemClick = onShelfItemClick)
                    }
                }
            }
            // Titled for where they live: the shelf above is also called Playlists, and it is the
            // account's.
            item { SectionTitle(DesktopStrings["d_playlists_on_this_computer", "Playlists on this computer"]) }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    playlists.forEach { playlist ->
                        LibraryTile(
                            Icons.Rounded.QueueMusic,
                            playlist.title,
                            "${playlist.songs.size} songs",
                        ) { onOpenPlaylist(playlist) }
                    }
                    // Last in the row, where the listener asked for it.
                    LibraryTile(
                        Icons.Rounded.Add,
                        DesktopStrings["new_playlist", "New Playlist"],
                        "Create a collection",
                        onClick = onCreatePlaylist,
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopHistoryPage(
    history: List<Song>,
    onSongClick: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    downloadedIds: Set<String>,
    downloadInProgress: Set<String>,
    contentPadding: PaddingValues,
    menu: (@Composable (Song) -> Unit)? = null,
) {
    DesktopPageScaffold(contentPadding) {
        LazyColumn(
            contentPadding = pagePadding(start = DesktopPageGutter, end = DesktopPageGutter, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { PageHeading(DesktopStrings["history", "History"], DesktopStrings["d_recently_played_on_this_computer", "Recently played on this computer"], gutter = 0.dp) }
            if (history.isEmpty()) item { DesktopEmptyPage(Icons.Rounded.History, "Nothing played yet", "Songs you play will show up here.") }
            else items(history, key = Song::videoId) {
                DesktopSongRow(
                    song = it,
                    liked = false,
                    onClick = onSongClick,
                    onToggleLike = null,
                    onDownload = onDownload,
                    onAddToPlaylist = onAddToPlaylist,
                    downloaded = it.videoId in downloadedIds,
                    downloadInProgress = it.videoId in downloadInProgress,
                    menu = menu,
                )
            }
        }
    }
}

@Composable
private fun DesktopDownloadsPage(
    downloads: List<Song>,
    onSongClick: (Song) -> Unit,
    contentPadding: PaddingValues,
    menu: (@Composable (Song) -> Unit)? = null,
) {
    DesktopLocalMusicPage(
        songs = downloads,
        onSongClick = onSongClick,
        contentPadding = contentPadding,
        menu = menu,
        title = DesktopStrings["downloads", "Downloads"],
        subtitle = DesktopStrings["d_available_offline", "Available offline"],
        emptyIcon = Icons.Rounded.CloudDownload,
        emptyTitle = DesktopStrings["d_no_downloads", "No downloads"],
        emptyDescription = DesktopStrings["d_downloaded_songs_will_appear_here", "Downloaded songs will appear here."],
        persistenceKey = "downloaded",
    )
}

@Composable
private fun DesktopLocalMusicPage(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    contentPadding: PaddingValues,
    title: String = "Local Music",
    subtitle: String = "Audio files found in Music and Downloads",
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.MusicNote,
    emptyTitle: String = "No local music",
    emptyDescription: String = "Put audio files in your Music folder and reopen this page.",
    persistenceKey: String = "local",
    menu: (@Composable (Song) -> Unit)? = null,
) {
    val persistence = remember { DesktopPersistence() }
    var selectedTab by remember(persistenceKey) { mutableStateOf(0) }
    var searchQuery by remember(persistenceKey) { mutableStateOf("") }
    var view by remember(persistenceKey) {
        mutableStateOf(
            if (persistence.string("${persistenceKey}_view", "LIST") == "GRID") {
                DesktopLibraryView.GRID
            } else {
                DesktopLibraryView.LIST
            },
        )
    }
    var sort by remember(persistenceKey) {
        mutableStateOf(
            runCatching {
                DesktopLibrarySort.valueOf(persistence.string("${persistenceKey}_sort", "TITLE_ASC"))
            }.getOrDefault(DesktopLibrarySort.TITLE_ASC),
        )
    }
    var drillDown by remember(persistenceKey) { mutableStateOf<Pair<String, List<Song>>?>(null) }
    val filteredSongs = remember(songs, sort, searchQuery) {
        songs.sortedForDesktopLibrary(sort).filter { song ->
            searchQuery.isBlank() || song.title.contains(searchQuery, true) || song.artist.contains(searchQuery, true) ||
                song.albumName.orEmpty().contains(searchQuery, true)
        }
    }
    val artists = remember(filteredSongs) {
        filteredSongs.groupBy { it.artist.ifBlank { "On This Computer" } }
            .entries.sortedBy { it.key.lowercase() }
    }
    val albums = remember(filteredSongs) {
        filteredSongs.groupBy { it.albumName?.takeIf(String::isNotBlank) ?: "Unknown Album" }
            .entries.sortedBy { it.key.lowercase() }
    }
    DesktopPageScaffold(contentPadding) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = DesktopPageGutter),
        ) {
            PageHeading(title, subtitle, gutter = 0.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                DesktopSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = {},
                    modifier = Modifier.weight(1f),
                    placeholder = DesktopStrings["d_search_your_music", "Search your music"],
                )
                Spacer(Modifier.width(10.dp))
                TextButton(onClick = {
                    sort = when (sort) {
                        DesktopLibrarySort.TITLE_ASC -> DesktopLibrarySort.TITLE_DESC
                        DesktopLibrarySort.TITLE_DESC -> DesktopLibrarySort.DATE_ADDED
                        DesktopLibrarySort.DATE_ADDED -> DesktopLibrarySort.DATE_MODIFIED
                        DesktopLibrarySort.DATE_MODIFIED -> DesktopLibrarySort.TITLE_ASC
                    }
                    persistence.saveString("${persistenceKey}_sort", sort.name)
                }) {
                    Text(sort.label())
                }
                IconButton(onClick = {
                    view = if (view == DesktopLibraryView.LIST) DesktopLibraryView.GRID else DesktopLibraryView.LIST
                    persistence.saveString("${persistenceKey}_view", view.name)
                }) {
                    Icon(if (view == DesktopLibraryView.LIST) Icons.Rounded.ViewModule else Icons.Rounded.ViewList, "Change view")
                }
            }
            Spacer(Modifier.height(10.dp))
            TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
                listOf("Songs", "Artists", "Albums").forEachIndexed { index, label ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(label) })
                }
            }
            Spacer(Modifier.height(8.dp))
            if (songs.isEmpty()) {
                DesktopEmptyPage(emptyIcon, emptyTitle, emptyDescription)
            } else if (drillDown != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { drillDown = null }) { Icon(Icons.Rounded.ArrowBack, DesktopStrings["back", "Back"]) }
                    Text(drillDown!!.first, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
                DesktopLibrarySongContent(drillDown!!.second, view, onSongClick, menu)
            } else when (selectedTab) {
                0 -> DesktopLibrarySongContent(filteredSongs, view, onSongClick, menu)
                1 -> DesktopLibraryGroupingContent(artists, view, onGroupClick = { drillDown = it })
                else -> DesktopLibraryGroupingContent(albums, view, onGroupClick = { drillDown = it })
            }
        }
    }
}

private fun DesktopLibrarySort.label(): String = when (this) {
    DesktopLibrarySort.TITLE_ASC -> "A–Z"
    DesktopLibrarySort.TITLE_DESC -> "Z–A"
    DesktopLibrarySort.DATE_ADDED -> "Added"
    DesktopLibrarySort.DATE_MODIFIED -> "Modified"
}

@Composable
private fun DesktopLibrarySongContent(
    songs: List<Song>,
    view: DesktopLibraryView,
    onSongClick: (Song) -> Unit,
    menu: (@Composable (Song) -> Unit)? = null,
) {
    if (songs.isEmpty()) {
        DesktopEmptyPage(Icons.Rounded.Search, "No matching music", "Try a different search.")
    } else if (view == DesktopLibraryView.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = pagePadding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            gridItems(songs, key = Song::videoId) { song ->
                Column(Modifier.fillMaxWidth().clickable { onSongClick(song) }) {
                    DesktopArtwork(song.thumbnailUrl, Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.height(7.dp))
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(song.artist, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = pagePadding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(songs, key = Song::videoId) { song ->
                DesktopSongRow(
                    song,
                    liked = false,
                    onClick = onSongClick,
                    onToggleLike = null,
                    downloaded = song.localPath != null,
                    menu = menu,
                )
            }
        }
    }
}

@Composable
private fun DesktopLibraryGroupingContent(
    groups: List<Map.Entry<String, List<Song>>>,
    view: DesktopLibraryView,
    onGroupClick: (Pair<String, List<Song>>) -> Unit,
) {
    if (groups.isEmpty()) {
        DesktopEmptyPage(Icons.Rounded.Search, "Nothing here yet", "Music will be grouped as it is added.")
    } else if (view == DesktopLibraryView.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = pagePadding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            gridItems(groups, key = { it.key }) { group ->
                Column(Modifier.fillMaxWidth().clickable { onGroupClick(group.key to group.value) }) {
                    DesktopArtwork(group.value.firstOrNull()?.thumbnailUrl, Modifier.fillMaxWidth().height(150.dp).clip(CircleShape))
                    Spacer(Modifier.height(7.dp))
                    Text(group.key, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text("${group.value.size} songs", color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = pagePadding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(groups, key = { it.key }) { group ->
                Row(
                    Modifier.fillMaxWidth().clickable { onGroupClick(group.key to group.value) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DesktopArtwork(group.value.firstOrNull()?.thumbnailUrl, Modifier.size(54.dp).clip(CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(group.key, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text("${group.value.size} songs", color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Rounded.PlayArrow, DesktopStrings["open", "Open"], tint = DesktopSecondary)
                }
            }
        }
    }
}

/**
 * Settings as a modal card over the page behind it, the way Music and iTunes present their own
 * preferences.
 */
@Composable
private fun DesktopSettingsDialog(
    autoplay: Boolean,
    onAutoplayChange: (Boolean) -> Unit,
    automix: Boolean,
    onAutomixChange: (Boolean) -> Unit,
    automixPerformance: AutomixPerformanceMode,
    onAutomixPerformanceChange: (AutomixPerformanceMode) -> Unit,
    shuffle: Boolean,
    onShuffleChange: (Boolean) -> Unit,
    repeatMode: DesktopRepeatMode,
    onRepeatModeChange: (DesktopRepeatMode) -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
    crossfadeSeconds: Int,
    onCrossfadeSecondsChange: (Int) -> Unit,
    animatedCanvas: Boolean,
    onAnimatedCanvasChange: (Boolean) -> Unit,
    spotifyCanvasReady: Boolean,
    onOpenSpotifyCanvasSetup: () -> Unit,
    dontRepeatSuggestions: Boolean,
    onDontRepeatSuggestionsChange: (Boolean) -> Unit,
    filterNonMusicAudio: Boolean,
    onFilterNonMusicAudioChange: (Boolean) -> Unit,
    onChooseLocalMusicFolder: (onChosen: () -> Unit) -> Unit,
    onLocalMusicFolderChanged: () -> Unit,
    syncedLyrics: Boolean,
    onSyncedLyricsChange: (Boolean) -> Unit,
    lyricsBlur: Boolean,
    onLyricsBlurChange: (Boolean) -> Unit,
    /** The sources that will actually be asked, in the order they are asked. */
    enabledLyricsSources: List<String>,
    onOpenLyricsSources: () -> Unit,
    onOpenTranslationLanguage: () -> Unit,
    onOpenEqualizer: () -> Unit,
    showNerdStats: Boolean,
    onShowNerdStatsChange: (Boolean) -> Unit,
    fullBleedArtwork: Boolean,
    onFullBleedArtworkChange: (Boolean) -> Unit,
    legacyMeshGradient: Boolean,
    onLegacyMeshGradientChange: (Boolean) -> Unit,
    trayIconEnabled: Boolean,
    closeToTray: Boolean,
    onCloseToTrayChange: (Boolean) -> Unit,
    onTrayIconChange: (Boolean) -> Unit,
    spatialAudio: Boolean,
    onSpatialAudioChange: (Boolean) -> Unit,
    skipSilence: Boolean,
    onSkipSilenceChange: (Boolean) -> Unit,
    outputPrecision: String,
    onOutputPrecisionChange: (String) -> Unit,
    outputSummary: String,
    downloadQuality: String,
    onDownloadQualityChange: (String) -> Unit,
    audioQuality: DesktopAudioQuality,
    onAudioQualityChange: (DesktopAudioQuality) -> Unit,
    sleepTimerMinutes: Int?,
    sleepAfterTrack: Boolean,
    sleepRemainingMs: Long?,
    onSleepTimerCycle: () -> Unit,
    sourceConfigs: List<DesktopSourceConfig>,
    sourceStatus: Map<String, String>,
    onSourceEnabledChange: (DesktopSourceConfig, Boolean) -> Unit,
    onSaveSource: (DesktopSourceConfig) -> Unit,
    onRemoveSource: (DesktopSourceConfig) -> Unit,
    onTestSource: (DesktopSourceConfig) -> Unit,
    onOpenIntegrations: () -> Unit,
    onDismiss: () -> Unit,
) {
    var editingSource by remember { mutableStateOf<DesktopSourceConfig?>(null) }
    var licensesOpen by remember { mutableStateOf(false) }
    val sourceProbeKey = sourceConfigs
        .filter { it.kind.needsServer && it.isComplete }
        .joinToString { "${it.id}@${it.baseUrl}" }
    LaunchedEffect(sourceProbeKey) {
        sourceConfigs
            .filter { it.kind.needsServer && it.isComplete }
            .forEach(onTestSource)
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(DesktopScrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // The same card the song menu and the lyrics dialog are drawn on, rather than a Material
        // surface with an elevation shadow — see [desktopCard].
        Box(
            Modifier
                .width(660.dp)
                .fillMaxHeight(0.84f)
                .desktopCard(RoundedCornerShape(18.dp))
                // Swallows the click so pressing inside the card does not dismiss it through the
                // scrim underneath.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    DesktopStrings["settings", "Settings"],
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 26.dp, top = 24.dp, bottom = 16.dp),
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
            item {
                    SettingsGroup(DesktopStrings["playback", "Playback"]) {
                        SettingsToggle(DesktopStrings["autoplay", "Autoplay"], DesktopStrings["d_keep_the_music_going_with_similar_songs", "Keep the music going with similar songs"], autoplay, onAutoplayChange)
                        SettingsToggle(DesktopStrings["shuffle", "Shuffle"], DesktopStrings["d_mix_the_order_of_the_current_queue", "Mix the order of the current queue"], shuffle, onShuffleChange)
                    SettingsRow(
                        Icons.Rounded.Repeat,
                        DesktopStrings["d_repeat", "Repeat"],
                        "${repeatMode.label()} · Tap to change",
                    ) { onRepeatModeChange(repeatMode.next()) }
                    SettingsRow(
                        Icons.Rounded.Tune,
                        DesktopStrings["d_playback_speed", "Playback speed"],
                        "${"%.2f".format(playbackSpeed)}×",
                    ) {
                        val next = when {
                            playbackSpeed < 0.76f -> 1.0f
                            playbackSpeed < 1.01f -> 1.25f
                            playbackSpeed < 1.26f -> 1.5f
                            playbackSpeed < 1.51f -> 2.0f
                            else -> 0.5f
                        }
                        onPlaybackSpeedChange(next)
                    }
                    if (!automix) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(DesktopStrings["crossfade", "Crossfade"], style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (crossfadeSeconds == 0) "Off" else "${crossfadeSeconds}s",
                                color = DesktopSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Slider(
                                value = crossfadeSeconds.toFloat(),
                                onValueChange = { onCrossfadeSecondsChange(it.toInt()) },
                                valueRange = 0f..12f,
                                steps = 11,
                                colors = androidx.compose.material3.SliderDefaults.colors(activeTrackColor = DesktopAccent, thumbColor = DesktopAccent),
                            )
                        }
                    }
                    SettingsToggle(
                        DesktopStrings["automix", "Automix [BETA]"],
                        if (automix) {
                            "Calculates timing and blends transitions from the tracks"
                        } else {
                            "Automatically calculates transition timing without a slider"
                        },
                        automix,
                        onAutomixChange,
                    )
                    if (automix) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(DesktopStrings["automix_performance", "Automix performance"], fontWeight = FontWeight.Medium)
                            Text(
                                DesktopStrings["automix_performance_subtitle", "Sets how much CPU background analysis may use"],
                                color = DesktopSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AutomixPerformanceMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = automixPerformance == mode,
                                        onClick = { onAutomixPerformanceChange(mode) },
                                        label = { Text(mode.label()) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                automixPerformance.detail(),
                                color = DesktopSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    SettingsRow(
                        Icons.Rounded.Bedtime,
                        "Sleep timer",
                        when {
                            sleepRemainingMs != null -> "${formatSleepTimer(sleepRemainingMs)} until playback pauses"
                            sleepAfterTrack -> "Pausing when this song ends"
                            else -> "Pause playback after a while"
                        },
                    ) { onSleepTimerCycle() }
                    // Windows only: on Linux the window manager owns the window's frame and there
                    // is nothing here to offer.
                    if (DesktopPlatform.drawsOwnWindowFrame) {
                        val titleBar by DesktopTitleBarSetting.enabled.collectAsState()
                        SettingsToggle(
                            DesktopStrings["d_title_bar", "Title bar"],
                            DesktopStrings[
                                "d_a_slim_bar_above_the_toolbar",
                                "A slim bar above the toolbar with the window's own buttons. " +
                                    "Off, the window has no title bar at all.",
                            ],
                            titleBar,
                            DesktopTitleBarSetting::set,
                        )
                    }
                    SettingsToggle(
                        DesktopStrings["full_screen_cover_art", "Full-screen cover art"],
                        DesktopStrings["full_screen_cover_art_subtitle", "Runs the cover to the edges of the player instead of a square sleeve"],
                        fullBleedArtwork,
                        onFullBleedArtworkChange,
                    )
                    SettingsToggle(DesktopStrings["d_animated_canvas", "Animated canvas"], DesktopStrings["d_show_motion_artwork_when_it_is_available", "Show motion artwork when it is available"], animatedCanvas, onAnimatedCanvasChange)
                    if (animatedCanvas) {
                        SettingsNavigationRow(
                            title = DesktopStrings["spotify_canvas_setup", "Spotify Canvas setup"],
                            subtitle = if (spotifyCanvasReady) {
                                "Connected"
                            } else {
                                "To use Spotify Canvas, provide your Spotify sp_dc cookie."
                            },
                            onClick = onOpenSpotifyCanvasSetup,
                        )
                    }
                    SettingsToggle(
                        DesktopStrings["show_nerd_stats", "Show stats for nerds"],
                        DesktopStrings["show_nerd_stats_subtitle", "Codec, bitrate and sample rate on the player"],
                        showNerdStats,
                        onShowNerdStatsChange,
                    )
                    SettingsToggle(
                        DesktopStrings["legacy_mesh_gradient", "Legacy mesh gradient"],
                        DesktopStrings["d_use_the_older_blob_backdrop_behind_the_player", "Use the older blob backdrop behind the player"],
                        legacyMeshGradient,
                        onLegacyMeshGradientChange,
                    )
                    val eqOn by DesktopEqualizerSettings.enabled.collectAsState()
                    SettingsNavigationRow(
                        title = DesktopStrings["equalizer", "Equalizer"],
                        subtitle = if (eqOn) {
                            DesktopStrings["equalizer_subtitle", "Tone, seven bands and balance"]
                        } else {
                            DesktopStrings["d_off", "Off"]
                        },
                        onClick = onOpenEqualizer,
                    )
                    SettingsToggle(
                        DesktopStrings["spatial_audio", "Spatial audio"],
                        DesktopStrings["spatial_audio_subtitle", "Widens stereo tracks for a more immersive feel"],
                        spatialAudio,
                        onSpatialAudioChange,
                    )
                    SettingsToggle(
                        DesktopStrings["skip_silence", "Skip silence"],
                        DesktopStrings["d_shorten_long_gaps_rather_than_playing_them_out", "Shorten long gaps rather than playing them out"],
                        skipSilence,
                        onSkipSilenceChange,
                    )
                    SettingsToggle(
                        DesktopStrings["d_tray_icon", "Tray icon"],
                        DesktopStrings["d_show_bitchord_in_the_system_tray_with_playback_controls", "Show BitChord in the system tray, with playback controls"],
                        trayIconEnabled,
                        onTrayIconChange,
                    )
                    // Only offered where it can be honoured.
                    if (trayIconEnabled) {
                        SettingsToggle(
                            DesktopStrings["d_keep_playing_when_closed", "Keep playing when closed"],
                            DesktopStrings["d_closing_the_window_leaves_bitchord_in_the_tray_instead_o", "Closing the window leaves BitChord in the tray instead of quitting"],
                            closeToTray,
                            onCloseToTrayChange,
                        )
                    }
                }
            }
            item {
                SettingsGroup(DesktopStrings["open_lyrics", "Lyrics"]) {
                    SettingsToggle(
                        DesktopStrings["synced_lyrics", "Synced lyrics"],
                        DesktopStrings["synced_lyrics_subtitle", "Lights up the words on the player as they're sung"],
                        syncedLyrics,
                        onSyncedLyricsChange,
                    )
                    // Nothing to choose between while the feature is off, and the sources are
                    // third-party services reached on the listener's connection.
                    if (syncedLyrics) {
                        SettingsToggle(
                            DesktopStrings["d_blur_unfocused_lyrics", "Blur unfocused lyrics"],
                            DesktopStrings["d_keeps_the_spotlight_on_the_current_line", "Keeps the spotlight on the current line"],
                            lyricsBlur,
                            onLyricsBlurChange,
                        )
                        SettingsNavigationRow(
                            title = DesktopStrings["lyrics_sources", "Lyrics sources"],
                            subtitle = enabledLyricsSources
                                .joinToString(", ")
                                .ifEmpty { "None. Lyrics will not be fetched." },
                            onClick = onOpenLyricsSources,
                        )
                        val translationLanguage by DesktopTranslationSetting.language.collectAsState()
                        SettingsNavigationRow(
                            title = DesktopStrings["translation_language", "Translation language"],
                            subtitle = DesktopTranslationSetting.describe(translationLanguage),
                            onClick = onOpenTranslationLanguage,
                        )
                    }
                }
            }
            item {
                SettingsGroup(DesktopStrings["language", "Language"]) {
                    var languageMenuOpen by remember { mutableStateOf(false) }
                    val chosen by DesktopStrings.language.collectAsState()
                    Box {
                        SettingsRow(
                            Icons.Rounded.Language,
                            DesktopStrings["app_language", "App language"],
                            DesktopStrings.languageLabel(),
                        ) { languageMenuOpen = true }
                        DropdownMenu(languageMenuOpen, onDismissRequest = { languageMenuOpen = false }) {
                            DesktopStrings.available.forEach { language ->
                                DropdownMenuItem(
                                    text = { Text(language.label) },
                                    trailingIcon = if (language.tag == chosen) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        DesktopStrings.setLanguage(language.tag)
                                        languageMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item {
                SettingsGroup(DesktopStrings["storage", "Storage"]) {
                    var limitMb by remember { mutableStateOf(DesktopMediaCache.limitMb()) }
                    var cleared by remember { mutableStateOf<String?>(null) }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(DesktopStrings["song_cache_limit", "Song cache limit"], style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (limitMb > DesktopMediaCache.WARNING_MB) {
                                "Keeps up to ${formatCacheSize(limitMb)} of downloaded audio on disk. " +
                                    "This can take a noticeable share of your free space."
                            } else {
                                "Keeps downloaded audio on disk for instant seeking and replays"
                            },
                            color = DesktopSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Slider(
                            value = limitMb.toFloat(),
                            onValueChange = { limitMb = it.roundToInt() },
                            onValueChangeFinished = { DesktopMediaCache.setLimitMb(limitMb) },
                            valueRange = DesktopMediaCache.MIN_LIMIT_MB.toFloat()..
                                DesktopMediaCache.MAX_LIMIT_MB.toFloat(),
                            steps = 18,
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                activeTrackColor = DesktopAccent,
                                thumbColor = DesktopAccent,
                            ),
                        )
                        Text(formatCacheSize(limitMb), color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    SettingsRow(
                        Icons.Rounded.DeleteSweep,
                        "Clear song cache",
                        cleared ?: "Frees space used by downloaded audio",
                    ) {
                        DesktopMediaCache.clear()
                        cleared = "Song cache cleared"
                    }
                    var imagesCleared by remember { mutableStateOf(false) }
                    SettingsRow(
                        Icons.Rounded.DeleteSweep,
                        "Clear image cache",
                        if (imagesCleared) "Image cache cleared" else "Frees space used by album artwork",
                    ) {
                        DesktopArtworkCache.clear()
                        imagesCleared = true
                    }
                }
            }
            item {
                SettingsGroup(DesktopStrings["local_music", "Local Music"]) {
                    var folderLabel by remember { mutableStateOf(DesktopLocalMusic.folderLabel()) }
                    SettingsRow(
                        Icons.Rounded.Folder,
                        "Local music folder",
                        folderLabel,
                    ) {
                        onChooseLocalMusicFolder { folderLabel = DesktopLocalMusic.folderLabel() }
                    }
                    if (DesktopLocalMusic.folder() != null) {
                        SettingsRow(
                            Icons.Rounded.LibraryMusic,
                            DesktopStrings["use_all_audio_folders", "Use all audio folders"],
                            DesktopStrings["use_all_audio_folders_subtitle", "Remove the folder limit and scan music across the device"],
                        ) {
                            DesktopLocalMusic.setFolder(null)
                            folderLabel = DesktopLocalMusic.folderLabel()
                            onLocalMusicFolderChanged()
                        }
                    }
                    SettingsToggle(
                        DesktopStrings["filter_non_music_audio", "Filter non-music audio"],
                        DesktopStrings[
                            "filter_non_music_audio_subtitle",
                            "Hides clips under 30 seconds, WAV files, voice notes, recordings and system sounds",
                        ],
                        filterNonMusicAudio,
                        onFilterNonMusicAudioChange,
                    )
                }
            }
            item {
                SettingsGroup(DesktopStrings["appearance", "Appearance"]) {
                    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
                    SettingsToggle(
                        DesktopStrings["reduce_dynamic_blur", "Reduce dynamic blur"],
                        DesktopStrings["reduce_dynamic_blur_subtitle", "Swaps frosted glass for solid fills across the app"],
                        reduceDynamicBlur,
                        DesktopAppearanceSettings::setReduceDynamicBlur,
                    )
                }
            }
            item {
                SettingsGroup(DesktopStrings["miscellaneous", "Miscellaneous"]) {
                    SettingsToggle(
                        DesktopStrings["d_dont_repeat_songs_in_current_session", "Don\u2019t repeat songs in current session"],
                        DesktopStrings["d_autoplay_wont_suggest_a_song_already_played_or_suggested", "AutoPlay won\u2019t suggest a song already played or suggested this session"],
                        dontRepeatSuggestions,
                        onDontRepeatSuggestionsChange,
                    )
                    val hideVolumeBar by DesktopAppearanceSettings.hideVolumeBar.collectAsState()
                    SettingsToggle(
                        DesktopStrings["hide_volume_bar", "Hide volume bar"],
                        DesktopStrings["hide_volume_bar_subtitle", "Removes the volume slider from the main player"],
                        hideVolumeBar,
                        DesktopAppearanceSettings::setHideVolumeBar,
                    )
                }
            }
            item {
                SettingsGroup(DesktopStrings["audio_quality", "Audio quality"]) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DesktopAudioQuality.entries.forEach { rung ->
                                FilterChip(
                                    selected = audioQuality == rung,
                                    onClick = { onAudioQualityChange(rung) },
                                    label = { Text(rung.label) },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // A rung is not a bitrate: it decides which kinds of source are allowed to
                        // answer, so say which.
                        Text(
                            audioQuality.detail,
                            color = DesktopSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item {
                SettingsGroup(DesktopStrings["d_output_precision", "Output precision"]) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("PCM_16" to "16-bit PCM", "FLOAT_32" to "32-bit float").forEach { (value, label) ->
                                FilterChip(
                                    selected = outputPrecision == value,
                                    onClick = { onOutputPrecisionChange(value) },
                                    label = { Text(label) },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // What was asked for and what the device agreed to are not the same
                        // question, and a setting that silently did not take is worse than one that
                        // says so.
                        Text(
                            "Playing at $outputSummary",
                            color = DesktopSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item {
                SettingsGroup(DesktopStrings["download_channel_name", "Downloads"]) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("STANDARD" to "Standard", "HIGH" to "High", "LOSSLESS" to "Lossless").forEach { (value, label) ->
                            FilterChip(
                                selected = downloadQuality == value,
                                onClick = { onDownloadQualityChange(value) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
            item {
                SettingsGroup(DesktopStrings["sources_order_header", "Sources · tried in this order"]) {
                    sourceConfigs.inSourceOrder().forEachIndexed { index, config ->
                        if (index > 0) HorizontalDivider(color = DesktopDivider)
                        DesktopSourceSettingsRow(
                            position = index + 1,
                            config = config,
                            status = sourceStatus[config.id],
                            skippedByQuality = config.enabled && !audioQuality.permits(config.kind),
                            onEdit = if (config.kind.needsServer) {
                                { editingSource = config }
                            } else {
                                null
                            },
                            onToggle = if (config.kind == DesktopSourceKind.YOUTUBE) {
                                null
                            } else {
                                { onSourceEnabledChange(config, it) }
                            },
                        )
                    }
                    if (sourceConfigs.isNotEmpty()) HorizontalDivider(color = DesktopDivider)
                    // One entry point, and it creates an addon.
                    SettingsRow(
                        Icons.Rounded.Add,
                        "Add a source",
                        DesktopSourceKind.ADDON.detail,
                    ) {
                        editingSource = DesktopSourceConfig(
                            id = UUID.randomUUID().toString(),
                            kind = DesktopSourceKind.ADDON,
                        )
                    }
                }
            }
            item {
                // One row rather than the four groups this used to be. Every
                // integration option Android has lives behind it; inline, the
                // list was longer than the rest of Settings put together.
                SettingsGroup(DesktopStrings["d_account", "Account"]) {
                    SettingsRow(
                        Icons.Rounded.Share,
                        DesktopStrings["account_integrations", "Account & integrations"],
                        DesktopStrings["d_discord_rich_presence_last_fm_and_listenbrainz", "Discord rich presence, Last.fm and ListenBrainz"],
                        onOpenIntegrations,
                    )
                }
            }
            item {
                DesktopSettingsFooter(onLicenses = { licensesOpen = true })
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesktopAccent,
                    contentColor = Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 26.dp, vertical = 10.dp),
            ) {
                Text(DesktopStrings["done", "Done"], fontWeight = FontWeight.SemiBold)
            }
        }
            }
        }
    }

    editingSource?.let { config ->
        DesktopSourceEditorDialog(
            config = config,
            isNew = sourceConfigs.none { it.id == config.id },
            status = sourceStatus[config.id],
            onDismiss = { editingSource = null },
            onSave = {
                onSaveSource(it)
                editingSource = null
            },
            onRemove = {
                onRemoveSource(config)
                editingSource = null
            },
            onTest = onTestSource,
        )
    }

    if (licensesOpen) {
        DesktopLicensesDialog(onDismiss = { licensesOpen = false })
    }
}

/** The line at the foot of the settings sheet, as Android has it. */
@Composable
private fun DesktopSettingsFooter(onLicenses: () -> Unit) {
    val version = remember { System.getProperty("bitchord.version") ?: "1.5.2" }
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = DesktopAccent, textDecoration = TextDecoration.Underline),
    )
    Text(
        text = buildAnnotatedString {
            append("bitchord $version  ")
            withLink(LinkAnnotation.Url("https://github.com/kushagrasinghx/BitChord", linkStyles)) {
                append("GitHub")
            }
            append("  ")
            withLink(LinkAnnotation.Url("https://github.com/kushagrasinghx", linkStyles)) {
                append("Developer")
            }
            append("  ")
            withLink(LinkAnnotation.Url("https://discord.gg/pDdKfrdHY6", linkStyles)) {
                append("Discord")
            }
            append("  ")
            withLink(LinkAnnotation.Clickable("licenses", linkStyles) { onLicenses() }) {
                append("Licenses")
            }
            append("\n~YouTube Music Backend")
        },
        style = MaterialTheme.typography.labelSmall,
        color = DesktopSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
    )
}

/** Everything this build is assembled from, and the terms each part came under. */
@Composable
private fun DesktopLicensesDialog(onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 620.dp)
                .fillMaxHeight(0.82f)
                .clip(RoundedCornerShape(20.dp))
                .background(DesktopGlassStrong)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Text(
                DesktopStrings["d_third_party_licenses", "Third-party licenses"],
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 22.dp, top = 22.dp, end = 22.dp, bottom = 4.dp),
            )
            Text(
                DesktopStrings["d_bitchord_is_free_software_and_so_is_everything_it_is_bui", "BitChord is free software, and so is everything it is built on."],
                color = DesktopSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 22.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                DesktopLicenses.groups.forEach { group ->
                    item(key = group.title) {
                        SettingsGroup(group.title) {
                            group.entries.forEach { entry ->
                                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(entry.name, fontWeight = FontWeight.Medium)
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            entry.license,
                                            color = DesktopAccent,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                    if (entry.note.isNotBlank()) {
                                        Text(
                                            entry.note,
                                            color = DesktopSecondary,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    Text(
                                        entry.url,
                                        color = DesktopSecondary.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = DesktopAccent),
                ) { Text(DesktopStrings["done", "Done"]) }
            }
        }
    }
}

@Composable
private fun DesktopSourceSettingsRow(
    position: Int,
    config: DesktopSourceConfig,
    status: String?,
    skippedByQuality: Boolean = false,
    onEdit: (() -> Unit)?,
    onToggle: ((Boolean) -> Unit)?,
) {
    val enabledAlpha = if (config.enabled) 1f else 0.45f
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = onEdit != null) { onEdit?.invoke() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$position",
            color = DesktopSecondary,
            modifier = Modifier.width(20.dp).alpha(enabledAlpha),
        )
        Icon(
            when (config.kind) {
                DesktopSourceKind.ADDON -> Icons.Rounded.Extension
                DesktopSourceKind.CUSTOM_MODULE, DesktopSourceKind.MODULE -> Icons.Rounded.Extension
                DesktopSourceKind.JIOSAAVN -> Icons.Rounded.GraphicEq
                DesktopSourceKind.YOUTUBE -> Icons.Rounded.PlayCircle
            },
            contentDescription = null,
            tint = DesktopAccent,
            modifier = Modifier.size(21.dp).alpha(enabledAlpha),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).alpha(enabledAlpha)) {
            Text(config.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    // The ceiling in force, not the switch, is why this one is being passed over —
                    // worth saying, or the row reads as enabled and silently unused.
                    skippedByQuality -> "Skipped at this audio quality"
                    status != null -> status
                    !config.isComplete -> "Setup required"
                    config.kind.needsServer -> "Checking…"
                    else -> config.kind.detail
                },
                color = if (skippedByQuality) DesktopAccent else DesktopSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (onToggle == null) {
            Text(DesktopStrings["always_on", "Always on"], color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            Switch(checked = config.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun DesktopSourceEditorDialog(
    config: DesktopSourceConfig,
    isNew: Boolean,
    status: String?,
    onDismiss: () -> Unit,
    onSave: (DesktopSourceConfig) -> Unit,
    onRemove: () -> Unit,
    onTest: (DesktopSourceConfig) -> Unit,
) {
    var label by remember(config.id) { mutableStateOf(config.label) }
    var baseUrl by remember(config.id) { mutableStateOf(config.baseUrl) }
    var checking by remember(config.id) { mutableStateOf(false) }
    var message by remember(config.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val candidate = config.copy(label = label.trim(), baseUrl = baseUrl.trim())

    /** Works out what is actually on the end of the URL before storing it. */
    fun identify(thenSave: Boolean) {
        if (candidate.baseUrl.isBlank() || checking) return
        checking = true
        message = null
        scope.launch {
            val outcome = DesktopSourceFormats.identify(candidate.baseUrl)
            checking = false
            outcome.fold(
                onSuccess = { detected ->
                    when (detected) {
                        is DesktopDetectedFormat.Addon -> {
                            val named = candidate.copy(
                                kind = DesktopSourceKind.ADDON,
                                baseUrl = detected.baseUrl,
                                label = candidate.label.ifBlank { detected.manifest.displayName },
                            )
                            message = "Addon · ${detected.manifest.displayName}"
                            if (thenSave) onSave(named)
                        }
                        is DesktopDetectedFormat.ModuleIndex -> {
                            val asIndex = candidate.copy(
                                kind = DesktopSourceKind.CUSTOM_MODULE,
                                baseUrl = detected.url.ifBlank { candidate.baseUrl },
                            )
                            message = "Module index · ${detected.moduleCount} modules"
                            if (thenSave) onSave(asIndex)
                        }
                        is DesktopDetectedFormat.Unsupported -> message = detected.reason
                    }
                },
                onFailure = { message = it.message ?: "Nothing answered at that address" },
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add a source" else config.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                        message = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(DesktopStrings["d_addon_url", "Addon URL"]) },
                    placeholder = { Text("https://example.com/addon") },
                    singleLine = true,
                )
                Text(
                    DesktopStrings[
                        "d_an_addon_server",
                        "An addon server — BitChord asks it over plain HTTP and runs no code from it. " +
                            "A compatible module index also works and is stored as one.",
                    ],
                    color = DesktopSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(DesktopStrings["d_name_optional", "Name (optional)"]) },
                    placeholder = { Text(DesktopStrings["d_my_music_source", "My music source"]) },
                    singleLine = true,
                )
                (message ?: status)?.let {
                    Text(it, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onRemove) { Text(DesktopStrings["remove", "Remove"]) }
                TextButton(onClick = onDismiss) { Text(DesktopStrings["cancel", "Cancel"]) }
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = {
                        onTest(candidate)
                        identify(thenSave = false)
                    },
                    enabled = candidate.baseUrl.isNotBlank() && !checking,
                ) {
                    Text(if (checking) "Checking…" else "Test")
                }
                TextButton(
                    onClick = { identify(thenSave = true) },
                    enabled = candidate.baseUrl.isNotBlank() && !checking,
                ) {
                    Text(DesktopStrings["save", "Save"])
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesktopCollectionPage(
    collection: DesktopCollection,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    /** The three edits only a playlist's owner is offered. */
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onRemoveFromPlaylist: ((Song) -> Unit)?,
    likedIds: Set<String>,
    onBack: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onDownloadAll: (List<Song>) -> Unit,
    animatedCanvas: Boolean,
    onToggleLike: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    downloadedIds: Set<String>,
    downloadInProgress: Set<String>,
    contentPadding: PaddingValues,
) {
    val songs = collection.songs
    val subtitleParts = collection.subtitle
        .split(" • ", " · ", " | ")
        .map(String::trim)
        .filter(String::isNotBlank)
    val credit = subtitleParts.firstOrNull {
        it.lowercase() !in setOf("album", "single", "ep", "playlist", "artist") &&
            !it.matches(Regex("\\d{4}")) &&
            !it.matches(Regex("\\d{1,2}:\\d{2}(?::\\d{2})?")) &&
            !it.matches(Regex("[0-9,.]+\\s*(?:songs?|tracks?)", RegexOption.IGNORE_CASE))
    }.orEmpty()
    val typeLabel = when (collection.type) {
        BrowseType.ALBUM -> "Album"
        BrowseType.PLAYLIST -> "Playlist"
        BrowseType.ARTIST -> "Artist"
        BrowseType.OTHER -> "Collection"
    }
    val metadata = buildList {
        add(typeLabel.uppercase())
        subtitleParts.firstOrNull { it.matches(Regex("\\d{4}")) }?.let(::add)
        if (songs.isNotEmpty()) add("${songs.size} songs")
        val seconds = songs.sumOf { it.durationText.durationMillis() / 1_000L }
        if (seconds > 0) add(formatCollectionDuration(seconds))
    }.joinToString(" • ").uppercase()
    DesktopPageScaffold(contentPadding) {
        LazyColumn(
            contentPadding = pagePadding(start = DesktopPageGutter, end = DesktopPageGutter, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, DesktopStrings["back", "Back"]) }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The services hang motion artwork off the release, so a page for one asks for
                    // it directly rather than picking a track and hoping it sits on the right
                    // edition.
                    val albumCanvas by produceState<DesktopCanvasArtwork?>(null, collection.title, animatedCanvas) {
                        value = if (!animatedCanvas) {
                            null
                        } else {
                            withContext(Dispatchers.IO) {
                                DesktopCanvasClient.lookupAlbum(
                                    collection.title,
                                    songs.firstOrNull()?.artist.orEmpty(),
                                )
                            }
                        }
                    }
                    Box(Modifier.size(286.dp).clip(RoundedCornerShape(8.dp))) {
                        DesktopArtwork(
                            collection.thumbnailUrl ?: songs.firstOrNull()?.thumbnailUrl,
                            Modifier.fillMaxSize(),
                        )
                        albumCanvas?.let {
                            DesktopCanvasView(it.url, Modifier.fillMaxSize(), fallbackUrl = it.fallbackUrl)
                        }
                    }
                    Spacer(Modifier.width(30.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            typeLabel.uppercase(),
                            color = DesktopSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            collection.title,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (credit.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                credit,
                                color = DesktopAccent,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (metadata.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(metadata, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(24.dp))
                        // Wraps rather than squeezes: an owned playlist has two buttons more than
                        // an album, and in a narrow window the last of them was crushed to a column
                        // of letters.
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            DesktopActionButton(DesktopStrings["play", "Play"], Icons.Rounded.PlayArrow) {
                                if (songs.isNotEmpty()) onPlaySongs(songs, 0)
                            }
                            DesktopActionButton(DesktopStrings["shuffle", "Shuffle"], Icons.Rounded.Shuffle) { onShuffle(songs) }
                            DesktopActionButton(
                                DesktopStrings["download_all", "Download all"],
                                Icons.Rounded.Download,
                            ) { onDownloadAll(songs) }
                            onRename?.let { DesktopActionButton(DesktopStrings["rename", "Rename"], Icons.Rounded.Edit, onClick = it) }
                            onDelete?.let { DesktopActionButton(DesktopStrings["delete", "Delete"], Icons.Rounded.Delete, onClick = it) }
                        }
                    }
                }
            }
            item {
                DesktopCollectionTableHeader(hasRemove = onRemoveFromPlaylist != null)
            }
            itemsIndexed(songs, key = { index, song -> "${song.videoId}-$index" }) { index, song ->
                DesktopCollectionSongRow(
                    song = song,
                    collectionType = collection.type,
                    collectionTitle = collection.title,
                    liked = song.videoId in likedIds,
                    onClick = { _ -> onPlaySongs(songs, index) },
                    onToggleLike = onToggleLike,
                    onDownload = onDownload,
                    onAddToPlaylist = onAddToPlaylist,
                    downloaded = song.videoId in downloadedIds,
                    downloadInProgress = song.videoId in downloadInProgress,
                    number = index + 1,
                    onRemove = onRemoveFromPlaylist?.takeIf { song.setVideoId != null },
                )
            }
            if (collection.continuation != null) {
                // Reaching this row is what asks for the next page, the way the home feed pages.
                item(key = "collection-more") {
                    LaunchedEffect(songs.size) { onLoadMore() }
                    Box(
                        Modifier.fillMaxWidth().height(72.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (loadingMore) {
                            CircularProgressIndicator(
                                color = DesktopAccent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The width of a collection row's action cluster, and of the header spacer that has to line up with
 * it.
 */
private fun collectionActionsWidth(hasRemove: Boolean): Dp =
    if (hasRemove) 170.dp else 136.dp

@Composable
private fun DesktopCollectionTableHeader(hasRemove: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(32.dp))
        Spacer(Modifier.width(44.dp))
        Spacer(Modifier.width(12.dp))
        Text(DesktopStrings["d_song", "SONG"], Modifier.weight(0.42f), color = DesktopSecondary, style = MaterialTheme.typography.labelSmall)
        Text(DesktopStrings["widget_preview_artist", "ARTIST"].uppercase(), Modifier.weight(0.20f), color = DesktopSecondary, style = MaterialTheme.typography.labelSmall)
        Text(DesktopStrings["album", "ALBUM"].uppercase(), Modifier.weight(0.25f), color = DesktopSecondary, style = MaterialTheme.typography.labelSmall)
        Text(DesktopStrings["d_time", "TIME"], Modifier.width(58.dp), color = DesktopSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
        Spacer(Modifier.width(collectionActionsWidth(hasRemove)))
    }
    HorizontalDivider(color = DesktopDivider)
}

@Composable
private fun DesktopCollectionSongRow(
    song: Song,
    collectionType: BrowseType,
    collectionTitle: String,
    liked: Boolean,
    onClick: (Song) -> Unit,
    onToggleLike: ((Song) -> Unit)?,
    onDownload: ((Song) -> Unit)?,
    onAddToPlaylist: ((Song) -> Unit)?,
    downloaded: Boolean,
    downloadInProgress: Boolean,
    number: Int,
    /** Takes this row out of the playlist being read. */
    onRemove: ((Song) -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick(song) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val nowPlaying = song.isNowPlaying()
        Text(number.toString(), Modifier.width(32.dp), color = DesktopSecondary, textAlign = TextAlign.Center)
        // No sleeve on an album's rows.
        if (collectionType != BrowseType.ALBUM) {
            DesktopArtwork(song.thumbnailUrl, Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)), px = ROW_ART_PX)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            song.title,
            Modifier.weight(0.42f),
            color = if (nowPlaying) DesktopAccent else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
        )
        Text(
            song.artist,
            Modifier.weight(0.20f).padding(horizontal = 8.dp),
            color = DesktopSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            song.albumName ?: if (collectionType == BrowseType.ALBUM) collectionTitle else "—",
            Modifier.weight(0.25f).padding(horizontal = 8.dp),
            color = DesktopSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            song.durationText ?: "—",
            Modifier.width(58.dp),
            color = DesktopSecondary,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            Modifier.width(collectionActionsWidth(onRemove != null)),
            horizontalArrangement = Arrangement.End,
        ) {
            onRemove?.let { remove ->
                IconButton(onClick = { remove(song) }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Rounded.RemoveCircleOutline,
                        DesktopStrings["d_remove_from_playlist", "Remove from playlist"],
                        tint = DesktopSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            onToggleLike?.let { onLike ->
                IconButton(onClick = { onLike(song) }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        "Favorite",
                        tint = if (liked) DesktopAccent else DesktopSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            onDownload?.let { onSave ->
                IconButton(onClick = { if (!downloaded) onSave(song) }, modifier = Modifier.size(34.dp)) {
                    if (downloadInProgress) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = DesktopAccent, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (downloaded) Icons.Rounded.Download else Icons.Rounded.CloudDownload,
                            if (downloaded) "Downloaded" else "Download",
                            tint = if (downloaded) DesktopAccent else DesktopSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            onAddToPlaylist?.let { onAdd ->
                IconButton(onClick = { onAdd(song) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.Add, DesktopStrings["add_to_playlist", "Add to playlist"], tint = DesktopSecondary, modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = { onClick(song) }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.PlayArrow, DesktopStrings["play", "Play"], tint = DesktopAccent, modifier = Modifier.size(19.dp))
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 88.dp), color = DesktopDivider)
}

@Composable
private fun DesktopNowPlayingPage(
    song: Song,
    onOpenArtist: (Song) -> Unit,
    actions: DesktopSongActions,
    /** The Lyrics settings, which decide whether to look and how to choose. */
    syncedLyrics: Boolean,
    lyricsBlur: Boolean,
    prioritizeSyllables: Boolean,
    lyricsSources: List<String>,
    panel: DesktopPlayerPanel,
    onPanelChange: (DesktopPlayerPanel) -> Unit,
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    error: String?,
    streamFormat: DesktopStreamFormat?,
    isResolving: Boolean,
    searchingBetter: Boolean,
    automix: Boolean,
    smartAnalysis: SmartAnalysis,
    transitionWindow: TransitionWindow?,
    mixing: Boolean,
    substituted: Boolean,
    pinnedToOriginal: Boolean,
    liked: Boolean,
    shuffle: Boolean,
    autoplay: Boolean,
    repeatMode: DesktopRepeatMode,
    playbackSpeed: Float,
    animatedCanvas: Boolean,
    showNerdStats: Boolean,
    fullBleedArtwork: Boolean,
    legacyMeshGradient: Boolean,
    volume: Float,
    upcoming: List<Song>,
    autoplayStart: Int,
    crossfadeSeconds: Int,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onRevertToOriginal: () -> Unit,
    onUpgradeQuality: () -> Unit,
    onToggleLike: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onAutoplayChange: (Boolean) -> Unit,
    onRepeatModeChange: (DesktopRepeatMode) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onQueueSongClick: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onCrossfadeSecondsChange: (Int) -> Unit,
    /** Looked up as soon as the track starts, not when this page opens — see [DesktopApp]. */
    lyrics: DesktopLyrics?,
    lyricsLoading: Boolean,
    lyricsError: String?,
    canvas: DesktopCanvasArtwork?,
    docked: Boolean = false,
) {
    var lyricsVisible by remember(song.videoId) { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize().background(DesktopBackground)) {
        if (legacyMeshGradient) {
            DesktopBackdrop(rememberDesktopArtworkPalette(song.thumbnailUrl))
        } else {
            DesktopMeshBackdrop(
                song.thumbnailUrl,
                // A frame only exists while the clip is decoding, so its presence is the signal.
                source = DesktopCanvasBackdrop.frameFor(canvas?.url),
            )
        }
        if (maxWidth >= 760.dp && !docked) {
            DesktopWideNowPlayingLayout(
                song = song,
                onOpenArtist = onOpenArtist,
                actions = actions,
                syncedLyrics = syncedLyrics,
                lyricsBlur = lyricsBlur,
                panel = panel,
                onPanelChange = onPanelChange,
                isPlaying = isPlaying,
                progressMs = progressMs,
                durationMs = durationMs,
                error = error,
                streamFormat = streamFormat,
                isResolving = isResolving,
                searchingBetter = searchingBetter,
                automix = automix,
                smartAnalysis = smartAnalysis,
                transitionWindow = transitionWindow,
                mixing = mixing,
                substituted = substituted,
                pinnedToOriginal = pinnedToOriginal,
                liked = liked,
                shuffle = shuffle,
                autoplay = autoplay,
                repeatMode = repeatMode,
                playbackSpeed = playbackSpeed,
                animatedCanvas = animatedCanvas,
                showNerdStats = showNerdStats,
                fullBleedArtwork = fullBleedArtwork,
                legacyMeshGradient = legacyMeshGradient,
                volume = volume,
                upcoming = upcoming,
                autoplayStart = autoplayStart,
                crossfadeSeconds = crossfadeSeconds,
                canvas = canvas,
                lyrics = lyrics,
                lyricsLoading = lyricsLoading,
                lyricsError = lyricsError,
                onClose = onClose,
                onPlayPause = onPlayPause,
                onRevertToOriginal = onRevertToOriginal,
                onUpgradeQuality = onUpgradeQuality,
                onToggleLike = onToggleLike,
                onSeek = onSeek,
                onPrevious = onPrevious,
                onNext = onNext,
                onShuffleChange = onShuffleChange,
                onAutoplayChange = onAutoplayChange,
                onRepeatModeChange = onRepeatModeChange,
                onPlaybackSpeedChange = onPlaybackSpeedChange,
                onVolumeChange = onVolumeChange,
                onQueueSongClick = onQueueSongClick,
                onClearQueue = onClearQueue,
                onCrossfadeSecondsChange = onCrossfadeSecondsChange,
            )
        } else Column(
            Modifier
                .fillMaxSize()
                .padding(if (docked) 16.dp else 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!docked) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, DesktopStrings["d_close_player", "Close player"]) }
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(22.dp))
            if (lyricsVisible) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 620.dp)
                        .heightIn(max = 330.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    when {
                        lyricsLoading -> CircularProgressIndicator(color = DesktopAccent)
                        lyricsError != null -> Text(DesktopStrings["d_lyrics_unavailable", "Lyrics unavailable"], color = DesktopSecondary)
                        lyrics == null -> Text(DesktopStrings["d_lyrics_unavailable", "Lyrics unavailable"], color = DesktopSecondary)
                        else -> {
                            val lyricLines = lyrics!!.lines
                            val activeLine = lyricLines.indexOfLast { it.timeMs <= progressMs }
                            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                            LaunchedEffect(activeLine) {
                                if (activeLine >= 0) listState.animateScrollToItem(activeLine)
                            }
                            LazyColumn(
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(9.dp),
                                contentPadding = PaddingValues(vertical = 16.dp),
                            ) {
                                itemsIndexed(lyricLines) { index, line ->
                                    val active = index == activeLine
                                    val lineModifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSeek(line.timeMs) }
                                        .padding(vertical = 6.dp)
                                    if (line.text.isBlank()) {
                                        // A bare lyric timestamp is the instrumental/music-verse
                                        // marker in Android's lyrics panel.
                                        Icon(
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = DesktopStrings["instrumental", "Instrumental"],
                                            tint = Color.White.copy(alpha = if (active) 1f else 0.62f),
                                            modifier = lineModifier.size(32.dp),
                                        )
                                    } else {
                                        val revealed = line.revealedChars(progressMs).toInt().coerceIn(0, line.text.length)
                                        Text(
                                            buildAnnotatedString {
                                                withStyle(SpanStyle(color = if (active) Color.White else Color.White.copy(alpha = 0.62f))) {
                                                    append(line.text.take(revealed))
                                                }
                                                withStyle(SpanStyle(color = Color.White.copy(alpha = if (active) 0.45f else 0.62f))) {
                                                    append(line.text.drop(revealed))
                                                }
                                            },
                                            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 27.sp, lineHeight = 33.sp),
                                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                            modifier = lineModifier,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp)
                    .padding(horizontal = if (docked) 16.dp else 36.dp, vertical = if (docked) 18.dp else 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                    Box(
                        if (fullBleedArtwork) {
                            Modifier.fillMaxWidth().aspectRatio(1f)
                        } else {
                            Modifier.size(if (docked) 230.dp else 360.dp).clip(RoundedCornerShape(18.dp))
                        },
                    ) {
                        Box(Modifier.fillMaxSize().heroFade(fullBleedArtwork)) {
                            // The still cover is always underneath.
                            DesktopArtwork(song.thumbnailUrl, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            if (canvas != null) {
                                DesktopCanvasView(canvas!!.url, Modifier.fillMaxSize(), isPlaying = isPlaying, fallbackUrl = canvas!!.fallbackUrl)
                            }
                        }
                        if (showNerdStats) {
                            DesktopSleeveStats(
                                format = streamFormat,
                                automix = automix,
                                smartAnalysis = smartAnalysis,
                                isVideo = song.isVideo,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        song.title,
                        style = MaterialTheme.typography.headlineSmall.copy(lineHeightStyle = TIGHT_LINE_HEIGHT),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        song.artist,
                        color = DesktopSecondary,
                        style = MaterialTheme.typography.titleMedium.copy(lineHeightStyle = TIGHT_LINE_HEIGHT),
                        modifier = Modifier.opensArtist(song.artistId) { onOpenArtist(song) },
                    )
                    if (error != null) {
                        Text(error, color = DesktopAccent, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(18.dp))
                    if (!lyricsVisible && syncedLyrics) {
                        DesktopCurrentLyricLine(
                            lyrics = lyrics,
                            loading = lyricsLoading,
                            unavailable = lyricsError != null,
                            trackKey = song.videoId,
                            progressMs = progressMs,
                            durationMs = durationMs,
                            isPlaying = isPlaying,
                            onClick = { if (lyrics?.lines?.isNotEmpty() == true) lyricsVisible = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Slider(
                        value = if (durationMs > 0) progressMs.toFloat() / durationMs else 0f,
                        onValueChange = { onSeek((it * durationMs).toLong()) },
                        colors = androidx.compose.material3.SliderDefaults.colors(activeTrackColor = DesktopAccent, thumbColor = DesktopAccent),
                    )
                    Box(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatTime(progressMs), color = DesktopSecondary, style = MaterialTheme.typography.labelSmall)
                            Text(if (durationMs > 0) formatTime(durationMs) else song.durationText ?: "—", color = DesktopSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                        DesktopQualityBadge(
                            format = streamFormat,
                            isResolving = isResolving,
                            searchingBetter = searchingBetter,
                            modifier = Modifier.align(Alignment.Center).padding(horizontal = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (docked) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onShuffleChange(!shuffle) }) {
                                Icon(Icons.Rounded.Shuffle, DesktopStrings["shuffle", "Shuffle"], tint = if (shuffle) DesktopAccent else Color.White)
                            }
                            IconButton(onClick = onPrevious) { Icon(Icons.Rounded.FastRewind, DesktopStrings["widget_previous", "Previous"]) }
                            IconButton(onClick = onPlayPause, modifier = Modifier.size(58.dp).clip(CircleShape).background(Color.White)) {
                                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play", tint = Color.Black, modifier = Modifier.size(30.dp))
                            }
                            IconButton(onClick = onNext) { Icon(Icons.Rounded.FastForward, DesktopStrings["widget_next", "Next"]) }
                            IconButton(onClick = { onRepeatModeChange(repeatMode.next()) }) {
                                Icon(Icons.Rounded.Repeat, "Repeat ${repeatMode.label()}", tint = if (repeatMode != DesktopRepeatMode.OFF) DesktopAccent else Color.White)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onToggleLike) { Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorite", tint = if (liked) DesktopAccent else Color.White) }
                            DesktopSongMenuAnchor(
                                song = song,
                                liked = liked,
                                actions = actions,
                                onToggleLike = onToggleLike,
                                onRevertToOriginal = onRevertToOriginal.takeIf { substituted && !pinnedToOriginal },
                                onUpgradeQuality = onUpgradeQuality.takeIf { pinnedToOriginal },
                            )
                            IconButton(onClick = { onAutoplayChange(!autoplay) }) {
                                Icon(Icons.Rounded.AllInclusive, DesktopStrings["autoplay", "Autoplay"], tint = if (autoplay) DesktopAccent else Color.White)
                            }
                            TextButton(onClick = {
                                val next = when {
                                    playbackSpeed < 0.76f -> 1.0f
                                    playbackSpeed < 1.01f -> 1.25f
                                    playbackSpeed < 1.26f -> 1.5f
                                    playbackSpeed < 1.51f -> 2.0f
                                    else -> 0.5f
                                }
                                onPlaybackSpeedChange(next)
                            }) { Text("${"%.2f".format(playbackSpeed)}×", color = DesktopSecondary) }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                            IconButton(onClick = { onShuffleChange(!shuffle) }) {
                                Icon(Icons.Rounded.Shuffle, DesktopStrings["shuffle", "Shuffle"], tint = if (shuffle) DesktopAccent else Color.White)
                            }
                            IconButton(onClick = onToggleLike) { Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorite", tint = if (liked) DesktopAccent else Color.White) }
                            DesktopSongMenuAnchor(
                                song = song,
                                liked = liked,
                                actions = actions,
                                onToggleLike = onToggleLike,
                                onRevertToOriginal = onRevertToOriginal.takeIf { substituted && !pinnedToOriginal },
                                onUpgradeQuality = onUpgradeQuality.takeIf { pinnedToOriginal },
                            )
                            IconButton(onClick = onPrevious) { Icon(Icons.Rounded.FastRewind, DesktopStrings["widget_previous", "Previous"], modifier = Modifier.size(32.dp)) }
                            IconButton(onClick = onPlayPause, modifier = Modifier.size(68.dp).clip(CircleShape).background(Color.White)) {
                                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play", tint = Color.Black, modifier = Modifier.size(34.dp))
                            }
                            IconButton(onClick = onNext) { Icon(Icons.Rounded.FastForward, DesktopStrings["widget_next", "Next"], modifier = Modifier.size(32.dp)) }
                            IconButton(onClick = { onRepeatModeChange(repeatMode.next()) }) {
                                Icon(
                                    Icons.Rounded.Repeat,
                                    "Repeat ${repeatMode.label()}",
                                    tint = if (repeatMode != DesktopRepeatMode.OFF) DesktopAccent else Color.White,
                                )
                            }
                            IconButton(onClick = { onAutoplayChange(!autoplay) }) {
                                Icon(Icons.Rounded.AllInclusive, DesktopStrings["autoplay", "Autoplay"], tint = if (autoplay) DesktopAccent else Color.White)
                            }
                            TextButton(onClick = {
                                val next = when {
                                    playbackSpeed < 0.76f -> 1.0f
                                    playbackSpeed < 1.01f -> 1.25f
                                    playbackSpeed < 1.26f -> 1.5f
                                    playbackSpeed < 1.51f -> 2.0f
                                    else -> 0.5f
                                }
                                onPlaybackSpeedChange(next)
                            }) { Text("${"%.2f".format(playbackSpeed)}×", color = DesktopSecondary) }
                        }
                    }
            }
        }
    }
}

/**
 * The full-window player, laid out the way Music/iTunes lays its own out on a desktop: sleeve and
 * transport down the left, one side panel.
 */
@Composable
private fun DesktopWideNowPlayingLayout(
    song: Song,
    onOpenArtist: (Song) -> Unit,
    actions: DesktopSongActions,
    syncedLyrics: Boolean,
    lyricsBlur: Boolean,
    panel: DesktopPlayerPanel,
    onPanelChange: (DesktopPlayerPanel) -> Unit,
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    error: String?,
    streamFormat: DesktopStreamFormat?,
    isResolving: Boolean,
    searchingBetter: Boolean,
    automix: Boolean,
    smartAnalysis: SmartAnalysis,
    transitionWindow: TransitionWindow?,
    mixing: Boolean,
    substituted: Boolean,
    pinnedToOriginal: Boolean,
    liked: Boolean,
    shuffle: Boolean,
    autoplay: Boolean,
    repeatMode: DesktopRepeatMode,
    playbackSpeed: Float,
    animatedCanvas: Boolean,
    showNerdStats: Boolean,
    fullBleedArtwork: Boolean,
    legacyMeshGradient: Boolean,
    volume: Float,
    upcoming: List<Song>,
    autoplayStart: Int,
    crossfadeSeconds: Int,
    canvas: DesktopCanvasArtwork?,
    lyrics: DesktopLyrics?,
    lyricsLoading: Boolean,
    lyricsError: String?,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onRevertToOriginal: () -> Unit,
    onUpgradeQuality: () -> Unit,
    onToggleLike: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onAutoplayChange: (Boolean) -> Unit,
    onRepeatModeChange: (DesktopRepeatMode) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onQueueSongClick: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onCrossfadeSecondsChange: (Int) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // With full-screen cover art on, the stage is handed the whole left of the window and
        // applies the inset to its controls itself.
        val stagePadding = if (fullBleedArtwork) PaddingValues(horizontal = 56.dp) else PaddingValues(0.dp)
        Row(
            Modifier.fillMaxSize().padding(
                start = if (fullBleedArtwork) 0.dp else 56.dp,
                // The cover reaches the far edge too when nothing is standing beside it.
                end = if (fullBleedArtwork && panel == DesktopPlayerPanel.NONE) 0.dp else 40.dp,
                top = if (fullBleedArtwork) 0.dp else 92.dp,
                bottom = 84.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            DesktopPlayerStage(
                song = song,
                onOpenArtist = onOpenArtist,
                actions = actions,
                lyrics = lyrics,
                lyricsLoading = lyricsLoading,
                lyricsUnavailable = lyricsError != null,
                syncedLyrics = syncedLyrics,
                lyricsPanelOpen = panel == DesktopPlayerPanel.LYRICS,
                onOpenLyrics = { onPanelChange(DesktopPlayerPanel.LYRICS) },
                isPlaying = isPlaying,
                progressMs = progressMs,
                durationMs = durationMs,
                error = error,
                streamFormat = streamFormat,
                isResolving = isResolving,
                searchingBetter = searchingBetter,
                automix = automix,
                smartAnalysis = smartAnalysis,
                transitionWindow = transitionWindow,
                mixing = mixing,
                substituted = substituted,
                pinnedToOriginal = pinnedToOriginal,
                liked = liked,
                shuffle = shuffle,
                repeatMode = repeatMode,
                playbackSpeed = playbackSpeed,
                animatedCanvas = animatedCanvas,
                showNerdStats = showNerdStats,
                fullBleedArtwork = fullBleedArtwork,
                canvas = canvas,
                onPlayPause = onPlayPause,
                onRevertToOriginal = onRevertToOriginal,
                onUpgradeQuality = onUpgradeQuality,
                onToggleLike = onToggleLike,
                onSeek = onSeek,
                onPrevious = onPrevious,
                onNext = onNext,
                onShuffleChange = onShuffleChange,
                onRepeatModeChange = onRepeatModeChange,
                onPlaybackSpeedChange = onPlaybackSpeedChange,
                contentPadding = stagePadding,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            if (panel != DesktopPlayerPanel.NONE) {
                // The Row gave its top inset up to the cover, so the panel beside it takes its own
                // back.
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(top = if (fullBleedArtwork) 92.dp else 0.dp),
                ) {
                    when (panel) {
                        DesktopPlayerPanel.LYRICS -> DesktopLyricsPanel(
                            lyrics = lyrics,
                            loading = lyricsLoading,
                            error = lyricsError,
                            progressMs = progressMs,
                            isPlaying = isPlaying,
                            blurUnfocused = lyricsBlur,
                            onSeek = onSeek,
                            trackId = song.videoId,
                        )
                        else -> DesktopPlayerQueuePanel(
                            upcoming = upcoming,
                            autoplayStart = autoplayStart,
                            autoplay = autoplay,
                            shuffle = shuffle,
                            onAutoplayChange = onAutoplayChange,
                            onShuffleChange = onShuffleChange,
                            onSongClick = onQueueSongClick,
                            onClearQueue = onClearQueue,
                        )
                    }
                }
            }
        }

        // Window controls, floating over the artwork wash rather than sitting in a title bar — the
        // same placement Music uses full-screen.
        DesktopPlayerPill(Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 18.dp)) {
            DesktopPlayerPillButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, DesktopStrings["d_close_player", "Close player"], tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(4.dp))
            // Beside the close button because the two are the same kind of thing — what the window
            // does, rather than what the music does — and it is where Music puts its own.
            val fullScreen by DesktopWindowMode.fullScreen.collectAsState()
            DesktopPlayerPillButton(
                onClick = { DesktopWindowMode.toggle() },
                selected = fullScreen,
            ) {
                Icon(
                    if (fullScreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                    if (fullScreen) "Leave full screen" else "Full screen",
                    tint = if (fullScreen) Color.Black else Color.White,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        val hideVolumeBar by DesktopAppearanceSettings.hideVolumeBar.collectAsState()
        if (!hideVolumeBar) {
            DesktopPlayerPill(Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = 18.dp)) {
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    modifier = Modifier.width(120.dp).height(20.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                    ),
                )
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Rounded.VolumeUp, DesktopStrings["d_volume", "Volume"], tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        DesktopPlayerPill(Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 18.dp)) {
            DesktopPlayerPillButton(
                onClick = {
                    onPanelChange(
                        if (panel == DesktopPlayerPanel.LYRICS) {
                            DesktopPlayerPanel.NONE
                        } else {
                            DesktopPlayerPanel.LYRICS
                        },
                    )
                },
                selected = panel == DesktopPlayerPanel.LYRICS,
            ) {
                Icon(
                    Icons.Rounded.Lyrics,
                    DesktopStrings["open_lyrics", "Lyrics"],
                    tint = if (panel == DesktopPlayerPanel.LYRICS) Color.Black else Color.White,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            DesktopPlayerPillButton(
                onClick = {
                    onPanelChange(
                        if (panel == DesktopPlayerPanel.QUEUE) {
                            DesktopPlayerPanel.NONE
                        } else {
                            DesktopPlayerPanel.QUEUE
                        },
                    )
                },
                selected = panel == DesktopPlayerPanel.QUEUE,
            ) {
                Icon(
                    Icons.Rounded.ViewList,
                    DesktopStrings["up_next", "Up Next"],
                    tint = if (panel == DesktopPlayerPanel.QUEUE) Color.Black else Color.White,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

private enum class DesktopPlayerPanel { NONE, LYRICS, QUEUE }

/** The track playing, for rows that want to say so. */
private val LocalNowPlaying = compositionLocalOf<Song?> { null }

/** Whether this row is the track being played, for the now-playing highlight. */
@Composable
private fun Song.isNowPlaying(): Boolean = isSameTrackAs(LocalNowPlaying.current)

/** The rounded translucent capsule the player's floating controls sit in. */
@Composable
internal fun DesktopPlayerPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
internal fun DesktopPlayerPillButton(
    onClick: () -> Unit,
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (selected) Color.White else Color.Transparent)
            // Only where it is not already filled: a wash over the white pill would dirty it.
            .then(if (selected) Modifier else Modifier.desktopHoverWash())
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/** Sleeve, credits, scrubber and transport — the left half of the player. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesktopPlayerStage(
    song: Song,
    onOpenArtist: (Song) -> Unit,
    actions: DesktopSongActions,
    /** The strip above the scrubber — see [DesktopCurrentLyricLine]. */
    lyrics: DesktopLyrics?,
    lyricsLoading: Boolean,
    lyricsUnavailable: Boolean,
    syncedLyrics: Boolean,
    lyricsPanelOpen: Boolean,
    onOpenLyrics: () -> Unit,
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    error: String?,
    streamFormat: DesktopStreamFormat?,
    isResolving: Boolean,
    searchingBetter: Boolean,
    automix: Boolean,
    smartAnalysis: SmartAnalysis,
    transitionWindow: TransitionWindow?,
    mixing: Boolean,
    substituted: Boolean,
    pinnedToOriginal: Boolean,
    liked: Boolean,
    shuffle: Boolean,
    repeatMode: DesktopRepeatMode,
    playbackSpeed: Float,
    animatedCanvas: Boolean,
    showNerdStats: Boolean,
    fullBleedArtwork: Boolean,
    canvas: DesktopCanvasArtwork?,
    onPlayPause: () -> Unit,
    onRevertToOriginal: () -> Unit,
    onUpgradeQuality: () -> Unit,
    onToggleLike: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onRepeatModeChange: (DesktopRepeatMode) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /** The inset the page would have given the whole stage. */
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 72.dp.toPx() }
    var swipeOffset by remember { mutableStateOf(0f) }
    val swipeSettle by animateFloatAsState(
        targetValue = swipeOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "swipeOffset",
    )

    Column(
        // Uncapped with full-screen cover art on: the cover spans the whole of the player's own
        // column, and the controls under it take the cap back for themselves.
        modifier.then(if (fullBleedArtwork) Modifier else Modifier.widthIn(max = 560.dp)),
        verticalArrangement = if (fullBleedArtwork) Arrangement.Top else Arrangement.Center,
        // Centred whichever cover is in use. Left-aligned, the contained sleeve centred itself in
        // the space it was given while the title, scrubber and transport stayed where the lyrics
        // panel had pushed them — so closing the panel moved the cover to the middle of the window
        // and left everything below it stranded down one side.
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                // Full-screen cover art takes every pixel the controls under it do not need.
                .weight(1f, fill = fullBleedArtwork),
            contentAlignment = Alignment.Center,
        ) {
            // Square, and bounded by whichever of the three runs out first: the column's width, the
            // height left over once the controls have taken theirs, and a ceiling so a maximised
            // window does not turn the sleeve into a poster. Sized rather than aspect-ratioed
            // because a ratio honours one constraint and ignores the other — capped by width it
            // left dead space above and below, and freed it grew down over the controls.
            val sleeve = minOf(maxWidth, maxHeight, SLEEVE_MAX)
            Box(
                Modifier
                    // Full-screen cover art fills the region it was given and crops to it; a phone
                    // can assume that region is square, and a resizable window never can.
                    .then(
                        if (fullBleedArtwork) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier.size(sleeve)
                        },
                    )
                    // Dragging the sleeve skips, and the sleeve follows the pointer so the gesture
                    // has something to hold on to.
                    .pointerInput(song.videoId) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragCancel = { swipeOffset = 0f },
                            onDragEnd = {
                                when {
                                    total <= -swipeThreshold -> onNext()
                                    total >= swipeThreshold -> onPrevious()
                                }
                                swipeOffset = 0f
                            },
                            onHorizontalDrag = { _, delta ->
                                total += delta
                                swipeOffset = total * SLEEVE_DRAG_DAMPING
                            },
                        )
                    }
                    .graphicsLayer { translationX = swipeSettle }
                    // A card casts a shadow; a picture that *is* the player has no edge to cast one
                    // from.
                    .then(if (fullBleedArtwork) Modifier else Modifier.shadow(28.dp, RoundedCornerShape(10.dp)))
                    .clip(if (fullBleedArtwork) RectangleShape else RoundedCornerShape(10.dp)),
            ) {
                Box(Modifier.fillMaxSize().heroFade(fullBleedArtwork)) {
                    // Always the cover, with the clip over it once it has a frame.
                    DesktopArtwork(
                        song.thumbnailUrl,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        px = PLAYER_ART_PX,
                    )
                    if (canvas != null && animatedCanvas) {
                        DesktopCanvasView(canvas.url, Modifier.fillMaxSize(), isPlaying = isPlaying, fallbackUrl = canvas.fallbackUrl)
                    }
                }
                // Inside the sleeve's own clip, so it takes the corners and the artwork behind it
                // rather than sitting in the chrome below. Only where the cover dissolves into the
                // page — on a contained sleeve it would be text laid over the picture.
                if (showNerdStats && fullBleedArtwork) {
                    DesktopSleeveStats(
                        format = streamFormat,
                        automix = automix,
                        smartAnalysis = smartAnalysis,
                        isVideo = song.isVideo,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
            // Which way the release will go.
            val hintProgress = (abs(swipeSettle) / swipeThreshold).coerceIn(0f, 1f)
            if (hintProgress > 0.01f) {
                val showNext = swipeSettle < 0f
                Icon(
                    imageVector = if (showNext) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = hintProgress * 0.85f),
                    modifier = Modifier.align(Alignment.BottomCenter).size(18.dp),
                )
            }
        }
        // The controls keep the width and the inset the page gives them; only the cover above is
        // allowed out to the window's own edges.
        BoxWithConstraints(
            Modifier.widthIn(max = if (fullBleedArtwork) HERO_CONTROLS_MAX else 560.dp),
        ) {
            // Whether there is room to spend on the controls themselves.
            val roomy = maxWidth >= HERO_ROOMY_CONTROLS
            Column(Modifier.fillMaxWidth().padding(contentPadding)) {
                Spacer(Modifier.height(if (fullBleedArtwork) 26.dp else 18.dp))
                if (showNerdStats && !fullBleedArtwork) {
                    DesktopSleeveStats(
                        format = streamFormat,
                        automix = automix,
                        smartAnalysis = smartAnalysis,
                        isVideo = song.isVideo,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        var titleOverflowing by remember(song.videoId) { mutableStateOf(false) }
                        DesktopMarqueeText(
                            // Trimmed of its leading.
                            text = song.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                lineHeightStyle = TIGHT_LINE_HEIGHT,
                            ),
                            color = Color.White,
                            onOverflowChange = { titleOverflowing = it },
                        )
                        DesktopMarqueeText(
                            text = song.artist,
                            style = MaterialTheme.typography.titleMedium.copy(
                                lineHeightStyle = TIGHT_LINE_HEIGHT,
                            ),
                            color = DesktopSecondary,
                            // A title that is also scrolling goes first: starting together reads as
                            // clutter, so the artist waits a beat.
                            startDelayMillis = if (titleOverflowing) MARQUEE_ARTIST_STAGGER_MS else 0L,
                            modifier = Modifier.opensArtist(song.artistId) { onOpenArtist(song) },
                        )
                    }
                    IconButton(onClick = onToggleLike) {
                        Icon(
                            if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            "Favorite",
                            tint = if (liked) DesktopAccent else Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    DesktopSongMenuAnchor(
                        song = song,
                        liked = liked,
                        actions = actions,
                        onToggleLike = onToggleLike,
                        onRevertToOriginal = onRevertToOriginal.takeIf { substituted && !pinnedToOriginal },
                        onUpgradeQuality = onUpgradeQuality.takeIf { pinnedToOriginal },
                    )
                }
                // Switching versions is deliberately not a button on this screen.
                if (error != null) {
                    Text(
                        error,
                        color = DesktopAccent,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(18.dp))
                // The line being sung, directly above the scrubber — the same strip Android puts
                // there, and the player's other way in to the full panel.
                if (!lyricsPanelOpen && syncedLyrics) {
                    DesktopCurrentLyricLine(
                        lyrics = lyrics,
                        loading = lyricsLoading,
                        unavailable = lyricsUnavailable,
                        trackKey = song.videoId,
                        progressMs = progressMs,
                        durationMs = durationMs,
                        isPlaying = isPlaying,
                        onClick = onOpenLyrics,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                }
                var scrubbing by remember(song.videoId) { mutableStateOf(false) }
                var scrubValue by remember(song.videoId) { mutableStateOf(0f) }
                val played = if (durationMs > 0) progressMs.toFloat() / durationMs else 0f
                // What the bar and the clock both read from, so a scrub moves the time as well as
                // the fill rather than leaving them disagreeing.
                val shown = if (scrubbing) scrubValue else played
                DesktopThinSlider(
                    value = shown,
                    onValueChange = {
                        scrubbing = true
                        scrubValue = it
                    },
                    onValueChangeFinished = {
                        // On release only.
                        if (durationMs > 0) onSeek((scrubValue * durationMs).toLong())
                        scrubbing = false
                    },
                    transitionWindow = transitionWindow?.let { it.start..it.end },
                    mixing = mixing,
                    modifier = Modifier.fillMaxWidth(),
                )
                // The slider draws its track a few pixels inside its own height, so a gap measured
                // from its bounds reads larger than it is.
                Box(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (durationMs > 0) formatTime((shown * durationMs).toLong()) else "--:--",
                            color = DesktopSecondary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            // Music counts down what is left rather than up to the end.
                            if (durationMs > 0) "-${formatTime((durationMs - progressMs).coerceAtLeast(0))}" else "--:--",
                            color = DesktopSecondary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    // Pinned to the box's own centre rather than squeezed into the gap between the
                    // two timestamps.
                    DesktopQualityBadge(
                        format = streamFormat,
                        isResolving = isResolving,
                        searchingBetter = searchingBetter,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 8.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                // Three slots, the outer two of equal weight, so play sits on the row's true
                // centre.
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.weight(1f).offset(x = -CONTROL_GLYPH_INSET),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        IconButton(onClick = { onShuffleChange(!shuffle) }) {
                            Icon(
                                Icons.Rounded.Shuffle,
                                DesktopStrings["shuffle", "Shuffle"],
                                tint = if (shuffle) DesktopAccent else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(21.dp),
                            )
                        }
                    }
                    Row(
                        // The cluster grows with the row it sits in.
                        horizontalArrangement = Arrangement.spacedBy(if (roomy) 46.dp else 34.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val transport = if (roomy) 34.dp else 30.dp
                        IconButton(onClick = onPrevious) {
                            Icon(Icons.Rounded.FastRewind, DesktopStrings["widget_previous", "Previous"], tint = Color.White, modifier = Modifier.size(transport))
                        }
                        IconButton(onClick = onPlayPause) {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(if (roomy) 42.dp else 36.dp),
                            )
                        }
                        IconButton(onClick = onNext) {
                            Icon(Icons.Rounded.FastForward, DesktopStrings["widget_next", "Next"], tint = Color.White, modifier = Modifier.size(transport))
                        }
                    }
                    Row(
                        Modifier.weight(1f).offset(x = CONTROL_GLYPH_INSET),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                val next = when {
                                    playbackSpeed < 0.76f -> 1.0f
                                    playbackSpeed < 1.01f -> 1.25f
                                    playbackSpeed < 1.26f -> 1.5f
                                    playbackSpeed < 1.51f -> 2.0f
                                    else -> 0.5f
                                }
                                onPlaybackSpeedChange(next)
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp),
                        ) {
                            Text(
                                "${"%.2f".format(playbackSpeed)}×",
                                color = if (playbackSpeed != 1.0f) DesktopAccent else DesktopSecondary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        IconButton(onClick = { onRepeatModeChange(repeatMode.next()) }) {
                            Icon(
                                if (repeatMode == DesktopRepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                "Repeat ${repeatMode.label()}",
                                tint = if (repeatMode != DesktopRepeatMode.OFF) DesktopAccent else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(21.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopInlineAction(label: String, onClick: () -> Unit) {
    Spacer(Modifier.width(10.dp))
    Text(
        label,
        color = DesktopSecondary,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** The frosted pane the floating bars are made of. */
@Composable
internal fun Modifier.desktopBarGlass(shape: Shape): Modifier =
    desktopFrosted(DesktopBarGlass, tintAlpha = 0.55f).border(0.5.dp, DesktopBarEdge, shape)

/** The edge of a chrome pane that meets the page, and so has to dissolve into it. */
internal enum class DesktopChromeEdge { NONE, BOTTOM, END }

/**
 * The same pane for the window's own chrome — square-edged, and a shade denser, because the sidebar
 * and top bar carry far more text than a floating bar does.
 *
 * [edge] names the side the page is on. The pane's tint is dissolved across the last [fade] of it so
 * the two meet in a gradient rather than on a line.
 */
@Composable
internal fun Modifier.desktopChromeGlass(
    edge: DesktopChromeEdge = DesktopChromeEdge.NONE,
    fade: Float = 0.2f,
): Modifier = desktopFrosted(
    opaque = DesktopSurface.copy(alpha = 0.88f),
    tintAlpha = 0.46f,
    edge = edge,
    fade = fade,
)

@Composable
private fun Modifier.desktopFrosted(
    opaque: Color,
    tintAlpha: Float,
    edge: DesktopChromeEdge = DesktopChromeEdge.NONE,
    fade: Float = 0f,
): Modifier {
    val haze = LocalDesktopHaze.current
    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
    if (haze == null || reduceDynamicBlur) {
        return this.background(edge.dissolve(opaque, fade) ?: SolidColor(opaque))
    }
    val dissolve = edge.dissolve(Color.Black, fade)
    return this.hazeEffect(state = haze) {
        // Spelled out rather than taken from a preset.
        blurEnabled = true
        backgroundColor = DesktopSurface
        blurRadius = 30.dp
        // Dark enough that white text on the pane always wins, translucent enough that the
        // colour of what is behind it still comes through.
        tints = listOf(HazeTint(DesktopSurface.copy(alpha = tintAlpha)))
        noiseFactor = 0.04f
        // Assigned either way: the scope belongs to the node and outlives the draw, so a pane that
        // stops dissolving — the leading stretch of the top bar, when the window grows back out of
        // compact — would otherwise keep the mask it was given in the other layout.
        mask = dissolve
    }
}

/** The gradient that takes [colour] to nothing across the pane's last [fade]. */
private fun DesktopChromeEdge.dissolve(colour: Color, fade: Float): Brush? {
    if (this == DesktopChromeEdge.NONE || fade <= 0f) return null
    val stops = arrayOf(
        0f to colour,
        (1f - fade).coerceIn(0f, 1f) to colour,
        1f to Color.Transparent,
    )
    return if (this == DesktopChromeEdge.BOTTOM) {
        Brush.verticalGradient(*stops)
    } else {
        Brush.horizontalGradient(*stops)
    }
}

/** Android's names for the analysis budget. */
/**
 * The quality badge under the scrubber — a port of Android's `LosslessOrStats`, decided on the same
 * measured figures rather than on what a source promised.
 */
@Composable
private fun DesktopQualityBadge(
    format: DesktopStreamFormat?,
    isResolving: Boolean,
    searchingBetter: Boolean,
    modifier: Modifier = Modifier,
) {
    when {
        // Still looking — either the stream has not opened yet, or a better copy is being hunted
        // under the music.
        (searchingBetter && format?.isLossless != true) || (isResolving && format == null) ->
            DesktopQualityLabel(
                text = if (format?.isHiQuality == true) "High quality, upgrading" else "Upgrading quality",
                animated = false,
                modifier = modifier,
            )
        format?.isLossless == true -> DesktopQualityLabel(
            // The line Tidal, Qobuz and Apple Music all draw: past 16-bit or past 48kHz is Hi-Res,
            // a bit-exact CD rip is not.
            text = if (format.isHiRes) "Hi-Res Lossless" else "Lossless",
            // The shimmer is reserved for what was asked for and confirmed, which is what makes
            // this read as an achievement rather than a label — only one of these two is.
            animated = true,
            modifier = modifier,
        )
        format?.isDolbyAtmos == true -> DesktopQualityLabel(
            text = "Dolby Atmos",
            animated = true,
            icon = DolbyAtmosMark,
            iconSize = DpSize(13.dp, 10.dp),
            modifier = modifier,
        )
        // Lossy, but the good end of lossy — a source's 320kbps tier, which for a great many tracks
        // is the best copy that exists anywhere this app can reach.
        format?.isHiQuality == true -> DesktopQualityLabel(
            text = "High quality",
            animated = false,
            modifier = modifier,
        )
        else -> Unit
    }
}

/** A quality glyph ahead of the status label. */
@Composable
private fun DesktopQualityLabel(
    text: String,
    animated: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Headphones,
    iconSize: DpSize = DpSize(13.dp, 13.dp),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = if (animated) 0.7f else 0.45f),
            modifier = Modifier.size(iconSize),
        )
        Spacer(Modifier.width(4.dp))
        if (animated) {
            DesktopShimmerText(text)
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The badge text with a highlight band sweeping left to right across it every three seconds —
 * confirmed, not just claimed, so it is worth the shine.
 */
@Composable
private fun DesktopShimmerText(text: String) {
    var widthPx by remember { mutableStateOf(0) }
    val transition = rememberInfiniteTransition(label = "quality-shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "quality-shimmer-progress",
    )
    val base = Color.White.copy(alpha = 0.55f)
    val brush = if (widthPx <= 0) {
        Brush.linearGradient(listOf(base, base))
    } else {
        val band = widthPx * 0.6f
        val center = -band + progress * (widthPx + 2 * band)
        Brush.linearGradient(
            colorStops = arrayOf(0f to base, 0.5f to Color.White, 1f to base),
            start = Offset(center - band, 0f),
            end = Offset(center + band, 0f),
        )
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(brush = brush, fontWeight = FontWeight.SemiBold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.onSizeChanged { widthPx = it.width },
    )
}

/** The measured stream, and how far Automix has got, pinned to the sleeve's own bottom edge. */
@Composable
private fun DesktopSleeveStats(
    format: DesktopStreamFormat?,
    automix: Boolean,
    smartAnalysis: SmartAnalysis,
    isVideo: Boolean,
    modifier: Modifier = Modifier,
) {
    if (format == null && !automix) return
    // A plain white line reads fine over the usual dark tile, but a light stretch of an animated
    // cover — sky, snow, a pale sleeve — washes it out entirely.
    val style = MaterialTheme.typography.labelSmall.copy(
        shadow = Shadow(color = Color.Black.copy(alpha = 0.55f), offset = Offset(0f, 1f), blurRadius = 4f),
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        format?.summary?.let {
            Text(
                it,
                style = style,
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        // Only when Automix is actually switched on: otherwise this would report on analysis
        // nothing is going to use, which is noise rather than a stat.
        if (automix) {
            Text(
                if (isVideo) {
                    "Automix · not supported for video uploads"
                } else {
                    "Automix · this song: ${smartAnalysis.current.label()} · next: ${smartAnalysis.next.label()}"
                },
                style = style,
                // Dimmer than the measured line above it: that one describes the audio, this one
                // describes the app, and the ranking should show.
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The official Dolby double-D mark, the same path Android's `ic_dolby_atmos` drawable carries, so
 * the two platforms draw one glyph.
 */
private val DolbyAtmosMark: ImageVector by lazy {
    ImageVector.Builder(
        name = "DolbyAtmos",
        defaultWidth = 17.dp,
        defaultHeight = 12.dp,
        viewportWidth = 108.95f,
        viewportHeight = 76.599f,
    ).apply {
        addPath(
            pathData = addPathNodes(
                "M0,0 L0,76.599 L108.95,76.599 L108.95,0 L0,0 z " +
                    "M12.882,9.987 L25.264,9.987 C38.156,9.987 48.932,22.857 48.932,38.151 " +
                    "C48.935,53.883 38.156,66.612 25.264,66.612 L12.882,66.612 L12.882,9.987 z " +
                    "M83.688,9.987 L96.17,9.987 L96.17,66.612 L83.688,66.612 " +
                    "C70.79,66.612 60.017,53.883 60.017,38.151 C60.017,22.857 70.79,9.987 83.688,9.987 z",
            ),
            pathFillType = PathFillType.EvenOdd,
            fill = SolidColor(Color.White),
        )
    }.build()
}

/** What an IconButton puts between its 48dp target and a 21dp glyph, and so how far the outer
 *  controls have to move to line up with the scrubber above them. */
private val CONTROL_GLYPH_INSET = 13.5.dp

/** How much of a full-bleed cover's height is spent dissolving into the page below it. */
private const val HERO_FADE_FRACTION = 0.42f

/** The same, for the three edges Android never has to fade. */
private const val HERO_EDGE_FADE_FRACTION = 0.14f

/** Ceilings on the two fades. */
private val HERO_FADE_MAX = 240.dp
private val HERO_EDGE_FADE_MAX = 110.dp

/** How large a contained sleeve is allowed to get, however much room the window has. */
private val SLEEVE_MAX = 460.dp

/** How wide the controls under a full-bleed cover may grow. */
private val HERO_CONTROLS_MAX = 780.dp

/** The width past which the controls themselves are worth enlarging. */
private val HERO_ROOMY_CONTROLS = 620.dp

/** Dissolves a full-bleed cover into the backdrop on all four sides. */
private fun Modifier.heroFade(enabled: Boolean): Modifier =
    if (!enabled) {
        this
    } else {
        this
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val bottom = minOf(size.height * HERO_FADE_FRACTION, HERO_FADE_MAX.toPx())
                val top = minOf(size.height * HERO_EDGE_FADE_FRACTION, HERO_EDGE_FADE_MAX.toPx())
                val side = minOf(size.width * HERO_EDGE_FADE_FRACTION, HERO_EDGE_FADE_MAX.toPx())
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startY = size.height - bottom,
                        endY = size.height,
                    ),
                    blendMode = BlendMode.DstIn,
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        startY = 0f,
                        endY = top,
                    ),
                    blendMode = BlendMode.DstIn,
                )
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        startX = 0f,
                        endX = side,
                    ),
                    blendMode = BlendMode.DstIn,
                )
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startX = size.width - side,
                        endX = size.width,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
    }

private fun AutomixPerformanceMode.label(): String = when (this) {
    AutomixPerformanceMode.EFFICIENT -> "Efficient"
    AutomixPerformanceMode.BALANCED -> "Balanced"
    AutomixPerformanceMode.PERFORMANCE -> "Performance"
}

/** Android's description of each rung. */
private fun AutomixPerformanceMode.detail(): String = when (this) {
    AutomixPerformanceMode.EFFICIENT ->
        "1 thread · lowest heat and battery use · analysis may take longer"
    AutomixPerformanceMode.BALANCED ->
        "2 threads · recommended balance of speed, heat and battery"
    AutomixPerformanceMode.PERFORMANCE ->
        "4 threads · fastest analysis · higher heat and battery use"
}

/** Android's wording for the Automix line, so both players read alike. */
private fun TrackAnalysisState.label(): String = when (this) {
    TrackAnalysisState.ANALYSED -> "analysed"
    TrackAnalysisState.REFINING -> "analysed, refining…"
    TrackAnalysisState.ANALYSING -> "analysing…"
    TrackAnalysisState.WAITING -> "waiting"
    TrackAnalysisState.FAILED -> "failed"
}

/** How fast the credits crawl — unhurried, not a ticker. */
private const val MARQUEE_DP_PER_SEC = 26f

/** Clear air between the tail of the line and the copy chasing it round. */
private val MARQUEE_GAP = 48.dp

/** How long a line rests back at its start before the next pass. */
private const val MARQUEE_REST_MS = 5_000L

/** The head start the title gets when both lines are scrolling. */
private const val MARQUEE_ARTIST_STAGGER_MS = 3_000L

/** How far the sleeve follows a drag: a hint, not a drag-to-position. */
private const val SLEEVE_DRAG_DAMPING = 0.35f

/** A single line that scrolls in place, only when it is too long to show in full. */
@Composable
private fun DesktopMarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    startDelayMillis: Long = 0L,
    onOverflowChange: (Boolean) -> Unit = {},
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.clipToBounds()) {
        val maxWidthPx = constraints.maxWidth
        val layout = remember(text, style, maxWidthPx) {
            measurer.measure(text = text, style = style, maxLines = 1, softWrap = false)
        }
        val overflowing = layout.size.width > maxWidthPx
        LaunchedEffect(overflowing) { onOverflowChange(overflowing) }

        if (!overflowing) {
            Text(text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            return@BoxWithConstraints
        }

        // One whole copy plus the gap behind it: the distance at which the second copy sits exactly
        // where the first one started.
        val travel = layout.size.width + with(density) { MARQUEE_GAP.roundToPx() }
        val offsetX = remember { Animatable(0f) }
        LaunchedEffect(text, travel, startDelayMillis) {
            offsetX.snapTo(0f)
            val perMs = with(density) { MARQUEE_DP_PER_SEC.dp.toPx() } / 1000f
            val duration = (travel / perMs).roundToInt().coerceAtLeast(400)
            delay(startDelayMillis)
            while (true) {
                offsetX.animateTo(-travel.toFloat(), tween(duration, easing = LinearEasing))
                // Invisible: the trailing copy has arrived at the leading one's starting mark, so
                // the line is already where this puts it.
                offsetX.snapTo(0f)
                delay(MARQUEE_REST_MS)
            }
        }

        Row(
            // Measured unbounded so the copies lay out at their full width.
            Modifier
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = style, color = color, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(MARQUEE_GAP))
            Text(text, style = style, color = color, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun DesktopPlayerQueuePanel(
    upcoming: List<Song>,
    /** Where AutoPlay's own tracks begin within [upcoming]; its size when none. */
    autoplayStart: Int,
    autoplay: Boolean,
    shuffle: Boolean,
    onAutoplayChange: (Boolean) -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onSongClick: (Int) -> Unit,
    onClearQueue: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DesktopQueueModeButton(
                icon = Icons.Rounded.AllInclusive,
                label = DesktopStrings["autoplay", "AutoPlay"],
                selected = autoplay,
                onClick = { onAutoplayChange(!autoplay) },
                modifier = Modifier.weight(1f),
            )
            // Shuffle, not crossfade.
            DesktopQueueModeButton(
                icon = Icons.Rounded.Shuffle,
                label = DesktopStrings["shuffle", "Shuffle"],
                selected = shuffle,
                onClick = { onShuffleChange(!shuffle) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                DesktopStrings["d_continue_playing", "Continue Playing"],
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            if (upcoming.isNotEmpty()) {
                TextButton(onClick = onClearQueue, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text(DesktopStrings["clear", "Clear"], color = DesktopAccent, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.16f))
        if (upcoming.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(DesktopStrings["d_there_s_no_music_in_the_queue", "There's no music in the queue."], color = DesktopSecondary)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(upcoming) { index, song ->
                    // The mix keeps its own heading and stays below everything the listener queued,
                    // which is where it plays from.
                    if (index == autoplayStart) {
                        Text(
                            DesktopStrings["autoplay", "AutoPlay"],
                            style = MaterialTheme.typography.labelLarge,
                            color = DesktopSecondary,
                            modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 6.dp),
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(7.dp))
                            .clickable { onSongClick(index) }
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DesktopArtwork(song.thumbnailUrl, Modifier.size(42.dp).clip(RoundedCornerShape(5.dp)), px = ROW_ART_PX)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                song.artist,
                                color = DesktopSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        song.durationText?.let {
                            Text(it, color = DesktopSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (index < upcoming.lastIndex) {
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.07f),
                            modifier = Modifier.padding(start = 62.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopQueueModeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color.White.copy(alpha = if (selected) 0.22f else 0.10f))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            null,
            tint = if (selected) Color.White else DesktopSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = if (selected) Color.White else DesktopSecondary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** What the queue panel's Crossfade button turns crossfade back on at. */
private const val DEFAULT_CROSSFADE_SECONDS = 6

@Composable
private fun DesktopShelf(
    shelf: HomeShelf,
    hero: Boolean,
    onItemClick: (ShelfItem) -> Unit,
    gutter: Dp = DesktopPageGutter,
) {
    Column {
        SectionTitle(shelf.title, shelf.subtitle, gutter)
        DesktopScrollableRow(gutter = gutter) {
            items(shelf.items, key = { it.videoId ?: it.browseId ?: it.title }) { item ->
                DesktopShelfCard(item, hero, onItemClick)
            }
        }
    }
}

/**
 * A sideways row with the affordances a desktop needs to move it.
 *
 * A carousel longer than the window has no way to be scrolled with a mouse — the wheel belongs to
 * the page underneath — so this adds the three that work: dragging it, the pointer's horizontal
 * ticks while it is hovered, and a paging arrow at whichever end still has more.
 *
 * Shared rather than repeated: every shelf-shaped row in the app wants the same three.
 */
@Composable
internal fun DesktopScrollableRow(
    gutter: Dp = DesktopPageGutter,
    spacing: Dp = 14.dp,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val rowState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    /** One screenful, less a sliver so the card at the edge stays in view. */
    fun page(forward: Boolean) {
        val viewport = rowState.layoutInfo.let { it.viewportEndOffset - it.viewportStartOffset }
        val distance = (viewport - SHELF_PAGE_OVERLAP_PX).coerceAtLeast(SHELF_PAGE_OVERLAP_PX)
        scope.launch { rowState.animateScrollBy(if (forward) distance.toFloat() else -distance.toFloat()) }
    }

    // Whichever row the pointer is in takes the horizontal ticks; the page underneath keeps the
    // wheel, as it does everywhere else.
    LaunchedEffect(hovered) {
        if (!hovered) return@LaunchedEffect
        DesktopHorizontalScroll.ticks.collect { tick -> rowState.scrollBy(tick * SHELF_WHEEL_STEP) }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .hoverable(interaction)
            // A row can be taken hold of and pulled, which is the first thing anyone tries.
            .draggable(
                state = rememberDraggableState { delta -> scope.launch { rowState.scrollBy(-delta) } },
                orientation = Orientation.Horizontal,
            ),
    ) {
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = gutter),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
        DesktopShelfArrow(
            alignment = Alignment.CenterStart,
            icon = Icons.Rounded.ChevronLeft,
            description = DesktopStrings["d_scroll_left", "Scroll left"],
            visible = hovered && rowState.canScrollBackward,
            onClick = { page(forward = false) },
        )
        DesktopShelfArrow(
            alignment = Alignment.CenterEnd,
            icon = Icons.Rounded.ChevronRight,
            description = DesktopStrings["d_scroll_right", "Scroll right"],
            visible = hovered && rowState.canScrollForward,
            onClick = { page(forward = true) },
        )
    }
}

/** How much of the outgoing card stays visible after a paged scroll. */
internal const val SHELF_PAGE_OVERLAP_PX = 120

/** How far one notch of a horizontal scroll moves a shelf. */
internal const val SHELF_WHEEL_STEP = 90f

@Composable
internal fun BoxScope.DesktopShelfArrow(
    alignment: Alignment,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    visible: Boolean,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(alignment).padding(horizontal = 6.dp),
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, description, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun DesktopShelfCard(item: ShelfItem, hero: Boolean, onClick: (ShelfItem) -> Unit) {
    val width = if (hero) 270.dp else 158.dp
    val shape = RoundedCornerShape(if (hero) 16.dp else 10.dp)
    Column(
        Modifier
            .width(width)
            // The whole card answers the pointer, artwork and captions together, so the two do not
            // move independently of each other.
            .desktopHoverLift(shape)
            .clickable { onClick(item) },
    ) {
        Box(
            Modifier
                .size(width, if (hero) 205.dp else 158.dp)
                .clip(shape)
                .background(DesktopGlass)
                .border(1.dp, Color.White.copy(alpha = 0.12f), shape),
        ) {
            DesktopArtwork(item.thumbnailUrl, Modifier.fillMaxSize())
            if (hero) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                        ),
                    ),
                )
                Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                    Text(item.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(item.subtitle, color = Color.White.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (!hero) {
            Spacer(Modifier.height(8.dp))
            Text(item.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DesktopSongRow(
    song: Song,
    liked: Boolean,
    onClick: (Song) -> Unit,
    onToggleLike: ((Song) -> Unit)?,
    onDownload: ((Song) -> Unit)? = null,
    onAddToPlaylist: ((Song) -> Unit)? = null,
    downloaded: Boolean = false,
    downloadInProgress: Boolean = false,
    number: Int? = null,
    /** The row's own "…", when the page has a menu to give it. */
    menu: (@Composable (Song) -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DesktopGlass.copy(alpha = 0.38f))
            .clickable { onClick(song) }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Text("$number", Modifier.width(28.dp), color = DesktopSecondary, textAlign = TextAlign.Center)
        }
        DesktopArtwork(
            song.thumbnailUrl,
            Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
            px = ROW_ART_PX,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                color = if (song.isNowPlaying()) DesktopAccent else Color.White,
            )
            Text(listOfNotNull(song.artist.takeIf(String::isNotBlank), song.durationText).joinToString(" · "), color = DesktopSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        song.sourceQuality?.let { quality ->
            val badge = quality.uppercase().takeIf { value ->
                value in setOf("LOSSLESS", "FLAC", "HI-RES", "HIRES", "HIGH", "320")
            }
            if (badge != null) {
                Text(
                    badge,
                    color = DesktopAccent,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        if (onToggleLike != null) {
            IconButton(onClick = { onToggleLike(song) }) { Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorite", tint = if (liked) DesktopAccent else DesktopSecondary) }
        }
        if (onDownload != null) {
            if (downloadInProgress) {
                IconButton(onClick = { onDownload(song) }) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = DesktopAccent,
                        strokeWidth = 2.dp,
                    )
                }
            } else {
                IconButton(onClick = { if (!downloaded) onDownload(song) }) {
                    Icon(
                        imageVector = if (downloaded) Icons.Rounded.Download else Icons.Rounded.CloudDownload,
                        contentDescription = if (downloaded) "Downloaded" else "Download",
                        tint = if (downloaded) DesktopAccent else DesktopSecondary,
                    )
                }
            }
        }
        if (onAddToPlaylist != null) {
            IconButton(onClick = { onAddToPlaylist(song) }) {
                Icon(Icons.Rounded.Add, DesktopStrings["add_to_playlist", "Add to playlist"], tint = DesktopSecondary)
            }
        }
        // The same menu every other surface opens, so a row on a list page offers what a row on
        // the player does rather than only a play button.
        menu?.invoke(song)
        IconButton(onClick = { onClick(song) }) { Icon(Icons.Rounded.PlayArrow, DesktopStrings["play", "Play"], tint = DesktopAccent) }
    }
}

@Composable
private fun DesktopBrowseRow(item: com.music.bitchord.data.model.BrowseItem, onClick: (com.music.bitchord.data.model.BrowseItem) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onClick(item) }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DesktopArtwork(item.thumbnailUrl, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle.ifBlank { item.type.name.lowercase().replaceFirstChar(Char::uppercase) }, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Rounded.Album, null, tint = DesktopSecondary)
    }
}

@Composable
private fun DesktopPlayerBar(
    song: Song?,
    isPlaying: Boolean,
    isLiked: Boolean,
    progressMs: Long,
    error: String?,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(82.dp)
            .background(DesktopGlassStrong)
            .border(width = 1.dp, color = DesktopDivider)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (song == null) {
            Icon(Icons.Rounded.MusicNote, null, tint = DesktopSecondary)
            Spacer(Modifier.width(12.dp))
            Text(DesktopStrings["d_choose_something_to_play", "Choose something to play"], color = DesktopSecondary)
        } else {
            Row(Modifier.weight(1f).clickable(onClick = onExpand), verticalAlignment = Alignment.CenterVertically) {
                DesktopArtwork(song.thumbnailUrl, Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(error ?: song.artist, color = if (error == null) DesktopSecondary else DesktopAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onToggleLike) { Icon(if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorite", tint = if (isLiked) DesktopAccent else Color.White) }
            IconButton(onClick = onPrevious) { Icon(Icons.Rounded.FastRewind, DesktopStrings["widget_previous", "Previous"]) }
            IconButton(onClick = onPlayPause) { Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Pause" else "Play", modifier = Modifier.size(30.dp)) }
            IconButton(onClick = onNext) { Icon(Icons.Rounded.FastForward, DesktopStrings["widget_next", "Next"]) }
            IconButton(onClick = onOpenQueue) { Icon(Icons.Rounded.QueueMusic, DesktopStrings["queue", "Queue"]) }
            Icon(Icons.Rounded.VolumeUp, DesktopStrings["d_volume", "Volume"], tint = DesktopSecondary, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** A page's box, inset from the shell's chrome. */
@Composable
private fun DesktopPageScaffold(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    val direction = LocalLayoutDirection.current
    Box(
        Modifier.fillMaxSize().padding(
            start = contentPadding.calculateStartPadding(direction),
            end = contentPadding.calculateEndPadding(direction),
            top = contentPadding.calculateTopPadding(),
        ),
    ) {
        CompositionLocalProvider(
            LocalDesktopBottomInset provides contentPadding.calculateBottomPadding(),
        ) {
            content()
        }
    }
}

/** A scrollable's own padding, plus room to clear whatever floats over the page. */
@Composable
private fun pagePadding(
    start: Dp = 0.dp,
    top: Dp = 0.dp,
    end: Dp = 0.dp,
    bottom: Dp = 0.dp,
): PaddingValues = PaddingValues(
    start = start,
    top = top,
    end = end,
    bottom = bottom + LocalDesktopBottomInset.current,
)

@Composable
private fun PageHeading(title: String, subtitle: String, gutter: Dp = DesktopPageGutter) {
    Column(Modifier.padding(horizontal = gutter, vertical = 24.dp)) {
        Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text(subtitle, color = DesktopSecondary, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String = "", gutter: Dp = DesktopPageGutter) {
    Column(Modifier.padding(horizontal = gutter)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (subtitle.isNotBlank()) Text(subtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DesktopLoadingPage(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = DesktopAccent)
            Spacer(Modifier.height(14.dp))
            Text(label, color = DesktopSecondary)
        }
    }
}

@Composable
private fun DesktopErrorPage(message: String, onRetry: () -> Unit) {
    DesktopEmptyPage(Icons.Rounded.Tune, "Something went wrong", message, onRetry, "Try again")
}

@Composable
private fun DesktopEmptyPage(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String, onAction: (() -> Unit)? = null, actionLabel: String? = null) {
    Column(Modifier.fillMaxWidth().padding(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = DesktopAccent, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(message, color = DesktopSecondary, textAlign = TextAlign.Center)
        if (onAction != null && actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun LibraryTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DesktopGlassStrong)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
            .padding(14.dp),
    ) {
        Icon(icon, null, tint = DesktopAccent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(34.dp))
        Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A titled block of settings rows. */
@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title.uppercase(),
            color = DesktopSecondary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
        )
        Column(Modifier.desktopCardInset(RoundedCornerShape(12.dp))) { content() }
    }
}

/** A settings row that opens something rather than toggling it. */
@Composable
private fun SettingsNavigationRow(title: String, subtitle: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (hovered) DesktopRowHover else Color.Transparent)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = DesktopSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.Rounded.ChevronRight,
            null,
            tint = DesktopSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** A settings row with an icon, a switch, and a click target of its own. */
@Composable
private fun SettingsSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (enabled) DesktopAccent else DesktopSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) Color.White else DesktopSecondary)
            Text(subtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** A settings row whose control is a slider, with its value read out beside the title. */
@Composable
private fun SettingsSlider(
    title: String,
    subtitle: String,
    readout: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title)
                Text(subtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Text(readout, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth().height(28.dp),
        )
    }
}

@Composable
private fun SettingsToggle(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title); Text(subtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall) }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = DesktopAccent)
        Spacer(Modifier.width(14.dp))
        Column { Text(title); Text(subtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun DesktopActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(modifier.clip(CircleShape).background(DesktopAccent).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

private fun SearchResult.key(): String = when (this) {
    is SearchResult.TopTrack -> "track:${song.videoId}"
    is SearchResult.Track -> "track:${song.videoId}"
    is SearchResult.Browse -> "browse:${item.browseId}"
}

private fun ShelfItem.toSong(): Song {
    val parts = subtitle.split(" • ", " · ", " | ").filter(String::isNotBlank)
    // Android's InnertubeParser uses the first non-type/non-tally segment.
    val artist = parts.firstOrNull {
        !it.matches(SHELF_DURATION) &&
            it.lowercase() !in SHELF_TYPE_WORDS &&
            !it.matches(SHELF_TALLY)
    } ?: "Unknown Artist"
    return Song(
        videoId = videoId.orEmpty(),
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        durationText = parts.firstOrNull { it.matches(SHELF_DURATION) },
    )
}

private fun String.isUnknownArtist(): Boolean =
    trim().equals("unknown", ignoreCase = true) ||
        trim().equals("unknown artist", ignoreCase = true) ||
        trim().equals("unknown artist(s)", ignoreCase = true) ||
        isBlank()

private val SHELF_DURATION = Regex("""\d{1,2}:\d{2}(?::\d{2})?""")
private val SHELF_TALLY = Regex("[0-9,.]+\\s*(?:views?|likes?|songs?)", RegexOption.IGNORE_CASE)
private val SHELF_TYPE_WORDS = setOf("song", "video", "album", "ep", "single", "playlist", "mix")

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatCollectionDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60L
    return if (minutes < 60L) {
        "$minutes min"
    } else {
        "${minutes / 60L} hr ${minutes % 60L} min"
    }
}

private fun formatSleepTimer(ms: Long): String = formatTime(ms)
