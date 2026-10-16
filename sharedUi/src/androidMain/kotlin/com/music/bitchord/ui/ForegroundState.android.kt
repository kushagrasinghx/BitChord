package com.music.bitchord.ui

import androidx.lifecycle.Lifecycle

// Preserve Android's existing policy for paused activities and screen-off work.
internal actual fun Lifecycle.State.isForeground(): Boolean =
    isAtLeast(Lifecycle.State.RESUMED)
