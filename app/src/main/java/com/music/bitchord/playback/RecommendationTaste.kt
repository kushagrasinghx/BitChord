package com.music.bitchord.playback

import android.os.SystemClock
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * On-device taste, preference, and skip telemetry for intelligent music recommendations.
 *
 * Re-ranks radio and autoplay candidate pools by evaluating user affinity, explicit likes,
 * skip penalties, and session continuity so listeners stay in flow without fatigue.
 */
object RecommendationTaste {

    private const val MAX_RECENT_TRACKS = 25
    private const val SIGNAL_TTL_MS = 2 * 60 * 60 * 1000L // 2 hours

    @set:org.jetbrains.annotations.TestOnly
    internal var clockMs: () -> Long = { SystemClock.elapsedRealtime() }

    private val recentTracks = Collections.synchronizedList(mutableListOf<String>())
    private val recentArtists = Collections.synchronizedSet(LinkedHashSet<String>())

    private val skippedTracks = ConcurrentHashMap<String, Long>()
    private val skippedArtists = ConcurrentHashMap<String, Long>()
    private val completedTracks = ConcurrentHashMap<String, Long>()
    private val completedArtists = ConcurrentHashMap<String, Long>()

    /** Records that [song] was played in the current listening session. */
    fun recordSessionTrack(song: Song) {
        val id = song.videoId
        synchronized(recentTracks) {
            recentTracks.remove(id)
            recentTracks.add(id)
            if (recentTracks.size > MAX_RECENT_TRACKS) recentTracks.removeAt(0)
        }
        val artists = QueueBuilder.artistSet(song.artist)
        synchronized(recentArtists) {
            recentArtists.addAll(artists)
            if (recentArtists.size > MAX_RECENT_TRACKS * 2) {
                val dropCount = recentArtists.size - (MAX_RECENT_TRACKS * 2)
                val it = recentArtists.iterator()
                var dropped = 0
                while (it.hasNext() && dropped < dropCount) {
                    it.next()
                    it.remove()
                    dropped++
                }
            }
        }
    }

    /**
     * Records an early manual skip (e.g. < 25s), registering a negative taste signal
     * for this track and artist in the current session.
     */
    fun recordSkip(song: Song) {
        val now = clockMs()
        skippedTracks[song.videoId] = now
        for (artist in QueueBuilder.artistSet(song.artist)) {
            skippedArtists[artist] = now
        }
    }

    /** Records that [song] was listened to completion or past the enjoyment threshold. */
    fun recordCompletion(song: Song) {
        val now = clockMs()
        completedTracks[song.videoId] = now
        for (artist in QueueBuilder.artistSet(song.artist)) {
            completedArtists[artist] = now
            // Forgive any previous skip penalty for this artist
            skippedArtists.remove(artist)
        }
        skippedTracks.remove(song.videoId)
    }

    /** Evaluates a candidate track against the user's taste and session context. */
    fun scoreCandidate(candidate: Song): Double {
        val now = clockMs()
        var score = 0.0

        // 1. Explicit Likes / Dislikes
        val likeStatus = LikeState.overrides.value[candidate.videoId]
        if (likeStatus == LikeStatus.DISLIKE) return -1000.0 // Reject completely
        if (likeStatus == LikeStatus.LIKE) score += 50.0

        // 2. Early-Skip Negative Signals
        val skippedAt = skippedTracks[candidate.videoId]
        if (skippedAt != null && now - skippedAt <= SIGNAL_TTL_MS) {
            score -= 80.0
        }

        val artists = QueueBuilder.artistSet(candidate.artist)
        for (artist in artists) {
            val artistSkippedAt = skippedArtists[artist]
            if (artistSkippedAt != null && now - artistSkippedAt <= SIGNAL_TTL_MS) {
                score -= 35.0
            }
            val artistCompletedAt = completedArtists[artist]
            if (artistCompletedAt != null && now - artistCompletedAt <= SIGNAL_TTL_MS) {
                score += 25.0
            }
        }

        // 3. Session Continuity & Vibe Alignment
        synchronized(recentArtists) {
            if (artists.any { it in recentArtists }) {
                score += 20.0
            }
        }

        // 4. Lossless Master Availability
        val format = StreamChoice.formatOf(candidate.videoId)
        if (format?.isLossless == true || format?.isDolbyAtmos == true) {
            score += 15.0
        }

        // 5. Session Fatigue Penalty (played very recently)
        synchronized(recentTracks) {
            if (candidate.videoId in recentTracks) {
                score -= 60.0
            }
        }

        return score
    }

    /**
     * Re-ranks a raw candidate pool from YouTube Music radio by personalized affinity.
     * Excludes disliked tracks and surfaces the most contextually relevant songs.
     */
    fun reRankCandidates(candidates: List<Song>): List<Song> {
        if (candidates.isEmpty()) return candidates
        val scored = candidates.map { song -> song to scoreCandidate(song) }
            .filter { (_, score) -> score > -500.0 } // filter out explicitly disliked
        return scored.sortedByDescending { it.second }.map { it.first }
    }

    /** Clears session state (useful for tests or new accounts). */
    fun resetForTesting() {
        clockMs = { SystemClock.elapsedRealtime() }
        recentTracks.clear()
        recentArtists.clear()
        skippedTracks.clear()
        skippedArtists.clear()
        completedTracks.clear()
        completedArtists.clear()
    }
}
