package com.music.bitchord

import com.music.bitchord.auth.AccountTransition
import com.music.bitchord.auth.AccountTransitionRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountTransitionRulesTest {

    @Test
    fun `no transition is complete`() {
        assertTrue(AccountTransitionRules.isComplete(null, "acct1"))
    }

    @Test
    fun `transition is complete once the target is active`() {
        val t = AccountTransition("a", "b", 1L)
        assertTrue(AccountTransitionRules.isComplete(t, "b"))
        assertFalse(AccountTransitionRules.isComplete(t, "a"))
    }

    @Test
    fun `recoverable only when the target still exists`() {
        val t = AccountTransition("a", "b", 1L)
        assertTrue(AccountTransitionRules.isRecoverable(t, setOf("a", "b")))
        assertFalse(AccountTransitionRules.isRecoverable(t, setOf("a")))
    }

    @Test
    fun `rollback target is the source when the target vanished`() {
        val t = AccountTransition("a", "b", 1L)
        org.junit.Assert.assertEquals("a", AccountTransitionRules.rollbackTarget(t, setOf("a")))
        org.junit.Assert.assertNull(AccountTransitionRules.rollbackTarget(t, setOf("a", "b")))
    }

    @Test
    fun `clear only when the target became active`() {
        val t = AccountTransition("a", "b", 1L)
        assertTrue(AccountTransitionRules.shouldClear(t, "b"))
        assertFalse(AccountTransitionRules.shouldClear(t, "a"))
    }
}
