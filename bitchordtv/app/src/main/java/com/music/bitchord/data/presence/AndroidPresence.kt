package com.music.bitchord.data.presence

import android.app.Application
import android.content.Context
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
    private const val PREFS = "presence"
    private const val KEY_INSTALL_ID = "install_id"

    /** Call once from [Application.onCreate], on the main thread. */
    fun install(app: Application) {
        val presence = Presence(PresencePlatform.Android, installId(app))
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = presence.setOpen(true)
            override fun onStop(owner: LifecycleOwner) = presence.setOpen(false)
        })
    }

    /** Random and local to this install: not the device, and not the account. */
    private fun installId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_INSTALL_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_INSTALL_ID, id).apply()
        return id
    }
}
