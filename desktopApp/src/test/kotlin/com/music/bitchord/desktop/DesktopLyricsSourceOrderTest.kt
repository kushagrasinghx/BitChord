package com.music.bitchord.desktop

import com.music.bitchord.data.lyrics.LyricsSource
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
    fun theDesktopOffersThePhonesProvidersInThePhonesOrder() {
        // One provider list for both apps: the lookup is the phone's LyricsRepository.
        val names = DesktopLyricsClient.sources.map { it.name }
        assertEquals(LyricsSource.entries.map { it.label }, names)
        assertTrue(names.all { lyricsSourceNamed(it) != null })
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
