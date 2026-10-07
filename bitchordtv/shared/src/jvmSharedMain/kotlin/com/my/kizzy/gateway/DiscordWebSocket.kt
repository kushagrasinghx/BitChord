package com.my.kizzy.gateway

import com.my.kizzy.gateway.entities.Heartbeat
import com.my.kizzy.gateway.entities.Identify.Companion.toIdentifyPayload
import com.my.kizzy.gateway.entities.Payload
import com.my.kizzy.gateway.entities.Ready
import com.my.kizzy.gateway.entities.Resume
import com.my.kizzy.gateway.entities.op.OpCode
import com.my.kizzy.gateway.entities.op.OpCode.DISPATCH
import com.my.kizzy.gateway.entities.op.OpCode.HEARTBEAT
import com.my.kizzy.gateway.entities.op.OpCode.HEARTBEAT_ACK
import com.my.kizzy.gateway.entities.op.OpCode.HELLO
import com.my.kizzy.gateway.entities.op.OpCode.IDENTIFY
import com.my.kizzy.gateway.entities.op.OpCode.INVALID_SESSION
import com.my.kizzy.gateway.entities.op.OpCode.PRESENCE_UPDATE
import com.my.kizzy.gateway.entities.op.OpCode.RECONNECT
import com.my.kizzy.gateway.entities.op.OpCode.RESUME
import com.my.kizzy.gateway.entities.presence.Presence
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.util.logging.Logger
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

/**
 * Modified by Zion Huang
 *
 * The socket's whole lifecycle lives in one coroutine — [runConnectionLoop] —
 * which connects, pumps frames until the connection goes away for any reason,
 * waits out a backoff and goes round again. That shape matters on Android: this
 * socket spends most of its life behind a backgrounded app, where the radio
 * sleeping, a wifi-to-cellular handover or Discord's own idle timeout will take
 * it down repeatedly over a listening session, and every one of those has to
 * heal without the user reopening the app.
 *
 * Two failures the loop alone does not cover, and which are handled here:
 *
 *  - **Zombie sockets.** A TCP connection that dies while the radio is asleep
 *    is not reported as closed — reads simply never complete and writes are
 *    accepted into a void. Nothing in the WebSocket API distinguishes that from
 *    a quiet connection, so [startHeartbeatJob] insists on Discord's
 *    `HEARTBEAT_ACK` for every heartbeat it sends and drops the socket when one
 *    goes unanswered. Without that check a backgrounded app publishes presences
 *    into a dead socket indefinitely and only recovers on a process restart.
 *
 *  - **A presence lost with its session.** Discord holds a presence for the
 *    lifetime of the gateway session and discards it when that session ends, so
 *    a reconnect leaves the profile blank until the next track change — which
 *    may be minutes away. [lastPresence] is therefore replayed as soon as a new
 *    session is ready.
 */
open class DiscordWebSocket(
    private val token: String,
    private val os: String = "Android",
    private val browser: String = "Discord Android",
    private val device: String = "Generic Android Device",
) : CoroutineScope {
    private val logger = Logger.getLogger(DiscordWebSocket::class.java.name)
    private val gatewayUrl = "wss://gateway.discord.gg/?v=9&encoding=json"
    private var websocket: DefaultClientWebSocketSession? = null
    private var sequence = 0
    private var sessionId: String? = null
    private var heartbeatInterval = 0L
    private var resumeGatewayUrl: String? = null
    private var heartbeatJob: Job? = null
    private var client: HttpClient = HttpClient {
        install(WebSockets)
    }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * One job, not a job per attempt.
     *
     * A stored [SupervisorJob] rather than a `get()` that builds one, because
     * every `launch` on this scope reads [coroutineContext]: a getter handed each
     * coroutine a parent of its own, which left [close] with nothing to cancel
     * and every heartbeat and reconnect it was meant to stop still running.
     */
    private val supervisor = SupervisorJob()

    override val coroutineContext: CoroutineContext = supervisor + Dispatchers.Default

    /**
     * The connect/pump/backoff loop. Reconnection is this coroutine going round
     * again rather than a second coroutine scheduled from inside the first —
     * which is what it used to be, and could not work: the guard against
     * scheduling two reconnects at once tested the very job that was asking for
     * one, so every disconnect that wasn't an explicit gateway `RECONNECT` ended
     * the presence for the life of the process.
     */
    private var connectionLoop: Job? = null

    private val loopLock = Any()

    /** Set by [close]. Stops the loop retrying an intentionally closed socket. */
    @Volatile
    private var closed = false

    /**
     * Whether there is a session that can carry a presence — true between
     * `READY`/`RESUMED` and the socket going down. A [MutableStateFlow] so
     * [sendActivity] can wait on it instead of polling: the wait used to be a
     * 10ms sleep loop with no exit, which on a socket that never came back
     * spun for as long as the service lived.
     */
    private val sessionReady = MutableStateFlow(false)

    /** Collapses the reconnect backoff on request — see [retryNow]. */
    private val retrySignal = Channel<Unit>(Channel.CONFLATED)

    /**
     * `elapsedRealtime` of the last `HEARTBEAT_ACK`.
     *
     * That clock and not `nanoTime`, which stops during deep sleep: the whole
     * point of the reading is to notice that a lot of wall time has passed with
     * nothing heard from Discord, and the case where that happens is a device
     * asleep behind a backgrounded app. A monotonic clock that sleeps too would
     * report the socket as fresh however long the phone had been in a pocket.
     */
    @Volatile
    private var lastAckAt = 0L

    /** A heartbeat is out and unanswered. */
    @Volatile
    private var awaitingAck = false

    /** Skip the next backoff — set when Discord itself asked us to reconnect. */
    @Volatile
    private var immediateRetry = false

    /**
     * The last presence handed to [sendActivity], replayed after a reconnect.
     *
     * Its timestamps are absolute instants rather than offsets, so a presence
     * built one socket ago is still correct on the next one.
     */
    @Volatile
    private var lastPresence: Presence? = null

    /**
     * What has already been delivered on the *current* session. Cleared with the
     * socket, because a new session has been told nothing.
     */
    @Volatile
    private var deliveredPresence: Presence? = null

    /** Starts the connection loop if it isn't already running. */
    fun connect() {
        if (closed) return
        synchronized(loopLock) {
            if (connectionLoop?.isActive == true) {
                logger.info("Gateway connection loop already running.")
                return
            }
            connectionLoop = launch { runConnectionLoop() }
        }
    }

    /**
     * Asks the loop to stop waiting out its backoff and try now.
     *
     * The backoff climbs to a minute, which is the right thing while there is no
     * network and the wrong thing the moment there is something to publish: a
     * track change is evidence the user is listening, and shouldn't wait on a
     * timer that was set when the last attempt failed.
     */
    fun retryNow() {
        if (closed) return
        retrySignal.trySend(Unit)
    }

    private suspend fun runConnectionLoop() {
        var backoff = INITIAL_RECONNECT_DELAY.inWholeMilliseconds
        while (currentCoroutineContext().isActive && !closed) {
            // A resume URL is only worth using while its session might still be
            // alive; every path that invalidates the session clears it, so
            // reaching for it here can't strand us on a dead one.
            val url = resumeGatewayUrl ?: gatewayUrl
            var connected = false
            try {
                logger.info("Connecting to Discord Gateway at $url")
                val session = client.webSocketSession(url) {
                    header("User-Agent", "Discord-Android/314013;RNA")
                    header("Accept-Language", "en-US")
                    header("Cache-Control", "no-cache")
                    header("Pragma", "no-cache")
                }
                websocket = session
                connected = true
                backoff = INITIAL_RECONNECT_DELAY.inWholeMilliseconds
                logger.info("Successfully connected to Discord Gateway.")
                // Runs until the socket closes or errors, which is the only way
                // out of an attempt — including the heartbeat watchdog's, which
                // works by cancelling the session underneath this.
                session.incoming.receiveAsFlow().collect { frame ->
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        // Per frame, so one payload this build doesn't
                        // understand — an opcode or event Discord added since —
                        // can't take the connection down with it.
                        runCatching { onMessage(json.decodeFromString(text)) }
                            .onFailure { logger.warning("Gateway: bad payload: ${it.message}") }
                    }
                }
                val reason = session.closeReason.await()
                logger.warning(
                    "Gateway closed with code: ${reason?.code}, reason: ${reason?.message}",
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                logger.warning("Gateway connection error: ${e.message}")
            } finally {
                teardownSocket()
            }

            if (closed) break

            // An attempt that died before it ever opened is evidence the resume
            // URL is stale as often as it is evidence the network is down, and
            // the base gateway will always take a fresh identify.
            if (!connected) forgetSession()

            if (immediateRetry) {
                immediateRetry = false
                continue
            }
            logger.info("Gateway: reconnecting in ${backoff}ms")
            // A wait, not a sleep: retryNow() cuts it short.
            withTimeoutOrNull(backoff) { retrySignal.receive() }
            backoff = (backoff * 2).coerceAtMost(MAX_RECONNECT_DELAY.inWholeMilliseconds)
        }
    }

    /**
     * Puts the socket and everything keyed to it back to a known-down state.
     *
     * Unconditional, and on every exit path, because the flag saying whether
     * there was a connection is what [connect] and [sendActivity] read: leaving
     * it set after a failure — which the old error path did, having returned
     * early before clearing it — made every later attempt to connect a no-op
     * and every push a wait for a socket that no longer existed.
     */
    private fun teardownSocket() {
        sessionReady.value = false
        awaitingAck = false
        deliveredPresence = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        websocket?.cancel()
        websocket = null
    }

    /** Drops what identifies a session, so the next HELLO identifies afresh. */
    private fun forgetSession() {
        resumeGatewayUrl = null
        sessionId = null
        sequence = 0
    }

    /**
     * Takes down the current socket so [runConnectionLoop] moves on to its next
     * attempt. Cancelling the session is what ends the frame pump.
     */
    private fun dropSocket(why: String) {
        logger.warning("Gateway: dropping socket ($why)")
        sessionReady.value = false
        websocket?.cancel()
    }

    private suspend fun onMessage(payload: Payload) {
        logger.info("Gateway received: op=${payload.op}, seq=${payload.s}, event=${payload.t}")
        payload.s?.let {
            sequence = it
        }
        when (payload.op) {
            DISPATCH -> payload.handleDispatch()
            HEARTBEAT -> sendHeartBeat()
            HEARTBEAT_ACK -> {
                awaitingAck = false
                lastAckAt = elapsedRealtimeMs()
            }
            RECONNECT -> reconnectWebSocket()
            INVALID_SESSION -> handleInvalidSession()
            HELLO -> payload.handleHello()
            else -> {}
        }
    }

    open fun Payload.handleDispatch() {
        when (this.t.toString()) {
            "READY" -> {
                val ready = json.decodeFromJsonElement<Ready>(this.d!!)
                sessionId = ready.sessionId
                resumeGatewayUrl = ready.resumeGatewayUrl + "/?v=9&encoding=json"
                logger.info(
                    "Gateway READY: resume_gateway_url updated to $resumeGatewayUrl, " +
                        "session_id updated to $sessionId",
                )
                onSessionReady()
                return
            }

            "RESUMED" -> {
                logger.info("Gateway: Session Resumed")
                onSessionReady()
            }

            else -> {}
        }
    }

    /**
     * Opens the session for presences and puts the last one back up.
     *
     * The replay is the difference between a dropped connection costing a blink
     * and costing the rest of the song: Discord discards a presence with the
     * session that set it, and nothing else here would send another until the
     * queue moved on.
     */
    private fun onSessionReady() {
        lastAckAt = elapsedRealtimeMs()
        sessionReady.value = true
        val presence = lastPresence ?: return
        launch {
            logger.info("Gateway: replaying presence onto the new session")
            runCatching { sendPresence(presence) }
        }
    }

    private suspend fun handleInvalidSession() {
        logger.warning("Gateway: Handling Invalid Session. Sending Identify after 150ms")
        // Cleared first: the session these named is the one Discord has just
        // told us is gone, and keeping them would resume against it again.
        forgetSession()
        delay(150)
        sendIdentify()
    }

    private suspend fun Payload.handleHello() {
        heartbeatInterval = json.decodeFromJsonElement<Heartbeat>(this.d!!).heartbeatInterval
        logger.info("Gateway: Setting heartbeatInterval=$heartbeatInterval")
        // Handshake first, then heartbeats — the order Discord's own client uses,
        // and not worth deviating from. A gateway that answers the handshake with
        // silence is still caught: the watchdog starts on the next line and gives
        // up on an unacknowledged heartbeat one interval later.
        if (sequence > 0 && !sessionId.isNullOrBlank()) {
            sendResume()
        } else {
            sendIdentify()
        }
        startHeartbeatJob(heartbeatInterval)
    }

    private suspend fun sendHeartBeat() {
        logger.info("Gateway: Sending $HEARTBEAT with seq: $sequence")
        send(
            op = HEARTBEAT,
            d = if (sequence == 0) "null" else sequence.toString(),
        )
    }

    private fun reconnectWebSocket() {
        immediateRetry = true
        dropSocket("gateway asked us to reconnect")
    }

    private suspend fun sendIdentify() {
        logger.info("Gateway: Sending $IDENTIFY")
        send(
            op = IDENTIFY,
            d = token.toIdentifyPayload(
                os = os,
                browser = browser,
                device = device,
            ),
        )
    }

    private suspend fun sendResume() {
        logger.info("Gateway: Sending $RESUME")
        send(
            op = RESUME,
            d = Resume(
                seq = sequence,
                sessionId = sessionId,
                token = token,
            ),
        )
    }

    /**
     * Heartbeats, and the only thing that notices a socket which has stopped
     * carrying traffic without being closed.
     *
     * Discord acknowledges every heartbeat. One going unanswered for a whole
     * interval means the connection is gone whatever the socket claims, and the
     * only recovery is to build a new one — so the watchdog drops this one and
     * lets [runConnectionLoop] take it from there.
     */
    private fun startHeartbeatJob(interval: Long) {
        heartbeatJob?.cancel()
        awaitingAck = false
        lastAckAt = elapsedRealtimeMs()
        heartbeatJob = launch {
            while (isActive) {
                if (awaitingAck) {
                    dropSocket("heartbeat went unacknowledged")
                    return@launch
                }
                awaitingAck = true
                sendHeartBeat()
                delay(interval)
            }
        }
    }

    /**
     * Whether a presence sent right now would actually reach Discord.
     *
     * The ack age is part of the answer and not a refinement of it. A socket
     * whose peer has gone away still reports itself open and still accepts
     * writes, so a check that only asked the socket returned true for a
     * connection that had been dead since the screen went off.
     */
    private fun isSocketConnectedToAccount(): Boolean =
        sessionReady.value && websocket?.isActive == true && !isStale()

    private fun isStale(): Boolean {
        val interval = heartbeatInterval
        if (interval <= 0L || lastAckAt == 0L) return false
        val sinceAckMs = elapsedRealtimeMs() - lastAckAt
        return sinceAckMs > interval + STALE_GRACE.inWholeMilliseconds
    }

    fun isWebSocketConnected(): Boolean = isSocketConnectedToAccount()

    /** Returns whether the frame was handed to a live socket. */
    private suspend inline fun <reified T> send(op: OpCode, d: T?): Boolean {
        val socket = websocket?.takeIf { it.isActive } ?: return false
        val payload = json.encodeToString(
            Payload(
                op = op,
                d = json.encodeToJsonElement(d),
            ),
        )
        if (op == IDENTIFY) {
            logger.info("Gateway sending payload: [REDACTED IDENTIFY PAYLOAD]")
        } else {
            logger.info("Gateway sending payload: $payload")
        }
        socket.send(Frame.Text(payload))
        return true
    }

    fun close() {
        closed = true
        sessionReady.value = false
        lastPresence = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        connectionLoop?.cancel()
        connectionLoop = null
        forgetSession()
        val socket = websocket
        websocket = null
        // Closed rather than cancelled, and waited on: a presence-clear was very
        // likely queued a moment ago by KizzyRPC.close(), and only a graceful
        // close flushes the outgoing frames before the connection goes. Bounded
        // so a socket that is already unreachable can't hold up a teardown.
        runBlocking {
            withTimeoutOrNull(CLOSE_TIMEOUT) {
                runCatching { socket?.close(CloseReason(CloseReason.Codes.NORMAL, "Client closed")) }
            }
        }
        socket?.cancel()
        supervisor.cancel()
        runCatching { client.close() }
        logger.info("Gateway: Connection to gateway closed")
    }

    /**
     * Publishes [presence], waiting for a usable session if there isn't one yet.
     *
     * Bounded, unlike the poll it replaces. A push that times out is not lost
     * work: the presence it was carrying is the one [onSessionReady] replays
     * when a session does come back, so giving up here costs nothing that
     * waiting forever would have saved.
     */
    suspend fun sendActivity(presence: Presence) {
        lastPresence = presence
        if (closed) return
        // A socket that has gone quiet reports itself open, so it has to be
        // dropped rather than waited on — otherwise this returns "sent" for a
        // presence nobody received.
        if (sessionReady.value && isStale()) dropSocket("stale before a presence push")
        connect()
        retryNow()
        val ready = withTimeoutOrNull(SESSION_WAIT_TIMEOUT.inWholeMilliseconds) {
            sessionReady.first { it }
        }
        if (ready == null) {
            logger.warning("Gateway: no session for presence push; it will be replayed on reconnect")
            return
        }
        sendPresence(presence)
    }

    /**
     * Publishes an activity-less presence, which is how Discord is told to clear
     * the card, and never waits for a session to do it.
     *
     * The distinction from [sendActivity] is deliberate. A presence lives only as
     * long as the gateway session that set it, so with the socket already down
     * there is nothing left on Discord to clear — dialling one purely to say
     * nothing would hold up the caller, and the caller here is a pause or a
     * teardown. Recording it as [lastPresence] is the part that still matters:
     * it makes the *clear* the thing a later reconnect replays, so a socket that
     * dropped while paused can't come back advertising a stopped track.
     */
    suspend fun clearActivity() {
        val presence = Presence(activities = emptyList())
        lastPresence = presence
        if (closed || !isSocketConnectedToAccount()) return
        sendPresence(presence)
    }

    private suspend fun sendPresence(presence: Presence) {
        // Discord rate-limits presence updates, and there are two routes to
        // sending one: this call, and the replay a fresh session triggers. On a
        // reconnect both fire with the same payload, so the second is dropped.
        if (deliveredPresence == presence) {
            logger.info("Gateway: presence unchanged on this session; not resending")
            return
        }
        logger.info("Gateway: Sending $PRESENCE_UPDATE")
        // Recorded only if the frame actually left, so a push that arrived just
        // as the socket went down isn't remembered as delivered — and is
        // therefore still eligible for the replay onto the next session.
        if (send(op = PRESENCE_UPDATE, d = presence)) {
            deliveredPresence = presence
        }
    }

    companion object {
        private val INITIAL_RECONNECT_DELAY = 1.seconds
        private val MAX_RECONNECT_DELAY = 60.seconds

        /**
         * How far past a heartbeat interval an unacknowledged socket is given
         * before it counts as dead. Covers a slow round trip on a bad mobile
         * connection without letting a genuinely dead one linger.
         */
        private val STALE_GRACE = 15.seconds

        /** How long a presence push waits for a session before deferring to the replay. */
        private val SESSION_WAIT_TIMEOUT = 20.seconds

        private val CLOSE_TIMEOUT = 2.seconds
    }
}

/**
 * Milliseconds since boot, counting time the device spent asleep.
 *
 * `System.nanoTime` is not that on Android — it can stop during deep sleep,
 * which is exactly when this reading matters: the point of it is to notice that
 * a lot of wall time has passed with nothing heard from Discord. So Android's
 * own clock is used where there is one, found reflectively because this module
 * is shared with the desktop and cannot import it.
 */
private val androidElapsedRealtime: java.lang.reflect.Method? = runCatching {
    Class.forName("android.os.SystemClock").getMethod("elapsedRealtime")
}.getOrNull()

internal fun elapsedRealtimeMs(): Long =
    androidElapsedRealtime?.let { runCatching { it.invoke(null) as Long }.getOrNull() }
        ?: (System.nanoTime() / 1_000_000L)
