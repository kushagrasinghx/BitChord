package com.music.bitchord.data.offline

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.LikeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A rating the listener asked for while there was nobody to ask.
 *
 * The UI writes a like to [LikeState] first and rolls it back if YouTube
 * refuses — which is the right call for a tap taken while a song plays, because
 * waiting on a round trip makes the heart look broken. But "refused" hides two
 * very different cases: YouTube answered *no*, and nobody was there to answer
 * at all. Rolling back the second one loses a tap that was perfectly valid, and
 * the listener has no way to tell the difference from the screen — the heart
 * simply empties itself again a beat later, for no reason they can see.
 *
 * So an offline failure is kept instead of rolled back, queued here, and
 * delivered the moment a connection comes back. Persistence is what makes this
 * more than a nicety: a like tapped on the platform, kept in a variable, and
 * lost to a process death is exactly the tap this exists to protect.
 *
 * One entry per track, newest wins. Two taps on the same heart while offline —
 * like, un-like — are not two operations for YouTube's sake; only the state
 * they ended in was ever real, and sending the intermediate one would briefly
 * undo a decision the listener has already changed their mind about.
 */
@Serializable
internal data class PendingRating(
    val videoId: String,
    /** What to tell YouTube, by [LikeStatus]' own name. */
    val status: String,
    /**
     * What the tap changed *from*, by name.
     *
     * Carried because clearing a heart is not only a rating: the track also
     * leaves the Liked Music lists, and a delivery that only sent the rating
     * would leave a song sitting in a playlist the listener has just removed
     * it from.
     */
    val previous: String,
) {
    val statusEnum: LikeStatus
        get() = runCatching { LikeStatus.valueOf(status) }.getOrDefault(LikeStatus.INDIFFERENT)

    val previousEnum: LikeStatus
        get() = runCatching { LikeStatus.valueOf(previous) }.getOrDefault(LikeStatus.INDIFFERENT)
}

internal object RatingOutbox {
    private const val TAG = "BitChord"
    private const val PREFS = "bitchord_rating_outbox"
    private const val KEY_PENDING = "pending"

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _pending = MutableStateFlow<List<PendingRating>>(emptyList())
    /** What is still waiting, for anything that wants to show it. */
    val pending: StateFlow<List<PendingRating>> = _pending.asStateFlow()

    private val _delivered = MutableSharedFlow<PendingRating>(
        replay = 0,
        extraBufferCapacity = 8,
    )
    /**
     * Ratings the server has now accepted, in the order they were accepted.
     *
     * Not just a success log: the bookkeeping that follows a rating — marking
     * the Liked Music lists stale, pulling an un-liked track out of them —
     * happens here rather than at the moment of the tap, because the tap's
     * network call has already returned by the time this fires and the code
     * that would have done it has moved on.
     */
    val delivered: SharedFlow<PendingRating> = _delivered.asSharedFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _pending.value = runCatching {
            prefs.getString(KEY_PENDING, null)?.let { json.decodeFromString<List<PendingRating>>(it) }
        }.getOrNull().orEmpty()

        // Reconnecting is the whole trigger this feature needs: it is the one
        // moment the queue can move, and it arrives whether or not the process
        // that queued anything is still the one in front — but a registered
        // callback only fires while the process lives, which is why a cold start
        // has to make its own attempt below.
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            appContext.getSystemService(ConnectivityManager::class.java)
                ?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) = flushNow()
                })
        }.onFailure { Log.w(TAG, "rating outbox could not watch for a connection", it) }

        flushNow()
    }

    /**
     * Whether there is any network at all right now.
     *
     * Checked rather than inferred from the failure: a request refused by
     * YouTube while the phone is online is the server's answer and belongs in
     * the rollback, where a request that never left the device has nothing to
     * answer with yet.
     */
    fun isOffline(): Boolean {
        if (!::appContext.isInitialized) return false
        return runCatching {
            appContext.getSystemService(ConnectivityManager::class.java)?.activeNetwork == null
        }.getOrDefault(false)
    }

    /**
     * Records a like YouTube did not get to hear about, and tries to send it.
     *
     * Replaces any entry for the same track: only the state the listener ended
     * on was ever real.
     */
    fun enqueue(videoId: String, status: LikeStatus, previous: LikeStatus) {
        if (!::prefs.isInitialized) return
        val entry = PendingRating(videoId, status.name, previous.name)
        val next = _pending.value.filterNot { it.videoId == videoId } + entry
        _pending.value = next
        runCatching { prefs.edit().putString(KEY_PENDING, json.encodeToString(next)).apply() }
        flushNow()
    }

    /** Sends whatever is queued, off the main thread. */
    fun flushNow() {
        if (!::prefs.isInitialized) return
        scope.launch { flush() }
    }

    private suspend fun flush() {
        // A reconnect and a cold start can both land here, and the loop below is
        // not idempotent — two of them would send the same rating twice and both
        // would try to drain the same list.
        if (!mutex.tryLock()) return
        try {
            while (true) {
                val next = _pending.value.firstOrNull() ?: return
                val outcome = YtMusicRepository.rate(next.videoId, next.statusEnum)
                if (outcome.isFailure) {
                    // Offline: keep it and wait for the connection this is
                    // registered against. Online: YouTube answered no, and
                    // holding on to a rating the server has rejected would
                    // replay a refusal on every reconnect for the rest of the
                    // session.
                    if (isOffline()) return
                    Log.w(TAG, "dropping undeliverable rating for ${next.videoId}: $outcome")
                    remove(next)
                    continue
                }
                // The optimistic write already painted this; setting it again
                // is a no-op there and the only thing that makes a *restored*
                // queue correct after a process death, where no screen ever
                // painted it in the first place.
                LikeState.set(next.videoId, next.statusEnum)
                remove(next)
                _delivered.tryEmit(next)
            }
        } finally {
            mutex.unlock()
        }
    }

    private fun remove(entry: PendingRating) {
        val next = _pending.value.filterNot { it.videoId == entry.videoId }
        _pending.value = next
        runCatching { prefs.edit().putString(KEY_PENDING, json.encodeToString(next)).apply() }
    }
}
