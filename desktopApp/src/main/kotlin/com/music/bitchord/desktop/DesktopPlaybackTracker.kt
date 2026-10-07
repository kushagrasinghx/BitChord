package com.music.bitchord.desktop

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.StreamResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

/** Registers plays against the signed-in account's YouTube Music history. */
internal object DesktopPlaybackTracker {

    /** Report watched time once this much new audio has gone by. */
    private const val REPORT_INTERVAL_SECONDS = 30L

    /** Attempts at opening a session before the play is written off. */
    private const val OPEN_ATTEMPTS = 3
    private const val OPEN_RETRY_DELAY_MS = 2_000L

    private class Session(
        val videoId: String,
        val cpn: String,
        val tracking: Tracking,
    ) {
        var reportedSeconds = 0L

        /** Set before the network call, so a slow flush cannot stack up. */
        var flushingTo = 0L
        var atrSent = false
    }

    /** The stats endpoints a player response nominates for one playback. */
    private class Tracking(
        val playbackUrl: String,
        val watchtimeUrl: String?,
        val atrUrl: String?,
        val atrAfterSeconds: Long,
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lock = Mutex()

    @Volatile
    private var session: Session? = null

    /** The track a session is being opened for, so repeat calls don't stack. */
    @Volatile
    private var opening: String? = null

    /** Call when [videoId] becomes audible. */
    fun onPlaying(videoId: String) {
        // A local file carries its path and an addon carries whatever that source uses; asking
        // Google to register a play of one cannot succeed.
        if (!DesktopSearchClient.isVideoId(videoId)) return
        if (!DesktopYouTubeAuth.isSignedIn) return
        if (session?.videoId == videoId || opening == videoId) return
        opening = videoId
        scope.launch {
            try {
                openWithRetries(videoId)
            } finally {
                if (opening == videoId) opening = null
            }
        }
    }

    /** Call when the queue moves on. */
    fun onTrackChanged(positionSeconds: Long) {
        val closing = session ?: return
        session = null
        scope.launch {
            runCatching { flush(closing, positionSeconds, final = true) }
                .onFailure { DesktopTrackLog.log("history: final ping failed for ${closing.videoId} — ${it.message}") }
        }
    }

    /** Progress for the current track, in seconds played. */
    fun onProgress(videoId: String, positionSeconds: Long) {
        val current = session ?: return
        if (current.videoId != videoId) return
        if (!current.atrSent && positionSeconds >= current.tracking.atrAfterSeconds) {
            current.atrSent = true
            current.tracking.atrUrl?.let { url ->
                scope.launch {
                    runCatching { Innertube.pingAtr(url, current.cpn) }
                        .onFailure { DesktopTrackLog.log("history: atr ping failed — ${it.message}") }
                }
            }
        }
        // Against `flushingTo` rather than `reportedSeconds`.
        if (positionSeconds - maxOf(current.reportedSeconds, current.flushingTo) < REPORT_INTERVAL_SECONDS) return
        scope.launch {
            runCatching { flush(current, positionSeconds) }
                .onFailure { DesktopTrackLog.log("history: watchtime ping failed — ${it.message}") }
        }
    }

    /** Close the current play out for good — the queue running dry, or the app going away. */
    fun onPlaybackFinished(positionSeconds: Long) {
        val closing = session ?: return
        session = null
        scope.launch {
            withContext(NonCancellable) {
                runCatching { flush(closing, positionSeconds, final = true) }
                    .onFailure { DesktopTrackLog.log("history: closing ping failed — ${it.message}") }
            }
        }
    }

    /** [open], given more than one chance. */
    private suspend fun openWithRetries(videoId: String) {
        repeat(OPEN_ATTEMPTS) { attempt ->
            if (opening != videoId) return
            val settled = try {
                open(videoId)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                DesktopTrackLog.log(
                    "history: registering '$videoId' failed " +
                        "(attempt ${attempt + 1}/$OPEN_ATTEMPTS) — ${failure.message}",
                )
                false
            }
            if (settled) return
            if (attempt < OPEN_ATTEMPTS - 1) delay(OPEN_RETRY_DELAY_MS)
        }
    }

    /**
     * @return whether there is anything left to try. False means the attempt is
     */
    private suspend fun open(videoId: String): Boolean = lock.withLock {
        // The one thing the tracking request cannot be answered without.
        val signatureTimestamp = withContext(Dispatchers.IO) {
            StreamResolver.signatureTimestamp(videoId)
        }
        if (signatureTimestamp == null) {
            DesktopTrackLog.log("history: no signature timestamp yet for '$videoId'; will retry")
            return@withLock false
        }
        val tracking = trackingFor(videoId, signatureTimestamp)
        if (tracking == null) {
            // A verdict, not a failure: asking again with the same timestamp gets the same answer.
            DesktopTrackLog.log("history: YouTube offered no tracking for '$videoId'")
            return@withLock true
        }
        val fresh = Session(videoId, Innertube.newCpn(), tracking)
        val status = Innertube.pingPlayback(tracking.playbackUrl, fresh.cpn)
        session = fresh
        DesktopTrackLog.log("history: entry created for '$videoId' (HTTP $status)")
        true
    }

    private suspend fun flush(target: Session, positionSeconds: Long, final: Boolean = false) {
        val url = target.tracking.watchtimeUrl ?: return
        // A final report is worth sending even at a position already covered: it is the `final=1`
        // that matters, not the number.
        if (!final && positionSeconds <= target.reportedSeconds) return
        target.flushingTo = maxOf(target.flushingTo, positionSeconds)
        lock.withLock {
            val status = Innertube.pingWatchtime(url, target.cpn, positionSeconds, final)
            target.reportedSeconds = maxOf(target.reportedSeconds, positionSeconds)
            DesktopTrackLog.log(
                "history: ${positionSeconds}s reported for '${target.videoId}'" +
                    (if (final) " (final)" else "") + " (HTTP $status)",
            )
        }
    }

    /** The tracking block of the session's own player response — the phone's request, shared. */
    private suspend fun trackingFor(videoId: String, signatureTimestamp: Int): Tracking? {
        val tracking = runCatching { Innertube.playbackTracking(videoId, signatureTimestamp) }.getOrNull()
        if (tracking == null) {
            DesktopTrackLog.log("history: no tracking block for '$videoId'")
            return null
        }
        return Tracking(
            playbackUrl = tracking.playbackUrl,
            watchtimeUrl = tracking.watchtimeUrl,
            atrUrl = tracking.atrUrl,
            atrAfterSeconds = tracking.atrAfterSeconds,
        )
    }

}
