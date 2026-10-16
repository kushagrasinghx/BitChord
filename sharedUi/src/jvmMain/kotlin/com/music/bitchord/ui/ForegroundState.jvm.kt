package com.music.bitchord.ui

import androidx.lifecycle.Lifecycle

// Desktop windows lose RESUMED on focus loss, even while still visible.
internal actual fun Lifecycle.State.isForeground(): Boolean =
    isAtLeast(Lifecycle.State.STARTED)
