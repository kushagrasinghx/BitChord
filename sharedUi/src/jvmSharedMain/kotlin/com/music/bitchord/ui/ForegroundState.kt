package com.music.bitchord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Whether the app is actually on screen.
 *
 * The distinction this exists to draw is one Compose does not draw on its own:
 * a `LaunchedEffect` belongs to the *composition*, not to the lifecycle, and a
 * composition outlives the activity being stopped — it is torn down only when
 * the activity is destroyed. So every loop the UI starts goes on running with
 * the screen off: position polls, frame clocks, a video decoder feeding a
 * surface nobody is looking at.
 *
 * None of that work has a viewer, and none of it survives being skipped —
 * whatever it was keeping up to date is re-read the moment the screen comes
 * back. Gating on this is the difference between "cheap while visible" and
 * "cheap", and it costs nothing on screen.
 *
 * RESUMED rather than STARTED: a paused-but-visible activity is one behind a
 * dialog or in the background half of split screen, which is not a case worth
 * animating for either.
 *
 * Playback itself is not gated on this and must not be — audio comes from
 * [com.music.bitchord.playback.PlaybackService], which is a foreground service
 * precisely so that it goes on running when this does not.
 */
@Composable
fun rememberIsForeground(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycle) {
        // Every event rather than ON_RESUME/ON_PAUSE alone: the state is read
        // back off the lifecycle instead of inferred from which event arrived,
        // so there is no transition this can be left out of step by.
        val observer = LifecycleEventObserver { owner, _ ->
            foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return foreground
}
