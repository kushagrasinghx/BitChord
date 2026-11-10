package com.music.bitchord.data.applemusic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleMusicAuthSessionStoreTest {
    @Test
    fun `disconnect while already signed out still invalidates a pending authorization`() {
        val fixture = Fixture()
        val generation = fixture.store.generation()
        fixture.store.disconnect()
        assertFalse(fixture.store.commit(generation, "late-account", "us"))
        assertEquals(0, fixture.saves)
        assertEquals(1, fixture.clears)
    }

    @Test
    fun `only one concurrent candidate may commit the same session generation`() = runBlocking {
        withTimeout(5_000) {
            val fixture = Fixture()
            val generation = fixture.store.generation()
            val results = (1..20).map { index ->
                async(Dispatchers.Default) { fixture.store.commit(generation, "account-$index", "us") }
            }.awaitAll()
            assertEquals(1, results.count { it })
            assertEquals(1, fixture.saves)
        }
    }

    @Test
    fun `directly cleared and replaced credentials invalidate earlier session generations`() {
        val fixture = Fixture()
        fixture.token = "original"
        val generation = fixture.store.generation()
        fixture.token = "replacement"
        assertFalse(fixture.store.commit(generation, "candidate", "us"))
        val replaced = fixture.store.generation()
        fixture.token = null
        assertFalse(fixture.store.commit(replaced, "candidate", "gb"))
        assertEquals(0, fixture.saves)
    }

    @Test
    fun `successful commit advances generation and disconnect clears both account and storefront`() {
        val fixture = Fixture()
        val generation = fixture.store.generation()
        assertTrue(fixture.store.commit(generation, "authorized-user", "gb"))
        assertTrue(fixture.store.generation() > generation)
        assertEquals("authorized-user", fixture.token)
        assertEquals("gb", fixture.storefront)
        fixture.store.disconnect()
        assertEquals(null, fixture.token)
        assertEquals(null, fixture.storefront)
    }

    private class Fixture {
        var token: String? = null
        var storefront: String? = null
        var saves = 0
        var clears = 0
        val store = AppleMusicAuthSessionStore(
            readToken = { token },
            save = { user, country, _ -> token = user; storefront = country; saves++ },
            clear = { token = null; storefront = null; clears++ },
        )
    }
}
