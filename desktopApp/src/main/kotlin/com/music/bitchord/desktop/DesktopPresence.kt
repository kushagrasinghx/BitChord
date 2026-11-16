package com.music.bitchord.desktop

import com.music.bitchord.data.presence.Presence
import com.music.bitchord.data.presence.PresencePlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.prefs.Preferences

/**
 * Counts this PC toward the live open-app figure while the window is showing,
 * minimized included. Closed to the tray, it stops counting, the same as a
 * phone with BitChord in the background.
 */
internal object DesktopPresence {
    private const val KEY_INSTALL_ID = "presence_install_id"
    private val preferences = Preferences.userRoot().node("com.music.bitchord.desktop")

    fun install() {
        val presence = Presence(PresencePlatform.Pc, installId())
        CoroutineScope(Dispatchers.Default).launch {
            DesktopWindowVisibility.visible.collect(presence::setOpen)
        }
        // Covers both the window's close and the tray's Quit (exitProcess), so the
        // count drops now rather than when the server's window runs out.
        Runtime.getRuntime().addShutdownHook(Thread(presence::closeBlocking, "bitchord-presence-close"))
    }

    /** Random and local to this install: not the machine, and not the account. */
    private fun installId(): String {
        preferences.get(KEY_INSTALL_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        preferences.put(KEY_INSTALL_ID, id)
        runCatching { preferences.flush() }
        return id
    }
}
