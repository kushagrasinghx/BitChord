package com.music.bitchord.playback

import com.music.bitchord.data.model.Song
import java.util.Collections

/**
 * Identity of a row within a collection. A playlist may list the same video
 * twice; YouTube tells those rows apart by [Song.setVideoId], so both survive.
 */
fun Song.contextEntryKey(): String =
    setVideoId?.takeIf { it.isNotBlank() }?.let { "$videoId@$it" } ?: videoId

/** The state of the continuation token owned by a registered context. */
enum class ContinuationState {
    /** A token is available and no request is currently using it. */
    AVAILABLE,

    /** A loader has claimed the token and may complete it asynchronously. */
    IN_FLIGHT,

    /** The last accepted page had no continuation token. */
    EXHAUSTED,

    /** A page made no progress or repeated a token, so loading is stopped. */
    BLOCKED,
}

/** A reason the registry stopped or rejected continuation work. */
enum class ContinuationDiagnostic {
    /** A page contained no song whose video id was new to the canonical list. */
    DUPLICATE_PAGE,

    /** A page contained no songs at all. */
    NO_PROGRESS,

    /** A page returned a continuation token already seen for this context. */
    REPEATED_CONTINUATION,

    /** A network/load failure released the request for a later retry. */
    REQUEST_FAILED,
}

/**
 * Read-only state of the active context.
 *
 * [songs] is a defensive, unmodifiable copy. It is the canonical order; queue
 * window or player code must not use a materialised window as a replacement for
 * this list.
 */
data class ContextSnapshot(
    val contextToken: String,
    val sourceKey: String,
    val browseId: String?,
    val songs: List<Song>,
    val version: Long,
    val continuationToken: String?,
    val continuationState: ContinuationState,
    val diagnostic: ContinuationDiagnostic? = null,
    /** Continuation tokens already accepted by this context, in encounter order. */
    val seenContinuationTokens: List<String> = emptyList(),
)

/** The immutable page returned by a continuation loader. */
data class ContinuationPage(
    val songs: List<Song>,
    val continuationToken: String?,
)

/**
 * An exclusive claim on one continuation token.
 *
 * The request carries the context version as well as the token. Either one can
 * make a late completion stale after registration or another append.
 */
data class ContinuationRequest(
    val requestId: Long,
    val contextToken: String,
    val sourceKey: String,
    val browseId: String?,
    val version: Long,
    val continuationToken: String,
)

enum class AppendStatus {
    APPENDED,
    NO_NEW_SONGS,
    STALE_CONTEXT,
    NO_ACTIVE_CONTEXT,
}

data class AppendResult(
    val status: AppendStatus,
    val snapshot: ContextSnapshot?,
    val addedSongs: List<Song> = emptyList(),
)

enum class ContinuationResultStatus {
    COMPLETED,
    FAILED,
    REJECTED_NO_PROGRESS,
    STALE_REQUEST,
}

data class ContinuationResult(
    val status: ContinuationResultStatus,
    val snapshot: ContextSnapshot?,
    val addedSongs: List<Song> = emptyList(),
    val diagnostic: ContinuationDiagnostic? = null,
    val error: Throwable? = null,
)

/**
 * Thread-safe, player-free owner of one active browsed context.
 *
 * The service that owns a loader claims a continuation request, performs I/O,
 * and then calls [complete] or [fail]. Registering a new context invalidates
 * every request belonging to the previous one. There is never more than one
 * claimed request for the active continuation token.
 */
class ContextRegistry(
    onGrowth: (ContextSnapshot) -> Unit = {},
) {
    private val lock = Any()
    private var active: MutableContext? = null
    private var nextVersion = 0L
    private var nextRequestId = 0L
    private var nextListenerId = 0L
    private val growthListeners = LinkedHashMap<Long, (ContextSnapshot) -> Unit>()

    init {
        growthListeners[nextListenerId++] = onGrowth
    }

    /**
     * Replaces the active context and invalidates any in-flight continuation.
     * The initial list is de-duplicated by [contextEntryKey] in arrival order.
     */
    fun register(
        contextToken: String,
        sourceKey: String,
        browseId: String?,
        songs: List<Song>,
        continuationToken: String? = null,
    ): ContextSnapshot {
        val snapshot = synchronized(lock) {
            val token = continuationToken.normalizedToken()
            val version = newVersionLocked()
            active = MutableContext(
                contextToken = contextToken,
                sourceKey = sourceKey,
                browseId = browseId,
                songs = immutableSongs(songs),
                version = version,
                continuationToken = token,
                continuationState = if (token == null) {
                    ContinuationState.EXHAUSTED
                } else {
                    ContinuationState.AVAILABLE
                },
                diagnostic = null,
                seenContinuationTokens = token?.let { linkedSetOf(it) } ?: linkedSetOf<String>(),
                request = null,
            )
            active!!.snapshot()
        }
        return snapshot
    }

    /**
     * Restores a previously persisted context without treating its continuation
     * state as a new registration.  An interrupted request cannot be resumed
     * safely after process death, so it becomes available for a retry.  Terminal
     * states remain terminal even when a stale token is still present in the
     * persisted record.
     */
    fun restore(snapshot: ContextSnapshot): ContextSnapshot {
        require(snapshot.contextToken.isNotBlank()) { "contextToken must not be blank" }
        require(snapshot.sourceKey.isNotBlank()) { "sourceKey must not be blank" }
        val songs = immutableRestoredSongs(snapshot.songs)
        val token = snapshot.continuationToken.normalizedToken()
        when (snapshot.continuationState) {
            ContinuationState.AVAILABLE,
            ContinuationState.IN_FLIGHT,
            -> require(token != null) {
                "${snapshot.continuationState} snapshots require a continuation token"
            }
            ContinuationState.EXHAUSTED,
            ContinuationState.BLOCKED,
            -> Unit
        }
        val seenTokens = LinkedHashSet<String>(snapshot.seenContinuationTokens.size + 1)
        snapshot.seenContinuationTokens.forEach { seenToken ->
            require(seenToken.isNotBlank()) {
                "seen continuation tokens must not be blank"
            }
            require(seenTokens.add(seenToken)) {
                "seen continuation tokens must be unique"
            }
        }
        token?.let(seenTokens::add)

        val restored = synchronized(lock) {
            val state = when (snapshot.continuationState) {
                ContinuationState.IN_FLIGHT -> ContinuationState.AVAILABLE
                else -> snapshot.continuationState
            }
            val version = newVersionLocked()
            active = MutableContext(
                contextToken = snapshot.contextToken,
                sourceKey = snapshot.sourceKey,
                browseId = snapshot.browseId,
                songs = songs,
                version = version,
                continuationToken = token,
                continuationState = state,
                diagnostic = snapshot.diagnostic,
                seenContinuationTokens = seenTokens,
                request = null,
            )
            active!!.snapshot()
        }
        return restored
    }

    /** Returns the current canonical state, or null before the first register. */
    fun snapshot(): ContextSnapshot? = synchronized(lock) { active?.snapshot() }

    /**
     * Adds songs to the active canonical list. This is intended for direct
     * service-side additions; continuation responses should use [complete] so
     * their request and page token are checked together.
     *
     * [expectedVersion] is optional for callers that already captured a
     * snapshot. Supplying it prevents a late direct append from crossing a
     * context version boundary. An append that succeeds while a request is in
     * flight invalidates that request and makes its token available again.
     */
    fun append(
        contextToken: String,
        songs: List<Song>,
        expectedVersion: Long? = null,
    ): AppendResult {
        var callbackSnapshot: ContextSnapshot? = null
        val result = synchronized(lock) {
            val state = active ?: return@synchronized AppendResult(
                status = AppendStatus.NO_ACTIVE_CONTEXT,
                snapshot = null,
            )
            if (state.contextToken != contextToken ||
                (expectedVersion != null && state.version != expectedVersion)
            ) {
                return@synchronized AppendResult(
                    status = AppendStatus.STALE_CONTEXT,
                    snapshot = state.snapshot(),
                )
            }

            val added = newSongs(state.songs, songs)
            if (added.isEmpty()) {
                return@synchronized AppendResult(
                    status = AppendStatus.NO_NEW_SONGS,
                    snapshot = state.snapshot(),
                )
            }

            if (state.continuationState == ContinuationState.IN_FLIGHT) {
                state.request = null
                state.continuationState = if (state.continuationToken == null) {
                    ContinuationState.EXHAUSTED
                } else {
                    ContinuationState.AVAILABLE
                }
            }
            state.songs = immutableSongs(state.songs + added)
            state.version = newVersionLocked()
            state.diagnostic = null
            callbackSnapshot = state.snapshot()
            AppendResult(
                status = AppendStatus.APPENDED,
                snapshot = callbackSnapshot,
                addedSongs = immutableSongs(added),
            )
        }
        callbackSnapshot?.let(::notifyGrowth)
        return result
    }

    /** Adds a growth listener and returns its removal id. */
    fun addGrowthListener(listener: (ContextSnapshot) -> Unit): Long = synchronized(lock) {
        val id = nextListenerId++
        growthListeners[id] = listener
        id
    }

    fun removeGrowthListener(id: Long): Boolean = synchronized(lock) {
        growthListeners.remove(id) != null
    }

    /**
     * Claims the one available continuation request.
     *
     * Passing [expectedContextToken] is useful when a service has retained a
     * context handle and does not want to claim a newer context by accident.
     */
    fun claimContinuation(expectedContextToken: String? = null): ContinuationRequest? =
        synchronized(lock) {
            val state = active ?: return@synchronized null
            if (expectedContextToken != null && state.contextToken != expectedContextToken) {
                return@synchronized null
            }
            if (state.continuationState != ContinuationState.AVAILABLE ||
                state.continuationToken == null
            ) {
                return@synchronized null
            }

            val request = ContinuationRequest(
                requestId = nextRequestId++,
                contextToken = state.contextToken,
                sourceKey = state.sourceKey,
                browseId = state.browseId,
                version = state.version,
                continuationToken = state.continuationToken!!,
            )
            state.request = request
            state.continuationState = ContinuationState.IN_FLIGHT
            request
        }

    /**
     * Completes a claimed request if it still belongs to the active version.
     *
     * Pages with no new unique songs, or with a continuation token already seen
     * in this context, are rejected and block further claims. This deliberately
     * favors a visible diagnostic over an unbounded continuation loop.
     */
    fun complete(request: ContinuationRequest, page: ContinuationPage): ContinuationResult {
        var callbackSnapshot: ContextSnapshot? = null
        val result = synchronized(lock) {
            val state = active
            if (state == null || !state.matches(request)) {
                return@synchronized staleResult(state)
            }

            val nextToken = page.continuationToken.normalizedToken()
            val added = newSongs(state.songs, page.songs)
            val diagnostic = when {
                added.isEmpty() && page.songs.isEmpty() -> ContinuationDiagnostic.NO_PROGRESS
                added.isEmpty() -> ContinuationDiagnostic.DUPLICATE_PAGE
                nextToken != null && nextToken in state.seenContinuationTokens ->
                    ContinuationDiagnostic.REPEATED_CONTINUATION
                else -> null
            }

            if (diagnostic != null) {
                state.request = null
                state.continuationState = ContinuationState.BLOCKED
                state.diagnostic = diagnostic
                return@synchronized ContinuationResult(
                    status = ContinuationResultStatus.REJECTED_NO_PROGRESS,
                    snapshot = state.snapshot(),
                    diagnostic = diagnostic,
                )
            }

            state.request = null
            state.songs = immutableSongs(state.songs + added)
            state.continuationToken = nextToken
            state.continuationState = if (nextToken == null) {
                ContinuationState.EXHAUSTED
            } else {
                state.seenContinuationTokens += nextToken
                ContinuationState.AVAILABLE
            }
            state.version = newVersionLocked()
            state.diagnostic = null
            callbackSnapshot = state.snapshot()
            ContinuationResult(
                status = ContinuationResultStatus.COMPLETED,
                snapshot = callbackSnapshot,
                addedSongs = immutableSongs(added),
            )
        }
        callbackSnapshot?.let(::notifyGrowth)
        return result
    }

    /**
     * Releases a claimed request after a loader failure. The same continuation
     * token remains available for a later retry; a stale failure is ignored.
     */
    fun fail(request: ContinuationRequest, error: Throwable? = null): ContinuationResult =
        synchronized(lock) {
            val state = active
            if (state == null || !state.matches(request)) {
                return@synchronized staleResult(state)
            }

            state.request = null
            state.continuationState = if (state.continuationToken == null) {
                ContinuationState.EXHAUSTED
            } else {
                ContinuationState.AVAILABLE
            }
            state.diagnostic = ContinuationDiagnostic.REQUEST_FAILED
            ContinuationResult(
                status = ContinuationResultStatus.FAILED,
                snapshot = state.snapshot(),
                diagnostic = ContinuationDiagnostic.REQUEST_FAILED,
                error = error,
            )
        }

    private fun staleResult(state: MutableContext?): ContinuationResult = ContinuationResult(
        status = ContinuationResultStatus.STALE_REQUEST,
        snapshot = state?.snapshot(),
    )

    private fun notifyGrowth(snapshot: ContextSnapshot) {
        val listeners = synchronized(lock) { growthListeners.values.toList() }
        listeners.forEach { it(snapshot) }
    }

    private fun newVersionLocked(): Long {
        nextVersion += 1
        return nextVersion
    }

    private class MutableContext(
        val contextToken: String,
        val sourceKey: String,
        val browseId: String?,
        var songs: List<Song>,
        var version: Long,
        var continuationToken: String?,
        var continuationState: ContinuationState,
        var diagnostic: ContinuationDiagnostic?,
        val seenContinuationTokens: MutableSet<String>,
        var request: ContinuationRequest?,
    ) {
        fun snapshot() = ContextSnapshot(
            contextToken = contextToken,
            sourceKey = sourceKey,
            browseId = browseId,
            songs = songs,
            version = version,
            continuationToken = continuationToken,
            continuationState = continuationState,
            diagnostic = diagnostic,
            seenContinuationTokens = Collections.unmodifiableList(seenContinuationTokens.toList()),
        )

        fun matches(candidate: ContinuationRequest): Boolean =
            continuationState == ContinuationState.IN_FLIGHT &&
                request == candidate &&
                candidate.contextToken == contextToken &&
                candidate.version == version &&
                candidate.continuationToken == continuationToken
    }

    private companion object {
        fun String?.normalizedToken(): String? = this?.takeIf { it.isNotBlank() }

        fun immutableRestoredSongs(songs: List<Song>): List<Song> {
            val ids = HashSet<String>(songs.size)
            songs.forEach { song ->
                require(song.videoId.isNotBlank()) {
                    "restored songs must not contain blank video ids"
                }
                require(ids.add(song.contextEntryKey())) {
                    "restored songs must not contain duplicate video ids"
                }
            }
            return Collections.unmodifiableList(songs.toList())
        }

        fun immutableSongs(songs: Collection<Song>): List<Song> {
            val byVideoId = LinkedHashMap<String, Song>(songs.size)
            songs.forEach { byVideoId.putIfAbsent(it.contextEntryKey(), it) }
            return Collections.unmodifiableList(byVideoId.values.toList())
        }

        fun newSongs(existing: List<Song>, candidates: List<Song>): List<Song> {
            val known = existing.asSequence().mapTo(HashSet()) { it.contextEntryKey() }
            val added = ArrayList<Song>()
            candidates.forEach { candidate ->
                if (known.add(candidate.contextEntryKey())) added += candidate
            }
            return added
        }
    }
}
