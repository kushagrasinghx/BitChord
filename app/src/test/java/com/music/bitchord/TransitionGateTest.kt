package com.music.bitchord

import com.music.bitchord.playback.smart.TrackAnalysis
import com.music.bitchord.playback.smart.TransitionTrackInfo
import com.music.bitchord.playback.smart.planTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The metadata text gate ("blocked-speech-or-live") was removed: a studio
 * track whose title contains a word like "Live" ("Scared to Live") must mix
 * like any other analysed pair. Mixing is refused only on evidence (no
 * duration, short track, no room for a dissolve) — never on a substring.
 */
class TransitionGateTest {

    private fun analysis(id: String, bpm: Double) = TrackAnalysis(
        trackId = id,
        duration = 212.0,
        bpm = bpm,
        beatInterval = 60.0 / bpm,
        beatConfidence = 0.8,
    )

    private fun track(id: String, title: String) = TransitionTrackInfo(
        id = id,
        durationMs = 212_000L,
        title = title,
        artist = "The Weeknd",
        album = "After Hours",
    )

    @Test
    fun `title containing Live still mixes when analysed`() {
        val plan = planTransition(
            analysis = analysis("a", 104.0),
            nextAnalysis = analysis("b", 122.0),
            currentTrack = track("a", "Hardest To Love"),
            nextTrack = track("b", "Scared to Live"),
            currentTime = 10.0,
            duration = 212.0,
            mixset = true,
        )
        assertFalse(plan.blocked)
        assertEquals(false, plan.reason.contains("speech-or-live"))
    }

    @Test
    fun `concert album still mixes when analysed`() {
        val plan = planTransition(
            analysis = analysis("a", 120.0),
            nextAnalysis = analysis("b", 124.0),
            currentTrack = track("a", "Song One"),
            nextTrack = TransitionTrackInfo(
                id = "b",
                durationMs = 212_000L,
                title = "Song Two",
                artist = "Band",
                album = "Live at the Hall",
            ),
            currentTime = 10.0,
            duration = 212.0,
            mixset = true,
        )
        assertFalse(plan.blocked)
    }
}
