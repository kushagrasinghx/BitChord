/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.soundcloud.SoundCloudAudioProvider
import moe.rukamori.archivetune.spotify.Spotify as SpotifyCore
import moe.rukamori.archivetune.spotify.SpotifyWebSession
import moe.rukamori.archivetune.canvas.SpotifyCanvasProvider
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.constants.ArchiveTuneCanvasKey
import moe.rukamori.archivetune.constants.SpotifyCanvasKey
import moe.rukamori.archivetune.constants.SpotifySpDcKey
import moe.rukamori.archivetune.constants.ArtistSeparatorsKey
import moe.rukamori.archivetune.constants.ExternalDownloaderEnabledKey
import moe.rukamori.archivetune.constants.ExternalDownloaderPackageKey
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.constants.SpeedDialSongIdsKey
import moe.rukamori.archivetune.constants.SongCanvasDisabledKey
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.CanvasArtworkRefetchResult
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.deezer.DeezerAudioProvider
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.jiosaavn.SaavnService
import moe.rukamori.archivetune.tidal.TidalAudioProvider
import moe.rukamori.archivetune.qobuz.QobuzAudioProvider
import moe.rukamori.archivetune.constants.QobuzBackupEndpointsKey
import moe.rukamori.archivetune.qobuz.QobuzBackupProvider
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.ListDialog
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.NewActionGrid
import moe.rukamori.archivetune.ui.player.rememberDeviceMusicVolumeController
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlaybackCache
import moe.rukamori.archivetune.ui.player.fetchCanvasArtworkForPlayback
import moe.rukamori.archivetune.ui.player.hasAnyCanvasSource
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.utils.SpeedDialPinType
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.utils.parseSpeedDialPins
import moe.rukamori.archivetune.audiosource.AudioSourceConfig
import moe.rukamori.archivetune.audiosource.SongSourceOverride
import moe.rukamori.archivetune.audiosource.SongCanvasDisabled
import moe.rukamori.archivetune.constants.AudioSourceOrderKey
import moe.rukamori.archivetune.constants.TidalAudioQuality
import moe.rukamori.archivetune.constants.TidalAudioQualityKey
import moe.rukamori.archivetune.constants.AudioSourceType
import moe.rukamori.archivetune.constants.SongSourceOverrideKey
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberLowDataModeActive
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.serializeSpeedDialPins
import moe.rukamori.archivetune.utils.shareLocalAudio
import moe.rukamori.archivetune.utils.toggleSpeedDialPin
import java.time.LocalDateTime
import moe.rukamori.archivetune.ui.component.MenuSectionDivider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private data class CanvasSourceOption(
    val label: String,
    val artwork: CanvasArtwork,
)

@Composable
fun PlayerMenu(
    mediaMetadata: MediaMetadata?,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    isQueueTrigger: Boolean? = false,
    onPlayNextFromQueue: (() -> Unit)? = null,
    onRemoveFromQueue: (() -> Unit)? = null,
    onShowDetailsDialog: () -> Unit,
    onDismiss: () -> Unit,
) {
    mediaMetadata ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val deviceMusicVolumeController = rememberDeviceMusicVolumeController()
    val onPlayerVolumeChange =
        remember(deviceMusicVolumeController) {
            { volume: Float -> deviceMusicVolumeController.setVolumeFraction(volume) }
        }
    val activityResultLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val librarySong by database.song(mediaMetadata.id).collectAsStateWithLifecycle(initialValue = null)
    val coroutineScope = rememberCoroutineScope()

    val downloadUtil = LocalDownloadUtil.current
    val (songSourceRaw, onSongSourceChange) = rememberPreference(SongSourceOverrideKey, "")
    val currentSongSource =
        remember(songSourceRaw, mediaMetadata.id) {
            SongSourceOverride.get(songSourceRaw.ifBlank { null }, mediaMetadata.id)
        }
    val downloadStateIds =
        remember(mediaMetadata.id, currentSongSource) {
            downloadUtil.currentSourceDownloadIds(mediaMetadata.id)
        }
    val downloadsMap by downloadUtil.downloads.collectAsStateWithLifecycle()
    val download = downloadStateIds.firstNotNullOfOrNull { downloadsMap[it] }

    val artists =
        remember(mediaMetadata.artists) {
            mediaMetadata.artists.filter { it.id != null }
        }

    val (artistSeparators) = rememberPreference(ArtistSeparatorsKey, defaultValue = ",;/&")
    val (externalDownloaderEnabled) = rememberPreference(ExternalDownloaderEnabledKey, defaultValue = false)
    val (externalDownloaderPackage) = rememberPreference(ExternalDownloaderPackageKey, defaultValue = "")
    val (archiveTuneCanvasEnabled) = rememberPreference(ArchiveTuneCanvasKey, defaultValue = true)
    val (spotifyCanvasEnabled) = rememberPreference(SpotifyCanvasKey, defaultValue = false)
    val (spotifySpDc) = rememberPreference(SpotifySpDcKey, defaultValue = "")
    val spotifyCanvasAvailable = spotifyCanvasEnabled || spotifySpDc.isNotBlank()
    val playerDesignStyle by rememberEnumPreference(PlayerDesignStyleKey, defaultValue = PlayerDesignStyle.APPLE_MUSIC)
    val lowDataModeActive = rememberLowDataModeActive()
    val isCanvasArtworkRefetching by playerConnection.isCanvasArtworkRefetching.collectAsStateWithLifecycle()

    var hasCanvasArtwork by remember(mediaMetadata.id) { mutableStateOf(false) }
    LaunchedEffect(mediaMetadata.id, isCanvasArtworkRefetching) {
        hasCanvasArtwork = CanvasArtworkPlaybackCache.hasEntry(mediaMetadata.id)
    }
    val (speedDialSongIds, onSpeedDialSongIdsChange) = rememberPreference(SpeedDialSongIdsKey, "")

    val (songCanvasDisabledRaw, onSongCanvasDisabledChange) = rememberPreference(SongCanvasDisabledKey, "")
    val songCanvasDisabledForCurrent =
        remember(songCanvasDisabledRaw, mediaMetadata.id) {
            SongCanvasDisabled.isDisabled(songCanvasDisabledRaw.ifBlank { null }, mediaMetadata.id)
        }
    val speedDialPins = remember(speedDialSongIds) { parseSpeedDialPins(speedDialSongIds) }
    val songPin = remember(mediaMetadata.id) { SpeedDialPin(type = SpeedDialPinType.SONG, id = mediaMetadata.id) }
    val isInSpeedDial =
        remember(speedDialPins, songPin) {
            speedDialPins.any { it.type == songPin.type && it.id == songPin.id }
        }
    val isLocalMedia =
        remember(librarySong?.song?.isLocal, mediaMetadata.id) {
            librarySong?.song?.isLocal == true || mediaMetadata.id.isLocalMediaId()
        }
    val castPlayerMenuAction = rememberCastPlayerMenuAction()

    data class SplitArtist(
        val name: String,
        val originalArtist: MediaMetadata.Artist?,
    )

    val splitArtists =
        remember(artists, artistSeparators) {
            if (artistSeparators.isEmpty()) {
                artists.map { SplitArtist(it.name, it) }
            } else {
                val separatorRegex = "[${Regex.escape(artistSeparators)}]".toRegex()
                artists.flatMap { artist ->
                    val parts =
                        artist.name
                            .split(separatorRegex)
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                    if (parts.size > 1) {
                        parts.map { name -> SplitArtist(name, artist) }
                    } else {
                        listOf(SplitArtist(artist.name, artist))
                    }
                }
            }
        }

    val artistIdsKey =
        remember(splitArtists) {
            splitArtists.mapNotNull { it.originalArtist?.id }.distinct().sorted()
        }
    val artistThumbnailsByKey: Map<String?, String?> by produceState(
        initialValue = emptyMap(),
        artistIdsKey,
    ) {
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<String?, String?>()
            val nameById =
                splitArtists
                    .mapNotNull { sa ->
                        sa.originalArtist?.id?.let { id -> id to sa.originalArtist.name }
                    }.toMap()

            splitArtists.mapNotNull { it.originalArtist?.id }.distinct().forEach { artistId ->
                val dbEntity = database.getArtistById(artistId)
                val cached = dbEntity?.thumbnailUrl
                if (!cached.isNullOrBlank()) {
                    result[artistId] = cached
                    value = result.toMap()
                } else {
                    val fetched =
                        runCatching { YouTube.artist(artistId) }
                            .getOrNull()
                            ?.getOrNull()
                            ?.artist
                            ?.thumbnail
                    if (!fetched.isNullOrBlank()) {
                        result[artistId] = fetched
                        value = result.toMap()
                        runCatching {
                            database.query {
                                upsert(
                                    ArtistEntity(
                                        id = artistId,
                                        name = dbEntity?.name ?: nameById[artistId].orEmpty(),
                                        thumbnailUrl = fetched,
                                        channelId = dbEntity?.channelId,
                                        lastUpdateTime = dbEntity?.lastUpdateTime ?: LocalDateTime.now(),
                                        bookmarkedAt = dbEntity?.bookmarkedAt,
                                        blockedAt = dbEntity?.blockedAt,
                                        isLocal = dbEntity?.isLocal ?: false,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            database.withTransaction {
                insert(mediaMetadata)
            }
            listOf(mediaMetadata.id)
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        },
        onAddComplete = { songCount, playlistNames ->
            val message =
                when {
                    playlistNames.size == 1 -> context.getString(R.string.added_to_playlist, playlistNames.first())
                    else -> context.getString(R.string.added_to_n_playlists, playlistNames.size)
                }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        },
    )

    var showSourceDialog by rememberSaveable { mutableStateOf(false) }

    val sourceRevision by playerConnection.service.resolvedSourcesRevision.collectAsStateWithLifecycle()
    LaunchedEffect(showSourceDialog, mediaMetadata.id) {
        if (showSourceDialog) {
            playerConnection.service.refreshSourcesForSong(mediaMetadata.id)
        }
    }
    val availableSources =
        remember(mediaMetadata.id, showSourceDialog, sourceRevision) {
            playerConnection.service.availableSourcesForSong(mediaMetadata.id)
                .filter { it != AudioSourceType.APPLE }
        }

    if (showSourceDialog) {
        SongSourceDialog(
            sources = availableSources,
            selected = currentSongSource,
            initialQuery =
                listOf(
                    mediaMetadata.artists.joinToString(" ") { it.name }.trim(),
                    mediaMetadata.title.trim(),
                ).filter { it.isNotEmpty() }.joinToString(" "),
            onDismiss = { showSourceDialog = false },
            onSelect = { source ->
                onSongSourceChange(SongSourceOverride.withOverride(songSourceRaw, mediaMetadata.id, source))
                playerConnection.service.setSongSourceOverride(mediaMetadata.id, source)
                showSourceDialog = false
                onDismiss()
            },
            onPlaySong = { song ->

                playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
            },
            onPlayFromSource = { result ->

                val source = result.source
                val trackId = result.trackId
                if (source != AudioSourceType.YOUTUBE && trackId.isNotBlank()) {
                    onSongSourceChange(
                        SongSourceOverride.withOverride(songSourceRaw, mediaMetadata.id, source),
                    )

                    when (source) {
                        AudioSourceType.QOBUZ ->
                            playerConnection.service.setSongSourceOverrideWithQobuzTrackId(
                                mediaId = mediaMetadata.id,
                                source = source,
                                qobuzTrackId = trackId,
                            )

                        AudioSourceType.QOBUZ_BACKUP ->
                            playerConnection.service.setSongSourceOverrideWithQobuzBackupVideoId(
                                mediaId = mediaMetadata.id,
                                source = source,
                                qobuzBackupVideoId = trackId,
                            )

                        AudioSourceType.DEEZER ->
                            playerConnection.service.setSongSourceOverrideWithDeezerTrackId(
                                mediaId = mediaMetadata.id,
                                source = source,
                                deezerTrackId = trackId,
                            )

                        AudioSourceType.TIDAL ->
                            playerConnection.service.setSongSourceOverrideWithTidalTrackId(
                                mediaId = mediaMetadata.id,
                                source = source,
                                tidalTrackId = trackId,
                            )

                        else ->
                            playerConnection.service.setSongSourceOverride(
                                mediaId = mediaMetadata.id,
                                source = source,
                            )
                    }
                    showSourceDialog = false
                    onDismiss()
                }
            },
        )
    }

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false },
        ) {
            items(splitArtists.distinctBy { it.name }, key = { it.name }) { splitArtist ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = splitArtist.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingContent = {
                        val thumbUrl =
                            splitArtist.originalArtist?.id?.let { id ->
                                artistThumbnailsByKey[id]
                            }
                        if (thumbUrl.isNullOrBlank()) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.music_note),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            AsyncImage(
                                model =
                                    thumbUrl.resize(
                                        width = 200,
                                        height = 200,
                                        ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
                                    ),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                            )
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                splitArtist.originalArtist?.let { artist ->
                                    navController.navigate("artist/${artist.id}")
                                    showSelectArtistDialog = false
                                    playerBottomSheetState.collapseSoft()
                                    onDismiss()
                                }
                            },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    var showSleepTimerSheet by rememberSaveable { mutableStateOf(false) }

    var showEqualizerDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showEqualizerDialog) {
        EqualizerDialog(
            onDismiss = { showEqualizerDialog = false },
            openSystemEqualizer = {
                val intent =
                    Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                        putExtra(
                            AudioEffect.EXTRA_AUDIO_SESSION,
                            playerConnection.localPlayer.audioSessionId,
                        )
                        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                    }
                if (intent.resolveActivity(context.packageManager) != null) {
                    activityResultLauncher.launch(intent)
                }
            },
        )
    }

    var showCanvasSourceDialog by rememberSaveable { mutableStateOf(false) }
    var canvasSources by remember(mediaMetadata.id) { mutableStateOf<List<CanvasSourceOption>>(emptyList()) }
    var canvasSourcesLoading by remember(mediaMetadata.id) { mutableStateOf(false) }
    var canvasSaving by remember(mediaMetadata.id) { mutableStateOf(false) }
    var canvasAvailable by remember(mediaMetadata.id) {
        mutableStateOf(CanvasArtworkPlaybackCache.hasEntry(mediaMetadata.id))
    }
    LaunchedEffect(mediaMetadata.id, archiveTuneCanvasEnabled, spotifyCanvasAvailable, isCanvasArtworkRefetching) {
        if (CanvasArtworkPlaybackCache.hasEntry(mediaMetadata.id)) {
            canvasAvailable = true
            return@LaunchedEffect
        }
        if (isLocalMedia || (!archiveTuneCanvasEnabled && !spotifyCanvasAvailable)) {
            canvasAvailable = false
            return@LaunchedEffect
        }
        val available =
            kotlinx.coroutines.withTimeoutOrNull(4_000L) {
                withContext(Dispatchers.IO) {
                    hasAnyCanvasSource(
                        mediaId = mediaMetadata.id,
                        songTitleRaw = mediaMetadata.title,
                        artistNameRaw = mediaMetadata.artists.firstOrNull()?.name.orEmpty(),
                        storefront = java.util.Locale.getDefault().country.lowercase().ifBlank { "us" },
                        albumTitle = mediaMetadata.album?.title,
                        includeAppleMusic = archiveTuneCanvasEnabled,
                        includeSpotify = spotifyCanvasAvailable,
                    )
                }
            } ?: false
        canvasAvailable = available
    }

    fun loadCanvasSources() {
        if (canvasSourcesLoading || canvasSaving) return
        canvasSourcesLoading = true
        coroutineScope.launch {
            val sources = withContext(Dispatchers.IO) {
                val byUrl = linkedMapOf<String, CanvasSourceOption>()
                val title = mediaMetadata.title
                val artist = mediaMetadata.artists.firstOrNull()?.name.orEmpty()
                val storefront = java.util.Locale.getDefault().country.lowercase().ifBlank { "us" }
                fetchCanvasArtworkForPlayback(
                    songTitleRaw = title,
                    artistNameRaw = artist,
                    storefront = storefront,
                    requireVertical = playerDesignStyle == PlayerDesignStyle.V7,
                    forceRefresh = true,
                    strictIdentity = !isLocalMedia,
                    albumTitle = mediaMetadata.album?.title,
                )?.let { artwork ->
                    artwork.preferredAnimationUrl?.takeIf { it.isNotBlank() }?.let { url ->
                        byUrl[url] = CanvasSourceOption("ArchiveTune / Apple Music", artwork)
                    }
                }
                if (spotifyCanvasAvailable && !isLocalMedia) {
                    runCatching {
                        SpotifyCanvasProvider.getByVideoId(
                            videoId = mediaMetadata.id,
                            songTitle = title,
                            artistName = artist,
                        )
                    }
                        .getOrNull()
                        ?.let { artwork ->
                            artwork.preferredAnimationUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                byUrl.putIfAbsent(url, CanvasSourceOption("Spotify", artwork))
                            }
                        }
                }
                byUrl.values.toList()
            }
            canvasSources = sources
            canvasSourcesLoading = false
            if (sources.isEmpty()) {
                Toast.makeText(context, context.getString(R.string.canvas_unavailable), Toast.LENGTH_SHORT).show()
            } else {
                showCanvasSourceDialog = true
            }
        }
    }

    fun saveCanvasSource(source: CanvasSourceOption) {
        showCanvasSourceDialog = false
        canvasSaving = true
        coroutineScope.launch {
            val saved = withContext(Dispatchers.IO) {
                CanvasArtworkPlaybackCache.save(mediaMetadata.id, source.artwork)
            }
            if (saved) {
                val playable =
                    withContext(Dispatchers.IO) {
                        CanvasArtworkPlaybackCache.getCachedOnlyFast(mediaMetadata.id)
                    }
                if (playable != null) {
                    playerConnection.publishCanvasArtworkUpdate(mediaMetadata.id, playable)
                }
            }
            canvasSaving = false
            Toast.makeText(
                context,
                context.getString(if (saved) R.string.canvas_saved else R.string.canvas_save_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun playCanvasSource(source: CanvasSourceOption) {
        showCanvasSourceDialog = false
        coroutineScope.launch {
            val artwork =
                withContext(Dispatchers.IO) {
                    CanvasArtworkPlaybackCache.replace(mediaMetadata.id, source.artwork)
                }
            playerConnection.publishCanvasArtworkUpdate(mediaMetadata.id, artwork)
            Toast.makeText(
                context,
                context.getString(R.string.canvas_source_selected, source.label),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    if (showCanvasSourceDialog) {
        ListDialog(onDismiss = { showCanvasSourceDialog = false }) {
            item(key = "canvas_source_title") {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.canvas_source_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            items(canvasSources, key = { it.label }) { source ->
                val providerTag = source.artwork.provider ?: source.artwork.inferredProvider()
                val sourceIcon =
                    if (providerTag == CanvasArtwork.PROVIDER_SPOTIFY) {
                        R.drawable.spotify_icon
                    } else {
                        R.drawable.apple_music_icon
                    }
                ListItem(
                    headlineContent = { Text(text = source.label) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(sourceIcon),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    trailingContent = {
                        if (canvasSaving) {
                            CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            IconButton(onClick = { saveCanvasSource(source) }) {
                                Icon(
                                    painter = painterResource(R.drawable.download),
                                    contentDescription = stringResource(R.string.save_canvas),
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable { playCanvasSource(source) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    val nowPlayingTitle =
        remember(mediaMetadata.title) {
            mediaMetadata.title.ifBlank { context.getString(R.string.no_title) }
        }

    val nowPlayingSubtitle =
        remember(mediaMetadata.artists) {
            mediaMetadata.artists.joinToString(separator = " • ") { it.name }
        }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            val thumb = mediaMetadata.thumbnailUrl
            if (thumb.isNullOrBlank()) {
                Box(
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.music_note),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                AsyncImage(
                    model = thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp)),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.now_playing),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = nowPlayingTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
                if (nowPlayingSubtitle.isNotBlank()) {
                    Text(
                        text = nowPlayingSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding =
            PaddingValues(
                start = 0.dp,
                top = 0.dp,
                end = 0.dp,
                bottom = 12.dp,
            ),
    ) {
        if (showSleepTimerSheet) {
            item {
                AppleMusicSleepTimerSheet(
                    sleepTimer = playerConnection.service.sleepTimer,
                    onDismiss = {
                        showSleepTimerSheet = false
                        onDismiss()
                    },
                )
            }
        } else {
        item {
            MenuSurfaceSection {
                NewActionGrid(
                    actions =
                        buildList {
                            if (playerDesignStyle != PlayerDesignStyle.APPLE_MUSIC) {
                                castPlayerMenuAction?.let(::add)
                            }
                            if (!isLocalMedia && !mediaMetadata.isPodcast) {
                                add(
                                    NewAction(
                                        icon = {
                                            Icon(
                                                painter = painterResource(R.drawable.radio),
                                                contentDescription = null,
                                                modifier = Modifier.size(28.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        text = stringResource(R.string.start_radio),
                                        onClick = {
                                            playerConnection.startRadio(mediaMetadata)
                                            onDismiss()
                                        },
                                    ),
                                )
                            }
                            if (
                                !isLocalMedia &&
                                isQueueTrigger != true &&
                                (hasCanvasArtwork || canvasAvailable || songCanvasDisabledForCurrent) &&
                                playerDesignStyle != PlayerDesignStyle.V5
                            ) {
                                add(
                                    NewAction(
                                        icon = {
                                            Icon(
                                                painter = painterResource(
                                                    if (songCanvasDisabledForCurrent) {
                                                        R.drawable.image
                                                    } else {
                                                        R.drawable.hide_image
                                                    },
                                                ),
                                                contentDescription = null,
                                                modifier = Modifier.size(28.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        text = stringResource(
                                            if (songCanvasDisabledForCurrent) {
                                                R.string.enable_canvas
                                            } else {
                                                R.string.disable_canvas
                                            },
                                        ),
                                        onClick = {
                                            onSongCanvasDisabledChange(
                                                SongCanvasDisabled.withDisabled(
                                                    songCanvasDisabledRaw,
                                                    mediaMetadata.id,
                                                    !songCanvasDisabledForCurrent,
                                                ),
                                            )
                                            if (!songCanvasDisabledForCurrent) {
                                                onDismiss()
                                            }
                                        },
                                        enabled = true,
                                    ),
                                )
                            }

                            if (
                                !isLocalMedia &&
                                isQueueTrigger != true &&
                                archiveTuneCanvasEnabled &&
                                !lowDataModeActive &&
                                playerDesignStyle != PlayerDesignStyle.V5 &&
                                hasCanvasArtwork &&
                                !songCanvasDisabledForCurrent
                            ) {
                                add(
                                    NewAction(
                                        icon = {
                                            if (isCanvasArtworkRefetching) {
                                                CircularWavyProgressIndicator(modifier = Modifier.size(28.dp))
                                            } else {
                                                Icon(
                                                    painter = painterResource(R.drawable.sync),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(28.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        text = stringResource(R.string.refetch_canvas),
                                        onClick = {
                                            coroutineScope.launch {
                                                when (
                                                    playerConnection.refetchCanvasArtwork(
                                                        metadata = mediaMetadata,
                                                        requireVertical = playerDesignStyle == PlayerDesignStyle.V7,
                                                    )
                                                ) {
                                                    CanvasArtworkRefetchResult.Success -> onDismiss()
                                                    CanvasArtworkRefetchResult.Failure -> {
                                                        Toast
                                                            .makeText(
                                                                context,
                                                                R.string.canvas_refetch_failed,
                                                                Toast.LENGTH_SHORT,
                                                            ).show()
                                                    }

                                                    CanvasArtworkRefetchResult.AlreadyRunning -> Unit
                                                }
                                            }
                                        },
                                        enabled = !isCanvasArtworkRefetching,
                                    ),
                                )
                            }

                            if (playerDesignStyle !in OnScreenShareStyles) {
                            add(
                                if (isLocalMedia) {
                                    NewAction(
                                        icon = {
                                            Icon(
                                                painter = painterResource(R.drawable.share),
                                                contentDescription = null,
                                                modifier = Modifier.size(28.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        text = stringResource(R.string.share),
                                        onClick = {
                                            shareLocalAudio(context, mediaMetadata.id, librarySong?.format?.mimeType)
                                            onDismiss()
                                        },
                                    )
                                } else {
                                    NewAction(
                                        icon = {
                                            Icon(
                                                painter = painterResource(R.drawable.link),
                                                contentDescription = null,
                                                modifier = Modifier.size(28.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        text = stringResource(R.string.copy_link),
                                        onClick = {
                                            val clipboard =
                                                context.getSystemService(
                                                    android.content.Context.CLIPBOARD_SERVICE,
                                                ) as android.content.ClipboardManager
                                            val clip =
                                                android.content.ClipData.newPlainText(
                                                    context.getString(R.string.copy_link),
                                                    "https://music.youtube.com/watch?v=${mediaMetadata.id}",
                                                )
                                            clipboard.setPrimaryClip(clip)
                                            android.widget.Toast
                                                .makeText(
                                                    context,
                                                    R.string.link_copied,
                                                    android.widget.Toast.LENGTH_SHORT,
                                                ).show()
                                            onDismiss()
                                        },
                                    )
                                },
                            )
                            }
                            if (!isLocalMedia) {
                                add(
                                    NewAction(
                                        icon = {
                                            Icon(
                                                painter = painterResource(R.drawable.tune),
                                                contentDescription = null,
                                                modifier = Modifier.size(28.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        text = stringResource(R.string.source),
                                        onClick = { showSourceDialog = true },
                                    ),
                                )
                            }
                            if (isQueueTrigger != true) {
                                add(
                                    NewAction(
                                        icon = {
                                            Icon(
                                                painter = painterResource(R.drawable.bedtime),
                                                contentDescription = null,
                                                modifier = Modifier.size(28.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        text = stringResource(R.string.aod_mode),
                                        onClick = {
                                            playerConnection.aodModeEnabled.value = true
                                            onDismiss()
                                        },
                                    ),
                                )
                            }
                        },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
        }
        item {
            MenuSectionDivider()
        }

        if (
            !isLocalMedia &&
            isQueueTrigger != true &&
            !lowDataModeActive &&
            playerDesignStyle != PlayerDesignStyle.V5 &&
            canvasAvailable
        ) {
            item {
                MenuSurfaceSection {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.canvas_menu_title)) },
                        leadingContent = {
                            if (canvasSourcesLoading || canvasSaving) {
                                CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.motion_photos_on),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier =
                            Modifier.clickable {
                                loadCanvasSources()
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            item {
                MenuSectionDivider()
            }
        }

        item {
            MenuSurfaceSection {
                Column {
                    if (playerDesignStyle !in OnScreenAddToPlaylistStyles) {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.add_to_playlist)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.playlist_add),
                                contentDescription = null,
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                showChoosePlaylistDialog = true
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp,
                    )
                    }
                    ListItem(
                        headlineContent = {
                            Text(
                                text =
                                    stringResource(
                                        if (isInSpeedDial) {
                                            R.string.remove_from_speed_dial
                                        } else {
                                            R.string.pin_to_speed_dial
                                        },
                                    ),
                            )
                        },
                        leadingContent = {
                            Icon(
                                painter =
                                    painterResource(
                                        if (isInSpeedDial) R.drawable.bookmark_filled else R.drawable.bookmark,
                                    ),
                                contentDescription = null,
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                val updatedPins = toggleSpeedDialPin(speedDialPins, songPin)
                                onSpeedDialSongIdsChange(serializeSpeedDialPins(updatedPins))
                                onDismiss()
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
        item {
            MenuSectionDivider()
        }
        if (splitArtists.isNotEmpty() || mediaMetadata.album != null) {
            item {
                MenuSurfaceSection {
                    Column {
                        if (splitArtists.isNotEmpty()) {
                            ListItem(
                                headlineContent = { Text(text = stringResource(R.string.view_artist)) },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.artist),
                                        contentDescription = null,
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        if (splitArtists.size == 1 && splitArtists[0].originalArtist != null) {
                                            onDismiss()
                                            playerBottomSheetState.snapTo(playerBottomSheetState.collapsedBound)
                                            navController.navigate("artist/${splitArtists[0].originalArtist!!.id}")
                                        } else {
                                            showSelectArtistDialog = true
                                        }
                                    },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }

                        if (splitArtists.isNotEmpty() && mediaMetadata.album != null) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp,
                            )
                        }

                        if (mediaMetadata.album != null) {
                            ListItem(
                                headlineContent = { Text(text = stringResource(R.string.view_album)) },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.album),
                                        contentDescription = null,
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        onDismiss()
                                        playerBottomSheetState.snapTo(playerBottomSheetState.collapsedBound)
                                        navController.navigate("album/${mediaMetadata.album.id}")
                                    },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }
            item {
                MenuSectionDivider()
            }
        }
        if (!isLocalMedia) {
            item {
                MenuSurfaceSection {
                    when (download?.state) {
                        Download.STATE_COMPLETED -> {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = stringResource(R.string.remove_download),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.offline),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        download?.let { dl ->
                                            DownloadService.sendRemoveDownload(
                                                context,
                                                ExoDownloadService::class.java,
                                                dl.request.id,
                                                false,
                                            )
                                        }
                                    },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }

                        Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                            ListItem(
                                headlineContent = { Text(text = stringResource(R.string.downloading)) },
                                leadingContent = {
                                    CircularWavyProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        download?.let { dl ->
                                            DownloadService.sendRemoveDownload(
                                                context,
                                                ExoDownloadService::class.java,
                                                dl.request.id,
                                                false,
                                            )
                                        }
                                    },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }

                        else -> {
                            ListItem(
                                headlineContent = { Text(text = stringResource(R.string.action_download)) },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.download),
                                        contentDescription = null,
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        database.transaction {
                                            insert(mediaMetadata)
                                        }

                                        coroutineScope.launch {
                                            downloadUtil.clearCurrentTargetCacheSpans(mediaMetadata.id)
                                            runCatching {
                                                downloadUtil.prewarmSongForDownload(mediaMetadata.id)
                                            }
                                            val downloadId = downloadUtil
                                                .currentSourceDownloadTarget(mediaMetadata.id).key
                                            val downloadRequest =
                                                DownloadRequest
                                                    .Builder(downloadId, mediaMetadata.id.toUri())
                                                    .setCustomCacheKey(downloadId)
                                                    .setData(mediaMetadata.title.toByteArray())
                                                    .build()
                                            DownloadService.sendAddDownload(
                                                context,
                                                ExoDownloadService::class.java,
                                                downloadRequest,
                                                false,
                                            )
                                        }
                                    },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                    if (externalDownloaderEnabled) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp,
                        )
                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.open_with_downloader)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.download),
                                    contentDescription = null,
                                )
                            },
                            modifier =
                                Modifier.clickable {
                                    onDismiss()
                                    val url = "https://music.youtube.com/watch?v=${mediaMetadata.id}"
                                    if (externalDownloaderPackage.isBlank()) {
                                        Toast
                                            .makeText(
                                                context,
                                                context.getString(R.string.external_downloader_not_configured),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        return@clickable
                                    }
                                    val intent =
                                        android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                            setPackage(externalDownloaderPackage)
                                            data = android.net.Uri.parse(url)
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: android.content.ActivityNotFoundException) {
                                        Toast
                                            .makeText(
                                                context,
                                                context.getString(R.string.external_downloader_not_installed),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    }
                                },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }
        item {
            MenuSurfaceSection {
                Column {
                    if (isQueueTrigger == true && onPlayNextFromQueue != null) {
                        ListItem(
                            headlineContent = {
                                Text(text = stringResource(R.string.play_next))
                            },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.playlist_play),
                                    contentDescription = null,
                                )
                            },
                            modifier =
                                Modifier.clickable {
                                    onPlayNextFromQueue()
                                    onDismiss()
                                },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp,
                        )
                    }

                    if (isQueueTrigger == true && onRemoveFromQueue != null) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.remove_from_queue),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.delete),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            modifier =
                                Modifier.clickable {
                                    onRemoveFromQueue()
                                    onDismiss()
                                },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp,
                        )

                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.play_next)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.playlist_play),
                                    contentDescription = null,
                                )
                            },
                            modifier =
                                Modifier.clickable {
                                    mediaMetadata?.toMediaItem()?.let { playerConnection.playNext(it) }
                                    onDismiss()
                                },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.add_to_queue)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.queue_music),
                                    contentDescription = null,
                                )
                            },
                            modifier =
                                Modifier.clickable {
                                    mediaMetadata?.toMediaItem()?.let { playerConnection.addToQueue(it) }
                                    onDismiss()
                                },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp,
                        )
                    }

                    if (playerDesignStyle !in OnScreenDetailsStyles) {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.details)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.info),
                                contentDescription = null,
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                onShowDetailsDialog()
                                onDismiss()
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    }

                    if (isQueueTrigger != true) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp,
                        )

                        if (playerDesignStyle !in OnScreenSleepTimerStyles) {
                            ListItem(
                                headlineContent = { Text(text = stringResource(R.string.sleep_timer)) },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.bedtime),
                                        contentDescription = null,
                                    )
                                },
                                modifier =
                                    Modifier.clickable { showSleepTimerSheet = true },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp,
                            )
                        }

                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.equalizer)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.equalizer),
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier.clickable { showEqualizerDialog = true },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )

                    }
                }
            }
        }
        }
    }
}

private fun AudioSourceType.sourceLabelRes(): Int =
    when (this) {
        AudioSourceType.TIDAL -> R.string.source_tidal
        AudioSourceType.QOBUZ -> R.string.source_qobuz
        AudioSourceType.QOBUZ_BACKUP -> R.string.source_qobuz_backup
        AudioSourceType.DEEZER -> R.string.source_deezer
        AudioSourceType.APPLE -> R.string.source_apple_music
        AudioSourceType.JIOSAAVN -> R.string.source_jiosaavn
        AudioSourceType.SOUNDCLOUD -> R.string.source_soundcloud
        AudioSourceType.SPOTIFY -> R.string.source_spotify
        AudioSourceType.YOUTUBE -> R.string.source_youtube
    }

private fun AudioSourceType.sourceIconRes(): Int =
    when (this) {
        AudioSourceType.TIDAL -> R.drawable.provider_tidal
        AudioSourceType.QOBUZ -> R.drawable.provider_qobuz
        AudioSourceType.QOBUZ_BACKUP -> R.drawable.provider_qobuz
        AudioSourceType.DEEZER -> R.drawable.provider_deezer
        AudioSourceType.APPLE -> R.drawable.provider_apple

        AudioSourceType.JIOSAAVN -> R.drawable.provider_jiosaavn
        AudioSourceType.SOUNDCLOUD -> R.drawable.solar_music_note_2_linear
        AudioSourceType.SPOTIFY -> R.drawable.spotify_icon
        AudioSourceType.YOUTUBE -> R.drawable.play
    }

private data class SourceSearchResult(
    val source: AudioSourceType,
    val trackId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationMs: Long?,
    val qualityLabel: String?,
    val qualityDetail: String? = null,
    val songItem: SongItem?,
)

private const val SOURCE_SEARCH_INITIAL_LIMIT = 8
private const val SOURCE_SEARCH_PAGE_STEP = 16
private const val SOURCE_SEARCH_MAX_LIMIT = 48

private fun SongItem.toSourceSearchResult(aacLabel: String): SourceSearchResult =
    SourceSearchResult(
        source = AudioSourceType.YOUTUBE,
        trackId = id,
        title = title,
        artist = artists.joinToString(", ") { it.name },
        thumbnailUrl = thumbnail,
        durationMs = duration?.toLong()?.times(1000L),
        qualityLabel = aacLabel,
        songItem = this,
    )

private fun sourceQualityDetail(
    source: AudioSourceType,
    durationMs: Long?,
    deezerLossless: Boolean,
    tidalQuality: TidalAudioQuality = TidalAudioQuality.FLAC,
): String? {
    val khz: String?
    val depth: String?
    val nominalKbps: Int?
    val upTo: Boolean
    when (source) {
        AudioSourceType.YOUTUBE -> {
            khz = null; depth = null; nominalKbps = 256; upTo = false
        }
        AudioSourceType.TIDAL ->
            when (tidalQuality) {
                TidalAudioQuality.AAC_320 -> {
                    khz = null; depth = null; nominalKbps = 320; upTo = false
                }
                TidalAudioQuality.FLAC -> {
                    khz = "44.1 kHz"; depth = "16-bit"; nominalKbps = 1000; upTo = false
                }
                TidalAudioQuality.HI_RES_LOSSLESS -> {
                    khz = "192 kHz"; depth = "24-bit"; nominalKbps = 2500; upTo = true
                }
            }
        AudioSourceType.QOBUZ -> {
            khz = "192 kHz"; depth = "24-bit"; nominalKbps = 2500; upTo = true
        }
        AudioSourceType.QOBUZ_BACKUP -> {
            khz = "44.1 kHz"; depth = "16-bit"; nominalKbps = 1000; upTo = false
        }
        AudioSourceType.DEEZER ->
            if (deezerLossless) {
                khz = "44.1 kHz"; depth = "16-bit"; nominalKbps = 1000; upTo = false
            } else {
                khz = null; depth = null; nominalKbps = 320; upTo = false
            }
        AudioSourceType.APPLE -> {
            khz = null; depth = null; nominalKbps = 256; upTo = false
        }
        AudioSourceType.JIOSAAVN -> {
            khz = null; depth = null; nominalKbps = 320; upTo = false
        }
        else -> return null
    }
    val parts = mutableListOf<String>()
    if (depth != null && khz != null) {
        parts.add(if (upTo) "up to $depth · $khz" else "$depth · $khz")
    }
    if (durationMs != null && durationMs > 0 && nominalKbps != null) {
        val sizeMb = durationMs / 1000.0 * nominalKbps / 8.0 / 1024.0
        if (sizeMb >= 0.1) {
            parts.add("≈ %.1f MB".format(sizeMb))
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun SourceSearchPill(
    label: String,
    iconRes: Int,
    selected: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        },
        trailingIcon = {
            if (isLoading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        },
        shape = RoundedCornerShape(16.dp),
        border = null,
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
    )
}

private suspend fun searchOneSource(
    source: AudioSourceType,
    query: String,
    limit: Int = SOURCE_SEARCH_INITIAL_LIMIT,
    aacLabel: String,
    saavnLabel: String,
    losslessLabel: String,
    deezerLabel: String,
    context: android.content.Context,
    onContinuation: ((String?) -> Unit)? = null,
): List<SourceSearchResult> =
    withContext(Dispatchers.IO) {
        when (source) {
            AudioSourceType.YOUTUBE -> {
                val ytResult =
                    runCatching {
                        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG, useAccountContext = false).getOrNull()
                    }.getOrNull()
                onContinuation?.invoke(ytResult?.continuation)
                val songs =
                    ytResult?.items
                        ?.filterIsInstance<SongItem>()
                        .orEmpty()
                songs.map { song -> song.toSourceSearchResult(aacLabel) }
            }

            AudioSourceType.TIDAL -> {
                val tidalQuery =
                    TidalAudioProvider.Query(
                        mediaId = "",
                        title = query,
                        artists = emptyList(),
                        album = null,
                        isrc = null,
                        durationMs = null,
                    )
                runCatching { TidalAudioProvider.searchCandidates(tidalQuery, limit = limit) }
                    .getOrDefault(emptyList())
                    .map { candidate ->
                        SourceSearchResult(
                            source = AudioSourceType.TIDAL,
                            trackId = candidate.trackId,
                            title = candidate.title,
                            artist = candidate.artist,
                            thumbnailUrl = candidate.thumbnailUrl,
                            durationMs = candidate.durationMs,
                            qualityLabel = losslessLabel,
                            songItem = null,
                        )
                    }
            }

            AudioSourceType.QOBUZ -> {
                runCatching { QobuzAudioProvider.searchCandidates(query, limit = limit) }
                    .getOrDefault(emptyList())
                    .map { candidate ->
                        val thumb = candidate.thumbnailUrl ?: run {
                            val term =
                                listOfNotNull(
                                    candidate.artist?.takeIf(String::isNotBlank),
                                    candidate.title,
                                ).joinToString(" ")
                            val ytResult =
                                YouTube.search(term, YouTube.SearchFilter.FILTER_SONG, useAccountContext = false).getOrNull()
                            ytResult?.items
                                ?.filterIsInstance<SongItem>()
                                ?.firstOrNull()
                                ?.thumbnail
                        }
                        SourceSearchResult(
                            source = AudioSourceType.QOBUZ,
                            trackId = candidate.trackId,
                            title = candidate.title,
                            artist = candidate.artist.orEmpty(),
                            thumbnailUrl = thumb,
                            durationMs = candidate.durationMs,
                            qualityLabel = losslessLabel,
                            songItem = null,
                        )
                    }
            }

            AudioSourceType.QOBUZ_BACKUP -> {
                runCatching {
                    QobuzBackupProvider.configuredEndpoints =
                        context.dataStore.data.first()[QobuzBackupEndpointsKey]
                            .orEmpty()
                            .split('\n')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                }
                runCatching { QobuzBackupProvider.searchCandidates(query, limit = limit) }
                    .getOrDefault(emptyList())
                    .map { candidate ->
                        SourceSearchResult(
                            source = AudioSourceType.QOBUZ_BACKUP,
                            trackId = candidate.videoId,
                            title = candidate.title,
                            artist = candidate.artist.orEmpty(),
                            thumbnailUrl = candidate.thumbnailUrl,
                            durationMs = null,
                            qualityLabel = if (candidate.isLossless) losslessLabel else aacLabel,
                            songItem = null,
                        )
                    }
            }

            AudioSourceType.DEEZER -> {
                runCatching { DeezerAudioProvider.searchCandidates(query, limit = limit) }
                    .getOrDefault(emptyList())
                    .map { candidate ->
                        SourceSearchResult(
                            source = AudioSourceType.DEEZER,
                            trackId = candidate.trackId,
                            title = candidate.title,
                            artist = candidate.artist.orEmpty(),
                            thumbnailUrl = candidate.coverUrl,
                            durationMs = candidate.durationMs,
                            qualityLabel = deezerLabel,
                            songItem = null,
                        )
                    }
            }

            AudioSourceType.APPLE -> {
                emptyList()
            }

            AudioSourceType.JIOSAAVN -> {
                SaavnService.searchSongs(query)
                    .getOrDefault(emptyList())
                    .map { saavnSong ->
                        val cover =
                            saavnSong.image.maxByOrNull {
                                runCatching { it.quality.substringBefore("x").toInt() }.getOrDefault(0)
                            }?.url
                        SourceSearchResult(
                            source = AudioSourceType.JIOSAAVN,
                            trackId = saavnSong.id,
                            title = saavnSong.name,
                            artist = saavnSong.artists.primary.joinToString(", ") { it.name },
                            thumbnailUrl = cover,
                            durationMs = saavnSong.duration?.toLong()?.times(1000L),
                            qualityLabel = saavnLabel,
                            songItem = null,
                        )
                    }
            }

            AudioSourceType.SOUNDCLOUD -> {
                runCatching { SoundCloudAudioProvider.searchMetadata(query, limit) }
                    .getOrDefault(emptyList())
                    .map { track ->
                        SourceSearchResult(
                            source = AudioSourceType.SOUNDCLOUD,
                            trackId = track.permalinkUrl,
                            title = track.title,
                            artist = track.artist,
                            thumbnailUrl = track.artworkUrl,
                            durationMs = track.durationMs,
                            qualityLabel = aacLabel,
                            songItem = null,
                        )
                    }
            }

            AudioSourceType.SPOTIFY -> {
                val token = SpotifyWebSession.ensureToken(context) ?: return@withContext emptyList()
                SpotifyCore.accessToken = token
                val result = runCatching { SpotifyCore.search(query, listOf("track"), limit).getOrNull() }.getOrNull()
                result?.tracks?.items.orEmpty().map { track ->
                    SourceSearchResult(
                        source = AudioSourceType.SPOTIFY,
                        trackId = track.id,
                        title = track.name,
                        artist = track.artists.joinToString(", ") { it.name },
                        thumbnailUrl = track.album?.images?.firstOrNull()?.url,
                        durationMs = track.durationMs.toLong().takeIf { it > 0 },
                        qualityLabel = aacLabel,
                        songItem = null,
                    )
                }
            }
        }
    }

@Composable
private fun SongSourceDialog(
    sources: List<AudioSourceType>,
    selected: AudioSourceType?,
    onDismiss: () -> Unit,
    onSelect: (AudioSourceType?) -> Unit,
    onPlaySong: (SongItem) -> Unit,
    onPlayFromSource: (SourceSearchResult) -> Unit,
    initialQuery: String = "",
) {
    val context = LocalContext.current
    var searchMode by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sourceFilter by rememberSaveable { mutableStateOf<AudioSourceType?>(null) }
    var providerLimit by remember { mutableStateOf(SOURCE_SEARCH_INITIAL_LIMIT) }
    var loadMoreToken by remember { mutableIntStateOf(0) }
    var ytContinuation by remember { mutableStateOf<String?>(null) }
    var loadingMore by remember { mutableStateOf(false) }

    var resultsBySource by remember {
        mutableStateOf<Map<AudioSourceType, List<SourceSearchResult>>>(emptyMap())
    }
    var loadingSources by remember { mutableStateOf<Set<AudioSourceType>>(emptySet()) }

    val aacLabel = stringResource(R.string.quality_badge_aac)
    val saavnLabel = stringResource(R.string.quality_badge_saavn)
    val mp3Label = stringResource(R.string.quality_badge_mp3)
    val losslessLabel = stringResource(R.string.quality_badge_lossless)
    val noResultsText = stringResource(R.string.source_search_no_results)

    val deezerLabel =
        remember(losslessLabel, mp3Label) {
            val availability = DeezerAudioProvider.accountAvailability()
            if (availability.manualPremium || availability.pooledPremium > 0) losslessLabel else mp3Label
        }

    val (sourceOrderRaw, _) = rememberPreference(AudioSourceOrderKey, "")
    val (tidalQualityRaw, _) = rememberPreference(TidalAudioQualityKey, TidalAudioQuality.FLAC.name)
    val tidalQualityPref =
        remember(tidalQualityRaw) {
            runCatching { TidalAudioQuality.valueOf(tidalQualityRaw) }.getOrDefault(TidalAudioQuality.FLAC)
        }
    val sourceOrder =
        remember(sourceOrderRaw) {
            AudioSourceConfig.parseOrder(sourceOrderRaw.ifBlank { null })
        }
    val searchableSources =
        remember(sourceOrder) {
            val eligible =
                listOf(
                    AudioSourceType.YOUTUBE,
                    AudioSourceType.TIDAL,
                    AudioSourceType.QOBUZ,
                    AudioSourceType.QOBUZ_BACKUP,
                    AudioSourceType.DEEZER,
                    AudioSourceType.JIOSAAVN,
                    AudioSourceType.SOUNDCLOUD,
                    AudioSourceType.SPOTIFY,
                )
            val ordered = sourceOrder.filter { it in eligible }
            ordered + eligible.filterNot { it in ordered }
        }
    val orderedSources =
        remember(sources, sourceOrder) {
            val ordered = sourceOrder.filter { it in sources }
            ordered + sources.filterNot { it in ordered }
        }

    LaunchedEffect(searchQuery) {
        providerLimit = SOURCE_SEARCH_INITIAL_LIMIT
        loadMoreToken = 0
        ytContinuation = null
    }

    LaunchedEffect(searchMode, searchQuery, loadMoreToken) {
        if (!searchMode || searchQuery.length < 2) {
            resultsBySource = emptyMap()
            loadingSources = emptySet()
            loadingMore = false
            return@LaunchedEffect
        }
        delay(350L)
        val query = searchQuery
        val firstPage = loadMoreToken == 0
        loadingSources = searchableSources.toSet()
        if (firstPage) {
            resultsBySource = emptyMap()
        }
        coroutineScope {
            searchableSources
                .map { source ->
                    async {
                        if (source == AudioSourceType.YOUTUBE && !firstPage) {
                            val continuation = ytContinuation
                            if (continuation != null) {
                                val more =
                                    runCatching {
                                        YouTube.searchContinuation(continuation).getOrNull()
                                    }.getOrNull()
                                if (more != null) {
                                    ytContinuation = more.continuation
                                    val added =
                                        more.items
                                            .filterIsInstance<SongItem>()
                                            .map { song -> song.toSourceSearchResult(aacLabel) }

                                    currentCoroutineContext().ensureActive()
                                    resultsBySource =
                                        resultsBySource + (
                                            source to
                                                (resultsBySource[source].orEmpty() + added)
                                                    .distinctBy { it.trackId }
                                        )
                                }
                            }
                        } else {
                            val results =
                                runCatching {
                                    searchOneSource(
                                        source = source,
                                        query = query,
                                        limit = providerLimit,
                                        aacLabel = aacLabel,
                                        saavnLabel = saavnLabel,
                                        losslessLabel = losslessLabel,
                                        deezerLabel = deezerLabel,
                                        context = context,
                                        onContinuation = { c -> ytContinuation = c },
                                    )
                                }.getOrDefault(emptyList())
                            currentCoroutineContext().ensureActive()
                            resultsBySource = resultsBySource + (source to results)
                        }
                        currentCoroutineContext().ensureActive()
                        loadingSources = loadingSources - source
                    }
                }
                .awaitAll()
        }
        loadingMore = false
    }

    val results =
        remember(resultsBySource, sourceFilter, searchableSources, tidalQualityPref) {
            searchableSources
                .flatMap { source -> resultsBySource[source].orEmpty() }
                .filter { sourceFilter == null || it.source == sourceFilter }
                .map { result ->
                    result.copy(
                        qualityDetail = sourceQualityDetail(
                            source = result.source,
                            durationMs = result.durationMs,
                            deezerLossless = deezerLabel == losslessLabel,
                            tidalQuality = tidalQualityPref,
                        ),
                    )
                }
                .distinctBy { "${it.source.name}:${it.trackId}" }
        }
    val filterLoading =
        sourceFilter?.let { it in loadingSources } ?: loadingSources.isNotEmpty()

    val resultListState = rememberLazyListState()
    val canLoadMore = providerLimit < SOURCE_SEARCH_MAX_LIMIT || ytContinuation != null

    fun loadMore() {
        if (loadingMore) return
        if (providerLimit >= SOURCE_SEARCH_MAX_LIMIT && ytContinuation == null) return
        loadingMore = true
        providerLimit = (providerLimit + SOURCE_SEARCH_PAGE_STEP).coerceAtMost(SOURCE_SEARCH_MAX_LIMIT)
        loadMoreToken++
    }

    LaunchedEffect(resultListState, results.size) {
        snapshotFlow {
            val last = resultListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            results.isNotEmpty() && last >= results.size - 4
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd) loadMore()
        }
    }

    DefaultDialog(
        onDismiss = onDismiss,
        buttons = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    ) {
        Column(modifier = Modifier.padding(top = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.play_from),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        if (!searchMode) {
                            searchQuery = initialQuery
                            sourceFilter = null
                            searchMode = true
                        } else {
                            searchMode = false
                            searchQuery = ""
                            sourceFilter = null
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = stringResource(R.string.download_source_search),
                        tint = if (searchMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (searchMode) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.download_source_search_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                )
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.width(12.dp))
                    SourceSearchPill(
                        label = stringResource(R.string.source_search_filter_all),
                        iconRes = R.drawable.search,
                        selected = sourceFilter == null,
                        isLoading = false,
                        onClick = { sourceFilter = null },
                    )
                    searchableSources.forEach { source ->
                        Spacer(Modifier.width(8.dp))
                        SourceSearchPill(
                            label = stringResource(source.sourceLabelRes()),
                            iconRes = source.sourceIconRes(),
                            selected = sourceFilter == source,
                            isLoading = source in loadingSources,
                            onClick = { sourceFilter = source },
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }
                when {
                    searchQuery.length < 2 -> {
                        Text(
                            text = noResultsText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        )
                    }
                    results.isEmpty() && filterLoading -> {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.source_search_searching),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    results.isEmpty() -> {
                        Text(
                            text = noResultsText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        )
                    }
                    else -> {
                        LazyColumn(
                            state = resultListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                        ) {
                            items(results, key = { result -> "${result.source.name}:${result.trackId}" }) { result ->
                                SourceSearchResultRow(
                                    result = result,
                                    showSourceIcon = sourceFilter == null,
                                ) {
                                    if (result.songItem != null) {
                                        onPlaySong(result.songItem)
                                    } else {
                                        onPlayFromSource(result)
                                    }
                                    onDismiss()
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            }
                            if (canLoadMore) {
                                item(key = "source-search-load-more") {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = !loadingMore) { loadMore() }
                                                .padding(vertical = 10.dp),
                                    ) {
                                        if (loadingMore) {
                                            CircularProgressIndicator(
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Text(
                                            text = stringResource(R.string.load_more),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                SongSourceRow(
                    iconRes = R.drawable.tune,
                    label = stringResource(R.string.play_from_automatic),
                    checked = selected == null,
                    onClick = { onSelect(null) },
                )
                orderedSources.forEach { source ->
                    SongSourceRow(
                        iconRes = source.sourceIconRes(),
                        label = stringResource(source.sourceLabelRes()),
                        checked = selected == source,
                        onClick = { onSelect(source) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceSearchResultRow(
    result: SourceSearchResult,
    showSourceIcon: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!result.thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = result.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
            )
            val subtitle =
                buildString {
                    append(result.artist)
                    result.durationMs?.let { ms ->
                        val totalSec = ms / 1000
                        val mm = totalSec / 60
                        val ss = totalSec % 60
                        append(" · ").append("%d:%02d".format(mm, ss))
                    }
                }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                )
            }
            if (!result.qualityDetail.isNullOrBlank()) {
                Text(
                    text = result.qualityDetail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                )
            }
        }
        if (!result.qualityLabel.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = result.qualityLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        if (showSourceIcon) {
            Icon(
                painter = painterResource(result.source.sourceIconRes()),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SongSourceRow(
    iconRes: Int,
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = checked, onClick = onClick)
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 12.dp).size(24.dp),
        )
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

private val OnScreenShareStyles =
    setOf(PlayerDesignStyle.V4, PlayerDesignStyle.TIKTOK, PlayerDesignStyle.SPATIALFLOW)
private val OnScreenAddToPlaylistStyles =
    setOf(PlayerDesignStyle.V10, PlayerDesignStyle.TIKTOK, PlayerDesignStyle.SIMPMUSIC)
private val OnScreenDetailsStyles =
    setOf(PlayerDesignStyle.SIMPMUSIC, PlayerDesignStyle.APPLE_MUSIC)
private val OnScreenSleepTimerStyles =
    setOf(
        PlayerDesignStyle.V5,
        PlayerDesignStyle.V9,
        PlayerDesignStyle.V10,
        PlayerDesignStyle.APPLE_MUSIC,
    )
