package com.music.bitchord.playback

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Process-local handoff for a bounded-context startup request.
 *
 * Media-session command arguments cross Binder, so only the token is sent in
 * the command. The complete request stays in this process and is consumed by
 * the service through [take]. A single pending request is intentional: if a
 * newer tap wins before the service handles an older command, the older
 * command must not start playback after it.
 */
object BoundedContextRequests {
    data class Request(
        val songs: List<Song>,
        val startIndex: Int,
        val source: QueueSource,
        val continuationToken: String?,
        val shuffle: Boolean,
        /** Opaque handoff id, filled by [put] and returned by [take]. */
        val handoffToken: String? = null,
    )

    /**
     * The latest canonical context known by the process.
     *
     * The service owns continuation loading after [take]. Publishing its
     * registry snapshot here lets the UI keep showing the same page as that
     * context grows, and gives a later tap the newest list and token without
     * sending the whole list through Binder again.
     */
    data class Snapshot(
        val request: Request,
        val songs: List<Song>,
        val continuationToken: String?,
        val version: Long,
        /** The service has taken this context and is walking its continuation. */
        val serviceOwned: Boolean = false,
        /** The service stopped walking the continuation; the page should resume it. */
        val released: Boolean = false,
    )

    private data class Pending(
        val token: String,
        val request: Request,
    )

    private var pending: Pending? = null
    private var active: Snapshot? = null
    private var version = 0L

    private val _snapshot = MutableStateFlow<Snapshot?>(null)

    /** Latest bounded context, including service-side continuation growth. */
    val snapshot: StateFlow<Snapshot?> = _snapshot.asStateFlow()

    @Synchronized
    fun put(request: Request): String {
        val token = UUID.randomUUID().toString()
        val stored = request.copy(
            songs = request.songs.toList(),
            handoffToken = token,
        )
        pending = Pending(token, stored)
        version += 1
        publishLocked(
            Snapshot(
                request = stored,
                songs = stored.songs,
                continuationToken = stored.continuationToken,
                version = version,
            ),
        )
        return token
    }

    @Synchronized
    fun take(token: String?): Request? {
        if (token == null) return null
        val current = pending ?: return null
        if (current.token != token) return null
        pending = null
        return current.request
    }

    /**
     * Replaces the active context's canonical list after the service accepts a
     * continuation page. [request] is the value returned by [take], so a late
     * service completion cannot overwrite a newer tap.
     */
    @Synchronized
    fun publish(
        request: Request,
        songs: List<Song>,
        continuationToken: String?,
    ): Boolean {
        val current = active ?: return false
        if (current.request.handoffToken != request.handoffToken ||
            request.handoffToken == null
        ) return false
        version += 1
        val updatedRequest = current.request.copy(
            songs = songs.toList(),
            continuationToken = continuationToken,
        )
        publishLocked(
            Snapshot(
                request = updatedRequest,
                songs = updatedRequest.songs,
                continuationToken = updatedRequest.continuationToken,
                version = version,
                serviceOwned = true,
            ),
        )
        return true
    }

    /**
     * The service gave up on the active context's continuation (repeated load
     * failures). Republished without service ownership so the detail page picks
     * the walk back up from the newest token instead of stopping short.
     */
    @Synchronized
    fun release(handoffToken: String) {
        val current = active ?: return
        if (current.request.handoffToken != handoffToken) return
        version += 1
        publishLocked(current.copy(version = version, serviceOwned = false, released = true))
    }

    /** Publishes a shared [ContextRegistry] snapshot without unpacking it first. */
    @Synchronized
    fun publish(request: Request, snapshot: ContextSnapshot): Boolean =
        publish(request, snapshot.songs, snapshot.continuationToken)

    /**
     * Rehydrates the process-local UI handoff after a service restart.  There
     * is no pending Binder command in this case, but keeping an active snapshot
     * lets the detail page observe continuation growth just as it did before
     * process death.
     */
    @Synchronized
    fun restore(request: Request): Request {
        val stored = request.copy(
            songs = request.songs.toList(),
            handoffToken = request.handoffToken ?: UUID.randomUUID().toString(),
        )
        version += 1
        publishLocked(
            Snapshot(
                request = stored,
                songs = stored.songs,
                continuationToken = stored.continuationToken,
                version = version,
                serviceOwned = true,
            ),
        )
        return stored
    }

    /** Same as [publish], addressed by the opaque token retained in [Request]. */
    @Synchronized
    fun publish(
        handoffToken: String,
        songs: List<Song>,
        continuationToken: String?,
    ): Boolean {
        val request = active?.request?.takeIf { it.handoffToken == handoffToken } ?: return false
        return publish(request, songs, continuationToken)
    }

    @Synchronized
    fun discard(token: String) {
        if (pending?.token == token) pending = null
        if (active?.request?.handoffToken == token) {
            active = null
            publishLocked(null)
        }
    }

    /** Clears process-local state; useful when the service abandons a request. */
    @Synchronized
    fun clear() {
        pending = null
        active = null
        publishLocked(null)
    }

    private fun publishLocked(snapshot: Snapshot?) {
        active = snapshot
        _snapshot.value = snapshot
    }
}
