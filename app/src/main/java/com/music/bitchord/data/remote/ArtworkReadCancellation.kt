package com.music.bitchord.data.remote

import java.io.InterruptedIOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

/** Own only the active artwork transport, never a playback socket or session. */
internal class ArtworkReadCancellation {
    private var stopped = false
    private var abort: (() -> Unit)? = null

    fun closeOnCancel(close: () -> Unit) {
        val cancelNow = synchronized(this) {
            if (stopped) true else {
                abort = close
                false
            }
        }
        if (cancelNow) {
            runCatching(close)
            throw InterruptedIOException("Artwork read cancelled")
        }
    }

    fun cancel() {
        val close = synchronized(this) {
            stopped = true
            abort.also { abort = null }
        }
        if (close != null) runCatching(close)
    }
}

/** Wait for the old blocking reader to exit before a replacement target can start. */
internal suspend fun <T> cancellableArtworkRead(block: (ArtworkReadCancellation) -> T): T = coroutineScope {
    val transport = ArtworkReadCancellation()
    val closer = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            transport.cancel()
        }
    }
    try {
        runInterruptible(Dispatchers.IO) { block(transport) }
    } finally {
        closer.cancel()
    }
}
