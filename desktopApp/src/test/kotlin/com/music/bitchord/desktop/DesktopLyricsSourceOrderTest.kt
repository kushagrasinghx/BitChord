package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The source list, as the settings dialog stores it and the lookup reads it back. */
class DesktopLyricsSourceOrderTest {

    @Test
    fun theChainIsTheStoredOrderNarrowedToWhatIsTicked() {
        val order = listOf("BiniLyrics", "LRCLIB", "Genius")
        assertEquals(
            listOf("BiniLyrics", "Genius"),
            DesktopLyricsClient.enabledSources(order, setOf("Genius", "BiniLyrics")),
        )
    }

    @Test
    fun untickingEverythingLeavesNothingToAsk() {
        assertEquals(emptyList(), DesktopLyricsClient.enabledSources(listOf("LRCLIB"), emptySet()))
    }

    @Test
    fun theAppleHostsLeadOutOfTheBox() {
        // The three of them carry the same catalogue and it is the one with the voices in it.
        val names = DesktopLyricsClient.sources.map { it.name }
        assertEquals(listOf("BiniLyrics", "BetterLyrics", "PaxSenix"), names.take(3))
        // LyricsPlus has the finest timing and the least reliable hosting, so a track does not wait
        // on a mirror that is down to be told what three other hosts already had.
        assertTrue(names.indexOf("LyricsPlus") > names.indexOf("BetterLyrics"))
        // The unsynced fallback is last by definition.
        assertEquals("Genius", names.last())
    }

    @Test
    fun everySourceOfferedIsOneTheLookupCanActuallyAsk() {
        // The dialog is built from [sources]; a name in it with no provider behind it is a row that
        // silently does nothing when ticked.
        val order = DesktopLyricsClient.sources.map { it.name }
        assertEquals(order, DesktopLyricsClient.enabledSources(order, order.toSet()))
    }
}
