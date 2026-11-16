package com.music.bitchord.data.scrobbling

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The Android half of durable scrobbling.
 *
 * [ScrobbleOutbox] decides what to keep and when to retry; this object gives it
 * somewhere to keep it, turns a queued row into the provider call it stands for,
 * and asks WorkManager to come back later. Playback hands work to [submitLastFm]
 * and [submitListenBrainz] and never awaits a network response.
 *
 * ## Why WorkManager
 *
 * The app already runs its background work through WorkManager (see
 * [com.music.bitchord.download.PlaylistSync]) and its own configuration, so a
 * one-shot constrained worker is the smallest thing that is still durable
 * across process death and Doze. A coroutine would retry only while the process
 * lived, which is exactly the case a commute tests.
 */
object ScrobbleRetry {
    private const val TAG = "ScrobbleRetry"
    private const val PREFS_NAME = "bitchord_scrobble_outbox"
    private const val PREF_KEY = "pending"
    private const val WORK_NAME = "scrobble-outbox"
    private const val RETRY_BACKOFF_SECONDS = 30L

    /** Its own scope: delivery must outlive a service being torn down. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var outbox: ScrobbleOutbox? = null

    @Volatile
    private var appContext: Context? = null

    /** Called once from [com.music.bitchord.BitChordApplication]. */
    fun install(context: Context) {
        appContext = context.applicationContext
        if (outbox == null) {
            synchronized(this) {
                if (outbox == null) outbox = ScrobbleOutbox(SharedPreferencesStore(context.applicationContext))
            }
        }
    }

    private fun requireOutbox(): ScrobbleOutbox? {
        val existing = outbox
        if (existing != null) return existing
        val context = appContext ?: return null
        install(context)
        return outbox
    }

    /** The account a queued Last.fm listen belongs to, or "" when signed out. */
    fun lastFmAccount(): String {
        val username = AppSettings.lastfmUsername.value.trim()
        if (username.isNotEmpty()) return "lastfm:$username"
        val sessionKey = AppSettings.lastfmSessionKey.value
        return if (sessionKey.isBlank()) "" else "lastfm:${ScrobbleOutbox.fingerprint(sessionKey)}"
    }

    /** The account a queued ListenBrainz listen belongs to, or "" when signed out. */
    fun listenBrainzAccount(): String {
        val token = AppSettings.listenBrainzToken.value
        return if (token.isBlank()) "" else "listenbrainz:${ScrobbleOutbox.fingerprint(token)}"
    }

    /**
     * Queues one Last.fm scrobble, then starts delivery.
     *
     * Suspending on the enqueue is deliberate: the listen is on disk before the
     * caller moves on, so a process death a moment later cannot lose it.
     */
    suspend fun submitLastFm(
        artist: String,
        track: String,
        album: String?,
        durationSeconds: Int?,
        timestampSeconds: Long,
    ) {
        val account = lastFmAccount()
        val box = requireOutbox()
        if (account.isBlank() || box == null) return
        val queued =
            withContext(Dispatchers.IO) {
                box.enqueue(
                    PendingScrobble(
                        provider = ScrobbleProvider.LASTFM,
                        account = account,
                        artist = artist,
                        track = track,
                        album = album,
                        durationSeconds = durationSeconds,
                        timestampSeconds = timestampSeconds,
                        listenedAtSeconds = timestampSeconds,
                        startMs = timestampSeconds * 1000L,
                        endMs = timestampSeconds * 1000L,
                    ),
                )
            }
        if (queued) flushAsync() else scheduleRetry()
    }

    /** Queues one finished ListenBrainz listen, then starts delivery. */
    suspend fun submitListenBrainz(
        song: Song,
        startMs: Long,
        endMs: Long,
        durationMs: Long?,
        primaryArtistOnly: Boolean,
    ) {
        val account = listenBrainzAccount()
        val box = requireOutbox()
        if (account.isBlank() || box == null) return
        val artist = if (primaryArtistOnly) song.artist.primaryArtist() else song.artist
        val durationSeconds = durationMs?.takeIf { it > 0 }?.div(1000)?.toInt()
        val listenedAt = clampListenedAt(startMs / 1000L)
        val queued =
            withContext(Dispatchers.IO) {
                box.enqueue(
                    PendingScrobble(
                        provider = ScrobbleProvider.LISTENBRAINZ,
                        account = account,
                        artist = artist,
                        track = song.title,
                        album = song.albumName,
                        durationSeconds = durationSeconds,
                        timestampSeconds = listenedAt,
                        listenedAtSeconds = listenedAt,
                        startMs = startMs,
                        endMs = endMs,
                    ),
                )
            }
        if (queued) flushAsync() else scheduleRetry()
    }

    /** Starts a delivery pass without blocking the caller. */
    fun flushAsync() {
        scope.launch { flush() }
    }

    /**
     * Delivers everything that is due, then schedules whatever is left.
     *
     * Reads the accounts at call time rather than caching them: the user may
     * have switched or signed out since the queue was written, and a listen
     * must never be submitted under an account it does not belong to.
     */
    suspend fun flush() {
        val box = requireOutbox() ?: return
        val scrobblingAvailable = AppSettings.scrobblingAvailable
        val lastFm =
            if (scrobblingAvailable && AppSettings.lastfmScrobbleEnabled.value) lastFmAccount() else ""
        val listenBrainz =
            if (scrobblingAvailable && AppSettings.listenBrainzEnabled.value) listenBrainzAccount() else ""
        val accounts =
            buildSet {
                if (lastFm.isNotBlank()) add(lastFm)
                if (listenBrainz.isNotBlank()) add(listenBrainz)
            }

        // Leftovers from a replaced credential or a sign-out that raced the
        // cleanup: not deliverable, and not something to keep on disk.
        withContext(Dispatchers.IO) { box.retainAccounts(accounts) }
        if (accounts.isEmpty()) return

        val result =
            box.drain(accounts) { event ->
                deliver(event)
            }
        if (result.sent > 0 || result.dropped > 0) {
            Log.d(TAG, "scrobble drain: sent=${result.sent} dropped=${result.dropped} retrying=${result.retrying}")
        }
        if (result.remaining > 0) scheduleRetry()
    }

    /** Number of listens still waiting, for diagnostics. */
    fun pendingCount(): Int = requireOutbox()?.pendingCount() ?: 0

    /** Sign-out: the listens belong to the account that just left. */
    fun clearLastFm() {
        requireOutbox()?.clearProvider(ScrobbleProvider.LASTFM)
    }

    fun clearListenBrainz() {
        requireOutbox()?.clearProvider(ScrobbleProvider.LISTENBRAINZ)
    }

    fun clearAll() {
        requireOutbox()?.let {
            it.clearProvider(ScrobbleProvider.LASTFM)
            it.clearProvider(ScrobbleProvider.LISTENBRAINZ)
        }
    }

    private suspend fun deliver(event: PendingScrobble): ScrobbleDelivery =
        when (event.provider) {
            ScrobbleProvider.LASTFM ->
                LastFM
                    .scrobble(
                        artist = event.artist,
                        track = event.track,
                        timestamp = event.timestampSeconds,
                        album = event.album,
                        duration = event.durationSeconds,
                    ).fold(
                        onSuccess = { ScrobbleDelivery.Sent },
                        onFailure = { classifyLastFm(it) },
                    )

            ScrobbleProvider.LISTENBRAINZ -> {
                val token = AppSettings.listenBrainzToken.value
                if (token.isBlank()) {
                    // Signed out between the drain starting and this row.
                    ScrobbleDelivery.Permanent("signed out")
                } else {
                    val code =
                        ListenBrainzManager.submitListen(
                            token = token,
                            artist = event.artist,
                            track = event.track,
                            album = event.album,
                            durationSeconds = event.durationSeconds,
                            startMs = event.startMs,
                            endMs = event.endMs,
                            listenedAtSeconds = event.listenedAtSeconds,
                        )
                    classifyListenBrainz(code)
                }
            }
        }

    /**
     * Turns a Last.fm failure into a retry decision.
     *
     * The numeric codes are Last.fm's own error codes, which arrive as the
     * "status" of [LastFM]'s exception; HTTP statuses arrive the same way. The
     * distinction that matters is whether waiting can ever help: a bad session
     * key or a suspended API key cannot be fixed by retrying, while a service
     * outage or a rate limit can.
     */
    internal fun classifyLastFm(failure: Throwable): ScrobbleDelivery {
        val code = (failure as? LastFM.LastFmException)?.code
        val reason = failure.message ?: failure::class.java.simpleName
        return when {
            code == null -> ScrobbleDelivery.Transient(reason)
            code == 29 || code == 429 -> ScrobbleDelivery.Transient("rate limited", RETRY_AFTER_RATE_LIMIT_MS)
            code == 408 || code in 500..599 -> ScrobbleDelivery.Transient(reason)
            code == 8 || code == 11 || code == 16 -> ScrobbleDelivery.Transient(reason)
            code == 4 || code == 9 || code == 10 || code == 13 || code == 26 ->
                ScrobbleDelivery.Permanent(reason)
            code in 400..499 -> ScrobbleDelivery.Permanent(reason)
            else -> ScrobbleDelivery.Permanent(reason)
        }
    }

    /**
     * Turns an HTTP status into a retry decision for ListenBrainz.
     *
     * A negative code is [ListenBrainzManager.HTTP_TRANSPORT_FAILURE]: the
     * request never got an answer, which is the definition of "try again".
     */
    internal fun classifyListenBrainz(code: Int): ScrobbleDelivery =
        when {
            code in 200..299 -> ScrobbleDelivery.Sent
            code == ListenBrainzManager.HTTP_TRANSPORT_FAILURE -> ScrobbleDelivery.Transient("no response")
            code == 429 -> ScrobbleDelivery.Transient("rate limited", RETRY_AFTER_RATE_LIMIT_MS)
            code == 401 || code == 403 -> ScrobbleDelivery.Permanent("token rejected")
            code == 408 || code in 500..599 -> ScrobbleDelivery.Transient("HTTP $code")
            code in 400..499 -> ScrobbleDelivery.Permanent("HTTP $code")
            else -> ScrobbleDelivery.Transient("HTTP $code")
        }

    private fun scheduleRetry() {
        val context = appContext ?: return
        val request =
            OneTimeWorkRequestBuilder<ScrobbleRetryWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        runCatching {
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }.onFailure { Log.w(TAG, "could not schedule scrobble retry: ${it.message}") }
    }

    /**
     * Nothing before 2005 is a real listen start, and a clock that says 1970 is
     * a clock that has not synced yet. Last.fm and ListenBrainz both reject
     * those, so they are replaced with "now" exactly once, when queuing.
     */
    private fun clampListenedAt(seconds: Long): Long {
        if (seconds >= MIN_LISTEN_TIMESTAMP) return seconds
        return System.currentTimeMillis() / 1000L
    }

    private const val MIN_LISTEN_TIMESTAMP = 1_033_430_400L
    private const val RETRY_AFTER_RATE_LIMIT_MS = 15 * 60 * 1000L

    private class SharedPreferencesStore(
        context: Context,
    ) : ScrobbleStore {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        override fun read(): String? = prefs.getString(PREF_KEY, null)

        override fun write(text: String) {
            // commit(), not apply(): the caller has just accepted a listen and
            // is about to be allowed to move on, so the row has to be on disk
            // when this returns. It runs on an IO dispatcher.
            prefs.edit().putString(PREF_KEY, text).commit()
        }
    }
}

/** WorkManager's wake-up call for a queue that still has listens in it. */
class ScrobbleRetryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        ScrobbleRetry.install(applicationContext)
        return try {
            ScrobbleRetry.flush()
            if (ScrobbleRetry.pendingCount() > 0) Result.retry() else Result.success()
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (failure: IOException) {
            Result.retry()
        }
    }
}
