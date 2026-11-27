package com.music.bitchord.auth

import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Independent verification test suite for account removal operations in [AuthStore], ensuring:
 * - Scenario A: Removing inactive Account B preserves Account A's actual WebView cookies.
 * - Scenario B: Removing active Account A transitions actual WebView cookies to fallback Account B.
 * - Scenario C: Removing final account clears actual WebView cookies and persistent storage.
 * - Scenario D: Removing multiple inactive accounts consecutively preserves active account cookies.
 * - Scenario E: Removing earlier-ordered inactive account preserves active account.
 * - Scenario F: Removing non-existent account is a safe no-op.
 * - Scenario G: Robust self-healing when activeAccountId is null, empty (""), or inconsistent.
 */
class AccountRemovalSessionRegressionTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var fakeCookieStore: FakeCookieStore
    private lateinit var authStore: AuthStore

    private val musicOrigin = "https://music.youtube.com"

    private val sessionA = GoogleAccountSession(
        accountId = "acc-A",
        cookie = "SID=a; SAPISID=cookie-secret-A; HSID=a",
        name = "Account A",
        email = "a@example.com",
        profiles = listOf(YouTubeProfile("prof-A-1", "Personal A")),
        activeProfileId = "prof-A-1",
    )

    private val sessionB = GoogleAccountSession(
        accountId = "acc-B",
        cookie = "SID=b; SAPISID=cookie-secret-B; HSID=b",
        name = "Account B",
        email = "b@example.com",
        profiles = listOf(YouTubeProfile("prof-B-1", "Personal B")),
        activeProfileId = "prof-B-1",
    )

    private val sessionC = GoogleAccountSession(
        accountId = "acc-C",
        cookie = "SID=c; SAPISID=cookie-secret-C; HSID=c",
        name = "Account C",
        email = "c@example.com",
        profiles = listOf(YouTubeProfile("prof-C-1", "Personal C")),
        activeProfileId = "prof-C-1",
    )

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        fakeCookieStore = FakeCookieStore()
        BrowserSession.cookieStore = fakeCookieStore
        authStore = AuthStore(fakePrefs)
    }

    @After
    fun tearDown() {
        BrowserSession.resetCookieStore()
    }

    /**
     * Scenario A — Removing an inactive account
     *
     * - Account A is active.
     * - Account B is inactive.
     * - Remove Account B.
     * - Verify Account A remains active.
     * - Verify Account A's actual WebView cookies in CookieStore are NOT cleared.
     * - Verify Account A's native cookie remains valid.
     */
    @Test
    fun testA_removingInactiveAccount_preservesActiveAccountWebViewSessionAndCookie() {
        // Setup: A active, B inactive
        authStore.sessions = listOf(sessionA, sessionB)
        authStore.select(sessionA.accountId, sessionA.activeProfileId)
        authStore.cookie = sessionA.cookie
        BrowserSession.installGoogleCookies(sessionA.cookie)

        val initialCookies = fakeCookieStore.getCookie(musicOrigin)
        assertNotNull(initialCookies)
        assertTrue(initialCookies!!.contains("SAPISID=cookie-secret-A"))

        // Action: Remove inactive account B
        val fallback = authStore.removeAccount(sessionB.accountId)

        // Verifications
        assertEquals("acc-A", authStore.activeAccountId)
        assertEquals("acc-A", fallback?.accountId)
        assertEquals(sessionA.cookie, authStore.cookie)

        // Verify actual cookie jar contents
        val cookiesAfterRemoval = fakeCookieStore.getCookie(musicOrigin)
        assertNotNull(cookiesAfterRemoval)
        assertTrue(cookiesAfterRemoval!!.contains("SAPISID=cookie-secret-A"))
        assertEquals(1, authStore.sessions.size)
        assertEquals("acc-A", authStore.sessions[0].accountId)
        assertTrue(authStore.isSignedIn)
    }

    /**
     * Scenario B — Removing the active account
     *
     * - Account A is active.
     * - Account B is fallback.
     * - Remove Account A.
     * - Verify Account B becomes active.
     * - Verify actual WebView cookies correctly transition to Account B.
     */
    @Test
    fun testB_removingActiveAccount_transitionsToFallbackAccount() {
        // Setup: A active, B fallback
        authStore.sessions = listOf(sessionA, sessionB)
        authStore.select(sessionA.accountId, sessionA.activeProfileId)
        authStore.cookie = sessionA.cookie
        BrowserSession.installGoogleCookies(sessionA.cookie)

        // Action: Remove active account A
        val fallback = authStore.removeAccount(sessionA.accountId)

        // Verifications
        assertEquals("acc-B", fallback?.accountId)
        assertEquals("acc-B", authStore.activeAccountId)
        assertEquals(sessionB.cookie, authStore.cookie)

        // Verify actual cookie jar transition: old secret gone, new secret installed
        val cookies = fakeCookieStore.getCookie(musicOrigin)
        assertNotNull(cookies)
        assertTrue(cookies!!.contains("SAPISID=cookie-secret-B"))
        assertFalse(cookies.contains("cookie-secret-A"))
        assertEquals(1, authStore.sessions.size)
        assertEquals("acc-B", authStore.sessions[0].accountId)
        assertTrue(authStore.isSignedIn)
    }

    /**
     * Scenario C — Removing the final account
     *
     * - Only Account A exists.
     * - Remove Account A.
     * - Verify the application reaches the intended logged-out state.
     * - Verify actual WebView cookies are purged.
     */
    @Test
    fun testC_removingFinalAccount_cleansAllAuthenticationState() {
        // Setup: Only Account A
        authStore.sessions = listOf(sessionA)
        authStore.select(sessionA.accountId, sessionA.activeProfileId)
        authStore.cookie = sessionA.cookie
        BrowserSession.installGoogleCookies(sessionA.cookie)

        // Action: Remove final account A
        val fallback = authStore.removeAccount(sessionA.accountId)

        // Verifications
        assertNull(fallback)
        assertNull(authStore.activeAccountId)
        assertNull(authStore.activeProfileId)
        assertNull(authStore.cookie)
        assertNull(fakeCookieStore.getCookie(musicOrigin)) // Cookies completely purged
        assertTrue(fakeCookieStore.storageRemoved)
        assertTrue(authStore.sessions.isEmpty())
        assertFalse(authStore.isSignedIn)
    }

    /**
     * Scenario D — Removing multiple inactive accounts consecutively
     *
     * - Account A active.
     * - Remove inactive B, then remove inactive C.
     * - Verify repeated removal keeps Account A's WebView cookies continuously undisturbed.
     */
    @Test
    fun testD_removingInactiveAccountsRepeatedly_doesNotDisturbActiveAccount() {
        // Setup: A active, B and C inactive
        authStore.sessions = listOf(sessionA, sessionB, sessionC)
        authStore.select(sessionA.accountId, sessionA.activeProfileId)
        authStore.cookie = sessionA.cookie
        BrowserSession.installGoogleCookies(sessionA.cookie)

        // First removal: B
        val fallback1 = authStore.removeAccount(sessionB.accountId)
        assertEquals("acc-A", fallback1?.accountId)
        assertEquals("acc-A", authStore.activeAccountId)
        assertTrue(fakeCookieStore.getCookie(musicOrigin)!!.contains("SAPISID=cookie-secret-A"))
        assertEquals(2, authStore.sessions.size)

        // Second removal: C
        val fallback2 = authStore.removeAccount(sessionC.accountId)
        assertEquals("acc-A", fallback2?.accountId)
        assertEquals("acc-A", authStore.activeAccountId)
        assertTrue(fakeCookieStore.getCookie(musicOrigin)!!.contains("SAPISID=cookie-secret-A"))
        assertEquals(1, authStore.sessions.size)
        assertEquals("acc-A", authStore.sessions[0].accountId)
        assertTrue(authStore.isSignedIn)
    }

    /**
     * Scenario E — Preserving active account when removing preceding inactive account
     *
     * - Session list is [B (inactive), A (active)].
     * - Remove B.
     * - Verify A remains active and is not displaced by list ordering.
     */
    @Test
    fun testE_removingPrecedingInactiveAccount_preservesActiveAccountSelection() {
        authStore.sessions = listOf(sessionB, sessionA) // B is index 0, A is index 1
        authStore.select(sessionA.accountId, sessionA.activeProfileId)
        authStore.cookie = sessionA.cookie
        BrowserSession.installGoogleCookies(sessionA.cookie)

        val fallback = authStore.removeAccount(sessionB.accountId)

        assertEquals("acc-A", fallback?.accountId)
        assertEquals("acc-A", authStore.activeAccountId)
        assertEquals(sessionA.cookie, authStore.cookie)
        assertTrue(fakeCookieStore.getCookie(musicOrigin)!!.contains("SAPISID=cookie-secret-A"))
    }

    /**
     * Scenario F — Removing an account that does not exist
     *
     * - Account A active, Account B inactive.
     * - Remove "non-existent-account".
     * - Verify sessions, active account, and cookies remain completely unchanged.
     */
    @Test
    fun testF_removingNonExistentAccount_isSafeNoOp() {
        authStore.sessions = listOf(sessionA, sessionB)
        authStore.select(sessionA.accountId, sessionA.activeProfileId)
        authStore.cookie = sessionA.cookie
        BrowserSession.installGoogleCookies(sessionA.cookie)

        val fallback = authStore.removeAccount("non-existent-account")

        assertEquals("acc-A", fallback?.accountId)
        assertEquals("acc-A", authStore.activeAccountId)
        assertEquals(2, authStore.sessions.size)
        assertEquals(sessionA.cookie, authStore.cookie)
        assertTrue(fakeCookieStore.getCookie(musicOrigin)!!.contains("SAPISID=cookie-secret-A"))
    }

    /**
     * Scenario G1 — activeAccountId is null
     *
     * - Two sessions exist, activeAccountId is null.
     * - removeAccount for the first session should be recognized as active session.
     */
    @Test
    fun testG1_activeAccountIdNull_fallsBackToFirstSessionAndTransitions() {
        authStore.sessions = listOf(sessionA, sessionB)
        authStore.activeAccountId = null
        authStore.cookie = sessionA.cookie
        BrowserSession.installGoogleCookies(sessionA.cookie)

        val fallback = authStore.removeAccount(sessionA.accountId)

        assertEquals("acc-B", fallback?.accountId)
        assertEquals("acc-B", authStore.activeAccountId)
        assertTrue(fakeCookieStore.getCookie(musicOrigin)!!.contains("SAPISID=cookie-secret-B"))
    }

    /**
     * Scenario G2 — activeAccountId is empty string ("")
     *
     * - Getter normalizes "" to null.
     * - Removing active session transitions cleanly without string corruption.
     */
    @Test
    fun testG2_activeAccountIdEmptyString_normalizesAndHandlesRemoval() {
        authStore.sessions = listOf(sessionA, sessionB)
        fakePrefs.putString("active_google_account_id_v2", "") // Raw empty string in storage
        authStore.cookie = sessionA.cookie
        BrowserSession.installGoogleCookies(sessionA.cookie)

        assertNull(authStore.activeAccountId) // Normalized to null!

        val fallback = authStore.removeAccount(sessionA.accountId)

        assertEquals("acc-B", fallback?.accountId)
        assertEquals("acc-B", authStore.activeAccountId)
        assertTrue(fakeCookieStore.getCookie(musicOrigin)!!.contains("SAPISID=cookie-secret-B"))
    }

    /**
     * Scenario G3 — activeAccountId is inconsistent with session list ("ghost-id")
     *
     * - activeAccountId points to a non-existent account ID.
     * - currentActiveId self-heals to activeSession (sessionA).
     */
    @Test
    fun testG3_activeAccountIdInconsistent_selfHealsToValidSession() {
        authStore.sessions = listOf(sessionA, sessionB)
        authStore.activeAccountId = "ghost-id"
        authStore.cookie = sessionA.cookie
        BrowserSession.installGoogleCookies(sessionA.cookie)

        // Removing sessionA (which is the actual active session) transitions to sessionB
        val fallback = authStore.removeAccount(sessionA.accountId)

        assertEquals("acc-B", fallback?.accountId)
        assertEquals("acc-B", authStore.activeAccountId)
        assertTrue(fakeCookieStore.getCookie(musicOrigin)!!.contains("SAPISID=cookie-secret-B"))
    }

    /**
     * Deterministic Fake CookieStore simulating Android CookieManager in memory.
     */
    private class FakeCookieStore : BrowserSession.CookieStore {
        private val jars = mutableMapOf<String, MutableMap<String, String>>()
        var flushCount = 0
        var storageRemoved = false

        override fun getCookie(url: String): String? {
            val cookies = jars[url]?.takeIf { it.isNotEmpty() } ?: return null
            return cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }

        override fun setCookie(url: String, value: String) {
            val nameValue = value.substringBefore(';')
            val name = nameValue.substringBefore('=').trim()
            val cookieVal = nameValue.substringAfter('=', "").trim()
            val isExpired = value.contains("Max-Age=0", ignoreCase = true) ||
                value.contains("1970", ignoreCase = true) || cookieVal.isEmpty()
            val jar = jars.getOrPut(url) { mutableMapOf() }
            if (isExpired) {
                jar.remove(name)
            } else {
                jar[name] = cookieVal
            }
        }

        override fun flush() {
            flushCount++
        }

        override fun removeStorage(origins: List<String>) {
            storageRemoved = true
        }
    }

    /**
     * In-memory SharedPreferences implementation mirroring the project's test double patterns.
     */
    private class FakeSharedPreferences : SharedPreferences {
        val data = mutableMapOf<String, Any?>()

        fun putString(key: String, value: String?) {
            if (value == null) data.remove(key) else data[key] = value
        }

        override fun getAll(): Map<String, *> = HashMap(data)
        override fun getString(key: String?, defValue: String?): String? =
            data[key] as? String ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? =
            data[key] as? Set<String> ?: defValues

        override fun getInt(key: String?, defValue: Int): Int =
            (data[key] as? Number)?.toInt() ?: defValue

        override fun getLong(key: String?, defValue: Long): Long =
            (data[key] as? Number)?.toLong() ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            (data[key] as? Number)?.toFloat() ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            data[key] as? Boolean ?: defValue

        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(this)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val prefs: FakeSharedPreferences) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removed = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: Set<String>?): SharedPreferences.Editor {
                if (key != null) pending[key] = values
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) removed.add(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearAll = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) prefs.data.clear()
                for (key in removed) prefs.data.remove(key)
                prefs.data.putAll(pending)
            }
        }
    }
}
