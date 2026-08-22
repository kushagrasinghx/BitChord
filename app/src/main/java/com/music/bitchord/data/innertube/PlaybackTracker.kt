package com.music.bitchord.data.innertube

import android.util.Log
import com.music.bitchord.data.TrackLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Registers plays against the signed-in account's YouTube Music history, so
 * recommendations on the home tab reflect what's actually been listened to.
 *
 * Two pings make one play, and both matter:
 *
 *  - `videostatsPlaybackUrl` the moment a track becomes audible. This creates
 *    the history entry.
 *  - `videostatsWatchtimeUrl` as it plays on. An entry with no watchtime
 *    behind it looks like a track that was skipped, which is close to worthless
 *    as a recommendation signal — this is what was missing before.
 *
 * Both carry the same client-playback-nonce, which is what ties them to one
 * play; a nonce reused across tracks gets the second play discarded.
 *
 * Best-effort only: any failure here must never affect playback itself.
 */
object PlaybackTracker {

    private const val TAG = "BitChord"

    /** Report watched time once this much new audio has gone by. */
    private const val REPORT_INTERVAL_SECONDS = 30L

    private class Session(
        val videoId: String,
        val cpn: String,
        val tracking: Innertube.PlaybackTracking,
    ) {
        var reportedSeconds = 0L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _registeredPlays = MutableStateFlow(0)

    /**
     * Bumped each time a play lands in the account's history. The home feed's
     * lead shelf is built from that history, so it has gone stale whenever this
     * moves — the counter is the signal to re-fetch, and carries no meaning
     * beyond having changed.
     */
    val registeredPlays: StateFlow<Int> = _registeredPlays.asStateFlow()

    /** Guards session hand-over: starting a track must not race its own pings. */
    private val lock = Mutex()

    @Volatile
    private var session: Session? = null

    /** The track a session is being opened for, so repeat calls don't stack up. */
    @Volatile
    private var opening: String? = null

    /**
     * Call when [videoId] becomes audible — both on play/resume and when the
     * queue moves on. A no-op while the same track is already being tracked, so
     * a pause/resume does not register a second play.
     */
    fun onPlaying(videoId: String) {
        if (session?.videoId == videoId || opening == videoId) return
        opening = videoId
        scope.launch {
            runCatching { open(videoId) }
                .onFailure { TrackLog.w(TAG, "history registration failed for $videoId: ${it.message}") }
            if (opening == videoId) opening = null
        }
    }

    /**
     * Call when the queue moves to a different track. The previous session's
     * watched time is flushed before it is dropped, so a track skipped at the
     * two-minute mark is reported as two minutes rather than lost.
     */
    fun onTrackChanged(positionSeconds: Long) {
        val closing = session ?: return
        session = null
        scope.launch {
            runCatching { flush(closing, positionSeconds) }
                .onFailure { TrackLog.w(TAG, "final watchtime ping failed: ${it.message}") }
        }
    }

    /**
     * Periodic progress report for the current track, in seconds played.
     * Cheap to call often — it only hits the network every
     * [REPORT_INTERVAL_SECONDS] of new audio.
     */
    fun onProgress(videoId: String, positionSeconds: Long) {
        val current = session ?: return
        if (current.videoId != videoId) return
        if (positionSeconds - current.reportedSeconds < REPORT_INTERVAL_SECONDS) return
        scope.launch {
            runCatching { flush(current, positionSeconds) }
                .onFailure { TrackLog.w(TAG, "watchtime ping failed for $videoId: ${it.message}") }
        }
    }

    private suspend fun open(videoId: String) = lock.withLock {
        val tracking = Innertube.playbackTracking(videoId)
        if (tracking == null) {
            TrackLog.d(TAG, "no playback tracking for $videoId (guest, or player call failed)")
            return@withLock
        }
        val fresh = Session(videoId, Innertube.newCpn(), tracking)
        val status = Innertube.pingPlayback(tracking.playbackUrl, fresh.cpn)
        session = fresh
        _registeredPlays.value++
        TrackLog.d(TAG, "history entry created for $videoId (HTTP $status)")
    }

    private suspend fun flush(target: Session, positionSeconds: Long) = lock.withLock {
        val url = target.tracking.watchtimeUrl ?: return@withLock
        if (positionSeconds <= target.reportedSeconds) return@withLock
        val status = Innertube.pingWatchtime(url, target.cpn, positionSeconds)
        target.reportedSeconds = positionSeconds
        TrackLog.d(TAG, "watchtime ${positionSeconds}s reported for ${target.videoId} (HTTP $status)")
    }
}
