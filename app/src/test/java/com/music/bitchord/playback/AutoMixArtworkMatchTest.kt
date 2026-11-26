package com.music.bitchord.playback

import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.canvas.CanvasRepository
import com.music.bitchord.data.canvas.CanvasSource
import com.music.bitchord.data.model.QueueEntryIdentity
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoMixArtworkMatchTest {
    private val cover = AutoMixCoverSource("https://lh3.googleusercontent.com/cover=w160-h160")
    private val large = AutoMixCoverSource("https://lh3.googleusercontent.com/cover=w720-h720")
    private val different = AutoMixCoverSource("https://lh3.googleusercontent.com/different=w160-h160")

    private fun shouldBypassAutoMixArtwork(
        autoMix: Boolean, art: Boolean, mini: Boolean,
        samePreparedCover: () -> Boolean, samePresentation: () -> Boolean,
    ): Boolean = artworkAdmission(
        autoMix, art, mini, matchingSources = samePreparedCover, prepared = { true }, matchingPresentation = samePresentation,
    ) == AutoMixArtworkAdmission.BYPASS

    @Test
    fun candidateHoldsOnlyPossibleMatchesUntilExistingPreparationSettles() {
        for ((art, mini) in listOf(true to false, false to true, true to true)) {
            assertEquals(AutoMixArtworkAdmission.WAITING, artworkAdmission(true, art, mini, { true }, { false }, { true }))
            assertEquals(AutoMixArtworkAdmission.BYPASS, artworkAdmission(true, art, mini, { true }, { true }, { true }))
        }
        assertEquals(AutoMixArtworkAdmission.WAITING, artworkAdmission(true, true, false, { true }, { true }, { null }))
        assertEquals(AutoMixArtworkAdmission.REVEAL, artworkAdmission(true, true, true, { true }, { true }, { false }))
        assertEquals(AutoMixArtworkAdmission.REVEAL, artworkAdmission(true, true, true, { false }, { error("unexpected") }, { error("unexpected") }))
        for ((autoMix, art) in listOf(false to true, true to false)) {
            assertEquals(AutoMixArtworkAdmission.REVEAL, artworkAdmission(autoMix, art, false,
                { error("inactive") }, { error("inactive") }, { error("inactive") }))
        }
    }

    @Test
    fun bothPlayerRenditionsMustResolveToTheSameCover() {
        assertEquals("https://lh3.googleusercontent.com/cover=w1200-h1200", matchingAutoMixCover(cover, large))
        assertNull(matchingAutoMixCover(cover, different))
        assertNull(matchingAutoMixCover(null, null))
        assertNull(matchingAutoMixCover(AutoMixCoverSource(""), AutoMixCoverSource("")))
        val tiny = AutoMixCoverSource("https://i.ytimg.com/vi/video/default.jpg")
        val high = AutoMixCoverSource("https://i.ytimg.com/vi/video/hqdefault.jpg")
        assertNull(matchingAutoMixCover(tiny, high)) // same full request, different mini requests
    }

    @Test
    fun remoteCoversRequireExactSourceAndAuthenticationScope() {
        val remote = AutoMixCoverSource("https://example.invalid/music/cover.jpg#bitchord-art-1", 1)
        assertEquals(remote.url, matchingAutoMixCover(remote, remote.copy()))
        assertNull(matchingAutoMixCover(remote, remote.copy(remoteScope = 2)))
        assertNull(matchingAutoMixCover(remote, remote.copy(remoteScope = null)))
        assertNull(matchingAutoMixCover(remote, remote.copy(url = remote.url.replace("music", "other"))))
        val path = remote.copy(url = "smb://host:1445/music/w160-h160/cover.jpg#bitchord-art-1")
        assertNull(matchingAutoMixCover(path, path.copy(url = path.url.replace("w160-h160", "w720-h720"))))
    }

    @Test
    fun disabledSettingsDoNotReadArtworkEvidence() {
        for (autoMix in listOf(false, true)) {
            for (art in listOf(false, true)) {
                for (mini in listOf(false, true)) {
                    if (autoMix && (art || mini)) continue
                    assertFalse(shouldBypassAutoMixArtwork(autoMix, art, mini,
                        { error("read cover while disabled") }, { error("read presentation while disabled") }))
                }
            }
        }
    }

    @Test
    fun matchingPreparedCoversBypassWithEitherVisualToggle() {
        for ((art, mini) in listOf(true to false, false to true, true to true)) {
            assertTrue(shouldBypassAutoMixArtwork(true, art, mini, { true }, { true }))
            assertFalse(shouldBypassAutoMixArtwork(true, art, mini, { false }, { error("cover not prepared") }))
        }
        assertTrue(shouldBypassAutoMixArtwork(true, false, true, { true }, { error("mini never draws Canvas") }))
        assertFalse(shouldBypassAutoMixArtwork(true, true, true, { true }, { false }))
    }

    @Test
    fun aSharedThumbnailCannotHideDifferentOrUnknownAnimatedPresentations() {
        val clip = CanvasArtwork("https://example.invalid/clip.m3u8", source = CanvasSource.APPLE)
        val animated = AutoMixCanvasPresentation(clip)
        val still = AutoMixCanvasPresentation(null)
        assertTrue(sameAutoMixCanvas(still, still))
        assertTrue(sameAutoMixCanvas(animated, AutoMixCanvasPresentation(clip.copy(title = "Other song"))))
        assertFalse(sameAutoMixCanvas(still, animated))
        assertFalse(sameAutoMixCanvas(animated, still))
        assertFalse(sameAutoMixCanvas(null, still))
        assertFalse(sameAutoMixCanvas(null, null))
        assertFalse(sameAutoMixCanvas(animated, AutoMixCanvasPresentation(clip.copy(url = "other"))))
        assertFalse(sameAutoMixCanvas(animated, AutoMixCanvasPresentation(clip.copy(portraitUrl = "other"))))
        assertFalse(sameAutoMixCanvas(animated, AutoMixCanvasPresentation(clip.copy(fallbackUrl = "other"))))
        assertFalse(sameAutoMixCanvas(animated, AutoMixCanvasPresentation(clip.copy(source = CanvasSource.SPOTIFY))))
    }

    @Test
    fun onlyCanvasIneligibleTracksAreKnownStillWithoutAnExistingClip() {
        val uncached = Song("uncached-canvas", "Title", "Artist", thumbnailUrl = cover.url, albumName = "Album")
        val local = uncached.copy(videoId = "", localUri = "content://media/example")
        assertNull(CanvasRepository.cachedAutoMixPresentation(uncached))
        val still = CanvasRepository.cachedAutoMixPresentation(local)
        assertTrue(sameAutoMixCanvas(still, still))
        assertFalse(sameAutoMixCanvas(still, CanvasRepository.cachedAutoMixPresentation(uncached)))
    }

    @Test
    fun armingCandidateCanSettleBeforeEffectsButCannotChangeOnceAudioStarts() {
        val a = AutoMixVisualTrack(QueueEntryIdentity("a", null, 0), 0, cover.url)
        val b = AutoMixVisualTrack(QueueEntryIdentity("b", null, 0), 1, cover.url)
        try {
            val first = AutoMixVisualFeed.arm(a, b, null, bypassArtwork = false, artworkDecisionPending = true)
            assertTrue(AutoMixVisualFeed.pending(first)?.artworkDecisionPending == true)
            AutoMixVisualFeed.resolveArtwork(first, bypass = true)
            assertTrue(AutoMixVisualFeed.pending(first)?.bypassArtwork == true)
            AutoMixVisualFeed.start(first, 0.0)
            AutoMixVisualFeed.swap(first, 1.0, 6.0)
            assertNull(AutoMixVisualFeed.pending(first)?.artworkWindow)

            val second = AutoMixVisualFeed.arm(a, b, null, bypassArtwork = false, artworkDecisionPending = true)
            AutoMixVisualFeed.resolveArtwork(first, bypass = true) // old worker
            AutoMixVisualFeed.start(second, 0.0) // seek/fast start beats artwork readiness
            AutoMixVisualFeed.resolveArtwork(second, bypass = true)
            AutoMixVisualFeed.swap(second, 1.0, 6.0)
            val active = requireNotNull(AutoMixVisualFeed.pending(second))
            assertFalse(active.artworkDecisionPending)
            assertFalse(active.bypassArtwork)
            assertTrue(active.artworkWindow != null)
        } finally {
            AutoMixVisualFeed.cancel()
        }
    }

    @Test
    fun theArmedVerdictSurvivesHandoffButNeverLeaksIntoAnotherGeneration() {
        val a = AutoMixVisualTrack(QueueEntryIdentity("same-song", "first", 0), 0, cover.url)
        val b = AutoMixVisualTrack(QueueEntryIdentity("same-song", "second", 0), 1, large.url)
        val c = AutoMixVisualTrack(QueueEntryIdentity("other-song", null, 0), 2, different.url)
        var prepared = true
        fun arm(from: AutoMixVisualTrack, to: AutoMixVisualTrack) = AutoMixVisualFeed.arm(
            from, to, remoteArtworkScope = null,
            bypassArtwork = shouldBypassAutoMixArtwork(true, true, true, {
                prepared && matchingAutoMixCover(from.artworkUrl?.let { AutoMixCoverSource(it) },
                    to.artworkUrl?.let { AutoMixCoverSource(it) }) != null
            }, { true }),
        )
        val first = arm(a, b)
        try {
            prepared = false
            AutoMixVisualFeed.start(first, 0.0)
            AutoMixVisualFeed.swap(first, 1.0, 6.0)
            assertTrue(AutoMixVisualFeed.pending(first)?.bypassArtwork == true)
            assertNull(AutoMixVisualFeed.pending(first)?.artworkWindow)
            AutoMixVisualFeed.complete(first)

            val second = arm(b, c)
            assertNotEquals(first, second)
            assertNull(AutoMixVisualFeed.pending(first))
            AutoMixVisualFeed.start(second, 0.0)
            prepared = true // too late to change this generation
            AutoMixVisualFeed.swap(second, 1.0, 6.0)
            assertFalse(AutoMixVisualFeed.pending(second)?.bypassArtwork ?: true)
            assertTrue(AutoMixVisualFeed.pending(second)?.artworkWindow != null)
            AutoMixVisualFeed.cancel(second)

            prepared = false
            val seek = arm(a, b)
            AutoMixVisualFeed.start(seek, 2.0) // seek into an overlap before preparation
            prepared = true
            AutoMixVisualFeed.swap(seek, 2.0, 8.0)
            assertFalse(AutoMixVisualFeed.pending(seek)?.bypassArtwork ?: true)
            AutoMixVisualFeed.cancel(seek)
            val repeated = arm(a, b)
            AutoMixVisualFeed.cancel(seek) // a late cancellation cannot affect the new arm
            assertTrue(AutoMixVisualFeed.pending(repeated)?.bypassArtwork == true)
        } finally {
            AutoMixVisualFeed.cancel()
        }
    }
}
