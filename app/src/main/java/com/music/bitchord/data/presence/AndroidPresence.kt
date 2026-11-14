package com.music.bitchord.data.presence

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.UUID

/**
 * Counts this phone toward the live open-app figure while BitChord is on
 * screen. Playing in the background with the app closed does not count.
 * ProcessLifecycleOwner already waits out an activity restart before it
 * reports a stop, so rotating the phone does not read as closing the app.
 */
object AndroidPresence {

    /** Call once from [Application.onCreate], on the main thread. */
    fun install(app: Application) {
        // Session-scoped rather than persisted: the public open-app count only
        // needs to distinguish concurrent sessions, and a per-process UUID
        // cannot link a user's launches across restarts.
        // No properly localized consent control exists yet, so the
        // non-essential open-app ping stays off by default. Listen Together
        // and playback do not depend on it.
        val presence = Presence(PresencePlatform.Android, UUID.randomUUID().toString(), enabled = false)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = presence.setOpen(true)
            override fun onStop(owner: LifecycleOwner) = presence.setOpen(false)
        })
    }
}
