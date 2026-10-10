package com.music.bitchord.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.playback.PlaybackPosition

/**
 * [LyricsPanel] for a screen in another module — the Android TV app's — that
 * only holds a [PlaybackPosition]: builds the lyric playhead from it, so the
 * internal playhead type never has to leave this module.
 */
@Composable
fun SyncedLyricsPanel(
    lines: List<LyricLine>,
    trackKey: String,
    position: PlaybackPosition,
    looking: Boolean,
    isPlaying: Boolean,
    onSeekToLine: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LyricsPanel(
        lines = lines,
        trackKey = trackKey,
        playhead = rememberLyricPlayhead(position),
        looking = looking,
        isPlaying = isPlaying,
        onSeekToLine = onSeekToLine,
        controlsOpen = true,
        onRevealControls = {},
        onHideControls = {},
        modifier = modifier,
    )
}
