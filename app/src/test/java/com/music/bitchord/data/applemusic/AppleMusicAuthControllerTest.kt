package com.music.bitchord.data.applemusic

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleMusicAuthControllerTest {
    @Test
    fun `missing SDK and unvalidated SDK are actionable before credentials are fetched`() = authTest {
        for (problem in listOf(AppleMusicAuthProblem.SDK_MISSING, AppleMusicAuthProblem.SDK_NOT_VALIDATED)) {
            val fixture = Fixture(unavailable = problem)
            assertEquals(AppleMusicAuthState.Failed(problem), fixture.controller.state.value)
            fixture.controller.connect()
            assertEquals(0, fixture.developerCalls)
            assertEquals(0, fixture.authorizeCalls)
            assertNull(fixture.token)
            assertTrue(problem.message.isNotBlank())
        }
    }

    @Test
    fun `invalid configuration does not fetch credentials or open authorization`() = authTest {
        val fixture = Fixture(configured = false)
        fixture.controller.connect()
        fixture.assertProblem(AppleMusicAuthProblem.CONFIGURATION)
        assertEquals(0, fixture.developerCalls)
        assertEquals(0, fixture.authorizeCalls)
    }

    @Test
    fun `backend errors are redacted and do not change an existing account`() = authTest {
        val fixture = Fixture(fetch = { error("leaked-developer-token") })
        fixture.token = "previous-account"
        fixture.storefront = "us"
        fixture.controller.connect()
        fixture.assertProblem(AppleMusicAuthProblem.BACKEND)
        assertEquals("previous-account", fixture.token)
        assertEquals("us", fixture.storefront)
        assertFalse(fixture.controller.state.value.toString().contains("leaked-developer-token"))
        assertEquals(0, fixture.authorizeCalls)
    }

    @Test
    fun `only verified storefront success saves the candidate and synchronizes settings`() = authTest {
        val fixture = Fixture()
        fixture.lookup = { developer, user ->
            assertEquals("developer-secret", developer)
            assertEquals("candidate-secret", user)
            assertNull(fixture.token)
            assertEquals(AppleMusicAuthState.CheckingStorefront, fixture.controller.state.value)
            "gb"
        }
        fixture.controller.connect()
        assertEquals("candidate-secret", fixture.token)
        assertEquals("gb", fixture.storefront)
        assertEquals("gb", fixture.settingsStorefront)
        assertEquals(1, fixture.saves)
        assertEquals(AppleMusicAuthState.Connected("gb"), fixture.controller.state.value)
        assertFalse(fixture.controller.state.value.toString().contains("secret"))
    }

    @Test
    fun `authorization errors keep distinct actionable states without saving credentials`() = authTest {
        val problems = listOf(
            AppleMusicAuthProblem.CANCELLED,
            AppleMusicAuthProblem.APP_MISSING,
            AppleMusicAuthProblem.BACKEND,
            AppleMusicAuthProblem.AUTHORIZATION_DENIED,
            AppleMusicAuthProblem.SUBSCRIPTION_REQUIRED,
            AppleMusicAuthProblem.SUBSCRIPTION_EXPIRED,
            AppleMusicAuthProblem.AUTHORIZATION_BUSY,
        )
        for (problem in problems) {
            val fixture = Fixture(authorize = { AppleMusicAuthorization.Failed(problem) })
            fixture.controller.connect()
            fixture.assertProblem(problem)
            assertEquals(0, fixture.lookups)
            assertNull(fixture.token)
        }
        assertEquals("https://play.google.com/store/apps/details?id=com.apple.android.music", AppleMusicAuthProblem.APP_MISSING.actionUrl)
    }

    @Test
    fun `malformed developer and user credentials never reach storefront lookup`() = authTest {
        for (invalid in listOf("", " ", "token\nvalue", "token\rvalue", "x".repeat(16385))) {
            val developer = Fixture(fetch = { invalid })
            developer.controller.connect()
            developer.assertProblem(AppleMusicAuthProblem.BACKEND)
            assertEquals(0, developer.authorizeCalls)
            val user = Fixture(authorize = { AppleMusicAuthorization.Authorized(invalid) })
            user.controller.connect()
            user.assertProblem(AppleMusicAuthProblem.AUTHORIZATION_DENIED)
            assertEquals(0, user.lookups)
        }
    }

    @Test
    fun `storefront failure never publishes connected or persists candidate credentials`() = authTest {
        val fixture = Fixture()
        fixture.lookup = { _, _ -> error("response-body-with-secret") }
        fixture.controller.connect()
        fixture.assertProblem(AppleMusicAuthProblem.STOREFRONT)
        assertEquals(0, fixture.saves)
        assertNull(fixture.token)
        assertFalse(fixture.controller.state.value.toString().contains("secret"))
    }

    @Test
    fun `typed storefront authorization errors retain the actionable reason`() = authTest {
        val fixture = Fixture()
        fixture.lookup = { _, _ -> throw AppleMusicAuthException(AppleMusicAuthProblem.AUTHORIZATION_DENIED) }
        fixture.controller.connect()
        fixture.assertProblem(AppleMusicAuthProblem.AUTHORIZATION_DENIED)
        assertEquals(0, fixture.saves)
    }

    @Test
    fun `invalid storefront is not replaced with a guessed country`() = authTest {
        for (country in listOf("", "USA", "US", "u1", "../us", "user-secret")) {
            val fixture = Fixture()
            fixture.lookup = { _, _ -> country }
            fixture.controller.connect()
            fixture.assertProblem(AppleMusicAuthProblem.STOREFRONT)
            assertNull(fixture.token)
        }
    }

    @Test
    fun `native runtime errors are distinct from authorization denial`() = authTest {
        val fixture = Fixture(authorize = { throw UnsatisfiedLinkError("native-secret") })
        fixture.controller.connect()
        fixture.assertProblem(AppleMusicAuthProblem.RUNTIME_UNAVAILABLE)
        assertNull(fixture.token)
    }

    @Test
    fun `cancelled noncooperative SDK completion cannot reconnect`() = authTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fixture = Fixture(authorize = {
            entered.complete(Unit)
            withContext(NonCancellable) { release.await() }
            AppleMusicAuthorization.Authorized("late-user-secret")
        })
        val request = launch { fixture.controller.connect() }
        entered.await()
        fixture.controller.cancel()
        release.complete(Unit)
        request.join()
        assertTrue(request.isCancelled)
        fixture.assertProblem(AppleMusicAuthProblem.CANCELLED)
        assertEquals(0, fixture.lookups)
        assertNull(fixture.token)
        assertTrue(currentCoroutineContext().isActive)
    }

    @Test
    fun `disconnect during noncooperative storefront request cannot restore credentials or settings`() = authTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fixture = Fixture()
        fixture.lookup = { _, _ ->
            entered.complete(Unit)
            withContext(NonCancellable) { release.await() }
            "us"
        }
        val request = launch { fixture.controller.connect() }
        entered.await()
        fixture.controller.disconnect()
        release.complete(Unit)
        request.join()
        assertNull(fixture.token)
        assertEquals("", fixture.settingsStorefront)
        assertEquals(0, fixture.saves)
        assertEquals(AppleMusicAuthState.Idle, fixture.controller.state.value)
    }

    @Test
    fun `a newer attempt wins over a late superseded authorization result`() = authTest {
        val release = CompletableDeferred<Unit>()
        var attempts = 0
        val fixture = Fixture(authorize = {
            val attempt = ++attempts
            if (attempt == 1) withContext(NonCancellable) { release.await() }
            AppleMusicAuthorization.Authorized(if (attempt == 1) "old-user" else "new-user")
        })
        val first = launch(start = CoroutineStart.UNDISPATCHED) { fixture.controller.connect() }
        fixture.controller.connect()
        release.complete(Unit)
        first.join()
        assertTrue(first.isCancelled)
        assertEquals(1, fixture.saves)
        assertEquals("new-user", fixture.token)
        assertEquals(AppleMusicAuthState.Connected("us"), fixture.controller.state.value)
    }

    @Test
    fun `external account replacement rejects a candidate without overwriting the new account`() = authTest {
        val fixture = Fixture()
        fixture.lookup = { _, _ -> fixture.token = "different-user"; "us" }
        fixture.controller.connect()
        fixture.assertProblem(AppleMusicAuthProblem.SESSION_CHANGED)
        assertEquals("different-user", fixture.token)
        assertEquals(0, fixture.saves)
    }

    @Test
    fun `closed controller cannot publish a late result`() = authTest {
        val release = CompletableDeferred<Unit>()
        val fixture = Fixture(authorize = {
            withContext(NonCancellable) { release.await() }
            AppleMusicAuthorization.Authorized("late-user")
        })
        val request = launch(start = CoroutineStart.UNDISPATCHED) { fixture.controller.connect() }
        fixture.controller.close()
        release.complete(Unit)
        request.join()
        assertEquals(AppleMusicAuthState.Idle, fixture.controller.state.value)
        assertEquals(0, fixture.saves)
    }

    @Test
    fun `caller cancellation is propagated and leaves the caller scope intact`() = authTest {
        val waiting = CompletableDeferred<Unit>()
        val fixture = Fixture(fetch = { waiting.await(); "developer-secret" })
        val request = launch(start = CoroutineStart.UNDISPATCHED) { fixture.controller.connect() }
        request.cancel()
        request.join()
        assertTrue(request.isCancelled)
        fixture.assertProblem(AppleMusicAuthProblem.CANCELLED)
        assertNull(fixture.token)
        assertTrue(currentCoroutineContext().isActive)
    }

    @Test
    fun `noncooperative failure after caller cancellation remains cancelled rather than denied`() = authTest {
        val release = CompletableDeferred<Unit>()
        val fixture = Fixture(authorize = {
            withContext(NonCancellable) { release.await() }
            error("secret-in-late-error")
        })
        val request = launch(start = CoroutineStart.UNDISPATCHED) { fixture.controller.connect() }
        request.cancel()
        release.complete(Unit)
        request.join()
        assertTrue(request.isCancelled)
        fixture.assertProblem(AppleMusicAuthProblem.CANCELLED)
        assertEquals(0, fixture.saves)
    }

    @Test
    fun `authorization success object does not print the credential`() {
        assertFalse(AppleMusicAuthorization.Authorized("never-log-me").toString().contains("never-log-me"))
    }

    private class Fixture(
        unavailable: AppleMusicAuthProblem? = null,
        configured: Boolean = true,
        fetch: suspend () -> String = { "developer-secret" },
        authorize: suspend () -> AppleMusicAuthorization = { AppleMusicAuthorization.Authorized("candidate-secret") },
    ) {
        var token: String? = null
        var storefront: String? = null
        var settingsStorefront = ""
        var saves = 0
        var developerCalls = 0
        var authorizeCalls = 0
        var lookups = 0
        var lookup: suspend (String, String) -> String = { _, _ -> "us" }
        val session = AppleMusicAuthSessionStore(
            readToken = { token },
            save = { user, country, _ -> token = user; storefront = country; settingsStorefront = country; saves++ },
            clear = { token = null; storefront = null; settingsStorefront = "" },
        )
        val controller = AppleMusicAuthController(
            adapter = object : AppleMusicAuthAdapter {
                override fun unavailableReason() = unavailable
                override suspend fun authorize(developerToken: String): AppleMusicAuthorization {
                    authorizeCalls++
                    return authorize()
                }
            },
            developerToken = { developerCalls++; fetch() },
            resolveStorefront = { dev, user -> lookups++; lookup(dev, user) },
            session = session,
            configurationValid = { configured },
        )
        fun assertProblem(problem: AppleMusicAuthProblem) {
            assertEquals(AppleMusicAuthState.Failed(problem), controller.state.value)
        }
    }

    private fun authTest(block: suspend CoroutineScope.() -> Unit) = runBlocking {
        withTimeout(5_000, block)
    }
}
