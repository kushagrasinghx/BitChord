package com.music.bitchord.playback

import com.music.bitchord.data.LikeState
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.SourceStream
import com.music.bitchord.data.sources.StreamFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class RecommendationTasteTest {

    private var currentTime = 1_000_000L

    @Before
    fun setUp() {
        RecommendationTaste.resetForTesting()
        RecommendationTaste.clockMs = { currentTime }
        LikeState.clear()
        StreamChoice.forget("flacSong")
    }

    @After
    fun tearDown() {
        RecommendationTaste.resetForTesting()
        LikeState.clear()
        StreamChoice.forget("flacSong")
    }

    private fun testSong(
        id: String,
        title: String = "Title $id",
        artist: String = "Artist $id",
    ) = Song(
        videoId = id,
        title = title,
        artist = artist,
        thumbnailUrl = null,
    )

    @Test
    fun `scoreCandidate gives neutral score to unknown track`() {
        val song = testSong("song1", artist = "New Artist")
        val score = RecommendationTaste.scoreCandidate(song)
        assertEquals(0.0, score, 0.001)
    }

    @Test
    fun `scoreCandidate boosts liked songs and filters out disliked songs`() {
        val likedSong = testSong("liked1", artist = "Liked Artist")
        val dislikedSong = testSong("disliked1", artist = "Disliked Artist")
        val neutralSong = testSong("neutral1", artist = "Neutral Artist")

        LikeState.set("liked1", LikeStatus.LIKE)
        LikeState.set("disliked1", LikeStatus.DISLIKE)

        val likedScore = RecommendationTaste.scoreCandidate(likedSong)
        val dislikedScore = RecommendationTaste.scoreCandidate(dislikedSong)

        assertEquals(50.0, likedScore, 0.001)
        assertEquals(-1000.0, dislikedScore, 0.001)

        val reranked = RecommendationTaste.reRankCandidates(listOf(neutralSong, dislikedSong, likedSong))
        // Disliked song must be excluded entirely
        assertEquals(2, reranked.size)
        assertEquals("liked1", reranked[0].videoId)
        assertEquals("neutral1", reranked[1].videoId)
        assertFalse(reranked.any { it.videoId == "disliked1" })
    }

    @Test
    fun `recordSkip applies early-skip penalties to song and artist`() {
        val songA = testSong("skip1", artist = "SkippedArtist")
        val songB = testSong("otherFromSameArtist", artist = "SkippedArtist")

        RecommendationTaste.recordSkip(songA)

        // songA gets -80 (track skip) + -35 (artist skip) = -115
        val scoreA = RecommendationTaste.scoreCandidate(songA)
        assertEquals(-115.0, scoreA, 0.001)

        // songB has not been skipped directly, but shares the artist: -35
        val scoreB = RecommendationTaste.scoreCandidate(songB)
        assertEquals(-35.0, scoreB, 0.001)
    }

    @Test
    fun `recordCompletion forgives artist skip penalty and rewards artist`() {
        val song1 = testSong("song1", artist = "DualArtist")
        val song2 = testSong("song2", artist = "DualArtist")

        // First, skip song1
        RecommendationTaste.recordSkip(song1)
        assertEquals(-35.0, RecommendationTaste.scoreCandidate(song2), 0.001)

        // User finishes song2 to completion
        RecommendationTaste.recordCompletion(song2)

        // Artist skip penalty is forgiven and completion bonus (+25) is awarded
        val score2 = RecommendationTaste.scoreCandidate(song2)
        assertEquals(25.0, score2, 0.001)
    }

    @Test
    fun `session artist continuity provides vibe boost`() {
        val sessionSong = testSong("currentPlaying", artist = "SessionArtist")
        val upcomingCandidate = testSong("candidate", artist = "SessionArtist")

        RecommendationTaste.recordSessionTrack(sessionSong)

        // Session artist boost is +20
        val score = RecommendationTaste.scoreCandidate(upcomingCandidate)
        assertEquals(20.0, score, 0.001)
    }

    @Test
    fun `recently played track incurs fatigue penalty`() {
        val playedSong = testSong("fatigueTrack", artist = "SoloArtist")

        RecommendationTaste.recordSessionTrack(playedSong)

        // Artist gets session bonus (+20), but repeated track gets fatigue penalty (-60) = net -40
        val score = RecommendationTaste.scoreCandidate(playedSong)
        assertEquals(-40.0, score, 0.001)
    }

    @Test
    fun `lossless match adds quality affinity boost`() {
        val song = testSong("flacSong", artist = "AudiophileArtist")
        val flacStream = SourceStream(
            url = "https://tidal.example/track.flac",
            format = StreamFormat(codec = "flac"),
        )
        StreamChoice.remember("flacSong", flacStream, substituted = true)

        val score = RecommendationTaste.scoreCandidate(song)
        assertEquals(15.0, score, 0.001)
    }

    @Test
    fun `reRankCandidates prioritizes liked and high affinity tracks over skipped ones`() {
        val liked = testSong("fav", artist = "Artist A")
        val neutral = testSong("mid", artist = "Artist B")
        val skipped = testSong("bad", artist = "Artist C")
        val disliked = testSong("hate", artist = "Artist D")

        LikeState.set("fav", LikeStatus.LIKE)
        LikeState.set("hate", LikeStatus.DISLIKE)
        RecommendationTaste.recordSkip(skipped)

        val rawQueue = listOf(skipped, neutral, disliked, liked)
        val ranked = RecommendationTaste.reRankCandidates(rawQueue)

        assertEquals(listOf("fav", "mid", "bad"), ranked.map { it.videoId })
    }
}
