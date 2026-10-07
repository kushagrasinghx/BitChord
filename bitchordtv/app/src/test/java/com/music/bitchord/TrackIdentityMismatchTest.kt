package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.SourceResolver
import com.music.bitchord.data.sources.TrackMatcher
import com.music.bitchord.playback.QualityUpgrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates playback identity preservation and duration mismatch handling.
 *
 * Specifically addresses the São Paulo (Hurry Up Tomorrow) regression where a
 * 3:29 (209s) YouTube music video was cached or substituted for a 5:02 (302s)
 * album track, and the runtime decoder duration poisoned the upgrade search target.
 */
class TrackIdentityMismatchTest {

    private fun song(
        title: String,
        artist: String,
        durationText: String? = null,
        album: String? = null,
        videoId: String = "test-id",
    ) = Song(
        videoId = videoId,
        title = title,
        artist = artist,
        thumbnailUrl = null,
        durationText = durationText,
        albumName = album,
    )

    @Test
    fun `Test 1 - catalogue target 302s + runtime 209s preserves catalogue duration`() {
        val catalogueDuration = 302
        val runtimeDuration = 209

        val effectiveDuration = QualityUpgrade.effectiveTargetDuration(
            expectedSec = catalogueDuration,
            playingSec = runtimeDuration,
        )

        assertEquals(
            "Authoritative catalogue duration must be preserved when runtime drifts severely",
            302,
            effectiveDuration,
        )
    }

    @Test
    fun `Test 2 - catalogue target 302s + candidate 302s accepted`() {
        val catalogueDuration = 302
        val candidateDuration = 302

        assertTrue(
            "Candidate duration matching catalogue duration must be accepted as same recording",
            SourceResolver.sameRecordingAs(candidateDuration, catalogueDuration),
        )

        val target = TrackMatcher.Target(
            title = "São Paulo",
            artist = "The Weeknd",
            durationSec = catalogueDuration,
            album = "Hurry Up Tomorrow",
        )
        val jioSaavnTrack = song("São Paulo", "The Weeknd", "5:02", album = "Hurry Up Tomorrow")

        assertTrue(
            "TrackMatcher must accept 302s candidate against 302s catalogue target",
            TrackMatcher.withinSeconds(jioSaavnTrack, target, 2),
        )
    }

    @Test
    fun `Test 3 - catalogue target 302s + cached rendition 209s detects severe mismatch`() {
        val catalogueDuration = 302
        val cachedRenditionDuration = 209

        assertTrue(
            "Cached rendition drifting 93s (> 30s limit) must be flagged as severe mismatch",
            TrackMatcher.isSevereMismatch(catalogueDuration, cachedRenditionDuration),
        )
    }

    @Test
    fun `Test 4 - small legitimate duration difference retains existing runtime behavior`() {
        val catalogueDuration = 302
        val runtimeDuration = 300 // 2s drift, common between audio masters

        assertFalse(
            "2s drift between masters is within tolerance and not a severe mismatch",
            TrackMatcher.isSevereMismatch(catalogueDuration, runtimeDuration),
        )

        val effectiveDuration = QualityUpgrade.effectiveTargetDuration(
            expectedSec = catalogueDuration,
            playingSec = runtimeDuration,
        )

        assertEquals(
            "Small legitimate differences retain runtime decoder duration for upgrade matching",
            300,
            effectiveDuration,
        )

        // Test with 14s difference (typical intro/outro pad)
        val runtime14sDrift = 288
        assertFalse(
            "14s drift is within 30s canonical duration limit",
            TrackMatcher.isSevereMismatch(catalogueDuration, runtime14sDrift),
        )
        assertEquals(
            288,
            QualityUpgrade.effectiveTargetDuration(catalogueDuration, runtime14sDrift),
        )
    }

    @Test
    fun `Test 5 - catalogue duration null retains existing runtime behavior`() {
        val runtimeDuration = 209

        assertFalse(
            "Null catalogue duration cannot be flagged as a severe mismatch",
            TrackMatcher.isSevereMismatch(null, runtimeDuration),
        )

        val effectiveDurationWithNullCatalogue = QualityUpgrade.effectiveTargetDuration(
            expectedSec = null,
            playingSec = runtimeDuration,
        )
        assertEquals(
            "When catalogue duration is null, runtime decoder duration is used",
            209,
            effectiveDurationWithNullCatalogue,
        )

        val effectiveDurationWithNullRuntime = QualityUpgrade.effectiveTargetDuration(
            expectedSec = 302,
            playingSec = null,
        )
        assertEquals(
            "When playing runtime is null, catalogue duration is used",
            302,
            effectiveDurationWithNullRuntime,
        )
    }

    @Test
    fun `Test 6 - full Sao Paulo regression scenario`() {
        val catalogueTarget = TrackMatcher.Target(
            title = "São Paulo",
            artist = "The Weeknd, Anitta",
            durationSec = 302,
            album = "Hurry Up Tomorrow",
        )

        val youtubeVideoRuntime = 209 // 3:29 music video (2kjolTLZ_Mg)
        val jioSaavnCandidateDuration = 302 // 5:02 album stream (-rq378sI)

        // 1. Check cached rendition mismatch detection
        assertTrue(
            "Cached 209s video rendition must be identified as mismatched against 302s catalogue entry",
            TrackMatcher.isSevereMismatch(catalogueTarget.durationSec, youtubeVideoRuntime),
        )

        // 2. Target duration calculation in QualityUpgrade.lookAgain
        val effectiveDuration = QualityUpgrade.effectiveTargetDuration(
            expectedSec = catalogueTarget.durationSec,
            playingSec = youtubeVideoRuntime,
        )
        assertEquals(
            "Effective target duration must preserve 302s instead of being poisoned by 209s",
            302,
            effectiveDuration,
        )

        // 3. Confirm regression behavior without fix:
        // Raw runtime comparison would fail and reject the genuine JioSaavn album stream
        assertFalse(
            "Flawed check against raw 209s runtime would reject genuine 302s album stream",
            SourceResolver.sameRecordingAs(jioSaavnCandidateDuration, youtubeVideoRuntime),
        )

        // 4. Confirm fixed behavior:
        // Comparison against effective catalogue duration accepts the genuine JioSaavn stream
        assertTrue(
            "Check against effective duration accepts genuine 302s JioSaavn album stream",
            SourceResolver.sameRecordingAs(jioSaavnCandidateDuration, effectiveDuration),
        )

        val jioSaavnSong = song(
            title = "São Paulo",
            artist = "The Weeknd, Anitta",
            durationText = "5:02",
            album = "Hurry Up Tomorrow",
            videoId = "-rq378sI",
        )
        val effectiveTarget = catalogueTarget.copy(durationSec = effectiveDuration)

        assertTrue(
            "TrackMatcher matches genuine JioSaavn song against effective target",
            TrackMatcher.withinSeconds(jioSaavnSong, effectiveTarget, 2),
        )
    }
}
