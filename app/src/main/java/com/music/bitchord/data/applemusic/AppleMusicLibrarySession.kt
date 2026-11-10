package com.music.bitchord.data.applemusic

import java.io.Closeable
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * Share one session between browse/import controllers. Call [refresh] immediately after
 * every authorization/account change, including replacing a token while still connected.
 * With [scope], disconnects are also observed automatically. Every response checks the
 * credential identity again, so even a non-cooperative old request cannot publish data.
 * Neither credentials nor account identifiers are exposed in presentation state.
 */
class AppleMusicLibrarySession(
    scope: CoroutineScope? = null,
    private val accountKey: () -> String? = { AppleMusicTokens.userToken() },
    private val userStorefront: () -> String? = { AppleMusicTokens.storefront() },
    connectionChanges: Flow<Boolean> = AppleMusicTokens.connected,
) : Closeable {
    data class State(val connected: Boolean, val storefront: String?, val revision: Long)

    private val lock = Any()
    private val samples = AtomicLong(0)
    private var appliedSample = 0L
    private var identity = accountKey()?.takeIf { it.isNotBlank() }
    private val mutableState = MutableStateFlow(State(identity != null, readStorefront(), 0))
    val state = mutableState.asStateFlow()
    private val listeners = mutableSetOf<(State) -> Unit>()
    private var closed = false
    private val observer = scope?.launch(start = CoroutineStart.UNDISPATCHED) {
        connectionChanges.collect { refresh() }
    }

    fun refresh(): State {
        // Token storage can synchronously notify connection observers while holding its
        // own lock. Never acquire that lock while holding the presentation/session lock.
        val sample = samples.incrementAndGet()
        val account = accountKey()?.takeIf { it.isNotBlank() }
        val storefront = readStorefront()
        return locked {
            if (closed || sample < appliedSample) return@locked mutableState.value
            appliedSample = sample
            if (identity != account || mutableState.value.storefront != storefront) {
                identity = account
                mutableState.value = State(account != null, storefront, mutableState.value.revision + 1)
                listeners.toList().forEach { it(mutableState.value) }
            }
            mutableState.value
        }
    }

    private fun readStorefront(): String? = userStorefront()?.trim()?.lowercase(Locale.ROOT)
        ?.takeIf { it.matches(Regex("[a-z]{2}")) }

    internal fun <T> locked(block: () -> T): T = synchronized(lock, block)

    internal fun listen(listener: (State) -> Unit): Closeable = locked {
        check(!closed) { "Apple Music library session is closed" }
        listeners += listener
        Closeable { locked { listeners -= listener } }
    }

    internal fun isCurrent(expected: State): Boolean = locked {
        !closed && mutableState.value.revision == expected.revision
    }

    override fun close() = locked {
        if (closed) return@locked
        observer?.cancel()
        closed = true
        identity = null
        mutableState.value = State(false, null, mutableState.value.revision + 1)
        listeners.toList().forEach { it(mutableState.value) }
        listeners.clear()
    }
}

/** Serializes each controller's selections without requiring a Main dispatcher or a ViewModel. */
internal class AppleMusicLibraryRequests(private val session: AppleMusicLibrarySession) : Closeable {
    private var serial = 0L
    private var activeJob: Job? = null
    private var closed = false

    inner class Ticket internal constructor(
        private val id: Long,
        val account: AppleMusicLibrarySession.State,
        private val job: Job,
    ) {
        fun check() = publish {}

        fun publish(block: () -> Unit) {
            session.refresh()
            session.locked {
                job.ensureActive()
                if (!session.isCurrent(account) || id != serial || closed) {
                    throw CancellationException("Apple Music library request was superseded")
                }
                block()
            }
        }

        fun finish(block: () -> Unit) {
            session.refresh()
            session.locked {
                if (session.isCurrent(account) && id == serial && !closed) block()
            }
        }
    }

    suspend fun <T> run(
        onStart: (AppleMusicLibrarySession.State) -> Unit,
        onCancelled: () -> Unit,
        block: suspend (Ticket) -> T,
    ): T = coroutineScope {
        val job = currentCoroutineContext().job
        session.refresh()
        val ticket = session.locked {
            check(!closed) { "Apple Music controller is closed" }
            val account = session.state.value
            check(session.isCurrent(account)) { "Apple Music library session is closed" }
            cancel()
            activeJob = job
            onStart(account)
            Ticket(serial, account, job)
        }
        try {
            ticket.check()
            block(ticket).also { ticket.check() }
        } catch (cancelled: CancellationException) {
            ticket.finish(onCancelled)
            throw cancelled
        } finally {
            ticket.finish { activeJob = null }
        }
    }

    fun cancel() = session.locked {
        serial++
        activeJob?.cancel()
        activeJob = null
    }

    override fun close() = session.locked {
        cancel()
        closed = true
    }
}
