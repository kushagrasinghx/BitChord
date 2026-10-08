package com.music.bitchord.ui.tv.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.music.bitchord.playback.PlaybackPosition

/**
 * Reads [position] and hands the value to [content]. Being its own composable,
 * the read — and so every playhead tick — recomposes this scope and nothing
 * above it.
 */
@Composable
fun PlaybackPositionScope(
    position: () -> Long,
    content: @Composable (Long) -> Unit,
) {
    content(position())
}

/**
 * The live [PlaybackPosition] when the caller has one, otherwise one fed from a
 * bare millisecond reading — for screens that were only ever handed a number.
 */
@Composable
fun rememberTvPlaybackPosition(position: PlaybackPosition?, positionMs: Long): PlaybackPosition {
    if (position != null) return position
    val fallback = remember { PlaybackPosition() }
    LaunchedEffect(positionMs) { fallback.report(positionMs) }
    return fallback
}
