package com.music.bitchord.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Decides how long [DownloadService] lives: when the drain starts, when it is
 * allowed to stop, and what happens to a track that arrives while it is
 * stopping.
 *
 * Split out of the service and kept free of Android types for one reason: this
 * is a small state machine around a race, and a race that is only ever reasoned
 * about is a race that comes back. Nothing here touches the platform, so the
 * concurrency can be driven by a test — a fake host that records the start ids
 * it was asked to stop for, and a drain pass the test can hold open while it
 * enqueues more work.
 *
 * ### The race it exists to close
 *
 * The drain finishes when its workers see an empty queue for a grace period.
 * Between that last look and the service actually stopping, a
 * [Downloads.enqueue] may land: it adds the track to the queue and starts the
 * service, which is already on its way out. The old code stopped
 * unconditionally, so whether that track was ever fetched came down to which
 * order two main-thread callbacks happened to run in — and when the service
 * lost, the row sat on "Queued" for the rest of the process, with [Downloads]
 * refusing a second tap because the id was still in its queue.
 *
 * Two separate guards, because there are two separate failure paths:
 *
 *  - **Work in the queue** ([hasWork]) is checked again after the last empty
 *    poll. If something arrived, the drain restarts instead of stopping. The
 *    notification has to be restarted with it, or the new drain runs invisibly
 *    while the shade keeps showing the track before last.
 *  - **A newer start request** is caught by [stopService], which is
 *    `stopSelfResult(lastStartId)` in production. If a start intent with a
 *    higher id beat the stop decision, that call returns false and the service
 *    is still alive — with no drain in it, which is the same stranding by
 *    another route. So a refused stop restarts the drain too.
 */
internal class DrainSupervisor(
    private val scope: CoroutineScope,
    /** Whether the queue still holds a track that was not fetched. */
    private val hasWork: () -> Boolean,
    /** One full pass of the workers over the queue; returns when it is empty. */
    private val drainPass: suspend () -> Unit,
    /** Keeps the notification current; cancelled with the pass it belongs to. */
    private val reporter: suspend () -> Unit,
    /** `stopSelfResult(startId)` in production; true when it is really stopping. */
    private val stopService: (startId: Int) -> Boolean,
    /** Remove the foreground notification — never stop the service. */
    private val clearNotification: () -> Unit,
) {

    private var drainJob: Job? = null
    private var reporterJob: Job? = null

    /**
     * The newest start request seen.
     *
     * This is the id [stopService] is asked about, and it is deliberately the
     * newest rather than the one the drain began with: a stop request for an
     * older id is exactly what the platform would ignore while a newer start is
     * pending, which would leave a live service with nothing draining it.
     */
    private var latestStartId = 0

    /** Whether a drain pass is in flight. */
    val running: Boolean get() = drainJob?.isActive == true

    /** Whether progress is being reflected into the notification. */
    val reporting: Boolean get() = reporterJob?.isActive == true

    /** The newest start id the service was started with. */
    val startId: Int get() = latestStartId

    /**
     * A start request arrived.
     *
     * Records the id even when a drain is already running — that is the whole
     * point of the id, and a drain picking up new work from the queue needs no
     * second one. @param drain false for a request that is not asking for work,
     * such as the cancel-all action.
     */
    fun onStartCommand(startId: Int, drain: Boolean = true) {
        latestStartId = startId
        if (!drain || running) return
        begin()
    }

    /** Start a pass and its reporter together, so neither can exist alone. */
    private fun begin() {
        // A reporter from the previous pass would otherwise keep posting the
        // same notification alongside this one, twice per refresh.
        reporterJob?.cancel()
        reporterJob = scope.launch { reporter() }
        drainJob = scope.launch {
            drainPass()
            finish()
        }
    }

    /**
     * The pass has returned: stop, or carry on.
     *
     * Runs inside the drain job, which is why it clears the field first rather
     * than cancelling itself.
     */
    private fun finish() {
        drainJob = null
        // Not "the queue was empty when the workers last looked" — that is a
        // moment ago, and the moments in between are exactly the window.
        if (hasWork()) {
            begin()
            return
        }
        reporterJob?.cancel()
        reporterJob = null
        clearNotification()
        // False means a start request with a newer id arrived while this pass
        // was finishing: the service is not stopping, so something has to drain
        // whatever that request was for.
        if (!stopService(latestStartId)) begin()
    }

    /**
     * Stop now, whatever the queue says: the user asked for it.
     *
     * [Downloads.cancel] has already emptied the queue of the tracks being
     * given up on by the time this is called, so only the jobs need tearing
     * down.
     */
    fun cancelAll() {
        drainJob?.cancel()
        drainJob = null
        reporterJob?.cancel()
        reporterJob = null
        clearNotification()
        stopService(latestStartId)
    }
}
