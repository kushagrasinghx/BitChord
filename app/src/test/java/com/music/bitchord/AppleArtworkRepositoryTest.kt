package com.music.bitchord

import com.music.bitchord.data.canvas.cleanedTrackTitle
import com.music.bitchord.data.canvas.foldProfanity
import com.music.bitchord.data.canvas.normalizeForMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleArtworkRepositoryTest {

    @Test
    fun `cleans noisy YouTube track titles`() {
        val raw = "fuck, i’m lonely (stripped) (feat. Anne-Marie)"
        val cleaned = raw.cleanedTrackTitle()
        assertEquals("fuck, i'm lonely", cleaned)
    }

    @Test
    fun `folds profanity censorship variants`() {
        val censored = "f*ck, i'm lonely"
        val folded = censored.foldProfanity()
        assertEquals("fuck, i'm lonely", folded)

        val normalizedFolded = censored.foldProfanity().normalizeForMatch()
        val normalizedRaw = "fuck, i'm lonely".normalizeForMatch()
        assertEquals(normalizedRaw, normalizedFolded)
    }

    @Test
    fun `matches explicit iTunes track name against cleaned YouTube title`() {
        val ytRaw = "fuck, i’m lonely (stripped) (feat. Anne-Marie)"
        val itunesTrack = "f*ck, i'm lonely (feat. Anne-Marie)"

        val wantedTitle = ytRaw.cleanedTrackTitle().foldProfanity().normalizeForMatch()
        val gotTitle = itunesTrack.cleanedTrackTitle().foldProfanity().normalizeForMatch()

        assertEquals("fuck i m lonely", wantedTitle)
        assertEquals("fuck i m lonely", gotTitle)
        assertEquals(wantedTitle, gotTitle)
    }
}
