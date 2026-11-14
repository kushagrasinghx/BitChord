package com.music.bitchord.data.listentogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PartyPseudonymsTest {

    @Test
    fun sameServerAndSubjectStayStableAcrossCalls() {
        val first = PartyPseudonyms.derive("secret", "https://parties.example", "user:a:profile")
        val second = PartyPseudonyms.derive("secret", "HTTPS://Parties.Example:443/", "user:a:profile")
        assertEquals(first, second)
    }

    @Test
    fun fixedVectorPinsTheHmacConstruction() {
        assertEquals(
            "b2b8f7dd5b94cf2615d5854709a70a30",
            PartyPseudonyms.derive("secret", "https://parties.example", "user:a:profile"),
        )
    }

    @Test
    fun differentServersCannotCorrelateTheSameSubject() {
        val a = PartyPseudonyms.derive("secret", "https://a.example", "user:a:profile")
        val b = PartyPseudonyms.derive("secret", "https://b.example", "user:a:profile")
        assertNotEquals(a, b)
    }

    @Test
    fun differentAccountsStayDistinctOnOneServer() {
        val a = PartyPseudonyms.derive("secret", "https://a.example", "user:a:profile")
        val b = PartyPseudonyms.derive("secret", "https://a.example", "user:b:profile")
        assertNotEquals(a, b)
    }

    @Test
    fun theOutputFitsTheExistingThirtyTwoCharacterWireField() {
        val value = PartyPseudonyms.derive("secret", "https://a.example", "device:install")
        assertEquals(32, value.length)
    }
}
