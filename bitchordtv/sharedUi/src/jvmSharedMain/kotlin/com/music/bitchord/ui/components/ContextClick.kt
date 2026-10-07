package com.music.bitchord.ui.components

import androidx.compose.ui.Modifier

/**
 * The secondary button, for whatever a long press opens.
 *
 * A phone holds a card to get its menu; a window right-clicks it, and nobody
 * reaching for a context menu with a mouse thinks to hold the left button down
 * instead. Nothing on the phone, which has no secondary button to press.
 */
expect fun Modifier.contextClick(onClick: (() -> Unit)?): Modifier
