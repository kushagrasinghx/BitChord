package com.music.bitchord.ui.player

import androidx.media3.common.MediaController
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata as Media3Metadata
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.PlayerDesignStyle
import com.music.bitchord.playback.beginRadioQueue
import com.music.bitchord.playback.playSongs
import com.music.bitchord.playback.toMediaItem
import com.music.bitchord.ui.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.bridge.ArchiveTuneHostBridge
import moe.rukamori.archivetune.constants.PlayerButtonsStyleKey
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.constants.SliderStyleKey
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.BridgedPreferences

/**
 * The app's answers to the ported ArchiveTune player: BitChord's controller
 * for playback, the view model for lyrics and likes, the app's settings for
 * every preference both players share.
 */
class ArchiveTuneBridgeImpl(
    private val controller: MediaController,
    private val viewModel: MainViewModel,
    @Suppress("unused") scope: CoroutineScope,
    private val openArtist: (String) -> Unit,
    private val openAlbum: (String) -> Unit,
    private val openSearch: () -> Unit,
) : ArchiveTuneHostBridge {
    /** Controller commands ride the main looper, as MediaController requires. */
    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** The lyrics translated into ArchiveTune's LRC-shaped store format. */
    private val bridgedLyrics = MutableStateFlow<LyricsEntity?>(null)

    init {
        installPreferenceAdapters()

        // Follow the app's lyrics and re-emit them in ArchiveTune's format.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            viewModel.lyrics.collect { lines ->
                val id = controller.currentMediaItem?.mediaId
                bridgedLyrics.value =
                    if (lines == null || id == null) {
                        null
                    } else {
                        LyricsEntity(
                            id = id,
                            lyrics = lines.toArchiveTuneLrc(),
                            source = LyricsEntity.Source.REMOTE.value,
                            providerName = viewModel.lyricsSource.value.label,
                        )
                    }
            }
        }
    }

    override fun player(): androidx.media3.common.Player = controller

    override fun pauseFromSleepTimer() {
        controller.pause()
    }

    override fun playItems(items: List<MediaItem>, startIndex: Int) {
        main.launch {
            controller.playSongs(items.map { it.toNeiroSong() }, startIndex)
        }
    }

    override fun playNext(items: List<MediaItem>) {
        enqueue(items) { at -> at }
    }

    override fun addToQueue(items: List<MediaItem>) {
        enqueue(items) { controller.mediaItemCount }
    }

    override fun addAfterQueueIndex(index: Int, items: List<MediaItem>) {
        enqueue(items) { at -> (at + index + 1).coerceIn(0, controller.mediaItemCount) }
    }

    private fun enqueue(items: List<MediaItem>, where: (Int) -> Int) {
        val songs = items.map { it.toNeiroSong() }
        if (songs.isEmpty()) return
        main.launch {
            val base = controller.currentMediaItemIndex
            controller.addMediaItems(where(base), songs.map { it.toMediaItem() })
        }
    }

    override fun startRadio(seed: MediaMetadata) {
        main.launch {
            if (controller.currentMediaItem?.mediaId != seed.id) {
                controller.playSongs(listOf(seed.toNeiroSong()), 0)
            }
            controller.beginRadioQueue()
        }
    }

    override fun toggleLike() {
        controller.currentMediaItem?.mediaId?.let(viewModel::toggleLike)
    }

    override fun likeSong(mediaId: String, liked: Boolean) {
        viewModel.toggleLike(mediaId)
    }

    override fun stopAndClearPlayback() {
        controller.clearMediaItems()
        controller.stop()
    }

    override fun song(mediaId: String): SongEntity? {
        val item = controller.currentMediaItem ?: return null
        if (item.mediaId != mediaId) return null
        val meta = item.mediaMetadata ?: return null
        return SongEntity(
            id = mediaId,
            title = meta.title?.toString() ?: "",
            duration = meta.durationMs?.div(1000L)?.toInt() ?: -1,
            thumbnailUrl = meta.artworkUri?.toString(),
            liked = viewModel.likeStatuses.value[mediaId] == LikeStatus.LIKED,
        )
    }

    override fun currentLyrics(): StateFlow<LyricsEntity?> = bridgedLyrics

    override fun playlists(): List<PlaylistEntity> =
        viewModel.playlists.value.map { playlist ->
            PlaylistEntity(
                id = playlist.playlistId,
                name = playlist.title,
                browseId = playlist.browseId,
                thumbnailUrl = playlist.thumbnailUrl,
            )
        }

    override fun addToPlaylist(playlistId: String, mediaId: String) {
        val playlist = viewModel.playlists.value.firstOrNull { it.playlistId == playlistId } ?: return
        val song =
            controller.currentMediaItem?.takeIf { it.mediaId == mediaId }?.toNeiroSong()
                ?: Song(videoId = mediaId, title = "", artist = "", thumbnailUrl = null)
        viewModel.addToPlaylists(listOf(playlist), song)
    }

    override fun download(metadata: MediaMetadata) {
        // Downloads stay this app's own flow; nothing to hand over yet.
    }

    override fun openArtist(browseId: String) = openArtist(browseId)

    override fun openAlbum(browseId: String) = openAlbum(browseId)

    override fun openSearch() = openSearch()
}

/**
 * Media items the ported player resolved back into app songs. The full
 * fidelity path is the queue (the controller's own items round-trip through
 * its own metadata); what the player hands over carries at least the id,
 * title, artist and artwork, which is what queuing needs.
 */
private fun MediaItem.toNeiroSong(): Song {
    val meta: Media3Metadata = mediaMetadata ?: Media3Metadata.Builder().build()
    return Song(
        videoId = mediaId,
        title = meta.title?.toString() ?: "",
        artist = meta.artist?.toString() ?: "",
        thumbnailUrl = meta.artworkUri?.toString(),
    )
}

/**
 * The app's lyric lines rendered into the LRC dialect ArchiveTune's player
 * parses: `[mm:ss.xx]` per line, `<mm:ss.xx>` per word where the app has
 * word timings.
 */
internal fun List<LyricLine>.toArchiveTuneLrc(): String =
    joinToString("\n") { line ->
        val stamp = formatLrcTime(line.timeMs)
        if (line.words.isEmpty()) {
            "$stamp ${line.text}".trimEnd()
        } else {
            val words =
                line.words.joinToString("") { word ->
                    "<${formatLrcTime(word.startMs)}>${word.text}"
                }
            "$stamp $words".trimEnd()
        }
    }

private fun formatLrcTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hundredths = (ms % 1000) / 10
    return "[%d:%02d.%02d]".format(minutes, seconds, hundredths)
}

/**
 * The preference adapters that keep the two apps' settings one source of
 * truth: the design style (whose enum names differ), and the slider and
 * button styles the earlier appearance round already ported.
 */
private fun ArchiveTuneBridgeImpl.installPreferenceAdapters() {
    // Design style: ArchiveTune's V-names map onto this app's descriptive
    // names; everything else is one-to-one.
    fun toAt(style: PlayerDesignStyle): moe.rukamori.archivetune.constants.PlayerDesignStyle =
        when (style) {
            PlayerDesignStyle.BITCHORD -> moe.rukamori.archivetune.constants.PlayerDesignStyle.BITCHORD
            PlayerDesignStyle.CINEMATIC -> moe.rukamori.archivetune.constants.PlayerDesignStyle.V4
            PlayerDesignStyle.LITTLE -> moe.rukamori.archivetune.constants.PlayerDesignStyle.V5
            PlayerDesignStyle.IMMERSIVE -> moe.rukamori.archivetune.constants.PlayerDesignStyle.V7
            PlayerDesignStyle.MATERIAL_EXTENDED -> moe.rukamori.archivetune.constants.PlayerDesignStyle.V9
            PlayerDesignStyle.EDITORIAL -> moe.rukamori.archivetune.constants.PlayerDesignStyle.V10
            PlayerDesignStyle.APPLE_MUSIC -> moe.rukamori.archivetune.constants.PlayerDesignStyle.APPLE_MUSIC
            PlayerDesignStyle.TIKTOK -> moe.rukamori.archivetune.constants.PlayerDesignStyle.TIKTOK
            PlayerDesignStyle.SIMPMUSIC -> moe.rukamori.archivetune.constants.PlayerDesignStyle.SIMPMUSIC
            PlayerDesignStyle.SPATIALFLOW -> moe.rukamori.archivetune.constants.PlayerDesignStyle.SPATIALFLOW
            PlayerDesignStyle.LOOPER -> moe.rukamori.archivetune.constants.PlayerDesignStyle.LOOPER
        }

    fun fromAt(style: moe.rukamori.archivetune.constants.PlayerDesignStyle): PlayerDesignStyle =
        when (style) {
            moe.rukamori.archivetune.constants.PlayerDesignStyle.BITCHORD -> PlayerDesignStyle.BITCHORD
            moe.rukamori.archivetune.constants.PlayerDesignStyle.V4 -> PlayerDesignStyle.CINEMATIC
            moe.rukamori.archivetune.constants.PlayerDesignStyle.V5 -> PlayerDesignStyle.LITTLE
            moe.rukamori.archivetune.constants.PlayerDesignStyle.V7 -> PlayerDesignStyle.IMMERSIVE
            moe.rukamori.archivetune.constants.PlayerDesignStyle.V9 -> PlayerDesignStyle.MATERIAL_EXTENDED
            moe.rukamori.archivetune.constants.PlayerDesignStyle.V10 -> PlayerDesignStyle.EDITORIAL
            moe.rukamori.archivetune.constants.PlayerDesignStyle.APPLE_MUSIC -> PlayerDesignStyle.APPLE_MUSIC
            moe.rukamori.archivetune.constants.PlayerDesignStyle.TIKTOK -> PlayerDesignStyle.TIKTOK
            moe.rukamori.archivetune.constants.PlayerDesignStyle.SIMPMUSIC -> PlayerDesignStyle.SIMPMUSIC
            moe.rukamori.archivetune.constants.PlayerDesignStyle.SPATIALFLOW -> PlayerDesignStyle.SPATIALFLOW
            moe.rukamori.archivetune.constants.PlayerDesignStyle.LOOPER -> PlayerDesignStyle.LOOPER
        }

    BridgedPreferences.bridgeEnum(
        key = PlayerDesignStyleKey,
        read = { toAt(AppSettings.playerDesignStyle.value) },
        write = { AppSettings.setPlayerDesignStyle(fromAt(it)) },
        changes = AppSettings.playerDesignStyle.map(::toAt).distinctUntilChanged(),
    )

    fun toAtSlider(style: com.music.bitchord.data.settings.PlayerSliderStyle): moe.rukamori.archivetune.constants.SliderStyle =
        when (style) {
            com.music.bitchord.data.settings.PlayerSliderStyle.STANDARD -> moe.rukamori.archivetune.constants.SliderStyle.Standard
            com.music.bitchord.data.settings.PlayerSliderStyle.WAVY -> moe.rukamori.archivetune.constants.SliderStyle.Wavy
            com.music.bitchord.data.settings.PlayerSliderStyle.THICK -> moe.rukamori.archivetune.constants.SliderStyle.Thick
            com.music.bitchord.data.settings.PlayerSliderStyle.CIRCULAR -> moe.rukamori.archivetune.constants.SliderStyle.Circular
            com.music.bitchord.data.settings.PlayerSliderStyle.SIMPLE -> moe.rukamori.archivetune.constants.SliderStyle.Simple
        }

    fun fromAtSlider(style: moe.rukamori.archivetune.constants.SliderStyle): com.music.bitchord.data.settings.PlayerSliderStyle =
        when (style) {
            moe.rukamori.archivetune.constants.SliderStyle.Standard -> com.music.bitchord.data.settings.PlayerSliderStyle.STANDARD
            moe.rukamori.archivetune.constants.SliderStyle.Wavy -> com.music.bitchord.data.settings.PlayerSliderStyle.WAVY
            moe.rukamori.archivetune.constants.SliderStyle.Thick -> com.music.bitchord.data.settings.PlayerSliderStyle.THICK
            moe.rukamori.archivetune.constants.SliderStyle.Circular -> com.music.bitchord.data.settings.PlayerSliderStyle.CIRCULAR
            moe.rukamori.archivetune.constants.SliderStyle.Simple -> com.music.bitchord.data.settings.PlayerSliderStyle.SIMPLE
        }

    BridgedPreferences.bridgeEnum(
        key = SliderStyleKey,
        read = { toAtSlider(AppSettings.playerSliderStyle.value) },
        write = { AppSettings.setPlayerSliderStyle(fromAtSlider(it)) },
        changes = AppSettings.playerSliderStyle.map(::toAtSlider).distinctUntilChanged(),
    )

    fun toAtButtons(style: com.music.bitchord.data.settings.PlayerButtonsStyle): moe.rukamori.archivetune.constants.PlayerButtonsStyle =
        moe.rukamori.archivetune.constants.PlayerButtonsStyle.valueOf(style.name)

    fun fromAtButtons(style: moe.rukamori.archivetune.constants.PlayerButtonsStyle): com.music.bitchord.data.settings.PlayerButtonsStyle =
        com.music.bitchord.data.settings.PlayerButtonsStyle.valueOf(style.name)

    BridgedPreferences.bridgeEnum(
        key = PlayerButtonsStyleKey,
        read = { toAtButtons(AppSettings.playerButtonsStyle.value) },
        write = { AppSettings.setPlayerButtonsStyle(fromAtButtons(it)) },
        changes = AppSettings.playerButtonsStyle.map(::toAtButtons).distinctUntilChanged(),
    )
}
