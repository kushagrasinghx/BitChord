package com.music.bitchord.download

import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.offline.PendingRating
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of the automatic download pass that can be answered without a
 * network, a filesystem or an Android runtime behind them.
 *
 * The pass itself is [SmartDownloadsWorker], and what makes it correct is a
 * handful of decisions it makes in the same way every run: what counts as new,
 * whether today has any quota left, and what survives to the next run. Those
 * are here, and they are pure precisely so that they can be.
 */
class SmartDownloadsTest {

    @Test
    fun newTrack_isCandidate() {
        assertTrue(isNewCandidate("abc", emptySet(), emptySet()))
    }

    @Test
    fun trackThePassAlreadyQueued_isNot() {
        assertFalse(isNewCandidate("abc", setOf("abc"), emptySet()))
    }

    @Test
    fun trackAlreadyOnDisk_isNot() {
        // Downloaded by hand or by an earlier run — either way there is
        // nothing for this one to do, and the pass has no way to tell the
        // two apart nor any reason to care.
        assertFalse(isNewCandidate("abc", emptySet(), setOf("abc")))
    }

    @Test
    fun trackFailedAndWaitingForRetry_isCandidateAgain() {
        // The worker takes a failed id out of the ledger before it filters
        // anything, so being decided is not what holds a failure back — see
        // SmartDownloadsWorker.harvest. With the entry gone, this is the case
        // that decides whether it is tried a second time.
        assertTrue(isNewCandidate("abc", emptySet(), emptySet()))
    }

    @Test
    fun emptyId_isNeverCandidate() {
        assertFalse(isNewCandidate("", emptySet(), emptySet()))
    }

    @Test
    fun quota_isLeftWhereTheDayCountedIt() {
        val ledger = SmartDownloadLedger(day = "2026-09-24", used = 7)
        assertEquals(7, ledger.usedOn("2026-09-24"))
        assertEquals(13, ledger.remainingQuota("2026-09-24", 20))
    }

    @Test
    fun quota_refillsOnANewDay() {
        val ledger = SmartDownloadLedger(day = "2026-09-23", used = 20)
        assertEquals(0, ledger.usedOn("2026-09-24"))
        assertEquals(20, ledger.remainingQuota("2026-09-24", 20))
    }

    @Test
    fun quota_neverGoesNegativeWhenTheLimitIsLowered() {
        // The daily limit is a slider the listener can move mid-day, so a run
        // that has already spent more than the new number is possible. A
        // negative quota would queue `limit - used` more tracks, which is the
        // opposite of what the setting now says.
        val ledger = SmartDownloadLedger(day = "2026-09-24", used = 40)
        assertEquals(0, ledger.remainingQuota("2026-09-24", 20))
    }

    @Test
    fun ledger_beforeItsFirstRun_countsNothing() {
        val ledger = SmartDownloadLedger()
        assertEquals(0, ledger.usedOn("2026-09-24"))
        assertEquals(20, ledger.remainingQuota("2026-09-24", 20))
        assertTrue(ledger.decided.isEmpty())
        assertEquals(0, ledger.playlistCursor)
    }

    @Test
    fun ledger_survivesARoundTripThroughJson() {
        // The ledger is written as text under a key the next process has to
        // read back, so a field that silently fails to decode would quietly
        // hand the pass an empty one — and an empty ledger means the whole
        // library looks new again. Unknown keys are ignored on purpose: a
        // downgrade must not throw away what an upgrade recorded.
        val ledger = SmartDownloadLedger(
            decided = linkedMapOf("abc" to 1_000L, "def" to 2_000L),
            day = "2026-09-24",
            used = 5,
            playlistCursor = 3,
        )
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<SmartDownloadLedger>(json.encodeToString(ledger))
        assertEquals(ledger, decoded)
    }

    @Test
    fun ledger_ignoresFieldsAFutureVersionAdded() {
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<SmartDownloadLedger>(
            """{"decided":{"abc":1000},"day":"2026-09-24","used":5,"playlistCursor":0,"somethingNew":true}""",
        )
        assertEquals("2026-09-24", decoded.day)
        assertEquals(5, decoded.used)
        assertEquals(mapOf("abc" to 1_000L), decoded.decided)
    }

    // ── Eviction order ──────────────────────────────────────────────────────

    @Test
    fun eviction_dropsTheTrackHeardLongestAgoFirst() {
        // Queue time deliberately against it: the older download is the newer
        // request, and the play time is still what decides.
        val order = evictionOrder(
            present = listOf("recent", "ancient"),
            heardAt = mapOf("recent" to 2_000L, "ancient" to 1_000L),
            decidedAt = mapOf("recent" to 1L, "ancient" to 2L),
        )
        assertEquals(listOf("ancient", "recent"), order)
    }

    @Test
    fun eviction_neverHeardGoesBeforeAnythingHeard() {
        // The unheard track was queued *after* the one heard last month and
        // still goes first — a folder is not a queue, so "oldest download" is
        // not the rule.
        val order = evictionOrder(
            present = listOf("heard", "unheard"),
            heardAt = mapOf("heard" to 1_000L),
            decidedAt = mapOf("unheard" to 1L, "heard" to 9L),
        )
        assertEquals(listOf("unheard", "heard"), order)
    }

    @Test
    fun eviction_amongTheNeverHeard_theOldestRequestGoesFirst() {
        // Most of a folder has never been played, so this tiebreak is the one
        // that actually runs: without it the same run would shuffle a
        // different handful out every day.
        val order = evictionOrder(
            present = listOf("newer", "older"),
            heardAt = emptyMap(),
            decidedAt = mapOf("older" to 100L, "newer" to 200L),
        )
        assertEquals(listOf("older", "newer"), order)
    }

    @Test
    fun eviction_aTrackHistoryHasForgotten_countsAsNeverHeard() {
        // History is pruned to a handful of months, so an id outside that
        // window has no timestamp at all. Reading it as "never" rather than as
        // "older than everything" is what keeps an id the record simply does
        // not carry from outliving one heard last week.
        val order = evictionOrder(
            present = listOf("forgotten", "heard"),
            heardAt = mapOf("heard" to 5_000L),
            decidedAt = mapOf("forgotten" to 1L, "heard" to 1L),
        )
        assertEquals(listOf("forgotten", "heard"), order)
    }

    @Test
    fun eviction_anIdOutsideTheLedgerCannotJumpTheQueue() {
        // Defensive: present on disk but absent from the ledger should be
        // last in every respect, never first.
        val order = evictionOrder(
            present = listOf("known", "stranger"),
            heardAt = mapOf("known" to 9_000L),
            decidedAt = mapOf("known" to 1L),
        )
        assertEquals(listOf("stranger", "known"), order)
    }

    // ── The offline outbox ──────────────────────────────────────────────────

    @Test
    fun pendingRating_survivesARoundTripThroughJson() {
        // What makes the queue worth having is that it outlives the process
        // that created it, so anything that fails to decode silently turns a
        // kept tap back into a lost one.
        val json = Json { ignoreUnknownKeys = true }
        val queued = PendingRating("abc", LikeStatus.LIKE.name, LikeStatus.INDIFFERENT.name)
        val decoded = json.decodeFromString<PendingRating>(json.encodeToString(queued))
        assertEquals(queued, decoded)
        assertEquals(LikeStatus.LIKE, decoded.statusEnum)
        assertEquals(LikeStatus.INDIFFERENT, decoded.previousEnum)
    }

    @Test
    fun pendingRating_ofAnUnknownStatusFallsBackRatherThanThrowing() {
        // A queue written by a newer build and read back by an older one must
        // not take the app down on launch; it simply cannot be delivered.
        val queued = PendingRating("abc", "SOMETHING_NEW", LikeStatus.LIKE.name)
        assertEquals(LikeStatus.INDIFFERENT, queued.statusEnum)
        assertEquals(LikeStatus.LIKE, queued.previousEnum)
    }
}
