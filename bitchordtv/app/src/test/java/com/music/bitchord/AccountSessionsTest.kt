package com.music.bitchord

import com.music.bitchord.auth.GoogleAccountSession
import com.music.bitchord.auth.YouTubeProfile
import com.music.bitchord.auth.adjacentProfile
import com.music.bitchord.auth.flattenedProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountSessionsTest {
    private val a = GoogleAccountSession("a", "cookie-a", profiles = listOf(
        YouTubeProfile("a-personal", "Personal"), YouTubeProfile("a-brand", "Brand"),
    ))
    private val b = GoogleAccountSession("b", "cookie-b", profiles = listOf(
        YouTubeProfile("b-personal", "Personal"), YouTubeProfile("b-brand", "Brand"),
    ))

    @Test fun `profiles retain account then profile ordering`() {
        assertEquals(listOf("a" to "a-personal", "a" to "a-brand", "b" to "b-personal", "b" to "b-brand"),
            flattenedProfiles(listOf(a, b)))
    }

    @Test fun `next previous and boundaries do not wrap`() {
        assertEquals("a" to "a-brand", adjacentProfile(listOf(a, b), "a", "a-personal", true))
        assertEquals("b" to "b-personal", adjacentProfile(listOf(a, b), "b", "b-brand", false))
        assertNull(adjacentProfile(listOf(a, b), "a", "a-personal", false))
        assertNull(adjacentProfile(listOf(a, b), "b", "b-brand", true))
    }

    @Test fun `one profile has no adjacent profile`() {
        val only = GoogleAccountSession("a", "cookie", profiles = listOf(YouTubeProfile("p", "Personal")))
        assertNull(adjacentProfile(listOf(only), "a", "p", true))
        assertNull(adjacentProfile(listOf(only), "a", "p", false))
    }
}
