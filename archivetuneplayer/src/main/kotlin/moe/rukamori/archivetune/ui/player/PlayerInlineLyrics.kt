/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.lyrics.LyricsEntry
import moe.rukamori.archivetune.lyrics.LyricsUtils
import moe.rukamori.archivetune.playback.PlayerConnection
import androidx.compose.runtime.getValue

@Composable
fun rememberInlineLyricLines(playerConnection: PlayerConnection): List<LyricsEntry> {
    val entity by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val text = entity?.lyrics?.trim()?.takeIf { it.isNotBlank() }
    return remember(text) {
        when {
            text == null -> emptyList()
            LyricsUtils.isTtml(text) ->
                LyricsUtils.parseTtml(text, playerConnection.player.duration.takeIf { it > 0 }?.toInt())
            LyricsUtils.isLineSyncedLrc(text) -> LyricsUtils.parseLyrics(text)
            else -> emptyList()
        }
    }
}
