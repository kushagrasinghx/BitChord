package com.music.bitchord.data.applemusic

import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job

/** Stable, credential-free presentation values; UI can localize these codes. */
enum class AppleMusicAuthProblem(val message: String, val actionUrl: String? = null) {
    SDK_MISSING(
        "This build does not include Apple's MusicKit Android SDK. Install a build with the official SDK and validated Apple Music support.",
    ),
    SDK_NOT_VALIDATED(
        "MusicKit files are present, but Apple Music authorization has not been validated for this build. Use a validated build; adding SDK files alone does not enable sign-in.",
    ),
    APP_MISSING(
        "Install the Apple Music Android app, then try connecting again.",
        "https://play.google.com/store/apps/details?id=com.apple.android.music",
    ),
    CONFIGURATION("Configure an HTTPS Apple Music token backend, then rebuild the app."),
    BACKEND("The Apple Music token backend could not provide a valid developer token. Check its configuration and connection, then retry."),
    CANCELLED("Apple Music sign-in was cancelled. You can try again."),
    AUTHORIZATION_DENIED("Apple Music did not authorize this account. Check the account's access, then reconnect."),
    SUBSCRIPTION_REQUIRED("An active Apple Music subscription is required. Check your subscription in the Apple Music app."),
    SUBSCRIPTION_EXPIRED("Your Apple Music subscription has expired. Renew it in the Apple Music app, then reconnect."),
    AUTHORIZATION_BUSY("A previous Apple Music sign-in screen is still open. Finish or dismiss it before trying again."),
    STOREFRONT("Apple Music could not verify this account's storefront. Check your connection and account, then retry."),
    SESSION_CHANGED("The Apple Music session changed during sign-in. Connect again."),
    RUNTIME_UNAVAILABLE("MusicKit could not run on this device. Use a compatible, validated build."),
    STORAGE("Apple Music could not save the authorized session. Check device storage, then retry."),
}

sealed interface AppleMusicAuthState {
    data object Idle : AppleMusicAuthState
    data object FetchingDeveloperToken : AppleMusicAuthState
    data object Authorizing : AppleMusicAuthState
    data object CheckingStorefront : AppleMusicAuthState
    /** Confirms authorization and storefront, not native playback entitlement or readiness. */
    data class Connected(val storefront: String) : AppleMusicAuthState
    data class Failed(val problem: AppleMusicAuthProblem) : AppleMusicAuthState
}

sealed interface AppleMusicAuthorization {
    /** Never use a data class: generated toString must not expose credentials. */
    class Authorized(val userToken: String, val playbackStorefront: String? = null) : AppleMusicAuthorization {
        override fun toString(): String = "AppleMusicAuthorization.Authorized(<redacted>)"
    }
    data class Failed(val problem: AppleMusicAuthProblem) : AppleMusicAuthorization
}

/** Native SDK seam. Preflight must not fetch credentials or launch another app. */
interface AppleMusicAuthAdapter {
    fun unavailableReason(): AppleMusicAuthProblem?
    suspend fun authorize(developerToken: String): AppleMusicAuthorization
}

/** Commit must atomically reject a stale generation without storing its candidate credentials. */
interface AppleMusicAuthSession {
    fun generation(): Long
    fun commit(expectedGeneration: Long, userToken: String, storefront: String, playbackStorefront: String? = null): Boolean
    fun disconnect()
}

/**
 * No candidate token is persisted until /me/storefront succeeds. Owns only its child
 * request, never the caller's scope. Route disconnect/account changes through this
 * controller (or the same session) so pending native callbacks cannot restore them.
 */
class AppleMusicAuthController(
    private val adapter: AppleMusicAuthAdapter,
    private val developerToken: suspend () -> String,
    private val resolveStorefront: suspend (developerToken: String, userToken: String) -> String,
    private val session: AppleMusicAuthSession,
    private val configurationValid: () -> Boolean = { true },
) : Closeable {
    private val lock = Any()
    private var generation = 0L
    private var activeJob: Job? = null
    private var closed = false
    private val mutableState = MutableStateFlow<AppleMusicAuthState>(
        adapter.unavailableReason()?.let { AppleMusicAuthState.Failed(it) } ?: AppleMusicAuthState.Idle,
    )
    val state = mutableState.asStateFlow()

    /** Cancels a superseded attempt. A native adapter must separately guard late Activity results. */
    suspend fun connect(): Unit = coroutineScope {
        val job = currentCoroutineContext().job
        val ticket = synchronized(lock) {
            check(!closed) { "Apple Music authorization controller is closed" }
            invalidate()
            activeJob = job
            generation to session.generation()
        }
        var failure = AppleMusicAuthProblem.BACKEND
        try {
            adapter.unavailableReason()?.let { fail(it) }
            failure = AppleMusicAuthProblem.CONFIGURATION
            if (!configurationValid()) fail(AppleMusicAuthProblem.CONFIGURATION)
            publish(ticket.first, job, AppleMusicAuthState.FetchingDeveloperToken)
            failure = AppleMusicAuthProblem.BACKEND
            val developer = developerToken()
            if (!validAuthToken(developer)) fail(AppleMusicAuthProblem.BACKEND)
            publish(ticket.first, job, AppleMusicAuthState.Authorizing)
            failure = AppleMusicAuthProblem.AUTHORIZATION_DENIED
            val authorization = when (val result = adapter.authorize(developer)) {
                is AppleMusicAuthorization.Authorized -> result
                is AppleMusicAuthorization.Failed -> fail(result.problem)
            }
            val user = authorization.userToken
            if (!validAuthToken(user)) fail(AppleMusicAuthProblem.AUTHORIZATION_DENIED)
            publish(ticket.first, job, AppleMusicAuthState.CheckingStorefront)
            failure = AppleMusicAuthProblem.STOREFRONT
            val storefront = resolveStorefront(developer, user).trim()
            // The value comes from Apple, never from a device locale fallback.
            if (!storefront.matches(Regex("[a-z]{2}"))) fail(AppleMusicAuthProblem.STOREFRONT)
            failure = AppleMusicAuthProblem.STORAGE
            synchronized(lock) {
                checkCurrent(ticket.first, job)
                if (!session.commit(ticket.second, user, storefront, authorization.playbackStorefront)) {
                    fail(AppleMusicAuthProblem.SESSION_CHANGED)
                }
                mutableState.value = AppleMusicAuthState.Connected(storefront)
            }
        } catch (cancelled: CancellationException) {
            finish(ticket.first, job, AppleMusicAuthState.Failed(AppleMusicAuthProblem.CANCELLED))
            throw cancelled
        } catch (error: AppleMusicAuthException) {
            finish(ticket.first, job, AppleMusicAuthState.Failed(error.problem))
        } catch (_: LinkageError) {
            finish(ticket.first, job, AppleMusicAuthState.Failed(AppleMusicAuthProblem.RUNTIME_UNAVAILABLE))
        } catch (_: Exception) {
            // SDK/transport/parser exceptions may contain tokens. Never expose or retain their causes.
            finish(ticket.first, job, AppleMusicAuthState.Failed(failure))
        } finally {
            synchronized(lock) {
                if (generation == ticket.first) activeJob = null
            }
        }
    }

    fun cancel() = synchronized(lock) {
        invalidate()
        if (!closed) mutableState.value = AppleMusicAuthState.Failed(AppleMusicAuthProblem.CANCELLED)
    }

    fun disconnect() = synchronized(lock) {
        check(!closed) { "Apple Music authorization controller is closed" }
        invalidate()
        session.disconnect()
        mutableState.value = adapter.unavailableReason()?.let { AppleMusicAuthState.Failed(it) }
            ?: AppleMusicAuthState.Idle
    }

    override fun close() = synchronized(lock) {
        if (closed) return@synchronized
        invalidate()
        closed = true
        mutableState.value = AppleMusicAuthState.Idle
    }

    private fun invalidate() {
        generation++
        activeJob?.cancel()
        activeJob = null
    }

    private fun checkCurrent(ticket: Long, job: Job) {
        job.ensureActive()
        if (closed || ticket != generation) throw CancellationException("Apple Music authorization was superseded")
    }

    private fun publish(ticket: Long, job: Job, state: AppleMusicAuthState) = synchronized(lock) {
        checkCurrent(ticket, job)
        mutableState.value = state
    }

    private fun finish(ticket: Long, job: Job, state: AppleMusicAuthState) = synchronized(lock) {
        if (!closed && generation == ticket) {
            mutableState.value = if (job.isActive) state else AppleMusicAuthState.Failed(AppleMusicAuthProblem.CANCELLED)
        }
    }

    private fun fail(problem: AppleMusicAuthProblem): Nothing = throw AppleMusicAuthException(problem)
}

/** Only a stable problem is retained; never wrap SDK/HTTP/parser exceptions containing credentials. */
internal open class AppleMusicAuthException(val problem: AppleMusicAuthProblem) : Exception(problem.message)

internal fun validAuthToken(value: String): Boolean =
    value.isNotEmpty() && value.length <= 16 * 1024 && value.all { it.code in 33..126 }
