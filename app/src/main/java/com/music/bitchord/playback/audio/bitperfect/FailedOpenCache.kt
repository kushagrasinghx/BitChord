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

import android.os.SystemClock

/**
 * Remembers streams the exclusive output refused, but only for a retry
 * window. A DAC held by another app, a transient permission failure or a
 * just-replugged endpoint should not disable bit-perfect output for the rest
 * of the process.
 */
internal class FailedOpenCache(
    private val ttlMs: Long = 30_000L,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private var scope: String? = null
    private val failedAt = HashMap<String, Long>()

    @Synchronized
    fun contains(scope: String, key: String): Boolean {
        switch(scope)
        val at = failedAt[key] ?: return false
        if (clock() - at < ttlMs) return true
        failedAt.remove(key)
        return false
    }

    @Synchronized
    fun mark(scope: String, key: String) {
        switch(scope)
        failedAt[key] = clock()
    }

    private fun switch(scope: String) {
        if (this.scope != scope) {
            failedAt.clear()
            this.scope = scope
        }
    }
}
