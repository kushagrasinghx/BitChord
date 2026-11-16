package com.music.bitchord

import com.music.bitchord.download.DrainSupervisor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The download drain's lifetime, driven rather than reasoned about.
 *
 * The defect these cover is a race between a drain finding the queue empty and
 * the service stopping: a track that arrives in that window used to be stranded
 * on "Queued" for the rest of the process, with `Downloads.enqueue` refusing
 * the id forever afterwards because it was still in the queue. A test of a
 * boolean helper could not have failed when that happened, which is why the
 * helper is gone and the decision now lives in something a test can drive.
 */
class DownloadDrainSupervisorTest {

    private class Harness {
        /** Start ids the supervisor asked to stop for, in order. */
        val stops = mutableListOf<Int>()

        /** Answers for `stopSelfResult`, consumed one per call; default is "stopped". */
        val stopAnswers = ArrayDeque<Boolean>()

        /** Answers for `hasWork`, consumed one per finish; default is [work]. */
        val workAnswers = ArrayDeque<Boolean>()
        var work = false

        var passes = 0
        var reports = 0
        var clears = 0
        var activePasses = 0
        var maxConcurrentPasses = 0

        /** Holds every pass from [gateFromPass] onwards open until the test releases it. */
        var gateFromPass = Int.MAX_VALUE
        var gate: CompletableDeferred<Unit>? = null

        fun supervisor(scope: CoroutineScope) = DrainSupervisor(
            scope = scope,
            hasWork = { workAnswers.removeFirstOrNull() ?: work },
            drainPass = {
                passes++
                activePasses++
                maxConcurrentPasses = maxOf(maxConcurrentPasses, activePasses)
                try {
                    if (passes >= gateFromPass) gate?.await()
                } finally {
                    activePasses--
                }
            },
            reporter = {
                reports++
                awaitCancellation()
            },
            stopService = { id ->
                stops += id
                stopAnswers.removeFirstOrNull() ?: true
            },
            clearNotification = { clears++ },
        )
    }

    /**
     * A scope on the test's own scheduler.
     *
     * Deliberately not `backgroundScope`: work launched there did not run under
     * [advanceUntilIdle] in this setup, which made every assertion below pass
     * or fail for reasons that had nothing to do with the supervisor. A scope
     * built on [TestScope.testScheduler] is explicit about which clock drives it.
     */
    private fun TestScope.drainScope(): CoroutineScope =
        CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())

    @Test
    fun `an idle service stops for the newest start id and stops reporting`() = runTest {
        val harness = Harness()
        val scope = drainScope()
        val supervisor = harness.supervisor(scope)

        supervisor.onStartCommand(7)
        advanceUntilIdle()

        assertEquals(listOf(7), harness.stops)
        assertEquals(1, harness.passes)
        assertEquals(1, harness.reports)
        assertEquals(1, harness.clears)
        assertFalse(supervisor.running)
        assertFalse(supervisor.reporting)
        scope.cancel()
    }

    @Test
    fun `work arriving during shutdown restarts the drain and its progress reporting`() = runTest {
        val harness = Harness()
        // The first finish sees a track that landed after the workers' last
        // empty poll; the second sees the queue genuinely empty.
        harness.workAnswers.addAll(listOf(true, false))
        val scope = drainScope()
        val supervisor = harness.supervisor(scope)

        supervisor.onStartCommand(3)
        advanceUntilIdle()

        assertEquals(2, harness.passes)
        assertEquals("progress must be restarted with the drain", 2, harness.reports)
        assertEquals(listOf(3), harness.stops)
        assertFalse(supervisor.running)
        assertFalse(supervisor.reporting)
        scope.cancel()
    }

    @Test
    fun `a restarted drain reports progress while it works`() = runTest {
        val harness = Harness()
        harness.workAnswers.add(true)
        harness.gateFromPass = 2
        harness.gate = CompletableDeferred()
        val scope = drainScope()
        val supervisor = harness.supervisor(scope)

        supervisor.onStartCommand(2)
        advanceUntilIdle()

        assertTrue(supervisor.running)
        assertTrue("the restarted drain must not run without its reporter", supervisor.reporting)
        assertEquals(2, harness.reports)
        assertEquals(emptyList<Int>(), harness.stops)

        harness.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(2), harness.stops)
        scope.cancel()
    }

    @Test
    fun `a newer start request that beats the stop decision keeps a drain running`() = runTest {
        val harness = Harness()
        // The platform refuses the stop: a start intent with a higher id is
        // pending. The service is alive and would otherwise have nothing in it.
        harness.stopAnswers.add(false)
        val scope = drainScope()
        val supervisor = harness.supervisor(scope)

        supervisor.onStartCommand(1)
        advanceUntilIdle()

        assertEquals(listOf(1, 1), harness.stops)
        assertEquals(2, harness.passes)
        assertEquals(2, harness.reports)
        assertEquals(1, harness.maxConcurrentPasses)
        assertFalse(supervisor.running)
        scope.cancel()
    }

    @Test
    fun `starts during a running drain keep one pass and the newest id`() = runTest {
        val harness = Harness()
        harness.gateFromPass = 1
        harness.gate = CompletableDeferred()
        val scope = drainScope()
        val supervisor = harness.supervisor(scope)

        supervisor.onStartCommand(1)
        advanceUntilIdle()
        assertEquals(1, harness.passes)

        // Concurrent enqueues: each starts the service again.
        supervisor.onStartCommand(2)
        supervisor.onStartCommand(3)
        advanceUntilIdle()

        assertEquals("must not start a second set of workers", 1, harness.passes)
        assertEquals(3, supervisor.startId)

        harness.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals("the stop must be judged against the newest id", listOf(3), harness.stops)
        assertEquals(1, harness.maxConcurrentPasses)
        scope.cancel()
    }

    @Test
    fun `repeated start and stop cycles never duplicate workers`() = runTest {
        val harness = Harness()
        val scope = drainScope()
        val supervisor = harness.supervisor(scope)

        repeat(5) { index ->
            val id = index + 1
            supervisor.onStartCommand(id)
            advanceUntilIdle()
            assertEquals(id, harness.stops.last())
            assertFalse(supervisor.running)
            assertFalse(supervisor.reporting)
        }

        assertEquals(5, harness.passes)
        assertEquals(5, harness.reports)
        assertEquals(1, harness.maxConcurrentPasses)
        scope.cancel()
    }

    @Test
    fun `cancelling everything stops the service without finishing the drain`() = runTest {
        val harness = Harness()
        harness.gateFromPass = 1
        harness.gate = CompletableDeferred()
        val scope = drainScope()
        val supervisor = harness.supervisor(scope)

        supervisor.onStartCommand(9)
        advanceUntilIdle()
        assertTrue(supervisor.running)

        supervisor.cancelAll()
        advanceUntilIdle()

        assertFalse(supervisor.running)
        assertFalse(supervisor.reporting)
        assertEquals(listOf(9), harness.stops)

        harness.gate!!.complete(Unit)
        advanceUntilIdle()
        // The cancelled pass must not resurrect itself, and no stop happens for
        // a drain that no longer exists.
        assertEquals(1, harness.passes)
        assertEquals(listOf(9), harness.stops)
        scope.cancel()
    }

    @Test
    fun `a cancel-all request on its own starts no drain`() = runTest {
        val harness = Harness()
        val scope = drainScope()
        val supervisor = harness.supervisor(scope)

        supervisor.onStartCommand(4, drain = false)
        advanceUntilIdle()

        assertEquals(0, harness.passes)
        assertEquals(0, harness.reports)
        assertEquals(emptyList<Int>(), harness.stops)
        assertEquals(4, supervisor.startId)
        scope.cancel()
    }
}
