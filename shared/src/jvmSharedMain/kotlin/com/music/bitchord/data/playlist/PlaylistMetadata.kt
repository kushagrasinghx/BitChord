package com.music.bitchord.data.playlist

import com.music.bitchord.data.model.Song
import kotlin.math.abs

/**
 * Conservative hints from the YouTube Music browse header, not an authoritative
 * playlist revision. Missing or rounded metadata never proves a playlist unchanged.
 */
object PlaylistMetadata {
    private val countPattern = Regex("""(?i)\b([0-9][0-9,]*)\s+(?:songs?|tracks?)\b""")
    private val hoursPattern = Regex("""(?i)\b(\d+)\s*(?:hours?|hrs?|hr)\b""")
    private val minutesPattern = Regex("""(?i)\b(\d+)\s*(?:minutes?|mins?|min)\b""")
    private val approximatePattern = Regex("""(?i)(?:\babout\b|\bover\b|\bmore than\b|\d\s*\+)""")

    data class Summary(val count: Int?, val durationSeconds: Long?)

    fun fromHeader(subtitle: String?): Summary {
        if (subtitle.isNullOrBlank()) return Summary(null, null)
        val count = countPattern.find(subtitle)?.groupValues?.get(1)
            ?.replace(",", "")?.toIntOrNull()
        // YouTube sometimes shows approximations like '6+ hours'; ignore those.
        val approximate = approximatePattern.containsMatchIn(subtitle)
        val hours = hoursPattern.find(subtitle)?.groupValues?.get(1)?.toLongOrNull()
        val minutes = minutesPattern.find(subtitle)?.groupValues?.get(1)?.toLongOrNull()
        // An hours-only label is often rounded/truncated (e.g. "2 hours"
        // can mean nearly 3). Never treat it as an exact duration.
        val duration = if (!approximate && minutes != null && minutes < 60L) {
            (hours ?: 0L) * 3600L + (minutes ?: 0L) * 60L
        } else null
        return Summary(count, duration)
    }

    /**
     * Compare the fetched first page against the beginning of a complete cached
     * snapshot. Detects newly inserted songs and reordering even when headers
     * omit the count. Not a substitute for the six-hour complete verification:
     * edits beyond the first page may remain invisible.
     */
    fun firstPageChanged(cached: List<Song>, fetched: List<Song>): Boolean {
        if (fetched.isEmpty()) return false // Empty/ambiguous page is not proof of deletion.
        if (cached.size < fetched.size) return true
        return fetched.indices.any { index ->
            playlistEntryKey(cached[index]) != playlistEntryKey(fetched[index])
        }
    }

    /** Avoid consecutive full scans after a quick app switch; tolerate clock resets. */
    fun shouldRunReturnCheck(now: Long, previous: Long?, cooldownMs: Long): Boolean =
        previous == null || now < previous || now - previous >= cooldownMs

    fun hasChanged(songs: List<Song>, headerSubtitle: String?): Boolean {
        val summary = fromHeader(headerSubtitle)
        if (summary.count != null && summary.count != songs.size) return true
        val duration = summary.durationSeconds ?: return false
        var total = 0L
        for (song in songs) {
            val parts = song.durationText?.split(':') ?: return false
            if (parts.size !in 2..3) return false
            val values = parts.map { it.toLongOrNull() ?: return false }
            if (values.drop(1).any { it >= 60L }) return false
            total += values.fold(0L) { acc, value -> acc * 60L + value }
        }
        // Header duration may be rounded to the minute; avoid false positives
        // from a few seconds of formatting differences.
        return abs(total - duration) >= 120L
    }
}
