package com.music.bitchord

import com.music.bitchord.ui.listenerCacheKey
import com.music.bitchord.ui.listenerCacheKeysOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Removing an account has to take that account's cached library, history and
 * playlists with it, and only that account's — the cache is keyed by identity.
 */
class ListenerCacheScopeTest {

    @Test
    fun `a key names one account and one profile`() {
        assertEquals("acct1:profile1", listenerCacheKey("acct1", "profile1"))
        assertNotEquals(
            listenerCacheKey("acct1", "profile1"),
            listenerCacheKey("acct1", "profile2"),
        )
        assertNotEquals(
            listenerCacheKey("acct1", "profile1"),
            listenerCacheKey("acct2", "profile1"),
        )
    }

    @Test
    fun `purging an account returns only that account's keys`() {
        val keys = setOf(
            listenerCacheKey("acct1", "personal"),
            listenerCacheKey("acct1", "brand"),
            listenerCacheKey("acct2", "personal"),
        )
        assertEquals(
            setOf(listenerCacheKey("acct1", "personal"), listenerCacheKey("acct1", "brand")),
            listenerCacheKeysOf(keys, "acct1").toSet(),
        )
    }

    @Test
    fun `an account id that prefixes another does not match it`() {
        val keys = setOf(listenerCacheKey("acct", "p"), listenerCacheKey("acct-extended", "p"))
        val purged = listenerCacheKeysOf(keys, "acct")
        assertEquals(listOf(listenerCacheKey("acct", "p")), purged)
        assertTrue(listenerCacheKey("acct-extended", "p") !in purged)
    }

    @Test
    fun `the last account's purge leaves nothing behind`() {
        val keys = mutableSetOf(listenerCacheKey("acct1", "p"))
        keys -= listenerCacheKeysOf(keys, "acct1").toSet()
        assertTrue(keys.isEmpty())
    }
}
