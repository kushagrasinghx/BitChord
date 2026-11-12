package com.music.bitchord.mcp

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** A hard wait bound for HTTP workers, even when a backend ignores coroutine cancellation.
 * Stuck backends cannot be safely killed on the JVM: at most four are retained, and further
 * authenticated work fails fast. No replacement thread is created for a stuck task.
 */
internal class McpRequestExecutor(private val timeoutMillis: Long = 16_000) : AutoCloseable {
    private val executor = ThreadPoolExecutor(4, 4, 10, TimeUnit.SECONDS,
        ArrayBlockingQueue(4), { task -> Thread(task, "mcp-dispatch").apply { isDaemon = true } })

    fun <T> execute(block: () -> T): T {
        val future = executor.submit<T> { block() }
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (e: ExecutionException) {
            throw (e.cause ?: e)
        } finally {
            future.cancel(true)
            executor.purge()
        }
    }
    override fun close() { executor.shutdownNow() }
}
