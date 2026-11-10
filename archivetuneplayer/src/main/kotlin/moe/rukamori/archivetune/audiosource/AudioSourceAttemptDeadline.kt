
package moe.rukamori.archivetune.audiosource

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

class AudioSourceAttemptDeadline private constructor(
    private val deadlineNanos: Long,
    private val nanoTime: () -> Long,
) {
    fun remainingMillis(): Long {
        val remainingNanos = deadlineNanos - nanoTime()
        if (remainingNanos <= 0L) return 0L
        val wholeMillis = TimeUnit.NANOSECONDS.toMillis(remainingNanos)
        return wholeMillis + if (TimeUnit.MILLISECONDS.toNanos(wholeMillis) == remainingNanos) 0L else 1L
    }

    fun isExpired(): Boolean = remainingMillis() == 0L

    fun child(timeoutMillis: Long): AudioSourceAttemptDeadline {
        require(timeoutMillis > 0L)
        val now = nanoTime()
        val remainingNanos = (deadlineNanos - now).coerceAtLeast(0L)
        val childNanos = minOf(TimeUnit.MILLISECONDS.toNanos(timeoutMillis), remainingNanos)
        return AudioSourceAttemptDeadline(now + childNanos, nanoTime)
    }

    suspend fun <T> runWithin(block: suspend () -> T): T? {
        val remaining = remainingMillis()
        if (remaining == 0L) return null
        return withTimeoutOrNull(remaining) { block() }
    }

    companion object {
        fun start(
            timeoutMillis: Long,
            nanoTime: () -> Long = System::nanoTime,
        ): AudioSourceAttemptDeadline {
            require(timeoutMillis > 0L)
            return AudioSourceAttemptDeadline(
                deadlineNanos = nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis),
                nanoTime = nanoTime,
            )
        }
    }
}

object AudioSourceAttemptTimeouts {
    const val PROVIDER_ATTEMPT_MS = 8_000L
    const val FALLBACK_CHAIN_MS = 30_000L
    const val AMAZON_STREAM_TRANSFER_MS = 120_000L
}

object AudioSourceAttemptScope {
    private val activeDeadline = ThreadLocal<AudioSourceAttemptDeadline?>()

    fun current(): AudioSourceAttemptDeadline? = activeDeadline.get()

    fun <T> within(
        timeoutMillis: Long = AudioSourceAttemptTimeouts.PROVIDER_ATTEMPT_MS,
        block: () -> T,
    ): T {
        val parent = activeDeadline.get()
        val deadline = parent?.child(timeoutMillis) ?: AudioSourceAttemptDeadline.start(timeoutMillis)
        return withDeadline(deadline, block)
    }

    fun <T> withDeadline(
        deadline: AudioSourceAttemptDeadline,
        block: () -> T,
    ): T {
        val previous = activeDeadline.get()
        activeDeadline.set(deadline)
        return try {
            block()
        } finally {
            if (previous == null) {
                activeDeadline.remove()
            } else {
                activeDeadline.set(previous)
            }
        }
    }

    suspend fun <T> withinSuspending(
        timeoutMillis: Long = AudioSourceAttemptTimeouts.PROVIDER_ATTEMPT_MS,
        block: suspend CoroutineScope.() -> T,
    ): T? {
        val parent = activeDeadline.get()
        val deadline = parent?.child(timeoutMillis) ?: AudioSourceAttemptDeadline.start(timeoutMillis)
        return withContext(activeDeadline.asContextElement(deadline)) {
            deadline.runWithin { block(this) }
        }
    }

    fun coroutineContextElement(): CoroutineContext {
        val deadline = activeDeadline.get() ?: return kotlin.coroutines.EmptyCoroutineContext
        return activeDeadline.asContextElement(deadline)
    }
}

fun Call.withAudioSourceAttemptDeadline(): Call {
    val deadline = AudioSourceAttemptScope.current() ?: return this
    val remaining = deadline.remainingMillis()
    if (remaining == 0L) throw AudioSourceAttemptTimedOutException()
    timeout().timeout(remaining, TimeUnit.MILLISECONDS)
    return this
}

class AudioSourceAttemptTimedOutException : SocketTimeoutException("Audio source attempt deadline exceeded")

fun Throwable.rethrowIfAudioSourceCancelled() {
    var cause: Throwable? = this
    var attemptTimedOut = false
    while (cause != null) {
        if (cause is SocketTimeoutException ||
            cause is InterruptedIOException && cause.message.equals("timeout", ignoreCase = true)
        ) {
            attemptTimedOut = true
        }
        when {
            cause is CancellationException -> throw cause
            cause is InterruptedException ->
                throw CancellationException("Audio source resolution cancelled").apply { initCause(this@rethrowIfAudioSourceCancelled) }
            cause is IOException && cause.message.equals("Canceled", ignoreCase = true) && !attemptTimedOut ->
                throw CancellationException("Audio source resolution cancelled").apply { initCause(this@rethrowIfAudioSourceCancelled) }
        }
        cause = cause.cause
    }
    if (Thread.currentThread().isInterrupted) {
        throw CancellationException("Audio source resolution cancelled").apply { initCause(this@rethrowIfAudioSourceCancelled) }
    }
}
