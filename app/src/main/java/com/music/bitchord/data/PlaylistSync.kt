package com.music.bitchord.data

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * Playlist adds tapped while the network is gone.
 *
 * Same deal as [LikeSync]: the tap is acknowledged now, the request travels
 * with the next connection. One entry per playlist+track — tapping twice
 * while offline queues once. Entries are `playlistId|videoId` lines on disk,
 * reloaded by [install], wiped on sign-out with everything else account-bound.
 *
 * The browse id is not stored: it is `"VL" + playlistId` by construction
 * (see `UserPlaylist`), so there is nothing to keep in sync.
 */
object PlaylistSync {
    private const val PREFS = "bitchord_playlistsync"
    private const val KEY_PENDING = "pending"

    @Volatile
    private var prefs: SharedPreferences? = null
    private val pending = ConcurrentHashMap.newKeySet<Pair<String, String>>()

    /** Load the queue left by a dead process; call once from `onCreate`. */
    fun install(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        parse(p.getString(KEY_PENDING, null)).forEach { pending.add(it) }
    }

    /** Queue `videoId` for `playlistId`; a second tap while offline is a no-op. */
    fun defer(playlistId: String, videoId: String) {
        if (pending.add(playlistId to videoId)) persist()
    }

    /** Drop an add that went through the normal path. */
    fun undefer(playlistId: String, videoId: String) {
        if (pending.remove(playlistId to videoId)) persist()
    }

    /** Queued adds belong to the account that made them; sign-out drops them. */
    fun clear() {
        if (pending.isNotEmpty()) {
            pending.clear()
            persist()
        }
    }

    /**
     * Send everything still waiting. Only a dead network stops the pass
     * (and keeps everything for the next reconnect): a sent add, an
     * already-there, and a permanently failed one — a deleted playlist, say —
     * all leave the queue, or one dead entry would jam every entry behind
     * it forever. [send] throws offline to stop, throws anything else to
     * drop that entry and continue.
     */
    suspend fun flush(send: suspend (playlistId: String, videoId: String) -> Unit) {
        for ((playlistId, videoId) in pending.toList()) {
            val error = runCatching { send(playlistId, videoId) }.exceptionOrNull()
            if (error != null && LikeSync.isOfflineFailure(error)) break
            undefer(playlistId, videoId)
        }
    }

    private fun persist() {
        val p = prefs ?: return
        val text = pending.joinToString("\n") { (playlistId, videoId) -> "$playlistId|$videoId" }
        p.edit().putString(KEY_PENDING, text).apply()
    }

    private fun parse(text: String?): List<Pair<String, String>> {
        if (text.isNullOrBlank()) return emptyList()
        return text.lineSequence().mapNotNull { line ->
            val playlistId = line.substringBefore("|").trim()
            val videoId = line.substringAfter("|", "").trim()
            if (playlistId.isEmpty() || videoId.isEmpty() || "|" in videoId) {
                null
            } else {
                playlistId to videoId
            }
        }.toList()
    }
}
