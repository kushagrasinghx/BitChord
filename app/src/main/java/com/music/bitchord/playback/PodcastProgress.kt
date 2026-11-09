package com.music.bitchord.playback

import android.content.Context
import android.content.SharedPreferences

/**
 * Local, per-episode podcast bookmarks.
 *
 * The bookmark is deliberately independent from LastPlayed's queue snapshot:
 * an episode can be opened from search, a show page, history or a new queue and
 * still resume at the same place. SharedPreferences is device-local and is
 * available before playback resolution, including with no network connection.
 */
object PodcastProgress {

    private data class Entry(
        val positionMs: Long,
        val durationMs: Long,
        val updatedAtMs: Long,
    )

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    internal fun initForTesting(testPrefs: SharedPreferences) {
        prefs = testPrefs
    }

    /** Returns the saved playhead, discarding bookmarks beyond a known episode end. */
    fun positionMs(videoId: String, durationMs: Long = 0L): Long {
        if (!::prefs.isInitialized || videoId.isBlank()) return 0L
        val entry = read(videoId) ?: return 0L
        val knownDuration = durationMs.takeIf { it > 0L } ?: entry.durationMs
        if (knownDuration > 0L && entry.positionMs >= knownDuration) {
            clear(videoId)
            return 0L
        }
        return entry.positionMs
    }

    /**
     * Save while playing with apply(); force a synchronous commit on pause and
     * service shutdown so an abrupt process death cannot lose the latest pause.
     * Position zero is intentionally ignored: only an explicit seek-to-start or
     * episode completion removes a bookmark.
     */
    fun save(videoId: String, positionMs: Long, durationMs: Long = 0L, commit: Boolean = false) {
        if (!::prefs.isInitialized || videoId.isBlank()) return
        val position = positionMs.coerceAtLeast(0L)
        val suppliedDuration = durationMs.coerceAtLeast(0L)
        if (position == 0L) return
        // A temporary TIME_UNSET from Media3 must not erase a duration we
        // learned earlier for this episode (e.g. during a buffering pause).
        val duration = suppliedDuration.takeIf { it > 0L } ?: read(videoId)?.durationMs ?: 0L
        if (duration > 0L && position >= duration) {
            clear(videoId, commit)
            return
        }
        val value = "${position}|${duration}|${System.currentTimeMillis()}"
        val editor = prefs.edit().putString(key(videoId), value)
        if (commit) editor.commit() else editor.apply()
        pruneIfNeeded()
    }

    fun clear(videoId: String, commit: Boolean = false) {
        if (!::prefs.isInitialized || videoId.isBlank()) return
        val editor = prefs.edit().remove(key(videoId))
        if (commit) editor.commit() else editor.apply()
    }

    private fun read(videoId: String): Entry? {
        val raw = prefs.getString(key(videoId), null) ?: return null
        val parts = raw.split('|')
        if (parts.size != 3) return null
        val position = parts[0].toLongOrNull()?.takeIf { it > 0L } ?: return null
        val duration = parts[1].toLongOrNull()?.takeIf { it >= 0L } ?: return null
        val updated = parts[2].toLongOrNull()?.takeIf { it >= 0L } ?: return null
        return Entry(position, duration, updated)
    }

    /** Bound storage for long-lived libraries without expiring active bookmarks. */
    private fun pruneIfNeeded() {
        val entries = prefs.all.keys.filter { it.startsWith(KEY_PREFIX) }
        if (entries.size <= MAX_EPISODES) return
        val oldest = entries.mapNotNull { key ->
            val raw = prefs.getString(key, null) ?: return@mapNotNull null
            val updatedAt = raw.substringAfterLast('|').toLongOrNull() ?: 0L
            key to updatedAt
        }.sortedBy { it.second }
            .take(entries.size - MAX_EPISODES)
        if (oldest.isEmpty()) return
        val editor = prefs.edit()
        oldest.forEach { (key, _) -> editor.remove(key) }
        editor.apply()
    }

    private fun key(videoId: String) = KEY_PREFIX + videoId

    private const val PREFS_NAME = "bitchord_podcast_progress"
    private const val KEY_PREFIX = "episode:"
    private const val MAX_EPISODES = 500
}
