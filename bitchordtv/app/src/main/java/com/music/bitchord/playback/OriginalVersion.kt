package com.music.bitchord.playback

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The tracks the listener has sent back to YouTube's own upload by hand, and
 * wants kept there.
 *
 * "Revert to original" used to be a fact about one queue entry: the item was
 * replaced with a direct-YouTube one — see [Song.toDirectYouTubeMediaItem] —
 * and that was the end of it. Play the same song again tomorrow, or reach the
 * end of the queue and come back round to it, and the whole substitution and
 * upgrade machinery started over from nothing and put the listener right back
 * on the copy they had just rejected. A revert is not a preference about a
 * moment; it is the listener saying this catalogue match is wrong for this
 * song, and the only useful lifetime for that is "until they say otherwise".
 *
 * So it is written down. [Song.toMediaItem] reads this wherever a queue entry
 * is built — from a list, from Android Auto, from the restart snapshot — and a
 * pinned track is built as the direct-YouTube item it was reverted to. That
 * covers process death, which is the case a purely in-memory set would miss and
 * the one most likely to be noticed, because the queue is restored from disk.
 *
 * "Upgrade quality" in the player's menu is the way back out — see
 * [PlaybackService][com.music.bitchord.playback.PlaybackService]'s
 * `ACTION_UPGRADE_QUALITY`. Nothing else clears an entry: an upgrade the app
 * decided on by itself must not overturn one the listener asked for, which is
 * exactly what the automatic path would do given the chance.
 */
object OriginalVersion {

    private lateinit var prefs: SharedPreferences

    private val _pinned = MutableStateFlow<Set<String>>(emptySet())

    /** Which tracks are pinned, for a menu that has to offer the right row. */
    val pinned: StateFlow<Set<String>> = _pinned.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Stored as one newline-joined string rather than as a string set,
        // because the order is what the cap below evicts by and a set of
        // strings comes back from SharedPreferences in no particular order.
        _pinned.value = prefs.getString(KEY_PINNED, null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
    }

    fun isPinned(videoId: String?): Boolean = videoId != null && videoId in _pinned.value

    /** Keeps [videoId] on YouTube's own upload for every play from here on. */
    fun pin(videoId: String) {
        if (videoId.isBlank() || isPinned(videoId)) return
        var next = _pinned.value + videoId
        // Oldest first, because the newest entry is the one the listener just
        // made. A cap at all is only about not carrying a list that grows for
        // the life of the install; at this size it is not a limit anyone
        // reverting songs by hand is going to reach.
        while (next.size > MAX_PINNED) next = next - next.first()
        write(next)
    }

    /** Lets [videoId] be substituted and upgraded again. */
    fun unpin(videoId: String) {
        if (!isPinned(videoId)) return
        write(_pinned.value - videoId)
    }

    private fun write(ids: Set<String>) {
        _pinned.value = ids
        // The flow is updated either way: a unit test or a not-yet-initialised
        // process should still behave correctly for as long as it lasts.
        if (!::prefs.isInitialized) return
        prefs.edit().putString(KEY_PINNED, ids.joinToString("\n")).apply()
    }

    private const val MAX_PINNED = 500
    private const val PREFS_NAME = "bitchord_original_versions"
    private const val KEY_PINNED = "pinned"
}
