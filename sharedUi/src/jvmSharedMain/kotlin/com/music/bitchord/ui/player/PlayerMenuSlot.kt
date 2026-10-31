package com.music.bitchord.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The lyrics controls the player's own three-dot menu draws in place of the
 * lyric pane's bar: the translation and romanization toggles and the source
 * line, as plain values so the host can lay them out without knowing how the
 * sheet keeps them.
 */
class PlayerLyricsMenu(
    /** False while there are no lyrics to run either toggle over. */
    val translationEnabled: Boolean,
    val translationActive: Boolean,
    /** A request is in flight; the toggle is inert until it lands. */
    val translationWorking: Boolean,
    val romanizationEnabled: Boolean,
    val romanizationActive: Boolean,
    val romanizationWorking: Boolean,
    /** The provider the current lyrics came from, or null while unknown. */
    val sourceLabel: String?,
    val onToggleTranslation: () -> Unit,
    val onToggleRomanization: () -> Unit,
    val onOpenProviders: () -> Unit,
)

/**
 * What [NowPlayingScreen] hands its host for the player's own menu. The screen
 * fills it as its lyrics state changes; the host reads it when the menu is
 * composed, so the rows stay live while the menu is up.
 */
class PlayerMenuSlot {
    var lyrics by mutableStateOf<PlayerLyricsMenu?>(null)
}
