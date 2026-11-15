package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState

/** Shared rounded material for queue selection and Undo, using the output drawer's frost. */
@OptIn(ExperimentalHazeApi::class)
fun Modifier.queueActionSurface(hazeState: HazeState?, reduceDynamicBlur: Boolean): Modifier {
    return clip(RoundedCornerShape(16.dp)).then(
        if (reduceDynamicBlur || hazeState == null) Modifier.background(MenuFrostDark)
        else Modifier.optimizedHazeEffect(hazeState, menuFrost(MenuFrostDark)) {
            // This state contains only sibling player artwork and queue-list sources,
            // never an ancestor recording this surface. An enclosing app source's
            // z-index must not exclude the player's independently owned sources.
            canDrawArea = { true }
        },
    )
}
