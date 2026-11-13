package com.music.bitchord

import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.playback.lyricsSettingChanges
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsSettingChangesTest {

    @Test
    fun `reordering sources or switching syllable sync reloads the service's lyrics`() = runTest {
        val synced = MutableStateFlow(true)
        val sources = MutableStateFlow(setOf(LyricsSource.LRCLIB, LyricsSource.GENIUS))
        val order = MutableStateFlow(listOf(LyricsSource.LRCLIB, LyricsSource.GENIUS))
        val prioritize = MutableStateFlow(false)
        var reloads = 0
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            lyricsSettingChanges(synced, sources, order, prioritize).collect { reloads++ }
        }

        order.value = listOf(LyricsSource.GENIUS, LyricsSource.LRCLIB)
        prioritize.value = true
        synced.value = true // unchanged: no reload

        assertEquals(3, reloads)
        job.cancel()
    }
}
