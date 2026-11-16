package com.music.bitchord.data.scrobbling

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.min
import kotlin.math.pow

/**
 * Which service a queued listen is on its way to.
 *
 * A listen that is destined for both Last.fm and ListenBrainz is two events,
 * not one with two targets: one service being down must not hold the other's
 * submission behind it, and one of them rejecting the listen must not cancel
 * the other's copy.
 */
enum class ScrobbleProvider {
    LASTFM,
    LISTENBRAINZ,
}

/**
 * A listen that has been accepted by the player but not yet by its service.
 *
 * Everything needed to finish the submission is here, including the timestamp
 * the service will use to recognise the play. Nothing secret is: [account] is
 * either the public Last.fm username or a truncated SHA-256 fingerprint of the
 * credential, never the credential itself. That matters because this record is
 * written to disk, survives sign-out, and is included in whatever back-up the
 * device takes.
 */
@Serializable
data class PendingScrobble(
    val provider: ScrobbleProvider,
    val account: String,
    val artist: String,
    val track: String,
    val album: String? = null,
    val durationSeconds: Int? = null,
    /** Last.fm's own idempotency key, and the play's wall-clock start. */
    val timestampSeconds: Long = 0L,
    /** ListenBrainz's `listened_at`, clamped away from implausible values. */
    val listenedAtSeconds: Long = 0L,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val attempts: Int = 0,
    val nextAttemptAtMs: Long = 0L,
    val lastError: String? = null,
) {
    /**
     * What makes two records the same listen.
     *
     * The timestamp is in the key on purpose. Last.fm treats a scrobble's
     * timestamp as its identity, so a duplicate callback for one play (a
     * player re-emitting a state change, say) must collapse to one row, while
     * the same track played again an hour later must not be swallowed by it.
     */
    val key: String
        get() = listOf(provider.name, account, artist, track, timestampSeconds.toString())
            .joinToString("\u0000")
}

/** What one attempt to deliver a queued listen came to. */
sealed interface ScrobbleDelivery {
    /** The service accepted it. The event leaves the queue. */
    data object Sent : ScrobbleDelivery

    /** The service may accept it later. Keep it and back off. */
    data class Transient(
        val reason: String,
        val retryAfterMs: Long? = null,
    ) : ScrobbleDelivery

    /** The service will never accept it. Drop it rather than retry forever. */
    data class Permanent(val reason: String) : ScrobbleDelivery
}

/** Where the queue is kept. Its only job is to carry one opaque string. */
interface ScrobbleStore {
    fun read(): String?

    fun write(text: String)
}

/** What a drain pass did, for logging and for deciding whether to reschedule. */
data class DrainResult(
    val sent: Int,
    val dropped: Int,
    val retrying: Int,
    val remaining: Int,
)

/**
 * The durable queue of listens waiting for their service.
 *
 * ## Why this exists
 *
 * Scrobbling used to be a fire-and-forget coroutine per play: if the request
 * failed — a tunnel, a dead zone, a service outage — the only trace was a line
 * in the log, and the listen was gone. A commute with no signal lost the whole
 * commute. The queue is what makes an accepted listen survive the process,
 * and it is why the player never waits for a network round trip to move on.
 *
 * ## At-least-once, not exactly-once
 *
 * A request can be accepted by a service while its response is lost in flight.
 * Without a server-side idempotency key the client cannot tell that apart from
 * a request that never arrived, so this queue can resend in that one window.
 * Last.fm's timestamp is an idempotency key and absorbs it; ListenBrainz does
 * not offer one, so a rare duplicate listen is possible and is the correct
 * trade against losing listens. Delivering at-least-once and being honest
 * about it beats claiming exactly-once that no protocol here supports.
 *
 * ## This class knows nothing about Android
 *
 * No `Context`, no `SharedPreferences`, no logger. That is deliberate: the
 * retry and dedupe rules are the part that is easy to get subtly wrong, and
 * they are only worth having if they can be driven by a test with a fake clock
 * and a scripted service. [ScrobbleRetry] is the Android half.
 */
class ScrobbleOutbox(
    private val store: ScrobbleStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val capacity: Int = DEFAULT_CAPACITY,
    private val baseBackoffMs: Long = DEFAULT_BASE_BACKOFF_MS,
    private val maxBackoffMs: Long = DEFAULT_MAX_BACKOFF_MS,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val jitter: (Long) -> Long = { window ->
        if (window <= 0L) 0L else (Math.random() * window).toLong()
    },
) {
    private val lock = Any()

    private var loaded = false

    private var events = mutableListOf<PendingScrobble>()

    /**
     * Accepts [event] for delivery.
     *
     * Returns false when an identical listen is already queued — that is a
     * duplicate callback, not a failure, and the caller should not schedule
     * anything for it.
     */
    fun enqueue(event: PendingScrobble): Boolean =
        synchronized(lock) {
            loadLocked()
            if (events.any { it.key == event.key }) return false
            events += event.copy(attempts = 0, nextAttemptAtMs = 0L, lastError = null)
            trimLocked()
            persistLocked()
            true
        }

    /** How many listens are queued, optionally for one account. */
    fun pendingCount(account: String? = null): Int =
        synchronized(lock) {
            loadLocked()
            if (account == null) events.size else events.count { it.account == account }
        }

    /** A copy of what is queued, for diagnostics. */
    fun snapshot(): List<PendingScrobble> =
        synchronized(lock) {
            loadLocked()
            events.toList()
        }

    /** Everything queued for one account, e.g. when that account signs out. */
    fun clearAccount(account: String): Int {
        if (account.isBlank()) return 0
        return removeWhere { it.account == account }
    }

    fun clearProvider(provider: ScrobbleProvider): Int = removeWhere { it.provider == provider }

    /**
     * Forgets every listen belonging to an account that is no longer signed in.
     *
     * Called before a drain with the accounts currently configured. Anything
     * else is either a leftover from a sign-out that raced the cleanup or from
     * a credential that has since been replaced; either way it must not sit on
     * disk, and must never be sent under the current account.
     */
    fun retainAccounts(accounts: Set<String>): Int = removeWhere { it.account !in accounts }

    /**
     * Attempts delivery of every due listen for [currentAccounts].
     *
     * [send] is called outside the lock, one event at a time, so a slow service
     * cannot block [enqueue] and the ordering within an account is stable.
     * Exceptions other than cancellation are treated as transient: a bug in a
     * provider client must not throw away a listen.
     */
    suspend fun drain(
        currentAccounts: Set<String>,
        send: suspend (PendingScrobble) -> ScrobbleDelivery,
    ): DrainResult {
        val due =
            synchronized(lock) {
                loadLocked()
                val now = clock()
                events.filter { it.account in currentAccounts && it.nextAttemptAtMs <= now }
            }

        var sent = 0
        var dropped = 0
        var retrying = 0

        for (event in due) {
            val outcome =
                try {
                    send(event)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Throwable) {
                    ScrobbleDelivery.Transient(failure.message ?: failure::class.java.simpleName)
                }

            synchronized(lock) {
                val index = events.indexOfFirst { it.key == event.key }
                if (index < 0) return@synchronized
                when (outcome) {
                    ScrobbleDelivery.Sent -> {
                        events.removeAt(index)
                        sent++
                    }

                    is ScrobbleDelivery.Permanent -> {
                        events.removeAt(index)
                        dropped++
                    }

                    is ScrobbleDelivery.Transient -> {
                        val attempts = events[index].attempts + 1
                        if (attempts >= maxAttempts) {
                            // Out of attempts. Keeping it would grow the queue
                            // forever on a service that is never coming back.
                            events.removeAt(index)
                            dropped++
                        } else {
                            events[index] =
                                events[index].copy(
                                    attempts = attempts,
                                    lastError = outcome.reason,
                                    nextAttemptAtMs =
                                        clock() + backoffMs(attempts, outcome.retryAfterMs),
                                )
                            retrying++
                        }
                    }
                }
            }
        }

        if (due.isNotEmpty()) {
            synchronized(lock) { persistLocked() }
        }

        return DrainResult(
            sent = sent,
            dropped = dropped,
            retrying = retrying,
            remaining = pendingCount(),
        )
    }

    /**
     * Delay before attempt number [attempt] (1-based).
     *
     * Doubling from [baseBackoffMs], capped, with a little jitter so a service
     * coming back up does not receive every user's backlog in the same second.
     * A `Retry-After` from the service wins when it is shorter, because the
     * service knows better than our curve does.
     */
    internal fun backoffMs(
        attempt: Int,
        retryAfterMs: Long? = null,
    ): Long {
        if (retryAfterMs != null && retryAfterMs > 0L) return min(retryAfterMs, maxBackoffMs)
        val factor = 2.0.pow((attempt - 1).coerceAtLeast(0))
        val window = min(maxBackoffMs, (baseBackoffMs * factor).toLong())
        val spread = min(window / 5, MAX_JITTER_MS)
        return (window + jitter(spread)).coerceAtLeast(0L)
    }

    private fun removeWhere(predicate: (PendingScrobble) -> Boolean): Int =
        synchronized(lock) {
            loadLocked()
            val before = events.size
            events.removeAll(predicate)
            val removed = before - events.size
            if (removed > 0) persistLocked()
            removed
        }

    /**
     * Drops the oldest rows once the queue is over [capacity].
     *
     * Bounded on purpose: a phone that is offline for a month should not carry
     * an unbounded history of what someone listened to, and the oldest rows are
     * the ones a service would reject as too old anyway.
     */
    private fun trimLocked() {
        val limit = capacity.coerceAtLeast(1)
        if (events.size <= limit) return
        events = events.takeLast(limit).toMutableList()
    }

    private fun persistLocked() {
        val payload =
            buildJsonObject {
                put("version", FORMAT_VERSION)
                putJsonArray("events") {
                    events.forEach { add(json.encodeToJsonElement(PendingScrobble.serializer(), it)) }
                }
            }
        runCatching { store.write(payload.toString()) }
    }

    /**
     * Reads the queue, salvaging whatever is readable.
     *
     * A single unreadable row must not cost the others: a truncated write, an
     * older format or one field that no longer parses is skipped rather than
     * turning the whole backlog into zero. A blob that is not JSON at all
     * yields an empty queue, which is the only safe reading of it.
     */
    private fun loadLocked() {
        if (loaded) return
        loaded = true
        val raw = runCatching { store.read() }.getOrNull() ?: return
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return
        val array =
            root["events"]?.let { element -> runCatching { element.jsonArray }.getOrNull() } ?: return
        val recovered =
            array.mapNotNull { element ->
                runCatching {
                    json.decodeFromJsonElement(PendingScrobble.serializer(), element)
                }.getOrNull()
            }
        // A queued duplicate is still a duplicate after a restart.
        val unique = LinkedHashMap<String, PendingScrobble>()
        recovered.forEach { unique[it.key] = it }
        events = unique.values.toMutableList()
        trimLocked()
    }

    companion object {
        private const val FORMAT_VERSION = 1
        private const val DEFAULT_CAPACITY = 500
        private const val DEFAULT_BASE_BACKOFF_MS = 30_000L
        private const val DEFAULT_MAX_BACKOFF_MS = 6 * 60 * 60 * 1000L
        private const val DEFAULT_MAX_ATTEMPTS = 8
        private const val MAX_JITTER_MS = 60_000L
        private const val FINGERPRINT_BYTES = 8

        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        /**
         * A stable, non-reversible id for a credential.
         *
         * The queue needs to know which account a listen belongs to, and it
         * must be able to say so without writing the session key or token to
         * disk. Sixteen hex characters of SHA-256 is ample to tell two
         * accounts apart and tells an attacker nothing.
         */
        fun fingerprint(secret: String): String {
            if (secret.isEmpty()) return ""
            val digest = MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8))
            return digest.take(FINGERPRINT_BYTES).joinToString("") {
                String.format(Locale.ROOT, "%02x", it)
            }
        }
    }
}
