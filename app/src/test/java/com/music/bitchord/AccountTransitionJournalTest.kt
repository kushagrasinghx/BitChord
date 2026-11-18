package com.music.bitchord

import com.music.bitchord.auth.AccountTransitionJournal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountTransitionJournalTest {

    private class Store {
        val strings = HashMap<String, String>()
        val longs = HashMap<String, Long>()

        fun journal(now: () -> Long = { 42L }) = AccountTransitionJournal(
            fromKey = "from",
            toKey = "to",
            startedKey = "at",
            readString = { strings[it] },
            writeString = { key, value -> if (value == null) strings.remove(key) else strings[key] = value },
            readLong = { longs[it] ?: 0L },
            writeLong = { key, value -> longs[key] = value },
            now = now,
        )
    }

    @Test
    fun transitionSurvivesASimulatedProcessRestart() {
        val store = Store()
        store.journal().begin("account-a", "account-b")
        val afterRestart = store.journal().pending()!!
        assertEquals("account-a", afterRestart.from)
        assertEquals("account-b", afterRestart.to)
        assertEquals(42L, afterRestart.startedAt)
        assertTrue(store.journal().hasPending())
    }

    @Test
    fun clearIsVisibleToTheNextProcess() {
        val store = Store()
        store.journal().begin("a", "b")
        store.journal().clear()
        assertNull(store.journal().pending())
        assertFalse(store.journal().hasPending())
        assertEquals(0, store.strings.size)
        assertEquals(0L, store.longs["at"])
    }

    @Test
    fun blankTargetIsNotJournaled() {
        val store = Store()
        store.journal().begin("a", " ")
        assertNull(store.journal().pending())
        assertFalse(store.journal().hasPending())
    }

    @Test
    fun repeatedBeginReplacesThePreviousTransition() {
        val store = Store()
        store.journal { 1L }.begin("a", "b")
        store.journal { 2L }.begin("b", "c")
        val pending = store.journal().pending()!!
        assertEquals("b", pending.from)
        assertEquals("c", pending.to)
        assertEquals(2L, pending.startedAt)
    }
}
