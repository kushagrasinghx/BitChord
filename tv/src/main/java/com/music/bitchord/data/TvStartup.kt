package com.music.bitchord.data

import android.content.Context
import androidx.startup.Initializer

/**
 * Runs once at process start, before any activity or the playback service, so
 * the TV-only hooks into the shared code are in place whichever of them gets
 * there first. Registered in the TV manifest.
 */
class TvStartup : Initializer<Unit> {
    override fun create(context: Context) {
        TvUsbMusic.install()
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
