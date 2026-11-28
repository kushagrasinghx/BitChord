/*
 * Copyright (C) 2026 BitChord Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.music.bitchord.playback.audio.bitperfect

import android.os.Handler
import android.os.Looper

/**
 * Runs the idle-release check even when Media3 stops offering buffers while
 * paused. The sink calls this on its playback thread, so the continuation
 * stays on the thread that owns the transport.
 */
internal fun interface IdleReleaseScheduler {
    fun schedule(delayMs: Long, task: () -> Unit): () -> Unit

    companion object {
        /** Used by plain-JVM tests and callers that do not own a looper. */
        val NONE = IdleReleaseScheduler { _, _ -> {} }
    }
}

internal class AndroidIdleReleaseScheduler : IdleReleaseScheduler {
    override fun schedule(delayMs: Long, task: () -> Unit): () -> Unit {
        val looper = Looper.myLooper() ?: return {}
        val handler = Handler(looper)
        val runnable = Runnable { task() }
        handler.postDelayed(runnable, delayMs)
        return { handler.removeCallbacks(runnable) }
    }
}
