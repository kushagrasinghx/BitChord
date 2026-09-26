package com.music.bitchord.desktop

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Which of the app's dialogs, sheets and full-page overlays are showing.
 *
 * One object rather than a score of loose booleans in the app composable. They were nineteen locals
 * that only ever appeared in pairs — a flag and the `if (flag) Dialog(...)` that reads it — and
 * collecting them names the thing they are collectively: what is on top of the page right now.
 */
@Stable
internal class DesktopOverlays {

    /** The signal-chain readout behind the player's quality badge. */
    var pipeline by mutableStateOf(false)

    /** Which device playback is sent to. */
    var audioOutput by mutableStateOf(false)

    /** The party this device is listening with. */
    var listenTogether by mutableStateOf(false)
    var songMenu by mutableStateOf(false)
    var downloadManager by mutableStateOf(false)
    var replay by mutableStateOf(false)
    var playlistDialog by mutableStateOf(false)
    var rename by mutableStateOf(false)
    var delete by mutableStateOf(false)
    var nowPlaying by mutableStateOf(false)
    var settings by mutableStateOf(false)
    var spotifyCanvasSetup by mutableStateOf(false)
    var lyricsSources by mutableStateOf(false)
    var translationLanguage by mutableStateOf(false)
    var equalizer by mutableStateOf(false)
    var lastfmLogin by mutableStateOf(false)
    var listenBrainzToken by mutableStateOf(false)
    var discordToken by mutableStateOf(false)
    var integrations by mutableStateOf(false)
    var accounts by mutableStateOf(false)
    var signIn by mutableStateOf(false)
}
