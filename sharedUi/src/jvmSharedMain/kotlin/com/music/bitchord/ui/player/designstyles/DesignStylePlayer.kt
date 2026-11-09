package com.music.bitchord.ui.player.designstyles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.request.ImageRequest
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.PlayerDesignStyle
import com.music.bitchord.data.settings.PlayerSliderStyle
import com.music.bitchord.ui.haptics.Haptics
import com.music.bitchord.ui.player.LyricPlayhead
import com.music.bitchord.ui.player.LyricsPicker
import com.music.bitchord.ui.player.LyricsTranslationUi
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.ui.player.PlayerSettings
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Everything a ported design style is handed, in one bundle: the track, the
 * playback state, every callback the host wired, and the pieces the birchord
 * player owns the same way — the scrub, the lyric kit, the artwork request.
 *
 * A plain holder rather than a scope: nothing here outlives the composition
 * that made it, and every field is read, never written.
 */
@Immutable
internal class DesignStylePlayerData(
    val song: Song,
    val playedBy: String?,
    val isPlaying: Boolean,
    val isLoading: Boolean,
    val audioVersionSwitching: Boolean,
    val position: PlaybackPosition,
    val durationMs: Long,
    val shown: () -> Float,
    val onScrub: (Float) -> Unit,
    val onScrubFinished: () -> Unit,
    val onSeekFraction: (Float) -> Unit,
    val onSeek: (Long) -> Unit,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onBlockedControl: () -> Unit,
    val controlsLocked: Boolean,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val queue: List<Song>,
    val queueIndex: Int,
    val onJumpTo: (Int) -> Unit,
    val onRemoveFromQueue: (Int) -> Unit,
    val onMoveInQueue: (Int, Int) -> Unit,
    val onClearQueue: () -> Unit,
    val onQueueDragActiveChange: (Boolean) -> Unit,
    val shuffleEnabled: Boolean,
    val repeatMode: Int,
    val autoplayEnabled: Boolean,
    val onToggleShuffle: () -> Unit,
    val onCycleRepeat: () -> Unit,
    val onToggleAutoplay: () -> Unit,
    val likeStatus: LikeStatus,
    val onToggleLike: () -> Unit,
    val onOpenMenu: () -> Unit,
    val onOpenAlbum: (String) -> Unit,
    val onOpenArtist: (String?, String) -> Unit,
    val onOpenOutput: () -> Unit,
    val onListenTogether: () -> Unit,
    val artRequest: ImageRequest,
    val remoteArt: String?,
    val canvas: CanvasArtwork?,
    val lyrics: List<LyricLine>?,
    val subLines: List<LyricLine>?,
    val displayedLyrics: List<LyricLine>,
    val lyricsPlayhead: LyricPlayhead,
    val seekToLyric: (Long) -> Unit,
    val lyricsUnavailable: Boolean,
    val lyricsTranslation: LyricsTranslationUi,
    val lyricPicker: LyricsPicker,
    val lyricsShareEnabled: Boolean,
    val volumeValue: () -> Float,
    val onVolumeChange: (Float) -> Unit,
    val onVolumeChangeFinished: () -> Unit,
    val haptics: Haptics,
    val onClose: () -> Unit,
    val onLiveCanvasChanged: (Boolean) -> Unit,
)

/**
 * The state a ported style's lyrics and queue overlays share: whether each is
 * up, and the one-at-a-time rule between them. Not persisted — the birchord
 * player's restore belongs to its own collapse system, which these layouts
 * stand outside of.
 */
internal class DesignStylePanelState {
    var lyricsOpen by mutableStateOf(false)
    var queueOpen by mutableStateOf(false)
    var lyricsScrolling by mutableStateOf(false)
    var queueScrolling by mutableStateOf(false)

    fun toggleLyrics() {
        lyricsOpen = !lyricsOpen
        if (lyricsOpen) queueOpen = false
    }

    fun toggleQueue() {
        queueOpen = !queueOpen
        if (queueOpen) lyricsOpen = false
    }
}

/** The ported player's entry: dispatches to whichever design is in force. */
@Composable
internal fun DesignStylePlayerContent(
    data: DesignStylePlayerData,
    windowWidth: Dp,
    windowHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val style by PlayerSettings.playerDesignStyle.collectAsStateWithLifecycle()
    val colors = rememberDesignStyleColors(data.remoteArt)
    val panels = remember { DesignStylePanelState() }
    val sliderStyle by PlayerSettings.playerSliderStyle.collectAsStateWithLifecycle()
    val reduceAnimation by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()

    // Landscape holds the portrait column centred, as ArchiveTune's V10 does:
    // each style is a portrait composition, and a wide window frames it rather
    // than re-flowing it.
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
            when (style) {
                PlayerDesignStyle.CINEMATIC -> CinematicPlayerContent(data, colors, panels, sliderStyle, reduceAnimation)
                PlayerDesignStyle.LITTLE -> LittlePlayerContent(data, colors, panels, reduceAnimation)
                PlayerDesignStyle.IMMERSIVE -> ImmersivePlayerContent(data, colors, panels, reduceAnimation)
                PlayerDesignStyle.MATERIAL_EXTENDED -> MaterialExtendedPlayerContent(data, colors, panels, sliderStyle, reduceAnimation)
                PlayerDesignStyle.EDITORIAL -> EditorialPlayerContent(data, colors, panels, reduceAnimation)
                PlayerDesignStyle.APPLE_MUSIC -> AppleMusicPlayerContent(data, colors, panels, reduceAnimation)
                PlayerDesignStyle.TIKTOK -> TikTokPlayerContent(data, colors, panels, reduceAnimation, windowWidth, windowHeight)
                PlayerDesignStyle.SIMPMUSIC -> SimpMusicPlayerContent(data, colors, panels, reduceAnimation)
                PlayerDesignStyle.SPATIALFLOW -> SpatialFlowPlayerContent(data, colors, panels, reduceAnimation)
                PlayerDesignStyle.LOOPER -> LooperPlayerContent(data, colors, panels, reduceAnimation)
                PlayerDesignStyle.BITCHORD -> Unit
            }
        }
    }
}

/** The sung line right now, for the strips the feed and the page styles run. */
internal fun currentLyricText(lines: List<LyricLine>, positionMs: Long): String? =
    lines.lastOrNull { it.timeMs <= positionMs + 200L && it.text.isNotBlank() }?.text
