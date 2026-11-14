package com.music.bitchord.playback

import com.music.bitchord.playback.smart.CrossfadeMode
import com.music.bitchord.playback.smart.EnergySample
import com.music.bitchord.playback.smart.TrackAnalysis
import com.music.bitchord.playback.smart.TransitionStyle
import com.music.bitchord.playback.smart.TransitionTrackInfo
import com.music.bitchord.playback.smart.planTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumAutoMixTransitionTest {

    private fun track(
        id: String,
        bpm: Double,
        key: String,
        vocal: Double,
        duration: Double = 200.0,
    ): TrackAnalysis {
        val beat = 60.0 / bpm
        val downbeats = generateSequence(0.5) { it + 4 * beat }.takeWhile { it < duration - 1 }.toList()
        val energy = (0 until (duration * 2).toInt()).map { EnergySample(it / 2.0, 1.0) }
        return TrackAnalysis(
            status = TrackAnalysis.STATUS_READY,
            trackId = id,
            duration = duration,
            bpm = bpm,
            beatInterval = beat,
            beatConfidence = 0.9,
            downbeats = downbeats,
            phraseBoundaries = downbeats.filterIndexed { index, _ -> index % 8 == 0 },
            firstBeat = 0.5,
            key = key,
            keyConfidence = 0.9,
            audibleStartTime = 0.5,
            contentEndTime = duration - 2,
            mixInTime = downbeats[4],
            energyCurve = energy,
            vocalProbability = vocal,
        )
    }

    @Test
    fun sameAlbumSequentialPlaybackProducesGaplessHandoff() {
        val current = track("song1", 124.0, "C major", vocal = 0.1)
        val next = track("song2", 124.0, "C major", vocal = 0.1)

        val plan = planTransition(
            analysis = current,
            nextAnalysis = next,
            currentTrack = TransitionTrackInfo("song1", 200_000, title = "Intro", album = "The Album", albumId = "alb1"),
            nextTrack = TransitionTrackInfo("song2", 200_000, title = "Outro", album = "The Album", albumId = "alb1"),
            currentTime = 50.0,
            duration = 200.0,
            mode = CrossfadeMode.SMART,
            albumSequential = true,
            advanced = true,
        )

        assertEquals(TransitionStyle.GAPLESS, plan.transitionStyle)
        assertTrue(plan.markerVisible)
        assertEquals(0.12, plan.fadeSeconds, 1e-4)
        assertEquals("before-gapless-window", plan.reason)
    }

    @Test
    fun differentAlbumDoesNotProduceGapless() {
        val current = track("song1", 124.0, "C major", vocal = 0.1)
        val next = track("song2", 124.0, "C major", vocal = 0.1)

        val plan = planTransition(
            analysis = current,
            nextAnalysis = next,
            currentTrack = TransitionTrackInfo("song1", 200_000, title = "Song A", album = "Album A", albumId = "alb1"),
            nextTrack = TransitionTrackInfo("song2", 200_000, title = "Song B", album = "Album B", albumId = "alb2"),
            currentTime = 50.0,
            duration = 200.0,
            mode = CrossfadeMode.SMART,
            albumSequential = true,
            advanced = true,
        )

        assertNotEquals(TransitionStyle.GAPLESS, plan.transitionStyle)
    }

    @Test
    fun nonSequentialAlbumDoesNotProduceGapless() {
        val current = track("song1", 124.0, "C major", vocal = 0.1)
        val next = track("song2", 124.0, "C major", vocal = 0.1)

        val plan = planTransition(
            analysis = current,
            nextAnalysis = next,
            currentTrack = TransitionTrackInfo("song1", 200_000, title = "Track 1", album = "Album", albumId = "alb1"),
            nextTrack = TransitionTrackInfo("song2", 200_000, title = "Track 2", album = "Album", albumId = "alb1"),
            currentTime = 50.0,
            duration = 200.0,
            mode = CrossfadeMode.SMART,
            albumSequential = false,
            advanced = true,
        )

        assertNotEquals(TransitionStyle.GAPLESS, plan.transitionStyle)
    }
}
