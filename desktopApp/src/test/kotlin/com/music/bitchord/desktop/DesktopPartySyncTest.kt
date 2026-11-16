package com.music.bitchord.desktop

import com.music.bitchord.desktop.DesktopPartySync.Companion.decideSeek
import com.music.bitchord.desktop.DesktopPartySync.Companion.queueForPartyPublish
import com.music.bitchord.data.listentogether.PartyMember
import com.music.bitchord.data.listentogether.PartyTrack
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When the playhead gets moved to follow the party.
 *
 * A seek is audible, so the rules matter more than the arithmetic: a deliberate control aligns at
 * once, ordinary drift has to be both large and persistent, and a correction that just fired
 * cannot fire again immediately.
 */
class DesktopPartySyncTest {

    @Test
    fun `party queue publish preserves current autoplay and drops only context tail`() {
        val past = Song("past", "Past", "Artist", null, queueTier = QueueTier.CONTEXT)
        val current = Song("current", "Current", "Artist", null, queueTier = QueueTier.AUTOPLAY)
        val contextTail = Song("context", "Context", "Artist", null, queueTier = QueueTier.CONTEXT)
        val autoplayTail = Song("auto", "Auto", "Artist", null, queueTier = QueueTier.AUTOPLAY)

        val (published, index) = queueForPartyPublish(
            songs = listOf(past, current, contextTail, autoplayTail),
            currentIndex = 1,
            currentVideoId = current.videoId,
            currentDurationMs = 123_000L,
        )

        assertEquals(listOf("past", "current", "auto"), published.map(PartyTrack::videoId))
        assertEquals(1, index)
        assertTrue(published[1].fromAutoplay, "the current AutoPlay item must remain marked")
        assertTrue(published[2].fromAutoplay, "the AutoPlay tail must be sent to the server")
        assertEquals(123_000L, published[1].durationMs)
    }

    @Test
    fun `party queue publish caps upcoming songs like the phone`() {
        val songs = listOf(Song("current", "Current", "Artist", null)) +
            (1..40).map {
                Song("next-$it", "Next $it", "Artist", null, queueTier = QueueTier.USER_QUEUE)
            }

        val (published, index) = queueForPartyPublish(
            songs = songs,
            currentIndex = 0,
            currentVideoId = "current",
            currentDurationMs = 1L,
        )

        assertEquals(26, published.size, "current plus 25 upcoming songs")
        assertEquals(0, index)
        assertEquals("next-25", published.last().videoId)
    }

    @Test
    fun `autoplay supplier election matches the phone`() {
        val host = PartyMember("host", isHost = true, connected = true)
        val member = PartyMember("a-member", connected = true)
        val you = PartyMember("you", connected = true)

        assertEquals(
            "host",
            DesktopAutoplay.supplierId(
                DesktopListenTogether.State(code = "party", you = you, members = listOf(member, host, you)),
            ),
        )
        assertEquals(
            "a-member",
            DesktopAutoplay.supplierId(
                DesktopListenTogether.State(code = "party", you = you, members = listOf(member, you)),
            ),
            "an unlocked party falls back deterministically after the host disconnects",
        )
        assertEquals(
            null,
            DesktopAutoplay.supplierId(
                DesktopListenTogether.State(
                    code = "party",
                    you = you,
                    members = listOf(member, you),
                    hostOnlyControl = true,
                ),
            ),
            "a host-only party waits for its host rather than electing a guest",
        )
    }

    @Test
    fun `autoplay rearms only when the current song is the empty tail`() {
        assertTrue(
            DesktopAutoplay.queueNeedsRefresh(
                enabled = true,
                repeatAll = false,
                currentIndex = 2,
                itemCount = 3,
                loadInProgress = false,
            ),
        )
        assertFalse(
            DesktopAutoplay.queueNeedsRefresh(
                enabled = true,
                repeatAll = false,
                currentIndex = 1,
                itemCount = 3,
                loadInProgress = false,
            ),
        )
        assertFalse(
            DesktopAutoplay.queueNeedsRefresh(
                enabled = true,
                repeatAll = false,
                currentIndex = 2,
                itemCount = 3,
                loadInProgress = true,
            ),
        )
    }

    @Test
    fun `party queue conversion preserves autoplay boundary`() {
        val autoplay = PartyTrack("next", "Next", "Artist", fromAutoplay = true).toDesktopSong()
        assertEquals(QueueTier.AUTOPLAY, autoplay.queueTier)

        val shared = Song("id", "Title", "Artist", null, queueTier = QueueTier.AUTOPLAY)
            .toPartyTrack(durationMs = 123_000L)
        assertTrue(shared.fromAutoplay)
        assertEquals(123_000L, shared.durationMs)
    }

    @Test
    fun `a new control aligns at once when it is meaningfully out`() {
        val decision = decideSeek(
            drift = 900, controlSeq = 7, alignedSeq = 6, strikes = 0, nowMs = 0, cooldownUntilMs = 0,
        )
        assertTrue(decision.seek, "a fresh control should align immediately")
        assertEquals(7, decision.alignedSeq)
    }

    @Test
    fun `a new control that already lines up does not seek`() {
        val decision = decideSeek(
            drift = 40, controlSeq = 7, alignedSeq = 6, strikes = 0, nowMs = 0, cooldownUntilMs = 0,
        )
        assertFalse(decision.seek, "40ms is inside the align tolerance")
        assertEquals(7, decision.alignedSeq, "the control is still marked as handled")
    }

    @Test
    fun `small drift is left alone and clears the strikes`() {
        val decision = decideSeek(
            drift = 800, controlSeq = 7, alignedSeq = 7, strikes = 1, nowMs = 0, cooldownUntilMs = 0,
        )
        assertFalse(decision.seek)
        assertEquals(0, decision.strikes, "a reading inside the limit resets the count")
    }

    @Test
    fun `one large reading is not enough on its own`() {
        val decision = decideSeek(
            drift = 5_000, controlSeq = 7, alignedSeq = 7, strikes = 0, nowMs = 0, cooldownUntilMs = 0,
        )
        assertFalse(decision.seek, "a single stall should not cause an audible seek")
        assertEquals(1, decision.strikes)
    }

    @Test
    fun `a second large reading corrects and opens a cooldown`() {
        val decision = decideSeek(
            drift = 5_000, controlSeq = 7, alignedSeq = 7, strikes = 1, nowMs = 10_000, cooldownUntilMs = 0,
        )
        assertTrue(decision.seek)
        assertEquals(0, decision.strikes, "the count restarts after a correction")
        assertTrue(decision.cooldownUntilMs > 10_000, "a correction must not be able to repeat at once")
    }

    @Test
    fun `nothing fires while the cooldown is still running`() {
        val decision = decideSeek(
            drift = 5_000, controlSeq = 7, alignedSeq = 7, strikes = 1, nowMs = 1_000, cooldownUntilMs = 6_000,
        )
        assertFalse(decision.seek, "still inside the cooldown")
        assertEquals(1, decision.strikes, "and the count is held rather than advanced")
    }

    @Test
    fun `drift is corrected in either direction`() {
        val behind = decideSeek(
            drift = -5_000, controlSeq = 7, alignedSeq = 7, strikes = 1, nowMs = 10_000, cooldownUntilMs = 0,
        )
        assertTrue(behind.seek, "running behind the party is as wrong as running ahead")
    }
}
