package com.music.bitchord

import com.music.bitchord.ui.player.KaraokeAvailability
import com.music.bitchord.ui.player.offersSyncedLyricsEnable
import com.music.bitchord.ui.player.opensLyricsOnKaraokeActivation
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The sing-along coordination rules, as the decisions the player hands its
 * state to — see [opensLyricsOnKaraokeActivation] and [offersSyncedLyricsEnable]
 * in the player for what each one governs.
 *
 * The rules the decisions cannot see are structural and live in the callers:
 * closing lyrics writes only lyric state (Karaoke stays on), the drawer's
 * master switch writes only the setting (lyrics stay open), and the panel
 * the sing-along opens is the Lyrics glyph's own [openLyrics], never a
 * second surface.
 */
class KaraokeSingAlongTest {
    @Test fun freshActivationWithReductionRunningAndLyricsAllowedOpensTheLyrics() {
        assertEquals(
            true,
            opensLyricsOnKaraokeActivation(
                alreadyEnabled = false,
                availability = KaraokeAvailability.AVAILABLE,
                syncedLyricsEnabled = true,
            ),
        )
    }

    @Test fun blockedRouteNeverOpensTheLyrics() {
        // Every route that holds the reduction up — bit-perfect, a Cast
        // speaker, the watch, a mono track, an undecoded stream — explains
        // itself in the drawer instead of a sing-along that never started.
        KaraokeAvailability.values()
            .filter { it != KaraokeAvailability.AVAILABLE }
            .forEach { availability ->
                assertEquals(
                    "activation on $availability must not open lyrics",
                    false,
                    opensLyricsOnKaraokeActivation(
                        alreadyEnabled = false,
                        availability = availability,
                        syncedLyricsEnabled = true,
                    ),
                )
            }
    }

    @Test fun syncedLyricsOffNeverOpensTheLyrics() {
        // The preference governs third-party network lookups, so activation
        // must not act as though it had flipped it. The drawer offers the
        // explicit tap instead (see below).
        assertEquals(
            false,
            opensLyricsOnKaraokeActivation(
                alreadyEnabled = false,
                availability = KaraokeAvailability.AVAILABLE,
                syncedLyricsEnabled = false,
            ),
        )
    }

    @Test fun pressWhileAlreadyOnLeavesTheLyricsAlone() {
        // Adjusting the level never drags the panel up: the press opens the
        // drawer, and only the listener brings the words back. Holds on every
        // route, running or paused, and with the words allowed or not.
        KaraokeAvailability.values().forEach { availability ->
            assertEquals(
                false,
                opensLyricsOnKaraokeActivation(
                    alreadyEnabled = true,
                    availability = availability,
                    syncedLyricsEnabled = true,
                ),
            )
            assertEquals(
                false,
                opensLyricsOnKaraokeActivation(
                    alreadyEnabled = true,
                    availability = availability,
                    syncedLyricsEnabled = false,
                ),
            )
        }
    }

    @Test fun theExplicitEnableTapIsOfferedOnlyWhileKaraokeIsOnAndLyricsAreOff() {
        assertEquals(
            true,
            offersSyncedLyricsEnable(karaokeEnabled = true, syncedLyricsEnabled = false),
        )
        // Never with the preference already allowed...
        assertEquals(
            false,
            offersSyncedLyricsEnable(karaokeEnabled = true, syncedLyricsEnabled = true),
        )
        // ...and never with Karaoke off, when the drawer is about the audio.
        assertEquals(
            false,
            offersSyncedLyricsEnable(karaokeEnabled = false, syncedLyricsEnabled = false),
        )
        assertEquals(
            false,
            offersSyncedLyricsEnable(karaokeEnabled = false, syncedLyricsEnabled = true),
        )
    }
}
