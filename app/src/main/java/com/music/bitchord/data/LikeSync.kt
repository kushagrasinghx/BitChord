package com.music.bitchord.data

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.download.AutoDownloads
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ratings tapped while the network is gone.
 *
 * The heart is applied locally the moment it is tapped — offline or not —
 * and only the trip to YouTube waits: a tap that visibly un-taps itself a
 * second later reads as broken, while a tap that sticks and lands when the
 * connection returns reads as slow. Keyed by track, so a change of mind
 * while still offline overwrites rather than queues.
 *
 * Survives a process death: the queue is written to disk on every change and
 * reloaded (back into [LikeState], so the hearts are already painted before
 * the library fetch that would state them) by [install]. Sign-out still wipes
 * everything — ratings belong to the account that made them.
 */
object LikeSync {
    private const val PREFS = "bitchord_likesync"
    private const val KEY_PENDING = "pending"

    @Volatile
    private var prefs: SharedPreferences? = null
    private val pending = ConcurrentHashMap<String, LikeStatus>()
    private val flushing = AtomicBoolean(false)

    /** Load the queue left by a dead process; call once from `onCreate`. */
    fun install(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val loaded = parse(p.getString(KEY_PENDING, null))
        if (loaded.isNotEmpty()) {
            pending.putAll(loaded)
            loaded.forEach { (videoId, status) -> LikeState.set(videoId, status) }
        }
    }

    /** Remember [status] for [videoId]; overwrites any earlier offline tap. */
    fun defer(videoId: String, status: LikeStatus) {
        pending[videoId] = status
        persist()
        AutoDownloads.noteRatingChanged(videoId, status)
    }

    /** Drop a rating that reached YouTube through the normal path. */
    fun undefer(videoId: String) {
        if (pending.remove(videoId) != null) persist()
    }

    /** Ratings belong to the account that made them; sign-out drops them. */
    fun clear() {
        if (pending.isNotEmpty()) {
            pending.clear()
            persist()
        }
    }

    /**
     * Send everything still waiting. Stops at the first offline failure —
     * hammering a dead network helps no one, and the next reconnect retries.
     * A permanently refused rating leaves the queue (but not the heart: the
     * tap stands, rolling back a tap the listener has lived with for days
     * reads as the app changing its mind for them). A track re-tapped while
     * this runs keeps its newer value: the remove only clears the entry this
     * attempt sent.
     *
     * Single-flight: the UI and the service both flush on reconnect, and a
     * second concurrent pass would double every request.
     */
    suspend fun flush(onSynced: suspend (videoId: String, status: LikeStatus) -> Unit = { _, _ -> }) {
        if (!flushing.compareAndSet(false, true)) return
        try {
            for ((videoId, status) in pending.entries.toList()) {
                val error = YtMusicRepository.rate(videoId, status).exceptionOrNull()
                if (error != null && isOfflineFailure(error)) break
                if (pending.remove(videoId, status)) {
                    persist()
                    if (error == null) onSynced(videoId, status)
                }
            }
        } finally {
            flushing.set(false)
        }
    }

    /**
     * Only a dead network keeps a tap local. Anything else — a refused
     * rating, a signed-out account mid-flight — rolls back as before.
     */
    fun isOfflineFailure(e: Throwable): Boolean = e is IOException

    private fun persist() {
        val p = prefs ?: return
        val text = pending.entries.joinToString("\n") { (id, status) -> "$id=${status.name}" }
        p.edit().putString(KEY_PENDING, text).apply()
    }

    private fun parse(text: String?): Map<String, LikeStatus> {
        if (text.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, LikeStatus>()
        for (line in text.lineSequence()) {
            val id = line.substringBefore("=").trim()
            val status = runCatching { LikeStatus.valueOf(line.substringAfter("=", "").trim()) }
                .getOrNull() ?: continue
            if (id.isNotEmpty()) out[id] = status
        }
        return out
    }
}
